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
import com.tuneplayer.app.data.db.PlaylistEntity
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.AudioInfo
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.model.LibrarySort
import com.metrolist.innertubex.extraction.StreamResolveException
import com.tuneplayer.app.data.model.YouTubeAudioQuality
import com.tuneplayer.app.data.model.applySort
import com.tuneplayer.app.data.model.matchesQuery
import com.tuneplayer.app.data.youtube.YouTubeSearchFilter
import com.tuneplayer.app.data.discord.ActiveHoldState
import com.tuneplayer.app.data.discord.DiscordAssetRegistrar
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
import com.tuneplayer.app.ui.viewmodel.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal const val DEFAULT_SLEEP_MINUTES = 30

/** How long a buffering track may keep the last Discord card before hiding it. */
internal const val DISCORD_HOLD_TIMEOUT_MS = 8_000L

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
    val homeShelves: List<com.tuneplayer.app.data.youtube.YouTubeHomeShelf> = emptyList(),
    val isLoadingHomeRecommendations: Boolean = false,
    val currentTrack: Track? = null,
    /** Codec/bitrate of the playing stream, when the player or resolver knows it. */
    val audioInfo: AudioInfo? = null,
    val query: String = "",
    val isLoadingLocal: Boolean = false,
    val isSearching: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
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
    val accent: String? = null,
    val searchHistory: List<String> = emptyList(),
    val historyTracks: List<Track> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    /** playlist id -> ordered song ids, populated from Room when tab opens. */
    val playlistTrackIds: Map<String, List<String>> = emptyMap(),
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
    /** Every line still being sung, including overlaps over the active one. */
    val activeLyricRows: List<Int> = emptyList(),
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
    /** Tracks of a browsed artist/album; route holds the browseId. */
    val detailTracks: List<Track> = emptyList(),
    val detailTitle: String = "",
    val isLoadingDetail: Boolean = false,
    val lyricsOffsetMs: Long = 0L,
    val lyricsSource: LyricsSource? = null,
    val downloadedTracks: List<Track> = emptyList(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadTotalBytes: Long = 0L,
    val discordRpcEnabled: Boolean = false,
    val discordTokenSaved: Boolean = false,
    val discordConnected: Boolean = false,
    val discordShowWhenPaused: Boolean = false,
    val discordApplicationId: String? = null,
)

class TunePlayerViewModel(application: Application) : AndroidViewModel(application) {

    internal val app = application as TunePlayerApp
    internal val localRepository = app.localMusicRepository
    internal val youTubeRepository = app.youTubeMusicRepository
    internal val lrclibLyricsRepository = app.lrclibLyricsRepository
    internal val ovhLyricsRepository = app.ovhLyricsRepository
    internal val kuGouLyricsRepository = app.kuGouLyricsRepository
    internal var searchJob: Job? = null
    internal var searchSuggestionsJob: Job? = null
    internal val favoriteStore = app.favoriteStore
    internal val queueStore = app.queueStore
    internal val playStatsStore = app.playStatsStore
    internal val resolvedUris = LinkedHashMap<String, String>()
    internal var positionPollJob: kotlinx.coroutines.Job? = null
    internal var lyricsJob: Job? = null
    internal var lyricsTrackId: String? = null
    internal var localLoadCompleted = false
    internal var knownDownloadErrors: Set<String> = emptySet()

    /** Buffering hold state for Discord presence; null when nothing is held. */
    internal var discordHold: ActiveHoldState? = null
    internal var lastVisiblePresence: LastAppliedVisiblePresence? = null

    /** Latest ExoPlayer state, used to distinguish pause from buffering. */
    internal var playerPlaybackState: Int = Player.STATE_IDLE

    internal var uiState by mutableStateOf(TunePlayerUiState())

    internal val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    ).buildAsync()

    internal var controller: MediaController? = null

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
            accent = app.settingsStore.accentColor,
            searchHistory = app.settingsStore.searchHistory,
            playlists = emptyList(), // loaded async by refreshPlaylists()
            playlistTrackIds = emptyMap(),
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
        refreshPlaylists()
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
                if (connected && !wasConnected) {
                    // Registration is a network call, so it must not block the
                    // flow collector; a second coroutine keeps the collect hot.
                    viewModelScope.launch {
                        runCatching { updateDiscordPresence() }
                            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
                    }
                }
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
                                uiState = uiState.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
                                if (playbackState == Player.STATE_ENDED) advanceAfterTrackEnded()
                            }

                            override fun onTracksChanged(tracks: Tracks) {
                                updateAudioInfo(tracks)
                            }
                        })
                        uiState = uiState.copy(
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


    internal var pollTicks = 0


    /** Local library as the user wants it: filtered, then sorted. */
    val visibleLocalTracks: List<Track>
        get() = uiState.localTracks
            .filter { it.matchesQuery(uiState.libraryQuery) }
            .applySort(uiState.librarySort, uiState.librarySortDescending)


    internal var sleepTimerJob: kotlinx.coroutines.Job? = null
    internal var sleepTimerFinishTrack = false


    // ---- Library bulk actions ----


    // ---- Playlists ----


    // ---- Stats ----


    internal var restoreAttempted = false


    /** Player item index -> uiState queue index; empty when no queue is loaded. */
    fun playNext() {
        val nextIndex = uiState.queueIndex + 1
        if (nextIndex in uiState.playbackQueue.indices) {
            playFromQueue(uiState.playbackQueue, nextIndex)
        }
    }


    // ---- Offline downloads ----


    // ---- Discord Rich Presence ----


    // ---- Lyrics ----


    internal companion object {
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


}
