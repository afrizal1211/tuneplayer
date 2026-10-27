package com.tuneplayer.app.ui.theme

import android.graphics.Color as AndroidColor
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

    val baseScheme = when {
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

    // Manual accent override: repaint primary + derived roles from the hex.
    val colorScheme = ThemeController.accent?.let { hex ->
        runCatching { Color(AndroidColor.parseColor(hex)) }
            .getOrNull()
            ?.let { accent ->
                baseScheme.copy(
                    primary = accent,
                    secondary = accent.copy(alpha = 0.7f),
                    tertiary = accent.copy(alpha = 0.5f),
                    primaryContainer = accent.copy(alpha = 0.18f),
                    onPrimary = if (accentLuminance(accent) > 0.5f) Color.Black else Color.White,
                )
            }
    } ?: baseScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** Relative luminance of a color, 0 (black) to 1 (white). */
private fun accentLuminance(color: Color): Float {
    val r = color.red * 0.2126f
    val g = color.green * 0.7152f
    val b = color.blue * 0.0722f
    return r + g + b
}
