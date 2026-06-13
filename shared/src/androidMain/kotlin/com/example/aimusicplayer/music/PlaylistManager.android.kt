package com.example.aimusicplayer.music

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.aimusicplayer.import.ImportedPlaylist
import com.example.aimusicplayer.import.ImportedSong
import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.datetime.Clock

fun createPlaylistManager(context: Context): PlaylistManager {
    val driver = AndroidSqliteDriver(PlaylistManagerSchema, context, "playlist.db")
    return PlaylistManager(driver)
}

// Schema object needed by AndroidSqliteDriver
object PlaylistManagerSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 4

    private val CREATE_IMPORTED_PLAYLIST = """
        CREATE TABLE IF NOT EXISTS imported_playlist (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            cover_url TEXT,
            platform TEXT NOT NULL,
            source_url TEXT NOT NULL,
            imported_at INTEGER NOT NULL
        )
    """.trimIndent()

    private val CREATE_IMPORTED_SONG = """
        CREATE TABLE IF NOT EXISTS imported_song (
            id TEXT PRIMARY KEY,
            playlist_id TEXT NOT NULL REFERENCES imported_playlist(id),
            original_name TEXT NOT NULL,
            original_artist TEXT NOT NULL,
            matched_song_id TEXT,
            matched_name TEXT,
            matched_artist TEXT,
            matched_platform TEXT,
            play_url TEXT,
            cover_url TEXT,
            quality TEXT,
            sort_order INTEGER NOT NULL
        )
    """.trimIndent()

    private val CREATE_FAVORITES = """
        CREATE TABLE IF NOT EXISTS favorites (
            song_id TEXT NOT NULL,
            song_name TEXT NOT NULL,
            artist TEXT NOT NULL DEFAULT '',
            platform TEXT NOT NULL,
            cover_url TEXT,
            duration INTEGER NOT NULL DEFAULT 0,
            quality TEXT NOT NULL DEFAULT 'standard',
            liked_at INTEGER NOT NULL,
            real_name TEXT,
            real_artist TEXT,
            PRIMARY KEY (song_id, platform)
        )
    """.trimIndent()

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS playlist (
                song_id TEXT PRIMARY KEY,
                song_name TEXT NOT NULL,
                artist TEXT NOT NULL DEFAULT '',
                platform TEXT NOT NULL,
                cover_url TEXT,
                play_url TEXT,
                quality TEXT NOT NULL DEFAULT 'standard',
                added_at INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                real_name TEXT,
                real_artist TEXT
            )
        """, 0)
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS history (
                song_id TEXT NOT NULL,
                song_name TEXT NOT NULL,
                artist TEXT NOT NULL DEFAULT '',
                platform TEXT NOT NULL,
                cover_url TEXT,
                play_url TEXT,
                quality TEXT NOT NULL DEFAULT 'standard',
                played_at INTEGER NOT NULL,
                real_name TEXT,
                real_artist TEXT
            )
        """, 0)
        driver.execute(null, """
            CREATE INDEX IF NOT EXISTS idx_history_played_at ON history(played_at DESC)
        """, 0)
        driver.execute(null, """
            CREATE TABLE IF NOT EXISTS playlist_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """, 0)
        driver.execute(null, CREATE_IMPORTED_PLAYLIST, 0)
        driver.execute(null, CREATE_IMPORTED_SONG, 0)
        driver.execute(null, CREATE_FAVORITES, 0)
        return QueryResult.Value(Unit)
    }
    override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion): QueryResult.Value<Unit> {
        // v1 → v2: add imported playlist tables
        if (oldVersion < 2) {
            driver.execute(null, CREATE_IMPORTED_PLAYLIST, 0)
            driver.execute(null, CREATE_IMPORTED_SONG, 0)
        }
        // v2 → v3: add realName/realArtist to playlist and history
        if (oldVersion < 3) {
            driver.execute(null, "ALTER TABLE playlist ADD COLUMN real_name TEXT", 0)
            driver.execute(null, "ALTER TABLE playlist ADD COLUMN real_artist TEXT", 0)
            driver.execute(null, "ALTER TABLE history ADD COLUMN real_name TEXT", 0)
            driver.execute(null, "ALTER TABLE history ADD COLUMN real_artist TEXT", 0)
        }
        // v3 → v4: add favorites table
        if (oldVersion < 4) {
            driver.execute(null, CREATE_FAVORITES, 0)
        }
        return QueryResult.Value(Unit)
    }
}

