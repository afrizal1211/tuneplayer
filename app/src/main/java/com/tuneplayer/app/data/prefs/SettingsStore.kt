package com.tuneplayer.app.data.prefs

import android.content.Context

enum class AppTheme { System, Light, Dark }

/**
 * Player settings backed by SharedPreferences.
 *
 * A single prefs file is enough here; migrate to DataStore if settings grow
 * beyond simple scalars that need asynchronous observation.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuneplayer_settings", Context.MODE_PRIVATE)

    var theme: AppTheme
        get() = runCatching { AppTheme.valueOf(prefs.getString(KEY_THEME, null).orEmpty()) }
            .getOrDefault(AppTheme.System)
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    var equalizerEnabled: Boolean
        get() = prefs.getBoolean(KEY_EQ_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_EQ_ENABLED, value).apply()
        }

    var bassBoostLevel: Int
        get() = prefs.getInt(KEY_BASS, 0).coerceIn(0, 100)
        set(value) {
            prefs.edit().putInt(KEY_BASS, value.coerceIn(0, 100)).apply()
        }

    var virtualizerLevel: Int
        get() = prefs.getInt(KEY_VIRTUALIZER, 0).coerceIn(0, 100)
        set(value) {
            prefs.edit().putInt(KEY_VIRTUALIZER, value.coerceIn(0, 100)).apply()
        }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_BASS = "bass_boost"
        const val KEY_VIRTUALIZER = "virtualizer"
    }
}
