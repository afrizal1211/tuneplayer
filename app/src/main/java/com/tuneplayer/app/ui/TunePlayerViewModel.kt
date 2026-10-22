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
import androidx.media3.session.SessionToken
import com.tuneplayer.app.TunePlayerApp
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.playback.PlaybackService
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
)

class TunePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TunePlayerApp
    private val localRepository = app.localMusicRepository
    private val youTubeRepository = app.youTubeMusicRepository

    var uiState by mutableStateOf(TunePlayerUiState())
        private set

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    ).buildAsync()

    private var controller: MediaController? = null

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { connectedController ->
                        controller = connectedController
                        connectedController.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                uiState = uiState.copy(isPlaying = isPlaying)
                            }
                        })
                        uiState = uiState.copy(
                            playerReady = true,
                            isPlaying = connectedController.isPlaying,
                        )
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
            
            // Add to queue if not already present
            val currentQueue = uiState.playbackQueue
            val existingIndex = currentQueue.indexOfFirst { it.id == track.id }
            val (newQueue, newIndex) = if (existingIndex >= 0) {
                currentQueue to existingIndex
            } else {
                val added = currentQueue + track
                added to (added.size - 1)
            }
            uiState = uiState.copy(
                playbackQueue = newQueue,
                queueIndex = newIndex,
            )
            
            if (track.isLocal) {
                val mediaUri = track.mediaUri
                if (mediaUri == null) {
                    uiState = uiState.copy(error = "Local track has no media URI.")
                    return@launch
                }
                playResolved(track, mediaUri, null)
            } else {
                val videoId = track.videoId
                if (videoId == null) {
                    uiState = uiState.copy(error = "YouTube track has no video id.")
                    return@launch
                }
                uiState = uiState.copy(isSearching = true)
                runCatching { youTubeRepository.resolveAudio(videoId) }
                    .onSuccess { audio ->
                        uiState = uiState.copy(isSearching = false)
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
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
            cancelSleepTimer()
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
            startSleepTimerIfNeeded()
        }
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    private fun startSleepTimerIfNeeded() {
        if (uiState.isSleepTimerActive && uiState.sleepDurationMillis > 0) {
            sleepTimerJob?.cancel()
            sleepTimerJob = viewModelScope.launch {
                delay(uiState.sleepDurationMillis)
                controller?.pause()
                uiState = uiState.copy(isSleepTimerActive = false, sleepDurationMillis = 0)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        uiState = uiState.copy(isSleepTimerActive = false, sleepDurationMillis = 0)
    }

    fun setSleepDuration(minutes: Int) {
        uiState = uiState.copy(sleepDurationMillis = minutes.toLong() * 60 * 1000)
        if (!uiState.isSleepTimerActive) {
            startSleepTimerIfNeeded()
        }
    }

    fun toggleSleepTimer() {
        uiState = uiState.copy(
            isSleepTimerActive = !uiState.isSleepTimerActive,
            sleepDurationMillis = if (!uiState.isSleepTimerActive) uiState.sleepDurationMillis else 0
        )
    }

    fun addToQueue(track: Track) {
        val newQueue = uiState.playbackQueue + track
        val newIndex = if (uiState.playbackQueue.isEmpty()) 0 else uiState.queueIndex
        uiState = uiState.copy(
            playbackQueue = newQueue,
            queueIndex = newIndex,
        )
        if (uiState.currentTrack == null) {
            playFromQueue(newQueue, newIndex)
        }
    }

    fun removeFromQueue(index: Int) {
        val newQueue = uiState.playbackQueue.filterIndexed { i, _ -> i != index }
        val newIndex = if (index < uiState.queueIndex && uiState.queueIndex > 0) uiState.queueIndex - 1
        else if (index == uiState.queueIndex && newQueue.isNotEmpty()) uiState.queueIndex
        else uiState.queueIndex.coerceAtMost(newQueue.size - 1).coerceAtLeast(0)
        uiState = uiState.copy(
            playbackQueue = newQueue,
            queueIndex = newIndex,
        )
    }

    fun clearQueue() {
        uiState = uiState.copy(
            playbackQueue = emptyList(),
            queueIndex = 0,
        )
    }

    fun playFromQueue(queue: List<Track>, index: Int) {
        if (queue.isEmpty() || index < 0 || index >= queue.size) return
        val track = queue[index]
        uiState = uiState.copy(queueIndex = index)
        play(track)
    }

    fun playNext() {
        val nextIndex = uiState.queueIndex + 1
        if (nextIndex < uiState.playbackQueue.size) {
            playFromQueue(uiState.playbackQueue, nextIndex)
        }
    }

    fun playPrevious() {
        val prevIndex = uiState.queueIndex - 1
        if (prevIndex >= 0) {
            playFromQueue(uiState.playbackQueue, prevIndex)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val newQueue = uiState.playbackQueue.toMutableList()
        val item = newQueue.removeAt(fromIndex)
        newQueue.add(toIndex, item)
        val newIndex = if (uiState.queueIndex == fromIndex) toIndex
        else if (fromIndex < uiState.queueIndex && toIndex >= uiState.queueIndex) uiState.queueIndex - 1
        else if (fromIndex > uiState.queueIndex && toIndex <= uiState.queueIndex) uiState.queueIndex + 1
        else uiState.queueIndex
        uiState = uiState.copy(
            playbackQueue = newQueue,
            queueIndex = newIndex.coerceAtMost(newQueue.size - 1).coerceAtLeast(0),
        )
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        controller?.release()
        MediaController.releaseFuture(controllerFuture)
        youTubeRepository.close()
        super.onCleared()
    }

    private fun playResolved(track: Track, uri: String, mimeType: String?) {
        val player = controller
        if (player == null) {
            uiState = uiState.copy(error = "Player is not ready yet.")
            return
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUri?.let(Uri::parse))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .apply { mimeType?.let(::setMimeType) }
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        uiState = uiState.copy(currentTrack = track, isPlaying = true)
        startSleepTimerIfNeeded()
    }
}
