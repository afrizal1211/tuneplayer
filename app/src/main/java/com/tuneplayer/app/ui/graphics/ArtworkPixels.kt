package com.tuneplayer.app.ui.graphics

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * Pixel access to artwork, for the backdrops and palettes that read covers
 * back colour by colour. This app only renders on Android, so the platform
 * hooks from BitChord's expect/actual pair stay folded in: a decoded Coil
 * image becomes a Compose bitmap and a CPU-readable request is asked for by
 * name.
 *
 * Adapted from BitChord (Apache-2.0).
 */

/** Asks Coil for a bitmap the CPU can read — a software bitmap on Android. */
fun ImageRequest.Builder.forPixelAccess(): ImageRequest.Builder = allowHardware(false)

/** The decoded image as a Compose bitmap. */
fun Image.toImageBitmap(): ImageBitmap = toBitmap().asImageBitmap()

/** An ARGB_8888 bitmap of [width] x [height] from packed ARGB [pixels]. */
fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

/** Every pixel of [image], packed ARGB, row by row. */
fun ImageBitmap.argbPixels(): IntArray =
    IntArray(width * height).also { readPixels(it) }

/** One colour the palette found, and how much of the image it covers. */
class Swatch(val rgb: Int, val population: Int)

/**
 * The colours androidx Palette would find in [image]: up to [maxColors]
 * swatches, with its default filter (near-black, near-white, the red I-line)
 * unless [clearFilters].
 */
fun paletteSwatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> {
    val builder = Palette.from(image.asAndroidBitmap()).maximumColorCount(maxColors)
    if (clearFilters) builder.clearFilters()
    return builder.generate().swatches.map { Swatch(it.rgb, it.population) }
}
