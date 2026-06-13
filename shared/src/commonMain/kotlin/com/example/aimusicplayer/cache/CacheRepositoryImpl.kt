package com.example.aimusicplayer.cache

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

/**
 * Implementation of [CacheRepository] using raw SqlDriver (SQLDelight runtime only,
 * no Gradle plugin code generation needed).
 *
 * @param driverFactory Platform-specific SQLite driver factory.
 * @param defaultTtlSeconds Cache TTL (default 7 days). Configurable per instance.
 */
class CacheRepositoryImpl(
    driverFactory: DatabaseDriverFactory,
    private val defaultTtlSeconds: Long = CacheRepository.DEFAULT_TTL_SECONDS
) : CacheRepository {

    private val driver: SqlDriver = driverFactory.createDriver()
    private val log = Logger.withTag("CacheRepository")
    // Schema (AIMusicSchema) is auto-applied by the driver on creation

    override suspend fun getCachedSong(id: String): SongCacheEntity? {
        val now = currentTimeSeconds()
        val result: List<SongCacheEntity> = driver.executeQuery(
            identifier = null,
            sql = "SELECT id, songName, artist, platform, audioUrl, coverUrl, lyrics, quality, expireTime, createTime FROM SongCache WHERE id = ? AND expireTime > ? LIMIT 1",
            mapper = { cursor ->
                val items = mutableListOf<SongCacheEntity>()
                while (cursor.next().unwrap()) {
                    items.add(
                        SongCacheEntity(
                            id = cursor.getString(0)!!,
                            songName = cursor.getString(1)!!,
                            artist = cursor.getString(2),
                            platform = cursor.getString(3)!!,
                            audioUrl = cursor.getString(4)!!,
                            coverUrl = cursor.getString(5),
                            lyrics = cursor.getString(6),
                            quality = cursor.getString(7)!!,
                            expireTime = cursor.getLong(8)!!,
                            createTime = cursor.getLong(9)!!
                        )
                    )
                }
                QueryResult.Value(items)
            },
            parameters = 2
        ) {
            bindString(0, id)
            bindLong(1, now)
        }.unwrap()

        val cached = result.firstOrNull()
        if (cached != null) {
            log.d { "Cache HIT for id=$id" }
        } else {
            log.d { "Cache MISS for id=$id" }
        }
        return cached
    }

    override suspend fun saveSong(song: SongCacheEntity) {
        driver.execute(
            identifier = null,
            sql = """
                INSERT OR REPLACE INTO SongCache(id, songName, artist, platform, audioUrl, coverUrl, lyrics, quality, expireTime, createTime)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            parameters = 10
        ) {
            bindString(0, song.id)
            bindString(1, song.songName)
            bindString(2, song.artist)
            bindString(3, song.platform)
            bindString(4, song.audioUrl)
            bindString(5, song.coverUrl)
            bindString(6, song.lyrics)
            bindString(7, song.quality)
            bindLong(8, song.expireTime)
            bindLong(9, song.createTime)
        }
        log.d { "Saved song: id=${song.id}, songName=${song.songName}" }
    }

    override suspend fun deleteExpired() {
        val now = currentTimeSeconds()

        // Count expired entries
        val expiredCount: Long = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM SongCache WHERE expireTime <= ?",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 1
        ) { bindLong(0, now) }.unwrap()

        // Delete expired
        driver.execute(
            identifier = null,
            sql = "DELETE FROM SongCache WHERE expireTime <= ?",
            parameters = 1
        ) { bindLong(0, now) }

        driver.execute(
            identifier = null,
            sql = "DELETE FROM FailedLookup WHERE expireTime <= ?",
            parameters = 1
        ) { bindLong(0, now) }

        if (expiredCount > 0) {
            log.d { "Deleted $expiredCount expired song cache entries" }
        }
    }

    override suspend fun markAsFailed(id: String, durationHours: Int) {
        val ttlSeconds = if (durationHours > 0) {
            durationHours * 3600L
        } else {
            CacheRepository.DEFAULT_FAILED_TTL_SECONDS
        }
        val expireTime = currentTimeSeconds() + ttlSeconds
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO FailedLookup(id, expireTime) VALUES (?, ?)",
            parameters = 2
        ) {
            bindString(0, id)
            bindLong(1, expireTime)
        }
        log.d { "Marked failed lookup: id=$id, expiresIn=${durationHours}h" }
    }

    override suspend fun isFailedLookup(id: String): Boolean {
        val now = currentTimeSeconds()
        val count: Long = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM FailedLookup WHERE id = ? AND expireTime > ?",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 2
        ) {
            bindString(0, id)
            bindLong(1, now)
        }.unwrap()
        return count > 0
    }

    companion object {
        /**
         * Create a new SongCacheEntity with proper timestamps.
         */
        fun createSongEntity(
            id: String,
            songName: String,
            artist: String?,
            platform: String,
            audioUrl: String,
            coverUrl: String? = null,
            lyrics: String? = null,
            quality: String = "standard",
            ttlSeconds: Long = CacheRepository.DEFAULT_TTL_SECONDS
        ): SongCacheEntity {
            val now = currentTimeSeconds()
            return SongCacheEntity(
                id = id,
                songName = songName,
                artist = artist,
                platform = platform,
                audioUrl = audioUrl,
                coverUrl = coverUrl,
                lyrics = lyrics,
                quality = quality,
                expireTime = now + ttlSeconds,
                createTime = now
            )
        }
    }
}

/**
 * Unwrap a [QueryResult] into its underlying value.
 */
internal fun <T> QueryResult<T>.unwrap(): T = when (this) {
    is QueryResult.Value -> value
    is QueryResult.AsyncValue -> value
}

/**
 * Cross-platform current time in seconds (Unix timestamp).
 */
internal fun currentTimeSeconds(): Long = Clock.System.now().epochSeconds
