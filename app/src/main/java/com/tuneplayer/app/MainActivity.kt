package com.tuneplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tuneplayer.app.ui.ThemeController
import com.tuneplayer.app.ui.TunePlayerScreen
import com.tuneplayer.app.ui.theme.TunePlayerTheme

class MainActivity : ComponentActivity() {

    private var requestedTab: Int? = null

    private fun initialTab(): Int = requestedTab ?: 0

    private fun rememberTab(intent: android.content.Intent?) {
        requestedTab = when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_YOUTUBE -> 1
            TAB_QUEUE -> 2
            else -> null
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        rememberTab(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        rememberTab(intent)
        ThemeController.set((application as TunePlayerApp).settingsStore.theme)
        setContent {
            TunePlayerTheme {
                TunePlayerScreen(initialTab = initialTab())
            }
        }
    }

    private companion object {
        const val EXTRA_OPEN_TAB = "com.tuneplayer.app.extra.OPEN_TAB"
        const val TAB_YOUTUBE = "youtube"
        const val TAB_QUEUE = "queue"
    }
}
