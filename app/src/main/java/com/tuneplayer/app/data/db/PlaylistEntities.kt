package com.tuneplayer.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Entity(
    tableName = "playlist_song_map",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistSongMap(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    @ColumnInfo(index = true) val playlistId: String,
    val songId: String,
    val position: Int = 0,
)

/** Like ArchiveTune: play counts keyed by song+year+month for time-bucketed stats. */
@Entity(
    tableName = "play_count",
    primaryKeys = ["songId", "year", "month"],
)
data class PlayCountEntity(
    val songId: String,
    val year: Int,
    val month: Int,
    @ColumnInfo(defaultValue = "0") val count: Int = 0,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    @ColumnInfo(defaultValue = "0") val timestamp: Long = 0L,
)

@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val songId: String,
    val timestamp: Long,
    val playTime: Long,
)

@Entity(tableName = "format")
data class FormatEntity(
    @PrimaryKey val id: String,
    val itag: Int = 0,
    val mimeType: String = "",
    val codecs: String = "",
    val bitrate: Int = 0,
    val sampleRate: Int? = null,
    val contentLength: Long = 0L,
    val playbackUrl: String? = null,
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: String,
    val source: String = "",
    val timed: Boolean = false,
    val content: String = "",
    val syncedBy: String = "",
)
