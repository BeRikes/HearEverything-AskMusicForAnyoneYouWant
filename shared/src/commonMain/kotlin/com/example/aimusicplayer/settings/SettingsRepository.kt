package com.example.aimusicplayer.settings

/**
 * Typed settings accessor wrapping [SettingsStorageProvider].
 *
 * Provides suspend get/put methods for each known key so callers don't
 * need to pass raw key strings or handle "true"/"false" conversion.
 *
 * @param storage Platform-specific [SettingsStorageProvider]
 */
class SettingsRepository(private val storage: SettingsStorageProvider) {

    // ── AI provider ─────────────────────────────────────────────────

    suspend fun getBaseUrl(): String =
        storage.getString(SettingsKeys.API_BASE_URL) ?: SettingsKeys.DEFAULT_BASE_URL

    suspend fun setBaseUrl(url: String) =
        storage.putString(SettingsKeys.API_BASE_URL, url)

    suspend fun getApiKey(): String =
        storage.getString(SettingsKeys.API_KEY) ?: ""

    suspend fun setApiKey(key: String) =
        storage.putString(SettingsKeys.API_KEY, key.ifBlank { null })

    suspend fun clearApiKey() =
        storage.remove(SettingsKeys.API_KEY)

    suspend fun getLyricsSync(): Boolean =
        storage.getString(SettingsKeys.LYRICS_SYNC)?.toBooleanStrictOrNull() ?: false

    suspend fun setLyricsSync(enabled: Boolean) =
        storage.putString(SettingsKeys.LYRICS_SYNC, enabled.toString())

    suspend fun getDefaultQuality(): String =
        storage.getString(SettingsKeys.DEFAULT_QUALITY) ?: SettingsKeys.DEFAULT_QUALITY_VALUE

    suspend fun setDefaultQuality(quality: String) =
        storage.putString(SettingsKeys.DEFAULT_QUALITY, quality)

    suspend fun getHistoryCount(): Int {
        val raw = storage.getString(SettingsKeys.HISTORY_COUNT)
        return raw?.toIntOrNull() ?: SettingsKeys.DEFAULT_HISTORY_COUNT
    }

    suspend fun setHistoryCount(count: Int) =
        storage.putString(SettingsKeys.HISTORY_COUNT, count.toString())

    // ── Theme ───────────────────────────────────────────────────────

    suspend fun getThemeMode(): String =
        storage.getString(SettingsKeys.THEME_MODE) ?: SettingsKeys.THEME_MODE_SYSTEM

    suspend fun setThemeMode(mode: String) =
        storage.putString(SettingsKeys.THEME_MODE, mode)

    // ── Bulk ────────────────────────────────────────────────────────

    /** Remove ALL stored settings (factory reset). */
    suspend fun clearAll() {
        storage.remove(SettingsKeys.API_BASE_URL)
        storage.remove(SettingsKeys.API_KEY)
        storage.remove(SettingsKeys.LYRICS_SYNC)
        storage.remove(SettingsKeys.DEFAULT_QUALITY)
        storage.remove(SettingsKeys.HISTORY_COUNT)
        storage.remove(SettingsKeys.LYRICS_OFFSETS)
        storage.remove(SettingsKeys.THEME_MODE)
    }

    // ── Raw access (for non-standard keys) ──────────────────────────

    suspend fun getRaw(key: String): String? = storage.getString(key)
    suspend fun putRaw(key: String, value: String?) = storage.putString(key, value)
}
