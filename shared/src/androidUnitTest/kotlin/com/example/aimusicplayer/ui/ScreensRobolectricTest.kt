package com.example.aimusicplayer.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.cache.DatabaseDriverFactory
import com.example.aimusicplayer.import.PlaylistImportManager
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.network.music.BilibiliApi
import com.example.aimusicplayer.network.music.NeteaseApi
import com.example.aimusicplayer.network.music.QQMusicApi
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.settings.SettingsStorage
import com.example.aimusicplayer.fixtures.FakeCacheRepository
import com.example.aimusicplayer.fixtures.FakeExportImportHandler
import com.example.aimusicplayer.fixtures.FakeFilePicker
import com.example.aimusicplayer.fixtures.FakeShareHandler
import com.example.aimusicplayer.ui.library.LibraryScreen
import com.example.aimusicplayer.ui.library.LibraryViewModel
import com.example.aimusicplayer.ui.playlist.PlaylistScreen
import com.example.aimusicplayer.ui.playlist.PlaylistViewModel
import com.example.aimusicplayer.ui.search.SearchScreen
import com.example.aimusicplayer.ui.search.SearchViewModel
import com.example.aimusicplayer.ui.settings.SettingsScreen
import com.example.aimusicplayer.ui.settings.SettingsViewModel
import com.example.aimusicplayer.ui.theme.HearEverythingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ScreensRobolectricTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun library_screen_renders() {
        val c = ctx(); val d = DatabaseDriverFactory(c).createDriver()
        val vm = LibraryViewModel(
            MusicPlayer(c), DownloadManager(c, d), PlaylistManager(d),
            FakeExportImportHandler(), FakeShareHandler(), FakeFilePicker(),
        )
        compose.setContent { HearEverythingTheme { LibraryScreen(vm, onBack = {}) } }
        compose.waitForIdle()
    }

    @Test
    fun settings_screen_renders() {
        val c = ctx(); val d = DatabaseDriverFactory(c).createDriver()
        val vm = SettingsViewModel(SettingsRepository(SettingsStorage(c)), AiAssistant(SettingsStorage(c)), DownloadManager(c, d))
        compose.setContent { HearEverythingTheme { SettingsScreen(vm, onBack = {}) } }
        compose.waitForIdle()
    }

    // ── MiniPlayerBar more states ───────────────────────────────

    @Test
    fun mini_player_paused_state() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "p1", songName = "暂停中", artist = "Test", platform = "netease", duration = 200
        )
        compose.setContent {
            HearEverythingTheme {
                com.example.aimusicplayer.ui.player.MiniPlayerBar(
                    currentSong = song, isPlaying = false,
                    onPlayPause = {}, onPrevious = {}, onNext = {}, onTap = {}, onPlaylist = {},
                    playbackError = null, previousSongName = null, nextSongName = null,
                    playlistIndex = 0, playlistSize = 1
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun mini_player_playing_state() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "p2", songName = "播放中", artist = "歌手", platform = "qq", duration = 180
        )
        compose.setContent {
            HearEverythingTheme {
                com.example.aimusicplayer.ui.player.MiniPlayerBar(
                    currentSong = song, isPlaying = true,
                    onPlayPause = {}, onPrevious = {}, onNext = {}, onTap = {}, onPlaylist = {},
                    playbackError = null, previousSongName = "上一首", nextSongName = "下一首",
                    playlistIndex = 1, playlistSize = 5
                )
            }
        }
        compose.waitForIdle()
    }

    // ── NowPlayingOverlay downloading state ──────────────────────

    @Test
    fun now_playing_preparing_download() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "dl", songName = "下载中", artist = "X", platform = "kugou", duration = 180
        )
        compose.setContent {
            HearEverythingTheme {
                com.example.aimusicplayer.ui.player.NowPlayingOverlay(
                    visible = true, currentSong = song, isPlaying = true,
                    currentPosition = 5000L, duration = 180000L,
                    onDismiss = {}, onPlayPause = {}, onNext = {}, onPrevious = {},
                    onDownload = {}, onLyrics = {}, onPlaylist = {}, onSeek = {},
                    showLyrics = false, lyricsText = null, downloadProgress = 0.7f,
                    isPreparingDownload = true, isDownloaded = false, playbackError = null,
                    onClearError = {}, lyricsOffsetMs = 0, onLyricsOffsetChanged = {},
                    onLyricsOffsetReset = {}, onLyricsReSearch = {}, isReSearching = false,
                    onLyricsManualSearch = { _, _, _ -> }
                )
            }
        }
        compose.waitForIdle()
    }

    // ── NowPlayingOverlay lyrics re-searching state ───────────────

    @Test
    fun now_playing_re_searching_lyrics() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "rl", songName = "搜歌词", artist = "B", platform = "bilibili", duration = 240
        )
        compose.setContent {
            HearEverythingTheme {
                com.example.aimusicplayer.ui.player.NowPlayingOverlay(
                    visible = true, currentSong = song, isPlaying = true,
                    currentPosition = 30000L, duration = 240000L,
                    onDismiss = {}, onPlayPause = {}, onNext = {}, onPrevious = {},
                    onDownload = {}, onLyrics = {}, onPlaylist = {}, onSeek = {},
                    showLyrics = true, lyricsText = "[00:00]lyrics...", downloadProgress = null,
                    isPreparingDownload = false, isDownloaded = true, playbackError = null,
                    onClearError = {}, lyricsOffsetMs = 500, onLyricsOffsetChanged = {},
                    onLyricsOffsetReset = {}, onLyricsReSearch = {}, isReSearching = true,
                    onLyricsManualSearch = { _, _, _ -> }
                )
            }
        }
        compose.waitForIdle()
    }
}
