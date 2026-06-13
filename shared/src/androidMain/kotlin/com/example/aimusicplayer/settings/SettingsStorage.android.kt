package com.example.aimusicplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * File-level DataStore extension. One store per app process — keep private.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

/**
 * Android implementation of [SettingsStorage] backed by Jetpack DataStore Preferences.
 *
 * DataStore provides async, non-blocking I/O with consistency guarantees.
 * All reads suspend until the first value is emitted, then return cached values.
 *
 * @param context Android [Context] (typically Application context for singleton usage)
 */
actual class SettingsStorage(private val context: Context) : SettingsStorageProvider {

    actual override suspend fun getString(key: String): String? {
        return try {
            val preferencesKey = stringPreferencesKey(key)
            context.dataStore.data.first()[preferencesKey]
        } catch (e: Exception) {
            null
        }
    }

    actual override suspend fun putString(key: String, value: String?) {
        val preferencesKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            if (value != null) {
                preferences[preferencesKey] = value
            } else {
                preferences.remove(preferencesKey)
            }
        }
    }

    actual override suspend fun remove(key: String) {
        putString(key, null)
    }
}
