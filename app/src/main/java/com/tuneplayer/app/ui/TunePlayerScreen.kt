package com.tuneplayer.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.tuneplayer.app.R
import com.tuneplayer.app.data.lyrics.LrcParser
import com.tuneplayer.app.data.model.LibrarySort
import com.tuneplayer.app.data.model.Track
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunePlayerScreen(
    initialTab: Int = 0,
    viewModel: TunePlayerViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.loadLocalMusic()
    }

    LaunchedEffect(Unit) {
        if (hasAudioPermission) {
            viewModel.loadLocalMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showSleepDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = { showSleepDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Sleep timer",
                            tint = if (state.isSleepTimerActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = viewModel::cyclePlaybackSpeed) {
                        Text(
                            text = "%.2gx".format(state.playbackSpeed),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                state.currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = state.isPlaying,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onOpenLyrics = {
                            viewModel.loadLyrics()
                            showLyricsSheet = true
                        },
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_library)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_youtube)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_queue)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_liked)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when (selectedTab) {
                0 -> LibraryScreen(
                    tracks = viewModel.visibleLocalTracks,
                    favoriteIds = state.favoriteIds,
                    query = state.libraryQuery,
                    sort = state.librarySort,
                    sortDescending = state.librarySortDescending,
                    isLoading = state.isLoadingLocal,
                    hasPermission = hasAudioPermission,
                    onRequestPermission = { permissionLauncher.launch(permission) },
                    onQueryChange = viewModel::updateLibraryQuery,
                    onSortChange = viewModel::setLibrarySort,
                    onToggleSortDirection = viewModel::toggleLibrarySortDirection,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                )

                1 -> YouTubeScreen(
                    query = state.query,
                    results = state.searchResults,
                    favoriteIds = state.favoriteIds,
                    isSearching = state.isSearching,
                    onQueryChange = viewModel::updateQuery,
                    onSearch = viewModel::search,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                )

                2 -> QueueScreen(
                    queue = state.playbackQueue,
                    currentIndex = state.queueIndex,
                    onPlay = { index -> viewModel.playFromQueue(state.playbackQueue, index) },
                    onRemove = viewModel::removeFromQueue,
                    onMove = viewModel::moveInQueue,
                    onClear = viewModel::clearQueue,
                )

                3 -> FavoritesScreen(
                    tracks = state.localTracks.filter { it.id in state.favoriteIds } +
                        state.searchResults.filter { it.id in state.favoriteIds },
                    favoriteIds = state.favoriteIds,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                )

                4 -> SettingsScreen(
                    theme = state.theme,
                    equalizerEnabled = state.equalizerEnabled,
                    bassBoostLevel = state.bassBoostLevel,
                    virtualizerLevel = state.virtualizerLevel,
                    onThemeChange = viewModel::setTheme,
                    onEqualizerChange = viewModel::updateEqualizer,
                )
            }
        }
        if (showLyricsSheet) {
            LyricsSheet(
                track = state.currentTrack,
                lyrics = state.lyrics,
                isLoading = state.isLoadingLyrics,
                isPlaying = state.isPlaying,
                isShuffleEnabled = state.isShuffleEnabled,
                repeatMode = state.repeatMode,
                isFavorite = state.currentTrack?.let { it.id in state.favoriteIds } == true,
                syncedLyrics = state.syncedLyrics,
                activeLyricIndex = state.activeLyricIndex,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
                onTogglePlayPause = viewModel::togglePlayPause,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::toggleFavorite,
                onShare = { track -> shareTrack(context, track) },
                onDismiss = { showLyricsSheet = false },
            )
        }
        if (showSleepDialog) {
            SleepTimerDialog(
                currentMinutes = (state.sleepDurationMillis / 60_000).toInt(),
                isActive = state.isSleepTimerActive,
                onDismiss = { showSleepDialog = false },
                onSelect = { minutes ->
                    viewModel.setSleepDuration(minutes)
                    showSleepDialog = false
                },
                onCancelTimer = {
                    viewModel.cancelSleepTimer()
                    showSleepDialog = false
                },
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    query: String,
    sort: LibrarySort,
    sortDescending: Boolean,
    isLoading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.permission_needed))
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.action_grant_access))
                }
            }
        }
        return
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryFilterBar(
            query = query,
            sort = sort,
            sortDescending = sortDescending,
            onQueryChange = onQueryChange,
            onSortChange = onSortChange,
            onToggleSortDirection = onToggleSortDirection,
        )
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.library_empty))
            }
            return
        }
        TrackList(
            tracks = tracks,
            favoriteIds = favoriteIds,
            onPlay = onPlay,
            onPlayNext = onPlayNext,
            onToggleFavorite = onToggleFavorite,
            onShare = onShare,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilterBar(
    query: String,
    sort: LibrarySort,
    sortDescending: Boolean,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onToggleSortDirection: () -> Unit,
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
                        Icon(Icons.Default.Close, contentDescription = "Clear filter")
                    }
                }
            },
        )
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

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Title -> R.string.sort_by_title
    LibrarySort.Artist -> R.string.sort_by_artist
    LibrarySort.Album -> R.string.sort_by_album
    LibrarySort.Duration -> R.string.sort_by_duration
}

