package com.tuneplayer.app.data.backup

import android.content.Context
import android.net.Uri
import com.tuneplayer.app.log.LogCatcher
import org.json.JSONArray
import org.json.JSONObject

/**
 * Whole-app preferences backup as a single JSON document.
 *
 * Each SharedPreferences file is dumped key by key with an explicit type tag,
 * so restoring does not depend on the current schema: unknown keys survive a
 * round trip and missing ones fall back to defaults.
 *
 * ponytail: one flat document, no versioning beyond [VERSION]. Add per-file
 * migrations only if a key's type ever changes.
 */
object BackupManager {

    private const val VERSION = 1
    private const val TAG = "BackupManager"

    /** Everything worth carrying between installs, and nothing transient. */
    private val PREF_FILES = listOf(
        "tuneplayer_settings",
        "tuneplayer_favorites",
        "tuneplayer_playlists",
        "tuneplayer_stats",
        "tuneplayer_queue",
    )

    fun export(context: Context, uri: Uri): Result<Int> = runCatching {
        val root = JSONObject().apply {
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("prefs", JSONObject())
        }
        val prefsJson = root.getJSONObject("prefs")

        var keys = 0
        PREF_FILES.forEach { file ->
            val all = context.getSharedPreferences(file, Context.MODE_PRIVATE).all
            val fileJson = JSONObject()
            all.forEach { (key, value) ->
                encode(value)?.let {
                    fileJson.put(key, it)
                    keys++
                }
            }
            prefsJson.put(file, fileJson)
        }

        val stream = context.contentResolver.openOutputStream(uri)
            ?: return@runCatching 0
        stream.use { it.write(root.toString().toByteArray()) }
        LogCatcher.d(TAG, "exported $keys keys to $uri")
        keys
    }

    fun restore(context: Context, uri: Uri): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)
            ?: return@runCatching 0
        val root = JSONObject(text)
        if (root.optInt("version", 0) != VERSION) {
            error("Unsupported backup version ${root.optInt("version", 0)}")
        }
        val prefsJson = root.getJSONObject("prefs")

        var keys = 0
        PREF_FILES.forEach { file ->
            val fileJson = prefsJson.optJSONObject(file) ?: return@forEach
            val editor = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            editor.clear()
            val names = fileJson.keys()
            while (names.hasNext()) {
                val key = names.next()
                val entry = fileJson.optJSONObject(key) ?: continue
                when (entry.optString("t")) {
                    "string" -> editor.putString(key, entry.optString("v"))
                    "int" -> editor.putInt(key, entry.optInt("v"))
                    "long" -> editor.putLong(key, entry.optLong("v"))
                    "float" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                    "boolean" -> editor.putBoolean(key, entry.optBoolean("v"))
                    "stringSet" -> editor.putStringSet(
                        key,
                        entry.optJSONArray("v")?.let { array ->
                            (0 until array.length()).map { array.optString(it) }.toSet()
                        }.orEmpty(),
                    )
                    else -> continue
                }
                keys++
            }
            editor.commit()
        }
        LogCatcher.d(TAG, "imported $keys keys from $uri")
        keys
    }

    private fun encode(value: Any?): JSONObject? = when (value) {
        is String -> JSONObject().put("t", "string").put("v", value)
        is Int -> JSONObject().put("t", "int").put("v", value)
        is Long -> JSONObject().put("t", "long").put("v", value)
        is Float -> JSONObject().put("t", "float").put("v", value.toDouble())
        is Boolean -> JSONObject().put("t", "boolean").put("v", value)
        is Set<*> -> JSONObject()
            .put("t", "stringSet")
            .put("v", JSONArray(value.mapNotNull { it as? String }))
        else -> null
    }
}
