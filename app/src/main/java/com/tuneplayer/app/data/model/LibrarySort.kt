package com.tuneplayer.app.data.model

enum class LibrarySort {
    Title,
    Artist,
    Album,
    Duration,
    ;

    companion object {
        fun fromName(name: String?): LibrarySort =
            entries.firstOrNull { it.name == name } ?: Title
    }
}

/** True when the track matches [query] on title, artist, or album. */
fun Track.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        album?.contains(query, ignoreCase = true) == true
}

/**
 * Sorts by [sort], honouring [descending].
 *
 * Kept as a pure top-level function so ordering is unit-testable without
 * Compose, a ViewModel, or Android.
 */
fun List<Track>.applySort(sort: LibrarySort, descending: Boolean): List<Track> {
    val comparator: Comparator<Track> = when (sort) {
        LibrarySort.Title -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySort.Artist -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }
        LibrarySort.Album -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.album.orEmpty() }
        LibrarySort.Duration -> compareBy { it.durationMs }
    }
    return if (descending) sortedWith(comparator.reversed()) else sortedWith(comparator)
}
