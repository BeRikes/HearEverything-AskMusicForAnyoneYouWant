package com.example.aimusicplayer.cache

/**
 * Repository for caching song metadata using SQLite via SqlDriver.
 *
 * Provides methods to store, retrieve, and expire cached song data
 * across platforms (Android + iOS).
 */
interface CacheRepository {

    /**
     * Retrieve a cached song by its ID.
     * Returns null if not found or the cache entry has expired.
     */
    suspend fun getCachedSong(id: String): SongCacheEntity?

    /**
     * Save (insert or update) a song cache entry.
     * For new entries, both [song.expireTime] and [song.createTime] should be set.
     */
    suspend fun saveSong(song: SongCacheEntity)

    /**
     * Remove all expired song cache entries and failed lookup records.
     * Should be called periodically (e.g., on app launch or before search).
     */
    suspend fun deleteExpired()

    /**
     * Mark a song lookup as "failed" for [durationHours] (default 24).
     * This prevents repeated failed network requests for songs known not to exist.
     * The [id] should be generated via [Md5Utils.generateSongId] before calling.
     */
    suspend fun markAsFailed(id: String, durationHours: Int = 24)

    /**
     * Check whether a lookup for the given [id] was recently marked as failed
     * and has not yet expired. Returns true if the lookup should be skipped.
     */
    suspend fun isFailedLookup(id: String): Boolean

    companion object {
        /** Default cache TTL: 7 days in seconds */
        const val DEFAULT_TTL_SECONDS: Long = 7 * 24 * 60 * 60

        /** Default failed lookup TTL: 24 hours in seconds */
        const val DEFAULT_FAILED_TTL_SECONDS: Long = 24 * 60 * 60
    }
}
