package com.example.aimusicplayer.music

import com.example.aimusicplayer.import.ImportedPlaylist
import com.example.aimusicplayer.import.ImportedSong
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.datetime.Clock

actual class PlaylistManager {
    private val playlist = mutableListOf<PlaylistSong>()
    private val history = mutableListOf<HistorySong>()
    private var playMode: PlayMode = PlayMode.SEQUENTIAL
    private val importedPlaylists = mutableListOf<ImportedPlaylist>()
    private val favorites = mutableListOf<PlaylistSong>()

    actual fun getPlayMode(): PlayMode = playMode
    actual fun setPlayMode(mode: PlayMode) { playMode = mode }

    actual suspend fun addToPlaylist(song: SongMetadata, playUrl: String?) {
        if (playlist.none { it.songId == song.songId && it.platform == song.platform }) {
            playlist.add(PlaylistSong(
                songId = song.songId,
                songName = song.songName,
                artist = song.artist,
                platform = song.platform,
                coverUrl = song.coverUrl,
                playUrl = playUrl,
                quality = song.quality,
                addedAt = Clock.System.now().toEpochMilliseconds(),
                order = playlist.size,
                realName = song.realName,
                realArtist = song.realArtist,
            ))
            while (playlist.size > MAX_PLAYLIST_SIZE) playlist.removeAt(0)
        }
    }

    actual suspend fun addToPlaylistFirst(song: SongMetadata, playUrl: String?) {
        if (playlist.none { it.songId == song.songId && it.platform == song.platform }) {
            playlist.add(0, PlaylistSong(
                songId = song.songId,
                songName = song.songName,
                artist = song.artist,
                platform = song.platform,
                coverUrl = song.coverUrl,
                playUrl = playUrl,
                quality = song.quality,
                addedAt = Clock.System.now().toEpochMilliseconds(),
                order = 0,
                realName = song.realName,
                realArtist = song.realArtist,
            ))
            playlist.forEachIndexed { i, s -> playlist[i] = s.copy(order = i) }
            while (playlist.size > MAX_PLAYLIST_SIZE) playlist.removeAt(playlist.lastIndex)
        }
    }

    companion object {
        private const val MAX_PLAYLIST_SIZE = 50
    }

    actual suspend fun removeFromPlaylist(songId: String, platform: String) {
        playlist.removeAll { it.songId == songId && it.platform == platform }
    }

    actual suspend fun moveSong(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex < 0 || fromIndex >= playlist.size) return
        val clampedToIndex = toIndex.coerceIn(0, playlist.size - 1)
        if (fromIndex == clampedToIndex) return
        val song = playlist.removeAt(fromIndex)
        playlist.add(clampedToIndex, song)
        playlist.forEachIndexed { i, s -> playlist[i] = s.copy(order = i) }
    }

    actual suspend fun getPlaylist(): List<PlaylistSong> = playlist.toList()

    actual suspend fun isInPlaylist(songId: String, platform: String): Boolean = playlist.any { it.songId == songId && it.platform == platform }

    actual suspend fun addToHistory(song: SongMetadata, playUrl: String?) {
        history.add(0, HistorySong(
            songId = song.songId,
            songName = song.songName,
            artist = song.artist,
            platform = song.platform,
            coverUrl = song.coverUrl,
            playUrl = playUrl,
            quality = song.quality,
            playedAt = Clock.System.now().toEpochMilliseconds(),
            realName = song.realName,
            realArtist = song.realArtist,
        ))
    }

    actual suspend fun getHistory(maxCount: Int): List<HistorySong> = history.take(maxCount)

    actual suspend fun clearHistory() { history.clear() }

    actual suspend fun clearPlaylist() { playlist.clear() }

    // ── Imported playlists (in-memory) ─────────────────────────

    actual suspend fun addImportedPlaylist(playlist: ImportedPlaylist) {
        importedPlaylists.removeAll { it.id == playlist.id }
        importedPlaylists.add(0, playlist)
    }

    actual suspend fun getImportedPlaylists(): List<ImportedPlaylist> =
        importedPlaylists.toList()

    actual suspend fun removeImportedPlaylist(id: String) {
        importedPlaylists.removeAll { it.id == id }
    }

    actual suspend fun removeImportedSong(playlistId: String, songIndex: Int) {
        val idx = importedPlaylists.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val playlist = importedPlaylists[idx]
            if (songIndex in playlist.songs.indices) {
                val mutableSongs = playlist.songs.toMutableList()
                mutableSongs.removeAt(songIndex)
                importedPlaylists[idx] = playlist.copy(songs = mutableSongs)
            }
        }
    }

    // ── Favorites ──────────────────────────────────────────────

    actual suspend fun addToFavorites(song: SongMetadata) {
        if (favorites.none { it.songId == song.songId && it.platform == song.platform }) {
            favorites.add(0, PlaylistSong(
                songId = song.songId,
                songName = song.songName,
                artist = song.artist,
                platform = song.platform,
                coverUrl = song.coverUrl,
                playUrl = null,
                quality = song.quality,
                duration = song.duration,
                addedAt = Clock.System.now().toEpochMilliseconds(),
                order = 0,
                realName = song.realName,
                realArtist = song.realArtist,
            ))
        }
    }

    actual suspend fun removeFromFavorites(songId: String, platform: String) {
        favorites.removeAll { it.songId == songId && it.platform == platform }
    }

    actual suspend fun getFavorites(): List<PlaylistSong> = favorites.toList()

    actual suspend fun clearFavorites() { favorites.clear() }

    actual suspend fun moveFavoriteSong(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= favorites.size) return
        val clampedTo = toIndex.coerceIn(0, favorites.size - 1)
        if (fromIndex == clampedTo) return
        val song = favorites.removeAt(fromIndex)
        favorites.add(clampedTo, song)
    }

    actual suspend fun isFavorite(songId: String, platform: String): Boolean =
        favorites.any { it.songId == songId && it.platform == platform }
}
