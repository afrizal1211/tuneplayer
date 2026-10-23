package com.tuneplayer.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
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
import com.tuneplayer.app.data.prefs.PlaylistStore
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
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    // Tabs: 0 Library, 1 YouTube, 2 Queue, 3 Liked, 4 History, 5 Playlists, 6 Settings
    val keepScreenOn = state.keepScreenOn
    val activity = context as? android.app.Activity
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val tabTitle = when (selectedTab) {
                        0 -> R.string.tab_library
                        1 -> R.string.tab_youtube
                        2 -> R.string.tab_queue
                        3 -> R.string.tab_liked
                        4 -> R.string.tab_history
                        5 -> R.string.tab_playlists
                        6 -> R.string.tab_settings
                        else -> R.string.tab_stats
                    }
                    Text(
                        text = stringResource(tabTitle),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = { showSleepDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.sleep_timer),
                            tint = if (state.isSleepTimerActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Box {
                        TextButton(onClick = { speedMenuOpen = true }) {
                            Text(
                                text = formatSpeed(state.playbackSpeed),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        DropdownMenu(
                            expanded = speedMenuOpen,
                            onDismissRequest = { speedMenuOpen = false },
                        ) {
                            PLAYBACK_SPEEDS.forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text(formatSpeed(speed)) },
                                    onClick = {
                                        viewModel.setPlaybackSpeed(speed)
                                        speedMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (kotlin.math.abs(state.playbackSpeed - speed) < 0.01f) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
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
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipNext = viewModel::skipToNext,
                        onOpenPlayer = { showPlayer = true },
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
                        selected = selectedTab >= 4,
                        onClick = { showMore = true },
                        icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_more)) },
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
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::clearError) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(180),
                modifier = Modifier.weight(1f),
                label = "tab-content",
            ) { tab ->
                when (tab) {
                0 -> LibraryScreen(
                    tracks = viewModel.visibleLocalTracks,
                    favoriteIds = state.favoriteIds,
                    query = state.libraryQuery,
                    sort = state.librarySort,
                    sortDescending = state.librarySortDescending,
                    gridLayout = state.gridLayout,
                    isLoading = state.isLoadingLocal,
                    hasPermission = hasAudioPermission,
                    onRequestPermission = { permissionLauncher.launch(permission) },
                    onQueryChange = viewModel::updateLibraryQuery,
                    onSortChange = viewModel::setLibrarySort,
                    onToggleSortDirection = viewModel::toggleLibrarySortDirection,
                    onToggleLayout = viewModel::toggleGridLayout,
                    onRefresh = viewModel::loadLocalMusic,
                    onShuffleAll = viewModel::shuffleAll,
                    onPlayAll = viewModel::playAll,
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
                    searchHistory = state.searchHistory,
                    onClearSearchHistory = viewModel::clearSearchHistory,
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

                3 -> {
                    val favoriteTracks = state.localTracks.filter { it.id in state.favoriteIds } +
                        state.searchResults.filter { it.id in state.favoriteIds }
                    FavoritesScreen(
                        tracks = favoriteTracks,
                        favoriteIds = state.favoriteIds,
                        onPlay = viewModel::play,
                        onPlayNext = viewModel::playNext,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onShare = { track -> shareTrack(context, track) },
                        onPlayAll = { viewModel.playTracks(favoriteTracks) },
                        onShuffleAll = { viewModel.shuffleTracks(favoriteTracks) },
                    )
                }

                4 -> HistoryScreen(
                    tracks = state.historyTracks,
                    favoriteIds = state.favoriteIds,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                    onPlayAll = { viewModel.playTracks(state.historyTracks) },
                    onShuffleAll = { viewModel.shuffleTracks(state.historyTracks) },
                )

                5 -> PlaylistsScreen(
                    playlists = state.playlists,
                    tracksFor = viewModel::tracksForPlaylist,
                    favoriteIds = state.favoriteIds,
                    onCreate = { name -> viewModel.createPlaylist(name) },
                    onDelete = viewModel::deletePlaylist,
                    onRename = { id, name -> viewModel.renamePlaylist(id, name) },
                    onAddQueue = viewModel::addQueueToPlaylist,
                    onRemoveTrack = { id, trackId -> viewModel.removeFromPlaylist(id, trackId) },
                    onPlay = viewModel::playPlaylist,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                )

                6 -> SettingsScreen(
                    theme = state.theme,
                    equalizerEnabled = state.equalizerEnabled,
                    bassBoostLevel = state.bassBoostLevel,
                    virtualizerLevel = state.virtualizerLevel,
                    keepScreenOn = state.keepScreenOn,
                    hapticsEnabled = state.hapticsEnabled,
                    onThemeChange = viewModel::setTheme,
                    onEqualizerChange = viewModel::updateEqualizer,
                    onKeepScreenOnChange = viewModel::setKeepScreenOn,
                    onHapticsChange = viewModel::setHapticsEnabled,
                    dynamicColor = state.dynamicColor,
                    pureBlack = state.pureBlack,
                    onDynamicColorChange = viewModel::setDynamicColor,
                    onPureBlackChange = viewModel::setPureBlack,
                    eqBands = state.eqBands,
                    eqBandFreqs = state.eqBandFreqs,
                    eqBandRange = state.eqBandRange,
                    eqPreset = state.eqPreset,
                    onEqBandChange = viewModel::setEqBand,
                    onEqPresetChange = viewModel::setEqPreset,
                    onReloadFromStores = viewModel::reloadFromStores,
                )

                7 -> StatsScreen(
                    totalPlays = viewModel.totalPlays(),
                    distinctTracks = viewModel.distinctTracks(),
                    topTracks = viewModel.topTracks(),
                    favoriteIds = state.favoriteIds,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                )
                }
            }
        }
        if (showMore) {
            ModalBottomSheet(onDismissRequest = { showMore = false }) {
                MoreSheetItem(
                    icon = Icons.Default.History,
                    label = stringResource(R.string.tab_history),
                ) {
                    selectedTab = 4
                    showMore = false
                }
                MoreSheetItem(
                    icon = Icons.Default.PlaylistPlay,
                    label = stringResource(R.string.tab_playlists),
                ) {
                    selectedTab = 5
                    showMore = false
                }
                MoreSheetItem(
                    icon = Icons.Default.BarChart,
                    label = stringResource(R.string.tab_stats),
                ) {
                    selectedTab = 7
                    showMore = false
                }
                MoreSheetItem(
                    icon = Icons.Default.Settings,
                    label = stringResource(R.string.tab_settings),
                ) {
                    selectedTab = 6
                    showMore = false
                }
            }
        }
        val playingTrack = state.currentTrack
        if (showPlayer && playingTrack != null) {
            NowPlayingSheet(
                track = playingTrack,
                isPlaying = state.isPlaying,
                isShuffleEnabled = state.isShuffleEnabled,
                repeatMode = state.repeatMode,
                isFavorite = playingTrack.id in state.favoriteIds,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipNext = viewModel::skipToNext,
                onSkipPrevious = viewModel::skipToPrevious,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::toggleFavorite,
                onShare = { track -> shareTrack(context, track) },
                onOpenLyrics = {
                    viewModel.loadLyrics()
                    showPlayer = false
                    showLyricsSheet = true
                },
                onDismiss = { showPlayer = false },
            )
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
                finishTrackArmed = state.sleepFinishTrack,
                onDismiss = { showSleepDialog = false },
                onSelect = { minutes ->
                    viewModel.setSleepDuration(minutes)
                    showSleepDialog = false
                },
                onFinishTrack = {
                    viewModel.setSleepFinishTrack()
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
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onShuffleAll,
                label = { Text(stringResource(R.string.action_shuffle_all)) },
                leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
            )
            AssistChip(
                onClick = onPlayAll,
                label = { Text(stringResource(R.string.action_play_all)) },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryGrouping.entries.forEachIndexed { index, option ->
                FilterChip(
                    selected = groupingIndex == index,
                    onClick = { groupingIndex = index },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.library_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.library_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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

private fun LibraryGrouping.labelRes(): Int = when (this) {
    LibraryGrouping.Songs -> R.string.library_group_songs
    LibraryGrouping.Albums -> R.string.library_group_albums
    LibraryGrouping.Artists -> R.string.library_group_artists
}

private fun Track.groupKey(grouping: LibraryGrouping, unknown: String): String = when (grouping) {
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
private fun GroupedTrackList(
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
        modifier = Modifier.fillMaxSize(),
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
private fun LibraryFilterBar(
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
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
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
    Column(modifier = Modifier.fillMaxSize()) {
        BulkActionRow(onPlayAll = onPlayAll, onShuffleAll = onShuffleAll)
        Box(modifier = Modifier.weight(1f)) {
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
}

/** Small play/shuffle pair used above list screens that have no toolbar. */
@Composable
private fun BulkActionRow(
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onShuffleAll,
            label = { Text(stringResource(R.string.action_shuffle_all)) },
            leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
        )
        AssistChip(
            onClick = onPlayAll,
            label = { Text(stringResource(R.string.action_play_all)) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouTubeScreen(
    query: String,
    results: List<Track>,
    favoriteIds: Set<String>,
    isSearching: Boolean,
    searchHistory: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearchHistory: () -> Unit,
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
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        if (searchHistory.isNotEmpty() && query.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.search_history_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    grid: Boolean = false,
) {
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(items = tracks, key = { it.id }) { track ->
                GridTrackCell(
                    track = track,
                    isFavorite = track.id in favoriteIds,
                    onPlay = { onPlay(track) },
                )
            }
        }
        return
    }
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

/** Grid cell: artwork, title, artist. */
@Composable
private fun GridTrackCell(
    track: Track,
    isFavorite: Boolean,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(4.dp),
    ) {
        Artwork(track = track, size = 160)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    trailing: (@Composable () -> Unit)? = null,
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
                contentDescription = stringResource(
                    if (isFavorite) R.string.action_unfavorite else R.string.action_favorite_add,
                ),
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
        trailing?.invoke()
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
    positionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(tonalElevation = 3.dp) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.clickable(onClick = onOpenPlayer)) {
                    Artwork(track = track, size = 44)
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenPlayer),
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
                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.action_skip_next),
                    )
                }
            }
        }
    }
}

/** 1x, 1.25x, 1.5x: no trailing zeros, no rounding surprises. */
private fun formatSpeed(speed: Float): String {
    val number = if (speed == speed.toInt().toFloat()) {
        "%.0f".format(speed)
    } else {
        "%.2f".format(speed).trimEnd('0').trimEnd('.')
    }
    return "${number}x"
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
    finishTrackArmed: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onFinishTrack: () -> Unit,
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
                        Text(stringResource(R.string.sleep_minutes, minutes), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        if (isActive && currentMinutes == minutes) {
                            Text(
                                text = stringResource(R.string.state_active),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onFinishTrack)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sleep_finish_track),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    if (finishTrackArmed) {
                        Text(
                            text = stringResource(R.string.state_active),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
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

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
private fun QueueScreen(
    queue: List<Track>,
    currentIndex: Int,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.queue_empty), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.queue_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val totalMs = queue.sumOf { it.durationMs }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.queue_title_count, queue.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (totalMs > 0L) {
                    Text(
                        text = stringResource(R.string.queue_total_duration, formatDuration(totalMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { confirmClear = true }) {
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
                    canMoveUp = index > 0,
                    canMoveDown = index < queue.lastIndex,
                    onPlay = { onPlay(index) },
                    onRemove = { onRemove(index) },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.action_clear)) },
            text = { Text(stringResource(R.string.queue_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun QueueRow(
    track: Track,
    isPlaying: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPlaying) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track = track, size = 48)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPlay),
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
            Spacer(Modifier.width(8.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.tab_more),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_move_up)) },
                    enabled = canMoveUp,
                    onClick = {
                        menuOpen = false
                        onMoveUp()
                    },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_move_down)) },
                    enabled = canMoveDown,
                    onClick = {
                        menuOpen = false
                        onMoveDown()
                    },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_remove)) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                )
            }
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
        android.content.Intent.createChooser(send, context.getString(R.string.action_share))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingSheet(
    track: Track,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onOpenLyrics: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(track = track, size = 240)
            Spacer(Modifier.height(20.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            SeekBarRow(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.action_shuffle),
                        tint = if (isShuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.action_skip_previous),
                        modifier = Modifier.size(32.dp),
                    )
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.action_skip_next),
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = stringResource(R.string.action_repeat),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onToggleFavorite(track) }) {
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
                    )
                }
                IconButton(onClick = onOpenLyrics) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = stringResource(R.string.action_lyrics),
                    )
                }
                IconButton(onClick = { onShare(track) }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.action_share),
                    )
                }
            }
        }
    }
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

