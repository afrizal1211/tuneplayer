package com.tuneplayer.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameAlbumTest {

    private fun item(title: String, artist: String, album: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(title)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build(),
            )
            .build()

    @Test
    fun `same album and artist is the same album`() {
        assertTrue(sameAlbum(item("a", "X", "Album"), item("b", "X", "Album")))
    }

    @Test
    fun `same album different artist is not the same album`() {
        assertFalse(sameAlbum(item("a", "X", "Album"), item("b", "Y", "Album")))
    }

    @Test
    fun `different albums are not the same album`() {
        assertFalse(sameAlbum(item("a", "X", "A"), item("b", "X", "B")))
    }

    @Test
    fun `unknown album is not the same album`() {
        assertFalse(sameAlbum(item("a", "X", ""), item("b", "X", "")))
    }
}
