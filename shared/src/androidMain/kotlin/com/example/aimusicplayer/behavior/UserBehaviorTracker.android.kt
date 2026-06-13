package com.example.aimusicplayer.behavior

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.datetime.Clock

// ── Factory ────────────────────────────────────────────────────────────────

fun createUserBehaviorTracker(context: Context): UserBehaviorTracker {
    val driver = AndroidSqliteDriver(BehaviorSchema, context, "behavior.db")
    return UserBehaviorTracker(driver)
}

fun createSongIndexDatabase(context: Context): SqlDriver {
    return AndroidSqliteDriver(SongIndexSchema, context, "song_index.db")
}

// ── Schema: behavior.db ────────────────────────────────────────────────────

object BehaviorSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS user_behavior (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                song_id TEXT NOT NULL,
                song_name TEXT NOT NULL,
                artist TEXT NOT NULL,
                platform TEXT NOT NULL,
                album TEXT,
                genre TEXT,
                year INTEGER,
                duration_ms INTEGER DEFAULT 0,
                play_duration_ms INTEGER DEFAULT 0,
                completed INTEGER DEFAULT 0,
                liked INTEGER DEFAULT 0,
                skipped INTEGER DEFAULT 0,
                downloaded INTEGER DEFAULT 0,
                played_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
        """, 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_behavior_song ON user_behavior(song_id, platform)", 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_behavior_artist ON user_behavior(artist)", 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_behavior_played_at ON user_behavior(played_at DESC)", 0)

        // song_index table (co-located for simplicity, accessed via LocalSongIndex)
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS song_index (
                song_id TEXT NOT NULL,
                platform TEXT NOT NULL,
                song_name TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                genre TEXT,
                year INTEGER,
                duration_ms INTEGER DEFAULT 0,
                cover_url TEXT,
                play_count_global INTEGER DEFAULT 0,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (song_id, platform)
            )
        """, 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_song_idx_artist ON song_index(artist)", 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_song_idx_genre ON song_index(genre)", 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_song_idx_play_count ON song_index(play_count_global DESC)", 0)

        return QueryResult.Value(Unit)
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> {
        return QueryResult.Value(Unit)
    }
}

// ── Schema: song_index.db (standalone) ─────────────────────────────────────

object SongIndexSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS song_index (
                song_id TEXT NOT NULL,
                platform TEXT NOT NULL,
                song_name TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                genre TEXT,
                year INTEGER,
                duration_ms INTEGER DEFAULT 0,
                cover_url TEXT,
                play_count_global INTEGER DEFAULT 0,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (song_id, platform)
            )
        """, 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_si_artist ON song_index(artist)", 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_si_genre ON song_index(genre)", 0)
        driver.execute(null,
            "CREATE INDEX IF NOT EXISTS idx_si_play_count ON song_index(play_count_global DESC)", 0)
        return QueryResult.Value(Unit)
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> {
        return QueryResult.Value(Unit)
    }
}

// ── Actual class ───────────────────────────────────────────────────────────

actual class UserBehaviorTracker(private val db: SqlDriver) {

    private var currentSongId: String? = null
    private var currentPlatform: String? = null
    private var playStartMs: Long = 0L

    actual suspend fun recordPlayStart(song: SongMetadata, totalDurationMs: Long) {
        // 如果之前有未结束的播放，先结束它
        currentSongId?.let { prevId ->
            if (prevId != song.songId || currentPlatform != song.platform) {
                val elapsed = Clock.System.now().toEpochMilliseconds() - playStartMs
                finishPlay(elapsed, skipThreshold = 0.3f)
            }
        }

        currentSongId = song.songId
        currentPlatform = song.platform
        playStartMs = Clock.System.now().toEpochMilliseconds()

        val now = playStartMs
        db.execute(null, """
            INSERT INTO user_behavior (song_id, song_name, artist, platform, album, genre, year,
                                        duration_ms, play_duration_ms, completed, liked, skipped, downloaded,
                                        played_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, ?, ?)
        """, 15) {
            bindString(0, song.songId)
            bindString(1, song.songName)
            bindString(2, song.artist)
            bindString(3, song.platform)
            bindString(4, song.album ?: "")
            bindString(5, "")  // genre — SongMetadata 不含此字段，后续从 MusicBrainz 补充
            bindLong(6, 0L)    // year — SongMetadata 不含此字段
            bindLong(7, totalDurationMs)  // duration_ms
            bindLong(8, now)   // played_at
            bindLong(9, now)   // created_at
            // position 10-14 are the zero defaults from VALUES clause
        }

        // Also upsert into song_index for local accumulation
        db.execute(null, """
            INSERT OR IGNORE INTO song_index (song_id, platform, song_name, artist, album,
                                               genre, year, duration_ms, cover_url, source, created_at)
            VALUES (?, ?, ?, ?, ?, '', 0, ?, '', 'history', ?)
        """, 7) {
            bindString(0, song.songId)
            bindString(1, song.platform)
            bindString(2, song.songName)
            bindString(3, song.artist)
            bindString(4, song.album ?: "")
            bindLong(5, if (song.duration > 0) song.duration.toLong() else totalDurationMs)
            bindLong(6, now)
        }
    }

    actual suspend fun recordPlayEnd(actualPlayMs: Long, skipThreshold: Float) {
        finishPlay(actualPlayMs, skipThreshold)
    }

    private fun finishPlay(actualPlayMs: Long, skipThreshold: Float) {
        val songId = currentSongId ?: return
        val platform = currentPlatform ?: return

        // Find the latest behavior row for this play session (the one we just inserted)
        val cursorResult = db.executeQuery(null, """
            SELECT id, duration_ms FROM user_behavior
            WHERE song_id = ? AND platform = ?
            ORDER BY played_at DESC LIMIT 1
        """, { cursor ->
            if (cursor.next().getValue()) {
                QueryResult.Value(cursor.getLong(0) to cursor.getLong(1))
            } else QueryResult.Value(null as Pair<Long, Long>?)
        }, 2) {
            bindString(0, songId)
            bindString(1, platform)
        }

        val pair = (cursorResult as QueryResult.Value<*>).value as? Pair<Long, Long> ?: return
        val id = (pair.first as? Long) ?: return
        val durationMs = (pair.second as? Long) ?: 0L

        val completed = durationMs > 0 && actualPlayMs >= durationMs * 0.9
        val skipped = !completed && durationMs > 0 && actualPlayMs < durationMs * skipThreshold

        db.execute(null, """
            UPDATE user_behavior
            SET play_duration_ms = ?, completed = ?, skipped = ?
            WHERE id = ?
        """, 4) {
            bindLong(0, actualPlayMs)
            bindLong(1, if (completed) 1L else 0L)
            bindLong(2, if (skipped) 1L else 0L)
            bindLong(3, id)
        }

        currentSongId = null
        currentPlatform = null
    }

    actual suspend fun toggleLike(songId: String, platform: String, songName: String, artist: String): Boolean {
        // 查找最近的该歌曲行为记录，切换 liked 状态
        val currentState = db.executeQuery(null, """
            SELECT id, liked FROM user_behavior
            WHERE song_id = ? AND platform = ?
            ORDER BY played_at DESC LIMIT 1
        """, { cursor ->
            if (cursor.next().getValue()) {
                QueryResult.Value(cursor.getLong(0) to cursor.getLong(1))
            } else QueryResult.Value(null as Pair<Long, Long>?)
        }, 2) {
            bindString(0, songId)
            bindString(1, platform)
        }

        val pair = (currentState as QueryResult.Value<*>).value as? Pair<Long, Long>
        val newLiked = if (pair != null) {
            val id = pair.first
            val old = pair.second == 1L
            val toggled = if (old) 0L else 1L
            db.execute(null, "UPDATE user_behavior SET liked = ? WHERE id = ?", 2) {
                bindLong(0, toggled)
                bindLong(1, id)
            }
            toggled == 1L
        } else {
            // 没有播放记录但有喜欢操作：创建一条仅标记喜欢的记录，使用传入的歌名/歌手
            val now = Clock.System.now().toEpochMilliseconds()
            db.execute(null, """
                INSERT INTO user_behavior (song_id, song_name, artist, platform,
                                            duration_ms, play_duration_ms, completed, liked, skipped, downloaded,
                                            played_at, created_at)
                VALUES (?, ?, ?, ?, 0, 0, 0, 1, 0, 0, ?, ?)
            """, 9) {
                bindString(0, songId)
                bindString(1, songName)
                bindString(2, artist)
                bindString(3, platform)
                bindLong(4, now)
                bindLong(5, now)
            }
            true
        }
        return newLiked
    }

    actual suspend fun isLiked(songId: String, platform: String): Boolean {
        val result = db.executeQuery(null, """
            SELECT liked FROM user_behavior
            WHERE song_id = ? AND platform = ?
            ORDER BY played_at DESC LIMIT 1
        """, { cursor ->
            if (cursor.next().getValue()) {
                QueryResult.Value(cursor.getLong(0) == 1L)
            } else QueryResult.Value(false)
        }, 2) {
            bindString(0, songId)
            bindString(1, platform)
        }
        return (result as QueryResult.Value<Boolean>).value
    }

    actual suspend fun recordDownload(song: SongMetadata) {
        // Update latest behavior row or insert a new one
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = db.executeQuery(null, """
            SELECT id FROM user_behavior
            WHERE song_id = ? AND platform = ?
            ORDER BY played_at DESC LIMIT 1
        """, { cursor ->
            if (cursor.next().getValue()) QueryResult.Value(cursor.getLong(0))
            else QueryResult.Value<Long?>(null)
        }, 2) {
            bindString(0, song.songId)
            bindString(1, song.platform)
        }

        val rowId = (existing as? QueryResult.Value<*>)?.value as? Long
        if (rowId != null) {
            db.execute(null, "UPDATE user_behavior SET downloaded = 1 WHERE id = ?", 1) {
                bindLong(0, rowId)
            }
        } else {
            db.execute(null, """
                INSERT INTO user_behavior (song_id, song_name, artist, platform,
                                            duration_ms, play_duration_ms, completed, liked, skipped, downloaded,
                                            played_at, created_at)
                VALUES (?, ?, ?, ?, 0, 0, 0, 0, 0, 1, ?, ?)
            """, 9) {
                bindString(0, song.songId)
                bindString(1, song.songName)
                bindString(2, song.artist)
                bindString(3, song.platform)
                bindLong(4, now)
                bindLong(5, now)
            }
        }
    }

    actual suspend fun getSkipCounts(songIds: List<Pair<String, String>>): Map<String, Int> {
        if (songIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Int>()
        for ((songId, platform) in songIds) {
            val count = db.executeQuery(null, """
                SELECT COUNT(*) FROM user_behavior
                WHERE song_id = ? AND platform = ? AND skipped = 1
            """, { cursor ->
                if (cursor.next().getValue()) QueryResult.Value((cursor.getLong(0) ?: 0L).toInt())
                else QueryResult.Value(0)
            }, 2) {
                bindString(0, songId)
                bindString(1, platform)
            }
            val c = (count as QueryResult.Value<Int>).value
            if (c > 0) result["${songId}|${platform}"] = c
        }
        return result
    }

    actual suspend fun getAllBehaviors(): List<UserBehavior> {
        return db.executeQuery(null, """
            SELECT * FROM user_behavior ORDER BY played_at DESC
        """, { cursor ->
            val items = mutableListOf<UserBehavior>()
            while (cursor.next().getValue()) {
                items.add(readBehavior(cursor))
            }
            QueryResult.Value(items)
        }, 0).getValue()
    }

    actual suspend fun getBehaviorCount(): Int {
        val result = db.executeQuery(null, "SELECT COUNT(*) FROM user_behavior", { cursor ->
            if (cursor.next().getValue()) QueryResult.Value((cursor.getLong(0) ?: 0L).toInt())
            else QueryResult.Value(0)
        }, 0)
        return (result as QueryResult.Value<Int>).value
    }

    actual suspend fun clearAll() {
        db.execute(null, "DELETE FROM user_behavior", 0)
        db.execute(null, "DELETE FROM song_index", 0)
    }

    private fun readBehavior(cursor: app.cash.sqldelight.db.SqlCursor): UserBehavior {
        return UserBehavior(
            id = cursor.getLong(0) ?: 0L,
            songId = cursor.getString(1) ?: "",
            songName = cursor.getString(2) ?: "",
            artist = cursor.getString(3) ?: "",
            platform = cursor.getString(4) ?: "",
            album = cursor.getString(5)?.ifBlank { null },
            genre = cursor.getString(6)?.ifBlank { null },
            year = cursor.getLong(7)?.toInt(),
            durationMs = cursor.getLong(8) ?: 0L,
            playDurationMs = cursor.getLong(9) ?: 0L,
            completed = (cursor.getLong(10) ?: 0L) == 1L,
            liked = (cursor.getLong(11) ?: 0L) == 1L,
            skipped = (cursor.getLong(12) ?: 0L) == 1L,
            downloaded = (cursor.getLong(13) ?: 0L) == 1L,
            playedAt = cursor.getLong(14) ?: 0L,
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> QueryResult<T>.getValue(): T =
    (this as QueryResult.Value<T>).value
