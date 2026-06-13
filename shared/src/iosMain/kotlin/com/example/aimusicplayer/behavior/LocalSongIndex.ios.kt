package com.example.aimusicplayer.behavior

/**
 * iOS 端歌曲索引 —— 纯内存实现（与 Android SQLite 版本接口一致）。
 */
actual class LocalSongIndex {
    private val entries = mutableListOf<SongIndexEntry>()

    actual suspend fun insertOrIgnore(entry: SongIndexEntry) {
        if (entries.none { it.songId == entry.songId && it.platform == entry.platform }) {
            entries.add(entry)
        }
    }

    actual suspend fun insertOrIgnoreAll(entries: List<SongIndexEntry>) {
        entries.forEach { insertOrIgnore(it) }
    }

    actual suspend fun queryByArtist(artist: String): List<SongIndexEntry> {
        return entries
            .filter { it.artist.equals(artist, ignoreCase = true) }
            .sortedByDescending { it.playCountGlobal }
    }

    actual suspend fun queryByGenre(genre: String, limit: Int): List<SongIndexEntry> {
        return entries
            .filter { it.genre.equals(genre, ignoreCase = true) }
            .sortedByDescending { it.playCountGlobal }
            .take(limit)
    }

    actual suspend fun queryHot(limit: Int): List<SongIndexEntry> {
        return entries
            .sortedByDescending { it.playCountGlobal }
            .take(limit)
    }

    actual suspend fun count(): Int = entries.size

    actual suspend fun getAll(): List<SongIndexEntry> = entries.toList()

    actual suspend fun getNonExpired(ttlHours: Int): List<SongIndexEntry> {
        val cutoff = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - ttlHours * 3600L * 1000L
        return entries.filter { it.createdAt == 0L || it.createdAt >= cutoff }
    }

    actual suspend fun removeExpired(ttlHours: Int) {
        val cutoff = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - ttlHours * 3600L * 1000L
        entries.removeAll { it.createdAt > 0 && it.createdAt < cutoff }
    }

    actual suspend fun removeByKey(songId: String, platform: String) {
        entries.removeAll { it.songId == songId && it.platform == platform }
    }

    actual suspend fun clearAll() { entries.clear() }
}
