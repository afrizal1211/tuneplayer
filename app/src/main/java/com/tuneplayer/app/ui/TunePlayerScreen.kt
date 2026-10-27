package com.tuneplayer.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuneplayer.app.R
import com.tuneplayer.app.ui.viewmodel.*
import com.tuneplayer.app.ui.components.Artwork
import com.tuneplayer.app.ui.components.EmptyState
import com.tuneplayer.app.ui.components.LyricsSheet
import com.tuneplayer.app.ui.components.MiniPlayer
import com.tuneplayer.app.ui.components.NowPlayingSheet
import com.tuneplayer.app.ui.components.SleepTimerDialog
import com.tuneplayer.app.ui.components.TrackList
import com.tuneplayer.app.ui.screens.BrowseScreen
import com.tuneplayer.app.ui.screens.DownloadsScreen
import com.tuneplayer.app.ui.Screens
import com.tuneplayer.app.ui.screens.FavoritesScreen
import com.tuneplayer.app.ui.screens.HistoryScreen
import com.tuneplayer.app.ui.screens.LibraryScreen
import com.tuneplayer.app.ui.screens.MoreSheetItem
import com.tuneplayer.app.ui.screens.PlaylistsScreen
import com.tuneplayer.app.ui.screens.QueueScreen
import com.tuneplayer.app.ui.screens.StatsScreen
import com.tuneplayer.app.ui.screens.YouTubeScreen
import com.tuneplayer.app.ui.util.LocalDownloadTrack
import com.tuneplayer.app.ui.util.LocalDownloadUiState
import com.tuneplayer.app.ui.util.LocalHapticsEnabled
import com.tuneplayer.app.ui.util.LocalNowPlayingId
import com.tuneplayer.app.ui.util.LocalRemoveDownload
import com.tuneplayer.app.ui.util.DownloadUiState
import com.tuneplayer.app.ui.util.shareTrack
import com.tuneplayer.app.ui.util.formatDuration
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.tuneplayer.app.ui.util.PLAYBACK_SPEEDS
import com.tuneplayer.app.ui.util.formatSpeed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunePlayerScreen(
    initialRoute: String = Screens.Library.route,
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

    // Route-based navigation matching ArchiveTune's NavHost pattern.
    // Back stack replaces the old comma-joined int CSV.
    var backStackRoutes by rememberSaveable { mutableStateOf(listOf(initialRoute)) }
    val currentRoute = backStackRoutes.lastOrNull() ?: Screens.Library.route
    val navigateTo: (String) -> Unit = { route ->
        if (route != currentRoute) backStackRoutes = backStackRoutes + route
    }
    BackHandler(enabled = backStackRoutes.size > 1) {
        backStackRoutes = backStackRoutes.dropLast(1)
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screens.Browse.route) viewModel.loadHomeRecommendations()
    }
    var showSleepDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSheet by rememberSaveable { mutableStateOf(false) }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }

    // Tabs: library, browse, search, queue, liked
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

    CompositionLocalProvider(
        LocalHapticsEnabled provides state.hapticsEnabled,
        LocalNowPlayingId provides state.currentTrack?.id,
        LocalDownloadUiState provides DownloadUiState(
            downloadedIds = state.downloadedTracks.map { it.id }.toSet(),
            progress = state.downloadProgress,
        ),
        LocalDownloadTrack provides viewModel::downloadTrack,
        LocalRemoveDownload provides viewModel::removeDownload,
    ) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val currentScreen = Screens.byRoute(currentRoute) ?: Screens.Library
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(currentScreen.titleId),
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
                scrollBehavior = scrollBehavior,
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
                    @Composable
                    fun navItem(
                        screen: Screens,
                        labelRes: Int,
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = { navigateTo(screen.route) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == screen.route) {
                                        screen.selectedIcon
                                    } else {
                                        screen.unselectedIcon
                                    },
                                    contentDescription = stringResource(labelRes),
                                )
                            },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                    navItem(Screens.Library, R.string.tab_library)
                    navItem(Screens.Browse, R.string.tab_browse)
                    navItem(Screens.Search, R.string.tab_youtube)
                    navItem(Screens.Queue, R.string.tab_queue)
                    navItem(Screens.Liked, R.string.tab_liked)
                    NavigationBarItem(
                        selected = false,
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

            // Scroll state preserved per route, same as the old Crossfade layer above.
            val libraryListState = rememberLazyListState()
            val queueListState = rememberLazyListState()
            val historyListState = rememberLazyListState()
            val playlistsListState = rememberLazyListState()
            val downloadsListState = rememberLazyListState()

            Box(modifier = Modifier.weight(1f)) {
                when (currentRoute) {
                    Screens.Library.route -> LibraryScreen(
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
                        listState = libraryListState,
                        onPlay = viewModel::play,
                        onPlayNext = viewModel::playNext,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onShare = { track -> shareTrack(context, track) },
                    )

                    Screens.Browse.route -> BrowseScreen(
                        shelves = state.homeShelves,
                        isLoading = state.isLoadingHomeRecommendations,
                        favoriteIds = state.favoriteIds,
                        onPlay = viewModel::play,
                        onPlayNext = viewModel::playNext,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onShare = { track -> shareTrack(context, track) },
                        onPlayShelf = viewModel::shuffleTracks,
                    )

                    Screens.Search.route -> YouTubeScreen(
                    query = state.query,
                    results = state.searchResults,
                    topResult = state.searchTopResult,
                    searchFilter = state.searchFilter,
                    searchCommitted = state.searchCommitted,
                    favoriteIds = state.favoriteIds,
                    isSearching = state.isSearching,
                    suggestions = state.searchSuggestions,
                    recommendations = state.searchRecommendations,
                    isLoadingSuggestions = state.isLoadingSuggestions,
                    homeRecommendations = state.homeRecommendations,
                    isLoadingHomeRecommendations = state.isLoadingHomeRecommendations,
                    onQueryChange = viewModel::updateQuery,
                    onSearch = viewModel::search,
                    onFilterChange = viewModel::setSearchFilter,
                    searchHistory = state.searchHistory,
                    onClearSearchHistory = viewModel::clearSearchHistory,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                )

                Screens.Queue.route -> QueueScreen(
                    queue = state.playbackQueue,
                    currentIndex = state.queueIndex,
                    onPlay = { index -> viewModel.playFromQueue(state.playbackQueue, index) },
                    onRemove = viewModel::removeFromQueue,
                    onMove = viewModel::moveInQueue,
                    onClear = viewModel::clearQueue,
                    listState = queueListState,
                )

                Screens.Liked.route -> {
                    val favoriteTracks = state.localTracks.filter { it.id in state.favoriteIds } +
                        state.searchResults.filter { it.id in state.favoriteIds } +
                        state.downloadedTracks.filter { it.id in state.favoriteIds }
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

                Screens.History.route -> HistoryScreen(
                    tracks = state.historyTracks,
                    favoriteIds = state.favoriteIds,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                    onPlayAll = { viewModel.playTracks(state.historyTracks) },
                    onShuffleAll = { viewModel.shuffleTracks(state.historyTracks) },
                    listState = historyListState,
                )

                Screens.Playlists.route -> PlaylistsScreen(
                    playlists = state.playlists,
                    tracksFor = viewModel::tracksForPlaylist,
                    favoriteIds = state.favoriteIds,
                    onCreate = { name -> viewModel.createPlaylist(name) },
                    onDelete = viewModel::deletePlaylist,
                    onRename = { id, name -> viewModel.renamePlaylist(id, name) },
                    onAddQueue = viewModel::addQueueToPlaylist,
                    onRemoveTrack = { id, trackId -> viewModel.removeFromPlaylist(id, trackId) },
                    onReorderTrack = viewModel::moveInPlaylist,
                    onPlay = viewModel::playPlaylist,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                    listState = playlistsListState,
                )

                Screens.Settings.route -> SettingsScreen(
                    theme = state.theme,
                    equalizerEnabled = state.equalizerEnabled,
                    bassBoostLevel = state.bassBoostLevel,
                    virtualizerLevel = state.virtualizerLevel,
                    skipSilence = state.skipSilence,
                    crossfadeEnabled = state.crossfadeEnabled,
                    crossfadeDurationSeconds = state.crossfadeDurationSeconds,
                    automixEnabled = state.automixEnabled,
                    keepScreenOn = state.keepScreenOn,
                    hapticsEnabled = state.hapticsEnabled,
                    onThemeChange = viewModel::setTheme,
                    onEqualizerChange = viewModel::updateEqualizer,
                    onSkipSilenceChange = viewModel::setSkipSilence,
                    onCrossfadeChange = viewModel::setCrossfade,
                    onAutomixChange = viewModel::setAutomixEnabled,
                    onKeepScreenOnChange = viewModel::setKeepScreenOn,
                    onHapticsChange = viewModel::setHapticsEnabled,
                    dynamicColor = state.dynamicColor,
                    pureBlack = state.pureBlack,
                    accent = state.accent,
                    onDynamicColorChange = viewModel::setDynamicColor,
                    onPureBlackChange = viewModel::setPureBlack,
                    onAccentChange = viewModel::setAccent,
                    eqBands = state.eqBands,
                    eqBandFreqs = state.eqBandFreqs,
                    eqBandRange = state.eqBandRange,
                    eqPreset = state.eqPreset,
                    onEqBandChange = viewModel::setEqBand,
                    onEqPresetChange = viewModel::setEqPreset,
                    youtubeSignedIn = state.youtubeSignedIn,
                    youtubeAudioQuality = state.youtubeAudioQuality,
                    onYouTubeLogin = viewModel::connectYouTubeAccount,
                    onYouTubeLogout = viewModel::disconnectYouTubeAccount,
                    onYouTubeAudioQualityChange = viewModel::setYouTubeAudioQuality,
                    onReloadFromStores = viewModel::reloadFromStores,
                    discordRpcEnabled = state.discordRpcEnabled,
                    discordTokenSaved = state.discordTokenSaved,
                    discordConnected = state.discordConnected,
                    discordShowWhenPaused = state.discordShowWhenPaused,
                    onDiscordRpcChange = viewModel::setDiscordRpcEnabled,
                    onDiscordShowWhenPausedChange = viewModel::setDiscordShowWhenPaused,
                     onDiscordTokenSave = viewModel::saveDiscordToken,
                     onDiscordTokenRemove = viewModel::removeDiscordToken,
                     discordApplicationId = state.discordApplicationId,
                     onDiscordApplicationIdSave = viewModel::saveDiscordApplicationId,
                     onDiscordApplicationIdRemove = viewModel::removeDiscordApplicationId,
                )

                Screens.Stats.route -> StatsScreen(
                    totalPlays = viewModel.totalPlays(),
                    distinctTracks = viewModel.distinctTracks(),
                    topTracks = viewModel.topTracks(),
                    favoriteIds = state.favoriteIds,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                )

                Screens.Downloads.route -> DownloadsScreen(
                    tracks = state.downloadedTracks,
                    totalBytes = state.downloadTotalBytes,
                    favoriteIds = state.favoriteIds,
                    onPlay = viewModel::play,
                    onPlayNext = viewModel::playNext,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onShare = { track -> shareTrack(context, track) },
                    onPlayAll = { viewModel.playTracks(state.downloadedTracks) },
                    onShuffleAll = { viewModel.shuffleTracks(state.downloadedTracks) },
                    onClearAll = viewModel::clearDownloads,
                    listState = downloadsListState,
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
                    navigateTo(Screens.History.route)
                    showMore = false
                }
                MoreSheetItem(
                    icon = Icons.Default.PlaylistPlay,
                    label = stringResource(R.string.tab_playlists),
                ) {
                    navigateTo(Screens.Playlists.route)
                    showMore = false
                }
                MoreSheetItem(
                    icon = Icons.Default.Download,
                    label = stringResource(R.string.tab_downloads),
                ) {
                    navigateTo(Screens.Downloads.route)
                    showMore = false
                }
                MoreSheetItem(
                    icon = Icons.Default.BarChart,
                    label = stringResource(R.string.tab_stats),
                ) {
                    navigateTo(Screens.Stats.route)
                    showMore = false
                }
                MoreSheetItem(
                    icon = Icons.Default.Settings,
                    label = stringResource(R.string.tab_settings),
                ) {
                    navigateTo(Screens.Settings.route)
                    showMore = false
                }
            }
        }
        val playingTrack = state.currentTrack
        if (showPlayer && playingTrack != null) {
            NowPlayingSheet(
                track = playingTrack,
                audioInfo = state.audioInfo,
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
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
                lyricPreview = state.syncedLyrics.getOrNull(state.activeLyricIndex)?.text,
                onOpenLyrics = {
                    viewModel.loadLyrics()
                    showPlayer = false
                    showLyricsSheet = true
                },
                onOpenQueue = {
                    showPlayer = false
                    navigateTo(Screens.Queue.route)
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
                activeWordIndex = state.activeWordIndex,
                activeLyricRows = state.activeLyricRows,
                lyricsOffsetMs = state.lyricsOffsetMs,
                lyricsSource = state.lyricsSource,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
                onTogglePlayPause = viewModel::togglePlayPause,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavorite = viewModel::toggleFavorite,
                onShare = { track -> shareTrack(context, track) },
                onLyricsOffsetChange = viewModel::setLyricsOffsetMs,
                onRefreshLyrics = viewModel::refreshLyrics,
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
}
