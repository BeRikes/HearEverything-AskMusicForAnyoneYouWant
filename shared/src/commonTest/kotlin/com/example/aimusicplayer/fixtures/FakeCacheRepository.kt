package com.example.aimusicplayer.fixtures

import com.example.aimusicplayer.cache.CacheRepository
import com.example.aimusicplayer.cache.SongCacheEntity

/**
 * In-memory implementation of [CacheRepository] for unit testing.
 *
 * Stores cached songs and failed lookups in mutable collections.
 * Times are represented as seconds since epoch for deterministic testing.
 */
class FakeCacheRepository : CacheRepository {

    private val cache = mutableMapOf<String, SongCacheEntity>()
    private val failedLookups = mutableMapOf<String, Long>()

    override suspend fun getCachedSong(id: String): SongCacheEntity? {
        val entity = cache[id] ?: return null
        // Check if expired
        if (entity.expireTime < currentTimeSeconds()) {
            cache.remove(id)
            return null
        }
        return entity
    }

    override suspend fun saveSong(song: SongCacheEntity) {
        cache[song.id] = song
    }

    override suspend fun deleteExpired() {
        val now = currentTimeSeconds()
        cache.entries.removeAll { it.value.expireTime < now }
        failedLookups.entries.removeAll { it.value < now }
    }

    override suspend fun markAsFailed(id: String, durationHours: Int) {
        failedLookups[id] = currentTimeSeconds() + durationHours * 3600L
    }

    override suspend fun isFailedLookup(id: String): Boolean {
        val expireTime = failedLookups[id] ?: return false
        return expireTime > currentTimeSeconds()
    }

    /** Set a fixed "current time" override for deterministic testing. */
    var timeOverride: Long? = null

    private fun currentTimeSeconds(): Long {
        return timeOverride ?: (System.currentTimeMillis() / 1000)
    }

    /** Clear all stored data. */
    fun reset() {
        cache.clear()
        failedLookups.clear()
        timeOverride = null
    }
}
