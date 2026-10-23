package com.tuneplayer.app.data.prefs

import android.content.Context

/**
 * Listening stats: play counts per track, plus derived top-lists.
 *
 * Counts are kept as an id->count map in prefs. Track metadata stays in
 * the media store, so ids are the only thing worth persisting.
 *
 * ponytail: no timestamps. Adding them means a Room migration; do that
 * only when a "listened this week" view is actually requested.
 */
class PlayStatsStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuneplayer_stats", Context.MODE_PRIVATE)

    fun count(trackId: String): Int = prefs.getInt(trackId, 0)

    fun totalPlays(): Int = prefs.all.values.sumOf { it as? Int ?: 0 }

    fun distinctTracks(): Int = prefs.all.size

    fun record(trackId: String) {
        prefs.edit().putInt(trackId, count(trackId) + 1).apply()
    }

    /** Ids sorted by play count, descending. Ties break on id for stability. */
    fun topIds(limit: Int = 20): List<String> =
        prefs.all.entries
            .mapNotNull { (id, value) -> (value as? Int)?.let { id to it } }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(limit)
            .map { it.first }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
