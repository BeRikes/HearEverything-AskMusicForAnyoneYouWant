package com.example.aimusicplayer.settings

import com.example.aimusicplayer.fixtures.FakeSettingsStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private val storage = FakeSettingsStorage()
    private val repo = SettingsRepository(storage)

    @Test
    fun `getBaseUrl returns default when not set`() = runTest {
        assertEquals(SettingsKeys.DEFAULT_BASE_URL, repo.getBaseUrl())
    }

    @Test
    fun `set and get base URL`() = runTest {
        repo.setBaseUrl("https://custom.api.com/v1")
        assertEquals("https://custom.api.com/v1", repo.getBaseUrl())
    }

    @Test
    fun `getApiKey returns empty when not set`() = runTest {
        assertEquals("", repo.getApiKey())
    }

    @Test
    fun `set and get API key`() = runTest {
        repo.setApiKey("sk-test-12345-abcd")
        assertEquals("sk-test-12345-abcd", repo.getApiKey())
    }

    @Test
    fun `setApiKey with blank removes key`() = runTest {
        repo.setApiKey("sk-old-key")
        repo.setApiKey("")
        assertEquals("", repo.getApiKey())
    }

    @Test
    fun `clearApiKey removes key`() = runTest {
        repo.setApiKey("sk-to-delete")
        repo.clearApiKey()
        assertEquals("", repo.getApiKey())
    }

    @Test
    fun `lyrics sync defaults to false`() = runTest {
        assertFalse(repo.getLyricsSync())
    }

    @Test
    fun `set lyrics sync true`() = runTest {
        repo.setLyricsSync(true)
        assertTrue(repo.getLyricsSync())
    }

    @Test
    fun `default quality value`() = runTest {
        assertEquals("standard", repo.getDefaultQuality())
    }

    @Test
    fun `set and get default quality`() = runTest {
        repo.setDefaultQuality("lossless")
        assertEquals("lossless", repo.getDefaultQuality())
    }

    @Test
    fun `history count defaults to 20`() = runTest {
        assertEquals(20, repo.getHistoryCount())
    }

    @Test
    fun `set and get history count`() = runTest {
        repo.setHistoryCount(50)
        assertEquals(50, repo.getHistoryCount())
    }

    @Test
    fun `history count falls back for non-numeric value`() = runTest {
        storage.putString(SettingsKeys.HISTORY_COUNT, "not-a-number")
        assertEquals(20, repo.getHistoryCount())
    }

    @Test
    fun `theme mode defaults to system`() = runTest {
        assertEquals("system", repo.getThemeMode())
    }

    @Test
    fun `set and get theme mode`() = runTest {
        repo.setThemeMode("dark")
        assertEquals("dark", repo.getThemeMode())
    }

    @Test
    fun `clearAll removes all settings`() = runTest {
        repo.setApiKey("sk-xxx")
        repo.setBaseUrl("https://custom.url")
        repo.setThemeMode("dark")

        repo.clearAll()

        assertEquals("", repo.getApiKey())
        assertEquals(SettingsKeys.DEFAULT_BASE_URL, repo.getBaseUrl())
        assertEquals("system", repo.getThemeMode())
    }

    @Test
    fun `raw get and put`() = runTest {
        assertNull(repo.getRaw("custom_key"))
        repo.putRaw("custom_key", "custom_value")
        assertEquals("custom_value", repo.getRaw("custom_key"))
    }

    @Test
    fun `raw put null removes value`() = runTest {
        repo.putRaw("temp", "value")
        repo.putRaw("temp", null)
        assertNull(repo.getRaw("temp"))
    }
}
