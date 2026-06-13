package com.example.aimusicplayer.ui.main

import co.touchlab.kermit.Logger
import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.ai.AiCommand
import com.example.aimusicplayer.ai.AiStep
import com.example.aimusicplayer.ai.AiResult
import com.example.aimusicplayer.music.BilibiliTitleParser
import com.example.aimusicplayer.music.CrossPlatformLyricsFetcher
import com.example.aimusicplayer.music.CrossPlatformLyricsResult
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.music.DownloadState
import com.example.aimusicplayer.music.downloadKey
import com.example.aimusicplayer.music.LyricsMatchConfidence
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.ParsedBilibiliTitle
import com.example.aimusicplayer.music.PlayMode
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.music.PlaylistSong
import com.example.aimusicplayer.import.PlaylistLinkParser
import com.example.aimusicplayer.music.SearchState
import com.example.aimusicplayer.network.RateLimiterRegistry
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.settings.SettingsKeys
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.ui.chat.ChatItem
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.aimusicplayer.behavior.LocalSongIndex
import com.example.aimusicplayer.behavior.UserBehaviorRepository
import com.example.aimusicplayer.behavior.UserBehaviorTracker
import com.example.aimusicplayer.recommendation.RecommendationEngine
import com.example.aimusicplayer.recommendation.config.RecommendationConfig
import com.example.aimusicplayer.viewmodel.ViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * State for the sleep timer feature.
 *
 * @property isActive         Whether a countdown is currently running.
 * @property remainingMs      Milliseconds remaining, updated every second.
 * @property totalMs          Initial total duration in milliseconds.
 * @property finishCurrentSong If true, wait for the current song to end
 *                             after countdown reaches zero before pausing.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val finishCurrentSong: Boolean = false,
)

/**
 * Format remaining milliseconds as a human-readable timer string.
 * - < 1 hour:  "MM:SS"
 * - ≥ 1 hour:  "HH:MM:SS"
 */
