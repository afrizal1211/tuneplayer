package com.tuneplayer.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioInfoTest {

    @Test
    fun `webm opus maps to opus`() {
        assertEquals("Opus", AudioInfo.codecLabel("audio/webm; codecs=opus"))
    }

    @Test
    fun `mp4 maps to aac`() {
        assertEquals("AAC", AudioInfo.codecLabel("audio/mp4; codecs=mp4a.40.2"))
        assertEquals("AAC", AudioInfo.codecLabel("audio/mp4a-latm"))
    }

    @Test
    fun `mpeg maps to mp3`() {
        assertEquals("MP3", AudioInfo.codecLabel("audio/mpeg"))
    }

    @Test
    fun `unknown mime falls back to codec string`() {
        assertEquals("ALAC", AudioInfo.codecLabel("audio/x-alac", "alac"))
        assertNull(AudioInfo.codecLabel(null, null))
    }
}
