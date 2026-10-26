package com.tuneplayer.app.ui.viewmodel

import android.webkit.CookieManager
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.YouTubeAudioQuality
import com.tuneplayer.app.data.prefs.AppTheme
import com.tuneplayer.app.log.LogCatcher
import com.tuneplayer.app.ui.TunePlayerViewModel

internal fun TunePlayerViewModel.setKeepScreenOn(enabled: Boolean) {
    app.settingsStore.keepScreenOn = enabled
    uiState = uiState.copy(keepScreenOn = enabled)
}

/** Persisted for the service; the prefs listener applies it live. */
internal fun TunePlayerViewModel.setSkipSilence(enabled: Boolean) {
    app.settingsStore.skipSilence = enabled
    uiState = uiState.copy(skipSilence = enabled)
}

/** Persisted for the service, which re-arms the blend on the next track. */
internal fun TunePlayerViewModel.setCrossfade(enabled: Boolean, durationSeconds: Float) {
    app.settingsStore.crossfadeEnabled = enabled
    app.settingsStore.crossfadeDurationSeconds = durationSeconds
    uiState = uiState.copy(crossfadeEnabled = enabled, crossfadeDurationSeconds = durationSeconds)
}

internal fun TunePlayerViewModel.setAutomixEnabled(enabled: Boolean) {
    app.settingsStore.automixEnabled = enabled
    uiState = uiState.copy(
        automixEnabled = enabled,
        crossfadeEnabled = enabled,
        crossfadeDurationSeconds = app.settingsStore.crossfadeDurationSeconds,
    )
}

internal fun TunePlayerViewModel.setHapticsEnabled(enabled: Boolean) {
    app.settingsStore.hapticsEnabled = enabled
    uiState = uiState.copy(hapticsEnabled = enabled)
}

internal fun TunePlayerViewModel.toggleGridLayout() {
    val next = !uiState.gridLayout
    app.settingsStore.gridLayout = next
    uiState = uiState.copy(gridLayout = next)
}

internal fun TunePlayerViewModel.setTheme(theme: AppTheme) {
    uiState = uiState.copy(theme = theme)
    ThemeController.set(theme)
    app.settingsStore.theme = theme
}

internal fun TunePlayerViewModel.setDynamicColor(enabled: Boolean) {
    app.settingsStore.dynamicColor = enabled
    ThemeController.updateDynamicColor(enabled)
    uiState = uiState.copy(dynamicColor = enabled)
}

internal fun TunePlayerViewModel.setPureBlack(enabled: Boolean) {
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
internal fun TunePlayerViewModel.reloadFromStores() {
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
    discordApplicationId = settings.discordApplicationId,
        discordTokenSaved = settings.discordToken != null,
    )
    app.syncDiscordRpc()
    refreshHistoryTracks()
    LogCatcher.d(TAG, "reloaded stores after restore")
}

internal fun TunePlayerViewModel.updateEqualizer(enabled: Boolean, bass: Int, virtualizer: Int) {
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
internal fun TunePlayerViewModel.connectYouTubeAccount(cookie: String) {
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

internal fun TunePlayerViewModel.disconnectYouTubeAccount() {
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

internal fun TunePlayerViewModel.setYouTubeAudioQuality(quality: YouTubeAudioQuality) {
    app.settingsStore.youtubeAudioQuality = quality
    // Cached stream URLs were resolved with the previous format.
    resolvedUris.clear()
    uiState = uiState.copy(youtubeAudioQuality = quality)
}

/**
 * Fits saved levels onto the five UI control points.
 *
 * Older builds stored whatever band count the device reported, so a
 * device switch could leave a different length behind. The service
 * resamples the five points onto the live device; the UI just needs a
 * stable count to render.
 */
internal fun TunePlayerViewModel.normalizeEqBands(stored: List<Int>): List<Int> {
    val count = AudioEffects.UI_BAND_FREQS_HZ.size
    if (stored.isEmpty()) return List(count) { 0 }
    val fitted = if (stored.size == count) stored else EqPresets.resample(stored, count)
    return fitted.map { it.coerceIn(AudioEffects.UI_BAND_RANGE_MB) }
}

internal fun TunePlayerViewModel.setEqBand(index: Int, levelMb: Int) {
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
internal fun TunePlayerViewModel.setEqPreset(preset: Int) {
    val count = uiState.eqBands.size.coerceAtLeast(uiState.eqBandFreqs.size)
    if (count == 0) return
    val shape = EqPresets.shapes.getOrNull(preset) ?: return
    val levels = EqPresets.resample(shape, count)
    app.settingsStore.eqBands = levels
    app.settingsStore.eqPreset = preset
    uiState = uiState.copy(eqBands = levels, eqPreset = preset)
    LogCatcher.d(TAG, "eq preset $preset over $count bands")
}
