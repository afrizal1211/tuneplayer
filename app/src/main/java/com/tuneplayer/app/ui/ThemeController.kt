package com.tuneplayer.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-wide theme selection. Recomposes every reader when [theme] changes.
 * Persistence lives in [com.tuneplayer.app.data.prefs.SettingsStore].
 */
object ThemeController {
    var theme by mutableStateOf(com.tuneplayer.app.data.prefs.AppTheme.System)
        private set

    fun set(theme: com.tuneplayer.app.data.prefs.AppTheme) {
        this.theme = theme
    }
}
