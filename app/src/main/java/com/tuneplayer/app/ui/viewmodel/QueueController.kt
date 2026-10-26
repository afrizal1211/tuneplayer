package com.tuneplayer.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.tuneplayer.app.data.lyrics.activeWordIndex
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.ui.TunePlayerViewModel
import kotlinx.coroutines.launch
import com.tuneplayer.app.ui.TunePlayerViewModel.Companion.TAG

/** Appends [track] to the end of the queue without disturbing playback. */
internal fun TunePlayerViewModel.addToQueue(track: Track) {
    val wasEmpty = uiState.playbackQueue.isEmpty()
    val queue = uiState.playbackQueue
    if (queue.any { it.id == track.id }) return
    val newQueue = queue + track
    uiState = uiState.copy(playbackQueue = newQueue)
    if (wasEmpty) {
        playFromQueue(newQueue, 0)
    } else {
        // Appending must not disturb what is playing: setMediaItems resets
        // the playhead, so the new item is added to the player's own list.
        val uri = playbackUri(track)
        if (uri != null) {
            controller?.addMediaItem(buildMediaItem(track, uri, null))
        }
        persistQueue()
    }
}

internal fun TunePlayerViewModel.removeFromQueue(index: Int) {
    if (index !in uiState.playbackQueue.indices) return
    val playingId = uiState.currentTrack?.id
    val removingCurrent = index == uiState.queueIndex
    val newQueue = uiState.playbackQueue.filterIndexed { i, _ -> i != index }
    // Track the playing item by identity; fall back to index clamping.
    val newIndex = playingId
        ?.let { id -> newQueue.indexOfFirst { it.id == id } }
        ?.takeIf { it >= 0 }
        ?: uiState.queueIndex.coerceIn(0, newQueue.lastIndex)
    uiState = uiState.copy(
        playbackQueue = newQueue,
        queueIndex = newIndex,
    )
    if (newQueue.isEmpty()) {
        clearQueue()
        return
    }
    if (removingCurrent) {
        playFromQueue(newQueue, newIndex)
    } else {
        // Only the queue display changed: the player keeps its own list and
        // its playhead. setMediaItems resets playback to the start of an
        // item, so the removal is applied to the player's list in place.
        removePlayerItemForQueueIndex(index)
        persistQueue()
    }
}

/**
 * Drops the player item that backs [queueIndex], leaving playback running.
 *
 * The player list can be shorter than the queue because remote tracks
 * without a cached URI are skipped, so the item is found by media id rather
 * than by offset.
 */
internal fun TunePlayerViewModel.removePlayerItemForQueueIndex(queueIndex: Int) {
    val player = controller ?: return
    val track = uiState.playbackQueue.getOrNull(queueIndex) ?: return
    val playerIndex = (0 until player.mediaItemCount)
        .firstOrNull { player.getMediaItemAt(it).mediaId == track.id }
        ?: return
    if (playerIndex == player.currentMediaItemIndex) return
    player.removeMediaItem(playerIndex)
}

internal fun TunePlayerViewModel.clearQueue() {
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

internal fun TunePlayerViewModel.playFromQueue(queue: List<Track>, index: Int) {
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
internal fun TunePlayerViewModel.pushQueueToPlayer(queue: List<Track>, startIndex: Int) {
    val player = controller ?: return
    val items = mutableListOf<MediaItem>()
    val queueIndexes = mutableListOf<Int>()
    queue.forEachIndexed { index, track ->
        val uri = playbackUri(track) ?: return@forEachIndexed
        items += buildMediaItem(track, uri, null)
        queueIndexes += index
    }
    if (items.isEmpty()) return
    val playerStart = queueIndexes.indexOfFirst { it >= startIndex }
        .takeIf { it >= 0 } ?: 0
    player.setMediaItems(items, playerStart, 0L)
    persistQueue()
}

/**
 * Rebuilds the last queue once, as soon as both the controller and the
 * library are available.
 *
 * Only ids that still resolve are kept; a queue of deleted files restores
 * as empty rather than as a list of broken rows. Nothing autoplays: the
 * saved item becomes current and the saved position is restored, so the
 * user decides when to resume.
 */
internal fun TunePlayerViewModel.maybeRestoreQueue() {
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
internal fun TunePlayerViewModel.persistQueue() {
    queueStore.ids = uiState.playbackQueue.map { it.id }
    queueStore.index = uiState.queueIndex
}

internal fun TunePlayerViewModel.moveInQueue(fromIndex: Int, toIndex: Int) {
    val queue = uiState.playbackQueue
    if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
    val playingId = queue.getOrNull(uiState.queueIndex)?.id
    val newQueue = queue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    val newIndex = playingId
        ?.let { id -> newQueue.indexOfFirst { it.id == id } }
        ?.takeIf { it >= 0 }
        ?: uiState.queueIndex.coerceIn(0, newQueue.lastIndex)
    uiState = uiState.copy(playbackQueue = newQueue, queueIndex = newIndex)
    // Reordering must not disturb the playhead. setMediaItems restarts the
    // current item, so only the persistence layer is updated here; the
    // player's own order is left for the next full queue load.
    persistQueue()
}

internal fun TunePlayerViewModel.playbackUri(track: Track): String? =
    track.mediaUri ?: app.offlineDownloadManager.localUri(track.id) ?: resolvedUris[track.id]