fun formatTimerRemaining(remainingMs: Long): String {
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

/**
 * ViewModel for the main chat + playback screen.
 *
 * ## Error handling strategy
 * | Scenario                        | User-visible behavior                                   |
 * |---------------------------------|--------------------------------------------------------|
 * | API Key missing                 | Chat banner + redirect to settings                      |
 * | API Key invalid (401)           | Key auto-cleared + settings prompt                      |
 * | AI network failure              | Retry once → error message in chat                      |
 * | All search platforms fail       | "未找到歌曲音源" + details in log                       |
 * | Play URL expired / unavailable  | Auto-retry next search result (up to 3 candidates)      |
 * | Decode failure                  | Skip to next result + log warning                       |
 * | Download network failure        | Error snackbar, song remains playable while streaming   |
 *
 * ## Logging
 * All significant events are logged via [Kermit] with tag `MainViewModel`.
 *
 * @param musicPlayer              Cross-platform audio player
 * @param aiAssistant              OpenAI-compatible chat assistant
 * @param searchManager            Multi-platform search orchestrator
 * @param downloadManager          File download manager
 * @param settingsRepo             Typed settings accessor
 */
class MainViewModel(
    private val musicPlayer: MusicPlayer,
    private val aiAssistant: AiAssistant,
    private val searchManager: MusicSearchManager,
    private val downloadManager: DownloadManager,
    private val settingsRepo: SettingsRepository,
    private val playlistManager: PlaylistManager,
    private val userBehaviorTracker: UserBehaviorTracker,
    private val localSongIndex: LocalSongIndex,
    private val onImportLinkDetected: (String) -> Unit = {},
    private val onAutoImportLinkDetected: (String) -> Unit = {},
) : ViewModel() {

    /** Callback to trigger the search panel UI for AI search requests.
     *  Params: songName, artist */
    var onSearchRequested: ((String, String) -> Unit)? = null
    /** Callback to open the now-playing overlay. */
    var onOpenNowPlaying: (() -> Unit)? = null
    /** Callback to open lyrics (toggles from overlay or main). */
    var onOpenLyrics: (() -> Unit)? = null

    private val log = Logger.withTag("MainViewModel")
    private val crossPlatformLyricsFetcher = CrossPlatformLyricsFetcher(searchManager)

    // ══════════════════════════════════════════════════════════════
    // Chat state
    // ══════════════════════════════════════════════════════════════

    private val _chatItems = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatItems: StateFlow<List<ChatItem>> = _chatItems.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Player state (delegated from MusicPlayer)
    // ══════════════════════════════════════════════════════════════

    val currentSong: StateFlow<SongMetadata?> = musicPlayer.currentSong
    val isPlaying: StateFlow<Boolean> = musicPlayer.isPlaying
    val currentPosition: StateFlow<Long> = musicPlayer.currentPosition
    val duration: StateFlow<Long> = musicPlayer.duration
    val playerError: StateFlow<String?> = musicPlayer.playbackError

    // ══════════════════════════════════════════════════════════════
    // Download state
    // ══════════════════════════════════════════════════════════════

    val downloadProgress: StateFlow<Map<String, DownloadState>> = downloadManager.allDownloads

    // ══════════════════════════════════════════════════════════════
    // Search state
    // ══════════════════════════════════════════════════════════════

    val searchState: StateFlow<SearchState> = searchManager.searchState

    // ══════════════════════════════════════════════════════════════
    // Error / snackbar
    // ══════════════════════════════════════════════════════════════

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Lyrics state
    // ══════════════════════════════════════════════════════════════

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _lyricsText = MutableStateFlow<String?>(null)
    val lyricsText: StateFlow<String?> = _lyricsText.asStateFlow()

    // In-memory lyrics cache: songId → raw LRC text
    private val cachedLyrics = mutableMapOf<String, String>()

    // B站 lyrics manual sync offset: songId → offset in milliseconds
    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()
    private val cachedLyricsOffsets = mutableMapOf<String, Long>()

    // B站 lyrics re-search: B站 songId → set of rejected Netease songIds
    private val rejectedLyricSongIds = mutableMapOf<String, MutableSet<String>>()
    // Track the current matched Netease songId for potential re-search
    private var currentMatchedSongId: String? = null

    private val _isReSearchingLyrics = MutableStateFlow(false)
    val isReSearchingLyrics: StateFlow<Boolean> = _isReSearchingLyrics.asStateFlow()

    /** Whether the current song's download is being prepared (URL resolve + lyrics fetch). */
    private val _isPreparingDownload = MutableStateFlow(false)
    val isPreparingDownload: StateFlow<Boolean> = _isPreparingDownload.asStateFlow()

    /** Callback invoked when a download completes or fails (for cache size update). */
    var onCacheChanged: (() -> Unit)? = null

    /** 推荐自动播放的协程 Job，用户操作时可取消。 */
    private var playRecommendJob: Job? = null

    /** 连续播放 N 首推荐的队列（null = 未启用连续播放模式）。 */
    private var recommendQueue: List<com.example.aimusicplayer.recommendation.reason.RecommendationResult>? = null
    private var recommendQueueIndex = 0
    private var recommendQueueTotal = 0
    /** 最后一次由推荐队列播放的歌曲 ID，用于检测用户是否手动干预。 */
    private var lastRecommendSongId: String? = null

    /** 清除连续播放推荐状态。 */
    private fun clearRecommendQueue() {
        recommendQueue = null
        lastRecommendSongId = null
        playRecommendJob?.cancel()
        playRecommendJob = null
    }

    // ══════════════════════════════════════════════════════════════
    // Sleep timer state
    // ══════════════════════════════════════════════════════════════

    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()
    private var timerJob: kotlinx.coroutines.Job? = null

    // ══════════════════════════════════════════════════════════════
    // Downloaded state
    // ══════════════════════════════════════════════════════════════

    private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

    init {
        scope.launch {
            val songs = downloadManager.getDownloadedSongs()
            _downloadedSongIds.value = songs.map { it.songId }.toSet()
            // Restore lyrics from previously downloaded songs
            for (s in songs) {
                if (!s.lyrics.isNullOrBlank()) {
                    cachedLyrics[s.songId] = s.lyrics
                }
            }
        }
        // Reactively track completed downloads so isCurrentSongDownloaded
        // updates in real-time when the user downloads from search panel
        scope.launch {
            downloadManager.allDownloads.collect { states ->
                val downloaded = states.filter { it.value.progress >= 1f }.keys
                if (downloaded.isNotEmpty()) {
                    val current = _downloadedSongIds.value
                    if (!current.containsAll(downloaded)) {
                        _downloadedSongIds.value = current + downloaded
                    }
                }
            }
        }
        // Load persisted lyrics offsets for B站 songs
        scope.launch {
            loadLyricsOffsets()
        }
        // Auto-fetch lyrics whenever the current song changes (regardless of
        // which ViewModel triggered playback — SearchViewModel or MainViewModel)
        scope.launch {
            currentSong.collect { song ->
                // Update lyrics offset when song changes
                _lyricsOffsetMs.value = if (song != null) getLyricsOffset(song.songId) else 0L
                if (song != null && song.songId !in cachedLyrics) {
                    fetchAndCacheLyrics(song)
                }
            }
        }
        // Auto-play next song when current song finishes
        scope.launch {
            var wasPlaying = false
            isPlaying.collect { playing ->
                if (wasPlaying && !playing) {
                    // Playback just stopped — check if it ended naturally
                    val pos = currentPosition.value
                    val dur = duration.value
                    if (dur > 0 && pos > 0 && pos >= dur - 3000) {
                        // Check sleep timer: if finish-current-song mode is active
                        // and countdown has already reached zero, pause instead of advancing
                        val timerState = _sleepTimer.value
                        if (timerState.isActive && timerState.remainingMs <= 0L && timerState.finishCurrentSong) {
                            log.d { "Sleep timer: finish-current-song — pausing after song end" }
                            _sleepTimer.value = SleepTimerState()
                            // Player already stopped naturally; no need to call pause()
                        } else {
                            // Normal auto-next behavior
                            log.d { "Auto-next: song ended naturally (pos=$pos, dur=$dur)" }
                            delay(2_000)
                            if (playlistManager.getPlayMode() == PlayMode.REPEAT_ONE) {
                                // Replay current song
                                musicPlayer.seekTo(0)
                                musicPlayer.resume()
                            } else {
                                // 如果在连续播放推荐模式，优先播下一首推荐
                                // 但先验证：刚结束的歌必须是推荐队列播放的（未被用户手动干预）
                                val finished = currentSong.value
                                if (recommendQueue != null &&
                                    finished != null &&
                                    finished.songId == lastRecommendSongId) {
                                    if (finished != null) {
                                        playlistManager.removeFromPlaylist(finished.songId, finished.platform)
                                    }
                                    val success = tryPlayNextRecommend()
                                    if (success) return@collect
                                    // 播放失败（搜不到等），清除队列恢复正常
                                    clearRecommendQueue()
                                    _chatItems.value = _chatItems.value +
                                        ChatItem.AiMessage("推荐歌曲暂时无法播放，已恢复普通播放模式", null)
                                    return@collect
                                }
                                // 用户手动干预了 → 清除队列
                                if (recommendQueue != null) {
                                    log.i { "User intervened during recommend queue — clearing" }
                                    clearRecommendQueue()
                                }
                                // Advance first, then remove (so nextTrack can find the current index)
                                nextTrack()
                                if (finished != null) {
                                    playlistManager.removeFromPlaylist(finished.songId, finished.platform)
                                }
                            }
                        }
                    }
                }
                wasPlaying = playing
            }
        }
        // Behavior tracking: record play start / end based on song & playback state changes
        var trackedSongId: String? = null
        var trackedPlayStartMs: Long = 0L
        scope.launch {
            combine(currentSong, isPlaying) { song, playing -> song to playing }.collect { (song, playing) ->
                val songKey = song?.let { "${it.songId}|${it.platform}" }
                if (song != null && playing && songKey != trackedSongId) {
                    // New song started playing — record previous song end first
                    if (trackedSongId != null && trackedPlayStartMs > 0) {
                        val elapsed = Clock.System.now().toEpochMilliseconds() - trackedPlayStartMs
                        userBehaviorTracker.recordPlayEnd(elapsed, skipThreshold = 0.3f)
                    }
                    trackedSongId = songKey
                    trackedPlayStartMs = Clock.System.now().toEpochMilliseconds()
                    // B站：使用 realName/realArtist 记录真实歌名和歌手（搜索输入框的值）
                    val trackingSong = if (song.platform == "bilibili") {
                        song.copy(
                            songName = song.realName ?: song.songName,
                            artist = song.realArtist ?: song.artist,
                        )
                    } else song
                    userBehaviorTracker.recordPlayStart(
                        song = trackingSong,
                        totalDurationMs = duration.value,
                    )
                } else if (!playing && trackedSongId != null && trackedPlayStartMs > 0) {
                    // Playback paused/stopped — record play end
                    val elapsed = Clock.System.now().toEpochMilliseconds() - trackedPlayStartMs
                    userBehaviorTracker.recordPlayEnd(elapsed, skipThreshold = 0.3f)
                    trackedSongId = null
                    trackedPlayStartMs = 0L
                }
            }
        }
    }

    /** Whether the current song is already downloaded. */
    val isCurrentSongDownloaded: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        scope.launch {
            downloadedSongIds.collect { ids ->
                flow.value = currentSong.value?.let { downloadKey(it.songId, it.platform) in ids } ?: false
            }
        }
        scope.launch {
            currentSong.collect { song ->
                flow.value = song?.let { downloadKey(it.songId, it.platform) in _downloadedSongIds.value } ?: false
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Playback error → auto-retry
    // ══════════════════════════════════════════════════════════════

    /** Last search results, kept for retry when a play URL fails. */
    private var lastSearchResults: List<SongMetadata> = emptyList()
    /** Index of the song currently attempted for playback. */
    private var currentSongIndex: Int = -1
    /** Maximum candidates to try before giving up. */
    private val maxRetryCandidates: Int = 3

    // ══════════════════════════════════════════════════════════════
    // Public actions
    // ══════════════════════════════════════════════════════════════

    /** Update the text input field. */
    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    /**
     * Send the current input text to the AI and process the response.
     *
     * Full pipeline:
     * ```
     * input → AiAssistant → AiCommand → MusicSearchManager → getPlayUrl → MusicPlayer
     * ```
     */
    fun sendMessage() {
        // 用户发送新指令，取消推荐自动播放
        clearRecommendQueue()

        val text = _inputText.value.trim()
        if (text.isBlank() || _isThinking.value) return

        // Check for pure music platform share links (no natural language)
        val trimmed = text.trim()
        if (trimmed.startsWith("http") && PlaylistLinkParser.isMusicLink(trimmed)) {
            onImportLinkDetected(trimmed)
            _chatItems.value = _chatItems.value + ChatItem.SystemMessage(
                text = "检测到音乐平台歌单链接，已跳转到导入页面",
                timestamp = Clock.System.now().toEpochMilliseconds(),
            )
            return
        }

        // ── 我的喜好 / 偏好查询 ──────────────────────────────
        val preferenceQueries = listOf("我的喜好", "我的偏好", "音乐画像",
            "我喜欢哪些歌手", "我喜欢什么歌", "我爱听什么", "我的听歌口味",
            "我经常听什么", "我的音乐品味", "分析我的听歌习惯", "我听过哪些歌")
        if (trimmed in preferenceQueries || trimmed.startsWith("我喜欢") && trimmed.endsWith("？") ||
            trimmed.startsWith("我常听") || trimmed.startsWith("我爱听")) {
            _chatItems.value = _chatItems.value + ChatItem.UserMessage(text)
            _inputText.value = ""
            _isThinking.value = true
            scope.launch {
                try {
                    val behaviors = userBehaviorTracker.getAllBehaviors()
                    if (behaviors.isEmpty()) {
                        _chatItems.value = _chatItems.value +
                            ChatItem.AiMessage("你还没听过任何歌曲呢～先搜几首喜欢的歌听听吧 🎵", null)
                        _isThinking.value = false
                        return@launch
                    }
                    // 统计偏好数据
                    val total = behaviors.size
                    val liked = behaviors.filter { it.liked }
                    val completed = behaviors.filter { it.completed }
                    val skipped = behaviors.filter { it.skipped }
                    val topArtists = behaviors.groupBy { it.artist }
                        .filter { it.key.isNotBlank() }
                        .mapValues { (_, list) -> list.size }
                        .entries.sortedByDescending { it.value }.take(10)
                        .joinToString("、") { "${it.key}(${it.value}次)" }
                    val likedSongs = liked.take(10).joinToString("、") { "《${it.songName}》-${it.artist}" }
                    val byPlatform = behaviors.groupBy { it.platform }
                        .mapValues { (_, list) -> list.size }
                        .entries.sortedByDescending { it.value }
                        .joinToString("、") { "${it.key}(${it.value}次)" }
                    val skipRate = if (total > 0) skipped.size * 100 / total else 0

                    val prompt = buildString {
                        append("请根据以下用户的真实听歌数据，生成一段中文音乐用户画像描述。")
                        append("本次只回复纯文本描述，不要输出JSON。")
                        append("要求：口语化、有洞察、指出用户音乐品味的特点，150字以内。\\n\\n")
                        append("【用户听歌数据】\\n")
                        append("总播放次数：$total\\n")
                        append("完整听完：${completed.size}首\\n")
                        append("跳过：${skipped.size}首（跳过率${skipRate}%）\\n")
                        append("喜欢标记：${liked.size}首\\n")
                        append("常听歌手（TOP10）：$topArtists\\n")
                        append("喜欢的歌曲：${likedSongs.ifBlank { "暂无" }}\\n")
                        append("平台分布：$byPlatform\\n")
                    }

                    log.i { "Sending my_preferences prompt to AI" }
                    when (val result = aiAssistant.sendMessage(prompt)) {
                        is AiResult.Success -> {
                            _chatItems.value = _chatItems.value +
                                ChatItem.AiMessage("画像数据已生成", result.command)
                        }
                        is AiResult.TextOnly -> {
                            _chatItems.value = _chatItems.value +
                                ChatItem.AiMessage(result.text, null)
                        }
                        else -> {
                            _chatItems.value = _chatItems.value +
                                ChatItem.AiMessage("暂时无法生成画像，请稍后再试", null)
                        }
                    }
                } catch (e: Exception) {
                    log.e { "my_preferences failed: ${e.message}" }
                    _chatItems.value = _chatItems.value +
                        ChatItem.AiMessage("画像生成失败: ${e.message}", null)
                } finally {
                    _isThinking.value = false
                }
            }
            return
        }

        // ── 重置推荐（候选池 + 用户画像） ──────────────────────
        if (trimmed == "重置推荐") {
            _chatItems.value = _chatItems.value + ChatItem.UserMessage(text)
            _inputText.value = ""
            _isThinking.value = true
            scope.launch {
                localSongIndex.clearAll()
                userBehaviorTracker.clearAll()
                _chatItems.value = _chatItems.value + ChatItem.AiMessage("候选池和播放记录已清空。输入「推荐」重新构建并获取推荐。", null)
                _isThinking.value = false
            }
            return
        }

        // ── 推荐关键词本地直发（不走 AI API） ──────────────────────
        val recMatch = Regex("^(推荐|有什么好听的|给我推荐|推荐一些|来点推荐)(\\d*)(首|首歌|个)?$").find(trimmed)
        if (recMatch != null) {
            val countStr = recMatch.groupValues.getOrElse(2) { "" }
            val count = countStr.toIntOrNull() ?: 10
            _chatItems.value = _chatItems.value + ChatItem.UserMessage(text)
            _inputText.value = ""
            _isThinking.value = true
            scope.launch {
                handleRecommend(AiStep(action = AiStep.ACTION_RECOMMEND, count = count))
                _isThinking.value = false
            }
            return
        }

        _chatItems.value = _chatItems.value + ChatItem.UserMessage(text)
        _inputText.value = ""
        _isThinking.value = true

        log.d { "sendMessage: \"$text\"" }

        scope.launch {
            when (val result = aiAssistant.sendMessage(text)) {
                is AiResult.Success -> {
                    log.i { "AI response: action=${result.command.action}" }
                    handleAiCommand(result.command)
                }
                is AiResult.TextOnly -> {
                    log.d { "AI text response: ${result.text}" }
                    _chatItems.value = _chatItems.value +
                        ChatItem.AiMessage(result.text, null)
                }
                is AiResult.ConfigMissing -> {
                    log.w { "API Key not configured" }
                    _chatItems.value = _chatItems.value +
                        ChatItem.SystemMessage("⚠️ 请先配置 API Key — 前往设置页面输入有效的 API Key")
                    _errorMessage.value = "未配置 API Key"
                }
                is AiResult.AuthError -> {
                    log.w { "API Key invalid (401)" }
                    _chatItems.value = _chatItems.value +
                        ChatItem.SystemMessage("⚠️ API Key 无效，已被清除 — 请重新配置")
                    _errorMessage.value = "API Key 无效"
                }
                is AiResult.Error -> {
                    log.e { "AI error: ${result.message}" }
                    _chatItems.value = _chatItems.value +
                        ChatItem.SystemMessage("❌ AI 服务异常: ${result.message}")
                    _errorMessage.value = "AI 服务连接失败，请检查网络和 API 配置"
                }
            }
            _isThinking.value = false
            log.d { "sendMessage complete" }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Player control actions
    // ══════════════════════════════════════════════════════════════

    fun togglePlayPause() {
        if (isPlaying.value) {
            musicPlayer.pause()
            log.d { "Player paused" }
        } else {
            musicPlayer.resume()
            log.d { "Player resumed" }
        }
    }

    /** 已喜欢的歌曲 ID 集合（songId|platform 格式），供 UI 同步喜欢状态。 */
    private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private fun updateLikedState(songId: String, platform: String) {
        scope.launch {
            val key = "$songId|$platform"
            val current = _likedSongIds.value.toMutableSet()
            val nowLiked = userBehaviorTracker.isLiked(songId, platform)
            if (nowLiked) current.add(key) else current.remove(key)
            _likedSongIds.value = current
        }
    }

    /** 供外部（如 PlaylistViewModel）在喜欢状态变更后调用，刷新 UI。 */
    fun refreshLikeState(songId: String, platform: String) {
        updateLikedState(songId, platform)
    }

    /** 重新加载全部喜欢状态（用于批量清除场景）。 */
    fun refreshAllLikes() {
        scope.launch {
            val freshIds = userBehaviorTracker.getAllBehaviors()
                .filter { it.liked }
                .map { "${it.songId}|${it.platform}" }
                .toSet()
            _likedSongIds.value = freshIds
        }
    }

    /** 切换当前歌曲的喜欢状态。返回切换后的状态。 */
    fun toggleLike() {
        val song = currentSong.value ?: return
        scope.launch {
            val isLiked = userBehaviorTracker.toggleLike(song.songId, song.platform)
            log.d { "toggleLike: ${song.songName} → $isLiked" }
            updateLikedState(song.songId, song.platform)
        }
    }

    /** 从候选池中删除指定歌曲（用户已通过搜索发现该歌曲）。 */
    fun removeFromSongIndex(songId: String, platform: String) {
        scope.launch {
            localSongIndex.removeByKey(songId, platform)
        }
    }

    /** 切换指定歌曲的喜欢状态（用于推荐卡片）。 */
    fun toggleLikeSong(songId: String, platform: String, songName: String = "", artist: String = "") {
        scope.launch {
            userBehaviorTracker.toggleLike(songId, platform, songName, artist)
            updateLikedState(songId, platform)
        }
    }

    /** 查询当前歌曲是否被喜欢（随歌曲切换和喜欢操作实时更新）。 */
    val isCurrentSongLiked: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        scope.launch {
            combine(currentSong, _likedSongIds) { song, likedIds ->
                song to likedIds
            }.collect { (song, likedIds) ->
                if (song != null) {
                    val key = "${song.songId}|${song.platform}"
                    val liked = key in likedIds
                    flow.value = liked
                    // 同步数据库状态到 likedIds
                    scope.launch { updateLikedState(song.songId, song.platform) }
                } else {
                    flow.value = false
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch { musicPlayer.seekTo(positionMs) }
    }

    fun nextTrack() {
        clearRecommendQueue()  // 用户手动切歌，退出推荐连续播放
        log.d { "nextTrack — navigating playlist" }
        scope.launch {
            val playlist = playlistManager.getPlaylist()
            if (playlist.isEmpty()) {
                musicPlayer.stop()
                _chatItems.value = _chatItems.value + ChatItem.SystemMessage("当前歌单为空")
                return@launch
            }
            val currentId = currentSong.value?.songId
            val currentIndex = playlist.indexOfFirst { it.songId == currentId }
            val playMode = playlistManager.getPlayMode()

            if (currentIndex >= 0) {
                val nextIndex = currentIndex + 1
                if (nextIndex < playlist.size) {
                    playPlaylistSong(playlist[nextIndex])
                } else {
                    // End of playlist
                    if (playMode == PlayMode.LOOP) {
                        playPlaylistSong(playlist[0])
                    } else {
                        musicPlayer.stop()
                        _chatItems.value = _chatItems.value + ChatItem.SystemMessage("已到歌单末尾")
                    }
                }
            } else {
                // Current song not in playlist — start from beginning
                playPlaylistSong(playlist[0])
            }
        }
    }

    fun previousTrack() {
        clearRecommendQueue()  // 用户手动切歌，退出推荐连续播放
        log.d { "previousTrack — navigating playlist" }
        scope.launch {
            val playlist = playlistManager.getPlaylist()
            if (playlist.isEmpty()) return@launch
            val currentId = currentSong.value?.songId
            val currentIndex = playlist.indexOfFirst { it.songId == currentId }

            if (currentIndex > 0) {
                playPlaylistSong(playlist[currentIndex - 1])
            } else if (currentIndex == 0) {
                val playMode = playlistManager.getPlayMode()
                if (playMode == PlayMode.LOOP) {
                    playPlaylistSong(playlist.last())
                }
                // In SEQUENTIAL mode, do nothing (already at first song)
            }
        }
    }

    /**
     * Start or restart the sleep timer with the given duration and mode.
     *
     * Calling this while a timer is already active overwrites the previous timer.
     *
     * @param minutes           Countdown duration in minutes.
     * @param finishCurrentSong Whether to wait for the current song to finish
     *                          before pausing after countdown reaches zero.
     */
    fun startSleepTimer(minutes: Int, finishCurrentSong: Boolean) {
        timerJob?.cancel()
        val totalMs = minutes * 60_000L
        _sleepTimer.value = SleepTimerState(
            isActive = true,
            remainingMs = totalMs,
            totalMs = totalMs,
            finishCurrentSong = finishCurrentSong,
        )
        timerJob = scope.launch { sleepTimerLoop() }
    }

    /**
     * Cancel the active sleep timer and reset state.
     * Safe to call when no timer is active (no-op).
     */
    fun cancelSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        _sleepTimer.value = SleepTimerState()
    }

    /**
     * Toggle the "finish current song" flag on the active timer.
     * Only meaningful when a countdown timer is already running.
     * If no timer is active, this is a no-op.
     */
    fun toggleSleepTimerFinishSong() {
        val current = _sleepTimer.value
        if (current.isActive && current.totalMs > 0) {
            _sleepTimer.value = current.copy(finishCurrentSong = !current.finishCurrentSong)
        }
    }

    /**
     * Internal countdown loop. Decrements [remainingMs] by 1000 every second.
     * When the countdown reaches zero and [SleepTimerState.finishCurrentSong] is false,
     * pauses immediately. Otherwise, the existing auto-next-track detection in [init]
     * picks up the zeroed timer and pauses when the song naturally ends.
     */
    private suspend fun sleepTimerLoop() {
        while (_sleepTimer.value.remainingMs > 0) {
            delay(1_000)
            val state = _sleepTimer.value
            val newRemaining = (state.remainingMs - 1_000).coerceAtLeast(0L)
            _sleepTimer.value = state.copy(remainingMs = newRemaining)
        }
        // Countdown finished
        val state = _sleepTimer.value
        if (!state.finishCurrentSong) {
            // Pause immediately
            musicPlayer.pause()
            _sleepTimer.value = SleepTimerState()
        }
        // If finishCurrentSong is true, do nothing — the auto-next-track
        // detection in init handles pausing when the song naturally ends.
    }

    /** Play a song from the playlist by its stored fields. */
    private suspend fun playPlaylistSong(song: PlaylistSong) {
        val url = song.playUrl
        if (url.isNullOrBlank()) {
            log.w { "No cached URL for ${song.songName} — skipping" }
            return
        }
        musicPlayer.play(
            url = url,
            title = song.realName ?: song.songName,
            artist = song.realArtist ?: song.artist,
            song = SongMetadata(
                songId = song.songId,
                songName = song.songName,
                artist = song.artist,
                platform = song.platform,
                quality = song.quality,
                coverUrl = song.coverUrl,
                duration = song.duration,
                realName = song.realName,
                realArtist = song.realArtist,
            ),
        )
    }

    fun downloadCurrentSong() {
        val song = currentSong.value ?: return
        _isPreparingDownload.value = true
        scope.launch {
            log.d { "downloadCurrentSong: ${song.songName} (${song.platform}/${song.songId})" }

            // 已下载则跳过
            val key = downloadKey(song.songId, song.platform)
            if (downloadManager.getDownloadedSongs().any { it.songId == key }) {
                _isPreparingDownload.value = false
                log.d { "downloadCurrentSong: ${song.songName} already downloaded, skipping" }
                return@launch
            }

            // ── B站: parse title for lyrics matching only ──────────
            val parsedTitle: ParsedBilibiliTitle? = if (song.platform == "bilibili") {
                BilibiliTitleParser.parse(song.songName, song.artist)
            } else null

            // Resolve play URL with one retry (B站/QQ URLs expire quickly)
            var url = searchManager.getPlayUrl(song.songId, song.platform, song.quality)
            if (url == null) {
                log.w { "First URL resolve failed for ${song.songName} — retrying..." }
                delay(500)
                RateLimiterRegistry.acquire(song.platform)
                url = searchManager.getPlayUrl(song.songId, song.platform, song.quality)
            }
            if (url != null) {
                // Fetch lyrics — priority: memory cache → DB cache → fresh fetch
                val lyrics = when {
                    // 1. In-memory cache (already fetched during play)
                    song.songId in cachedLyrics -> cachedLyrics[song.songId]
                    else -> {
                        // 2. DownloadRecord DB (previously downloaded with lyrics)
                        val dbLyrics = try {
                            downloadManager.getDownloadedSongs().find { it.songId == downloadKey(song.songId, song.platform) }?.lyrics
                        } catch (_: Exception) { null }
                        if (!dbLyrics.isNullOrBlank()) {
                            cachedLyrics[song.songId] = dbLyrics
                            dbLyrics
                        } else if (song.platform == "bilibili") {
                            // 3. Fresh cross-platform fetch (B站)
                            fetchBilibiliLyricsSync(song, parsedTitle)
                        } else {
                            // 3. Fresh native API fetch
                            searchManager.getLyrics(song.songId, song.platform)?.also {
                                cachedLyrics[song.songId] = it
                            }
                        }
                    }
                }

                downloadManager.download(audioUrl = url, song = song, quality = song.quality, lyrics = lyrics, coverUrl = song.coverUrl, realName = song.realName, realArtist = song.realArtist)
                    .onSuccess { path ->
                        _isPreparingDownload.value = false
                        log.i { "Downloaded → $path (lyrics: ${lyrics != null})" }
                        _downloadedSongIds.value = _downloadedSongIds.value + downloadKey(song.songId, song.platform)
                        onCacheChanged?.invoke()
                        scope.launch { userBehaviorTracker.recordDownload(song) }
                    }
                    .onFailure { e ->
                        _isPreparingDownload.value = false
                        log.e { "Download failed: ${e.message}" }
                        _errorMessage.value = "下载失败: ${e.message}"
                    }
            } else {
                _isPreparingDownload.value = false
                log.w { "Cannot resolve download URL for ${song.songName}" }
                _errorMessage.value = "无法获取音频下载地址"
            }
        }
    }

    /**
     * Synchronous version of [fetchBilibiliLyrics] for use during download.
     * @param parsedTitle Pre-parsed title from caller (avoids redundant parsing).
     */
    private suspend fun fetchBilibiliLyricsSync(song: SongMetadata, parsedTitle: ParsedBilibiliTitle? = null): String? {
        val parsed = parsedTitle ?: BilibiliTitleParser.parse(song.songName, song.artist)
        // B站标题解析的歌手名不可靠 — 只传歌名避免错误歌手拉低匹配分数
        val result = crossPlatformLyricsFetcher.fetch(
            songName = parsed.cleanSongName,
            artist = "",
            durationS = song.duration,
            isSpecialVersion = parsed.isSpecialVersion,
        )
        return when (result?.confidence) {
            LyricsMatchConfidence.HIGH -> {
                cachedLyrics[song.songId] = result.lyrics
                result.lyrics
            }
            LyricsMatchConfidence.MEDIUM -> {
                val withNote = result.lyrics + "\n\n[歌词来自网易云匹配，仅供参考]"
                cachedLyrics[song.songId] = withNote
                withNote
            }
            else -> null
        }
    }

    fun clearError() { _errorMessage.value = null }

    /** Clear playback error (user dismissed the error banner). */
    fun clearPlaybackError() { musicPlayer.clearPlaybackError() }

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
        if (_showLyrics.value) {
            val song = currentSong.value
            if (song != null) {
                // Bilibili: use cross-platform lyrics matching
                if (song.platform == "bilibili") {
                    handleBilibiliLyricsToggle(song)
                    return
                }
                // Check memory cache first
                val cached = cachedLyrics[song.songId]
                if (cached != null) {
                    _lyricsText.value = cached
                    log.d { "Lyrics cache HIT for: ${song.songName}" }
                } else {
                    _lyricsText.value = null // show loading
                    scope.launch {
                        log.d { "Lyrics cache MISS, fetching: ${song.songName}" }
                        val lyrics = searchManager.getLyrics(song.songId, song.platform)
                        if (lyrics != null) {
                            cachedLyrics[song.songId] = lyrics
                            _lyricsText.value = lyrics
                        } else {
                            _lyricsText.value = "暂无歌词"
                        }
                        log.d { "Lyrics fetch result: ${if (lyrics != null) "found (${lyrics.length} chars)" else "not found"}" }
                    }
                }
            } else {
                _lyricsText.value = "暂无歌词"
            }
        }
    }

    /** Handle lyrics toggle for Bilibili songs with cross-platform matching. */
    private fun handleBilibiliLyricsToggle(song: SongMetadata) {
        // Check if we already have cached lyrics
        val cached = cachedLyrics[song.songId]
        if (cached != null) {
            _lyricsText.value = cached
            log.d { "B站 lyrics cache HIT: ${song.songName}" }
            return
        }

        // Show loading state and start async fetch
        _lyricsText.value = "B站音频 — 正在匹配歌词..."
        scope.launch {
            log.d { "B站 lyrics: parsing title + searching Netease" }
            fetchBilibiliLyrics(song)
            // fetchBilibiliLyrics already updates _lyricsText if the sheet is open
            if (_lyricsText.value == "B站音频 — 正在匹配歌词...") {
                _lyricsText.value = "暂无匹配歌词"
            }
        }
    }

    fun dismissLyrics() { _showLyrics.value = false }

    // ══════════════════════════════════════════════════════════════
    // B站 lyrics manual sync offset
    // ══════════════════════════════════════════════════════════════

    /** Get the saved lyrics offset for a song (default 0). */
    fun getLyricsOffset(songId: String): Long = cachedLyricsOffsets[songId] ?: 0L

    /** Set and persist the lyrics offset for a song. */
    fun setLyricsOffset(songId: String, offsetMs: Long) {
        cachedLyricsOffsets[songId] = offsetMs
        _lyricsOffsetMs.value = offsetMs
        scope.launch { persistLyricsOffsets() }
    }

    /** Reset the lyrics offset for a song to 0. */
    fun resetLyricsOffset(songId: String) {
        cachedLyricsOffsets.remove(songId)
        _lyricsOffsetMs.value = 0L
        scope.launch { persistLyricsOffsets() }
    }

    /**
     * Re-search lyrics for a Bilibili song, excluding previously matched results.
     *
     * Adds the current [currentMatchedSongId] to the rejection list, clears the
     * cached lyrics, and re-triggers the cross-platform fetch. If all candidates
     * are exhausted, shows an appropriate message.
     */
    fun reSearchLyrics(songId: String) {
        val song = currentSong.value ?: return
        if (song.platform != "bilibili") return

        // Add current match to rejection set
        val rejected = rejectedLyricSongIds.getOrPut(songId) { mutableSetOf() }
        currentMatchedSongId?.let { rejected.add(it) }
        log.i { "Re-searching lyrics for $songId — excluding ${rejected.size} candidates" }

        // Clear cached lyrics so old lyrics aren't reused
        cachedLyrics.remove(songId)

        // Trigger fresh fetch with exclusions (skip downloaded lyrics — user wants a new match)
        scope.launch {
            _isReSearchingLyrics.value = true
            try {
                fetchBilibiliLyrics(song, skipDownloadedCheck = true)
            } finally {
                _isReSearchingLyrics.value = false
            }
            // If no new match was found, show exhausted message
            if (_showLyrics.value && cachedLyrics[songId] == null) {
                _lyricsText.value = if (rejected.isNotEmpty())
                    "已尝试 ${rejected.size} 个匹配结果，均不适用\n请尝试手动同步或更换音源"
                else
                    "暂无匹配歌词"
            }
        }
    }

    /**
     * Manual lyrics search with user-provided song name / artist.
     * Bypasses [BilibiliTitleParser] entirely — uses the user's input directly.
     */
    fun reSearchLyricsWith(songId: String, songName: String, artist: String) {
        val song = currentSong.value ?: return
        if (song.platform != "bilibili") return

        // Persist user-specified names to the download record (best-effort)
        scope.launch {
            try {
                downloadManager.renameSong(downloadKey(song.songId, song.platform), songName, artist)
                log.i { "Updated realName/realArtist for $songId → \"$songName\" / \"$artist\"" }
            } catch (_: Exception) { /* song may not be downloaded yet */ }
        }

        // Add current match to rejection set (user can iterate through multiple manual attempts)
        val rejected = rejectedLyricSongIds.getOrPut(songId) { mutableSetOf() }
        currentMatchedSongId?.let { rejected.add(it) }
        log.i { "Manual lyrics search for $songId — songName=\"$songName\" artist=\"$artist\" excluded=${rejected.size}" }

        cachedLyrics.remove(songId)

        scope.launch {
            _isReSearchingLyrics.value = true
            try {
                val result = crossPlatformLyricsFetcher.fetch(
                    songName = songName,
                    artist = artist,
                    durationS = song.duration,
                    excludeSongIds = rejected,
                )
                if (result != null) {
                    currentMatchedSongId = result.matchedSongId
                    val finalLyrics = when (result.confidence) {
                        LyricsMatchConfidence.HIGH -> result.lyrics
                        LyricsMatchConfidence.MEDIUM ->
                            result.lyrics + "\n\n[歌词来自网易云匹配，仅供参考]"
                        LyricsMatchConfidence.LOW -> {
                            log.d { "Manual search: low confidence — skipping" }
                            null
                        }
                    }
                    if (finalLyrics != null) {
                        cachedLyrics[songId] = finalLyrics
                        _lyricsText.value = finalLyrics
                        _showLyrics.value = true
                        persistLyricsOffsets()
                    } else {
                        _lyricsText.value = "未找到匹配歌词\n请尝试修改歌名或歌手名"
                    }
                } else {
                    _lyricsText.value = "未找到匹配歌词\n请尝试修改歌名或歌手名"
                }
            } finally {
                _isReSearchingLyrics.value = false
            }
        }
    }

    private val offsetJson = Json { ignoreUnknownKeys = true }

    private suspend fun loadLyricsOffsets() {
        try {
            val raw = settingsRepo.getRaw(SettingsKeys.LYRICS_OFFSETS)
            if (!raw.isNullOrBlank()) {
                val map: Map<String, Long> = offsetJson.decodeFromString(raw)
                cachedLyricsOffsets.putAll(map)
                log.d { "Loaded ${map.size} lyrics offsets" }
            }
        } catch (_: Exception) { /* never mind, start fresh */ }
    }

    private suspend fun persistLyricsOffsets() {
        try {
            val encoded = offsetJson.encodeToString(cachedLyricsOffsets)
            settingsRepo.putRaw(SettingsKeys.LYRICS_OFFSETS, encoded)
        } catch (e: Exception) {
            log.w { "Failed to persist lyrics offsets: ${e.message}" }
        }
    }

    override fun onCleared() {
        super.onCleared()
        log.d { "MainViewModel cleared" }
        searchManager.clearSearchState()
    }

    // ══════════════════════════════════════════════════════════════
    // Command handler
    // ══════════════════════════════════════════════════════════════

    /**
     * Execute the parsed [AiCommand] by searching, resolving, and playing.
     *
     * For [AiCommand.ACTION_SEARCH_PLAY], up to [maxRetryCandidates] search
     * results are tried. If the first result's play URL fails, the next
     * candidate is attempted automatically.
     */
    private suspend fun handleAiCommand(command: AiCommand) {
        val steps = command.toSteps()
        log.i { "Executing ${steps.size} action(s): ${steps.map { it.action }}" }

        for ((i, step) in steps.withIndex()) {
            if (i > 0) delay(500) // brief pause between steps
            executeStep(step, command)
        }
    }

    private suspend fun executeStep(step: AiStep, command: AiCommand) {
        when (step.action) {
            AiStep.ACTION_SEARCH_PLAY -> handleSearchPlay(command)
            AiStep.ACTION_DOWNLOAD_CURRENT -> {
                downloadCurrentSong()
                _chatItems.value = _chatItems.value +
                    ChatItem.AiMessage("正在下载当前歌曲...", command)
            }
            AiStep.ACTION_CONTROL -> handleControl(command)
            AiStep.ACTION_SET_QUALITY -> handleSetQuality(command)
            AiStep.ACTION_OPEN_LYRICS -> {
                log.i { "Agent: opening lyrics" }
                val handler = onOpenLyrics
                if (handler != null) {
                    handler()
                    _chatItems.value = _chatItems.value +
                        ChatItem.AiMessage("已打开歌词界面", command)
                }
            }
            AiStep.ACTION_OPEN_NOW_PLAYING -> {
                log.i { "Agent: opening now playing" }
                val handler = onOpenNowPlaying
                if (handler != null) {
                    handler()
                }
            }
            AiStep.ACTION_IMPORT_PLAYLIST -> handleImportPlaylist(step)
            AiStep.ACTION_AUTO_IMPORT_PLAYLIST -> handleAutoImportPlaylist(step)
            AiStep.ACTION_RECOMMEND -> handleRecommend(step)
            AiStep.ACTION_TOGGLE_LIKE -> {
                log.i { "Agent: toggle like" }
                toggleLike()
                _chatItems.value = _chatItems.value +
                    ChatItem.AiMessage("已切换喜欢状态", command)
            }
            AiStep.ACTION_SLEEP_TIMER -> {
                val mins = step.minutes ?: 30
                val finish = step.finishCurrentSong ?: false
                log.i { "Agent: sleep timer ${mins}min, finishCurrentSong=$finish" }
                startSleepTimer(mins, finish)
                val desc = if (finish) "定时 ${mins} 分钟，听完当前歌曲后暂停" else "定时 ${mins} 分钟"
                _chatItems.value = _chatItems.value + ChatItem.AiMessage(desc, command)
            }
            AiStep.ACTION_SET_PLAY_MODE -> {
                val mode = when (step.mode?.lowercase()) {
                    "loop" -> PlayMode.LOOP
                    "repeat_one", "repeat_one" -> PlayMode.REPEAT_ONE
                    else -> PlayMode.SEQUENTIAL
                }
                playlistManager.setPlayMode(mode)
                val label = when (mode) {
                    PlayMode.LOOP -> "列表循环"
                    PlayMode.REPEAT_ONE -> "单曲循环"
                    PlayMode.SEQUENTIAL -> "顺序播放"
                }
                log.i { "Agent: set play mode → $label" }
                _chatItems.value = _chatItems.value + ChatItem.AiMessage("播放模式已切换为: $label", command)
            }
            AiStep.ACTION_CLEAR_PLAYLIST -> {
                log.i { "Agent: clear playlist" }
                playlistManager.clearPlaylist()
                _chatItems.value = _chatItems.value + ChatItem.AiMessage("当前歌单已清空", command)
            }
            AiStep.ACTION_PLAY_RECOMMEND -> {
                handlePlayRecommend(step)
            }
            AiStep.ACTION_PLAY_N_RECOMMEND -> {
                handlePlayNRecommend(step)
            }
            AiStep.ACTION_UNKNOWN -> {
                _chatItems.value = _chatItems.value +
                    ChatItem.AiMessage("抱歉，我不太理解你的意思。试试说「播放周杰伦的晴天」或「暂停播放」。", command)
            }
            else -> log.w { "Unknown action: ${step.action}" }
        }
    }

    // ── Sub-handlers ──────────────────────────────────────────────

    private suspend fun handleSearchPlay(command: AiCommand) {
        // Use songName/artist from the step, with legacy query fallback
        val steps = command.toSteps()
        val step = steps.firstOrNull { it.action == AiStep.ACTION_SEARCH_PLAY }
        val songName = step?.songName?.takeIf { it.isNotBlank() }
        val artist = step?.artist?.takeIf { it.isNotBlank() } ?: ""
        val effectiveQuery = songName ?: step?.query

        if (effectiveQuery.isNullOrBlank()) {
            log.w { "SEARCH_PLAY with null query" }
            return
        }

        log.i { "handleSearchPlay: songName=\"$songName\", artist=\"$artist\"" }

        val handler = onSearchRequested
        if (handler != null) {
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("正在搜索: ${songName ?: effectiveQuery} ${if (artist.isNotBlank()) "- $artist" else ""}".trim(), command)
            handler(effectiveQuery, artist)

            // If a subsequent step depends on playback (open_lyrics / open_now_playing),
            // wait for the user to pick a song and playback to start.
            val searchIdx = steps.indexOfFirst { it.action == AiStep.ACTION_SEARCH_PLAY }
            val nextSteps = if (searchIdx >= 0) steps.drop(searchIdx + 1) else emptyList()
            val needsPlayback = nextSteps.any {
                it.action == AiStep.ACTION_OPEN_LYRICS || it.action == AiStep.ACTION_OPEN_NOW_PLAYING
            }
            if (needsPlayback) {
                log.i { "Waiting for user to pick a song..." }
                // Record the song that's currently playing (if any), then
                // wait for a DIFFERENT song to start — this handles the
                // case where another song was already playing.
                val previousSongId = currentSong.value?.songId
                combine(currentSong, isPlaying) { song, playing ->
                    song to playing
                }.first { (song, playing) ->
                    playing && song != null && song.songId != previousSongId
                }
                log.i { "User started playback: ${currentSong.value?.songName}" }
            }
        } else {
            log.w { "onSearchRequested not wired — search panel unavailable" }
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("搜索功能暂不可用", command)
        }
    }

    private suspend fun handleControl(command: AiCommand) {
        val action = command.controlAction
        val replyText: String = when (action) {
            "play", "resume" -> { musicPlayer.resume(); "▶️ 继续播放" }
            "pause" -> { musicPlayer.pause(); "⏸️ 已暂停" }
            "next" -> { nextTrack(); "⏭️ 下一首" }
            "prev" -> { previousTrack(); "⏮️ 上一首" }
            "stop" -> { musicPlayer.stop(); "⏹️ 已停止" }
            else -> {
                log.w { "Unknown control action: $action" }
                "未知控制指令: $action"
            }
        }
        _chatItems.value = _chatItems.value + ChatItem.AiMessage(replyText, command)
    }

    private suspend fun handleSetQuality(command: AiCommand) {
        val quality = command.quality ?: "standard"
        settingsRepo.setDefaultQuality(quality)
        log.i { "Quality set to $quality" }
        _chatItems.value = _chatItems.value +
            ChatItem.AiMessage("🎵 默认音质已设置为: $quality", command)
    }

    private suspend fun handleImportPlaylist(step: AiStep) {
        val url = step.url
        if (url.isNullOrBlank()) {
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("请提供音乐平台的歌单分享链接（支持网易云音乐、QQ音乐、酷狗音乐）", null)
            return
        }
        if (!PlaylistLinkParser.isMusicLink(url)) {
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("无法识别该链接，请提供网易云音乐、QQ音乐或酷狗的歌单分享链接", null)
            return
        }
        log.i { "import_playlist: $url" }
        _chatItems.value = _chatItems.value +
            ChatItem.AiMessage("正在导入歌单，请在确认页面选择每首歌曲的匹配版本...", null)
        onImportLinkDetected(url)
    }

    private suspend fun handleAutoImportPlaylist(step: AiStep) {
        val url = step.url
        if (url.isNullOrBlank()) {
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("请提供音乐平台的歌单分享链接（支持网易云音乐、QQ音乐、酷狗音乐）", null)
            return
        }
        if (!PlaylistLinkParser.isMusicLink(url)) {
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("无法识别该链接，请提供网易云音乐、QQ音乐或酷狗的歌单分享链接", null)
            return
        }
        log.i { "auto_import_playlist: $url" }
        _chatItems.value = _chatItems.value +
            ChatItem.AiMessage("正在自动导入歌单，将自动匹配每首歌的最佳版本...", null)
        onAutoImportLinkDetected(url)
    }

    // ── Recommendation ────────────────────────────────────────────

    /** 生成推荐列表，返回结果（供 handleRecommend 和 handlePlayRecommend 复用）。 */
    private suspend fun generateRecommendations(count: Int): List<com.example.aimusicplayer.recommendation.reason.RecommendationResult>? {
        val config = RecommendationConfig.fromSettings(settingsRepo)
        log.i { "generateRecommendations: ttlHours=${config.songIndexTtlHours}, indexSize=${localSongIndex.count()}" }

        if (config.songIndexTtlHours == 0) {
            localSongIndex.clearAll()
        }

        val indexSize = localSongIndex.count()
        if (indexSize < config.minSongIndexSize) {
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("正在补充候选曲库...", null)
            seedHotSongs()
        }

        val engine = RecommendationEngine(
            behaviorRepo = UserBehaviorRepository(userBehaviorTracker),
            songIndex = localSongIndex,
            config = config,
        )
        return engine.recommend(n = count).ifEmpty { null }
    }

    private suspend fun handleRecommend(step: AiStep) {
        val count = step.count ?: 10
        log.i { "handleRecommend: count=$count" }

        _chatItems.value = _chatItems.value +
            ChatItem.AiMessage("正在根据你的听歌习惯生成推荐...", null)

        try {
            val results = generateRecommendations(count)
            if (results == null) {
                _chatItems.value = _chatItems.value +
                    ChatItem.AiMessage("暂无推荐 —— 请先多听一些歌曲，让我了解你的口味 🎵", null)
                return
            }

            _chatItems.value = _chatItems.value +
                ChatItem.RecommendationMessage(
                    recommendations = results,
                    command = null,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
        } catch (e: Exception) {
            log.e { "handleRecommend failed: ${e.message}" }
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("推荐生成失败: ${e.message}", null)
        }
    }

    /** 搜索并播放一首推荐歌曲。返回 true 表示成功。 */
    private suspend fun tryPlayRecommendSong(
        rec: com.example.aimusicplayer.recommendation.reason.RecommendationResult,
        attemptLabel: String = "",
    ): Boolean {
        val songName = rec.songName
        val artistName = rec.artist
        val label = if (attemptLabel.isNotEmpty()) " [$attemptLabel]" else ""
        log.i { "tryPlayRecommendSong$label: \"$songName\" - \"$artistName\"" }

        val searchPlatforms = listOf("bilibili", "netease", "qq")
        for (platform in searchPlatforms) {
            try {
                RateLimiterRegistry.acquire(platform)
                delay(Random.nextLong(200, 1500))

                val queries = if (platform == "bilibili") {
                    val hasArtist = artistName.isNotBlank() && artistName != "未知歌手"
                    if (hasArtist) listOf("$songName $artistName", songName)
                    else listOf(songName)
                } else {
                    listOf("$artistName $songName")
                }

                for (q in queries) {
                    val songs = searchManager.searchOnPlatform(q, platform)
                    val playable = songs.filter { it.fee == 0 }
                    if (playable.isEmpty()) continue

                    val song = playable.first()
                    RateLimiterRegistry.acquire(platform)
                    delay(Random.nextLong(100, 800))
                    val url = searchManager.getPlayUrl(song.songId, platform, song.quality)
                    if (url.isNullOrBlank()) continue

                    val songWithMeta = if (platform == "bilibili") {
                        song.copy(realName = songName.ifBlank { null }, realArtist = artistName.ifBlank { null })
                    } else song

                    musicPlayer.play(url = url, title = songWithMeta.songName, artist = songWithMeta.artist, song = songWithMeta)
                    if (waitForPlayerReady() && currentSong.value?.songId == songWithMeta.songId) {
                        log.i { "  Now playing: ${songWithMeta.songName} (${platform})" }
                        lastRecommendSongId = songWithMeta.songId
                        scope.launch { playlistManager.addToPlaylistFirst(songWithMeta, url) }
                        removeFromSongIndex(rec.songId, rec.platform)
                        return true
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e  // 重新抛出，让外层处理取消
            } catch (e: Exception) {
                log.w { "  Search failed on $platform: ${e.message}" }
            }
        }
        return false
    }

    /** 推荐并自动播放第一首可播放的歌曲。 */
    private fun handlePlayRecommend(step: AiStep) {
        playRecommendJob?.cancel()
        recommendQueue = null  // 清除连续播放模式
        playRecommendJob = scope.launch {
            val count = step.count ?: 10
            log.i { "handlePlayRecommend: count=$count" }

            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("正在生成推荐并为你播放...", null)

            try {
                val results = generateRecommendations(count)
                if (results == null) {
                    _chatItems.value = _chatItems.value +
                        ChatItem.AiMessage("暂无推荐 —— 请先多听一些歌曲，让我了解你的口味 🎵", null)
                    return@launch
                }

                _chatItems.value = _chatItems.value +
                    ChatItem.RecommendationMessage(recommendations = results, command = null,
                        timestamp = Clock.System.now().toEpochMilliseconds())

                val maxAttempts = minOf(3, results.size)
                for (i in 0 until maxAttempts) {
                    if (!isActive) return@launch
                    if (tryPlayRecommendSong(results[i], "${i + 1}/$maxAttempts")) {
                        _chatItems.value = _chatItems.value +
                            ChatItem.AiMessage("正在播放推荐: ${results[i].songName} - ${results[i].artist}", null)
                        return@launch
                    }
                }

                _chatItems.value = _chatItems.value +
                    ChatItem.AiMessage("推荐歌曲暂时无法播放，请点击推荐卡片手动搜索", null)
            } catch (_: kotlinx.coroutines.CancellationException) {
                log.i { "handlePlayRecommend: cancelled by user action" }
            } catch (e: Exception) {
                log.e { "handlePlayRecommend failed: ${e.message}" }
                _chatItems.value = _chatItems.value +
                    ChatItem.AiMessage("推荐播放失败: ${e.message}", null)
            } finally {
                log.i { "handlePlayRecommend: coroutine ended" }
                playRecommendJob = null
            }
        }
    }

    /** 推荐并连续播放 N 首。利用自动切歌机制，每首结束后自动播放下一首推荐。 */
    private fun handlePlayNRecommend(step: AiStep) {
        playRecommendJob?.cancel()
        val count = step.count?.coerceIn(1, 20) ?: 3
        log.i { "handlePlayNRecommend: count=$count" }

        playRecommendJob = scope.launch {
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("正在生成 $count 首推荐并连续播放...", null)

            try {
                val results = generateRecommendations(count * 2)  // 多生成一些，确保够搜
                if (results == null) {
                    _chatItems.value = _chatItems.value +
                        ChatItem.AiMessage("暂无推荐 —— 请先多听一些歌曲，让我了解你的口味 🎵", null)
                    return@launch
                }

                _chatItems.value = _chatItems.value +
                    ChatItem.RecommendationMessage(recommendations = results, command = null,
                        timestamp = Clock.System.now().toEpochMilliseconds())

                // 设置连续播放队列
                recommendQueue = results
                recommendQueueIndex = 0
                recommendQueueTotal = count

                // 播放第一首
                if (tryPlayRecommendSong(results[0], "1/$count")) {
                    recommendQueueIndex = 1
                    _chatItems.value = _chatItems.value +
                        ChatItem.AiMessage("连续播放推荐 (1/$count): ${results[0].songName} - ${results[0].artist}", null)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                recommendQueue = null
                log.i { "handlePlayNRecommend: cancelled" }
            } catch (e: Exception) {
                recommendQueue = null
                log.e { "handlePlayNRecommend failed: ${e.message}" }
            } finally {
                log.i { "handlePlayNRecommend: coroutine ended" }
                if (recommendQueue == null) playRecommendJob = null
            }
        }
    }

    /** 尝试播放推荐队列中的下一首。由自动切歌调用。返回 true 表示播放成功。 */
    private suspend fun tryPlayNextRecommend(): Boolean {
        val queue = recommendQueue ?: return false
        if (recommendQueueIndex >= recommendQueueTotal) {
            log.i { "Recommend queue exhausted ($recommendQueueTotal/$recommendQueueTotal)" }
            recommendQueue = null
            playRecommendJob = null
            return false
        }
        val rec = queue[recommendQueueIndex]
        if (tryPlayRecommendSong(rec, "${recommendQueueIndex + 1}/$recommendQueueTotal")) {
            recommendQueueIndex++
            _chatItems.value = _chatItems.value +
                ChatItem.AiMessage("连续播放推荐 (${recommendQueueIndex}/$recommendQueueTotal): ${rec.songName} - ${rec.artist}", null)
            if (recommendQueueIndex >= recommendQueueTotal) {
                log.i { "Recommend queue completed: $recommendQueueTotal songs played" }
                recommendQueue = null
                playRecommendJob = null
                _chatItems.value = _chatItems.value +
                    ChatItem.AiMessage("$recommendQueueTotal 首推荐歌曲播放完毕 🎵", null)
            }
            return true
        }
        // 当前这首搜不到，跳过试下一首
        recommendQueueIndex++
        return tryPlayNextRecommend()
    }

    /**
     * 根据用户喜好 + 热门兜底填充候选池。
     * 优先搜索用户常听的歌手，无历史时用固定热门列表兜底。
     */
    private suspend fun seedHotSongs() {
        // 清除旧的 seed 数据，让候选池随用户喜好更新
        localSongIndex.clearAll()

        // 获取用户 top 5 歌手
        val repo = UserBehaviorRepository(userBehaviorTracker)
        val userArtists = repo.getTopArtists(5)
        val likedArtists = repo.getTopLikedArtists(3)

        // 合并去重：用户常听 + 用户喜欢 + 热门兜底
        val fallbackArtists = listOf("周杰伦", "林俊杰", "陈奕迅", "邓紫棋")
        val seedArtists = (userArtists + likedArtists + fallbackArtists).distinct()

        for (artist in seedArtists) {
            try {
                // 网易云搜 5 首
                val neteaseResults = searchManager.searchOnPlatform(artist, "netease")
                for (song in neteaseResults.take(5)) {
                    localSongIndex.insertOrIgnore(
                        com.example.aimusicplayer.behavior.SongIndexEntry(
                            songId = song.songId,
                            platform = song.platform,
                            songName = song.songName,
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.duration.toLong() * 1000,
                            coverUrl = null,
                            playCountGlobal = 1000L,
                            source = "search_seed",
                        )
                    )
                }
                // QQ 音乐搜 5 首
                val qqResults = searchManager.searchOnPlatform(artist, "qq")
                for (song in qqResults.take(5)) {
                    localSongIndex.insertOrIgnore(
                        com.example.aimusicplayer.behavior.SongIndexEntry(
                            songId = song.songId,
                            platform = song.platform,
                            songName = song.songName,
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.duration.toLong() * 1000,
                            coverUrl = null,
                            playCountGlobal = 1000L,
                            source = "search_seed",
                        )
                    )
                }
            } catch (_: Exception) { /* 单个歌手失败不影响整体 */ }
        }
        log.i { "seedHotSongs: index size now = ${localSongIndex.count()}" }
    }

    // ── Core: resolve + play with retry + truncation fallback ─────

    /**
     * Try to resolve and play the search result at [index].
     *
     * ## Two-phase fallback on truncation
     * When the player reports a duration that indicates VIP truncation (30s):
     * 1. **Phase 1** — try other music platforms (QQ → Kugou) for the same song
     * 2. **Phase 2** — Bilibili guaranteed search with multi-strategy crawl
     * 3. **Extreme case** — all platforms + B站 failed → stop the truncated
     *    playback and report "找不到完整歌曲"
     *
     * @return `true` if a full-length track started playing, `false` if all attempts failed.
     */
    private suspend fun playSearchResultAtIndex(index: Int): Boolean {
        if (index >= lastSearchResults.size) return false
        val quality = settingsRepo.getDefaultQuality()

        for (attempt in index until (index + maxRetryCandidates).coerceAtMost(lastSearchResults.size)) {
            val song = lastSearchResults[attempt]
            log.d { "Attempt ${attempt + 1}/${lastSearchResults.size}: ${song.songName} (${song.platform})" }

            val url = searchManager.getPlayUrl(song.songId, song.platform, quality)
            if (url.isNullOrBlank()) {
                log.w { "  ⚠️ No play URL for ${song.songName} — trying next" }
                if (attempt == index) {
                    _chatItems.value = _chatItems.value +
                        ChatItem.SystemMessage("⚠️ ${song.songName} 音源失效，尝试备选...")
                }
                continue
            }

            try {
                musicPlayer.play(url = url, title = song.songName, artist = song.artist, song = song)

                // ── Wait for player to report duration (poll, max 5 s) ──
                if (!waitForPlayerReady()) {
                    log.w { "  ⚠️ Player not ready within timeout for ${song.songName}" }
                    if (attempt > index) {
                        _chatItems.value = _chatItems.value +
                            ChatItem.SystemMessage("⚠️ ${song.songName} 加载超时，尝试备选...")
                    }
                    continue
                }

                val isNowPlaying = musicPlayer.isPlaying.value

                if (isNowPlaying) {
                    currentSongIndex = attempt
                    log.i { "  ✅ Playing: ${song.songName}" }
                    fetchAndCacheLyrics(song)

                    // Auto-add to current playlist
                    scope.launch {
                        playlistManager.addToPlaylist(song, url)
                    }
                    // 30s history timer
                    scope.launch {
                        delay(30_000)
                        if (musicPlayer.isPlaying.value &&
                            musicPlayer.currentSong.value?.songId == song.songId) {
                            playlistManager.addToHistory(song, url)
                        }
                    }

                    return true
                } else {
                    // Check for playback errors
                    val error = musicPlayer.playbackError.value
                    log.w { "  ⚠️ Player not started for ${song.songName} — error=$error" }
                    if (attempt > index) {
                        _chatItems.value = _chatItems.value +
                            ChatItem.SystemMessage("⚠️ ${song.songName} 解码失败，尝试备选...")
                    }
                }
            } catch (e: Exception) {
                log.e { "  ❌ Play exception for ${song.songName}: ${e.message}" }
            }
        }

        // All candidates exhausted
        log.e { "All $maxRetryCandidates candidates failed to play" }
        _chatItems.value = _chatItems.value +
            ChatItem.SystemMessage("❌ 所有音源均无法播放，请稍后重试")
        _errorMessage.value = "音源均失效，已自动尝试备选"
        return false
    }

    /**
     * Fetch lyrics in the background and cache them in memory.
     *
     * Called automatically after a song starts playing successfully.
     * Lyrics are stored in [cachedLyrics] so subsequent [toggleLyrics]
     * calls return instantly without a network round-trip.
     */
    private fun fetchAndCacheLyrics(song: SongMetadata) {
        if (song.songId in cachedLyrics) return // already cached

        // Bilibili: try cross-platform lyrics matching
        if (song.platform == "bilibili") {
            scope.launch { fetchBilibiliLyrics(song) }
            return
        }

        scope.launch {
            log.d { "Auto-fetching lyrics: ${song.songName} (${song.platform})" }
            val lyrics = searchManager.getLyrics(song.songId, song.platform)
            if (lyrics != null) {
                cachedLyrics[song.songId] = lyrics
                log.d { "Lyrics cached: ${song.songName} (${lyrics.length} chars)" }
            }
            // Also update the UI if the lyrics sheet is currently open for this song
            if (_showLyrics.value && currentSong.value?.songId == song.songId) {
                _lyricsText.value = lyrics ?: "暂无歌词"
            }
        }
    }

    /**
     * Fetch lyrics for a Bilibili audio track by cross-referencing Netease.
     *
     * Uses [BilibiliTitleParser] to extract a real artist and clean song name
     * from the raw video title, then searches Netease via [CrossPlatformLyricsFetcher].
     * Only caches results with HIGH or MEDIUM confidence.
     */
    private suspend fun fetchBilibiliLyrics(song: SongMetadata, skipDownloadedCheck: Boolean = false) {
        log.d { "Cross-platform lyrics: ${song.songName} (bilibili)" }

        // ── Check downloaded lyrics first (offline support) ──────────
        // Skip this check when re-searching — the user wants a fresh match.
        if (!skipDownloadedCheck) {
            try {
                val downloaded = downloadManager.getDownloadedSongs().find { it.songId == downloadKey(song.songId, song.platform) }
                if (!downloaded?.lyrics.isNullOrBlank()) {
                    cachedLyrics[song.songId] = downloaded!!.lyrics
                    log.i { "B站 lyrics loaded from download: ${song.songName}" }
                    if (_showLyrics.value && currentSong.value?.songId == song.songId) {
                        _lyricsText.value = downloaded.lyrics
                    }
                    return
                }
            } catch (_: Exception) { /* fall through to network lookup */ }
        }

        // ── Network lookup via Netease ───────────────────────────────
        val parsed = BilibiliTitleParser.parse(song.songName, song.artist)
        // B站标题解析的歌手名不可靠 — 只传歌名避免错误歌手拉低匹配分数
        log.d { "Parsed B站 title: songName=\"${parsed.cleanSongName}\", artist=\"${parsed.extractedArtist}\" (fromTitle=${parsed.artistFromTitle}), special=${parsed.isSpecialVersion}" }

        // Get rejected Netease songIds for this B站 track
        val excluded = rejectedLyricSongIds[song.songId] ?: emptySet()

        val result = crossPlatformLyricsFetcher.fetch(
            songName = parsed.cleanSongName,
            artist = "",
            durationS = song.duration,
            isSpecialVersion = parsed.isSpecialVersion,
            excludeSongIds = excluded,
        )

        if (result != null) {
            currentMatchedSongId = result.matchedSongId
            val finalLyrics = when (result.confidence) {
                LyricsMatchConfidence.HIGH -> result.lyrics
                LyricsMatchConfidence.MEDIUM ->
                    result.lyrics + "\n\n[歌词来自网易云匹配，仅供参考]"
                LyricsMatchConfidence.LOW -> {
                    log.d { "Low confidence — skipping lyrics" }
                    null
                }
            }
            if (finalLyrics != null) {
                cachedLyrics[song.songId] = finalLyrics
                log.i { "B站 lyrics matched: ${result.matchedSongName} (${result.confidence})" }
            }
        } else {
            currentMatchedSongId = null
        }

        // Update UI if lyrics sheet is currently open for this song
        if (_showLyrics.value && currentSong.value?.songId == song.songId) {
            _lyricsText.value = cachedLyrics[song.songId] ?: "暂无匹配歌词"
        }
    }

    /**
     * Poll the player until it reports a valid duration (> 0) and is playing,
     * or until [timeoutMs] elapses.
     *
     * Also checks [MusicPlayer.playbackError] on each poll — returns false
     * immediately if the player reports an error (e.g. 403, 404, decode failure).
     *
     * @param timeoutMs Maximum time to wait (default 5 s)
     * @return `true` if the player reported a valid duration, `false` on timeout
     */
    private suspend fun waitForPlayerReady(timeoutMs: Long = 5000): Boolean {
        val pollInterval = 200L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            val err = musicPlayer.playbackError.value
            if (err != null) {
                log.w { "Player error detected: $err" }
                return false
            }
            val dur = musicPlayer.duration.value
            val playing = musicPlayer.isPlaying.value
            if (dur > 0 && playing) return true
            delay(pollInterval)
            elapsed += pollInterval
        }
        return musicPlayer.duration.value > 0 && musicPlayer.isPlaying.value
    }

    /** Human-readable platform name for user-facing messages. */
    private fun platformLabel(key: String): String = when (key) {
        "netease" -> "网易云音乐"
        "qq" -> "QQ音乐"
        "kugou" -> "酷狗音乐"
        "bilibili" -> "B站"
        else -> key
    }
}
