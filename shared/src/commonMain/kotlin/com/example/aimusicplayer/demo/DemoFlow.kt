package com.example.aimusicplayer.demo

import co.touchlab.kermit.Logger
import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.ai.AiCommand
import com.example.aimusicplayer.ai.AiResult
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.music.downloadKey
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.SearchState
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.settings.SettingsRepository
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════
// DemoFlow — end-to-end integration demo
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Demonstrates the complete pipeline from user text input to audio playback.
 *
 * ## Covered scenarios
 * | Step | Happy path                                          | Error path                      |
 * |------|-----------------------------------------------------|---------------------------------|
 * | 1    | User inputs natural language                        | Empty input → ignored           |
 * | 2    | AiAssistant parses to [AiCommand]                   | ConfigMissing → redirect        |
 * | 3    | MusicSearchManager queries cache, then API          | All platforms fail → no results |
 * | 4    | getPlayUrl resolves stream URL                      | URL expired → auto-retry        |
 * | 5    | MusicPlayer starts playing                          | Decode fail → next result       |
 * | 6    | DownloadManager optionally caches for offline use    | Network fail during download    |
 *
 * ## Usage
 * ```kotlin
 * val demo = DemoFlow(assistant, search, player, downloader, settings)
 * // Run full pipeline:
 * demo.runFullPipeline("播放周杰伦的夜曲")
 * // Or step through manually:
 * val result = demo.step1_parseUserInput("播放周杰伦的夜曲")
 * ```
 *
 * All steps produce structured [FlowStep] results and [Kermit][Logger] logs.
 */
