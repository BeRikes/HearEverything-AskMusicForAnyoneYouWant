package com.example.aimusicplayer.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.aimusicplayer.ui.common.pressFlash
import com.example.aimusicplayer.ui.player.MarqueeText
import com.example.aimusicplayer.ui.theme.HearEverythingTheme
import com.example.aimusicplayer.fixtures.FakeExportImportHandler
import com.example.aimusicplayer.fixtures.FakeFilePicker
import com.example.aimusicplayer.fixtures.FakeShareHandler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests powered by Robolectric — runs on JVM, no device needed.
 * Robolectric shadows Android framework APIs including InputManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class RobolectricComposeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // ── Theme ────────────────────────────────────────────────────────

    @Test
    fun theme_light_mode_renders() {
        compose.setContent {
            HearEverythingTheme(darkTheme = false) {
                Text("Light Theme")
            }
        }
        compose.onNode(hasText("Light Theme")).assertIsDisplayed()
    }

    @Test
    fun theme_dark_mode_renders() {
        compose.setContent {
            HearEverythingTheme(darkTheme = true) {
                Text("Dark Theme")
            }
        }
        compose.onNode(hasText("Dark Theme")).assertIsDisplayed()
    }

    @Test
    fun theme_system_default_renders() {
        compose.setContent {
            HearEverythingTheme {
                Text("System Theme")
            }
        }
        compose.onNode(hasText("System Theme")).assertIsDisplayed()
    }

    // ── MarqueeText ──────────────────────────────────────────────────

    @Test
    fun marquee_short_text_renders() {
        compose.setContent {
            MarqueeText("Hi", isAnimating = false)
        }
        compose.onNode(hasText("Hi")).assertIsDisplayed()
    }

    @Test
    fun marquee_chinese_text_renders() {
        compose.setContent {
            MarqueeText("发如雪—周杰伦", isAnimating = false)
        }
        compose.onNode(hasText("发如雪—周杰伦")).assertIsDisplayed()
    }

    @Test
    fun marquee_long_text_paused_renders() {
        val longText = "A Very Long Song Title That Would Normally Scroll Horizontally"
        compose.setContent {
            MarqueeText(longText, isAnimating = false)
        }
        compose.onNode(hasText(longText)).assertIsDisplayed()
    }

    // ── PressAnimation ───────────────────────────────────────────────

    @Test
    fun press_flash_renders_content() {
        compose.setContent {
            Box(Modifier.size(100.dp).pressFlash(onTap = { })) {
                Text("Tap")
            }
        }
        compose.onNode(hasText("Tap")).assertIsDisplayed()
    }

    @Test
    fun press_flash_with_long_press_renders() {
        compose.setContent {
            Box(Modifier.size(100.dp).pressFlash(onTap = { }, onLongPress = { })) {
                Text("Hold")
            }
        }
        compose.onNode(hasText("Hold")).assertIsDisplayed()
    }

    // ── MiniPlayerBar ────────────────────────────────────────────────

    @Test
    fun mini_player_bar_renders_idle_state() {
        compose.setContent {
            com.example.aimusicplayer.ui.player.MiniPlayerBar(
                currentSong = null,
                isPlaying = false,
                onPlayPause = {},
                onPrevious = {},
                onNext = {},
                onTap = {},
                onPlaylist = {},
                playbackError = null,
                previousSongName = null,
                nextSongName = null,
                playlistIndex = 0,
                playlistSize = 0,
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun mini_player_bar_renders_with_song() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "test-1",
            songName = "夜曲",
            artist = "周杰伦",
            platform = "netease",
            duration = 222,
        )
        compose.setContent {
            com.example.aimusicplayer.ui.player.MiniPlayerBar(
                currentSong = song,
                isPlaying = true,
                onPlayPause = {},
                onPrevious = {},
                onNext = {},
                onTap = {},
                onPlaylist = {},
                playbackError = null,
                previousSongName = "稻香",
                nextSongName = "晴天",
                playlistIndex = 0,
                playlistSize = 3,
            )
        }
        compose.onNode(hasText("夜曲")).assertIsDisplayed()
    }

    @Test
    fun mini_player_bar_shows_error() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "err-1", songName = "Error Song", artist = "Test",
            platform = "qq", duration = 180,
        )
        compose.setContent {
            com.example.aimusicplayer.ui.player.MiniPlayerBar(
                currentSong = song,
                isPlaying = false,
                onPlayPause = {},
                onPrevious = {},
                onNext = {},
                onTap = {},
                onPlaylist = {},
                playbackError = "播放失败：网络错误",
                previousSongName = null,
                nextSongName = null,
                playlistIndex = 0,
                playlistSize = 1,
            )
        }
        compose.waitForIdle()
    }

    // ── NowPlayingOverlay ──────────────────────────────────────────

    @Test
    fun now_playing_overlay_hidden_when_not_visible() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "np-1", songName = "晴天", artist = "周杰伦",
            platform = "netease", duration = 269,
        )
        compose.setContent {
            com.example.aimusicplayer.ui.player.NowPlayingOverlay(
                visible = false,
                currentSong = song,
                isPlaying = false,
                currentPosition = 0L,
                duration = 269000L,
                onDismiss = {},
                onPlayPause = {},
                onNext = {},
                onPrevious = {},
                onDownload = {},
                onLyrics = {},
                onPlaylist = {},
                onSeek = {},
                showLyrics = false,
                lyricsText = null,
                downloadProgress = null,
                isPreparingDownload = false,
                isDownloaded = false,
                playbackError = null,
                onClearError = {},
                lyricsOffsetMs = 0,
                onLyricsOffsetChanged = {},
                onLyricsOffsetReset = {},
                onLyricsReSearch = {},
                isReSearching = false,
                onLyricsManualSearch = { _, _, _ -> },
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun now_playing_overlay_visible_with_lyrics() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "np-2", songName = "夜曲", artist = "周杰伦",
            platform = "netease", duration = 222,
        )
        compose.setContent {
            com.example.aimusicplayer.ui.player.NowPlayingOverlay(
                visible = true,
                currentSong = song,
                isPlaying = true,
                currentPosition = 60000L,
                duration = 222000L,
                onDismiss = {},
                onPlayPause = {},
                onNext = {},
                onPrevious = {},
                onDownload = {},
                onLyrics = {},
                onPlaylist = {},
                onSeek = {},
                showLyrics = true,
                lyricsText = "[00:00.00]一群嗜血的蚂蚁 被腐肉所吸引",
                downloadProgress = null,
                isPreparingDownload = false,
                isDownloaded = true,
                playbackError = null,
                onClearError = {},
                lyricsOffsetMs = 0,
                onLyricsOffsetChanged = {},
                onLyricsOffsetReset = {},
                onLyricsReSearch = {},
                isReSearching = false,
                onLyricsManualSearch = { _, _, _ -> },
            )
        }
        // Verify the overlay renders without crashing
        compose.waitForIdle()
    }

    @Test
    fun now_playing_downloading_state() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "dl-1", songName = "稻香", artist = "周杰伦",
            platform = "qq", duration = 240,
        )
        compose.setContent {
            com.example.aimusicplayer.ui.player.NowPlayingOverlay(
                visible = true,
                currentSong = song,
                isPlaying = true,
                currentPosition = 30000L,
                duration = 240000L,
                onDismiss = {},
                onPlayPause = {},
                onNext = {},
                onPrevious = {},
                onDownload = {},
                onLyrics = {},
                onPlaylist = {},
                onSeek = {},
                showLyrics = false,
                lyricsText = null,
                downloadProgress = 0.45f,
                isPreparingDownload = false,
                isDownloaded = false,
                playbackError = null,
                onClearError = {},
                lyricsOffsetMs = 0,
                onLyricsOffsetChanged = {},
                onLyricsOffsetReset = {},
                onLyricsReSearch = {},
                isReSearching = false,
                onLyricsManualSearch = { _, _, _ -> },
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun now_playing_with_error() {
        val song = com.example.aimusicplayer.network.music.SongMetadata(
            songId = "err-2", songName = "Error", artist = "X",
            platform = "kugou", duration = 180,
        )
        compose.setContent {
            com.example.aimusicplayer.ui.player.NowPlayingOverlay(
                visible = true,
                currentSong = song,
                isPlaying = false,
                currentPosition = 0L,
                duration = 180000L,
                onDismiss = {},
                onPlayPause = {},
                onNext = {},
                onPrevious = {},
                onDownload = {},
                onLyrics = {},
                onPlaylist = {},
                onSeek = {},
                showLyrics = false,
                lyricsText = null,
                downloadProgress = null,
                isPreparingDownload = false,
                isDownloaded = false,
                playbackError = "音频解码失败",
                onClearError = {},
                lyricsOffsetMs = 0,
                onLyricsOffsetChanged = {},
                onLyricsOffsetReset = {},
                onLyricsReSearch = {},
                isReSearching = false,
                onLyricsManualSearch = { _, _, _ -> },
            )
        }
        compose.waitForIdle()
    }

    // ── Bilibili title display ─────────────────────────────────────

    @Test
    fun marquee_special_characters() {
        compose.setContent {
            MarqueeText("🎵 Music ♫ ♪", isAnimating = false)
        }
        compose.waitForIdle()
    }

    @Test
    fun marquee_very_long_chinese_title() {
        val longChinese = "这是一个非常长的歌名用于测试跑马灯效果在中文环境下是否正常显示"
        compose.setContent {
            MarqueeText(longChinese, isAnimating = false)
        }
        compose.onNode(hasText(longChinese)).assertIsDisplayed()
    }

    // ── Import screen with real Robolectric deps (Library → simpler ─

    @Test
    fun library_screen_with_real_viewmodel_renders() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val driver = com.example.aimusicplayer.cache.DatabaseDriverFactory(ctx).createDriver()
        val player = com.example.aimusicplayer.player.MusicPlayer(ctx)
        val dl = com.example.aimusicplayer.music.DownloadManager(ctx, driver)
        val pl = com.example.aimusicplayer.music.PlaylistManager(driver)
        val libraryVM = com.example.aimusicplayer.ui.library.LibraryViewModel(
            player, dl, pl,
            FakeExportImportHandler(), FakeShareHandler(), FakeFilePicker(),
        )

        compose.setContent {
            HearEverythingTheme {
                com.example.aimusicplayer.ui.library.LibraryScreen(
                    viewModel = libraryVM,
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }
}
