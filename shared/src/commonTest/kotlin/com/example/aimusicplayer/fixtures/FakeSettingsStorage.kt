package com.example.aimusicplayer.fixtures

import com.example.aimusicplayer.settings.SettingsStorageProvider

/**
 * In-memory implementation of [SettingsStorageProvider] for unit testing.
 */
class FakeSettingsStorage : SettingsStorageProvider {

    private val store = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = store[key]

    override suspend fun putString(key: String, value: String?) {
        if (value != null) store[key] = value else store.remove(key)
    }

    override suspend fun remove(key: String) {
        store.remove(key)
    }
}