class DemoFlow(
    private val aiAssistant: AiAssistant,
    private val searchManager: MusicSearchManager,
    private val musicPlayer: MusicPlayer,
    private val downloadManager: DownloadManager,
    private val settingsRepo: SettingsRepository,
) {
    private val log = Logger.withTag("DemoFlow")

    // ══════════════════════════════════════════════════════════════
    // Full pipeline
    // ══════════════════════════════════════════════════════════════

    /**
     * Run the complete pipeline: parse → search → resolve → play → cache.
     *
     * @return List of [FlowStepResult] documenting every stage, including failures.
     */
    suspend fun runFullPipeline(
        input: String,
        quality: String = "standard",
        shouldDownload: Boolean = false,
    ): List<FlowStepResult> {
        val results = mutableListOf<FlowStepResult>()
        log.i { "═══ Pipeline START — input: \"$input\", quality: $quality ═══" }

        // ── Step 1: Parse ──────────────────────────────────────────
        val parseResult = step1_parseUserInput(input)
        results.add(parseResult)
        if (parseResult !is FlowStepResult.Success) {
            log.e { "Pipeline ABORTED at Step 1: ${parseResult.summary}" }
            return results
        }
        val command = (parseResult as FlowStepResult.Success).command
            ?: return results.also {
                log.e { "Pipeline ABORTED: null command from AI" }
            }

        // ── Step 2: Search ─────────────────────────────────────────
        val searchResult = step2_searchMusic(command, quality)
        results.add(searchResult)
        if (searchResult !is FlowStepResult.Success) {
            log.e { "Pipeline ABORTED at Step 2: ${searchResult.summary}" }
            return results
        }
        val songs = (searchResult as FlowStepResult.Success).songs

        // ── Step 3: Resolve play URL (with retry) ──────────────────
        val resolveResult = step3_resolvePlayUrl(songs, quality)
        results.add(resolveResult)
        if (resolveResult !is FlowStepResult.Success) {
            log.e { "Pipeline ABORTED at Step 3: ${resolveResult.summary}" }
            return results
        }
        val playTarget = (resolveResult as FlowStepResult.Success).playTarget
            ?: return results.also {
                log.e { "Pipeline ABORTED: null playTarget" }
            }
        val (song, audioUrl) = playTarget

        // ── Step 4: Play ───────────────────────────────────────────
        val playResult = step4_playAudio(song, audioUrl)
        results.add(playResult)

        // ── Step 5: Download (optional) ────────────────────────────
        if (shouldDownload && playResult is FlowStepResult.Success) {
            val downloadResult = step5_download(song, audioUrl, quality)
            results.add(downloadResult)
        }

        log.i { "═══ Pipeline END — ${results.size} steps completed ═══" }
        return results
    }

    // ══════════════════════════════════════════════════════════════
    // Step 1: Parse user input via AI
    // ══════════════════════════════════════════════════════════════

    suspend fun step1_parseUserInput(input: String): FlowStepResult {
        log.d { "[Step 1] Parsing user input..." }

        if (input.isBlank()) {
            log.w { "[Step 1] Empty input — rejected" }
            return FlowStepResult.Failure("输入为空")
        }

        return when (val result = aiAssistant.sendMessage(input)) {
            is AiResult.Success -> {
                log.i { "[Step 1] ✅ AI parsed → action=${result.command.action}, query=${result.command.query}" }
                FlowStepResult.Success(command = result.command)
            }
            is AiResult.ConfigMissing -> {
                log.e { "[Step 1] ❌ API Key not configured" }
                FlowStepResult.Failure("API Key 未配置 — 请前往设置页面输入有效的 API Key")
            }
            is AiResult.AuthError -> {
                log.e { "[Step 1] ❌ API Key invalid (401) — key auto-cleared" }
                FlowStepResult.Failure("API Key 无效，已被自动清除 — 请重新配置")
            }
            is AiResult.TextOnly -> {
                log.w { "[Step 1] ⚠️ AI returned text only (no command): ${result.text}" }
                FlowStepResult.Failure("AI 无法解析为指令: ${result.text}")
            }
            is AiResult.Error -> {
                log.e { "[Step 1] ❌ AI error: ${result.message}" }
                log.e { "[Step 1]    Cause: ${result.cause?.stackTraceToString()}" }
                FlowStepResult.Failure("AI 调用失败: ${result.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Step 2: Search across platforms
    // ══════════════════════════════════════════════════════════════

    suspend fun step2_searchMusic(
        command: AiCommand,
        quality: String,
    ): FlowStepResult {
        val query = command.query ?: return FlowStepResult.Failure("AI 未提供搜索词")
        log.d { "[Step 2] Searching: \"$query\"" }

        val results = searchManager.search(query, quality)

        // Wait for search state to settle
        delay(100)

        return when (val state = searchManager.searchState.value) {
            is SearchState.Results -> {
                log.i { "[Step 2] ✅ Found ${state.songs.size} results from ${state.platform}" }
                for ((i, song) in state.songs.withIndex()) {
                    log.d { "[Step 2]   [$i] ${song.songName} — ${song.artist} (${song.platform})" }
                }
                FlowStepResult.Success(songs = state.songs)
            }
            is SearchState.NoResults -> {
                log.w { "[Step 2] ❌ No results for \"$query\" — all platforms exhausted" }
                FlowStepResult.Failure("未找到歌曲: $query")
            }
            is SearchState.Error -> {
                log.e { "[Step 2] ❌ Search error: ${state.message}" }
                FlowStepResult.Failure("搜索出错: ${state.message}")
            }
            else -> {
                log.w { "[Step 2] ❌ Unexpected state: $state" }
                FlowStepResult.Failure("搜索未完成")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Step 3: Resolve playable URL (with retry on next platform)
    // ══════════════════════════════════════════════════════════════

    suspend fun step3_resolvePlayUrl(
        songs: List<SongMetadata>,
        quality: String,
        maxRetries: Int = 3,
    ): FlowStepResult {
        log.d { "[Step 3] Resolving play URL — ${songs.size} candidates, maxRetries=$maxRetries" }

        val candidates = songs.take(maxRetries)
        for ((i, song) in candidates.withIndex()) {
            log.d { "[Step 3]   Try ${i + 1}/${candidates.size}: ${song.songName} (${song.platform})" }

            val url = searchManager.getPlayUrl(song.songId, song.platform, quality)
            if (!url.isNullOrBlank()) {
                log.i { "[Step 3] ✅ URL resolved: ${url.take(80)}..." }
                return FlowStepResult.Success(
                    playTarget = Pair(song, url),
                    songs = songs,
                )
            }
            log.w { "[Step 3]   ⚠️ URL expired or unavailable for ${song.songName} — trying next" }
        }

        log.e { "[Step 3] ❌ All $maxRetries candidates failed to resolve" }
        return FlowStepResult.Failure("无法获取音频播放地址 — 已尝试 ${candidates.size} 个音源")
    }

    // ══════════════════════════════════════════════════════════════
    // Step 4: Start playback
    // ══════════════════════════════════════════════════════════════

    suspend fun step4_playAudio(
        song: SongMetadata,
        audioUrl: String,
    ): FlowStepResult {
        log.d { "[Step 4] Starting playback: ${song.songName}" }

        return try {
            musicPlayer.play(url = audioUrl, title = song.songName, artist = song.artist, song = song)
            // Brief wait for player state to stabilise
            delay(500)
            val playing = musicPlayer.isPlaying.value
            if (playing) {
                log.i { "[Step 4] ✅ Now playing: ${song.songName} — ${song.artist}" }
                FlowStepResult.Success(
                    command = null,
                    songs = listOf(song),
                    playTarget = Pair(song, audioUrl),
                )
            } else {
                log.w { "[Step 4] ⚠️ play() called but isPlaying=false — possible decode issue" }
                FlowStepResult.Failure("音频可能无法解码: ${song.songName}")
            }
        } catch (e: Exception) {
            log.e { "[Step 4] ❌ Playback failed: ${e.message}" }
            log.e { "[Step 4]    ${e.stackTraceToString()}" }
            FlowStepResult.Failure("播放失败: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Step 5: Download for offline playback
    // ══════════════════════════════════════════════════════════════

    suspend fun step5_download(
        song: SongMetadata,
        audioUrl: String,
        quality: String,
    ): FlowStepResult {
        log.d { "[Step 5] Downloading: ${song.songName}" }

        // 已下载则跳过
        if (downloadManager.getDownloadedSongs().any { it.songId == downloadKey(song.songId, song.platform) }) {
            log.i { "[Step 5] ⏭ ${song.songName} already downloaded, skipping" }
            return FlowStepResult.Success(
                command = null,
                songs = listOf(song),
                playTarget = Pair(song, audioUrl),
            )
        }

        val result = downloadManager.download(
            audioUrl = audioUrl,
            song = song,
            quality = quality,
        )

        return result.fold(
            onSuccess = { localPath ->
                log.i { "[Step 5] ✅ Downloaded → $localPath" }
                FlowStepResult.Success(
                    command = null,
                    songs = listOf(song),
                    playTarget = Pair(song, audioUrl),
                )
            },
            onFailure = { e ->
                log.e { "[Step 5] ❌ Download failed: ${e.message}" }
                FlowStepResult.Failure("下载失败: ${e.message}")
            },
        )
    }

    // ══════════════════════════════════════════════════════════════
    // Utility: log current state
    // ══════════════════════════════════════════════════════════════

    /** Dump diagnostic information about all components. */
    suspend fun dumpState() {
        val apiKey = settingsRepo.getApiKey()
        val baseUrl = settingsRepo.getBaseUrl()
        log.i {
            """
            ╔══════════════════ DIAGNOSTIC ══════════════════╗
            ║ Player  isPlaying = ${musicPlayer.isPlaying.value}
            ║         position  = ${musicPlayer.currentPosition.value}ms
            ║         error     = ${musicPlayer.playbackError.value}
            ║ Search  state     = ${searchManager.searchState.value}
            ║ Settings apiKey   = ${if (apiKey.length >= 10) "configured" else "MISSING"}
            ║         baseUrl   = $baseUrl
            ╚════════════════════════════════════════════════╝
            """.trimIndent()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Flow step result model
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Outcome of a single pipeline step.
 */
sealed class FlowStepResult {
    /** Human-readable summary of this step. */
    abstract val summary: String

    /** Step completed successfully. */
    data class Success(
        /** Parsed AI command (Step 1). */
        val command: AiCommand? = null,
        /** Search results (Steps 2–5). */
        val songs: List<SongMetadata> = emptyList(),
        /** Resolved song + audio URL (Steps 3–5). */
        val playTarget: Pair<SongMetadata, String>? = null,
    ) : FlowStepResult() {
        override val summary: String = buildString {
            command?.let { append("command=${it.action}") }
            if (songs.isNotEmpty()) append(" songs=${songs.size}")
            playTarget?.let { append(" nowPlaying=${it.first.songName}") }
        }
    }

    /** Step failed with an explanation. */
    data class Failure(val reason: String) : FlowStepResult() {
        override val summary: String = "FAILED: $reason"
    }
}
