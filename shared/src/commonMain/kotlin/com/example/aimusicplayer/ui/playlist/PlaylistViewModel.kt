package com.example.aimusicplayer.ui.playlist

import com.example.aimusicplayer.behavior.UserBehaviorTracker
import com.example.aimusicplayer.music.HistorySong
import com.example.aimusicplayer.music.PlayMode
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.music.PlaylistSong
import com.example.aimusicplayer.import.ImportPhase
import com.example.aimusicplayer.import.ImportSession
import com.example.aimusicplayer.import.ImportedPlaylist
import com.example.aimusicplayer.import.ImportedSong
import com.example.aimusicplayer.import.PlaylistImportManager
import com.example.aimusicplayer.music.DownloadManagerProvider
import com.example.aimusicplayer.music.downloadKey
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.player.MusicPlayerProvider
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.viewmodel.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val playlistManager: PlaylistManager,
    private val musicPlayer: MusicPlayerProvider,
    private val settingsRepo: SettingsRepository,
    private val importManager: PlaylistImportManager,
    private val downloadManager: DownloadManagerProvider,
    private val behaviorTracker: UserBehaviorTracker,
) : ViewModel() {

    /** 通知外部（MainViewModel）喜欢状态已变更。参数: songId, platform */
    var onFavoriteChanged: ((String, String) -> Unit)? = null

    // ── Tab selection ────────────────────────────────────────────
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // ── Current playlist ──────────────────────────────────────────
    private val _playlist = MutableStateFlow<List<PlaylistSong>>(emptyList())
    val playlist: StateFlow<List<PlaylistSong>> = _playlist.asStateFlow()

    // ── History ───────────────────────────────────────────────────
    private val _history = MutableStateFlow<List<HistorySong>>(emptyList())
    val history: StateFlow<List<HistorySong>> = _history.asStateFlow()

    // ── Play mode ─────────────────────────────────────────────────
    private val _playMode = MutableStateFlow(playlistManager.getPlayMode())
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    // ── Playlist song IDs set (for checking if a search result is already added) ─
    private val _playlistSongIds = MutableStateFlow<Set<String>>(emptySet())
    val playlistSongIds: StateFlow<Set<String>> = _playlistSongIds.asStateFlow()

    // ── Favorite song IDs set ─────────────────────────────────────
    private val _favoriteSongIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSongIds: StateFlow<Set<String>> = _favoriteSongIds.asStateFlow()

    // ── Favorites list (我的喜欢) ──────────────────────────────────
    private val _favorites = MutableStateFlow<List<PlaylistSong>>(emptyList())
    val favorites: StateFlow<List<PlaylistSong>> = _favorites.asStateFlow()

    // ── Import state ──────────────────────────────────────────
    val importSession: StateFlow<ImportSession> = importManager.session

    private val _importSubView = MutableStateFlow(0)
    val importSubView: StateFlow<Int> = _importSubView.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    private val _importedPlaylists = MutableStateFlow<List<ImportedPlaylist>>(emptyList())
    val importedPlaylists: StateFlow<List<ImportedPlaylist>> = _importedPlaylists.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    init {
        scope.launch {
            importManager.session.collect { session ->
                if (session.phase == ImportPhase.COMPLETED) {
                    refreshImportedPlaylists()
                }
            }
        }
    }

    fun selectTab(index: Int) {
        if (_selectedTab.value != index) _selectedTab.value = index
    }

    fun togglePlayMode() {
        val newMode = when (_playMode.value) {
            PlayMode.SEQUENTIAL -> PlayMode.LOOP
            PlayMode.LOOP -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SEQUENTIAL
        }
        _playMode.value = newMode
        playlistManager.setPlayMode(newMode)
    }

    /** Toggle favorite/like for the currently playing song. Persists to favorites storage. */
    fun toggleFavorite() {
        val song = musicPlayer.currentSong.value ?: return
        scope.launch {
            val key = "${song.songId}|${song.platform}"
            if (playlistManager.isFavorite(song.songId, song.platform)) {
                playlistManager.removeFromFavorites(song.songId, song.platform)
                _favoriteSongIds.value = _favoriteSongIds.value - key
            } else {
                playlistManager.addToFavorites(song)
                _favoriteSongIds.value = _favoriteSongIds.value + key
            }
            loadFavorites()
        }
    }

    fun addToPlaylist(song: SongMetadata, playUrl: String?) {
        scope.launch {
            playlistManager.addToPlaylist(song, playUrl)
            refresh()
        }
    }

    fun removeFromPlaylist(songId: String, platform: String) {
        scope.launch {
            playlistManager.removeFromPlaylist(songId, platform)
            refresh()
        }
    }

    fun moveSong(fromIndex: Int, toIndex: Int) {
        scope.launch {
            playlistManager.moveSong(fromIndex, toIndex)
            _playlist.value = playlistManager.getPlaylist()
        }
    }

    fun playSongFromPlaylist(song: PlaylistSong) {
        scope.launch {
            val url = resolvePlaySource(song.songId, song.platform, song.quality, song.playUrl) ?: return@launch
            val meta = SongMetadata(songId = song.songId, songName = song.songName, artist = song.artist,
                platform = song.platform, quality = song.quality, coverUrl = song.coverUrl, duration = song.duration,
                realName = song.realName, realArtist = song.realArtist)
            musicPlayer.play(url = url, title = song.realName ?: meta.songName, artist = song.realArtist ?: meta.artist, song = meta)
            // 30s history timer — also pushes to UI immediately
            scope.launch {
                delay(30_000)
                if (musicPlayer.isPlaying.value &&
                    musicPlayer.currentSong.value?.songId == meta.songId) {
                    playlistManager.addToHistory(meta, url)
                    _history.value = playlistManager.getHistory(settingsRepo.getHistoryCount())
                }
            }
        }
    }

    fun playSongFromHistory(song: HistorySong) {
        scope.launch {
            val url = resolvePlaySource(song.songId, song.platform, song.quality, song.playUrl) ?: return@launch
            val meta = SongMetadata(songId = song.songId, songName = song.songName, artist = song.artist,
                platform = song.platform, quality = song.quality, coverUrl = song.coverUrl, duration = song.duration,
                realName = song.realName, realArtist = song.realArtist)
            musicPlayer.play(url = url, title = song.realName ?: meta.songName, artist = song.realArtist ?: meta.artist, song = meta)
            // Auto-add to current playlist
            playlistManager.addToPlaylistFirst(meta, url)
            _playlistSongIds.value = _playlistSongIds.value + "${song.songId}|${song.platform}"
            _playlist.value = playlistManager.getPlaylist()
            // 30s history timer
            scope.launch {
                delay(30_000)
                if (musicPlayer.isPlaying.value &&
                    musicPlayer.currentSong.value?.songId == meta.songId) {
                    playlistManager.addToHistory(meta, url)
                    _history.value = playlistManager.getHistory(settingsRepo.getHistoryCount())
                }
            }
        }
    }

    fun clearPlaylist() {
        scope.launch {
            playlistManager.clearPlaylist()
            _playlist.value = emptyList()
            _playlistSongIds.value = emptySet()
        }
    }

    fun clearHistory() {
        scope.launch {
            playlistManager.clearHistory()
            _history.value = emptyList()
        }
    }

    fun isInPlaylist(songId: String, platform: String): Boolean = "${songId}|${platform}" in _playlistSongIds.value

    suspend fun refresh() {
        _playlist.value = playlistManager.getPlaylist()
        _history.value = playlistManager.getHistory(settingsRepo.getHistoryCount())
        _playlistSongIds.value = _playlist.value.map { "${it.songId}|${it.platform}" }.toSet()
        _importedPlaylists.value = playlistManager.getImportedPlaylists()
        loadFavorites()
    }

    private suspend fun loadFavorites() {
        _favorites.value = playlistManager.getFavorites()
        _favoriteSongIds.value = _favorites.value.map { "${it.songId}|${it.platform}" }.toSet()
    }

    // ── Import actions ────────────────────────────────────────

    fun startImport(url: String) {
        importManager.startImport(url)
    }

    fun confirmTrack(index: Int, result: SongMetadata) {
        importManager.confirmTrack(index, result)
    }

    fun skipTrack(index: Int) {
        importManager.skipTrack(index)
    }

    fun autoConfirmAll() {
        scope.launch {
            while (true) {
                val tracks = importManager.session.value.tracks
                val pending = tracks.filter {
                    it.confirmStatus == com.example.aimusicplayer.import.TrackConfirmStatus.AWAITING ||
                    it.confirmStatus == com.example.aimusicplayer.import.TrackConfirmStatus.SEARCHING
                }
                if (pending.isEmpty()) break // All done

                for (track in pending) {
                    val current = importManager.session.value.tracks.getOrNull(track.index) ?: continue
                    when (current.confirmStatus) {
                        com.example.aimusicplayer.import.TrackConfirmStatus.AWAITING -> {
                            val firstResult = current.searchResults.firstOrNull()
                            if (firstResult != null) {
                                importManager.confirmTrack(current.index, firstResult)
                            } else {
                                importManager.skipTrack(current.index)
                            }
                        }
                        com.example.aimusicplayer.import.TrackConfirmStatus.SEARCHING -> {
                            // Wait for search to complete — poll every 500ms
                            kotlinx.coroutines.delay(500)
                        }
                        else -> { /* already resolved by another iteration */ }
                    }
                }
            }
        }
    }

    fun retryTrack(index: Int) {
        importManager.retryTrack(index)
    }

    fun saveImport() {
        importManager.saveImport()
    }

    fun resetImport() {
        importManager.reset()
    }

    fun setImportSubView(view: Int) {
        _importSubView.value = view
    }

    fun selectImportedPlaylist(id: String) {
        _selectedPlaylistId.value = id
        _importSubView.value = 2
    }

    fun clearSelectedPlaylist() {
        _selectedPlaylistId.value = null
        _importSubView.value = 0
    }

    suspend fun refreshImportedPlaylists() {
        _importedPlaylists.value = playlistManager.getImportedPlaylists()
    }

    /** Play a song from an imported playlist. Local file first, network fallback. */
    fun playImportedSong(song: ImportedSong) {
        scope.launch {
            if (song.matchedSongId == null || song.matchedPlatform == null) return@launch
            val url = resolvePlaySource(song.matchedSongId, song.matchedPlatform, song.quality ?: "standard", song.playUrl)
                ?: return@launch
            val meta = SongMetadata(songId = song.matchedSongId, songName = song.matchedName ?: song.originalName,
                artist = song.matchedArtist ?: song.originalArtist, platform = song.matchedPlatform,
                quality = song.quality ?: "standard", coverUrl = song.coverUrl,
                // B站：用原始歌单中的歌名/歌手名作为真实值（匹配结果标题和UP主名不可靠）
                realName = if (song.matchedPlatform == "bilibili") song.originalName else null,
                realArtist = if (song.matchedPlatform == "bilibili") song.originalArtist else null,
            )
            musicPlayer.play(url = url, title = meta.songName, artist = meta.artist, song = meta)
            // Auto-add to current playlist
            playlistManager.addToPlaylistFirst(meta, url)
            _playlistSongIds.value = _playlistSongIds.value + "${song.matchedSongId}|${song.matchedPlatform}"
            _playlist.value = playlistManager.getPlaylist()
            // 30s history timer
            scope.launch {
                delay(30_000)
                if (musicPlayer.isPlaying.value &&
                    musicPlayer.currentSong.value?.songId == meta.songId) {
                    playlistManager.addToHistory(meta, url)
                    _history.value = playlistManager.getHistory(settingsRepo.getHistoryCount())
                }
            }
        }
    }

    /** Resolve play source: local file first, network URL fallback. */
    private suspend fun resolvePlaySource(songId: String, platform: String, quality: String, cachedUrl: String?): String? {
        // 1. Check local download
        val key = downloadKey(songId, platform)
        val local = downloadManager.getDownloadedSongs().find { it.songId == key }
        if (local != null) return local.localPath
        // 2. Resolve network URL
        val fresh = importManager.resolvePlayUrl(songId, platform, quality)
        return fresh ?: cachedUrl
    }

    /** Remove an imported playlist. */
    fun removeImportedPlaylist(id: String) {
        scope.launch {
            playlistManager.removeImportedPlaylist(id)
            refreshImportedPlaylists()
        }
    }

    /** Replace current playlist with an imported playlist's matched songs. */
    fun replacePlaylistWithImported(playlistId: String) {
        scope.launch {
            val imported = playlistManager.getImportedPlaylists().firstOrNull { it.id == playlistId } ?: return@launch
            playlistManager.clearPlaylist()
            var firstPlayUrl: String? = null
            imported.songs.forEach { song ->
                if (song.matchedSongId != null && song.matchedPlatform != null) {
                    val url = song.playUrl
                        ?: importManager.resolvePlayUrl(song.matchedSongId, song.matchedPlatform, song.quality ?: "standard")
                    if (firstPlayUrl == null && !url.isNullOrBlank()) firstPlayUrl = url
                    playlistManager.addToPlaylist(
                        SongMetadata(
                            songId = song.matchedSongId,
                            songName = song.matchedName ?: song.originalName,
                            artist = song.matchedArtist ?: song.originalArtist,
                            platform = song.matchedPlatform,
                            quality = song.quality ?: "standard",
                            coverUrl = song.coverUrl,
                        ),
                        url,
                    )
                }
            }
            refresh()
            // Start playing from first song
            val playlist = playlistManager.getPlaylist()
            if (playlist.isNotEmpty() && firstPlayUrl != null) {
                val first = playlist.first()
                val meta = SongMetadata(
                    songId = first.songId,
                    songName = first.songName,
                    artist = first.artist,
                    platform = first.platform,
                    quality = first.quality,
                    coverUrl = first.coverUrl,
                )
                musicPlayer.play(
                    url = firstPlayUrl!!,
                    title = first.songName,
                    artist = first.artist,
                    song = meta,
                )
                // 30s history timer
                scope.launch {
                    delay(30_000)
                    if (musicPlayer.isPlaying.value &&
                        musicPlayer.currentSong.value?.songId == meta.songId) {
                        playlistManager.addToHistory(meta, firstPlayUrl)
                        _history.value = playlistManager.getHistory(settingsRepo.getHistoryCount())
                    }
                }
            }
        }
    }

    /** Remove a single song from an imported playlist by its index. */
    fun removeImportedSong(playlistId: String, songIndex: Int) {
        scope.launch {
            playlistManager.removeImportedSong(playlistId, songIndex)
            refreshImportedPlaylists()
        }
    }

    /** Download all songs in the current playlist (skip already downloaded). */
    fun downloadPlaylist() {
        scope.launch {
            val songs = playlistManager.getPlaylist()
            val downloaded = downloadManager.getDownloadedSongs().map { it.songId }.toSet()
            var success = 0
            var skipped = 0
            songs.forEach { song ->
                if (downloadKey(song.songId, song.platform) in downloaded) {
                    skipped++
                    return@forEach
                }
                val url = song.playUrl ?: return@forEach
                val songMeta = SongMetadata(
                    songId = song.songId,
                    songName = song.songName,
                    artist = song.artist,
                    platform = song.platform,
                    quality = song.quality,
                    coverUrl = song.coverUrl,
                )
                val result = downloadManager.download(
                    audioUrl = url,
                    song = songMeta,
                    quality = song.quality,
                    coverUrl = song.coverUrl,
                    realName = null,
                    realArtist = null,
                )
                if (result.isSuccess) {
                    success++
                    scope.launch { behaviorTracker.recordDownload(songMeta) }
                } else skipped++
            }
        }
    }

    /** Download all matched songs in an imported playlist. */
    fun downloadImportedPlaylist(playlistId: String) {
        scope.launch {
            val imported = playlistManager.getImportedPlaylists().firstOrNull { it.id == playlistId } ?: return@launch
            val downloaded = downloadManager.getDownloadedSongs().map { it.songId }.toSet()
            var success = 0
            var skipped = 0
            imported.songs.forEach { song ->
                if (song.matchedSongId == null || song.matchedPlatform == null) return@forEach
                if (downloadKey(song.matchedSongId, song.matchedPlatform) in downloaded) {
                    skipped++
                    return@forEach
                }
                val url = song.playUrl
                    ?: importManager.resolvePlayUrl(song.matchedSongId, song.matchedPlatform, song.quality ?: "standard")
                    ?: return@forEach
                val importedSongMeta = SongMetadata(
                    songId = song.matchedSongId,
                    songName = song.matchedName ?: song.originalName,
                    artist = song.matchedArtist ?: song.originalArtist,
                    platform = song.matchedPlatform,
                    quality = song.quality ?: "standard",
                    coverUrl = song.coverUrl,
                )
                val result = downloadManager.download(
                    audioUrl = url,
                    song = importedSongMeta,
                    quality = song.quality ?: "standard",
                    coverUrl = song.coverUrl,
                    // B站：用原始歌单中的歌名/歌手作为 realName/realArtist（匹配结果的标题和UP主名不可靠）
                    realName = if (song.matchedPlatform == "bilibili") song.originalName else null,
                    realArtist = if (song.matchedPlatform == "bilibili") song.originalArtist else null,
                )
                if (result.isSuccess) {
                    success++
                    scope.launch { behaviorTracker.recordDownload(importedSongMeta) }
                } else skipped++
            }
        }
    }

    // ── Favorites actions ─────────────────────────────────────

    /** Play a song from the favorites list. Resolves URL via network/local. */
    fun playFavoriteSong(song: PlaylistSong) {
        scope.launch {
            val url = resolvePlaySource(song.songId, song.platform, song.quality, song.playUrl) ?: return@launch
            val meta = SongMetadata(
                songId = song.songId, songName = song.songName, artist = song.artist,
                platform = song.platform, quality = song.quality, coverUrl = song.coverUrl,
                duration = song.duration, realName = song.realName, realArtist = song.realArtist,
            )
            musicPlayer.play(url = url, title = meta.songName, artist = meta.artist, song = meta)
            // Auto-add to current playlist
            playlistManager.addToPlaylistFirst(meta, url)
            _playlistSongIds.value = _playlistSongIds.value + "${song.songId}|${song.platform}"
            _playlist.value = playlistManager.getPlaylist()
            // 30s history timer
            scope.launch {
                delay(30_000)
                if (musicPlayer.isPlaying.value &&
                    musicPlayer.currentSong.value?.songId == meta.songId) {
                    playlistManager.addToHistory(meta, url)
                    _history.value = playlistManager.getHistory(settingsRepo.getHistoryCount())
                }
            }
        }
    }

    /** Reorder favorites via drag-and-drop. */
    fun moveFavoriteSong(fromIndex: Int, toIndex: Int) {
        scope.launch {
            playlistManager.moveFavoriteSong(fromIndex, toIndex)
            _favorites.value = playlistManager.getFavorites()
        }
    }

    /** Remove a song from favorites (also unlikes in behavior tracker). */
    fun removeFavoriteSong(songId: String, platform: String) {
        scope.launch {
            playlistManager.removeFromFavorites(songId, platform)
            _favoriteSongIds.value = _favoriteSongIds.value - "$songId|$platform"
            loadFavorites()
            // 同步取消 behavior tracker 中的喜欢状态
            behaviorTracker.toggleLike(songId, platform, "", "")
            // 通知 MainViewModel 刷新喜欢状态
            onFavoriteChanged?.invoke(songId, platform)
        }
    }

    /** Clear all favorites (also unlikes all in behavior tracker). */
    fun clearFavorites() {
        scope.launch {
            // 清除所有喜欢前，先同步 unlike 到 behavior tracker
            val allFavorites = playlistManager.getFavorites()
            for (song in allFavorites) {
                behaviorTracker.toggleLike(song.songId, song.platform, "", "")
            }
            playlistManager.clearFavorites()
            _favoriteSongIds.value = emptySet()
            loadFavorites()
            // 通知 MainViewModel 刷新（传空表示全部刷新）
            onFavoriteChanged?.invoke("", "")
        }
    }
}
