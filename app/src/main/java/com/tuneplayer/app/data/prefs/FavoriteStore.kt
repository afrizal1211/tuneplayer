package com.tuneplayer.app.data.prefs

import android.content.Context

/**
 * Liked track ids and recent-play history.
 *
 * Kept in SharedPreferences as two string sets: a set of ids and an
 * insertion-ordered list of the last N ids. No DB needed at this size;
 * migrate to Room when history needs queries (by date, per-track count).
 */
class FavoriteStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuneplayer_favorites", Context.MODE_PRIVATE)

    /** Newest first. Capped at [HISTORY_LIMIT] entries. */
    var history: List<String>
        get() = prefs.getString(KEY_HISTORY, null)
            ?.split(SEP)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        set(value) {
            prefs.edit()
                .putString(KEY_HISTORY, value.take(HISTORY_LIMIT).joinToString(SEP))
                .apply()
        }

    fun isFavorite(id: String): Boolean =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.contains(id) == true

    fun toggleFavorite(id: String): Boolean {
        val current = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toMutableSet()
        val added = current.add(id)
        if (!added) current.remove(id)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return added
    }

    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()

    /** Moves [id] to the front of history, deduped. */
    fun recordPlay(id: String) {
        history = (listOf(id) + history.filter { it != id })
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val SEP = "\u001F"
        const val HISTORY_LIMIT = 100
    }
}
