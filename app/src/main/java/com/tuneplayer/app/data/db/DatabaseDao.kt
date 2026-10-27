package com.tuneplayer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Playlist row plus its ordered song ids, resolved in one snapshot. */
data class PlaylistWithSongs(
    val playlist: PlaylistEntity,
    val songIds: List<String>,
)

@Dao
interface DatabaseDao {
    // --- Songs ---

    @Upsert
    suspend fun upsertSong(song: SongEntity)

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM song WHERE id = :id")
    suspend fun song(id: String): SongEntity?

    @Query("SELECT * FROM song ORDER BY dateAdded DESC")
    fun allSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM song WHERE liked = 1 ORDER BY rowid DESC")
    fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM song")
    suspend fun allSongsOnce(): List<SongEntity>

    @Query("UPDATE song SET liked = :liked WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean)

    @Query("UPDATE song SET playTime = playTime + :deltaMs WHERE id = :id")
    suspend fun addPlayTime(id: String, deltaMs: Long)

    // --- Playlists ---

    @Query("SELECT * FROM playlist ORDER BY position ASC, createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist ORDER BY position ASC, createdAt DESC")
    suspend fun playlistList(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun playlist(id: String): PlaylistEntity?

    @Upsert
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("UPDATE playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String)

    @Query("SELECT songId FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun playlistSongIds(playlistId: String): List<String>

    @Query("SELECT MAX(position) FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun maxPlaylistPosition(playlistId: String): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongMaps(maps: List<PlaylistSongMap>)

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removePlaylistSong(playlistId: String, songId: String)

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun playlistSongMaps(playlistId: String): List<PlaylistSongMap>

    /** Snapshot of every playlist with its ordered song ids, for UI state. */
    @Transaction
    suspend fun playlistsSnapshot(): List<PlaylistWithSongs> {
        val all = playlistList()
        return all.map { pl -> PlaylistWithSongs(pl, playlistSongIds(pl.id)) }
    }

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Transaction
    suspend fun setPlaylistSongs(playlistId: String, songIds: List<String>) {
        // Full rewrite is the simplest correct reorder; playlists are small.
        deletePlaylistSongs(playlistId)
        insertPlaylistSongMaps(
            songIds.mapIndexed { i, songId -> PlaylistSongMap(playlistId = playlistId, songId = songId, position = i) },
        )
    }

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist")
    suspend fun nextPlaylistPosition(): Int

    // --- Play counts ---

    @Query(
        """
        INSERT INTO play_count (songId, year, month, count) VALUES (:songId, :year, :month, 1)
        ON CONFLICT(songId, year, month) DO UPDATE SET count = count + 1
        """,
    )
    suspend fun incrementPlayCount(songId: String, year: Int, month: Int)

    @Query("SELECT * FROM play_count")
    suspend fun allPlayCounts(): List<PlayCountEntity>

    @Query("SELECT COALESCE(SUM(count), 0) FROM play_count")
    suspend fun totalPlays(): Int

    @Query("SELECT songId FROM play_count GROUP BY songId ORDER BY SUM(count) DESC, songId ASC LIMIT :limit")
    suspend fun topSongIds(limit: Int): List<String>

    // --- Search history ---

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun recentSearches(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun removeSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Insert
    suspend fun insertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trimSearchHistory(keep: Int = 50)

    // --- Events (play log) ---

    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Query("SELECT * FROM event ORDER BY timestamp DESC LIMIT :limit")
    fun recentEvents(limit: Int = 100): Flow<List<EventEntity>>

    // --- Format cache ---

    @Upsert
    suspend fun upsertFormat(format: FormatEntity)

    @Query("SELECT * FROM format WHERE id = :id")
    suspend fun format(id: String): FormatEntity?

    // --- Lyrics ---

    @Upsert
    suspend fun upsertLyrics(lyrics: LyricsEntity)

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun lyrics(songId: String): LyricsEntity?

    @Query("DELETE FROM lyrics")
    suspend fun clearLyrics()
}
