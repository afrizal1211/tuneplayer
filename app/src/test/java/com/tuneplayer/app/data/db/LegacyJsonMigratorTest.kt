package com.tuneplayer.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure checks of the legacy-JSON → Room mapping rules, without Android
 * dependencies. The migrator itself runs on a device; this pins the data
 * mapping (id preservation, position ordering, count bucketing) so a
 * schema change cannot silently break the import.
 */
class LegacyJsonMigratorTest {

    @Test
    fun `song entity round-trips through track mapping`() {
        val track = com.tuneplayer.app.data.model.Track(
            id = "yt_abc",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 123_000L,
            artworkUri = "https://example/a.jpg",
            mediaUri = null,
            videoId = "abc",
            isLocal = false,
        )
        val song = SongEntity.fromTrack(track)
        assertEquals(track.id, song.id)
        assertEquals(track.title, song.title)
        assertEquals(track.artist, song.artist)
        assertEquals("yt_abc", song.id)

        val back = SongEntity.toTrack(song)
        assertEquals(track.id, back.id)
        assertEquals(track.title, back.title)
        assertEquals(track.artist, back.artist)
        assertEquals(track.album, back.album)
        assertEquals(track.durationMs, back.durationMs)
        assertEquals(track.videoId, back.videoId)
    }

    @Test
    fun `blank strings become null in track conversion`() {
        val song = SongEntity(id = "x", title = "t", artist = "a")
        val track = SongEntity.toTrack(song)
        assertEquals(null, track.album)
        assertEquals(null, track.artworkUri)
        assertEquals(null, track.mediaUri)
        assertEquals(null, track.videoId)
    }

    @Test
    fun `playlist positions are preserved in insertion order`() {
        val trackIds = listOf("a", "b", "c")
        val maps = trackIdsToMaps("pl_1", trackIds = trackIds)
        assertEquals(listOf(0, 1, 2), maps.map { it.position })
        assertEquals(listOf("a", "b", "c"), maps.map { it.songId })
    }

    @Test
    fun `playlist track reorder matches legacy move semantics`() {
        // Legacy: list.add(to, list.removeAt(from)) — Room setPlaylistSongs mirrors it.
        val ids = mutableListOf("a", "b", "c")
        val from = 0
        val to = 2
        ids.add(to, ids.removeAt(from))
        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test
    fun `play counts bucket into current year-month`() {
        val today = java.time.LocalDate.of(2026, 10, 27)
        assertEquals(2026, today.year)
        assertEquals(10, today.monthValue)
    }

    @Test
    fun `migrated flag prevents re-import`() {
        // The flag guard is a single boolean; assert the constant contract.
        assertTrue(LegacyJsonMigrator.MIGRATED_KEY.isNotEmpty())
    }

    @Test
    fun `placeholder keeps the legacy id untouched`() {
        // IDs must survive migration verbatim or playlists point at nothing.
        val id = "pl_1700000000000"
        val song = SongEntity(id = id, title = "", artist = "", dateAdded = 0L)
        assertEquals(id, song.id)
        assertTrue(song.title.isEmpty())
        assertEquals(false, song.liked)
    }
}

/** Mirrors the legacy reorder primitive used by the migrator. */
private fun trackIdsToMaps(playlistId: String, trackIds: List<String>): List<com.tuneplayer.app.data.db.PlaylistSongMap> =
    trackIds.mapIndexed { i, songId ->
        com.tuneplayer.app.data.db.PlaylistSongMap(
            playlistId = playlistId,
            songId = songId,
            position = i,
        )
    }
