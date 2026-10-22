import com.tuneplayer.app.data.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses mm ss and millis`() {
        val lrc = "[00:01.50]Hello\n[00:03.00]World"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Hello", lines[0].text)
        assertEquals(3000L, lines[1].timeMs)
    }

    @Test
    fun `multiple tags on one line`() {
        val lines = LrcParser.parse("[00:10.00][00:40.00]Chorus")
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
        assertEquals(40_000L, lines[1].timeMs)
    }

    @Test
    fun `two digit fraction treated as centis`() {
        val lines = LrcParser.parse("[01:02:50]x") // hour form also accepted
        assertEquals(1, lines.size)
        assertEquals(62_500L, lines[0].timeMs)
    }

    @Test
    fun `no tags yields empty so caller falls back to plain`() {
        assertEquals(emptyList<LrcParser.LrcLine>(), LrcParser.parse("just words"))
        assertEquals(emptyList<LrcParser.LrcLine>(), LrcParser.parse(null))
    }

    @Test
    fun `active index tracks position`() {
        val lines = LrcParser.parse("[00:00.00]a\n[00:10.00]b\n[00:20.00]c")
        assertEquals(-1, LrcParser.activeIndex(lines, -5))
        assertEquals(0, LrcParser.activeIndex(lines, 0))
        assertEquals(0, LrcParser.activeIndex(lines, 9_999))
        assertEquals(1, LrcParser.activeIndex(lines, 10_000))
        assertEquals(2, LrcParser.activeIndex(lines, 999_999))
    }

    @Test
    fun `sorted by time`() {
        val lines = LrcParser.parse("[00:20.00]c\n[00:05.00]b\n[00:00.00]a")
        assertEquals(listOf("a", "b", "c"), lines.map { it.text })
    }
}
