package com.tuneplayer.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.tuneplayer.app.ui.graphics.Swatch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.tuneplayer.app.ui.graphics.ColorUtils
import com.tuneplayer.app.ui.graphics.forPixelAccess
import com.tuneplayer.app.ui.graphics.paletteSwatches
import com.tuneplayer.app.ui.graphics.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * The colours a record paints in, as a finished set rather than a raw swatch.
 *
 * Rather than handing callers extracted colours and letting each of them
 * guess, this is the finished set: a page tint, an accent that is legible *on*
 * that tint, and the two text colours and hairline that go with them.
 *
 * Every value is theme-aware. The same sleeve yields a near-black tint in dark
 * mode and a pale wash of the same hue in light mode, which is the only way a
 * surface stays readable when the app's theme disagrees with the artwork's.
 *
 * Adapted from BitChord's ArtworkPalette (Apache-2.0) — the key-colour and
 * status-scrim paths live in surfaces this app does not have and are left out;
 * the seeding, quantising and HSL shaping are the original's, unaltered.
 */
@Immutable
data class ArtworkPalette(
    /** The surface's background wash. */
    val background: Color,
    /**
     * The colour the artwork's own bottom edge blurs down to.
     *
     * A blur wide enough to lose the picture leaves the mean of what it
     * sampled, so a surface that starts from this colour where the artwork
     * stops reads as that blur carrying on rather than as a second surface
     * beginning. Lighter than [background].
     */
    val wash: Color,
    /** Fill for the glass buttons and chips that sit on [background]. */
    val elevated: Color,
    /** The artwork's own colour, contrast-corrected — titles, icons, Play. */
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
)

/**
 * Pulls [ArtworkPalette] out of the artwork at [imageUrl].
 *
 * Artwork that has already been read once is tinted on the very first frame,
 * off [seedCache] — a sheet opened from a card it shares a cover with, or a
 * card opened twice, has nothing to wait for and nothing to fade. Only a
 * sleeve genuinely being seen for the first time starts from the theme's own
 * colours and warms into the artwork's, so it never flashes a placeholder
 * tint.
 */
@Composable
fun rememberArtworkPalette(
    imageUrl: String?,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
): ArtworkPalette {
    val scheme = MaterialTheme.colorScheme
    val context = LocalPlatformContext.current
    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(seedCache::get)) }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(PALETTE_PX)
            .forPixelAccess()
            .build()
        val result = runCatching {
            SingletonImageLoader.get(context).execute(request)
        }.getOrNull() as? SuccessResult ?: return@LaunchedEffect
        val bitmap = runCatching { result.image.toImageBitmap() }.getOrNull() ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { seedOf(bitmap) } ?: return@LaunchedEffect
        seedCache[imageUrl] = found
        seed = found
    }
    // Whether the colours were there from the first frame. If they were, there
    // is nothing to crossfade *from* and animating would only put a delay in
    // front of a surface that could already be right.
    val knownUpFront = remember(imageUrl) { seed != null }

    val target = seed?.toPalette(dark) ?: ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outline,
    )

    val spec: AnimationSpec<Color> = if (knownUpFront) snap() else tween(TINT_FADE_MS)
    return ArtworkPalette(
        background = animateColorAsState(target.background, spec, label = "tintBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "tintWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "tintElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "tintAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "tintOn").value,
        onBackgroundVariant = animateColorAsState(
            target.onBackgroundVariant, spec, label = "tintOnVariant",
        ).value,
        divider = animateColorAsState(target.divider, spec, label = "tintDivider").value,
    )
}

/**
 * Colours already read, keyed by artwork URL.
 *
 * Reading them again costs a decode and a quantise for an answer that cannot
 * have changed — the artwork at a URL is the artwork at that URL. Access is
 * from composition and from the resumption of [rememberArtworkPalette]'s
 * effect, both on the main thread, so it needs no locking of its own.
 */
private val seedCache = object : LinkedHashMap<String, Seed>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, Seed>) = size > SEED_CACHE_ENTRIES
}

/** Deep enough to cover a session's browsing without holding a screenful of colours. */
private const val SEED_CACHE_ENTRIES = 128

private const val PALETTE_PX = 128

/** Short: this is a surface settling into its colour, not an effect in itself. */
private const val TINT_FADE_MS = 260

/**
 * The raw artwork colours: what the page is mostly made of, its brightest
 * note, and what its bottom edge averages out to.
 */
private data class Seed(
    val dominant: Color,
    val vibrant: Color,
    val edge: Color,
)

