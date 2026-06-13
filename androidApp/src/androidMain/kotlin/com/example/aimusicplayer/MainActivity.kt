package com.example.aimusicplayer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.aimusicplayer.behavior.createLocalSongIndex
import com.example.aimusicplayer.behavior.createUserBehaviorTracker
import com.example.aimusicplayer.cache.createCacheRepository
import com.example.aimusicplayer.export.createExportImportHandler
import com.example.aimusicplayer.export.createFilePicker
import com.example.aimusicplayer.export.createShareHandler
import com.example.aimusicplayer.music.createDownloadManager
import com.example.aimusicplayer.music.createPlaylistManager
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.player.MusicPlayerHolder
import com.example.aimusicplayer.player.PlaybackService
import com.example.aimusicplayer.player.SystemMediaController
import com.example.aimusicplayer.settings.SettingsStorage
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create shared instances before service so App() has them ready
        val musicPlayer = MusicPlayer(applicationContext)
        val systemMediaController = SystemMediaController(applicationContext).also {
            it.attach(musicPlayer)
        }
        MusicPlayerHolder.musicPlayer = musicPlayer
        MusicPlayerHolder.systemMediaController = systemMediaController

        PlaybackService.start(applicationContext)

        val storage = SettingsStorage(applicationContext)
        setContent {
            val context = applicationContext
            var importCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let {
                    val path = copyUriToCache(context, it)
                    importCallback?.invoke(path)
                }
            }

            App(
                storage = storage,
                createMusicPlayer = { MusicPlayerHolder.musicPlayer },
                createDownloadManager = { createDownloadManager(applicationContext) },
                createCacheRepository = { createCacheRepository(applicationContext) },
                createPlaylistManager = { createPlaylistManager(applicationContext) },
                createSystemMediaController = { MusicPlayerHolder.systemMediaController },
                createBehaviorTracker = { createUserBehaviorTracker(applicationContext) },
                createSongIndex = { createLocalSongIndex(applicationContext) },
                createExportImportHandler = { createExportImportHandler(applicationContext) },
                createShareHandler = { createShareHandler(applicationContext) },
                createFilePicker = { createFilePicker(applicationContext) },
                onPickImportFile = { callback ->
                    importCallback = callback
                    importLauncher.launch(arrayOf("application/zip"))
                },
            )
        }
    }

    /** 将 Content URI 拷贝到缓存目录并返回绝对路径。 */
    private fun copyUriToCache(context: android.content.Context, uri: Uri): String {
        val fileName = "imported_${System.currentTimeMillis()}.zip"
        val destFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile.absolutePath
    }
}
