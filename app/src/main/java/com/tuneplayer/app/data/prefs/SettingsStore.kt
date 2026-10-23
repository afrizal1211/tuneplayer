package com.tuneplayer.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.tuneplayer.app.data.model.LibrarySort
import com.tuneplayer.app.data.model.YouTubeAudioQuality

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

    var librarySort: LibrarySort
        get() = LibrarySort.fromName(prefs.getString(KEY_LIBRARY_SORT, null))
        set(value) {
            prefs.edit().putString(KEY_LIBRARY_SORT, value.name).apply()
        }

    var librarySortDescending: Boolean
        get() = prefs.getBoolean(KEY_LIBRARY_SORT_DESC, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LIBRARY_SORT_DESC, value).apply()
        }

    /** Equalizer band levels in millibels, custom order matches the device bands. */
    var eqBands: List<Int>
        get() = prefs.getString(KEY_EQ_BANDS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
        set(value) {
            prefs.edit().putString(KEY_EQ_BANDS, value.joinToString(",")).apply()
        }

    /** Index into the preset list, or -1 when the bands were tuned by hand. */
    var eqPreset: Int
        get() = prefs.getInt(KEY_EQ_PRESET, -1)
        set(value) {
            prefs.edit().putInt(KEY_EQ_PRESET, value).apply()
        }

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }

    var pureBlack: Boolean
        get() = prefs.getBoolean(KEY_PURE_BLACK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PURE_BLACK, value).apply()
        }

    var gridLayout: Boolean
        get() = prefs.getBoolean(KEY_GRID_LAYOUT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GRID_LAYOUT, value).apply()
        }

    /** Skips silent passages during playback (ExoPlayer silence skipping). */
    var skipSilence: Boolean
        get() = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
        }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    /** YouTube Music session captured from the in-app WebView login. */
    var youtubeCookie: String?
        get() = prefs.getString(KEY_YOUTUBE_COOKIE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_YOUTUBE_COOKIE, value).apply()
        }

    var youtubeDataSyncId: String?
        get() = prefs.getString(KEY_YOUTUBE_DATA_SYNC_ID, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_YOUTUBE_DATA_SYNC_ID, value).apply()
        }

    var youtubeAuthUser: String
        get() = prefs.getString(KEY_YOUTUBE_AUTH_USER, "0").orEmpty().ifBlank { "0" }
        set(value) {
            prefs.edit().putString(KEY_YOUTUBE_AUTH_USER, value.filter { it.isDigit() }.ifBlank { "0" }).apply()
        }

    var youtubeAudioQuality: YouTubeAudioQuality
        get() = runCatching {
            YouTubeAudioQuality.valueOf(prefs.getString(KEY_YOUTUBE_QUALITY, null).orEmpty())
        }.getOrDefault(YouTubeAudioQuality.High)
        set(value) {
            prefs.edit().putString(KEY_YOUTUBE_QUALITY, value.name).apply()
        }

    /** Shift applied to synced lyric timestamps, in milliseconds. */
    var lyricsOffsetMs: Long
        get() = prefs.getLong(KEY_LYRICS_OFFSET, 0L).coerceIn(-5_000L, 5_000L)
        set(value) {
            prefs.edit().putLong(KEY_LYRICS_OFFSET, value.coerceIn(-5_000L, 5_000L)).apply()
        }

    /** Discord gateway presence is opt-in because it needs a user token. */
    var discordRpcEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISCORD_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DISCORD_ENABLED, value).apply()
        }

    var discordToken: String?
        get() = prefs.getString(KEY_DISCORD_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_DISCORD_TOKEN, value?.takeIf { it.isNotBlank() }).apply()
        }

    fun clearDiscordToken() {
        prefs.edit().remove(KEY_DISCORD_TOKEN).putBoolean(KEY_DISCORD_ENABLED, false).apply()
    }

    fun clearYouTubeSession() {
        prefs.edit()
            .remove(KEY_YOUTUBE_COOKIE)
            .remove(KEY_YOUTUBE_DATA_SYNC_ID)
            .remove(KEY_YOUTUBE_AUTH_USER)
            .apply()
    }

    var searchHistory: List<String>
        get() = prefs.getString(KEY_SEARCH_HISTORY, null)
            ?.split(SEP)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SEARCH_HISTORY, value.take(SEARCH_HISTORY_LIMIT).joinToString(SEP)).apply()
        }

    /** Newest first, deduped, capped. */
    fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        searchHistory = (listOf(trimmed) + searchHistory.filter { !it.equals(trimmed, ignoreCase = true) })
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
    }

    /** Let the playback service react to effect changes made by the UI. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_BASS = "bass_boost"
        const val KEY_VIRTUALIZER = "virtualizer"
        const val KEY_LIBRARY_SORT = "library_sort"
        const val KEY_LIBRARY_SORT_DESC = "library_sort_desc"
        const val KEY_GRID_LAYOUT = "grid_layout"
        const val KEY_EQ_BANDS = "eq_bands"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_PURE_BLACK = "pure_black"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_YOUTUBE_COOKIE = "youtube_cookie"
        const val KEY_YOUTUBE_DATA_SYNC_ID = "youtube_data_sync_id"
        const val KEY_YOUTUBE_AUTH_USER = "youtube_auth_user"
        const val KEY_YOUTUBE_QUALITY = "youtube_audio_quality"
        const val KEY_LYRICS_OFFSET = "lyrics_offset_ms"
        const val KEY_DISCORD_ENABLED = "discord_rpc_enabled"
        const val KEY_DISCORD_TOKEN = "discord_token"
        const val SEP = "\u001F"
        const val SEARCH_HISTORY_LIMIT = 20
    }
}
