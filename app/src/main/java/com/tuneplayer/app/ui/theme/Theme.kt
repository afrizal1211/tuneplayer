package com.tuneplayer.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tuneplayer.app.data.prefs.AppTheme
import com.tuneplayer.app.ui.ThemeController

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
)

/**
 * True-black variant for OLED panels.
 *
 * Only the base surfaces are forced to black; elevated containers stay at
 * their Material values so cards and sheets keep a visible edge on black.
 */
private val PureBlackColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
)

@Composable
fun TunePlayerTheme(content: @Composable () -> Unit) {
    val darkTheme = when (ThemeController.theme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    val context = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        // Pure black wins over dynamic color: asking for OLED black and
        // getting a tinted near-black defeats the point.
        darkTheme && ThemeController.pureBlack -> PureBlackColors
        dynamicAvailable && ThemeController.dynamicColor && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicAvailable && ThemeController.dynamicColor ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