actual class PlaylistManager(driver: SqlDriver) {
    private val db = driver
    private var cachedPlayMode: PlayMode = loadPlayMode()

    private fun loadPlayMode(): PlayMode {
        return try {
            // Ensure table exists (belt-and-suspenders for migrated DBs)
            db.execute(null, "CREATE TABLE IF NOT EXISTS playlist_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)", 0)
            val result = db.executeQuery(null, "SELECT value FROM playlist_settings WHERE key = 'play_mode'", { cursor ->
                QueryResult.Value(if (cursor.next().getValue()) cursor.getString(0) else null)
            }, 0).getValue()
            when (result) {
                "LOOP" -> PlayMode.LOOP
                else -> PlayMode.SEQUENTIAL
            }
        } catch (_: Exception) {
            PlayMode.SEQUENTIAL
        }
    }

    actual fun getPlayMode(): PlayMode = cachedPlayMode

    actual fun setPlayMode(mode: PlayMode) {
        cachedPlayMode = mode
        try {
            db.execute(null, """
                INSERT OR REPLACE INTO playlist_settings (key, value) VALUES ('play_mode', ?)
            """, 1) {
                bindString(0, mode.name)
            }
        } catch (_: Exception) { /* best-effort persistence */ }
    }

    actual suspend fun addToPlaylist(song: SongMetadata, playUrl: String?) {
        if (isInPlaylist(song.songId, song.platform)) return // dedup: consistent with iOS behavior
        val maxOrder = getMaxOrder()
        db.execute(null, """
            INSERT OR IGNORE INTO playlist (song_id, song_name, artist, platform, cover_url, play_url, quality, added_at, sort_order, real_name, real_artist)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, 11) {
            bindString(0, song.songId)
            bindString(1, song.songName)
            bindString(2, song.artist)
            bindString(3, song.platform)
            bindString(4, song.coverUrl ?: "")
            bindString(5, playUrl ?: "")
            bindString(6, song.quality)
            bindLong(7, Clock.System.now().toEpochMilliseconds())
            bindLong(8, (maxOrder + 1).toLong())
            bindString(9, song.realName ?: "")
            bindString(10, song.realArtist ?: "")
        }
        trimPlaylist()
    }

    actual suspend fun addToPlaylistFirst(song: SongMetadata, playUrl: String?) {
        if (isInPlaylist(song.songId, song.platform)) return
        // Shift all existing songs down by 1, insert new at sort_order = 0
        db.execute(null, "UPDATE playlist SET sort_order = sort_order + 1", 0)
        db.execute(null, """
            INSERT OR IGNORE INTO playlist (song_id, song_name, artist, platform, cover_url, play_url, quality, added_at, sort_order, real_name, real_artist)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
        """, 10) {
            bindString(0, song.songId)
            bindString(1, song.songName)
            bindString(2, song.artist)
            bindString(3, song.platform)
            bindString(4, song.coverUrl ?: "")
            bindString(5, playUrl ?: "")
            bindString(6, song.quality)
            bindLong(7, Clock.System.now().toEpochMilliseconds())
            bindString(8, song.realName ?: "")
            bindString(9, song.realArtist ?: "")
        }
        trimPlaylist()
    }

    actual suspend fun removeFromPlaylist(songId: String, platform: String) {
        db.execute(null, "DELETE FROM playlist WHERE song_id = ? AND platform = ?", 2) {
            bindString(0, songId)
            bindString(1, platform)
        }
    }

    actual suspend fun moveSong(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val playlist = getPlaylist()
        if (fromIndex < 0 || fromIndex >= playlist.size) return
        val clampedToIndex = toIndex.coerceIn(0, playlist.size - 1)
        if (fromIndex == clampedToIndex) return
        val mutable = playlist.toMutableList()
        val song = mutable.removeAt(fromIndex)
        mutable.add(clampedToIndex, song)
        mutable.forEachIndexed { index, s ->
            db.execute(null, "UPDATE playlist SET sort_order = ? WHERE song_id = ?", 2) {
                bindLong(0, index.toLong())
                bindString(1, s.songId)
            }
        }
    }

    actual suspend fun getPlaylist(): List<PlaylistSong> {
        return db.executeQuery(null, "SELECT * FROM playlist ORDER BY sort_order ASC", { cursor ->
            val items = mutableListOf<PlaylistSong>()
            while (cursor.next().getValue()) {
                items.add(PlaylistSong(
                    songId = cursor.getString(0)!!,
                    songName = cursor.getString(1)!!,
                    artist = cursor.getString(2) ?: "",
                    platform = cursor.getString(3)!!,
                    coverUrl = cursor.getString(4)?.ifBlank { null },
                    playUrl = cursor.getString(5)?.ifBlank { null },
                    quality = cursor.getString(6) ?: "standard",
                    addedAt = cursor.getLong(7) ?: 0L,
                    order = (cursor.getLong(8) ?: 0L).toInt(),
                    realName = cursor.getString(9)?.ifBlank { null },
                    realArtist = cursor.getString(10)?.ifBlank { null },
                ))
            }
            QueryResult.Value(items)
        }, 0).getValue()
    }

    actual suspend fun isInPlaylist(songId: String, platform: String): Boolean {
        val result = db.executeQuery(null, "SELECT 1 FROM playlist WHERE song_id = ? AND platform = ?", { cursor ->
            QueryResult.Value(cursor.next().getValue())
        }, 2) {
            bindString(0, songId)
            bindString(1, platform)
        }.getValue()
        return result
    }

    actual suspend fun addToHistory(song: SongMetadata, playUrl: String?) {
        db.execute(null, """
            INSERT INTO history (song_id, song_name, artist, platform, cover_url, play_url, quality, played_at, real_name, real_artist)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, 10) {
            bindString(0, song.songId)
            bindString(1, song.songName)
            bindString(2, song.artist)
            bindString(3, song.platform)
            bindString(4, song.coverUrl ?: "")
            bindString(5, playUrl ?: "")
            bindString(6, song.quality)
            bindLong(7, Clock.System.now().toEpochMilliseconds())
            bindString(8, song.realName ?: "")
            bindString(9, song.realArtist ?: "")
        }
    }

    actual suspend fun getHistory(maxCount: Int): List<HistorySong> {
        val limit = maxCount.coerceIn(1, 1000)
        return db.executeQuery(null, "SELECT * FROM history ORDER BY played_at DESC LIMIT ?", { cursor ->
            val items = mutableListOf<HistorySong>()
            while (cursor.next().getValue()) {
                items.add(HistorySong(
                    songId = cursor.getString(0)!!,
                    songName = cursor.getString(1)!!,
                    artist = cursor.getString(2) ?: "",
                    platform = cursor.getString(3)!!,
                    coverUrl = cursor.getString(4)?.ifBlank { null },
                    playUrl = cursor.getString(5)?.ifBlank { null },
                    quality = cursor.getString(6) ?: "standard",
                    playedAt = cursor.getLong(7) ?: 0L,
                    realName = cursor.getString(8)?.ifBlank { null },
                    realArtist = cursor.getString(9)?.ifBlank { null },
                ))
            }
            QueryResult.Value(items)
        }, 1) { bindLong(0, limit.toLong()) }.getValue()
    }

    actual suspend fun clearHistory() {
        db.execute(null, "DELETE FROM history", 0)
    }

    actual suspend fun clearPlaylist() {
        db.execute(null, "DELETE FROM playlist", 0)
    }

    // ── Imported playlists ──────────────────────────────────────

    actual suspend fun addImportedPlaylist(playlist: ImportedPlaylist) {
        db.execute(null, """
            INSERT OR REPLACE INTO imported_playlist (id, title, cover_url, platform, source_url, imported_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """, 6) {
            bindString(0, playlist.id)
            bindString(1, playlist.title)
            bindString(2, playlist.coverUrl ?: "")
            bindString(3, playlist.platform)
            bindString(4, playlist.sourceUrl)
            bindLong(5, playlist.importedAt)
        }
        playlist.songs.forEachIndexed { index, song ->
            db.execute(null, """
                INSERT OR REPLACE INTO imported_song (id, playlist_id, original_name, original_artist, matched_song_id, matched_name, matched_artist, matched_platform, play_url, cover_url, quality, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, 12) {
                bindString(0, "${playlist.id}_$index")
                bindString(1, playlist.id)
                bindString(2, song.originalName)
                bindString(3, song.originalArtist)
                bindString(4, song.matchedSongId ?: "")
                bindString(5, song.matchedName ?: "")
                bindString(6, song.matchedArtist ?: "")
                bindString(7, song.matchedPlatform ?: "")
                bindString(8, song.playUrl ?: "")
                bindString(9, song.coverUrl ?: "")
                bindString(10, song.quality ?: "")
                bindLong(11, index.toLong())
            }
        }
    }

    actual suspend fun getImportedPlaylists(): List<ImportedPlaylist> {
        val playlists = db.executeQuery(null, "SELECT * FROM imported_playlist ORDER BY imported_at DESC", { cursor ->
            val items = mutableListOf<ImportedPlaylist>()
            while (cursor.next().getValue()) {
                items.add(ImportedPlaylist(
                    id = cursor.getString(0)!!,
                    title = cursor.getString(1)!!,
                    coverUrl = cursor.getString(2)?.ifBlank { null },
                    platform = cursor.getString(3)!!,
                    sourceUrl = cursor.getString(4)!!,
                    importedAt = cursor.getLong(5) ?: 0L,
                    songs = emptyList(),
                ))
            }
            QueryResult.Value(items)
        }, 0).getValue()

        return playlists.map { playlist ->
            val songs = db.executeQuery(null, "SELECT * FROM imported_song WHERE playlist_id = ? ORDER BY sort_order ASC", { cursor ->
                val items = mutableListOf<ImportedSong>()
                while (cursor.next().getValue()) {
                    items.add(ImportedSong(
                        originalName = cursor.getString(2)!!,
                        originalArtist = cursor.getString(3)!!,
                        matchedSongId = cursor.getString(4)?.ifBlank { null },
                        matchedName = cursor.getString(5)?.ifBlank { null },
                        matchedArtist = cursor.getString(6)?.ifBlank { null },
                        matchedPlatform = cursor.getString(7)?.ifBlank { null },
                        playUrl = cursor.getString(8)?.ifBlank { null },
                        coverUrl = cursor.getString(9)?.ifBlank { null },
                        quality = cursor.getString(10)?.ifBlank { null },
                    ))
                }
                QueryResult.Value(items)
            }, 1) { bindString(0, playlist.id) }.getValue()
            playlist.copy(songs = songs)
        }
    }

    actual suspend fun removeImportedPlaylist(id: String) {
        db.execute(null, "DELETE FROM imported_song WHERE playlist_id = ?", 1) { bindString(0, id) }
        db.execute(null, "DELETE FROM imported_playlist WHERE id = ?", 1) { bindString(0, id) }
    }

    actual suspend fun removeImportedSong(playlistId: String, songIndex: Int) {
        // Resolve the DB row id at the given sort_order position, then delete by that id.
        // This is necessary because the DB id column stores "${playlistId}_${index}",
        // not the matchedSongId — matching on matchedSongId would silently fail.
        val songIds = db.executeQuery(null,
            "SELECT id FROM imported_song WHERE playlist_id = ? ORDER BY sort_order ASC",
            { cursor ->
                val ids = mutableListOf<String>()
                while (cursor.next().getValue()) {
                    ids.add(cursor.getString(0)!!)
                }
                QueryResult.Value(ids)
            }, 1) { bindString(0, playlistId) }.getValue()

        if (songIndex in songIds.indices) {
            db.execute(null, "DELETE FROM imported_song WHERE id = ?", 1) {
                bindString(0, songIds[songIndex])
            }
        }
    }

    // ── Favorites ───────────────────────────────────────────────

    actual suspend fun addToFavorites(song: SongMetadata) {
        db.execute(null, """
            INSERT OR REPLACE INTO favorites (song_id, song_name, artist, platform, cover_url, duration, quality, liked_at, real_name, real_artist)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, 10) {
            bindString(0, song.songId)
            bindString(1, song.songName)
            bindString(2, song.artist)
            bindString(3, song.platform)
            bindString(4, song.coverUrl ?: "")
            bindLong(5, song.duration.toLong())
            bindString(6, song.quality)
            bindLong(7, Clock.System.now().toEpochMilliseconds())
            bindString(8, song.realName ?: "")
            bindString(9, song.realArtist ?: "")
        }
    }

    actual suspend fun removeFromFavorites(songId: String, platform: String) {
        db.execute(null, "DELETE FROM favorites WHERE song_id = ? AND platform = ?", 2) {
            bindString(0, songId)
            bindString(1, platform)
        }
    }

    actual suspend fun getFavorites(): List<PlaylistSong> {
        return db.executeQuery(null, "SELECT * FROM favorites ORDER BY liked_at DESC", { cursor ->
            val items = mutableListOf<PlaylistSong>()
            while (cursor.next().getValue()) {
                items.add(PlaylistSong(
                    songId = cursor.getString(0)!!,
                    songName = cursor.getString(1)!!,
                    artist = cursor.getString(2) ?: "",
                    platform = cursor.getString(3)!!,
                    coverUrl = cursor.getString(4)?.ifBlank { null },
                    playUrl = null,
                    quality = cursor.getString(6) ?: "standard",
                    duration = (cursor.getLong(5) ?: 0L).toInt(),
                    addedAt = cursor.getLong(7) ?: 0L,
                    order = 0,
                    realName = cursor.getString(8)?.ifBlank { null },
                    realArtist = cursor.getString(9)?.ifBlank { null },
                ))
            }
            QueryResult.Value(items)
        }, 0).getValue()
    }

    actual suspend fun clearFavorites() {
        db.execute(null, "DELETE FROM favorites", 0)
    }

    actual suspend fun moveFavoriteSong(fromIndex: Int, toIndex: Int) {
        val list = getFavorites().toMutableList()
        if (fromIndex < 0 || fromIndex >= list.size) return
        val clampedTo = toIndex.coerceIn(0, list.size - 1)
        if (fromIndex == clampedTo) return
        val song = list.removeAt(fromIndex)
        list.add(clampedTo, song)
        val baseTime = Clock.System.now().toEpochMilliseconds()
        list.forEachIndexed { i, s ->
            db.execute(null, "UPDATE favorites SET liked_at = ? WHERE song_id = ? AND platform = ?", 3) {
                bindLong(0, baseTime - i.toLong())
                bindString(1, s.songId)
                bindString(2, s.platform)
            }
        }
    }

    actual suspend fun isFavorite(songId: String, platform: String): Boolean {
        val result = db.executeQuery(null, "SELECT 1 FROM favorites WHERE song_id = ? AND platform = ?", { cursor ->
            QueryResult.Value(cursor.next().getValue())
        }, 2) {
            bindString(0, songId)
            bindString(1, platform)
        }.getValue()
        return result
    }

    private fun getMaxOrder(): Int {
        val result = db.executeQuery(null, "SELECT MAX(sort_order) FROM playlist", { cursor ->
            QueryResult.Value(if (cursor.next().getValue()) cursor.getLong(0)?.toInt() ?: 0 else 0)
        }, 0).getValue()
        return result
    }

    private fun trimPlaylist() {
        db.execute(null, """
            DELETE FROM playlist WHERE sort_order NOT IN (
                SELECT sort_order FROM playlist ORDER BY sort_order DESC LIMIT ?
            )
        """, 1) { bindLong(0, MAX_PLAYLIST_SIZE.toLong()) }
    }

    companion object {
        private const val MAX_PLAYLIST_SIZE = 50
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> QueryResult<T>.getValue(): T =
    (this as QueryResult.Value<T>).value
