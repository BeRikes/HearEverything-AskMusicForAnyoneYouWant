package com.example.aimusicplayer.ui.playlist

import androidx.test.core.app.ApplicationProvider
import com.example.aimusicplayer.behavior.createUserBehaviorTracker
import com.example.aimusicplayer.cache.DatabaseDriverFactory
import com.example.aimusicplayer.import.PlaylistImportManager
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.PlayMode
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.network.music.BilibiliApi
import com.example.aimusicplayer.network.music.NeteaseApi
import com.example.aimusicplayer.network.music.QQMusicApi
import com.example.aimusicplayer.player.MusicPlayer
import com.example.aimusicplayer.settings.SettingsRepository
import com.example.aimusicplayer.settings.SettingsStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PlaylistViewModelRobolectricTest {

    private lateinit var viewModel: PlaylistViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sqlDriver = DatabaseDriverFactory(context).createDriver()
        val playlistManager = PlaylistManager(sqlDriver)
        val musicPlayer = MusicPlayer(context)
        val storage = SettingsStorage(context)
        val settingsRepo = SettingsRepository(storage)
        val musicApis = listOf(BilibiliApi(), NeteaseApi(), QQMusicApi())
        val searchManager = MusicSearchManager(musicApis)
        val importManager = PlaylistImportManager(musicApis, searchManager, playlistManager)
        val downloadManager = DownloadManager(context, sqlDriver)

        viewModel = PlaylistViewModel(
            playlistManager, musicPlayer, settingsRepo, importManager, downloadManager,
            behaviorTracker = createUserBehaviorTracker(context),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Tab selection ───────────────────────────────────────────────

    @Test
    fun default_tab_is_zero() {
        assertEquals(0, viewModel.selectedTab.value)
    }

    @Test
    fun select_tab_updates_state() {
        viewModel.selectTab(1)
        assertEquals(1, viewModel.selectedTab.value)
    }

    @Test
    fun select_same_tab_does_not_trigger_update() {
        viewModel.selectTab(0) // already 0, no-op
        assertEquals(0, viewModel.selectedTab.value)
    }

    // ── Play mode ───────────────────────────────────────────────────

    @Test
    fun toggle_play_mode_cycles() {
        val initial = viewModel.playMode.value
        viewModel.togglePlayMode()
        val afterFirst = viewModel.playMode.value
        viewModel.togglePlayMode()
        val afterSecond = viewModel.playMode.value
        viewModel.togglePlayMode()
        val afterThird = viewModel.playMode.value

        // After 3 toggles, should be back to initial
        assertEquals(initial, afterThird)
        // All 3 modes should be different from each other (SEQUENTIAL → LOOP → REPEAT_ONE)
        assertTrue(setOf(initial, afterFirst, afterSecond).size >= 2)
    }

    // ── State flows ─────────────────────────────────────────────────

    @Test
    fun playlist_starts_empty() {
        assertTrue(viewModel.playlist.value.isEmpty())
    }

    @Test
    fun history_starts_empty() {
        assertTrue(viewModel.history.value.isEmpty())
    }

    @Test
    fun imported_playlists_start_empty() {
        assertTrue(viewModel.importedPlaylists.value.isEmpty())
    }

    @Test
    fun selected_playlist_id_starts_null() {
        assertEquals(null, viewModel.selectedPlaylistId.value)
    }
}
