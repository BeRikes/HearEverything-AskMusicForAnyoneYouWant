package com.example.aimusicplayer.behavior

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

// ── Factory (hides SqlDriver from consumer modules) ──────────────────────

fun createLocalSongIndex(context: Context): LocalSongIndex {
    val driver = AndroidSqliteDriver(SongIndexSchema, context, "song_index.db")
    return LocalSongIndex(driver)
}

// ── Schema (shared with UserBehaviorTracker.android.kt) ──────────────────

actual class LocalSongIndex(private val db: SqlDriver) {

    actual suspend fun insertOrIgnore(entry: SongIndexEntry) {
        db.execute(null, """
            INSERT OR IGNORE INTO song_index (song_id, platform, song_name, artist, album,
                                               genre, year, duration_ms, cover_url, play_count_global,
                                               source, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, 12) {
            bindString(0, entry.songId)
            bindString(1, entry.platform)
            bindString(2, entry.songName)
            bindString(3, entry.artist)
            bindString(4, entry.album ?: "")
            bindString(5, entry.genre ?: "")
            if (entry.year != null) bindLong(6, entry.year.toLong()) else bindLong(6, 0)
            bindLong(7, entry.durationMs)
            bindString(8, entry.coverUrl ?: "")
            bindLong(9, entry.playCountGlobal)
            bindString(10, entry.source)
            bindLong(11, kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
        }
    }

    actual suspend fun insertOrIgnoreAll(entries: List<SongIndexEntry>) {
        entries.forEach { insertOrIgnore(it) }
    }

    actual suspend fun queryByArtist(artist: String): List<SongIndexEntry> {
        return db.executeQuery(null, """
            SELECT * FROM song_index WHERE artist = ? ORDER BY play_count_global DESC
        """, { cursor -> readAll(cursor) }, 1) {
            bindString(0, artist)
        }.getValue()
    }

    actual suspend fun queryByGenre(genre: String, limit: Int): List<SongIndexEntry> {
        return db.executeQuery(null, """
            SELECT * FROM song_index WHERE genre = ? ORDER BY play_count_global DESC LIMIT ?
        """, { cursor -> readAll(cursor) }, 2) {
            bindString(0, genre)
            bindLong(1, limit.toLong())
        }.getValue()
    }

    actual suspend fun queryHot(limit: Int): List<SongIndexEntry> {
        return db.executeQuery(null, """
            SELECT * FROM song_index ORDER BY play_count_global DESC LIMIT ?
        """, { cursor -> readAll(cursor) }, 1) {
            bindLong(0, limit.toLong())
        }.getValue()
    }

    actual suspend fun count(): Int {
        val result = db.executeQuery(null, "SELECT COUNT(*) FROM song_index", { cursor ->
            if (cursor.next().getValue()) QueryResult.Value((cursor.getLong(0) ?: 0L).toInt())
            else QueryResult.Value(0)
        }, 0)
        return (result as QueryResult.Value<Int>).value
    }

    actual suspend fun getAll(): List<SongIndexEntry> {
        return db.executeQuery(null, "SELECT * FROM song_index", { cursor ->
            readAll(cursor)
        }, 0).getValue()
    }

    actual suspend fun getNonExpired(ttlHours: Int): List<SongIndexEntry> {
        val cutoff = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - ttlHours * 3600L * 1000L
        return db.executeQuery(null,
            "SELECT * FROM song_index WHERE created_at >= ? OR created_at = 0",
            { cursor -> readAll(cursor) }, 1
        ) { bindLong(0, cutoff) }.getValue()
    }

    actual suspend fun removeExpired(ttlHours: Int) {
        val cutoff = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - ttlHours * 3600L * 1000L
        db.execute(null, "DELETE FROM song_index WHERE created_at > 0 AND created_at < ?", 1) {
            bindLong(0, cutoff)
        }
    }

    actual suspend fun removeByKey(songId: String, platform: String) {
        db.execute(null, "DELETE FROM song_index WHERE song_id = ? AND platform = ?", 2) {
            bindString(0, songId)
            bindString(1, platform)
        }
    }

    actual suspend fun clearAll() {
        db.execute(null, "DELETE FROM song_index", 0)
    }

    private fun readAll(cursor: app.cash.sqldelight.db.SqlCursor): QueryResult.Value<List<SongIndexEntry>> {
        val items = mutableListOf<SongIndexEntry>()
        while (cursor.next().getValue()) {
            items.add(SongIndexEntry(
                songId = cursor.getString(0) ?: "",
                platform = cursor.getString(1) ?: "",
                songName = cursor.getString(2) ?: "",
                artist = cursor.getString(3) ?: "",
                album = cursor.getString(4)?.ifBlank { null },
                genre = cursor.getString(5)?.ifBlank { null },
                year = cursor.getLong(6)?.toInt(),
                durationMs = cursor.getLong(7) ?: 0L,
                coverUrl = cursor.getString(8)?.ifBlank { null },
                playCountGlobal = cursor.getLong(9) ?: 0L,
                source = cursor.getString(10) ?: "unknown",
                createdAt = cursor.getLong(11) ?: 0L,
            ))
        }
        return QueryResult.Value(items)
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> QueryResult<T>.getValue(): T =
    (this as QueryResult.Value<T>).value
