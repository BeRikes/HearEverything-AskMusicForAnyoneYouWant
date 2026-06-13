package com.example.aimusicplayer.ui.search

import co.touchlab.kermit.Logger
import com.example.aimusicplayer.behavior.UserBehaviorTracker
import com.example.aimusicplayer.cache.CacheRepository
import com.example.aimusicplayer.cache.CacheRepositoryImpl
import com.example.aimusicplayer.cache.Md5Utils
import com.example.aimusicplayer.cache.SongCacheEntity
import com.example.aimusicplayer.music.BilibiliTitleParser
import com.example.aimusicplayer.music.CrossPlatformLyricsFetcher
import com.example.aimusicplayer.music.ParsedBilibiliTitle
import com.example.aimusicplayer.music.DownloadManagerProvider
import com.example.aimusicplayer.music.DownloadState
import com.example.aimusicplayer.music.downloadKey
import com.example.aimusicplayer.music.LyricsMatchConfidence
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.music.SearchState
import com.example.aimusicplayer.network.RateLimiterRegistry
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.player.MusicPlayerProvider
import com.example.aimusicplayer.viewmodel.ViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════
// SearchViewModel — independent music search with cache-first strategy
// ═══════════════════════════════════════════════════════════════════════════

/**
 * ViewModel for the Search tab.
 *
 * ## Search flow
 * ```
 * User input (songName + artist) →
 *   1. Query local cache (all platforms, by deterministic ID)
 *   2. Cache HIT + not expired → resolve play URL → play
 *   3. Cache MISS → search platforms in order (Netease→QQ→Kugou→Bilibili)
 *      - Public API → get audio direct link
 *      - Rate limited via [RateLimiterRegistry] + random jitter
 *   4. Cache ALL successful results for next lookup
 *   5. Return results to UI
 * ```
 *
 * This ViewModel is fully independent of the AI assistant — users can search
 * and play music without configuring an API key.
 *
 * @param musicPlayer              Cross-platform audio player
 * @param searchManager            Multi-platform search orchestrator
 * @param cacheRepository          Local SQLite cache for song metadata + URLs
 * @param downloadManager          File download manager for offline playback
 */
