package com.tuneplayer.app.data.db

import android.content.Context
import com.tuneplayer.app.data.prefs.FavoriteStore
import com.tuneplayer.app.data.prefs.PlayStatsStore
import com.tuneplayer.app.data.prefs.PlaylistStore
import java.time.LocalDate

/**
 * One-shot import of the legacy JSON/SharedPreferences stores into Room.
 *
 * Runs at most once: guarded by a flag in Settings prefs. Legacy stores stay
 * readable forever (cheap), but every new write goes through the DAO.
 * ponytail: delete legacy stores once telemetry shows >99% of installs on
 * Room version >= 1.
 */
object LegacyJsonMigrator {
    const val MIGRATED_KEY = "room_migration_v1_done"

    suspend fun migrateIfNeeded(
        context: Context,
        database: TunePlayerDatabase,
        playlists: PlaylistStore,
        favorites: com.tuneplayer.app.data.prefs.FavoriteStore,
        stats: com.tuneplayer.app.data.prefs.PlayStatsStore,
    ) {
        val flagPrefs = context.getSharedPreferences("tuneplayer_migration", Context.MODE_PRIVATE)
        if (flagPrefs.getBoolean(MIGRATED_KEY, false)) return

        val dao = database.dao()
        val now = System.currentTimeMillis()

        // Songs referenced anywhere (history drives the set; favorites/stats may
        // reference songs whose metadata is unknown and get a placeholder row).
        val knownSongs = LinkedHashMap<String, SongEntity>()
        fun placeholder(id: String) = knownSongs.getOrPut(id) {
            SongEntity(id = id, title = "", artist = "", dateAdded = now)
        }

        favorites.history.forEach { id -> knownSongs.getOrPut(id) { placeholder(id) } }
        favorites.favorites().forEach { id -> knownSongs.getOrPut(id) { placeholder(id) } }
        stats.topIds(Int.MAX_VALUE).forEach { id -> knownSongs.getOrPut(id) { placeholder(id) } }
        playlists.all().forEach { pl -> pl.trackIds.forEach { knownSongs.getOrPut(it) { placeholder(it) } } }

        dao.upsertSongs(knownSongs.values.toList())

        // Liked songs.
        favorites.favorites().forEach { id ->
            knownSongs[id]?.let { dao.setLiked(id, true) }
        }

        // Play history: newest first -> events with descending timestamps.
        favorites.history.forEachIndexed { index, songId ->
            dao.insertEvent(
                EventEntity(songId = songId, timestamp = now - index, playTime = 0),
            )
        }

        // Play counts: legacy store has no timestamps; bucket everything into
        // the current month so stats screens have data from day one.
        val today = LocalDate.now()
        stats.topIds(Int.MAX_VALUE).forEach { id ->
            val count = stats.count(id)
            if (count > 0) {
                dao.upsertSong(placeholder(id)) // ensure FK target exists
                dao.incrementPlayCount(id, today.year, today.monthValue)
            }
        }

        // Playlists with their ordered tracks.
        var position = 0
        playlists.all().forEach { legacy ->
            dao.insertPlaylist(
                PlaylistEntity(
                    id = legacy.id,
                    name = legacy.name,
                    createdAt = now,
                    position = position++,
                ),
            )
            dao.setPlaylistSongs(legacy.id, legacy.trackIds)
        }

        flagPrefs.edit().putBoolean(MIGRATED_KEY, true).apply()
    }
}
