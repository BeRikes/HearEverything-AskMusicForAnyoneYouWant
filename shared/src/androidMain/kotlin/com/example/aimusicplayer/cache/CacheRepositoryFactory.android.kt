package com.example.aimusicplayer.cache

import android.content.Context

/**
 * Convenience factory that creates a fully-wired [CacheRepository]
 * from an Android [Context].
 *
 * This hides the [SqlDriver] dependency from the androidApp module
 * so callers only need to pass a Context.
 *
 * Uses the same database file as [DownloadManager] (via the shared
 * [DatabaseDriverFactory] expect/actual).
 */
fun createCacheRepository(context: Context): CacheRepository {
    return CacheRepositoryImpl(DatabaseDriverFactory(context))
}
