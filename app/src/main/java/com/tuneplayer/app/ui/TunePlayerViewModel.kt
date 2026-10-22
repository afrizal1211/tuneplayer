package com.tuneplayer.app.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionToken
import com.tuneplayer.app.TunePlayerApp
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.data.prefs.AppTheme
import com.tuneplayer.app.data.prefs.FavoriteStore
import com.tuneplayer.app.data.lyrics.LrcParser
import com.tuneplayer.app.playback.PlaybackService
import com.tuneplayer.app.widget.NowPlayingWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_SLEEP_MINUTES = 30

data class TunePlayerUiState(
    val localTracks: List<Track> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val currentTrack: Track? = null,
    val query: String = "",
    val isLoadingLocal: Boolean = false,
    val isSearching: Boolean = false,
    val isPlaying: Boolean = false,
    val playerReady: Boolean = false,
    val error: String? = null,
    val sleepDurationMillis: Long = 0,
    val isSleepTimerActive: Boolean = false,
    val playbackQueue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val theme: AppTheme = AppTheme.System,
    val equalizerEnabled: Boolean = false,
    val bassBoostLevel: Int = 0,
    val virtualizerLevel: Int = 0,
    val lyrics: String? = null,
    val isLoadingLyrics: Boolean = false,
    val syncedLyrics: List<LrcParser.LrcLine> = emptyList(),
    val activeLyricIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val favoriteIds: Set<String> = emptySet(),
)

class TunePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TunePlayerApp
    private var audioSessionId: Int? = null
    private val localRepository = app.localMusicRepository
    private val youTubeRepository = app.youTubeMusicRepository
    private val favoriteStore = app.favoriteStore
    private val resolvedUris = LinkedHashMap<String, String>()
    private var positionPollJob: kotlinx.coroutines.Job? = null

    var uiState by mutableStateOf(TunePlayerUiState())
        private set

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    ).buildAsync()

    private var controller: MediaController? = null

    init {
        uiState = uiState.copy(
            theme = app.settingsStore.theme,
            equalizerEnabled = app.settingsStore.equalizerEnabled,
            bassBoostLevel = app.settingsStore.bassBoostLevel,
            virtualizerLevel = app.settingsStore.virtualizerLevel,
            favoriteIds = favoriteStore.favorites(),
        )
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
                                if (playbackState == Player.STATE_ENDED) advanceAfterTrackEnded()
                            }
                        })
                        uiState = uiState.copy(
                            playerReady = true,
                            isPlaying = connectedController.isPlaying,
                            isShuffleEnabled = connectedController.shuffleModeEnabled,
                            repeatMode = connectedController.repeatMode,
                            playbackSpeed = connectedController.playbackParameters.speed,
                        )
                        startPositionPolling()
                    }
                    .onFailure { error ->
                        uiState = uiState.copy(error = error.message ?: "Player connection failed.")
                    }
            },
            ContextCompat.getMainExecutor(application),
        )
    }

    fun loadLocalMusic() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingLocal = true, error = null)
            runCatching { localRepository.loadTracks() }
                .onSuccess { tracks ->
                    uiState = uiState.copy(localTracks = tracks, isLoadingLocal = false)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isLoadingLocal = false,
                        error = error.message ?: "Could not read local music.",
                    )
                }
        }
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = viewModelScope.launch {
            while (true) {
                val lines = uiState.syncedLyrics
                if (lines.isNotEmpty()) {
                    val pos = controller?.currentPosition ?: 0L
                    val idx = LrcParser.activeIndex(lines, pos)
                    if (idx != uiState.activeLyricIndex) {
                        uiState = uiState.copy(activeLyricIndex = idx)
                    }
                }
                delay(250)
            }
        }
    }

    /** Mirrors ExoPlayer's own auto-advance (shuffle, repeat-one, next track) into uiState. */
    private fun syncQueueIndexFromPlayer() {
        val player = controller ?: return
        val playerIndex = player.currentMediaItemIndex
        if (playerIndex !in playerIndexToQueueIndex.indices) return
        val index = playerIndexToQueueIndex[playerIndex]
        if (index in uiState.playbackQueue.indices && index != uiState.queueIndex) {
            val track = uiState.playbackQueue[index]
            uiState = uiState.copy(queueIndex = index, currentTrack = track)
            recordHistory(track.id)
            publishWidgetState()
        }
    }

    private fun advanceAfterTrackEnded() {
        if (uiState.repeatMode == Player.REPEAT_MODE_ONE) return
        if (uiState.queueIndex < uiState.playbackQueue.lastIndex) return
        if (!uiState.isShuffleEnabled && uiState.repeatMode == Player.REPEAT_MODE_OFF) {
            // Queue exhausted: stop rather than looping back to the top.
            uiState = uiState.copy(isPlaying = false)
        }
    }

    fun toggleShuffle() {
        val player = controller ?: return
        val enabled = !player.shuffleModeEnabled
        player.shuffleModeEnabled = enabled
        uiState = uiState.copy(isShuffleEnabled = enabled)
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

    fun toggleFavorite(track: Track) {
        val added = favoriteStore.toggleFavorite(track.id)
        val ids = uiState.favoriteIds.toMutableSet()
        if (added) ids += track.id else ids -= track.id
        uiState = uiState.copy(favoriteIds = ids)
    }

    private fun recordHistory(id: String) {
        favoriteStore.recordPlay(id)
    }

    /** Stream URIs are short-lived; cap the cache and let it expire with the process. */
    private fun cacheResolvedUri(trackId: String, uri: String) {
        resolvedUris[trackId] = uri
        if (resolvedUris.size > MAX_RESOLVED_CACHE) {
            resolvedUris.keys.firstOrNull()?.let(resolvedUris::remove)
        }
    }

    fun updateQuery(query: String) {
        uiState = uiState.copy(query = query)
    }

    fun search() {
        val query = uiState.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            uiState = uiState.copy(isSearching = true, error = null)
            runCatching { youTubeRepository.search(query) }
                .onSuccess { tracks ->
                    uiState = uiState.copy(searchResults = tracks, isSearching = false)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isSearching = false,
                        error = error.message ?: "YouTube Music search failed.",
                    )
                }
        }
    }

    fun play(track: Track) {
        viewModelScope.launch {
            uiState = uiState.copy(error = null)

            // Enqueue without disturbing an existing queue slot, then play.
            enqueue(track)
            val index = uiState.queueIndex
            pushQueueToPlayer(uiState.playbackQueue, index)
            startTrack(track)
        }
    }

    /** Adds [track] to the queue and points [TunePlayerUiState.queueIndex] at it. */
    private fun enqueue(track: Track) {
        val queue = uiState.playbackQueue
        val existing = queue.indexOfFirst { it.id == track.id }
        val newQueue = if (existing >= 0) queue else queue + track
        val newIndex = if (existing >= 0) existing else newQueue.lastIndex
        uiState = uiState.copy(playbackQueue = newQueue, queueIndex = newIndex)
    }

    private suspend fun startTrack(track: Track) {
        if (track.isLocal) {
            val mediaUri = track.mediaUri
            if (mediaUri == null) {
                uiState = uiState.copy(error = "Local track has no media URI.")
                return
            }
            playResolved(track, mediaUri, null)
        } else {
            val videoId = track.videoId
            if (videoId == null) {
                uiState = uiState.copy(error = "YouTube track has no video id.")
                return
            }
            uiState = uiState.copy(isSearching = true)
            runCatching { youTubeRepository.resolveAudio(videoId) }
                .onSuccess { audio ->
                    uiState = uiState.copy(isSearching = false)
                    cacheResolvedUri(track.id, audio.url)
                    playResolved(track, audio.url, audio.mimeType)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isSearching = false,
                        error = error.message ?: "Could not resolve YouTube audio.",
                    )
                }
        }
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

    /** Sleep timer is wall-clock: it counts down regardless of pause or track changes. */
    private fun startSleepTimer() {
        sleepTimerJob?.cancel()
        val duration = uiState.sleepDurationMillis
        if (!uiState.isSleepTimerActive || duration <= 0) return
        sleepTimerJob = viewModelScope.launch {
            delay(duration)
            controller?.pause()
            uiState = uiState.copy(isSleepTimerActive = false, sleepDurationMillis = 0)
            sleepTimerJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        uiState = uiState.copy(isSleepTimerActive = false)
    }

    /** Selecting a duration starts the countdown immediately. */
    fun setSleepDuration(minutes: Int) {
        uiState = uiState.copy(
            sleepDurationMillis = minutes.toLong() * 60 * 1000,
            isSleepTimerActive = minutes > 0,
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

    fun addToQueue(track: Track) {
        val wasEmpty = uiState.playbackQueue.isEmpty()
        enqueue(track)
        if (wasEmpty) {
            playFromQueue(uiState.playbackQueue, uiState.queueIndex)
        } else {
            pushQueueToPlayer(uiState.playbackQueue, uiState.queueIndex)
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
        uiState = uiState.copy(
            playbackQueue = emptyList(),
            queueIndex = 0,
            currentTrack = null,
            isPlaying = false,
        )
        cancelSleepTimer()
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
     * [playerIndexToQueueIndex] keeps uiState aligned with the player's own
     * (smaller) item list.
     */
    private fun pushQueueToPlayer(queue: List<Track>, startIndex: Int) {
        val player = controller ?: return
        val built = mutableListOf<Pair<MediaItem, Int>>()
        queue.forEachIndexed { index, track ->
            val uri = track.mediaUri ?: resolvedUris[track.id] ?: return@forEachIndexed
            built += buildMediaItem(track, uri, null) to index
        }
        if (built.isEmpty()) return
        playerIndexToQueueIndex = built.map { it.second }
        val playerStart = playerIndexToQueueIndex.indexOfFirst { it >= startIndex }
            .takeIf { it >= 0 } ?: 0
        player.setMediaItems(built.map { it.first }, playerStart, 0L)
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

    fun updateEqualizer(enabled: Boolean, bass: Int, virtualizer: Int) {
        uiState = uiState.copy(
            equalizerEnabled = enabled,
            bassBoostLevel = bass,
            virtualizerLevel = virtualizer,
        )
        app.settingsStore.equalizerEnabled = enabled
        app.settingsStore.bassBoostLevel = bass
        app.settingsStore.virtualizerLevel = virtualizer
        applyAudioEffects()
    }

    fun loadLyrics() {
        val videoId = uiState.currentTrack?.videoId ?: run {
            uiState = uiState.copy(lyrics = null, isLoadingLyrics = false, syncedLyrics = emptyList(), activeLyricIndex = -1)
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingLyrics = true, lyrics = null)
            val text = runCatching { youTubeRepository.fetchLyrics(videoId) }.getOrNull()
            val synced = LrcParser.parse(text)
            uiState = uiState.copy(
                isLoadingLyrics = false,
                lyrics = text,
                syncedLyrics = synced,
                activeLyricIndex = -1,
            )
        }
    }

    private fun publishWidgetState() {
        val track = uiState.currentTrack
        NowPlayingWidget.publish(app, track?.title, track?.artist, uiState.isPlaying)
    }

    private fun applyAudioEffects() {
        runCatching {
            val sessionId = audioSessionId ?: return@runCatching
            AudioEffects.apply(
                sessionId = sessionId,
                enabled = uiState.equalizerEnabled,
                bass = uiState.bassBoostLevel,
                virtualizerLevel = uiState.virtualizerLevel,
            )
        }
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
        const val MAX_RESOLVED_CACHE = 50
    }

    override fun onCleared() {
        positionPollJob?.cancel()
        sleepTimerJob?.cancel()
        AudioEffects.release()
        controller?.release()
        MediaController.releaseFuture(controllerFuture)
        youTubeRepository.close()
        super.onCleared()
    }

    /**
     * Plays an already-resolved track.
     *
     * Updates the player item at [TunePlayerUiState.queueIndex] in place when a
     * queue is loaded, so shuffle/repeat/auto-advance keep working. Falls back
     * to a single item when no queue exists.
     */
    private fun playResolved(track: Track, uri: String, mimeType: String?) {
        val player = controller
        if (player == null) {
            uiState = uiState.copy(error = "Player is not ready yet.")
            return
        }

        val mediaItem = buildMediaItem(track, uri, mimeType)
        val playerIndex = playerIndexToQueueIndex.indexOf(uiState.queueIndex)
        if (playerIndex >= 0 && playerIndex < player.mediaItemCount) {
            player.replaceMediaItem(playerIndex, mediaItem)
            player.seekTo(playerIndex, 0L)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.prepare()
        player.play()
        audioSessionId = (player as? ExoPlayer)?.audioSessionId?.takeIf { it != 0 }
        uiState = uiState.copy(currentTrack = track, isPlaying = true)
        recordHistory(track.id)
        applyAudioEffects()
        publishWidgetState()
    }

    private fun buildMediaItem(track: Track, uri: String, mimeType: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUri?.let(Uri::parse))
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .apply { mimeType?.let(::setMimeType) }
            .build()
    }
}
