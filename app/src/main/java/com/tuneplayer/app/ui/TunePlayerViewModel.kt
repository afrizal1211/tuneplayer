package com.tuneplayer.app.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tuneplayer.app.TunePlayerApp
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.data.prefs.PlayStatsStore
import com.tuneplayer.app.data.prefs.PlaylistStore
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.AudioInfo
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.model.LibrarySort
import com.tuneplayer.app.data.model.YouTubeAudioQuality
import com.tuneplayer.app.data.model.applySort
import com.tuneplayer.app.data.model.matchesQuery
import com.tuneplayer.app.data.youtube.YouTubeSearchFilter
import com.tuneplayer.app.data.discord.ActiveHoldState
import com.tuneplayer.app.data.discord.DiscordHoldContext
import com.tuneplayer.app.data.discord.DiscordPresenceDecision
import com.tuneplayer.app.data.discord.DiscordPresenceInputs
import com.tuneplayer.app.data.discord.LastAppliedVisiblePresence
import com.tuneplayer.app.data.discord.derivePlaybackSemanticState
import com.tuneplayer.app.data.discord.deriveRawDiscordPresenceDecision
import com.tuneplayer.app.data.discord.resolveDiscordPresenceDecision
import com.tuneplayer.app.data.prefs.AppTheme
import com.tuneplayer.app.data.prefs.FavoriteStore
import com.tuneplayer.app.data.lyrics.LyricsLine
import com.tuneplayer.app.data.lyrics.LyricsParser
import com.tuneplayer.app.data.lyrics.KuGouLyricsRepository
import com.tuneplayer.app.data.lyrics.activeLineIndex
import com.tuneplayer.app.data.lyrics.activeWordIndex
import com.tuneplayer.app.data.lyrics.LyricsLookup
import com.tuneplayer.app.data.lyrics.LyricsSource
import com.tuneplayer.app.playback.PlaybackService
import com.tuneplayer.app.playback.PlaybackKeys
import com.tuneplayer.app.playback.QueueBuilder
import com.tuneplayer.app.widget.NowPlayingWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val DEFAULT_SLEEP_MINUTES = 30

/** How long a buffering track may keep the last Discord card before hiding it. */
private const val DISCORD_HOLD_TIMEOUT_MS = 8_000L