private fun seedOf(bitmap: ImageBitmap): Seed? {
    val decoded = runCatching { paletteSwatches(bitmap, SWATCH_COUNT, true) }.getOrNull()
    val all = decoded ?: return null
    if (all.isEmpty()) return null
    val accentCandidates = runCatching { paletteSwatches(bitmap, SWATCH_COUNT, false) }.getOrNull()?.ifEmpty { all } ?: all

    val dominant = all.maxBy { it.population } ?: return null
    val vibrant: Swatch? = accentCandidates.maxBy { swatch: Swatch ->
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
        hsl[1] * sqrt(swatch.population.toFloat())
    }
    val edgeColor = runCatching { bitmap.bottomEdgeColor() }.getOrNull() ?: Color(0xFF000000)
    return Seed(
        dominant = Color(dominant.rgb),
        vibrant = vibrant?.let { s: Swatch -> Color(s.rgb) } ?: Color(dominant.rgb),
        edge = edgeColor,
    )
}

private const val SWATCH_COUNT = 24

/**
 * The mean of the artwork's bottom band — what a blur wide enough to lose the
 * picture leaves behind at that edge.
 *
 * A flat mean rather than a quantised swatch on purpose: a blur has no notion
 * of which colour is *important*, and the page under the artwork has to match
 * what the blur actually produced, not what the picture is about.
 */
private fun ImageBitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    readPixels(pixels, startX = 0, startY = height - band, width = width, height = band)

    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size.coerceAtLeast(1)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}

/** How much of the artwork's height the edge colour is read from. */
private const val EDGE_BAND = 0.18f

private fun Seed.toPalette(dark: Boolean): ArtworkPalette = if (dark) {
    ArtworkPalette(
        // Deep enough that white body text clears contrast on any sleeve, but
        // not so deep the hue is gone — the whole point is that the page is
        // recognisably *this* record's colour.
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.13f },
        ),
        // Follows the edge's own brightness within a band that stays clear of
        // white body text at the top and of [background] at the bottom: a
        // sleeve that ends dark hands over almost invisibly, one that ends
        // bright leaves a page that is visibly lit from under the artwork.
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.18f, maximum = 0.58f) },
            lightness = { it.coerceIn(0.14f, 0.24f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.22f },
        ),
        accent = vibrant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.55f, maximum = 1f) },
            lightness = { it.coerceIn(0.62f, 0.78f) },
        ),
        onBackground = Color.White,
        // A pale tint of the page's own hue rather than a translucent white —
        // Apple Music's secondary lines are the sleeve's colour, lifted, so a
        // blue record's credits read light blue on its blue page. Bright
        // enough that it still clears contrast on [background].
        onBackgroundVariant = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.22f, maximum = 0.50f) },
            lightness = { 0.80f },
        ),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    ArtworkPalette(
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.91f },
        ),
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.12f, maximum = 0.46f) },
            lightness = { it.coerceIn(0.78f, 0.90f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.83f },
        ),
        accent = vibrant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.55f, maximum = 1f) },
            lightness = { it.coerceIn(0.30f, 0.44f) },
        ),
        onBackground = Color.Black,
        // The same hue taken down rather than up, for the pale page.
        onBackgroundVariant = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.22f, maximum = 0.55f) },
            lightness = { 0.34f },
        ),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

/**
 * Keeps neutral artwork neutral instead of inventing a hue for it.
 *
 * HSL represents grey with hue zero. Raising that grey to a saturation floor
 * therefore does not make it "more colourful"; it manufactures red, which
 * becomes brown/maroon once the page lightness is lowered. A small real amount
 * of colour is kept as-is, while an unmistakably chromatic swatch can still be
 * strengthened enough to make controls legible and the page recognisable.
 */
internal fun adaptedArtworkSaturation(source: Float, minimum: Float, maximum: Float): Float {
    val saturation = source.coerceIn(0f, 1f)
    return if (saturation < CHROMATIC_SATURATION_THRESHOLD) {
        saturation
    } else {
        saturation.coerceIn(minimum, maximum)
    }
}

/** Below this, boosting saturation makes quantisation noise visible as a tint. */
private const val CHROMATIC_SATURATION_THRESHOLD = 0.12f

private fun Color.withHsl(
    saturation: (Float) -> Float = { it },
    lightness: (Float) -> Float = { it },
): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Perceived brightness, used only to tell a dark theme from a light one. */
private fun Color.luminance(): Float = ColorUtils.calculateLuminance(toArgb()).toFloat()
