package com.example.aimusicplayer.settings

import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [SettingsStorage] backed by NSUserDefaults.
 *
 * NSUserDefaults is the idiomatic iOS key-value store, suitable for
 * small amounts of user-facing configuration like API keys.
 */
actual class SettingsStorage : SettingsStorageProvider {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual override suspend fun getString(key: String): String? {
        return defaults.stringForKey(key)
    }

    actual override suspend fun putString(key: String, value: String?) {
        if (value != null) {
            defaults.setObject(value, forKey = key)
        } else {
            defaults.removeObjectForKey(key)
        }
        defaults.synchronize()
    }

    actual override suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }
}
