package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.youtube.YouTubeSearchFilter
import com.tuneplayer.app.ui.fadingEdge
import com.tuneplayer.app.ui.components.Artwork
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackList
import com.tuneplayer.app.ui.screens.YouTubeScreen
import com.tuneplayer.app.ui.util.formatDuration

internal fun YouTubeScreen(
    query: String,
    results: List<Track>,
    topResult: Track?,
    searchFilter: YouTubeSearchFilter,
    searchCommitted: Boolean,
    favoriteIds: Set<String>,
    isSearching: Boolean,
    suggestions: List<String>,
    recommendations: List<Track>,
    isLoadingSuggestions: Boolean,
    homeRecommendations: List<Track>,
    isLoadingHomeRecommendations: Boolean,
    searchHistory: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFilterChange: (YouTubeSearchFilter) -> Unit,
    onClearSearchHistory: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxSize()) {
        YouTubeSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
        )

        if (searchCommitted && query.isNotBlank()) {
            SearchFilterRow(
                selected = searchFilter,
                onSelect = onFilterChange,
            )
        }

        if (searchHistory.isNotEmpty() && query.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_history_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearSearchHistory) {
                    Text(stringResource(R.string.search_history_clear))
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                searchHistory.forEach { past ->
                    SuggestionChip(
                        onClick = {
                            onQueryChange(past)
                            onSearch()
                        },
                        label = { Text(past) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            query.isEmpty() && homeRecommendations.isNotEmpty() -> {
                SearchSuggestionList(
                    query = "",
                    suggestions = emptyList(),
                    recommendations = homeRecommendations,
                    favoriteIds = favoriteIds,
                    onSuggestionClick = { _ -> },
                    onSearchQuery = {},
                    onFillSuggestion = { _ -> },
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                )
            }

            query.isEmpty() && isLoadingHomeRecommendations -> {
                SearchLoading()
            }

            query.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.search_empty),
                    hint = stringResource(R.string.search_empty_hint),
                )
            }

            !searchCommitted && isLoadingSuggestions -> {
                SearchLoading()
            }

            !searchCommitted -> {
                SearchSuggestionList(
                    query = query,
                    suggestions = suggestions,
                    recommendations = recommendations,
                    favoriteIds = favoriteIds,
                    onSuggestionClick = { term ->
                        onQueryChange(term)
                        onSearch()
                        focusManager.clearFocus()
                    },
                    onSearchQuery = {
                        onSearch()
                        focusManager.clearFocus()
                    },
                    onFillSuggestion = onQueryChange,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                )
            }

            isSearching -> {
                SearchLoading()
            }

            results.isEmpty() && topResult == null -> {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.search_empty),
                    hint = stringResource(R.string.search_empty_hint),
                )
            }

            else -> {
                SearchResultsList(
                    tracks = results,
                    topResult = topResult,
                    filter = searchFilter,
                    favoriteIds = favoriteIds,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                )
            }
        }
    }
}

@Composable
internal fun YouTubeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val submit = {
        if (query.isNotBlank()) {
            onSearch()
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = submit,
            enabled = query.isNotBlank(),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = {
                    onQueryChange("")
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun SearchFilterRow(
    selected: YouTubeSearchFilter,
    onSelect: (YouTubeSearchFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .fadingEdge(left = 16.dp, right = 16.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        YouTubeSearchFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable(enabled = !active) { onSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = searchFilterLabel(filter),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun searchFilterLabel(filter: YouTubeSearchFilter): String = when (filter) {
    YouTubeSearchFilter.ALL -> stringResource(R.string.search_filter_all)
    YouTubeSearchFilter.SONGS -> stringResource(R.string.search_filter_songs)
    YouTubeSearchFilter.VIDEOS -> stringResource(R.string.search_filter_videos)
}

@Composable
internal fun SearchLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun SearchResultsList(
    tracks: List<Track>,
    topResult: Track?,
    filter: YouTubeSearchFilter,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    val promoted = topResult.takeIf { filter == YouTubeSearchFilter.ALL }
    val sectionTitle = when (filter) {
        YouTubeSearchFilter.VIDEOS -> stringResource(R.string.search_filter_videos)
        else -> stringResource(R.string.search_filter_songs)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (promoted != null) {
            item(key = "search_top_result") {
                SearchTopResultCard(
                    track = promoted,
                    isFavorite = promoted.id in favoriteIds,
                    onPlay = { onPlay(promoted) },
                    onToggleFavorite = { onToggleFavorite(promoted) },
                )
            }
        }
        if (tracks.isNotEmpty()) {
            item(key = "search_results_header") {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(items = tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isFavorite = track.id in favoriteIds,
                    onPlay = { onPlay(track) },
                    onPlayNext = { onPlayNext(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onShare = { onShare(track) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
internal fun SearchTopResultCard(
    track: Track,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.search_top_result),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(onClick = onPlay)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track = track, size = 80, cornerRadius = 12)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(track.artist)
                        if (track.durationMs > 0) {
                            append(" · ")
                            append(formatDuration(track.durationMs))
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DownloadIconButton(track = track, modifier = Modifier.size(40.dp))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.action_unfavorite else R.string.action_favorite_add,
                    ),
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.action_play),
                )
            }
        }
    }
}

@Composable
internal fun SearchSuggestionList(
    query: String,
    suggestions: List<String>,
    recommendations: List<Track>,
    favoriteIds: Set<String>,
    onSuggestionClick: (String) -> Unit,
    onSearchQuery: () -> Unit,
    onFillSuggestion: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (query.isNotBlank()) {
            item(key = "search_query_action") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSearchQuery)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.search_for_query, query),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            item(key = "search_suggestions_header") {
                Text(
                    text = stringResource(R.string.search_suggestions_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(items = suggestions, key = { "suggestion:$it" }) { term ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(term) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = term,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onFillSuggestion(term) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.NorthWest,
                            contentDescription = stringResource(R.string.search_fill_suggestion),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (recommendations.isNotEmpty()) {
            item(key = "search_recommendations_header") {
                Text(
                    text = stringResource(R.string.search_recommendations_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(items = recommendations, key = { "recommendation:${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isFavorite = track.id in favoriteIds,
                    onPlay = { onPlay(track) },
                    onPlayNext = { onPlayNext(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onShare = { onShare(track) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
