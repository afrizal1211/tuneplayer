package com.tuneplayer.app.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCatcherTest {

    @Test
    fun `snapshot is oldest first and bounded by capacity`() {
        LogCatcher.clear()
        repeat(250) { LogCatcher.d("t", "line $it") }
        val entries = LogCatcher.snapshot()
        assertEquals(200, entries.size)
        assertEquals("line 50", entries.first().message)
        assertEquals("line 249", entries.last().message)
    }

    @Test
    fun `export renders level and tag`() {
        LogCatcher.clear()
        LogCatcher.e("crash", "boom", IllegalStateException("bad state"))
        val text = LogCatcher.export()
        assertTrue(text.contains("E/crash: boom"))
        assertTrue(text.contains("IllegalStateException: bad state"))
    }

    @Test
    fun `clear empties buffer`() {
        LogCatcher.d("t", "x")
        LogCatcher.clear()
        assertTrue(LogCatcher.snapshot().isEmpty())
    }

    @Test
    fun `not installed by default in a plain JVM test`() {
        // install() runs in Application.onCreate, which unit tests never invoke.
        assertFalse(LogCatcher.isInstalled)
    }
}
