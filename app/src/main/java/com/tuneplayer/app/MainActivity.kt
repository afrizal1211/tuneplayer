package com.tuneplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.tuneplayer.app.ui.ThemeController
import com.tuneplayer.app.ui.TunePlayerScreen
import com.tuneplayer.app.ui.theme.TunePlayerTheme

class MainActivity : ComponentActivity() {

    private val initialTab = mutableStateOf(0)

    private fun rememberTab(intent: android.content.Intent?) {
        initialTab.value = tabForIntent(intent)
    }

    private fun tabForIntent(intent: android.content.Intent?): Int =
        when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_YOUTUBE -> TAB_INDEX_YOUTUBE
            TAB_QUEUE -> TAB_INDEX_QUEUE
            TAB_LIKED -> TAB_INDEX_LIKED
            else -> TAB_INDEX_LIBRARY
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
                val tab by initialTab
                TunePlayerScreen(initialTab = tab)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "com.tuneplayer.app.extra.OPEN_TAB"
        const val TAB_YOUTUBE = "youtube"
        const val TAB_QUEUE = "queue"
        const val TAB_LIKED = "liked"
        const val TAB_LIBRARY = "library"

        const val TAB_INDEX_LIBRARY = 0
        const val TAB_INDEX_YOUTUBE = 1
        const val TAB_INDEX_QUEUE = 2
        const val TAB_INDEX_LIKED = 3
    }
}
