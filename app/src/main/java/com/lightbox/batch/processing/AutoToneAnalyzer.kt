package com.lightbox.batch.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.lightbox.batch.model.PresetParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Analyzes JPEG image histograms to compute deterministic auto-correction
 * parameters. No ML/cloud required — pure statistical analysis.
 *
 * Computes:
 * - Exposure correction based on average luminance deviation from mid-gray
 * - Contrast via histogram spread (std deviation)
 * - Highlight/shadow recovery based on clipping percentage
 * - Saturation boost for undersaturated images
 */
object AutoToneAnalyzer {

    private const val ANALYSIS_MAX_DIM = 512 // Downsample for fast analysis
    private const val MID_GRAY = 128f
    private const val TARGET_MEAN = 120f // Slightly below mid-gray for pleasing look

    /**
     * Analyze a JPEG and return auto-correction PresetParameters.
     */
    suspend fun analyze(context: Context, uri: Uri): PresetParameters = withContext(Dispatchers.Default) {
        // Decode at reduced resolution for fast histogram
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return@withContext PresetParameters.AUTO_CORRECT

        try {
            computeAutoParameters(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun computeAutoParameters(bitmap: Bitmap): PresetParameters {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        // Build luminance histogram and collect stats
        val histogram = IntArray(256)
        var sumR = 0L; var sumG = 0L; var sumB = 0L
        var sumLum = 0L
        var sumSat = 0L

        val pixels = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val pixel = pixels[x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                sumR += r; sumG += g; sumB += b

                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
                histogram[lum]++
                sumLum += lum

                // Quick saturation estimate
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val sat = if (maxC > 0) ((maxC - minC) * 255 / maxC) else 0
                sumSat += sat
            }
        }

        val meanLum = sumLum.toFloat() / totalPixels
        val meanSat = sumSat.toFloat() / totalPixels

        // Compute std deviation of luminance
        var sumSqDiff = 0.0
        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val pixel = pixels[x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val diff = lum - meanLum
                sumSqDiff += diff * diff
            }
        }
        val stdDev = Math.sqrt(sumSqDiff / totalPixels).toFloat()

        // Count clipped highlights and shadows
        val shadowClip = (0..10).sumOf { histogram[it] }.toFloat() / totalPixels
        val highlightClip = (245..255).sumOf { histogram[it] }.toFloat() / totalPixels

        // --- Compute corrections ---

        // Exposure: correct toward target mean luminance
        val exposureCorrection = ((TARGET_MEAN - meanLum) / 160f).coerceIn(-0.35f, 0.45f)

        // Contrast: deliver pleasant, punchy dynamic range (+0.12 to +0.22)
        val contrastCorrection = if (stdDev < 50f) 0.22f else 0.15f

        // Highlight recovery: protect highlights from clipping
        val highlightCorrection = if (highlightClip > 0.01f) {
            -(highlightClip * 4f).coerceIn(0.10f, 0.35f)
        } else -0.10f

        // Shadow recovery: lift underexposed shadows smoothly
        val shadowCorrection = if (shadowClip > 0.01f) {
            (shadowClip * 4f).coerceIn(0.15f, 0.35f)
        } else 0.18f

        val whiteCorrection = 0.10f
        val blackCorrection = -0.08f

        // Saturation & Vibrance: enhance color vitality and skin tones
        val saturationCorrection = if (meanSat < 80f) 0.12f else 0.06f
        val vibranceCorrection = 0.18f

        return PresetParameters(
            name = "Auto Correct",
            exposure = exposureCorrection,
            contrast = contrastCorrection,
            highlights = highlightCorrection,
            shadows = shadowCorrection,
            whites = whiteCorrection,
            blacks = blackCorrection,
            saturation = saturationCorrection,
            vibrance = vibranceCorrection
        )
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val maxSide = maxOf(width, height)
        while (maxSide / sampleSize > ANALYSIS_MAX_DIM * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