@Composable
private fun FavoritesScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.liked_empty), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.liked_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    TrackList(
        tracks = tracks,
        favoriteIds = favoriteIds,
        onPlay = onPlay,
        onPlayNext = onPlayNext,
        onToggleFavorite = onToggleFavorite,
        onShare = onShare,
    )
}

@Composable
private fun YouTubeScreen(
    query: String,
    results: List<Track>,
    favoriteIds: Set<String>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_empty))
            }
        } else {
            TrackList(
                tracks = results,
                favoriteIds = favoriteIds,
                onPlay = onPlay,
                onPlayNext = onPlayNext,
                onToggleFavorite = onToggleFavorite,
                onShare = onShare,
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
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

@Composable
private fun TrackRow(
    track: Track,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track = track, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.durationMs > 0) {
                        append(" · ")
                        append(formatDuration(track.durationMs))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onPlayNext) {
            Icon(Icons.Default.PlaylistAdd, contentDescription = stringResource(R.string.action_play_next))
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
        }
    }
}

@Composable
private fun Artwork(track: Track, size: Int) {
    val modifier = Modifier
        .size(size.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (track.artworkUri != null) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, contentDescription = null)
        }
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track = track, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenLyrics),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun SleepTimerDialog(
    currentMinutes: Int,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onCancelTimer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column {
                Text(
                    text = if (isActive) {
                        stringResource(R.string.sleep_timer_active, currentMinutes)
                    } else {
                        stringResource(R.string.sleep_timer_prompt)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SLEEP_MINUTE_OPTIONS.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$minutes min", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        if (isActive && currentMinutes == minutes) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (isActive) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.action_cancel_timer),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCancelTimer)
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

private val SLEEP_MINUTE_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90)

@Composable
private fun QueueScreen(
    queue: List<Track>,
    currentIndex: Int,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.queue_empty), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.queue_empty_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Queue (${queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_clear))
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(items = queue, key = { _, track -> track.id }) { index, track ->
                        QueueRow(
                            track = track,
                            isPlaying = index == currentIndex,
                            onPlay = { onPlay(index) },
                            onRemove = { onRemove(index) },
                            onMoveUp = { if (index > 0) onMove(index, index - 1) },
                            onMoveDown = { if (index < queue.lastIndex) onMove(index, index + 1) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    track: Track,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track = track, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clickable(onClick = onPlay)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.durationMs > 0) {
                        append(" · ")
                        append(formatDuration(track.durationMs))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = stringResource(R.string.now_playing),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column {
            IconButton(onClick = onMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
            }
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove))
        }
    }
}

/** Opens the system share sheet with a search link for the track. */
private fun shareTrack(context: android.content.Context, track: Track) {
    val body = if (track.isLocal) {
        "${track.title} — ${track.artist}"
    } else {
        "${track.title} — ${track.artist}\nhttps://music.youtube.com/watch?v=${track.videoId.orEmpty()}"
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, track.title)
        putExtra(android.content.Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        android.content.Intent.createChooser(send, "Share track")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(
    track: Track?,
    lyrics: String?,
    isLoading: Boolean,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    syncedLyrics: List<LrcParser.LrcLine>,
    activeLyricIndex: Int,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (track == null) {
                Text(stringResource(R.string.lyrics_nothing), style = MaterialTheme.typography.titleMedium)
                return@Column
            }
            Artwork(track = track, size = 120)
            Spacer(Modifier.height(16.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            SeekBarRow(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.action_shuffle),
                        tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = stringResource(R.string.action_repeat),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onToggleFavorite(track) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.action_favorite),
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onShare(track) }) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                isLoading -> CircularProgressIndicator()
                syncedLyrics.isNotEmpty() -> SyncedLyrics(
                    lines = syncedLyrics,
                    activeIndex = activeLyricIndex,
                )
                lyrics != null -> Text(
                    text = lyrics,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
                track.isLocal -> Text(
                    text = stringResource(R.string.lyrics_none_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    text = stringResource(R.string.lyrics_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Seek bar with elapsed / total labels.
 *
 * While dragging, [Slider] owns the value so the thumb does not jump back
 * under the 250 ms position poll.
 */
@Composable
private fun SeekBarRow(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val max = durationMs.coerceAtLeast(1L).toFloat()

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (dragging) dragValue else positionMs.coerceIn(0L, durationMs).toFloat(),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..max,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (dragging) dragValue.toLong() else positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Highlights the line for the current playback position and keeps it in view. */@Composable
private fun SyncedLyrics(
    lines: List<LrcParser.LrcLine>,
    activeIndex: Int,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices) {
            listState.animateScrollToItem(activeIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 360.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                color = if (index == activeIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }
    }
}
