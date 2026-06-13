package com.example.aimusicplayer.import

import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.network.RateLimiterRegistry
import com.example.aimusicplayer.network.music.MusicPlatformApi
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random

class PlaylistImportManager(
    private val musicApis: List<MusicPlatformApi>,
    private val searchManager: MusicSearchManager,
    private val playlistManager: PlaylistManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trackSemaphore = Semaphore(5) // Limit concurrent track searches
    private val trackJobs = mutableMapOf<Int, Job>() // Track search jobs for cancellation

    private val _session = MutableStateFlow(ImportSession())
    val session: StateFlow<ImportSession> = _session.asStateFlow()

    /** Platform key of the currently importing playlist (set during startImport). */
    private var currentPlatform: String = ""

    /**
     * Start importing a playlist from a share URL.
     * Flow: parse link → fetch playlist detail → concurrent search all tracks.
     */
    fun startImport(url: String) {
        scope.launch {
            _session.value = ImportSession(phase = ImportPhase.PARSING)

            // 1. Parse link
            val parsed = PlaylistLinkParser.parse(url)
            if (parsed == null) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "无法识别的链接，请粘贴 QQ音乐/网易云/酷狗 歌单分享链接",
                )
                return@launch
            }

            // 2. Get platform API
            currentPlatform = parsed.platform
            val api = musicApis.firstOrNull { it.platform == currentPlatform }
            if (api == null) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "暂不支持 $currentPlatform 平台",
                )
                return@launch
            }

            // 3. Fetch playlist detail
            _session.value = ImportSession(phase = ImportPhase.FETCHING)
            val detail = api.getPlaylistDetail(parsed.playlistId)
            if (detail == null) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "获取歌单失败，请检查链接或稍后重试",
                )
                return@launch
            }
            if (detail.tracks.isEmpty()) {
                _session.value = ImportSession(
                    phase = ImportPhase.ERROR,
                    errorMessage = "歌单为空，没有可导入的歌曲",
                )
                return@launch
            }

            // 4. Initialize track states
            val tracks = detail.tracks.mapIndexed { index, track ->
                TrackImportState(
                    index = index,
                    originalName = track.songName,
                    originalArtist = track.artist,
                )
            }

            _session.value = ImportSession(
                playlistDetail = detail,
                tracks = tracks,
                phase = ImportPhase.SEARCHING,
            )

            // 5. Concurrent per-track search — mirrors SearchViewModel per-platform streaming logic
            tracks.forEach { track ->
                val job = ioScope.launch {
                    trackSemaphore.withPermit {
                        searchTrackStreaming(track.index, track.originalName, track.originalArtist)
                    }
                }
                trackJobs[track.index] = job
            }
            // Transition to CONFIRMING immediately — cards appear as results stream in
            _session.value = _session.value.copy(phase = ImportPhase.CONFIRMING)
        }
    }

    /** User confirms a track — cancel its search and select the result. */
    fun confirmTrack(index: Int, result: SongMetadata) {
        // Guard: don't overwrite if already resolved (prevents auto-confirm race)
        val current = _session.value.tracks.getOrNull(index) ?: return
        if (current.confirmStatus == TrackConfirmStatus.CONFIRMED ||
            current.confirmStatus == TrackConfirmStatus.SKIPPED) return

        trackJobs.remove(index)?.cancel() // Stop further search for this track
        updateTrack(index) {
            it.copy(
                selectedResult = result,
                confirmStatus = TrackConfirmStatus.CONFIRMED,
            )
        }
        advanceIfAllDone()
    }

    /** User skips a track — cancel its search. */
    fun skipTrack(index: Int) {
        // Guard: don't overwrite if already resolved
        val current = _session.value.tracks.getOrNull(index) ?: return
        if (current.confirmStatus == TrackConfirmStatus.CONFIRMED ||
            current.confirmStatus == TrackConfirmStatus.SKIPPED) return

        trackJobs.remove(index)?.cancel() // Stop further search for this track
        updateTrack(index) {
            it.copy(confirmStatus = TrackConfirmStatus.SKIPPED)
        }
        advanceIfAllDone()
    }

    /** Re-search a specific track using the same per-platform streaming logic. */
    fun retryTrack(index: Int) {
        val track = _session.value.tracks.getOrNull(index) ?: return
        updateTrack(index) {
            it.copy(confirmStatus = TrackConfirmStatus.SEARCHING, searchResults = emptyList(), resolvedPlayUrls = emptyMap())
        }
        val job = ioScope.launch {
            trackSemaphore.withPermit {
                searchTrackStreaming(track.index, track.originalName, track.originalArtist)
            }
        }
        trackJobs[track.index] = job
    }

    /** Save confirmed tracks to PlaylistManager. */
    fun saveImport() {
        scope.launch {
            _session.value = _session.value.copy(phase = ImportPhase.SAVING)
            val session = _session.value
            val detail = session.playlistDetail ?: return@launch

            val importedSongs = session.tracks.map { track ->
                val result = track.selectedResult
                if (track.confirmStatus == TrackConfirmStatus.CONFIRMED && result != null) {
                    ImportedSong(
                        originalName = track.originalName,
                        originalArtist = track.originalArtist,
                        matchedSongId = result.songId,
                        matchedName = result.songName,
                        matchedArtist = result.artist,
                        matchedPlatform = result.platform,
                        playUrl = track.resolvedPlayUrls[result.songId],
                        coverUrl = result.coverUrl,
                        quality = result.quality,
                    )
                } else {
                    ImportedSong(
                        originalName = track.originalName,
                        originalArtist = track.originalArtist,
                        matchedSongId = null,
                        matchedName = null,
                        matchedArtist = null,
                        matchedPlatform = null,
                        playUrl = null,
                        coverUrl = null,
                        quality = null,
                    )
                }
            }

            val playlist = ImportedPlaylist(
                id = detail.playlistId,
                title = detail.title,
                coverUrl = detail.coverUrl,
                platform = currentPlatform,
                sourceUrl = "",
                importedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                songs = importedSongs,
            )

            playlistManager.addImportedPlaylist(playlist)
            _session.value = _session.value.copy(phase = ImportPhase.COMPLETED)
        }
    }

    /** Resolve a playable audio URL for on-demand playback. */
    suspend fun resolvePlayUrl(songId: String, platform: String, quality: String): String? {
        return searchManager.getPlayUrl(songId, platform, quality)
    }

    /** Reset to idle. Cancel all in-progress searches. */
    fun reset() {
        trackJobs.values.forEach { it.cancel() }
        trackJobs.clear()
        ioScope.coroutineContext.cancelChildren()
        scope.coroutineContext.cancelChildren()
        _session.value = ImportSession(phase = ImportPhase.IDLE)
    }

    // ── Per-track streaming search (mirrors SearchViewModel.searchAcrossPlatforms) ──

    /**
     * Search for a single track across all platforms with per-platform rate limiting,
     * VIP filtering, and immediate URL resolution. Results stream into the
     * track's card one-by-one as they're found — same behavior as the search panel.
     */
    private suspend fun searchTrackStreaming(trackIndex: Int, songName: String, artistName: String) {
        val platformOrder = listOf("bilibili", "netease", "qq", "kugou")
        val maxPerPlatform = 3
        val allResults = mutableListOf<SongMetadata>()
        val allUrls = mutableMapOf<String, String>()

        try {
            for (platform in platformOrder) {
                if (allResults.size >= 8) break // max total results

                // ── Bilibili: two-round search ──────────────────────
                if (platform == "bilibili") {
                    val hasArtist = artistName.isNotBlank() && artistName != "未知歌手"

                    // Round 1: song name + artist name (strict match)
                    if (hasArtist) {
                        allResults += searchAndResolveForImport(
                            platform, "$songName $artistName", songName, artistName, allResults, allUrls, trackIndex,
                            maxPerPlatform = 3, maxTotal = 8,
                        )
                    }

                    // Round 2: song name only (ignore artist, dedup vs round 1)
                    if (allResults.size < 8) {
                        allResults += searchAndResolveForImport(
                            platform, songName, songName, artistName, allResults, allUrls, trackIndex,
                            maxPerPlatform = 3, maxTotal = 8,
                        )
                    }
                } else {
                    // ── Other platforms: single-round search ─────────
                    allResults += searchAndResolveForImport(
                        platform, "$artistName $songName", songName, artistName, allResults, allUrls, trackIndex,
                        maxPerPlatform = maxPerPlatform, maxTotal = 8,
                    )
                }
            }
        } catch (_: Exception) { /* track-level failure — partial results still shown */ }

        // If we found NO results at all, mark as skipped (unless user already acted)
        if (allResults.isEmpty()) {
            scope.launch {
                updateTrack(trackIndex) { current ->
                    if (current.confirmStatus == TrackConfirmStatus.CONFIRMED) {
                        current // User already confirmed — don't overwrite
                    } else {
                        current.copy(confirmStatus = TrackConfirmStatus.SKIPPED)
                    }
                }
                advanceIfAllDone()
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────

    private fun updateTrack(index: Int, transform: (TrackImportState) -> TrackImportState) {
        val tracks = _session.value.tracks.toMutableList()
        if (index in tracks.indices) {
            tracks[index] = transform(tracks[index])
            _session.value = _session.value.copy(tracks = tracks)
        }
    }

    /**
     * Execute a single search round for playlist import: search, filter, resolve
     * play URLs, and stream results to the track card.
     *
     * @return List of newly resolved [SongMetadata] from this round.
     */
    private suspend fun searchAndResolveForImport(
        platform: String,
        query: String,
        songName: String,
        artistName: String,
        existingResults: List<SongMetadata>,
        existingUrls: MutableMap<String, String>,
        trackIndex: Int,
        maxPerPlatform: Int,
        maxTotal: Int,
    ): List<SongMetadata> {
        val newResults = mutableListOf<SongMetadata>()

        // Rate limit before each platform search
        try { RateLimiterRegistry.acquire(platform) } catch (_: Exception) {}
        delay(Random.nextLong(100, 800))

        val songs = try {
            searchManager.searchOnPlatform(query, platform)
        } catch (_: Exception) { emptyList() }

        // Filter results: exact match on song name, dedup vs existing
        val existingIds = existingResults.map { it.songId }.toSet()
        val matched = songs.filter { s ->
            val sName = s.songName.trim()
            (sName.contains(songName, ignoreCase = true) || songName.contains(sName, ignoreCase = true)) &&
            s.songId !in existingIds
        }


        var platformCount = 0
        for (song in matched) {
            if (platformCount >= maxPerPlatform) break
            if (existingResults.size + newResults.size >= maxTotal) break

            // Skip VIP/paid songs (matches SearchViewModel behavior)
            if ((platform == "netease" || platform == "qq") && song.fee > 0) continue

            try { RateLimiterRegistry.acquire(platform) } catch (_: Exception) {}
            delay(Random.nextLong(50, 400))

            val url = searchManager.getPlayUrl(song.songId, platform, song.quality)
            if (!url.isNullOrBlank()) {
                // B站：用原始歌单中的歌名/歌手作为 realName/realArtist
                val songWithMeta = if (platform == "bilibili") {
                    song.copy(
                        realName = songName.ifBlank { null },
                        realArtist = artistName.ifBlank { null },
                    )
                } else song
                existingUrls[songWithMeta.songId] = url
                newResults.add(songWithMeta)
                platformCount++

                // STREAM: push result to card immediately
                val combined = existingResults + newResults
                scope.launch {
                    updateTrack(trackIndex) { current ->
                        if (current.confirmStatus == TrackConfirmStatus.CONFIRMED ||
                            current.confirmStatus == TrackConfirmStatus.SKIPPED) {
                            current
                        } else {
                            current.copy(
                                searchResults = combined.toList(),
                                resolvedPlayUrls = existingUrls.toMap(),
                                confirmStatus = TrackConfirmStatus.AWAITING,
                            )
                        }
                    }
                }
            }
        }

        return newResults
    }

    private fun advanceIfAllDone() {
        val tracks = _session.value.tracks
        val allResolved = tracks.all {
            it.confirmStatus == TrackConfirmStatus.CONFIRMED ||
            it.confirmStatus == TrackConfirmStatus.SKIPPED
        }
        if (allResolved && tracks.isNotEmpty()) {
            _session.value = _session.value.copy(phase = ImportPhase.SAVING)
        }
    }
}