/**
 * Recently played. Track ids come from [com.tuneplayer.app.data.prefs.FavoriteStore]
 * history and are resolved against whatever library metadata is loaded.
 *
 * Ids that no longer resolve (deleted file, stale YouTube entry) are dropped
 * rather than shown as broken rows.
 */
@Composable
private fun HistoryScreen(
    tracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.history_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BulkActionRow(onPlayAll = onPlayAll, onShuffleAll = onShuffleAll)
        Box(modifier = Modifier.weight(1f)) {
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
}

@Composable
private fun PlaylistsScreen(
    playlists: List<PlaylistStore.Playlist>,
    tracksFor: (PlaylistStore.Playlist) -> List<Track>,
    favoriteIds: Set<String>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onAddQueue: (String) -> Unit,
    onRemoveTrack: (String, String) -> Unit,
    onPlay: (PlaylistStore.Playlist, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    var openId by remember { mutableStateOf<String?>(null) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tab_playlists),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showCreate = true }) {
                Text(stringResource(R.string.action_create_playlist))
            }
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.playlists_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.playlists_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = playlists, key = { it.id }) { playlist ->
                val count = tracksFor(playlist).size
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = {
                        Text(stringResource(R.string.playlist_track_count, count))
                    },
                    leadingContent = {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onAddQueue(playlist.id) }) {
                                Icon(
                                    Icons.Default.PlaylistAdd,
                                    contentDescription = stringResource(R.string.action_add_to_playlist),
                                )
                            }
                            IconButton(onClick = { onPlay(playlist, 0) }) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.action_play),
                                )
                            }
                            IconButton(onClick = { onDelete(playlist.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete_playlist),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { openId = playlist.id },
                )
                HorizontalDivider()
            }
        }
    }

    if (showCreate) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_create_playlist),
            initialName = "",
            onConfirm = { name ->
                onCreate(name)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    val open = playlists.firstOrNull { it.id == openId }
    if (open != null) {
        val tracks = tracksFor(open)
        AlertDialog(
            onDismissRequest = { openId = null },
            title = { Text(open.name) },
            text = {
                if (tracks.isEmpty()) {
                    Text(stringResource(R.string.playlist_empty))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(items = tracks, key = { _, t -> t.id }) { index, track ->
                            TrackRow(
                                track = track,
                                isFavorite = track.id in favoriteIds,
                                onPlay = { onPlay(open, index) },
                                onPlayNext = { onPlayNext(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onShare = { onShare(track) },
                                trailing = {
                                    IconButton(onClick = { onRemoveTrack(open.id, track.id) }) {
                                        Icon(
                                            Icons.Default.RemoveCircleOutline,
                                            contentDescription = stringResource(
                                                R.string.action_remove_from_playlist,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { openId = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameId = open.id; openId = null }) {
                    Text(stringResource(R.string.action_rename_playlist))
                }
            },
        )
    }

    val renaming = playlists.firstOrNull { it.id == renameId }
    if (renaming != null) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_rename_playlist),
            confirmLabel = stringResource(R.string.action_rename_playlist),
            initialName = renaming.name,
            onConfirm = { name ->
                onRename(renaming.id, name)
                renameId = null
                openId = null
            },
            onDismiss = { renameId = null },
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel ?: stringResource(R.string.action_create_playlist)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun MoreSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Listening stats.
 *
 * Counts come from [com.tuneplayer.app.data.prefs.PlayStatsStore], resolved against
 * the loaded library, so a track deleted from disk drops out of the top list
 * instead of showing a dead row. The raw counts stay; only the display is filtered.
 */
@Composable
private fun StatsScreen(
    totalPlays: Int,
    distinctTracks: Int,
    topTracks: List<Track>,
    favoriteIds: Set<String>,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onShare: (Track) -> Unit,
) {
    if (totalPlays == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.stats_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatCell(stringResource(R.string.stats_total_plays), totalPlays)
            StatCell(stringResource(R.string.stats_distinct_tracks), distinctTracks)
        }
        Text(
            text = stringResource(R.string.stats_top_tracks),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (topTracks.isEmpty()) {
            // Counts exist but nothing resolves: the files moved or the library
            // has not been scanned yet.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.stats_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TrackList(
                tracks = topTracks,
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
private fun StatCell(label: String, value: Int) {
    Column {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
