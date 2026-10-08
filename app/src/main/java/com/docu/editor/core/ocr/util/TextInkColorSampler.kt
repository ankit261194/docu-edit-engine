package com.docu.editor.core.ocr.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

object TextInkColorSampler {

    data class InkSampleResult(
        val dominantRgb: Int,
        val foregroundRatio: Float,
        val foregroundMask: BooleanArray,
        val cropWidth: Int,
        val cropHeight: Int
    )

    fun sampleInk(source: Bitmap, bounds: Rect): InkSampleResult {
        val left = max(0, bounds.left)
        val top = max(0, bounds.top)
        val right = min(source.width, bounds.right)
        val bottom = min(source.height, bounds.bottom)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return InkSampleResult(Color.BLACK, 0f, BooleanArray(0), 0, 0)
        }

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, left, top, width, height)

        val histogram = IntArray(256)
        val luminances = IntArray(pixels.size)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            luminances[i] = lum
            histogram[lum]++
        }

        val threshold = calculateOtsuThreshold(histogram, pixels.size)
        val countBelow = luminances.count { it <= threshold }
        val countAbove = luminances.size - countBelow
        val textIsDarker = countBelow <= countAbove

        val mask = BooleanArray(pixels.size)
        val foregroundColors = mutableListOf<Int>()
        for (i in pixels.indices) {
            val isForeground = if (textIsDarker) luminances[i] <= threshold else luminances[i] > threshold
            mask[i] = isForeground

            if (isForeground) {
                foregroundColors.add(pixels[i])
            }
        }

        val dominantColor = if (foregroundColors.isNotEmpty()) {
            // Check if there are authentic chromatic (colored ink) pixels (blue ballpoint pen, red stamp, green ink, etc.)
            val chromaticPixels = foregroundColors.filter { c ->
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val chroma = maxOf(r, g, b) - minOf(r, g, b)
                val isDistinctColor = (b > r + 20 && b > g + 15) || // Blue ink
                                      (r > g + 25 && r > b + 25) || // Red stamp
                                      (g > r + 20 && g > b + 20)    // Green ink
                chroma > 28 || (chroma > 20 && isDistinctColor)
            }

            val targetSamples = if (chromaticPixels.size >= maxOf(4, (foregroundColors.size * 0.12f).toInt())) {
                // Dominant colored ink: sort chromatic pixels by saturation/chroma and take the top 40%
                chromaticPixels.sortedByDescending { c ->
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    maxOf(r, g, b) - minOf(r, g, b)
                }.take(maxOf(1, (chromaticPixels.size * 0.40f).toInt()))
            } else {
                // Monochrome/grayscale ink (black, charcoal, pencil): sort by luminance and take purest 15% dark core pixels
                val sortedByLuma = foregroundColors.sortedBy { c ->
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                }
                val coreCount = (sortedByLuma.size * 0.15f).toInt().coerceAtLeast(1)
                sortedByLuma.take(coreCount)
            }

            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (c in targetSamples) {
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
            }
            Color.rgb(
                (sumR / targetSamples.size).toInt(),
                (sumG / targetSamples.size).toInt(),
                (sumB / targetSamples.size).toInt()
            )
        } else {
            Color.BLACK
        }

        val foregroundRatio = foregroundColors.size.toFloat() / max(1, pixels.size)

        return InkSampleResult(
            dominantRgb = dominantColor,
            foregroundRatio = foregroundRatio,
            foregroundMask = mask,
            cropWidth = width,
            cropHeight = height
        )
    }

    private fun calculateOtsuThreshold(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i]

        var sumB = 0.0
        var wB = 0
        var maxVariance = 0.0
        var threshold = 128

        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val betweenVariance = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (betweenVariance > maxVariance) {
                maxVariance = betweenVariance
                threshold = t
            }
        }
        return threshold
    }
}
