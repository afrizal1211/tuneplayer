package com.tuneplayer.app.data.prefs

import android.content.Context

/**
 * Last known playback queue, so the app reopens where it left off.
 *
 * ponytail: ids joined by a unit separator in prefs. Ids come from MediaStore
 * or YouTube, neither of which can contain 0x1F. Swap for Room if the queue
 * ever needs per-entry metadata (added-at, source, etc).
 */
class QueueStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuneplayer_queue", Context.MODE_PRIVATE)

    var ids: List<String>
        get() = prefs.getString(KEY_IDS, null)
            ?.split(SEP)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        set(value) {
            prefs.edit().putString(KEY_IDS, value.joinToString(SEP)).apply()
        }

    var index: Int
        get() = prefs.getInt(KEY_INDEX, 0)
        set(value) {
            prefs.edit().putInt(KEY_INDEX, value).apply()
        }

    var positionMs: Long
        get() = prefs.getLong(KEY_POSITION, 0L)
        set(value) {
            prefs.edit().putLong(KEY_POSITION, value).apply()
        }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_IDS = "queue_ids"
        const val KEY_INDEX = "queue_index"
        const val KEY_POSITION = "queue_position"
        const val SEP = "\u001F"
    }
}