class SearchViewModel(
    private val musicPlayer: MusicPlayerProvider,
    private val searchManager: MusicSearchManager,
    private val cacheRepository: CacheRepository,
    private val downloadManager: DownloadManagerProvider,
    private val playlistManager: PlaylistManager,
    private val behaviorTracker: UserBehaviorTracker,
) : ViewModel() {

    private val log = Logger.withTag("SearchViewModel")
    private val crossPlatformLyricsFetcher = CrossPlatformLyricsFetcher(searchManager)

    /** Platform search order (deterministic fallback). */
    private val platformOrder = listOf("bilibili", "netease", "qq")

    // ── Input state ──────────────────────────────────────────────────

    private val _songName = MutableStateFlow("")
    val songName: StateFlow<String> = _songName.asStateFlow()

    private val _artist = MutableStateFlow("")
    val artist: StateFlow<String> = _artist.asStateFlow()

    // ── Search state ─────────────────────────────────────────────────

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    /** Which step the search is currently on (for progress display). */
    private val _searchStep = MutableStateFlow<String?>(null)
    val searchStep: StateFlow<String?> = _searchStep.asStateFlow()

    /** Has the user performed at least one search? */
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    // ── Player state (delegated) ─────────────────────────────────────

    val currentSong: StateFlow<SongMetadata?> = musicPlayer.currentSong
    val isPlaying: StateFlow<Boolean> = musicPlayer.isPlaying
    val currentPosition: StateFlow<Long> = musicPlayer.currentPosition
    val duration: StateFlow<Long> = musicPlayer.duration

    // ── Download state ───────────────────────────────────────────────

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.allDownloads

    /** Callback invoked when a download completes or fails (for cache size update). */
    var onCacheChanged: (() -> Unit)? = null

    /** Set of songIds currently being prepared for download (URL resolve + lyrics fetch). */
    private val _preparingDownloads = MutableStateFlow<Set<String>>(emptySet())
    val preparingDownloads: StateFlow<Set<String>> = _preparingDownloads.asStateFlow()

    /** Set of songIds whose download failed. Cleared on new download attempt. */
    private val _downloadErrors = MutableStateFlow<Set<String>>(emptySet())
    val downloadErrors: StateFlow<Set<String>> = _downloadErrors.asStateFlow()

    // ── Playlist state ───────────────────────────────────────────────

    private val _playlistSongIds = MutableStateFlow<Set<String>>(emptySet())
    val playlistSongIds: StateFlow<Set<String>> = _playlistSongIds.asStateFlow()

    // ── Panel visibility (survives tab switches) ──────────────────────

    private val _isSearchExpanded = MutableStateFlow(false)
    val isSearchExpanded: StateFlow<Boolean> = _isSearchExpanded.asStateFlow()

    fun expandSearch() { _isSearchExpanded.value = true }
    fun collapseSearch() { _isSearchExpanded.value = false }

    // ── Stop search ───────────────────────────────────────────────────

    private var searchJob: kotlinx.coroutines.Job? = null

    /** Cancel the ongoing search without clearing already-found results. */
    fun stopSearch() {
        searchJob?.cancel()
        searchJob = null
        _isSearching.value = false
        _searchStep.value = if (_searchResults.value.isNotEmpty()) {
            "已停止搜索（保留 ${_searchResults.value.size} 首结果）"
        } else {
            "已停止搜索"
        }
        log.i { "Search stopped by user — ${_searchResults.value.size} results kept" }
    }

    // ── Public actions ───────────────────────────────────────────────

    fun onSongNameChanged(value: String) { _songName.value = value }
    fun onArtistChanged(value: String) { _artist.value = value }

    /**
     * Execute the search with the cache-first strategy described in the
     * class-level documentation.
     *
     * Called when the user taps the search button or presses Enter.
     */
    fun search() {
        val song = _songName.value.trim()
        val art = _artist.value.trim()

        if (song.isBlank()) {
            _searchError.value = "请输入歌曲名"
            return
        }

        // Cancel any previous search before starting a new one
        searchJob?.cancel()

        _isSearching.value = true
        _searchError.value = null
        _searchResults.value = emptyList()
        _hasSearched.value = true

        val query = if (art.isNotBlank()) "$song $art" else song
        log.i { "Search: song=\"$song\", artist=\"$art\", query=\"$query\"" }

        searchJob = scope.launch {
            try {
                // ── Step 1: Check local cache across all platforms ───
                _searchStep.value = "正在查询本地缓存..."
                val cachedResults = checkAllPlatformCaches(song, art)
                if (cachedResults.isNotEmpty()) {
                    log.i { "Cache HIT: ${cachedResults.size} results from cache" }
                    _searchResults.value = cachedResults
                    _isSearching.value = false
                    _searchStep.value = null
                    return@launch
                }

                // ── Step 2: Cache miss → search platforms (streaming) ─
                searchAcrossPlatforms(song, art, query)

                if (_searchResults.value.isEmpty()) {
                    _searchError.value = "未找到歌曲 — 已尝试所有平台"
                    log.w { "No results for query=\"$query\"" }
                } else {
                    log.i { "Search complete: ${_searchResults.value.size} results" }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User stopped the search — results are already preserved, just clean up
                log.i { "Search cancelled by user" }
            } catch (e: Exception) {
                log.e { "Search exception: ${e.message}" }
                _searchError.value = "搜索出错: ${e.message}"
            } finally {
                _isSearching.value = false
                _searchStep.value = null
                searchJob = null
            }
        }
    }

    /**
     * Play the selected search result.
     *
     * The URL was already resolved and validated during search —
     * VIP/paid songs (fee>0) are filtered at that stage for all platforms.
     */
    fun playResult(item: SearchResultItem) {
        scope.launch {
            val song = item.song
            log.i { "playResult: ${song.songName} — ${song.artist} (${song.platform})" }

            // ── Step 0: Check local download first ──
            val localFile = downloadManager.getDownloadedSongs()
                .find { it.songId == downloadKey(song.songId, song.platform) }?.localPath
            if (localFile != null) {
                musicPlayer.play(url = localFile, title = song.songName, artist = song.artist, song = song)
                // Auto-add to current playlist (same as network path)
                scope.launch {
                    playlistManager.addToPlaylistFirst(song, localFile)
                    _playlistSongIds.value = _playlistSongIds.value + "${song.songId}|${song.platform}"
                }
                scope.launch {
                    delay(30_000)
                    if (musicPlayer.isPlaying.value && musicPlayer.currentSong.value?.songId == song.songId) {
                        playlistManager.addToHistory(song, localFile)
                    }
                }
                return@launch
            }

            // ── Step 1: Use the URL already resolved during search ──
            // QQ Music CDN URLs expire in seconds — skip cache, always resolve fresh
            var url: String? = if (song.platform == "qq") null else item.cachedUrl
            var played = false

            if (!url.isNullOrBlank()) {
                musicPlayer.play(url = url, title = song.songName, artist = song.artist, song = song)
                if (waitForPlayerReady()) {
                    log.i { "Now playing: ${song.songName}" }

                    // Auto-add to current playlist
                    scope.launch {
                        playlistManager.addToPlaylistFirst(song, url)
                        _playlistSongIds.value = _playlistSongIds.value + "${song.songId}|${song.platform}"
                    }
                    // 30s history timer
                    scope.launch {
                        delay(30_000)
                        if (musicPlayer.isPlaying.value && musicPlayer.currentSong.value?.songId == song.songId) {
                            playlistManager.addToHistory(song, url)
                        }
                    }

                    return@launch
                }
            }

            // ── Step 2: Resolve fresh URL (fallback if cached failed) ──
            RateLimiterRegistry.acquire(song.platform)
            url = searchManager.getPlayUrl(song.songId, song.platform, song.quality)

            if (!url.isNullOrBlank()) {
                // QQ: play first, cache after — don't let SQLite write delay the HTTP request
                if (song.platform != "qq") {
                    cacheSong(song, url)
                }
                musicPlayer.play(url = url, title = song.songName, artist = song.artist, song = song)
                if (waitForPlayerReady()) {
                    played = true
                    if (song.platform == "qq") {
                        cacheSong(song, url) // cache after successful playback start
                    }
                } else {
                    played = musicPlayer.isPlaying.value
                    // QQ: even if not fully ready, check if duration suggests playback
                    if (song.platform == "qq" && played) {
                        cacheSong(song, url)
                    }
                }
            }

            // ── Step 3: Handle failure ──────────────────────────────
            if (!played) {
                log.w { "Playback failed for ${song.songName}" }
                if (url.isNullOrBlank()) _searchError.value = "无法获取播放地址: ${song.songName}"
                else _searchError.value = "播放失败: ${song.songName}"
                tryNextResult(item)
            } else {
                log.i { "Now playing (fresh): ${song.songName}" }

                // Auto-add to current playlist
                scope.launch {
                    playlistManager.addToPlaylist(song, url)
                }
                // 30s history timer
                scope.launch {
                    delay(30_000)
                    if (musicPlayer.isPlaying.value && musicPlayer.currentSong.value?.songId == song.songId) {
                        playlistManager.addToHistory(song, url)
                    }
                }
            }
        }
    }

    /**
     * Poll the player until it reports a valid duration (> 0) and is playing,
     * or until [timeoutMs] elapses.
     *
     * Also checks [MusicPlayer.playbackError] on each poll — returns false
     * immediately if the player reports an error (e.g. 403, 404, decode failure).
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

    /** Human-readable platform name. */
    private fun platformLabel(key: String): String = when (key) {
        "netease" -> "网易云音乐"
        "qq" -> "QQ音乐"
        "kugou" -> "酷狗音乐"
        "bilibili" -> "B站"
        else -> key
    }

    /** Download the selected song for offline playback. */
    fun downloadResult(item: SearchResultItem) {
        // Show spinner immediately, clear previous error
        _preparingDownloads.value = _preparingDownloads.value + item.song.songId
        _downloadErrors.value = _downloadErrors.value - item.song.songId
        scope.launch {
            val song = item.song
            log.i { "downloadResult: ${song.songName}" }

            // 已下载则跳过
            val downloadedIds = downloadManager.getDownloadedSongs().map { it.songId }.toSet()
            if (downloadKey(song.songId, song.platform) in downloadedIds) {
                _preparingDownloads.value = _preparingDownloads.value - song.songId
                log.i { "downloadResult: ${song.songName} already downloaded, skipping" }
                return@launch
            }

            // ── B站: parse title for lyrics matching only ────────────
            val parsedTitle: ParsedBilibiliTitle? = if (song.platform == "bilibili") {
                BilibiliTitleParser.parse(song.songName, song.artist)
            } else null

            // ── Fetch lyrics (B站 URL expires in seconds) ───────────
            val lyrics = fetchLyricsForDownload(song, parsedTitle)
            if (lyrics != null) {
                log.i { "Lyrics fetched for download: ${song.songName} (${lyrics.length} chars)" }
            }

            // ── Resolve URL right before download (fresh for B站/QQ) ─
            val useCached = song.platform != "bilibili" && song.platform != "qq"
            var url: String? = if (useCached) item.cachedUrl else null
            if (url.isNullOrBlank()) {
                RateLimiterRegistry.acquire(song.platform)
                url = searchManager.getPlayUrl(song.songId, song.platform, song.quality)
                if (!url.isNullOrBlank()) {
                    cacheSong(song, url)
                }
            }

            if (url.isNullOrBlank()) {
                _preparingDownloads.value = _preparingDownloads.value - song.songId
                _searchError.value = "无法获取下载地址"
                return@launch
            }

            downloadManager.download(audioUrl = url, song = song, quality = song.quality, lyrics = lyrics, coverUrl = song.coverUrl, realName = song.realName, realArtist = song.realArtist)
                .onSuccess { path ->
                    _preparingDownloads.value = _preparingDownloads.value - song.songId
                    _downloadErrors.value = _downloadErrors.value - song.songId
                    log.i { "Downloaded → $path (lyrics: ${lyrics != null})" }
                    _searchError.value = null
                    onCacheChanged?.invoke()
                    scope.launch { behaviorTracker.recordDownload(song) }
                }
                .onFailure { e ->
                    _preparingDownloads.value = _preparingDownloads.value - song.songId
                    _downloadErrors.value = _downloadErrors.value + song.songId
                    log.e { "Download failed: ${e.message}" }
                    // Retry with fresh URL — resolve new URL even if it matches old one
                    scope.launch {
                        _preparingDownloads.value = _preparingDownloads.value + song.songId
                        delay(500)
                        RateLimiterRegistry.acquire(song.platform)
                        val freshUrl = searchManager.getPlayUrl(song.songId, song.platform, song.quality)
                        if (!freshUrl.isNullOrBlank()) {
                            cacheSong(song, freshUrl)
                            log.i { "Retrying download with fresh URL: ${song.songName}" }
                            downloadManager.download(audioUrl = freshUrl, song = song, quality = song.quality, lyrics = lyrics, coverUrl = song.coverUrl, realName = song.realName, realArtist = song.realArtist)
                                .onSuccess { path ->
                                    _preparingDownloads.value = _preparingDownloads.value - song.songId
                                    _downloadErrors.value = _downloadErrors.value - song.songId
                                    log.i { "Downloaded (retry) → $path" }
                                    onCacheChanged?.invoke()
                                    scope.launch { behaviorTracker.recordDownload(song) }
                                }
                                .onFailure { e2 ->
                                    _preparingDownloads.value = _preparingDownloads.value - song.songId
                                    _downloadErrors.value = _downloadErrors.value + song.songId
                                    log.e { "Download retry failed: ${e2.message}" }
                                    _searchError.value = "下载失败: ${e2.message}"
                                }
                        } else {
                            _preparingDownloads.value = _preparingDownloads.value - song.songId
                            _downloadErrors.value = _downloadErrors.value + song.songId
                            _searchError.value = "下载失败: ${e.message}"
                        }
                    }
                }
        }
    }

    /**
     * Fetch lyrics for a song before downloading it.
     *
     * Priority: 1) DownloadRecord DB (already downloaded with lyrics),
     * 2) Cross-platform / native API (fresh fetch).
     *
     * For Bilibili: uses [CrossPlatformLyricsFetcher] with title parsing.
     * For other platforms: uses the native lyrics API directly.
     */
    /**
     * @param parsedTitle Pre-parsed B站 title (from caller), avoids redundant parsing.
     *                    If null and platform is bilibili, parses internally.
     */
    private suspend fun fetchLyricsForDownload(song: SongMetadata, parsedTitle: ParsedBilibiliTitle? = null): String? {
        // ── Check DB first: already downloaded with lyrics? ──────────
        try {
            val downloaded = downloadManager.getDownloadedSongs().find { it.songId == downloadKey(song.songId, song.platform) }
            if (!downloaded?.lyrics.isNullOrBlank()) {
                log.d { "Lyrics already in DownloadRecord for ${song.songName}" }
                return downloaded!!.lyrics
            }
        } catch (_: Exception) { /* fall through to fresh fetch */ }

        // ── Fresh fetch ──────────────────────────────────────────────
        return if (song.platform == "bilibili") {
            val parsed = parsedTitle ?: BilibiliTitleParser.parse(song.songName, song.artist)
            // B站标题解析的歌手名不可靠 — 只传歌名避免错误歌手拉低匹配分数
            val result = crossPlatformLyricsFetcher.fetch(
                songName = parsed.cleanSongName,
                artist = "",
                durationS = song.duration,
                isSpecialVersion = parsed.isSpecialVersion,
            )
            when (result?.confidence) {
                LyricsMatchConfidence.HIGH -> result.lyrics
                LyricsMatchConfidence.MEDIUM -> result.lyrics + "\n\n[歌词来自网易云匹配，仅供参考]"
                else -> null
            }
        } else {
            searchManager.getLyrics(song.songId, song.platform)
        }
    }

    fun clearError() { _searchError.value = null }

    /** Clear all search state (called when navigating away). */
    fun clearSearch() {
        _searchResults.value = emptyList()
        _searchError.value = null
        _searchStep.value = null
        _hasSearched.value = false
        searchManager.clearSearchState()
    }

    // ── Playlist actions ────────────────────────────────────────────

    fun addToPlaylist(item: SearchResultItem) {
        val key = "${item.song.songId}|${item.song.platform}"
        if (key in _playlistSongIds.value) return // already added
        scope.launch {
            playlistManager.addToPlaylist(item.song, item.cachedUrl)
            _playlistSongIds.value = _playlistSongIds.value + key
            _searchError.value = "已添加到当前歌单"
        }
    }

    fun refreshPlaylistStatus() {
        scope.launch {
            _playlistSongIds.value = playlistManager.getPlaylist().map { "${it.songId}|${it.platform}" }.toSet()
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearSearch()
        log.d { "SearchViewModel cleared" }
    }

    // ══════════════════════════════════════════════════════════════════
    // Cache layer
    // ══════════════════════════════════════════════════════════════════

    /**
     * Check the local SQLite cache for ALL platforms.
     *
     * The cache key is deterministic: MD5("songName|artist|platform").
     * This means we can find cached entries for a specific song even when
     * the user searches with slightly different queries.
     *
     * @return List of [SearchResultItem] with cached URLs, or empty list
     */
    private suspend fun checkAllPlatformCaches(
        songName: String,
        artistName: String,
    ): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()

        for (platform in platformOrder) {
            val cacheId = Md5Utils.generateSongId(songName, artistName, platform)

            // Skip entries previously detected as truncated
            val cached = cacheRepository.getCachedSong(cacheId)
            if (cached != null) {
                val cachedSong = SongMetadata(
                    songId = cached.id,
                    songName = cached.songName,
                    artist = cached.artist ?: "",
                    platform = cached.platform,
                    quality = cached.quality,
                    coverUrl = cached.coverUrl,
                    duration = 0,
                )
                if (matchesQuery(cachedSong, songName, artistName)) {
                    results.add(
                        SearchResultItem(
                            song = cachedSong,
                            cachedUrl = cached.audioUrl,
                            fromCache = true,
                        )
                    )
                    log.d { "Cache HIT: platform=$platform" }
                }
            } else {
                if (cacheRepository.isFailedLookup(cacheId)) {
                    log.d { "Skipping platform=$platform (marked as failed lookup)" }
                }
            }
        }

        return results
    }

    /**
     * Search across all platforms in deterministic order.
     *
     * For each platform:
     * 1. Acquire rate limit permit + apply random jitter
     * 2. Search via [searchManager.searchOnPlatform] + resolve play URLs
     * 3. Collect results from all platforms for maximum coverage
     * 4. Cache each successful resolution
     *
     * Results are filtered to only include songs whose name and artist
     * match the user's search terms.
     *
     * @return Ordered list of [SearchResultItem], best match first
     */
    /**
     * Search across all platforms in priority order, streaming each playable
     * result to the UI as soon as its URL is resolved.
     *
     * Platforms are searched sequentially (Bilibili → Netease → QQ → Kugou).
     * Each time a song's play URL is successfully resolved, it is immediately
     * appended to [_searchResults] so the user sees results appear in real-time
     * without waiting for the remaining platforms.
     */
    private suspend fun searchAcrossPlatforms(
        songName: String,
        artistName: String,
        query: String,
    ) {
        val maxPerPlatform = 3    // Limit results per platform to avoid flooding
        val maxTotal = 8          // Enough total results across all platforms

        for (platform in platformOrder) {
            // ── Bilibili: two-round search ──────────────────────────
            if (platform == "bilibili") {
                val hasArtist = artistName.isNotBlank() && artistName != "未知歌手"

                // Round 1: song name + artist name (strict match)
                if (hasArtist) {
                    searchAndResolveForSearchPanel(
                        platform, "$songName $artistName", songName, artistName,
                        maxPerPlatform = 3, maxTotal = maxTotal,
                    )
                }

                // Round 2: song name only (ignore artist in search query AND filter, dedup vs round 1)
                // 但 realArtist 仍用原始输入，避免回退到 UP主名
                if (_searchResults.value.size < maxTotal) {
                    searchAndResolveForSearchPanel(
                        platform, songName, songName, artistName = "",
                        maxPerPlatform = 3, maxTotal = maxTotal,
                        realArtistFallback = artistName,
                    )
                }
            } else {
                // ── Other platforms: single-round search ─────────────
                searchAndResolveForSearchPanel(
                    platform, query, songName, artistName,
                    maxPerPlatform = maxPerPlatform, maxTotal = maxTotal,
                )
            }

            // Stop early if we have enough total results
            if (_searchResults.value.size >= maxTotal) {
                log.i { "Collected ${_searchResults.value.size} results across platforms — stopping search" }
                break
            }
        }

        if (_searchResults.value.isNotEmpty()) {
            val platforms = _searchResults.value.map { it.song.platform }.distinct().joinToString(", ")
            _searchStep.value = "已从 $platforms 找到 ${_searchResults.value.size} 首可播放歌曲"
        }
    }

    /**
     * Execute a single search round for the search panel: search on [platform]
     * with [query], filter, resolve play URLs, and stream to [_searchResults].
     *
     * Results already in [_searchResults] are skipped (dedup by songId).
     */
    private suspend fun searchAndResolveForSearchPanel(
        platform: String,
        query: String,
        songName: String,
        artistName: String,
        maxPerPlatform: Int,
        maxTotal: Int,
        realArtistFallback: String = artistName,
    ) {
        val label = platformLabel(platform)
        val existingIds = _searchResults.value.map { it.song.songId }.toSet()

        _searchStep.value = "正在搜索 $label ..."
        RateLimiterRegistry.acquire(platform)
        delay(Random.nextLong(200, 1500))

        val songs = try {
            searchManager.searchOnPlatform(query, platform, quality = "standard")
        } catch (e: Exception) {
            log.w { "Platform $platform search exception: ${e.message}" }
            emptyList()
        }

        // Filter: dedup + match
        val matchedSongs = songs.filter {
            matchesQuery(it, songName, artistName) && it.songId !in existingIds
        }
        log.i { "Platform $platform query=\"$query\": ${songs.size} raw → ${matchedSongs.size} matched" }

        if (matchedSongs.isNotEmpty()) {
            _searchStep.value = "正在解析 $label 播放地址..."
            var platformResults = 0

            for (song in matchedSongs) {
                if (platformResults >= maxPerPlatform) break
                if (_searchResults.value.size >= maxTotal) break

                // Skip Netease/QQ VIP/paid songs
                if ((song.platform == "netease" || song.platform == "qq") && song.fee > 0) {
                    log.d { "Skipping VIP: ${song.songName} (${song.platform} fee=${song.fee})" }
                    continue
                }

                RateLimiterRegistry.acquire(platform)
                delay(Random.nextLong(100, 800))

                val url = searchManager.getPlayUrl(song.songId, platform, song.quality)
                if (!url.isNullOrBlank()) {
                    // B站：用搜索输入框的值覆盖假的 songName/artist（视频标题/UP主名）
                    val songWithMeta = if (platform == "bilibili") {
                        song.copy(
                            realName = songName.ifBlank { null },
                            realArtist = realArtistFallback.ifBlank { null },
                        )
                    } else song
                    cacheSong(songWithMeta, url)
                    val item = SearchResultItem(song = songWithMeta, cachedUrl = url, fromCache = false)

                    // STREAMING: push result to UI immediately
                    _searchResults.value = _searchResults.value + item
                    platformResults++

                    log.i { "Streamed: ${song.songName} — ${song.artist} (${song.platform})" }
                }
            }

            if (platformResults > 0) {
                log.i { "Platform $platform: added $platformResults playable results (total=${_searchResults.value.size})" }
                _searchStep.value = "$label 找到 $platformResults 首 ✓"
            } else {
                _searchStep.value = "$label 找到匹配歌曲但无法获取播放地址，尝试下一平台..."
            }
        } else {
            _searchStep.value = "$label 无匹配结果，尝试下一平台..."
        }
    }

    /**
     * Check whether a song matches the user's search terms.
     *
     * Matching rules:
     * - Song name must contain or be contained by the search term
     * - If artist was provided, artist must also match
     * - **Bilibili special case**: [SongMetadata.artist] is the uploader
     *   name, not the song's artist. Check the title instead.
     * - Comparison is case-insensitive
     */
    private fun matchesQuery(song: SongMetadata, searchSong: String, searchArtist: String): Boolean {
        val songName = song.songName.trim()
        val artist = song.artist.trim()

        // Song name match: either direction containment
        val songMatch = songName.contains(searchSong, ignoreCase = true) ||
                        searchSong.contains(songName, ignoreCase = true)
        if (!songMatch) return false

        // Artist match: only required if user provided artist
        if (searchArtist.isNotBlank()) {
            val artistMatch = if (song.platform == "bilibili") {
                // Bilibili's "artist" field is the uploader — check the
                // title instead (it virtually always includes the artist name)
                songName.contains(searchArtist, ignoreCase = true)
            } else {
                artist.contains(searchArtist, ignoreCase = true) ||
                searchArtist.contains(artist, ignoreCase = true)
            }
            return artistMatch
        }

        return true
    }

    /**
     * Persist a resolved song + URL to the local SQLite cache.
     *
     * Uses deterministic cache ID: MD5("songName|artist|platform")
     * with the default 7-day TTL.
     */
    private suspend fun cacheSong(song: SongMetadata, audioUrl: String) {
        val cacheId = Md5Utils.generateSongId(song.songName, song.artist, song.platform)
        val entity = CacheRepositoryImpl.createSongEntity(
            id = cacheId,
            songName = song.songName,
            artist = song.artist,
            platform = song.platform,
            audioUrl = audioUrl,
            coverUrl = song.coverUrl,
            quality = song.quality,
        )
        cacheRepository.saveSong(entity)
        log.d { "Cached: ${song.songName} → ${song.platform}" }
    }

    // ── Auto-retry next result ───────────────────────────────────────

    /**
     * When the current result fails to play, automatically try the next
     * result in the list.
     */
    private fun tryNextResult(currentItem: SearchResultItem) {
        val items = _searchResults.value
        val currentIndex = items.indexOf(currentItem)
        if (currentIndex < items.size - 1) {
            val next = items[currentIndex + 1]
            log.i { "Auto-retry next result: ${next.song.songName}" }
            _searchError.value = "${currentItem.song.songName} 无法播放，尝试备选..."
            playResult(next)
        } else {
            _searchError.value = "所有音源均无法播放"
        }
    }

    companion object {
        /** Minimum characters required before a search can be triggered. */
        const val MIN_SEARCH_LENGTH = 1
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Search result item (UI model)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Wraps a [SongMetadata] with additional UI-relevant fields.
 *
 * @param song      The underlying song metadata
 * @param cachedUrl Pre-resolved play URL (from cache or fresh resolution)
 * @param fromCache Whether this result came from the local SQLite cache
 */
data class SearchResultItem(
    val song: SongMetadata,
    val cachedUrl: String? = null,
    val fromCache: Boolean = false,
)
