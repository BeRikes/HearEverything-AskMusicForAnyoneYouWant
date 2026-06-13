package com.example.aimusicplayer.music

import com.example.aimusicplayer.cache.DatabaseDriverFactory

/**
 * Convenience factory that creates a fully-wired [DownloadManager] for iOS.
 */
fun createDownloadManager(): DownloadManager {
    val driver = DatabaseDriverFactory().createDriver()
    return DownloadManager(driver)
}
