package com.tuneplayer.app.data.lyrics

import android.content.Context

/**
 * Small on-device lyrics cache keyed by track id.
 *
 * Downloaded songs are expected to keep working without a network, so a lyric
 * lookup that succeeded once should not need YouTube or LRCLIB again. Values
 * live in SharedPreferences because lyrics are short text blobs and this avoids
 * a database migration for a feature that is read one track at a time.
 */
class LyricsCache(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(trackId: String): LyricsLookup? {
        val text = prefs.getString("$trackId:text", null)?.takeIf { it.isNotBlank() }
            ?: return null
        val source = prefs.getString("$trackId:source", null)
            ?.let { name -> LyricsSource.entries.firstOrNull { it.name == name } }
            ?: LyricsSource.YouTube
        val synced = prefs.getBoolean("$trackId:synced", false)
        return LyricsLookup(text = text, synced = synced, source = source)
    }

    fun put(trackId: String, lookup: LyricsLookup) {
        prefs.edit()
            .putString("$trackId:text", lookup.text)
            .putString("$trackId:source", lookup.source.name)
            .putBoolean("$trackId:synced", lookup.synced)
            .apply()
    }

    fun remove(trackId: String) {
        prefs.edit()
            .remove("$trackId:text")
            .remove("$trackId:source")
            .remove("$trackId:synced")
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "tuneplayer_lyrics_cache"
    }
}
