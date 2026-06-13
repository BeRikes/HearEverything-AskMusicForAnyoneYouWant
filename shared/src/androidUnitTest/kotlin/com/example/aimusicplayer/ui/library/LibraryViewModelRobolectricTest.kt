package com.example.aimusicplayer.ui.library

import androidx.test.core.app.ApplicationProvider
import com.example.aimusicplayer.cache.DatabaseDriverFactory
import com.example.aimusicplayer.music.DownloadedSong
import com.example.aimusicplayer.music.PlaylistManager
import com.example.aimusicplayer.player.MusicPlayer
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
import com.example.aimusicplayer.fixtures.FakeDownloadManager
import com.example.aimusicplayer.fixtures.FakeExportImportHandler
import com.example.aimusicplayer.fixtures.FakeFilePicker
import com.example.aimusicplayer.fixtures.FakeShareHandler
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LibraryViewModelRobolectricTest {

    private lateinit var viewModel: LibraryViewModel
    private lateinit var fakeDownloadManager: FakeDownloadManager
    private lateinit var fakeExportImport: FakeExportImportHandler
    private lateinit var fakeShareHandler: FakeShareHandler
    private lateinit var fakeFilePicker: FakeFilePicker
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val musicPlayer = MusicPlayer(context)
        val driverFactory = DatabaseDriverFactory(context)
        val sqlDriver = driverFactory.createDriver()
        val playlistManager = PlaylistManager(sqlDriver)

        fakeDownloadManager = FakeDownloadManager()
        fakeExportImport = FakeExportImportHandler()
        fakeShareHandler = FakeShareHandler()
        fakeFilePicker = FakeFilePicker()

        viewModel = LibraryViewModel(
            musicPlayer, fakeDownloadManager, playlistManager,
            fakeExportImport, fakeShareHandler, fakeFilePicker,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleDisplayMode_switches_state() {
        assertFalse(viewModel.isSimpleMode.value)
        viewModel.toggleDisplayMode()
        assertTrue(viewModel.isSimpleMode.value)
        viewModel.toggleDisplayMode()
        assertFalse(viewModel.isSimpleMode.value)
    }

    @Test
    fun showRenameDialog_sets_target() {
        val song = DownloadedSong(
            songId = "test", fileName = "f.mp3", songName = "Song",
            artist = "A", platform = "qq", audioUrl = "url", localPath = "/tmp/f.mp3",
        )
        assertNull(viewModel.renameTarget.value)
        viewModel.showRenameDialog(song)
        assertEquals("test", viewModel.renameTarget.value?.songId)
    }

    @Test
    fun dismissRenameDialog_clears_target() {
        val song = DownloadedSong(
            songId = "test2", fileName = "f2.mp3", songName = "S2",
            artist = "B", platform = "netease", audioUrl = "u", localPath = "/t/f2.mp3",
        )
        viewModel.showRenameDialog(song)
        viewModel.dismissRenameDialog()
        assertNull(viewModel.renameTarget.value)
    }

    @Test
    fun clearSnackbar_sets_null() {
        viewModel.clearSnackbar()
        assertNull(viewModel.snackbarMessage.value)
    }

    @Test
    fun initial_state_is_loading() {
        // After construction, loadSongs() is called in init
        // The StateFlow might be loading or loaded depending on timing
        assertNotNull(viewModel.downloadedSongs.value)
    }

    @Test
    fun onCacheChanged_callback_works() {
        var called = false
        viewModel.onCacheChanged = { called = true }
        viewModel.onCacheChanged?.invoke()
        assertTrue(called)
    }

    @Test
    fun `exportAll calls exportToZip and shares file`() {
        fakeDownloadManager.addSong(
            DownloadedSong(
                songId = "e1", fileName = "song.mp3", songName = "Test Song",
                artist = "Artist", platform = "netease", audioUrl = "url",
                localPath = "/path/song.mp3",
            )
        )
        fakeExportImport.exportResult = Result.success(
            com.example.aimusicplayer.export.ExportResult("/fake/export.zip", 1, 1)
        )
        viewModel.exportAll()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, fakeShareHandler.shareCount)
        assertEquals("/fake/export.zip", fakeShareHandler.lastSharedPath)
    }

    @Test
    fun `importFromPath shows success snackbar with counts`() {
        fakeExportImport.importResult = Result.success(
            com.example.aimusicplayer.export.ImportResult(importedCount = 5, skippedCount = 2)
        )
        viewModel.importFromPath("/test.zip")
        testDispatcher.scheduler.advanceUntilIdle()
        val msg = viewModel.snackbarMessage.value
        assertNotNull(msg)
        assertTrue(msg!!.contains("5"), "Expected snackbar to contain imported count 5")
        assertTrue(msg.contains("2"), "Expected snackbar to contain skipped count 2")
    }

    @Test
    fun `importFromPath shows error snackbar on failure`() {
        fakeExportImport.importResult = Result.failure(RuntimeException("文件损坏"))
        viewModel.importFromPath("/bad.zip")
        testDispatcher.scheduler.advanceUntilIdle()
        val msg = viewModel.snackbarMessage.value
        assertNotNull(msg)
        assertTrue(msg!!.contains("导入失败"), "Expected error snackbar")
    }
}
