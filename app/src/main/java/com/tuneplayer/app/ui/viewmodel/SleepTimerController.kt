package com.tuneplayer.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.tuneplayer.app.ui.TunePlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tuneplayer.app.ui.DEFAULT_SLEEP_MINUTES
import com.tuneplayer.app.ui.TunePlayerViewModel.Companion.END_TOLERANCE_MS

/**
 * Sleep timer is wall-clock: it counts down regardless of pause or track changes.
 *
 * When "finish current track" is armed the wait happens on the player's
 * own end-of-media event instead of a [delay], so pausing does not burn
 * the remaining wall-clock budget.
 */
internal fun TunePlayerViewModel.startSleepTimer() {
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

internal fun TunePlayerViewModel.cancelSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    sleepTimerFinishTrack = false
    uiState = uiState.copy(isSleepTimerActive = false)
}

/** Selecting a duration starts the countdown immediately, disarming finish-track. */
internal fun TunePlayerViewModel.setSleepDuration(minutes: Int) {
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
internal fun TunePlayerViewModel.setSleepFinishTrack() {
    sleepTimerFinishTrack = true
    uiState = uiState.copy(
        sleepDurationMillis = 0,
        isSleepTimerActive = true,
        sleepFinishTrack = true,
    )
    startSleepTimer()
}

internal fun TunePlayerViewModel.toggleSleepTimer() {
    if (uiState.isSleepTimerActive) {
        cancelSleepTimer()
    } else {
        setSleepDuration(DEFAULT_SLEEP_MINUTES)
    }
}
