package com.example.aimusicplayer.cache

/**
 * Convenience factory that creates a fully-wired [CacheRepository] for iOS.
 *
 * Uses the same SQLite database as [DownloadManager] (via the shared
 * [DatabaseDriverFactory] expect/actual).
 */
fun createCacheRepository(): CacheRepository {
    return CacheRepositoryImpl(DatabaseDriverFactory())
}
