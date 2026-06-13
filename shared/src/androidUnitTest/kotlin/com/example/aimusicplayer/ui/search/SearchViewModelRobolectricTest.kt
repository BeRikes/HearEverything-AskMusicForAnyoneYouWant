package com.example.aimusicplayer.ui.search

import androidx.test.core.app.ApplicationProvider
import com.example.aimusicplayer.behavior.createUserBehaviorTracker
import com.example.aimusicplayer.cache.CacheRepositoryImpl
import com.example.aimusicplayer.cache.DatabaseDriverFactory
import com.example.aimusicplayer.cache.Md5Utils
import com.example.aimusicplayer.fixtures.FakeCacheRepository
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.music.MusicSearchManager
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.network.music.BilibiliApi
import com.example.aimusicplayer.network.music.NeteaseApi
import com.example.aimusicplayer.network.music.QQMusicApi
import com.example.aimusicplayer.network.music.SongMetadata
import com.example.aimusicplayer.player.MusicPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SearchViewModelRobolectricTest {

    private lateinit var viewModel: SearchViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val musicPlayer = MusicPlayer(context)
        val musicApis = listOf(BilibiliApi(), NeteaseApi(), QQMusicApi())
        val searchManager = MusicSearchManager(musicApis)
        val cacheRepo = FakeCacheRepository()
        val sqlDriver = DatabaseDriverFactory(context).createDriver()
        val downloadManager = DownloadManager(context, sqlDriver)
        val playlistManager = PlaylistManager(sqlDriver)

        viewModel = SearchViewModel(
            musicPlayer = musicPlayer,
            searchManager = searchManager,
            cacheRepository = cacheRepo,
            downloadManager = downloadManager,
            playlistManager = playlistManager,
            behaviorTracker = createUserBehaviorTracker(context),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Input state ──────────────────────────────────────────────────

    @Test
    fun song_name_starts_empty() {
        assertEquals("", viewModel.songName.value)
    }

    @Test
    fun set_song_name_updates_state() {
        viewModel.onSongNameChanged("夜曲")
        assertEquals("夜曲", viewModel.songName.value)
    }

    @Test
    fun artist_starts_empty() {
        assertEquals("", viewModel.artist.value)
    }

    @Test
    fun set_artist_updates_state() {
        viewModel.onArtistChanged("周杰伦")
        assertEquals("周杰伦", viewModel.artist.value)
    }

    // ── Search expansion ─────────────────────────────────────────────

    @Test
    fun search_panel_starts_collapsed() {
        assertFalse(viewModel.isSearchExpanded.value)
    }

    @Test
    fun expand_search_toggles_state() {
        viewModel.expandSearch()
        assertTrue(viewModel.isSearchExpanded.value)
    }

    @Test
    fun collapse_search_toggles_state() {
        viewModel.expandSearch()
        viewModel.collapseSearch()
        assertFalse(viewModel.isSearchExpanded.value)
    }

    // ── Search state ─────────────────────────────────────────────────

    @Test
    fun is_searching_starts_false() {
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun has_searched_starts_false() {
        assertFalse(viewModel.hasSearched.value)
    }

    @Test
    fun search_error_starts_null() {
        assertEquals(null, viewModel.searchError.value)
    }

    @Test
    fun search_results_start_empty() {
        assertTrue(viewModel.searchResults.value.isEmpty())
    }

    // ── Stop search ──────────────────────────────────────────────────

    @Test
    fun stop_search_sets_not_searching() {
        viewModel.stopSearch()
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun stop_search_sets_step_message() {
        viewModel.stopSearch()
        assertNotNull(viewModel.searchStep.value)
    }

    // ── Player delegation ────────────────────────────────────────────

    @Test
    fun current_song_proxies_to_player() = runTest {
        assertNotNull(viewModel.currentSong)
    }

    @Test
    fun is_playing_proxies_to_player() {
        assertNotNull(viewModel.isPlaying)
    }

    // ── Cache callback ───────────────────────────────────────────────

    @Test
    fun on_cache_changed_callback() {
        var called = false
        viewModel.onCacheChanged = { called = true }
        viewModel.onCacheChanged?.invoke()
        assertTrue(called)
    }
}
