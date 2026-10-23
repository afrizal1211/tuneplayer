package com.tuneplayer.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide theme selection. Recomposes every reader when any flag changes.
 * Persistence lives in [com.tuneplayer.app.data.prefs.SettingsStore].
 */
object ThemeController {
    var theme by mutableStateOf(com.tuneplayer.app.data.prefs.AppTheme.System)
        private set

    /** Wallpaper-derived palette (Android 12+). Ignored on older releases. */
    var dynamicColor by mutableStateOf(true)
        private set

    /** Pure black backgrounds instead of Material's elevated grey. */
    var pureBlack by mutableStateOf(false)
        private set

    fun set(theme: com.tuneplayer.app.data.prefs.AppTheme) {
        this.theme = theme
    }

    fun setDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
    }

    fun setPureBlack(enabled: Boolean) {
        pureBlack = enabled
    }
}
