package com.example.aimusicplayer.settings

/**
 * Testable interface for cross-platform persistent key-value storage.
 * Each platform's [SettingsStorage] actual class implements this interface
 * so that [SettingsRepository] can be tested with a fake implementation.
 */
interface SettingsStorageProvider {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String?)
    suspend fun remove(key: String)
}

/**
 * Cross-platform persistent key-value storage for app settings.
 *
 * ## Usage
 * ```kotlin
 * val storage: SettingsStorage = ...
 * storage.putString(SettingsKeys.API_KEY, "sk-xxx")
 * val key = storage.getString(SettingsKeys.API_KEY)
 * ```
 *
 * **Android**: Backed by Jetpack DataStore Preferences (async, non-blocking).
 * **iOS**: Backed by NSUserDefaults.
 */
expect class SettingsStorage : SettingsStorageProvider {
    override suspend fun getString(key: String): String?
    override suspend fun putString(key: String, value: String?)
    override suspend fun remove(key: String)
}
