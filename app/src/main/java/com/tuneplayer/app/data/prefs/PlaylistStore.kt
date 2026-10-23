package com.tuneplayer.app.data.prefs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * User playlists, backed by SharedPreferences as JSON.
 *
 * ponytail: JSON-in-prefs. Fine for a few hundred playlists of a few
 * hundred ids. Migrate to Room when playlists need reordering, or when
 * play counts per playlist become a query.
 */
class PlaylistStore(context: Context) {

    private val prefs = context.getSharedPreferences("tuneplayer_playlists", Context.MODE_PRIVATE)

    /** All playlists, newest first. */
    fun all(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toPlaylist() }
        }.getOrDefault(emptyList())
    }

    fun create(name: String): Playlist {
        val playlist = Playlist(
            id = "pl_${System.currentTimeMillis()}",
            name = name.trim(),
            trackIds = emptyList(),
        )
        write(all() + playlist)
        return playlist
    }

    fun rename(id: String, name: String) = mutate(id) { it.copy(name = name.trim()) }

    fun delete(id: String) = write(all().filterNot { it.id == id })

    fun addTrack(id: String, trackId: String) = mutate(id) {
        if (trackId in it.trackIds) it else it.copy(trackIds = it.trackIds + trackId)
    }

    fun removeTrack(id: String, trackId: String) = mutate(id) {
        it.copy(trackIds = it.trackIds - trackId)
    }

    fun moveTrack(id: String, from: Int, to: Int) = mutate(id) {
        if (from !in it.trackIds.indices || to !in it.trackIds.indices) {
            it
        } else {
            val list = it.trackIds.toMutableList()
            list.add(to, list.removeAt(from))
            it.copy(trackIds = list)
        }
    }

    private fun mutate(id: String, transform: (Playlist) -> Playlist) {
        write(all().map { if (it.id == id) transform(it) else it })
    }

    private fun write(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    private fun JSONObject.toPlaylist(): Playlist {
        val ids = getJSONArray("trackIds")
        return Playlist(
            id = getString("id"),
            name = getString("name"),
            trackIds = (0 until ids.length()).map { ids.getString(it) },
        )
    }

    private fun Playlist.toJson(): JSONObject {
        val ids = JSONArray()
        trackIds.forEach { ids.put(it) }
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("trackIds", ids)
        }
    }

    data class Playlist(
        val id: String,
        val name: String,
        val trackIds: List<String>,
    )

    private companion object {
        const val KEY_PLAYLISTS = "playlists"
    }
}
