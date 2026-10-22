package com.tuneplayer.app.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.tuneplayer.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicRepository(private val context: Context) {

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        runCatching {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    tracks += Track(
                        id = "local:$id",
                        title = cursor.getString(titleColumn) ?: "Unknown title",
                        artist = cursor.getString(artistColumn) ?: "Unknown artist",
                        album = cursor.getString(albumColumn),
                        durationMs = cursor.getLong(durationColumn),
                        artworkUri = ContentUris
                            .withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                            .toString(),
                        mediaUri = ContentUris.withAppendedId(collection, id).toString(),
                        isLocal = true,
                    )
                }
            }
        }
        tracks
    }
}
