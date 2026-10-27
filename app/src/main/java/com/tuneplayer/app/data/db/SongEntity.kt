package com.tuneplayer.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * One playable item (local or YouTube). `id` is the stable key used across
 * the app: MediaStore id for local, videoId for YouTube.
 *
 * ponytail: no album/artist relation tables yet (ArchiveTune has them). Add
 * when Library grouping needs SQL joins instead of in-memory maps.
 */
@Entity(tableName = "song")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    @ColumnInfo(defaultValue = "") val album: String = "",
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0L,
    @ColumnInfo(defaultValue = "") val artworkUri: String = "",
    @ColumnInfo(defaultValue = "") val mediaUri: String = "",
    @ColumnInfo(defaultValue = "") val videoId: String = "",
    @ColumnInfo(defaultValue = "0") val isLocal: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isVideo: Boolean = false,
    @ColumnInfo(defaultValue = "0") val liked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val inLibrary: Boolean = false,
    @ColumnInfo(defaultValue = "0") val dateAdded: Long = 0L,
    @ColumnInfo(defaultValue = "0") val playTime: Long = 0L,
) {
    companion object {
        fun fromTrack(track: com.tuneplayer.app.data.model.Track): SongEntity =
            SongEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album ?: "",
                durationMs = track.durationMs,
                artworkUri = track.artworkUri ?: "",
                mediaUri = track.mediaUri ?: "",
                videoId = track.videoId ?: "",
                isLocal = track.isLocal,
                isVideo = track.isVideo,
            )

        fun toTrack(song: SongEntity): com.tuneplayer.app.data.model.Track =
            com.tuneplayer.app.data.model.Track(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album.ifEmpty { null },
                durationMs = song.durationMs,
                artworkUri = song.artworkUri.ifEmpty { null },
                mediaUri = song.mediaUri.ifEmpty { null },
                videoId = song.videoId.ifEmpty { null },
                isLocal = song.isLocal,
                isVideo = song.isVideo,
            )
    }
}
