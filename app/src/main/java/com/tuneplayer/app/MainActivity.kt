package com.tuneplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.tuneplayer.app.ui.ThemeController
import com.tuneplayer.app.ui.Screens
import com.tuneplayer.app.ui.TunePlayerScreen
import com.tuneplayer.app.ui.theme.TunePlayerTheme

class MainActivity : ComponentActivity() {

    /** Deep-link target route, consumed by the screen's initial navigation state. */
    private val initialRoute = mutableStateOf(Screens.Library.route)

    private fun rememberTab(intent: android.content.Intent?) {
        initialRoute.value = routeForIntent(intent)
    }


    private fun routeForIntent(intent: android.content.Intent?): String =
        when (intent?.getStringExtra(EXTRA_OPEN_TAB)) {
            TAB_YOUTUBE -> Screens.Search.route
            TAB_QUEUE -> Screens.Queue.route
            TAB_LIKED -> Screens.Liked.route
            else -> Screens.Library.route
        }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        rememberTab(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        rememberTab(intent)
        val settings = (application as TunePlayerApp).settingsStore
        ThemeController.set(settings.theme)
        ThemeController.updateDynamicColor(settings.dynamicColor)
        ThemeController.updatePureBlack(settings.pureBlack)
        setContent {
            TunePlayerTheme {
                TunePlayerScreen(initialRoute = initialRoute.value)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "com.tuneplayer.app.extra.OPEN_TAB"
        const val TAB_YOUTUBE = "youtube"
        const val TAB_QUEUE = "queue"
        const val TAB_LIKED = "liked"
        const val TAB_LIBRARY = "library"

    }
}
