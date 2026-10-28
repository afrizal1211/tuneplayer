package com.tuneplayer.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.tuneplayer.app.data.lyrics.LyricsLookup
import com.tuneplayer.app.data.lyrics.LyricsParser
import com.tuneplayer.app.data.lyrics.LyricsSource
import com.tuneplayer.app.data.lyrics.activeLineIndex
import com.tuneplayer.app.data.lyrics.activeLyricRows
import com.tuneplayer.app.data.lyrics.activeWordIndex
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.ui.TunePlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.tuneplayer.app.ui.TunePlayerViewModel.Companion.TAG

/** Positive values delay lyrics; negative values show them earlier. */
internal fun TunePlayerViewModel.setLyricsOffsetMs(offsetMs: Long) {
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
        activeLyricRows = activeLyricRows(lines, (uiState.positionMs - clamped).coerceAtLeast(0L)),
    )
}

internal fun TunePlayerViewModel.loadLyrics() {
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

internal fun TunePlayerViewModel.refreshLyrics() {
    val track = uiState.currentTrack ?: return
    fetchLyrics(track, showLoading = true, force = true)
}

/** Warms the cache so downloaded tracks still show lyrics without a network. */
internal fun TunePlayerViewModel.prefetchLyrics(track: Track) {
    val cached = app.lyricsCache.get(track.id)
    if (cached != null) {
        if (uiState.currentTrack?.id == track.id && uiState.lyrics == null) {
            applyLyrics(track.id, cached)
        }
        return
    }
    fetchLyrics(track, showLoading = false)
}

internal fun TunePlayerViewModel.fetchLyrics(track: Track, showLoading: Boolean, force: Boolean = false) {
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
            activeLyricRows = emptyList(),
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
                        activeLyricRows = emptyList(),
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

internal suspend fun TunePlayerViewModel.resolveLyrics(track: Track): LyricsLookup? {
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

internal fun TunePlayerViewModel.applyLyrics(trackId: String, lookup: LyricsLookup) {
    if (uiState.currentTrack?.id != trackId) return
    val synced = LyricsParser.parse(lookup.text)
    val position = (uiState.positionMs - uiState.lyricsOffsetMs).coerceAtLeast(0L)
    val active = if (synced.isEmpty()) -1 else activeLineIndex(synced, position)
    val rows = activeLyricRows(synced, position)
    val word = if (active in synced.indices) activeWordIndex(synced[active], position) else -1
    uiState = uiState.copy(
        isLoadingLyrics = false,
        lyrics = lookup.text,
        syncedLyrics = synced,
        activeLyricIndex = active,
        activeWordIndex = word,
        activeLyricRows = rows,
        lyricsSource = lookup.source,
    )
}

internal fun TunePlayerViewModel.clearLyricsState() {
    lyricsJob?.cancel()
    lyricsJob = null
    lyricsTrackId = null
    uiState = uiState.copy(
        isLoadingLyrics = false,
        lyrics = null,
        syncedLyrics = emptyList(),
        activeLyricIndex = -1,
        activeWordIndex = -1,
        activeLyricRows = emptyList(),
        lyricsSource = null,
    )
}
