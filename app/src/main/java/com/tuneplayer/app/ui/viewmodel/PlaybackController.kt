package com.tuneplayer.app.ui.viewmodel

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.metrolist.innertubex.extraction.StreamResolveException
import com.tuneplayer.app.R
import com.tuneplayer.app.data.lyrics.activeLineIndex
import com.tuneplayer.app.data.lyrics.activeLyricRows
import com.tuneplayer.app.data.lyrics.activeWordIndex
import com.tuneplayer.app.data.model.AudioInfo
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.playback.PlaybackKeys
import com.tuneplayer.app.playback.QueueBuilder
import com.tuneplayer.app.ui.TunePlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tuneplayer.app.ui.TunePlayerViewModel.Companion.TAG

internal fun TunePlayerViewModel.startPositionPolling() {
    positionPollJob?.cancel()
    positionPollJob = viewModelScope.launch {
        while (true) {
            val player = controller
            if (player != null) {
                val playing = player.isPlaying
                // Copying the whole state while paused recomposes every
                // reader of uiState for nothing; only the lyrics block needs
                // ticks while paused, and it reads positionMs, not the player.
                if (playing) {
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
                // Play-time accounting: accumulate real playback time per
                // track and flush it periodically. Only counted while
                // actually playing, so paused hours do not inflate stats.
                val track = uiState.currentTrack
                if (playing && track != null) {
                    playedTimeAccumulatorMs += TunePlayerViewModel.POLL_INTERVAL_MS
                    if (playedTimeAccumulatorMs >= TunePlayerViewModel.PLAY_TIME_FLUSH_MS) {
                        val delta = playedTimeAccumulatorMs
                        playedTimeAccumulatorMs = 0L
                        viewModelScope.launch {
                            runCatching { app.database.dao().addPlayTime(track.id, delta) }
                                .onFailure { LogCatcher.w(TAG, "addPlayTime failed", it) }
                        }
                    }
                }
                val lines = uiState.syncedLyrics
                if (lines.isNotEmpty()) {
                    val position = (uiState.positionMs - uiState.lyricsOffsetMs).coerceAtLeast(0L)
                    val idx = activeLineIndex(lines, position)
                    val word = if (idx == uiState.activeLyricIndex) {
                        uiState.activeWordIndex
                    } else if (idx in lines.indices) {
                        activeWordIndex(lines[idx], position)
                    } else {
                        -1
                    }
                    val rows = activeLyricRows(lines, position)
                    if (idx != uiState.activeLyricIndex || rows != uiState.activeLyricRows) {
                        uiState = uiState.copy(
                            activeLyricIndex = idx,
                            activeWordIndex = word,
                            activeLyricRows = rows,
                        )
                    } else if (word != uiState.activeWordIndex) {
                        uiState = uiState.copy(activeWordIndex = word)
                    }
                }
            }
            delay(250)
        }
    }
}

/** Seeks to [positionMs], clamped to the known duration. */
internal fun TunePlayerViewModel.seekTo(positionMs: Long) {
    val player = controller ?: run {
        LogCatcher.w(TAG, "seekTo ignored: player not ready")
        return
    }
    val target = positionMs.coerceAtLeast(0L)
    player.seekTo(target)
    LogCatcher.d(TAG, "seekTo ${target}ms")
    uiState = uiState.copy(positionMs = target)
    viewModelScope.launch {
        runCatching { updateDiscordPresence() }
            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
    }
}

internal fun TunePlayerViewModel.advanceAfterTrackEnded() {
    // Repeat-one loops the item; repeat-all and shuffle both keep going on
    // their own. Only the exhausted, unshuffled, non-repeating queue stops.
    if (uiState.repeatMode != Player.REPEAT_MODE_OFF) return
    if (uiState.isShuffleEnabled) return
    if (uiState.queueIndex < uiState.playbackQueue.lastIndex) return
    uiState = uiState.copy(isPlaying = false)
}

internal fun TunePlayerViewModel.skipToNext() {
    val player = controller ?: return
    runCatching { player.seekToNext() }
        .onFailure { LogCatcher.w(TAG, "seekToNext failed", it) }
}

/** Restarts the track when past the platform's previous threshold, else goes back. */
internal fun TunePlayerViewModel.skipToPrevious() {
    val player = controller ?: return
    runCatching { player.seekToPrevious() }
        .onFailure { LogCatcher.w(TAG, "seekToPrevious failed", it) }
}

internal fun TunePlayerViewModel.togglePlayPause() {
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

/** Replaces the queue with [tracks] in order and starts the first one. */
internal fun TunePlayerViewModel.playTracks(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    uiState = uiState.copy(playbackQueue = tracks, queueIndex = 0)
    pushQueueToPlayer(tracks, 0)
    viewModelScope.launch { startTrack(tracks.first()) }
    LogCatcher.d(TAG, "playTracks: ${tracks.size} tracks")
}

/** Same as [playTracks] but shuffled, for list-level shuffle actions. */
internal fun TunePlayerViewModel.shuffleTracks(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    playTracks(tracks.shuffled())
}

/** Replaces the queue with every visible local track in shuffled order and plays. */
internal fun TunePlayerViewModel.shuffleAll() {
    shuffleTracks(visibleLocalTracks)
}

/** Replaces the queue with every visible local track in sort order and plays. */
internal fun TunePlayerViewModel.playAll() {
    playTracks(visibleLocalTracks)
}

internal fun TunePlayerViewModel.play(track: Track) {
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

/** Adds [track] to the queue and points [TunePlayerUiState.queueIndex] at it. */
internal suspend fun TunePlayerViewModel.startTrack(track: Track) {
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
            // Do not delay playback for a metadata request. Fill in the
            // estimated bitrate if the player did not report one.
            viewModelScope.launch {
                val estimated = youTubeRepository.estimateBitrateKbps(
                    url = audio.url,
                    requestHeaders = audio.headers,
                    durationMs = track.durationMs,
                    knownContentLengthBytes = audio.contentLengthBytes,
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
                error = describeResolveFailure(error),
            )
        }
    }
}

/**
 * The message a failed resolve shows the listener.
 *
 * The library reports *why* a track could not be resolved, and the reason
 * changes what the listener can do about it: an age gate or a takedown is
 * permanent, a network failure is not. Showing the raw exception text tells
 * them nothing they can act on.
 */
internal fun TunePlayerViewModel.describeResolveFailure(error: Exception): String {
    val res = when (error) {
        is StreamResolveException -> when (error.reason) {
            StreamResolveException.Reason.UNAVAILABLE -> R.string.error_stream_unavailable
            StreamResolveException.Reason.AGE_RESTRICTED -> R.string.error_stream_age_restricted
            StreamResolveException.Reason.EXPLICIT_UNSUPPORTED -> R.string.error_stream_explicit
            StreamResolveException.Reason.NO_MUSIC_VIDEO -> R.string.error_stream_no_music_video
            StreamResolveException.Reason.NETWORK -> R.string.error_stream_network
            else -> R.string.error_resolve_failed
        }
        else -> R.string.error_resolve_failed
    }
    return app.getString(res)
}

/** Inserts [track] directly after the current item, or appends when nothing plays. */
internal fun TunePlayerViewModel.playNext(track: Track) {
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

internal fun TunePlayerViewModel.playPrevious() {
    val prevIndex = uiState.queueIndex - 1
    if (prevIndex in uiState.playbackQueue.indices) {
        playFromQueue(uiState.playbackQueue, prevIndex)
    }
}

/**
 * Plays an already-resolved track.
 *
 * Updates the player item at [TunePlayerUiState.queueIndex] in place when a
 * queue is loaded, so shuffle/repeat/auto-advance keep working. Falls back
 * to a single item when no queue exists.
 */
internal fun TunePlayerViewModel.playResolved(
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
        activeLyricRows = emptyList(),
        lyricsSource = null,
        isLoadingLyrics = false,
    )
    recordHistory(track)
    prefetchLyrics(track)
    publishWidgetState()
}

/**
 * Pulls codec/bitrate from the player's selected audio track.
 *
 * Formats reported by Media3 are authoritative when present; a resolver
 * estimate is kept as a fallback because WebM/Opus often omits bitrate.
 */
internal fun TunePlayerViewModel.updateAudioInfo(tracks: Tracks) {
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
internal fun TunePlayerViewModel.audioInfoFromTracks(tracks: Tracks): AudioInfo? {
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

internal fun TunePlayerViewModel.buildMediaItem(
    track: Track,
    uri: String,
    mimeType: String?,
    requestHeaders: Map<String, String> = emptyMap(),
): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(track.title)
        .setArtist(track.artist)
        .setAlbumTitle(track.album)
        .setArtworkUri(track.artworkUri?.let(Uri::parse))
        // The playback service plans crossfade length against the incoming
        // track's real duration, and cannot read that until it prepares.
        .setExtras(
            Bundle().apply { putLong(PlaybackKeys.EXTRA_TRACK_DURATION_MS, track.durationMs) },
        )
        .build()
    return MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(uri)
        .setMediaMetadata(metadata)
        // A streaming URL is signed against the headers it was minted with,
        // so they travel with the item. Without them the player fetches the
        // URL under its own default identity and is refused.
        .apply { mimeType?.let(::setMimeType) }
        .build()
}

/** Stream URIs are short-lived; cap the cache and let it expire with the process. */
internal fun TunePlayerViewModel.cacheResolvedUri(trackId: String, uri: String) {
    // LinkedHashMap is access-ordered; insertion/read order eviction is the
    // map's job now (removeEldestEntry), so this is just a put.
    resolvedUris[trackId] = uri
}

internal fun TunePlayerViewModel.recordHistory(track: Track) {
    favoriteStore.recordPlay(track.id)
    playStatsStore.record(track.id)
    // Room mirror: events log + month-bucketed play counts.
    // Seed the song row first so event.songId FK target exists for any track
    // reached via direct play/queue paths (not just PlaylistController).
    viewModelScope.launch {
        runCatching {
            val dao = app.database.dao()
            dao.upsertSong(com.tuneplayer.app.data.db.SongEntity.fromTrack(track))
            val now = System.currentTimeMillis()
            dao.insertEvent(
                com.tuneplayer.app.data.db.EventEntity(songId = track.id, timestamp = now, playTime = 0),
            )
            val today = java.time.LocalDate.now()
            dao.incrementPlayCount(track.id, today.year, today.monthValue)
        }.onFailure { LogCatcher.e(TAG, "recordHistory db write failed", it) }
    }
    // Cheap: history is capped at 100 ids, and the tab must not go stale.
    refreshHistoryTracks()
}

/**
 * Builds the queue [play] starts from when the user taps a song.
 *
 * An already-populated queue is kept and the track is inserted after the
 * current item: replacing it would drop everything the user queued, and
 * [playResolved] would then fall back to a single-item player. Only an
 * empty or single-track queue is seeded from the library, starting at the
 * tapped song and wrapping around, so playback has somewhere to go.
 *
 * A tap is a request to play that song now, not to enqueue it, so a track
 * already in the queue is moved up rather than inserted a second time.
 * Without that, tapping library rows grows the queue by one per tap with
 * no ceiling — the queue fills with duplicates of what is already there.
 */
internal fun TunePlayerViewModel.autoQueueSeed(track: Track): List<Track> {
    val queue = uiState.playbackQueue
    if (queue.size > 1) {
        val existing = queue.indexOfFirst { it.id == track.id }
        if (existing >= 0) {
            // Already queued: drop it from where it sits and re-insert just
            // after the current item, so it plays next without duplicating.
            val withoutTrack = queue.filterIndexed { i, _ -> i != existing }
            val playingId = queue.getOrNull(uiState.queueIndex)?.id
            val currentIndex = playingId
                ?.let { id -> withoutTrack.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: uiState.queueIndex.coerceIn(0, withoutTrack.lastIndex)
            val insertAt = (currentIndex + 1).coerceAtMost(withoutTrack.size)
            return withoutTrack.toMutableList().apply { add(insertAt, track) }
        }
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

/**
 * Mirrors ExoPlayer's own auto-advance (shuffle, repeat, next track) into uiState.
 *
 * The item is identified by media id, never by index: with shuffle enabled
 * the player's indices address the *shuffled* timeline, so mapping a player
 * index back through the queue order silently points at the wrong track.
 */
internal fun TunePlayerViewModel.syncQueueIndexFromPlayer() {
    val player = controller ?: return
    val mediaId = player.currentMediaItem?.mediaId ?: return
    val index = uiState.playbackQueue.indexOfFirst { it.id == mediaId }
    if (index >= 0 && index != uiState.queueIndex) {
        val track = uiState.playbackQueue[index]
        uiState = uiState.copy(queueIndex = index, currentTrack = track, audioInfo = null)
        clearLyricsState()
        recordHistory(track)
        prefetchLyrics(track)
        publishWidgetState()
    }
}

internal fun TunePlayerViewModel.toggleShuffle() {
    val player = controller ?: return
    val enabled = !player.shuffleModeEnabled
    player.shuffleModeEnabled = enabled
    uiState = uiState.copy(isShuffleEnabled = enabled)
    // Media3 reorders its own timeline when shuffle turns on; the current
    // item keeps playing, so re-sync the queue pointer to it.
    syncQueueIndexFromPlayer()
}

internal fun TunePlayerViewModel.cycleRepeatMode() {
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
internal fun TunePlayerViewModel.cyclePlaybackSpeed() {
    val player = controller ?: return
    val ladder = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val current = player.playbackParameters.speed
    val next = ladder.firstOrNull { it > current + 0.01f } ?: ladder.first()
    player.setPlaybackSpeed(next)
    uiState = uiState.copy(playbackSpeed = next)
}

/** Sets an exact speed; the menu offers musical steps, the API clamps the rest. */
internal fun TunePlayerViewModel.setPlaybackSpeed(speed: Float) {
    val player = controller ?: return
    val clamped = speed.coerceIn(0.25f, 3.0f)
    player.setPlaybackSpeed(clamped)
    uiState = uiState.copy(playbackSpeed = clamped)
}

internal fun TunePlayerViewModel.clearError() {
    uiState = uiState.copy(error = null)
}
