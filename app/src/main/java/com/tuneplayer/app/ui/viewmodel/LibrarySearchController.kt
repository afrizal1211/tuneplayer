package com.tuneplayer.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.LibrarySort
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.youtube.YouTubeSearchFilter
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.ui.TunePlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tuneplayer.app.ui.TunePlayerViewModel.Companion.TAG

internal fun TunePlayerViewModel.loadLocalMusic() {
    viewModelScope.launch {
        uiState = uiState.copy(isLoadingLocal = true, error = null)
        try {
            val tracks = localRepository.loadTracks()
            uiState = uiState.copy(localTracks = tracks, isLoadingLocal = false)
            refreshHistoryTracks()
            maybeRestoreQueue()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LogCatcher.e(TAG, "Local music load failed", error)
            uiState = uiState.copy(
                isLoadingLocal = false,
                error = error.message ?: app.getString(R.string.error_local_failed),
            )
        } finally {
            localLoadCompleted = true
            maybeRestoreQueue()
        }
    }
}

internal fun TunePlayerViewModel.refreshHistoryTracks() {
    val known = (uiState.localTracks + uiState.searchResults + uiState.downloadedTracks)
        .associateBy { it.id }
    // Ids that no longer resolve are dropped rather than shown as broken rows.
    val resolved = favoriteStore.history.mapNotNull { known[it] }
    uiState = uiState.copy(historyTracks = resolved)
}

internal fun TunePlayerViewModel.updateLibraryQuery(query: String) {
    uiState = uiState.copy(libraryQuery = query)
}

internal fun TunePlayerViewModel.setLibrarySort(sort: LibrarySort) {
    uiState = uiState.copy(librarySort = sort)
    app.settingsStore.librarySort = sort
}

internal fun TunePlayerViewModel.toggleLibrarySortDirection() {
    val descending = !uiState.librarySortDescending
    uiState = uiState.copy(librarySortDescending = descending)
    app.settingsStore.librarySortDescending = descending
}

internal fun TunePlayerViewModel.toggleFavorite(track: Track) {
    val added = favoriteStore.toggleFavorite(track.id)
    val ids = uiState.favoriteIds.toMutableSet()
    if (added) ids += track.id else ids -= track.id
    uiState = uiState.copy(favoriteIds = ids)
}

internal fun TunePlayerViewModel.updateQuery(query: String) {
    val changed = query.trim() != uiState.query.trim()
    if (changed) {
        searchJob?.cancel()
        searchJob = null
    }
    uiState = uiState.copy(
        query = query,
        searchResults = if (changed) emptyList() else uiState.searchResults,
        searchTopResult = if (changed) null else uiState.searchTopResult,
        searchCommitted = if (changed) false else uiState.searchCommitted,
        isSearching = if (changed) false else uiState.isSearching,
        error = if (changed) null else uiState.error,
        searchSuggestions = if (changed) emptyList() else uiState.searchSuggestions,
        searchRecommendations = if (changed) emptyList() else uiState.searchRecommendations,
    )
    scheduleSearchSuggestions(query)
    if (query.isBlank()) loadHomeRecommendations()
}

/**
 * Home-feed picks for the empty search tab.
 *
 * Guarded so switching tabs does not refetch a loaded shelf, while a failed
 * or empty response can be retried by opening or clearing the search tab.
 */
