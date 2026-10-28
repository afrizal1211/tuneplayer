package com.tuneplayer.app.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.tuneplayer.app.R
import com.tuneplayer.app.data.discord.DiscordAssetRegistrar
import com.tuneplayer.app.data.discord.DiscordHoldContext
import com.tuneplayer.app.data.discord.DiscordPresenceDecision
import com.tuneplayer.app.data.discord.DiscordPresenceInputs
import com.tuneplayer.app.data.discord.LastAppliedVisiblePresence
import com.tuneplayer.app.data.discord.derivePlaybackSemanticState
import com.tuneplayer.app.data.discord.deriveRawDiscordPresenceDecision
import com.tuneplayer.app.data.discord.resolveDiscordPresenceDecision
import com.tuneplayer.app.data.model.Track
import com.tuneplayer.app.ui.TunePlayerViewModel
import com.tuneplayer.app.widget.NowPlayingWidget
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.tuneplayer.app.ui.DISCORD_HOLD_TIMEOUT_MS
import com.tuneplayer.app.ui.TunePlayerViewModel.Companion.MAX_DISCORD_TEXT

internal fun TunePlayerViewModel.setDiscordRpcEnabled(enabled: Boolean) {
    app.settingsStore.discordRpcEnabled = enabled
    uiState = uiState.copy(discordRpcEnabled = enabled)
    app.syncDiscordRpc()
    viewModelScope.launch {
        runCatching { updateDiscordPresence() }
            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
    }
}

/** Keep or drop the card while paused, independent of the enable toggle. */
internal fun TunePlayerViewModel.setDiscordShowWhenPaused(show: Boolean) {
    app.settingsStore.discordShowWhenPaused = show
    uiState = uiState.copy(discordShowWhenPaused = show)
    viewModelScope.launch {
        runCatching { updateDiscordPresence() }
            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
    }
}

/** Saving a token also opts in, matching the single dialog that collects it. */
internal fun TunePlayerViewModel.saveDiscordToken(token: String) {
    val trimmed = token.trim().removePrefix("Bearer ").trim()
    if (trimmed.isEmpty()) return
    app.settingsStore.discordToken = trimmed
    app.settingsStore.discordRpcEnabled = true
    uiState = uiState.copy(discordRpcEnabled = true, discordTokenSaved = true)
    app.syncDiscordRpc()
    viewModelScope.launch {
        runCatching { updateDiscordPresence() }
            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
    }
}

internal fun TunePlayerViewModel.removeDiscordToken() {
    app.settingsStore.clearDiscordToken()
    uiState = uiState.copy(
        discordRpcEnabled = false,
        discordTokenSaved = false,
        discordConnected = false,
    )
    app.syncDiscordRpc()
}

/**
 * Stores the application id that owns the presence artwork.
 *
 * Cached registrations are dropped because the asset namespace is per
 * application: an asset registered under the old id is meaningless to the
 * new one.
 */
internal fun TunePlayerViewModel.saveDiscordApplicationId(applicationId: String) {
    val digits = applicationId.filter(Char::isDigit)
    if (digits.isBlank()) return
    app.settingsStore.discordApplicationId = digits
    DiscordAssetRegistrar.invalidate()
    uiState = uiState.copy(discordApplicationId = digits)
    viewModelScope.launch {
        runCatching { updateDiscordPresence() }
            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
    }
}

internal fun TunePlayerViewModel.removeDiscordApplicationId() {
    app.settingsStore.discordApplicationId = null
    DiscordAssetRegistrar.invalidate()
    uiState = uiState.copy(discordApplicationId = null)
    viewModelScope.launch {
        runCatching { updateDiscordPresence() }
            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
    }
}

/**
 * Applies the playback snapshot to the Discord card.
 *
 * The decision comes from [DiscordPresencePolicy]; buffering holds the last
 * visible card rather than flickering the presence off and back on, and a
 * stalled stream falls back to a cleared card.
 */
internal suspend fun TunePlayerViewModel.updateDiscordPresence() {
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

internal suspend fun TunePlayerViewModel.buildDiscordActivity(track: Track, isPaused: Boolean): JSONObject {
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

    // Artwork needs a registered application id. Discord drops `assets`
    // outright when `application_id` is absent, so an app id is never
    // guessed; without one the card still shows title, artist and
    // timestamps, just without the sleeve.
    val applicationId = app.settingsStore.discordApplicationId
    val token = app.settingsStore.discordToken
    val artworkUrl = track.artworkUri?.toString()?.takeIf {
        it.startsWith("http://") || it.startsWith("https://")
    }
    val largeImage = if (applicationId != null && token != null && artworkUrl != null) {
        DiscordAssetRegistrar.resolveImage(applicationId, token, artworkUrl)
    } else {
        null
    }
    // Both text fields link to the track on YouTube Music, which is what
    // makes the card clickable. Local tracks have no video id, so they
    // stay plain text rather than linking somewhere wrong.
    val trackUrl = track.videoId
        ?.takeIf { it.isNotBlank() && !track.isLocal }
        ?.let { "https://music.youtube.com/watch?v=$it" }

    return JSONObject().apply {
        put("name", appName)
        put("type", 2) // Listening
        put("details", title)
        put("state", stateText)
        put("status_display_type", 2) // Prefer the track title in compact surfaces.
        if (applicationId != null) {
            put("application_id", applicationId)
        }
        if (largeImage != null) {
            put("assets", JSONObject().apply {
                put("large_image", largeImage)
                put("large_text", title)
            })
        }
        if (trackUrl != null) {
            put("details_url", trackUrl)
        }
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

internal fun TunePlayerViewModel.publishWidgetState() {
    val track = uiState.currentTrack
    NowPlayingWidget.publish(app, track?.title, track?.artist, uiState.isPlaying)
    viewModelScope.launch {
        runCatching { updateDiscordPresence() }
            .onFailure { LogCatcher.w(TAG, "Discord presence failed", it) }
    }
}
