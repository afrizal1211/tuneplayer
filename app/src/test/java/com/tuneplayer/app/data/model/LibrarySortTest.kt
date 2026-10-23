import com.tuneplayer.app.data.model.LibrarySort
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.model.applySort
import com.tuneplayer.app.data.model.matchesQuery
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {

    private fun track(
        id: String,
        title: String = "t",
        artist: String = "a",
        album: String? = null,
        durationMs: Long = 0L,
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        isLocal = true,
    )

    @Test
    fun `sort by title is case insensitive`() {
        val list = listOf(track("1", title = "banana"), track("2", title = "Apple"), track("3", title = "cherry"))
        assertEquals(listOf("2", "1", "3"), list.applySort(LibrarySort.Title, false).map { it.id })
    }

    @Test
    fun `sort descending reverses`() {
        val list = listOf(track("1", title = "a"), track("2", title = "c"), track("3", title = "b"))
        assertEquals(listOf("2", "3", "1"), list.applySort(LibrarySort.Title, true).map { it.id })
    }

    @Test
    fun `sort by artist then title for ties`() {
        val list = listOf(
            track("1", title = "z", artist = "b"),
            track("2", title = "y", artist = "a"),
            track("3", title = "x", artist = "a"),
        )
        assertEquals(listOf("3", "2", "1"), list.applySort(LibrarySort.Artist, false).map { it.id })
    }

    @Test
    fun `sort by duration numeric not lexical`() {
        val list = listOf(
            track("1", durationMs = 100),
            track("2", durationMs = 90_000),
            track("3", durationMs = 5_000),
        )
        assertEquals(listOf("1", "3", "2"), list.applySort(LibrarySort.Duration, false).map { it.id })
    }

    @Test
    fun `null album sorts as empty not crash`() {
        val list = listOf(track("1", album = "B"), track("2", album = null), track("3", album = "A"))
        assertEquals(listOf("2", "3", "1"), list.applySort(LibrarySort.Album, false).map { it.id })
    }

    @Test
    fun `query matches title artist or album case insensitive`() {
        val t = track("1", title = "Blue Monday", artist = "New Order", album = "Power, Corruption")
        assertEquals(true, t.matchesQuery("blue"))
        assertEquals(true, t.matchesQuery("NEW ORDER"))
        assertEquals(true, t.matchesQuery("corruption"))
        assertEquals(false, t.matchesQuery("jazz"))
    }

    @Test
    fun `blank query matches everything`() {
        val t = track("1", title = "x", artist = "y")
        assertEquals(true, t.matchesQuery(""))
        assertEquals(true, t.matchesQuery("   "))
    }
}
