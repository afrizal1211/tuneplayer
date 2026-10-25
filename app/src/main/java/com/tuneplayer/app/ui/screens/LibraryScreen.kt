package com.tuneplayer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuneplayer.app.R
import com.tuneplayer.app.data.lyrics.LyricsSource
import com.tuneplayer.app.data.model.LibrarySort
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.fadingEdge
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.TrackList
import com.tuneplayer.app.ui.screens.LibraryScreen

internal fun LibraryScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    query: String,
    sort: LibrarySort,
    sortDescending: Boolean,
    gridLayout: Boolean,
    isLoading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlayAll: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    if (!hasPermission) {
        EmptyState(
            icon = Icons.Default.LibraryMusic,
            title = stringResource(R.string.permission_needed),
            action = {
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.action_grant_access))
                }
            },
        )
        return
    }

    if (isLoading && tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var groupingIndex by rememberSaveable { mutableIntStateOf(0) }
    val grouping = LibraryGrouping.entries[groupingIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryFilterBar(
            query = query,
            sort = sort,
            sortDescending = sortDescending,
            onQueryChange = onQueryChange,
            onSortChange = onSortChange,
            onToggleSortDirection = onToggleSortDirection,
            onToggleLayout = onToggleLayout,
            isLoading = isLoading,
            onRefresh = onRefresh,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AssistChip(
                onClick = onShuffleAll,
                label = {
                    Text(
                        stringResource(R.string.action_shuffle_all),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            AssistChip(
                onClick = onPlayAll,
                label = {
                    Text(
                        stringResource(R.string.action_play_all),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibraryGrouping.entries.forEachIndexed { index, option ->
                FilterChip(
                    selected = groupingIndex == index,
                    onClick = { groupingIndex = index },
                    label = {
                        Text(
                            stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        if (tracks.isEmpty()) {
            val hasQuery = query.isNotBlank()
            EmptyState(
                icon = if (hasQuery) Icons.Default.Search else Icons.Default.LibraryMusic,
                title = stringResource(
                    if (hasQuery) R.string.search_empty else R.string.library_empty,
                ),
                hint = stringResource(
                    if (hasQuery) R.string.search_empty_hint else R.string.library_empty_hint,
                ),
            )
            return
        }
        if (grouping == LibraryGrouping.Songs) {
            TrackList(
                tracks = tracks,
                favoriteIds = favoriteIds,
                onPlay = onPlay,
                onPlayNext = onPlayNext,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
                grid = gridLayout,
            )
        } else {
            GroupedTrackList(
                tracks = tracks,
                grouping = grouping,
                favoriteIds = favoriteIds,
                onPlay = onPlay,
                onPlayNext = onPlayNext,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
            )
        }
    }
}

private enum class LibraryGrouping { Songs, Albums, Artists }

internal fun LibraryGrouping.labelRes(): Int = when (this) {
    LibraryGrouping.Songs -> R.string.library_group_songs
    LibraryGrouping.Albums -> R.string.library_group_albums
    LibraryGrouping.Artists -> R.string.library_group_artists
}

internal fun Track.groupKey(grouping: LibraryGrouping, unknown: String): String = when (grouping) {
    LibraryGrouping.Albums -> album?.takeIf { it.isNotBlank() } ?: unknown
    LibraryGrouping.Artists -> artist.takeIf { it.isNotBlank() } ?: unknown
    LibraryGrouping.Songs -> id
}

/**
 * Albums/artists view: one header per group, tracks beneath.
 *
 * Groups are sorted by name rather than by the library sort so the section
 * order stays stable while the tracks inside each section keep that sort.
 */
@Composable
internal fun GroupedTrackList(
    tracks: List<Track>,
    grouping: LibraryGrouping,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    val unknown = stringResource(
        if (grouping == LibraryGrouping.Albums) {
            R.string.library_unknown_album
        } else {
            R.string.library_unknown_artist
        },
    )
    val groups = remember(tracks, grouping, unknown) {
        tracks.groupBy { it.groupKey(grouping, unknown) }
            .toList()
            .sortedBy { (name, _) -> name.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().fadingEdge(bottom = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        groups.forEach { (name, groupTracks) ->
            item(key = "header:$grouping:$name") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.library_group_count, groupTracks.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = groupTracks, key = { it.id }) { track ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterBar(
    query: String,
    sort: LibrarySort,
    sortDescending: Boolean,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleLayout: () -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.library_filter_hint)) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_filter))
                    }
                }
            },
        )
        IconButton(onClick = onToggleLayout) {
            Icon(
                Icons.Default.GridView,
                contentDescription = stringResource(R.string.action_toggle_layout),
            )
        }
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.library_refresh),
                )
            }
        }
        Box {
            IconButton(onClick = { sortMenuOpen = true }) {
                Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort_by_title))
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                LibrarySort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            onSortChange(option)
                            sortMenuOpen = false
                        },
                        trailingIcon = {
                            if (option == sort) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (sortDescending) {
                                    R.string.sort_descending
                                } else {
                                    R.string.sort_ascending
                                },
                            ),
                        )
                    },
                    onClick = {
                        onToggleSortDirection()
                        sortMenuOpen = false
                    },
                )
            }
        }
    }
}

internal fun LyricsSource.labelRes(): Int = when (this) {
    LyricsSource.YouTube -> R.string.lyrics_source_youtube
    LyricsSource.Lrclib -> R.string.lyrics_source_lrclib
    LyricsSource.Ovh -> R.string.lyrics_source_ovh
    LyricsSource.KuGou -> R.string.lyrics_source_kugou
}

internal fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Title -> R.string.sort_by_title
    LibrarySort.Artist -> R.string.sort_by_artist
    LibrarySort.Album -> R.string.sort_by_album
    LibrarySort.Duration -> R.string.sort_by_duration
}

@Composable