data class TunePlayerUiState(
    val localTracks: List<Track> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val searchTopResult: Track? = null,
    val searchFilter: YouTubeSearchFilter = YouTubeSearchFilter.ALL,
    val searchCommitted: Boolean = false,
    val searchSuggestions: List<String> = emptyList(),
    val searchRecommendations: List<Track> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    val homeRecommendations: List<Track> = emptyList(),
    val isLoadingHomeRecommendations: Boolean = false,
    val currentTrack: Track? = null,
    /** Codec/bitrate of the playing stream, when the player or resolver knows it. */
    val audioInfo: AudioInfo? = null,
    val query: String = "",
    val isLoadingLocal: Boolean = false,
    val isSearching: Boolean = false,
    val isPlaying: Boolean = false,
    val playerReady: Boolean = false,
    val error: String? = null,
    val sleepDurationMillis: Long = 0,
    val isSleepTimerActive: Boolean = false,
    val sleepFinishTrack: Boolean = false,
    val keepScreenOn: Boolean = false,
    val skipSilence: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationSeconds: Float = 5f,
    val automixEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val gridLayout: Boolean = false,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val historyTracks: List<Track> = emptyList(),
    val playlists: List<PlaylistStore.Playlist> = emptyList(),
    val playbackQueue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val theme: AppTheme = AppTheme.System,
    val equalizerEnabled: Boolean = false,
    val eqBands: List<Int> = emptyList(),
    val eqBandFreqs: List<Int> = emptyList(),
    val eqBandRange: IntRange? = null,
    val eqPreset: Int = -1,
    val bassBoostLevel: Int = 0,
    val virtualizerLevel: Int = 0,
    val lyrics: String? = null,
    val isLoadingLyrics: Boolean = false,
    val syncedLyrics: List<LyricsLine> = emptyList(),
    val activeLyricIndex: Int = -1,
    /** Word within [activeLyricIndex] currently being sung, or -1 for line-only lyrics. */
    val activeWordIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val favoriteIds: Set<String> = emptySet(),
    val librarySort: LibrarySort = LibrarySort.Title,
    val librarySortDescending: Boolean = false,
    val libraryQuery: String = "",
    val youtubeSignedIn: Boolean = false,
    val youtubeAudioQuality: YouTubeAudioQuality = YouTubeAudioQuality.High,
    val lyricsOffsetMs: Long = 0L,
    val lyricsSource: LyricsSource? = null,
    val downloadedTracks: List<Track> = emptyList(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadTotalBytes: Long = 0L,
    val discordRpcEnabled: Boolean = false,
    val discordTokenSaved: Boolean = false,
    val discordConnected: Boolean = false,
    val discordShowWhenPaused: Boolean = false,
)

class TunePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TunePlayerApp
    private val localRepository = app.localMusicRepository
    private val youTubeRepository = app.youTubeMusicRepository
    private val lrclibLyricsRepository = app.lrclibLyricsRepository
    private val ovhLyricsRepository = app.ovhLyricsRepository
    private val kuGouLyricsRepository = KuGouLyricsRepository()
    private var searchJob: Job? = null
    private var searchSuggestionsJob: Job? = null
    private val favoriteStore = app.favoriteStore
    private val playlistStore = app.playlistStore
    private val queueStore = app.queueStore
    private val playStatsStore = app.playStatsStore
    private val resolvedUris = LinkedHashMap<String, String>()
    private var positionPollJob: kotlinx.coroutines.Job? = null
    private var lyricsJob: Job? = null
    private var lyricsTrackId: String? = null
    private var localLoadCompleted = false
    private var knownDownloadErrors: Set<String> = emptySet()

    /** Buffering hold state for Discord presence; null when nothing is held. */
    private var discordHold: ActiveHoldState? = null
    private var lastVisiblePresence: LastAppliedVisiblePresence? = null

    /** Latest ExoPlayer state, used to distinguish pause from buffering. */
    private var playerPlaybackState: Int = Player.STATE_IDLE

    var uiState by mutableStateOf(TunePlayerUiState())
        private set

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    ).buildAsync()

    private var controller: MediaController? = null

    init {
        val offlineState = app.offlineDownloadManager.state.value
        uiState = uiState.copy(
            theme = app.settingsStore.theme,
            equalizerEnabled = app.settingsStore.equalizerEnabled,
            bassBoostLevel = app.settingsStore.bassBoostLevel,
            virtualizerLevel = app.settingsStore.virtualizerLevel,
            eqBands = normalizeEqBands(app.settingsStore.eqBands),
            eqBandFreqs = AudioEffects.UI_BAND_FREQS_HZ,
            eqBandRange = AudioEffects.UI_BAND_RANGE_MB,
            eqPreset = app.settingsStore.eqPreset,
            favoriteIds = favoriteStore.favorites(),
            librarySort = app.settingsStore.librarySort,
            librarySortDescending = app.settingsStore.librarySortDescending,
            keepScreenOn = app.settingsStore.keepScreenOn,
            skipSilence = app.settingsStore.skipSilence,
            hapticsEnabled = app.settingsStore.hapticsEnabled,
            gridLayout = app.settingsStore.gridLayout,
            dynamicColor = app.settingsStore.dynamicColor,
            pureBlack = app.settingsStore.pureBlack,
            searchHistory = app.settingsStore.searchHistory,
            playlists = playlistStore.all(),
            youtubeSignedIn = app.settingsStore.youtubeCookie != null,
            youtubeAudioQuality = app.settingsStore.youtubeAudioQuality,
            lyricsOffsetMs = app.settingsStore.lyricsOffsetMs,
            downloadedTracks = app.offlineDownloadManager.downloadedTracks(),
            downloadProgress = offlineState.progress,
            downloadTotalBytes = app.offlineDownloadManager.totalBytes(),
            discordRpcEnabled = app.settingsStore.discordRpcEnabled,
            discordTokenSaved = app.settingsStore.discordToken != null,
            discordShowWhenPaused = app.settingsStore.discordShowWhenPaused,
        )
        knownDownloadErrors = offlineState.errors.keys
        viewModelScope.launch {
            app.offlineDownloadManager.state.collect { downloads ->
                val newErrors = downloads.errors.keys - knownDownloadErrors
                knownDownloadErrors = downloads.errors.keys
                uiState = uiState.copy(
                    downloadedTracks = app.offlineDownloadManager.downloadedTracks(),
                    downloadProgress = downloads.progress,
                    downloadTotalBytes = app.offlineDownloadManager.totalBytes(),
                    error = if (newErrors.isNotEmpty()) {
                        app.getString(R.string.download_failed)
                    } else {
                        uiState.error
                    },
                )
                refreshHistoryTracks()
                maybeRestoreQueue()
            }
        }
        viewModelScope.launch {
            app.discordRpcClient.connected.collect { connected ->
                val wasConnected = uiState.discordConnected
                uiState = uiState.copy(discordConnected = connected)
                if (connected && !wasConnected) updateDiscordPresence()
            }
        }
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { connectedController ->
                        controller = connectedController
                        connectedController.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                uiState = uiState.copy(isPlaying = isPlaying)
                                publishWidgetState()
                            }

                            override fun onMediaItemTransition(
                                mediaItem: MediaItem?,
                                reason: Int,
                            ) {
                                syncQueueIndexFromPlayer()
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                playerPlaybackState = playbackState
                                if (playbackState == Player.STATE_ENDED) advanceAfterTrackEnded()
                            }

                            override fun onTracksChanged(tracks: Tracks) {
                                updateAudioInfo(tracks)
                            }
                        })
                        uiState = uiState.copy(
                            playerReady = true,
                            isPlaying = connectedController.isPlaying,
                            isShuffleEnabled = connectedController.shuffleModeEnabled,
                            repeatMode = connectedController.repeatMode,
                            playbackSpeed = connectedController.playbackParameters.speed,
                            audioInfo = audioInfoFromTracks(connectedController.currentTracks),
                        )
                        startPositionPolling()
                        maybeRestoreQueue()
                    }
                    .onFailure { error ->
                        LogCatcher.e(TAG, "Player connection failed", error)
                        uiState = uiState.copy(error = error.message ?: app.getString(R.string.error_connection))
                    }
            },
            ContextCompat.getMainExecutor(application),
        )
    }

    fun loadLocalMusic() {
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

    private fun refreshHistoryTracks() {
        val known = (uiState.localTracks + uiState.searchResults + uiState.downloadedTracks)
            .associateBy { it.id }
        // Ids that no longer resolve are dropped rather than shown as broken rows.
        val resolved = favoriteStore.history.mapNotNull { known[it] }
        uiState = uiState.copy(historyTracks = resolved)
    }

    private var pollTicks = 0

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = viewModelScope.launch {
            while (true) {
                val player = controller
                if (player != null) {
                    uiState = uiState.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration
                            .takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET }
                            ?: uiState.currentTrack?.durationMs
                            ?: 0L,
                    )
                }
                // Roughly every 5s: prefs writes are cheap but not free.
                pollTicks++
                if (pollTicks % 20 == 0 && uiState.currentTrack != null) {
                    queueStore.positionMs = uiState.positionMs
                    queueStore.index = uiState.queueIndex
                }
                val lines = uiState.syncedLyrics
                if (lines.isNotEmpty()) {
                    val position = (uiState.positionMs - uiState.lyricsOffsetMs).coerceAtLeast(0L)
                    val idx = activeLineIndex(lines, position)
                    val word = if (idx == uiState.activeLyricIndex) {
                        uiState.activeWordIndex
                    } else {
                        activeWordIndex(lines[idx], position)
                    }
                    if (idx != uiState.activeLyricIndex) {
                        uiState = uiState.copy(activeLyricIndex = idx, activeWordIndex = word)
                    } else if (word != uiState.activeWordIndex) {
                        uiState = uiState.copy(activeWordIndex = word)
                    }
                }
                delay(250)
            }
        }
    }

    /** Seeks to [positionMs], clamped to the known duration. */
    fun seekTo(positionMs: Long) {
        val player = controller ?: run {
            LogCatcher.w(TAG, "seekTo ignored: player not ready")
            return
        }
        val target = positionMs.coerceAtLeast(0L)
        player.seekTo(target)
        LogCatcher.d(TAG, "seekTo ${target}ms")
        uiState = uiState.copy(positionMs = target)
        updateDiscordPresence()
    }

    /**
     * Mirrors ExoPlayer's own auto-advance (shuffle, repeat, next track) into uiState.
     *
     * The item is identified by media id, never by index: with shuffle enabled
     * the player's indices address the *shuffled* timeline, so mapping a player
     * index back through the queue order silently points at the wrong track.
     */
    private fun syncQueueIndexFromPlayer() {
        val player = controller ?: return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val index = uiState.playbackQueue.indexOfFirst { it.id == mediaId }
        if (index >= 0 && index != uiState.queueIndex) {
            val track = uiState.playbackQueue[index]
            uiState = uiState.copy(queueIndex = index, currentTrack = track, audioInfo = null)
            clearLyricsState()
            recordHistory(track.id)
            prefetchLyrics(track)
            publishWidgetState()
        }
    }

    private fun advanceAfterTrackEnded() {
        // Repeat-one loops the item; repeat-all and shuffle both keep going on
        // their own. Only the exhausted, unshuffled, non-repeating queue stops.
        if (uiState.repeatMode != Player.REPEAT_MODE_OFF) return
        if (uiState.isShuffleEnabled) return
        if (uiState.queueIndex < uiState.playbackQueue.lastIndex) return
        uiState = uiState.copy(isPlaying = false)
    }

    fun toggleShuffle() {
        val player = controller ?: return
        val enabled = !player.shuffleModeEnabled
        player.shuffleModeEnabled = enabled
        uiState = uiState.copy(isShuffleEnabled = enabled)
        // Media3 reorders its own timeline when shuffle turns on; the current
        // item keeps playing, so re-sync the queue pointer to it.
        syncQueueIndexFromPlayer()
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        uiState = uiState.copy(repeatMode = next)
    }

    /** Steps speed through a small musical ladder: 0.5, 0.75, 1.0, 1.25, 1.5, 2.0. */
    fun cyclePlaybackSpeed() {
        val player = controller ?: return
        val ladder = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val current = player.playbackParameters.speed
        val next = ladder.firstOrNull { it > current + 0.01f } ?: ladder.first()
        player.setPlaybackSpeed(next)
        uiState = uiState.copy(playbackSpeed = next)
    }

    /** Local library as the user wants it: filtered, then sorted. */
    val visibleLocalTracks: List<Track>
        get() = uiState.localTracks
            .filter { it.matchesQuery(uiState.libraryQuery) }
            .applySort(uiState.librarySort, uiState.librarySortDescending)

    fun updateLibraryQuery(query: String) {
        uiState = uiState.copy(libraryQuery = query)
    }

    fun setLibrarySort(sort: LibrarySort) {
        uiState = uiState.copy(librarySort = sort)
        app.settingsStore.librarySort = sort
    }

    fun toggleLibrarySortDirection() {
        val descending = !uiState.librarySortDescending
        uiState = uiState.copy(librarySortDescending = descending)
        app.settingsStore.librarySortDescending = descending
    }

    fun toggleFavorite(track: Track) {
        val added = favoriteStore.toggleFavorite(track.id)
        val ids = uiState.favoriteIds.toMutableSet()
        if (added) ids += track.id else ids -= track.id
        uiState = uiState.copy(favoriteIds = ids)
    }

    private fun recordHistory(id: String) {
        favoriteStore.recordPlay(id)
        playStatsStore.record(id)
        // Cheap: history is capped at 100 ids, and the tab must not go stale.
        refreshHistoryTracks()
    }

    /** Stream URIs are short-lived; cap the cache and let it expire with the process. */
    private fun cacheResolvedUri(trackId: String, uri: String) {
        resolvedUris[trackId] = uri
        if (resolvedUris.size > MAX_RESOLVED_CACHE) {
            resolvedUris.keys.firstOrNull()?.let(resolvedUris::remove)
        }
    }

    fun updateQuery(query: String) {
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
    fun loadHomeRecommendations() {
        if (uiState.isLoadingHomeRecommendations || uiState.homeRecommendations.isNotEmpty()) return
        uiState = uiState.copy(isLoadingHomeRecommendations = true)
        viewModelScope.launch {
            try {
                val tracks = youTubeRepository.homeRecommendations()
                uiState = uiState.copy(
                    homeRecommendations = tracks,
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
    private fun scheduleSearchSuggestions(rawQuery: String) {
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

    fun search() {
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
    fun setSearchFilter(filter: YouTubeSearchFilter) {
        if (filter == uiState.searchFilter) return
        val query = uiState.query.trim()
        val committed = uiState.searchCommitted
        uiState = uiState.copy(searchFilter = filter)
        if (committed && query.isNotEmpty()) runSearch(query, filter)
    }

    private fun runSearch(query: String, filter: YouTubeSearchFilter) {
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

    fun play(track: Track) {
        LogCatcher.d(TAG, "play: ${track.title} local=${track.isLocal}")
        viewModelScope.launch {
            uiState = uiState.copy(error = null)
            val seeded = autoQueueSeed(track)
            val index = seeded.indexOfFirst { it.id == track.id }
                .takeIf { it >= 0 } ?: seeded.lastIndex
            uiState = uiState.copy(playbackQueue = seeded, queueIndex = index)
            pushQueueToPlayer(seeded, index)
            startTrack(track)
        }
    }

    /**
     * Builds the queue [play] starts from when the user taps a song.
     *
     * An already-populated queue is kept and the track is inserted after the
     * current item: replacing it would drop everything the user queued, and
     * [playResolved] would then fall back to a single-item player. Only an
     * empty or single-track queue is seeded from the library, starting at the
     * tapped song and wrapping around, so playback has somewhere to go.
     */
    private fun autoQueueSeed(track: Track): List<Track> {
        val queue = uiState.playbackQueue
        if (queue.size > 1) {
            if (queue.any { it.id == track.id }) return queue
            val insertAt = (uiState.queueIndex + 1).coerceAtMost(queue.size)
            return queue.toMutableList().apply { add(insertAt, track) }
        }
        val pool = (visibleLocalTracks + uiState.searchResults + uiState.downloadedTracks)
            .distinctBy { it.id }
        if (pool.isEmpty()) return listOf(track)
        val start = pool.indexOfFirst { it.id == track.id }
        if (start < 0) return listOf(track) + pool
        val ordered = pool.subList(start, pool.size) + pool.subList(0, start)
        // Drop the duplicate cuts and cap how much of one artist a station
        // carries, so the seed is a mix rather than a discography.
        return QueueBuilder.extend(listOf(track), ordered.drop(1), limit = 50)
            .let { listOf(track) + it }
    }

    /** Adds [track] to the queue and points [TunePlayerUiState.queueIndex] at it. */
    private suspend fun startTrack(track: Track) {
        val offlineUri = app.offlineDownloadManager.localUri(track.id)
        if (offlineUri != null) {
            playResolved(track, offlineUri, null, null)
            return
        }
        if (track.isLocal) {
            val mediaUri = track.mediaUri
            if (mediaUri == null) {
                uiState = uiState.copy(error = app.getString(R.string.error_no_audio_uri))
                return
            }
            playResolved(track, mediaUri, null, null)
        } else {
            val videoId = track.videoId
            if (videoId == null) {
                uiState = uiState.copy(error = app.getString(R.string.error_no_video_id))
                return
            }
            uiState = uiState.copy(isSearching = true)
            try {
                val audio = youTubeRepository.resolveAudio(videoId, uiState.youtubeAudioQuality)
                uiState = uiState.copy(isSearching = false)
                cacheResolvedUri(track.id, audio.url)
                playResolved(
                    track = track,
                    uri = audio.url,
                    mimeType = audio.mimeType,
                    info = AudioInfo(codec = audio.codec),
                )
                // Do not delay playback for a metadata HEAD request. Fill in the
                // estimated bitrate if the player did not report one.
                viewModelScope.launch {
                    val estimated = youTubeRepository.estimateBitrateKbps(
                        url = audio.url,
                        requestHeaders = audio.headers,
                        durationMs = track.durationMs,
                    )
                    if (estimated != null && uiState.currentTrack?.id == track.id) {
                        val current = uiState.audioInfo
                        if (current?.bitrateKbps == null) {
                            uiState = uiState.copy(
                                audioInfo = AudioInfo(
                                    codec = current?.codec ?: audio.codec,
                                    bitrateKbps = estimated,
                                    sampleRateHz = current?.sampleRateHz,
                                    channelCount = current?.channelCount,
                                ),
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogCatcher.e(TAG, "YouTube resolve failed", error)
                uiState = uiState.copy(
                    isSearching = false,
                    error = error.message ?: app.getString(R.string.error_resolve_failed),
                )
            }
        }
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    /** Restarts the track when past the platform's previous threshold, else goes back. */
    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    /** Sets an exact speed; the menu offers musical steps, the API clamps the rest. */
    fun setPlaybackSpeed(speed: Float) {
        val player = controller ?: return
        val clamped = speed.coerceIn(0.25f, 3.0f)
        player.setPlaybackSpeed(clamped)
        uiState = uiState.copy(playbackSpeed = clamped)
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sleepTimerFinishTrack = false

    /**
     * Sleep timer is wall-clock: it counts down regardless of pause or track changes.
     *
     * When "finish current track" is armed the wait happens on the player's
     * own end-of-media event instead of a [delay], so pausing does not burn
     * the remaining wall-clock budget.
     */
    private fun startSleepTimer() {
        sleepTimerJob?.cancel()
        val duration = uiState.sleepDurationMillis
        if (!uiState.isSleepTimerActive || duration <= 0) return
        sleepTimerJob = viewModelScope.launch {
            if (sleepTimerFinishTrack) {
                // Wait for the player to reach the end of the current item.
                while (true) {
                    val player = controller
                    val atEnd = player == null ||
                        (player.duration > 0 && player.currentPosition >= player.duration - END_TOLERANCE_MS)
                    if (atEnd || !uiState.isSleepTimerActive) break
                    delay(500)
                }
            } else {
                delay(duration)
            }
            if (!uiState.isSleepTimerActive) return@launch
            controller?.pause()
            uiState = uiState.copy(isSleepTimerActive = false, sleepDurationMillis = 0)
            sleepTimerJob = null
            sleepTimerFinishTrack = false
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerFinishTrack = false
        uiState = uiState.copy(isSleepTimerActive = false)
    }

    /** Selecting a duration starts the countdown immediately, disarming finish-track. */
    fun setSleepDuration(minutes: Int) {
        sleepTimerFinishTrack = false
        uiState = uiState.copy(
            sleepDurationMillis = minutes.toLong() * 60 * 1000,
            isSleepTimerActive = minutes > 0,
            sleepFinishTrack = false,
        )
        startSleepTimer()
    }

    /**
     * Arms "stop when the current track ends" instead of a wall-clock countdown.
     * The duration picker still reports an active timer; the stop happens at
     * the track boundary.
     */
    fun setSleepFinishTrack() {
        sleepTimerFinishTrack = true
        uiState = uiState.copy(
            sleepDurationMillis = 0,
            isSleepTimerActive = true,
            sleepFinishTrack = true,
        )
        startSleepTimer()
    }

    fun toggleSleepTimer() {
        if (uiState.isSleepTimerActive) {
            cancelSleepTimer()
        } else {
            setSleepDuration(DEFAULT_SLEEP_MINUTES)
        }
    }

    // ---- Library bulk actions ----

    /** Replaces the queue with [tracks] in order and starts the first one. */
    fun playTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        uiState = uiState.copy(playbackQueue = tracks, queueIndex = 0)
        pushQueueToPlayer(tracks, 0)
        viewModelScope.launch { startTrack(tracks.first()) }
        LogCatcher.d(TAG, "playTracks: ${tracks.size} tracks")
    }

    /** Same as [playTracks] but shuffled, for list-level shuffle actions. */
    fun shuffleTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playTracks(tracks.shuffled())
    }

    /** Replaces the queue with every visible local track in shuffled order and plays. */
    fun shuffleAll() {
        shuffleTracks(visibleLocalTracks)
    }

    /** Replaces the queue with every visible local track in sort order and plays. */
    fun playAll() {
        playTracks(visibleLocalTracks)
    }

    // ---- Playlists ----

    fun playlists(): List<PlaylistStore.Playlist> = playlistStore.all()

    /** Re-reads the store after a mutation so the UI sees the new list. */
    private fun refreshPlaylists() {
        uiState = uiState.copy(playlists = playlistStore.all())
    }

    fun createPlaylist(name: String): PlaylistStore.Playlist? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val created = playlistStore.create(trimmed)
        refreshPlaylists()
        LogCatcher.d(TAG, "createPlaylist: $trimmed")
        return created
    }

    fun deletePlaylist(id: String) {
        playlistStore.delete(id)
        refreshPlaylists()
    }

    fun renamePlaylist(id: String, name: String) {
        playlistStore.rename(id, name)
        refreshPlaylists()
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        playlistStore.addTrack(playlistId, track.id)
        refreshPlaylists()
        LogCatcher.d(TAG, "addToPlaylist: ${track.title} -> $playlistId")
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        playlistStore.removeTrack(playlistId, trackId)
        refreshPlaylists()
    }

    fun moveInPlaylist(playlistId: String, from: Int, to: Int) {
        playlistStore.moveTrack(playlistId, from, to)
        refreshPlaylists()
    }

    /** Bulk-adds everything currently queued; used by the playlist screen. */
    fun addQueueToPlaylist(playlistId: String) {
        uiState.playbackQueue.forEach { playlistStore.addTrack(playlistId, it.id) }
        refreshPlaylists()
    }

    /** Resolves playlist ids against the local library, downloads, and current search results. */
    fun tracksForPlaylist(playlist: PlaylistStore.Playlist): List<Track> {
        val known = (uiState.localTracks + uiState.downloadedTracks + uiState.searchResults)
            .associateBy { it.id }
        return playlist.trackIds.mapNotNull { known[it] }
    }

    fun playPlaylist(playlist: PlaylistStore.Playlist, startIndex: Int = 0) {
        val tracks = tracksForPlaylist(playlist)
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(tracks.indices)
        uiState = uiState.copy(playbackQueue = tracks, queueIndex = index)
        pushQueueToPlayer(tracks, index)
        val first = tracks[index]
        viewModelScope.launch { startTrack(first) }
    }

    // ---- Stats ----

    fun topTracks(limit: Int = 20): List<Track> {
        val known = (uiState.localTracks + uiState.downloadedTracks + uiState.searchResults)
            .associateBy { it.id }
        return playStatsStore.topIds(limit).mapNotNull { known[it] }
    }

    fun totalPlays(): Int = playStatsStore.totalPlays()

    fun distinctTracks(): Int = playStatsStore.distinctTracks()

    fun setKeepScreenOn(enabled: Boolean) {
        app.settingsStore.keepScreenOn = enabled
        uiState = uiState.copy(keepScreenOn = enabled)
    }

    /** Persisted for the service; the prefs listener applies it live. */
    fun setSkipSilence(enabled: Boolean) {
        app.settingsStore.skipSilence = enabled
        uiState = uiState.copy(skipSilence = enabled)
    }

    /** Persisted for the service, which re-arms the blend on the next track. */
    fun setCrossfade(enabled: Boolean, durationSeconds: Float) {
        app.settingsStore.crossfadeEnabled = enabled
        app.settingsStore.crossfadeDurationSeconds = durationSeconds
    }

    fun setAutomixEnabled(enabled: Boolean) {
        app.settingsStore.automixEnabled = enabled
        uiState = uiState.copy(automixEnabled = enabled)
        uiState = uiState.copy(
            crossfadeEnabled = enabled,
            crossfadeDurationSeconds = app.settingsStore.crossfadeDurationSeconds,
        )
    }

    fun setHapticsEnabled(enabled: Boolean) {
        app.settingsStore.hapticsEnabled = enabled
        uiState = uiState.copy(hapticsEnabled = enabled)
    }

    fun toggleGridLayout() {
        val next = !uiState.gridLayout
        app.settingsStore.gridLayout = next
        uiState = uiState.copy(gridLayout = next)
    }

    fun clearSearchHistory() {
        app.settingsStore.clearSearchHistory()
        uiState = uiState.copy(searchHistory = emptyList())
    }

    /** Appends [track] to the end of the queue without disturbing playback. */
    fun addToQueue(track: Track) {
        val wasEmpty = uiState.playbackQueue.isEmpty()
        val queue = uiState.playbackQueue
        if (queue.any { it.id == track.id }) return
        val newQueue = queue + track
        uiState = uiState.copy(playbackQueue = newQueue)
        if (wasEmpty) {
            playFromQueue(newQueue, 0)
        } else {
            pushQueueToPlayer(newQueue, uiState.queueIndex)
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in uiState.playbackQueue.indices) return
        val playingId = uiState.currentTrack?.id
        val removingCurrent = index == uiState.queueIndex
        val newQueue = uiState.playbackQueue.filterIndexed { i, _ -> i != index }
        // Track the playing item by identity; fall back to index clamping.
        val newIndex = playingId
            ?.let { id -> newQueue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: uiState.queueIndex.coerceAtMost(newQueue.lastIndex).coerceAtLeast(0)
        uiState = uiState.copy(
            playbackQueue = newQueue,
            queueIndex = newIndex,
        )
        if (newQueue.isEmpty()) {
            clearQueue()
        } else {
            pushQueueToPlayer(newQueue, newIndex)
            if (removingCurrent) {
                playFromQueue(newQueue, newIndex)
            }
        }
    }

    fun clearQueue() {
        controller?.stop()
        controller?.clearMediaItems()
        queueStore.clear()
        uiState = uiState.copy(
            playbackQueue = emptyList(),
            queueIndex = 0,
            currentTrack = null,
            audioInfo = null,
            isPlaying = false,
            lyrics = null,
            syncedLyrics = emptyList(),
            activeLyricIndex = -1,
            activeWordIndex = -1,
            lyricsSource = null,
            isLoadingLyrics = false,
        )
        lyricsJob?.cancel()
        cancelSleepTimer()
        discordHold = null
        lastVisiblePresence = null
        publishWidgetState()
    }

    fun playFromQueue(queue: List<Track>, index: Int) {
        if (index !in queue.indices) return
        val track = queue[index]
        uiState = uiState.copy(playbackQueue = queue, queueIndex = index)
        viewModelScope.launch {
            uiState = uiState.copy(error = null)
            pushQueueToPlayer(queue, index)
            startTrack(track)
        }
    }

    /**
     * Loads [queue] into ExoPlayer so the platform handles auto-advance,
     * shuffle, and repeat. Remote tracks without a cached URI are skipped, so
     * the player's item list can be shorter than [queue]; matching back by
     * media id keeps uiState aligned with what is actually playing.
     */
    private fun pushQueueToPlayer(queue: List<Track>, startIndex: Int) {
        val player = controller ?: return
        val items = mutableListOf<MediaItem>()
        val queueIndexes = mutableListOf<Int>()
        queue.forEachIndexed { index, track ->
            val uri = playbackUri(track) ?: return@forEachIndexed
            items += buildMediaItem(track, uri, null)
            queueIndexes += index
        }
        if (items.isEmpty()) return
        playerIndexToQueueIndex = queueIndexes
        val playerStart = queueIndexes.indexOfFirst { it >= startIndex }
            .takeIf { it >= 0 } ?: 0
        player.setMediaItems(items, playerStart, 0L)
        persistQueue()
    }

    private fun playbackUri(track: Track): String? =
        track.mediaUri ?: app.offlineDownloadManager.localUri(track.id) ?: resolvedUris[track.id]

    private var restoreAttempted = false

    /**
     * Rebuilds the last queue once, as soon as both the controller and the
     * library are available.
     *
     * Only ids that still resolve are kept; a queue of deleted files restores
     * as empty rather than as a list of broken rows. Nothing autoplays: the
     * saved item becomes current and the saved position is restored, so the
     * user decides when to resume.
     */
    private fun maybeRestoreQueue() {
        if (restoreAttempted) return
        if (controller == null) return
        // Wait for the local scan so file-backed queue entries are not mistaken
        // for stale ids. A queue made entirely of downloads can restore now.
        if (!localLoadCompleted) {
            val pendingIds = queueStore.ids
            if (pendingIds.isEmpty()) return
            if (pendingIds.any { app.offlineDownloadManager.localUri(it) == null }) return
        }
        if (uiState.playbackQueue.isNotEmpty()) {
            // Something is already queued (shortcut, widget). Do not clobber it.
            restoreAttempted = true
            return
        }
        restoreAttempted = true
        val ids = queueStore.ids
        if (ids.isEmpty()) return
        val known = (uiState.localTracks + uiState.searchResults + uiState.downloadedTracks)
            .associateBy { it.id }
        val queue = ids.mapNotNull { known[it] }
        if (queue.isEmpty()) {
            queueStore.clear()
            return
        }
        val index = queueStore.index.coerceIn(queue.indices)
        uiState = uiState.copy(
            playbackQueue = queue,
            queueIndex = index,
            currentTrack = queue[index],
            positionMs = queueStore.positionMs,
        )
        pushQueueToPlayer(queue, index)
        val saved = queueStore.positionMs
        if (saved > 0) controller?.seekTo(saved)
        prefetchLyrics(queue[index])
        publishWidgetState()
        LogCatcher.d(TAG, "restored queue: ${queue.size} tracks at $index")
    }

    /** Mirrors the queue into prefs; called on every queue mutation. */
    private fun persistQueue() {
        queueStore.ids = uiState.playbackQueue.map { it.id }
        queueStore.index = uiState.queueIndex
    }

    /** Player item index -> uiState queue index; empty when no queue is loaded. */
    private var playerIndexToQueueIndex: List<Int> = emptyList()

    fun playNext() {
        val nextIndex = uiState.queueIndex + 1
        if (nextIndex in uiState.playbackQueue.indices) {
            playFromQueue(uiState.playbackQueue, nextIndex)
        }
    }

    /** Inserts [track] directly after the current item, or appends when nothing plays. */
    fun playNext(track: Track) {
        val queue = uiState.playbackQueue
        val existing = queue.indexOfFirst { it.id == track.id }
        if (existing >= 0) {
            if (existing != uiState.queueIndex + 1) moveInQueue(existing, uiState.queueIndex + 1)
            return
        }
        val insertAt = if (queue.isEmpty()) 0 else (uiState.queueIndex + 1).coerceAtMost(queue.size)
        val newQueue = queue.toMutableList().apply { add(insertAt, track) }
        val playingId = queue.getOrNull(uiState.queueIndex)?.id
        val newIndex = playingId
            ?.let { id -> newQueue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        uiState = uiState.copy(playbackQueue = newQueue, queueIndex = newIndex)
        if (queue.isEmpty()) playFromQueue(newQueue, 0)
    }

    fun playPrevious() {
        val prevIndex = uiState.queueIndex - 1
        if (prevIndex in uiState.playbackQueue.indices) {
            playFromQueue(uiState.playbackQueue, prevIndex)
        }
    }

    fun setTheme(theme: AppTheme) {
        uiState = uiState.copy(theme = theme)
        ThemeController.set(theme)
        app.settingsStore.theme = theme
    }

    fun setDynamicColor(enabled: Boolean) {
        app.settingsStore.dynamicColor = enabled
        ThemeController.updateDynamicColor(enabled)
        uiState = uiState.copy(dynamicColor = enabled)
    }

    fun setPureBlack(enabled: Boolean) {
        app.settingsStore.pureBlack = enabled
        ThemeController.updatePureBlack(enabled)
        uiState = uiState.copy(pureBlack = enabled)
    }

    /**
     * Re-reads every store after a restore.
     *
     * The screen already holds composed state, so nothing updates unless it is
     * pulled again here. Playback is left alone: the import may have replaced
     * the queue, but stopping the current track mid-song would be worse.
     */
    fun reloadFromStores() {
        val settings = app.settingsStore
        ThemeController.set(settings.theme)
        ThemeController.updateDynamicColor(settings.dynamicColor)
        ThemeController.updatePureBlack(settings.pureBlack)
        val cookie = settings.youtubeCookie
        if (cookie == null) {
            youTubeRepository.clearSession()
        } else {
            youTubeRepository.applySession(
                cookie = cookie,
                dataSyncId = settings.youtubeDataSyncId,
                authUser = settings.youtubeAuthUser,
            )
        }
        uiState = uiState.copy(
            theme = settings.theme,
            equalizerEnabled = settings.equalizerEnabled,
            bassBoostLevel = settings.bassBoostLevel,
            virtualizerLevel = settings.virtualizerLevel,
            eqBands = normalizeEqBands(settings.eqBands),
            eqBandFreqs = AudioEffects.UI_BAND_FREQS_HZ,
            eqBandRange = AudioEffects.UI_BAND_RANGE_MB,
            eqPreset = settings.eqPreset,
            favoriteIds = favoriteStore.favorites(),
            librarySort = settings.librarySort,
            librarySortDescending = settings.librarySortDescending,
            gridLayout = settings.gridLayout,
            keepScreenOn = settings.keepScreenOn,
            skipSilence = settings.skipSilence,
            crossfadeEnabled = settings.crossfadeEnabled,
            crossfadeDurationSeconds = settings.crossfadeDurationSeconds,
            automixEnabled = settings.automixEnabled,
            hapticsEnabled = settings.hapticsEnabled,
            dynamicColor = settings.dynamicColor,
            pureBlack = settings.pureBlack,
            searchHistory = settings.searchHistory,
            playlists = playlistStore.all(),
            youtubeSignedIn = settings.youtubeCookie != null,
            youtubeAudioQuality = settings.youtubeAudioQuality,
            lyricsOffsetMs = settings.lyricsOffsetMs,
            discordRpcEnabled = settings.discordRpcEnabled,
            discordTokenSaved = settings.discordToken != null,
        )
        app.syncDiscordRpc()
        refreshHistoryTracks()
        LogCatcher.d(TAG, "reloaded stores after restore")
    }

    fun updateEqualizer(enabled: Boolean, bass: Int, virtualizer: Int) {
        uiState = uiState.copy(
            equalizerEnabled = enabled,
            bassBoostLevel = bass,
            virtualizerLevel = virtualizer,
        )
        app.settingsStore.equalizerEnabled = enabled
        app.settingsStore.bassBoostLevel = bass
        app.settingsStore.virtualizerLevel = virtualizer
    }

    /** Stores a SAPISID-bearing cookie and switches InnerTube to the account. */
    fun connectYouTubeAccount(cookie: String) {
        val normalized = cookie.trim()
        if (!normalized.contains("SAPISID=", ignoreCase = true)) {
            uiState = uiState.copy(error = app.getString(R.string.youtube_login_failed))
            return
        }
        app.settingsStore.youtubeCookie = normalized
        app.settingsStore.youtubeDataSyncId = null
        app.settingsStore.youtubeAuthUser = "0"
        youTubeRepository.applySession(normalized, dataSyncId = null, authUser = "0")
        resolvedUris.clear()
        uiState = uiState.copy(
            youtubeSignedIn = true,
            error = null,
            homeRecommendations = emptyList(),
        )
        LogCatcher.d(TAG, "YouTube Music account connected")
    }

    fun disconnectYouTubeAccount() {
        app.settingsStore.clearYouTubeSession()
        youTubeRepository.clearSession()
        resolvedUris.clear()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        uiState = uiState.copy(
            youtubeSignedIn = false,
            homeRecommendations = emptyList(),
        )
        LogCatcher.d(TAG, "YouTube Music account disconnected")
    }

    fun setYouTubeAudioQuality(quality: YouTubeAudioQuality) {
        app.settingsStore.youtubeAudioQuality = quality
        // Cached stream URLs were resolved with the previous format.
        resolvedUris.clear()
        uiState = uiState.copy(youtubeAudioQuality = quality)
    }

    // ---- Offline downloads ----

    fun downloadTrack(track: Track) {
        app.offlineDownloadManager.download(track, uiState.youtubeAudioQuality)
    }

    fun removeDownload(track: Track) {
        app.offlineDownloadManager.remove(track.id)
    }

    fun clearDownloads() {
        app.offlineDownloadManager.clearAll()
    }

    // ---- Discord Rich Presence ----

    fun setDiscordRpcEnabled(enabled: Boolean) {
        app.settingsStore.discordRpcEnabled = enabled
        uiState = uiState.copy(discordRpcEnabled = enabled)
        app.syncDiscordRpc()
        updateDiscordPresence()
    }

    /** Keep or drop the card while paused, independent of the enable toggle. */
    fun setDiscordShowWhenPaused(show: Boolean) {
        app.settingsStore.discordShowWhenPaused = show
        uiState = uiState.copy(discordShowWhenPaused = show)
        updateDiscordPresence()
    }

    /** Saving a token also opts in, matching the single dialog that collects it. */
    fun saveDiscordToken(token: String) {
        val trimmed = token.trim().removePrefix("Bearer ").trim()
        if (trimmed.isEmpty()) return
        app.settingsStore.discordToken = trimmed
        app.settingsStore.discordRpcEnabled = true
        uiState = uiState.copy(discordRpcEnabled = true, discordTokenSaved = true)
        app.syncDiscordRpc()
        updateDiscordPresence()
    }

    fun removeDiscordToken() {
        app.settingsStore.clearDiscordToken()
        uiState = uiState.copy(
            discordRpcEnabled = false,
            discordTokenSaved = false,
            discordConnected = false,
        )
        app.syncDiscordRpc()
    }

    /**
     * Applies the playback snapshot to the Discord card.
     *
     * The decision comes from [DiscordPresencePolicy]; buffering holds the last
     * visible card rather than flickering the presence off and back on, and a
     * stalled stream falls back to a cleared card.
     */
    private fun updateDiscordPresence() {
        val settings = app.settingsStore
        if (!settings.discordRpcEnabled || settings.discordToken == null) {
            discordHold = null
            app.discordRpcClient.setActivity(null)
            return
        }

        val track = uiState.currentTrack
        val decision =
            resolveDiscordPresenceDecision(
                rawDecision =
                    deriveRawDiscordPresenceDecision(
                        input =
                            DiscordPresenceInputs(
                                enabled = settings.discordRpcEnabled,
                                hasToken = settings.discordToken != null,
                                trackId = track?.id,
                                isPlaying = uiState.isPlaying,
                                showWhenPaused = settings.discordShowWhenPaused,
                                playWhenReady = uiState.isPlaying,
                                playbackState = playerPlaybackState,
                            ),
                        semanticState =
                            derivePlaybackSemanticState(
                                DiscordPresenceInputs(
                                    enabled = settings.discordRpcEnabled,
                                    hasToken = settings.discordToken != null,
                                    trackId = track?.id,
                                    isPlaying = uiState.isPlaying,
                                    showWhenPaused = settings.discordShowWhenPaused,
                                    playWhenReady = uiState.isPlaying,
                                    playbackState = playerPlaybackState,
                                ),
                            ),
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = System.currentTimeMillis(),
                        activeHoldState = discordHold,
                        lastAppliedVisiblePresence = lastVisiblePresence,
                        holdTimeoutMs = DISCORD_HOLD_TIMEOUT_MS,
                    ),
            )

        discordHold = decision.nextHoldState

        when (val applied = decision.decision) {
            is DiscordPresenceDecision.Visible -> {
                val shown = track ?: return
                lastVisiblePresence = LastAppliedVisiblePresence(shown.id, applied.mode)
                app.discordRpcClient.setActivity(buildDiscordActivity(shown, applied.isPaused))
            }

            is DiscordPresenceDecision.Hold -> Unit

            is DiscordPresenceDecision.Hidden -> {
                lastVisiblePresence = null
                app.discordRpcClient.setActivity(null)
            }
        }
    }

    private fun buildDiscordActivity(track: Track, isPaused: Boolean): JSONObject {
        val appName = app.getString(R.string.app_name).take(MAX_DISCORD_TEXT)
        val title = track.title.trim().ifBlank { appName }.take(MAX_DISCORD_TEXT)
        val artist = track.artist.trim()
            .ifBlank { app.getString(R.string.library_unknown_artist) }
            .take(MAX_DISCORD_TEXT)
        val stateText = if (isPaused) {
            app.getString(R.string.discord_state_paused, artist).take(MAX_DISCORD_TEXT)
        } else {
            artist
        }
        val now = System.currentTimeMillis()
        val duration = uiState.durationMs
        val position = uiState.positionMs.coerceAtLeast(0L)
        return JSONObject().apply {
            put("name", appName)
            put("type", 2) // Listening
            put("details", title)
            put("state", stateText)
            put("status_display_type", 2) // Prefer the track title in compact surfaces.
            if (!isPaused) {
                put("timestamps", JSONObject().apply {
                    put("start", now - position)
                    if (duration > 0L) {
                        put("end", now + (duration - position).coerceAtLeast(0L))
                    }
                })
            }
        }
    }

    // ---- Lyrics ----

    /** Positive values delay lyrics; negative values show them earlier. */
    fun setLyricsOffsetMs(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-5_000L, 5_000L)
        app.settingsStore.lyricsOffsetMs = clamped
        val lines = uiState.syncedLyrics
        val active = if (lines.isEmpty()) {
            -1
        } else {
            activeLineIndex(
                lines,
                (uiState.positionMs - clamped).coerceAtLeast(0L),
            )
        }
        uiState = uiState.copy(
            lyricsOffsetMs = clamped,
            activeLyricIndex = active,
            activeWordIndex = -1,
        )
    }

    fun loadLyrics() {
        val track = uiState.currentTrack ?: run {
            clearLyricsState()
            return
        }
        val cached = app.lyricsCache.get(track.id)
        if (cached != null) {
            applyLyrics(track.id, cached)
            return
        }
        fetchLyrics(track, showLoading = true)
    }

    fun refreshLyrics() {
        val track = uiState.currentTrack ?: return
        fetchLyrics(track, showLoading = true, force = true)
    }

    /** Warms the cache so downloaded tracks still show lyrics without a network. */
    private fun prefetchLyrics(track: Track) {
        val cached = app.lyricsCache.get(track.id)
        if (cached != null) {
            if (uiState.currentTrack?.id == track.id && uiState.lyrics == null) {
                applyLyrics(track.id, cached)
            }
            return
        }
        fetchLyrics(track, showLoading = false)
    }

    private fun fetchLyrics(track: Track, showLoading: Boolean, force: Boolean = false) {
        if (!force && lyricsJob?.isActive == true && lyricsTrackId == track.id) {
            if (showLoading && !uiState.isLoadingLyrics) {
                uiState = uiState.copy(isLoadingLyrics = true)
            }
            return
        }
        lyricsJob?.cancel()
        lyricsTrackId = track.id
        if (showLoading) {
            uiState = uiState.copy(
                isLoadingLyrics = true,
                lyrics = null,
                syncedLyrics = emptyList(),
                activeLyricIndex = -1,
                activeWordIndex = -1,
                lyricsSource = null,
            )
        }
        lyricsJob = viewModelScope.launch {
            try {
                val lookup = resolveLyrics(track)
                if (lookup == null) {
                    if (uiState.currentTrack?.id == track.id) {
                        uiState = uiState.copy(
                            isLoadingLyrics = false,
                            lyrics = null,
                            syncedLyrics = emptyList(),
                            activeLyricIndex = -1,
                            activeWordIndex = -1,
                            lyricsSource = null,
                        )
                    }
                } else {
                    app.lyricsCache.put(track.id, lookup)
                    applyLyrics(track.id, lookup)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogCatcher.w(TAG, "lyrics lookup failed: ${error.message}")
                if (uiState.currentTrack?.id == track.id) {
                    uiState = uiState.copy(isLoadingLyrics = false)
                }
            } finally {
                if (lyricsTrackId == track.id) {
                    lyricsTrackId = null
                    lyricsJob = null
                }
            }
        }
    }

    private suspend fun resolveLyrics(track: Track): LyricsLookup? {
        val youtubeText = track.videoId?.let { videoId ->
            withTimeoutOrNull(6_000L) {
                runCatching { youTubeRepository.fetchLyrics(videoId) }.getOrNull()
            }
        }
        var text = youtubeText
        var source = if (!text.isNullOrBlank()) LyricsSource.YouTube else null
        var synced = LyricsParser.parse(text).isNotEmpty()

        // YouTube timedtext is often plain text, and LRCLIB only line-times. Both
        // are asked before KuGou so word-synced lyrics win only when nothing
        // better is available, and KuGou still gets a turn at the text itself.
        if (!synced) {
            val external = withTimeoutOrNull(10_000L) {
                runCatching { lrclibLyricsRepository.find(track) }.getOrNull()
            }
            if (external != null && (text.isNullOrBlank() || external.synced)) {
                text = external.text
                source = external.source
                synced = external.synced
            }
        }
        // KuGou is the one key-less source that word-times, so it is consulted
        // even when line timing already answered.
        if (!synced) {
            val kugou = withTimeoutOrNull(10_000L) {
                runCatching { kuGouLyricsRepository.find(track) }.getOrNull()
            }
            if (kugou != null && (text.isNullOrBlank() || kugou.wordSynced)) {
                text = kugou.text
                source = kugou.source
                synced = kugou.synced
            }
        }
        // Last resort: lyrics.ovh, which only ever answers with plain text.
        if (text.isNullOrBlank()) {
            val ovh = withTimeoutOrNull(10_000L) {
                runCatching { ovhLyricsRepository.find(track) }.getOrNull()
            }
            if (ovh != null) {
                text = ovh.text
                source = ovh.source
            }
        }
        val finalText = text?.takeIf { it.isNotBlank() } ?: return null
        return LyricsLookup(
            text = finalText,
            synced = LyricsParser.parse(finalText).isNotEmpty(),
            source = source ?: LyricsSource.YouTube,
        )
    }

    private fun applyLyrics(trackId: String, lookup: LyricsLookup) {
        if (uiState.currentTrack?.id != trackId) return
        val synced = LyricsParser.parse(lookup.text)
        val position = (uiState.positionMs - uiState.lyricsOffsetMs).coerceAtLeast(0L)
        val active = if (synced.isEmpty()) -1 else activeLineIndex(synced, position)
        val word = if (active >= 0) activeWordIndex(synced[active], position) else -1
        uiState = uiState.copy(
            isLoadingLyrics = false,
            lyrics = lookup.text,
            syncedLyrics = synced,
            activeLyricIndex = active,
            activeWordIndex = word,
            lyricsSource = lookup.source,
        )
    }

    private fun clearLyricsState() {
        lyricsJob?.cancel()
        lyricsJob = null
        lyricsTrackId = null
        uiState = uiState.copy(
            isLoadingLyrics = false,
            lyrics = null,
            syncedLyrics = emptyList(),
            activeLyricIndex = -1,
            activeWordIndex = -1,
            lyricsSource = null,
        )
    }

    private fun publishWidgetState() {
        val track = uiState.currentTrack
        NowPlayingWidget.publish(app, track?.title, track?.artist, uiState.isPlaying)
        updateDiscordPresence()
    }

    /**
     * Fits saved levels onto the five UI control points.
     *
     * Older builds stored whatever band count the device reported, so a
     * device switch could leave a different length behind. The service
     * resamples the five points onto the live device; the UI just needs a
     * stable count to render.
     */
    private fun normalizeEqBands(stored: List<Int>): List<Int> {
        val count = AudioEffects.UI_BAND_FREQS_HZ.size
        if (stored.isEmpty()) return List(count) { 0 }
        val fitted = if (stored.size == count) stored else EqPresets.resample(stored, count)
        return fitted.map { it.coerceIn(AudioEffects.UI_BAND_RANGE_MB) }
    }

    fun setEqBand(index: Int, levelMb: Int) {
        val range = uiState.eqBandRange ?: return
        val clamped = levelMb.coerceIn(range.first, range.last)
        val levels = uiState.eqBands.toMutableList()
        while (levels.size <= index) levels += 0
        levels[index] = clamped
        app.settingsStore.eqBands = levels
        app.settingsStore.eqPreset = -1
        uiState = uiState.copy(eqBands = levels, eqPreset = -1)
    }

    /** Applies a preset shape, resampled onto however many bands the device has. */
    fun setEqPreset(preset: Int) {
        val count = uiState.eqBands.size.coerceAtLeast(uiState.eqBandFreqs.size)
        if (count == 0) return
        val shape = EqPresets.shapes.getOrNull(preset) ?: return
        val levels = EqPresets.resample(shape, count)
        app.settingsStore.eqBands = levels
        app.settingsStore.eqPreset = preset
        uiState = uiState.copy(eqBands = levels, eqPreset = preset)
        LogCatcher.d(TAG, "eq preset $preset over $count bands")
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val queue = uiState.playbackQueue
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val playingId = queue.getOrNull(uiState.queueIndex)?.id
        val newQueue = queue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        val newIndex = playingId
            ?.let { id -> newQueue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        uiState = uiState.copy(playbackQueue = newQueue, queueIndex = newIndex)
        pushQueueToPlayer(newQueue, newIndex)
    }

    private companion object {
        const val TAG = "TunePlayerVM"
        const val MAX_RESOLVED_CACHE = 50
        const val END_TOLERANCE_MS = 750L
        const val MAX_DISCORD_TEXT = 128
    }

    override fun onCleared() {
        positionPollJob?.cancel()
        sleepTimerJob?.cancel()
        controller?.release()
        MediaController.releaseFuture(controllerFuture)
        // The repository is Application-scoped. Closing it here killed the
        // shared HttpClient on every rotation, and the next search failed
        // with "Parent job is Completed".
        super.onCleared()
    }

    /**
     * Plays an already-resolved track.
     *
     * Updates the player item at [TunePlayerUiState.queueIndex] in place when a
     * queue is loaded, so shuffle/repeat/auto-advance keep working. Falls back
     * to a single item when no queue exists.
     */
    private fun playResolved(
        track: Track,
        uri: String,
        mimeType: String?,
        info: AudioInfo?,
    ) {
        val player = controller
        if (player == null) {
            uiState = uiState.copy(error = app.getString(R.string.error_player_not_ready))
            return
        }

        val mediaItem = buildMediaItem(track, uri, mimeType)
        // Match by media id: player indices follow the shuffle order, not the
        // queue order, so an index lookup can land on a different track.
        val playerIndex = (0 until player.mediaItemCount)
            .firstOrNull { player.getMediaItemAt(it).mediaId == track.id }
        if (playerIndex != null) {
            player.replaceMediaItem(playerIndex, mediaItem)
            player.seekTo(playerIndex, 0L)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.prepare()
        player.play()
        uiState = uiState.copy(
            currentTrack = track,
            isPlaying = true,
            audioInfo = info,
            lyrics = null,
            syncedLyrics = emptyList(),
            activeLyricIndex = -1,
            activeWordIndex = -1,
            lyricsSource = null,
            isLoadingLyrics = false,
        )
        recordHistory(track.id)
        prefetchLyrics(track)
        publishWidgetState()
    }

    /**
     * Pulls codec/bitrate from the player's selected audio track.
     *
     * Formats reported by Media3 are authoritative when present; a resolver
     * estimate is kept as a fallback because WebM/Opus often omits bitrate.
     */
    private fun updateAudioInfo(tracks: Tracks) {
        val fresh = audioInfoFromTracks(tracks) ?: return
        val fallback = uiState.audioInfo
        uiState = uiState.copy(
            audioInfo = fresh.copy(
                codec = fresh.codec ?: fallback?.codec,
                bitrateKbps = fresh.bitrateKbps ?: fallback?.bitrateKbps,
            ),
        )
    }

    @OptIn(UnstableApi::class)
    private fun audioInfoFromTracks(tracks: Tracks): AudioInfo? {
        val group = tracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_AUDIO && it.isSelected
        } ?: return null
        val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
        val format = group.getTrackFormat(index)
        val bitrate = format.averageBitrate.takeIf { it > 0 }
            ?: format.bitrate.takeIf { it > 0 }
        val info = AudioInfo(
            codec = AudioInfo.codecLabel(format.sampleMimeType, format.codecs),
            bitrateKbps = bitrate?.let { (it + 500) / 1000 },
            sampleRateHz = format.sampleRate.takeIf { it > 0 },
            channelCount = format.channelCount.takeIf { it > 0 },
        )
        return info.takeIf { it != AudioInfo() }
    }

    private fun buildMediaItem(track: Track, uri: String, mimeType: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUri?.let(Uri::parse))
            // The playback service plans crossfade length against the incoming
            // track's real duration, and cannot read that until it prepares.
            .setExtras(Bundle().apply { putLong(PlaybackKeys.EXTRA_TRACK_DURATION_MS, track.durationMs) })
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .apply { mimeType?.let(::setMimeType) }
            .build()
    }
}
