package com.tuneplayer.app.ui.theme

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Extracts a two-color gradient from artwork, without androidx.palette.
 *
 * A 32x32 thumbnail is sampled from the bitmap Coil already caches; the most
 * populous color becomes the top stop and the most *distant* hue becomes the
 * bottom stop. Distance matters more than population: a blue-and-white sleeve
 * should give blue and white, not blue and a slightly different blue.
 *
 * Returns null when no image can be loaded, so callers keep their static theme.
 */
object ArtworkGradient {

    suspend fun fromUri(uri: String?, context: android.content.Context): List<Color>? {
        if (uri.isNullOrBlank()) return null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { loadBitmap(uri, context) }.getOrNull()
        } ?: return null
        return colors(bitmap)
    }

    private suspend fun loadBitmap(uri: String, context: android.content.Context): Bitmap? {
        // A fresh loader per call is fine here: it borrows the app's shared
        // disk/memory cache, so the heavy work is still done once.
        val loader = runCatching { ImageLoader(context) }.getOrNull() ?: return null
        val request = ImageRequest.Builder(context)
            .data(Uri.parse(uri))
            // Small enough to be cheap, large enough to skip JPEG block noise.
            .size(64, 64)
            .build()
        val result = runCatching { loader.execute(request) }.getOrNull() as? SuccessResult
            ?: return null
        return runCatching { result.image.toBitmap() }.getOrNull()
    }

    /** Two-stop gradient from the most populous and most distant colors. */
    fun colors(bitmap: Bitmap): List<Color> {
        val thumb = downscale(bitmap, 32)
        val buckets = HashMap<Int, Int>()
        var opaque = 0
        for (y in 0 until thumb.height) {
            for (x in 0 until thumb.width) {
                val pixel = thumb.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 128) continue
                opaque++
                val key = quantize(pixel)
                buckets[key] = (buckets[key] ?: 0) + 1
            }
        }
        if (opaque == 0 || buckets.isEmpty()) return fallback()

        val first = buckets.maxByOrNull { it.value }?.key ?: return fallback()
        val second = buckets.entries
            .filter { it.key != first }
            .maxByOrNull { entry ->
                // Score by hue + luminance distance, weighted by population.
                val distance = colorDistance(first, entry.key)
                distance * (1 + entry.value / 64f)
            }?.key

        val top = Color(first)
        val bottom = second?.let(::Color) ?: top.darken()
        return listOf(top, bottom)
    }

    private fun downscale(source: Bitmap, size: Int): Bitmap {
        if (source.width <= size && source.height <= size) return source
        val scale = size.toFloat() / maxOf(source.width, source.height)
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /** Keeps 5 bits per channel so JPEG noise does not fragment the histogram. */
    private fun quantize(pixel: Int): Int {
        val r = android.graphics.Color.red(pixel) shr 3 shl 3
        val g = android.graphics.Color.green(pixel) shr 3 shl 3
        val b = android.graphics.Color.blue(pixel) shr 3 shl 3
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Rough perceptual distance: hue and luma, squared so extremes dominate. */
    private fun colorDistance(a: Int, b: Int): Float {
        val rA = android.graphics.Color.red(a).toFloat()
        val gA = android.graphics.Color.green(a).toFloat()
        val bA = android.graphics.Color.blue(a).toFloat()
        val rB = android.graphics.Color.red(b).toFloat()
        val gB = android.graphics.Color.green(b).toFloat()
        val bB = android.graphics.Color.blue(b).toFloat()
        val dr = rA - rB
        val dg = gA - gB
        val db = bA - bB
        val lumaA = 0.299f * rA + 0.587f * gA + 0.114f * bA
        val lumaB = 0.299f * rB + 0.587f * gB + 0.114f * bB
        val dl = lumaA - lumaB
        return abs(dr) + abs(dg) + abs(db) + abs(dl) * 1.5f
    }

    private fun fallback(): List<Color> = listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

private fun Color.darken(): Color = copy(
    red = red * 0.55f,
    green = green * 0.55f,
    blue = blue * 0.55f,
)
