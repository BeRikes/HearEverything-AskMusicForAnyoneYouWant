package com.example.aimusicplayer

import androidx.compose.ui.window.ComposeUIViewController
import com.example.aimusicplayer.behavior.LocalSongIndex
import com.example.aimusicplayer.behavior.UserBehaviorTracker
import com.example.aimusicplayer.cache.createCacheRepository
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.music.createDownloadManager
import com.example.aimusicplayer.export.createExportImportHandler
import com.example.aimusicplayer.export.createFilePicker
import com.example.aimusicplayer.export.createShareHandler
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.player.SystemMediaController
import com.example.aimusicplayer.settings.SettingsStorage

fun MainViewController() = ComposeUIViewController {
    val storage = SettingsStorage()
    App(
        storage = storage,
        createMusicPlayer = { MusicPlayer() },
        createDownloadManager = { createDownloadManager() },
        createCacheRepository = { createCacheRepository() },
        createPlaylistManager = { PlaylistManager() },
        createSystemMediaController = { SystemMediaController() },
        createBehaviorTracker = { UserBehaviorTracker() },
        createSongIndex = { LocalSongIndex() },
        createExportImportHandler = { createExportImportHandler() },
        createShareHandler = { createShareHandler() },
        createFilePicker = { createFilePicker() },
        onPickImportFile = { callback -> Unit /* iOS 使用 FilePicker 方式 */ },
    )
}
