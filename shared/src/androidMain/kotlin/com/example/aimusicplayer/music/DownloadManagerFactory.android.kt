package com.example.aimusicplayer.music

import android.content.Context
import com.example.aimusicplayer.cache.DatabaseDriverFactory

/**
 * Convenience factory that creates a fully-wired [DownloadManager]
 * from an Android [Context].
 *
 * This hides the [SqlDriver] dependency from the androidApp module
 * so callers only need to pass a Context.
 */
fun createDownloadManager(context: Context): DownloadManager {
    val driver = DatabaseDriverFactory(context).createDriver()
    return DownloadManager(context, driver)
}
