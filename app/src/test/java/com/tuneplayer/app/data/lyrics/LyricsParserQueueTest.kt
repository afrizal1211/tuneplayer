package com.tuneplayer.app.data.lyrics

import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.playback.QueueBuilder
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {

    @Test
    fun `parses line only lrc`() {
        val lines = LyricsParser.parse("[00:12.00]Hello darkness\n[00:17.00]my old friend")
        assertEquals(2, lines.size)
        assertEquals(12_000L, lines[0].timeMs)
        assertEquals("Hello darkness", lines[0].text)
        assertNull(lines[0].words)
        assertFalse(LyricsParser.isWordSynced(lines))
    }

    @Test
    fun `parses lrc word extension with absolute tags`() {
        val lines = LyricsParser.parse("[00:12.00]Hello <00:12.00>dark <00:12.40>ness")
        assertEquals(1, lines.size)
        val words = lines[0].words
        assertNotNull(words)
        assertEquals(2, words!!.size)
        assertEquals("dark", words[0].text)
        assertEquals(12_000L, words[0].startTimeMs)
        assertEquals(12_400L, words[0].endTimeMs)
        assertEquals("ness", words[1].text)
        assertEquals(12_400L, words[1].startTimeMs)
        assertTrue(LyricsParser.isWordSynced(lines))
    }

    @Test
    fun `parses qrc offsets relative to line`() {
        val lines = LyricsParser.parse("[00:12.00]Hello(0,400)darkness(400,900)")
        val words = lines[0].words
        assertNotNull(words)
        assertEquals(2, words!!.size)
        assertEquals("Hello", words[0].text)
        assertEquals(12_000L, words[0].startTimeMs)
        assertEquals(12_400L, words[0].endTimeMs)
        assertEquals("darkness", words[1].text)
        assertEquals(12_400L, words[1].startTimeMs)
        assertEquals(13_300L, words[1].endTimeMs)
    }

    @Test
    fun `multiple line tags share one body`() {
        val lines = LyricsParser.parse("[00:01.00][00:30.00]Chorus")
        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals(30_000L, lines[1].timeMs)
        assertEquals("Chorus", lines[1].text)
    }

    @Test
    fun `instrumental markers become instrumental lines`() {
        val lines = LyricsParser.parse("[00:05.00]♪\n[00:20.00]****")
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.isInstrumental })
    }

    @Test
    fun `keeps the sung text verbatim, packaging included`() {
        val lines = LyricsParser.parse("[00:01.00]Song (Official Video)")
        assertEquals("Song (Official Video)", lines[0].text)
    }

    @Test
    fun `activeLineIndex picks last line at or before position`() {
        val lines = LyricsParser.parse("[00:00.00]a\n[00:10.00]b\n[00:20.00]c")
        assertEquals(-1, activeLineIndex(lines, -1))
        assertEquals(0, activeLineIndex(lines, 0))
        assertEquals(0, activeLineIndex(lines, 9_999))
        assertEquals(1, activeLineIndex(lines, 10_000))
        assertEquals(2, activeLineIndex(lines, 99_000))
    }

    @Test
    fun `activeWordIndex picks word containing position`() {
        val lines = LyricsParser.parse("[00:10.00]one <00:10.00>two <00:10.50>three")
        val line = lines[0]
        assertEquals(-1, activeWordIndex(line, 9_999))
        assertEquals(0, activeWordIndex(line, 10_100))
        assertEquals(1, activeWordIndex(line, 10_600))
        assertEquals(-1, activeWordIndex(LyricsLine(0L, "x", null), 0))
    }

    @Test
    fun `blank input yields nothing`() {
        assertTrue(LyricsParser.parse(null).isEmpty())
        assertTrue(LyricsParser.parse("   ").isEmpty())
        assertTrue(LyricsParser.parse("no tags here").isEmpty())
    }
}

class QueueBuilderTest {

    private fun track(id: String, title: String, artist: String = "A") =
        Track(id = id, title = title, artist = artist)

    @Test
    fun `drops duplicate id`() {
        val out = QueueBuilder.extend(listOf(track("1", "S")), listOf(track("1", "S")), 10)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `drops same recording under different id`() {
        val out = QueueBuilder.extend(
            listOf(track("1", "Kesariya (From Brahmastra)", "Pritam")),
            listOf(track("2", "kesariya", "Arijit Singh, Pritam")),
            10,
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `keeps different recordings with shared artist`() {
        val out = QueueBuilder.extend(
            listOf(track("1", "Kesariya", "Pritam")),
            listOf(track("2", "Raabta", "Pritam")),
            10,
        )
        assertEquals(1, out.size)
    }

    @Test
    fun `caps one artist per batch`() {
        val existing = emptyList<Track>()
        val candidates = (1..10).map { track("$it", "Song $it", "One Artist") }
        val out = QueueBuilder.extend(existing, candidates, 20)
        assertEquals(QueueBuilder.PER_ARTIST_LIMIT, out.size)
    }

    @Test
    fun `seed artist gets a higher cap`() {
        val seed = track("seed", "Seed", "Pritam")
        val candidates = (1..8).map { track("$it", "Song $it", "Pritam") }
        val out = QueueBuilder.extend(listOf(seed), candidates, 20)
        assertEquals(QueueBuilder.SEED_ARTIST_LIMIT, out.size)
    }

    @Test
    fun `respects limit`() {
        val candidates = (1..50).map { track("$it", "Song $it", "Artist $it") }
        val out = QueueBuilder.extend(emptyList(), candidates, 5)
        assertEquals(5, out.size)
    }

    @Test
    fun `prefers audio cut over music video`() {
        // The video normalises to the same recording, so only the audio cut survives.
        val candidates = listOf(
            track("vid", "Song | Official Video", "A").copy(isVideo = true),
            track("aud", "Song", "A"),
        )
        val out = QueueBuilder.extend(emptyList(), candidates, 10)
        assertEquals(1, out.size)
        assertEquals("aud", out[0].id)
    }

    @Test
    fun `falls back to videos when no songs available`() {
        val candidates = listOf(track("vid", "Song | Official Video", "A").copy(isVideo = true))
        val out = QueueBuilder.extend(emptyList(), candidates, 10)
        assertEquals(1, out.size)
    }

    @Test
    fun `normalised title drops packaging but keeps mix markers`() {
        assertEquals("kesariya", QueueBuilder.normalisedTitle("Kesariya (Official Video)"))
        assertEquals(
            "kesariya remix",
            QueueBuilder.normalisedTitle("Kesariya (Remix)"),
        )
    }

    @Test
    fun `artistSet ignores billing order and topic suffix`() {
        val left = QueueBuilder.artistSet("Pritam, Arijit Singh & Shilpa Rao - Topic")
        val right = QueueBuilder.artistSet("Shilpa Rao, Arijit Singh, & Pritam")
        assertTrue(left.any { it in right })
    }
}
