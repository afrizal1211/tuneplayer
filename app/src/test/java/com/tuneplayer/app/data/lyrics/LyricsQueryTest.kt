package com.tuneplayer.app.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsQueryTest {

    @Test
    fun `bracketed credits are removed`() {
        assertEquals("Dracula", "Dracula (feat. JENNIE)".forLyricsSearch())
        assertEquals("Song", "Song [ft. Someone]".forLyricsSearch())
    }

    @Test
    fun `upload packaging is removed`() {
        assertEquals("Song", "Song (Official Video)".forLyricsSearch())
        assertEquals("Song", "Song [Lyrics]".forLyricsSearch())
    }

    @Test
    fun `different recordings keep their qualifier`() {
        assertEquals("Song (Live)", "Song (Live)".forLyricsSearch())
        assertEquals("Song (Remix)", "Song (Remix)".forLyricsSearch())
    }

    @Test
    fun `topic artist suffix is removed`() {
        assertEquals("Artist", "Artist - Topic".artistForLyricsSearch())
        assertEquals("Artist", "Artist".artistForLyricsSearch())
    }
}