internal fun TunePlayerViewModel.loadHomeRecommendations() {
    if (uiState.isLoadingHomeRecommendations || uiState.homeShelves.isNotEmpty()) return
    uiState = uiState.copy(isLoadingHomeRecommendations = true)
    viewModelScope.launch {
        try {
            val shelves = youTubeRepository.homeShelves()
            val flat = shelves.firstOrNull()?.tracks.orEmpty()
            uiState = uiState.copy(
                homeShelves = shelves,
                homeRecommendations = flat,
                isLoadingHomeRecommendations = false,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LogCatcher.d(TAG, "YouTube recommendations failed: ${error.message}")
            uiState = uiState.copy(isLoadingHomeRecommendations = false)
        }
    }
}

/**
 * Debounced YouTube Music typeahead.
 *
 * Partial queries are never recorded in search history; only [search]
 * commits a term. Signed-in users get the account-aware dropdown, while
 * guests still receive YouTube's public query completions and song rows.
 */
internal fun TunePlayerViewModel.scheduleSearchSuggestions(rawQuery: String) {
    searchSuggestionsJob?.cancel()
    val query = rawQuery.trim()
    if (query.isEmpty()) {
        uiState = uiState.copy(
            searchSuggestions = emptyList(),
            searchRecommendations = emptyList(),
            isLoadingSuggestions = false,
        )
        return
    }
    uiState = uiState.copy(isLoadingSuggestions = true)
    searchSuggestionsJob = viewModelScope.launch {
        delay(300)
        try {
            val suggestions = youTubeRepository.searchSuggestions(
                input = query,
                signedIn = uiState.youtubeSignedIn,
            )
            if (uiState.query.trim() == query) {
                uiState = uiState.copy(
                    searchSuggestions = suggestions.queries,
                    searchRecommendations = suggestions.tracks,
                    isLoadingSuggestions = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            LogCatcher.d(TAG, "YouTube suggestions failed: ${error.message}")
            if (uiState.query.trim() == query) {
                uiState = uiState.copy(
                    searchSuggestions = emptyList(),
                    searchRecommendations = emptyList(),
                    isLoadingSuggestions = false,
                )
            }
        }
    }
}

internal fun TunePlayerViewModel.search() {
    val query = uiState.query.trim()
    if (query.isEmpty()) return
    app.settingsStore.recordSearch(query)
    uiState = uiState.copy(searchHistory = app.settingsStore.searchHistory)
    runSearch(query, uiState.searchFilter)
}

/**
 * Switches YouTube's search category. The filter row is only visible after
 * a committed search, so an active query always has a result set to reload.
 */
internal fun TunePlayerViewModel.setSearchFilter(filter: YouTubeSearchFilter) {
    if (filter == uiState.searchFilter) return
    val query = uiState.query.trim()
    val committed = uiState.searchCommitted
    uiState = uiState.copy(searchFilter = filter)
    if (committed && query.isNotEmpty()) runSearch(query, filter)
}

internal fun TunePlayerViewModel.runSearch(query: String, filter: YouTubeSearchFilter) {
    searchSuggestionsJob?.cancel()
    searchSuggestionsJob = null
    searchJob?.cancel()
    uiState = uiState.copy(
        searchFilter = filter,
        searchCommitted = true,
        isSearching = true,
        searchResults = emptyList(),
        searchTopResult = null,
        searchSuggestions = emptyList(),
        searchRecommendations = emptyList(),
        isLoadingSuggestions = false,
        error = null,
    )
    searchJob = viewModelScope.launch {
        try {
            val results = youTubeRepository.search(query, filter)
            if (uiState.query.trim() == query && uiState.searchFilter == filter) {
                uiState = uiState.copy(
                    searchResults = results.tracks,
                    searchTopResult = results.topResult,
                    isSearching = false,
                )
                refreshHistoryTracks()
            }
        } catch (cancelled: CancellationException) {
            // A newer query or filter owns the screen now.
            throw cancelled
        } catch (error: Exception) {
            LogCatcher.e(TAG, "YouTube search failed", error)
            if (uiState.query.trim() == query && uiState.searchFilter == filter) {
                uiState = uiState.copy(
                    isSearching = false,
                    error = error.message ?: app.getString(R.string.error_search_failed),
                )
            }
        }
    }
}

internal fun TunePlayerViewModel.clearSearchHistory() {
    app.settingsStore.clearSearchHistory()
    uiState = uiState.copy(searchHistory = emptyList())
}
