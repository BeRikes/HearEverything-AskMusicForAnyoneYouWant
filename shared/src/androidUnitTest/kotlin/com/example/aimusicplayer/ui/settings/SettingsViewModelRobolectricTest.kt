package com.example.aimusicplayer.ui.settings

import androidx.test.core.app.ApplicationProvider
import com.example.aimusicplayer.ai.AiAssistant
import com.example.aimusicplayer.cache.DatabaseDriverFactory
import com.example.aimusicplayer.music.DownloadManager
import com.example.aimusicplayer.settings.SettingsKeys
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
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SettingsViewModelRobolectricTest {

    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = SettingsStorage(context)
        val settingsRepo = SettingsRepository(storage)
        val aiAssistant = AiAssistant(storage)
        val sqlDriver = DatabaseDriverFactory(context).createDriver()
        val downloadManager = DownloadManager(context, sqlDriver)

        viewModel = SettingsViewModel(settingsRepo, aiAssistant, downloadManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_theme_mode_is_system() {
        assertEquals(SettingsKeys.THEME_MODE_SYSTEM, viewModel.themeMode.value)
    }

    @Test
    fun cache_size_text_is_initialized() {
        assertNotNull(viewModel.cacheSizeText.value)
    }

    @Test
    fun history_count_has_default_value() {
        assertEquals(SettingsKeys.DEFAULT_HISTORY_COUNT, viewModel.historyCount.value)
    }

    @Test
    fun test_result_starts_null() {
        assertEquals(null, viewModel.testResult.value)
    }

    @Test
    fun is_testing_starts_false() {
        assertEquals(false, viewModel.isTesting.value)
    }

    @Test
    fun api_key_starts_empty() {
        assertEquals("", viewModel.apiKey.value)
    }

    @Test
    fun base_url_starts_empty() {
        assertEquals("", viewModel.baseUrl.value)
    }
}
