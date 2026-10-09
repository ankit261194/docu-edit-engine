package com.docu.editor.core.ocr.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Microscopic Ink & Lighting Sampling Engine.
 *
 * Samples authentic ink color, luminance, contrast, and color temperature
 * from the target word and neighboring letters on the same line to match
 * real-world smartphone camera lighting gradients and ink formulation.
 */
object TextInkColorSampler {

    data class InkSampleResult(
        val dominantRgb: Int,
        val foregroundRatio: Float,
        val foregroundMask: BooleanArray,
        val cropWidth: Int,
        val cropHeight: Int,
        val paperBackgroundRgb: Int = Color.WHITE,
        val contrastRatio: Float = 0.85f
    )

    fun sampleInk(source: Bitmap, bounds: Rect): InkSampleResult {
        return sampleInternal(source, bounds)
    }

    /**
     * Line-Level Neighboring Ink Consensus Sampling:
     * Samples ink from targetBounds AND immediate adjacent words on that line
     * to eliminate camera noise or short-word sampling errors.
     */
    fun sampleLineInk(
        source: Bitmap,
        targetBounds: Rect,
        neighborBounds: List<Rect> = emptyList()
    ): InkSampleResult {
        val targetResult = sampleInternal(source, targetBounds)
        if (neighborBounds.isEmpty()) return targetResult

        val neighborSamples = neighborBounds.mapNotNull { nb ->
            if (nb.width() > 6 && nb.height() > 6) {
                sampleInternal(source, nb)
            } else null
        }.filter { it.foregroundRatio > 0.08f }

        if (neighborSamples.isEmpty()) return targetResult

        // If target word had low ink confidence or was very short, rely heavily on line neighbors
        val targetConfidence = (targetResult.foregroundRatio * 4f).coerceIn(0.1f, 1.0f)
        val neighborColor = averageColors(neighborSamples.map { it.dominantRgb })

        val finalRgb = if (targetResult.foregroundRatio < 0.08f) {
            neighborColor
        } else {
            val targetWeight = (targetConfidence * 0.7f).coerceIn(0.25f, 0.75f)
            blendRgb(targetResult.dominantRgb, neighborColor, targetWeight)
        }

        val finalPaper = averageColors(listOf(targetResult.paperBackgroundRgb) + neighborSamples.map { it.paperBackgroundRgb })

        return targetResult.copy(
            dominantRgb = finalRgb,
            paperBackgroundRgb = finalPaper
        )
    }

    private fun sampleInternal(source: Bitmap, bounds: Rect): InkSampleResult {
        val left = max(0, bounds.left)
        val top = max(0, bounds.top)
        val right = min(source.width, bounds.right)
        val bottom = min(source.height, bounds.bottom)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return InkSampleResult(Color.BLACK, 0f, BooleanArray(0), 0, 0, Color.WHITE, 1.0f)
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
        val backgroundColors = mutableListOf<Int>()

        for (i in pixels.indices) {
            val isForeground = if (textIsDarker) luminances[i] <= threshold else luminances[i] > threshold
            mask[i] = isForeground

            if (isForeground) {
                foregroundColors.add(pixels[i])
            } else {
                backgroundColors.add(pixels[i])
            }
        }

        val dominantColor = if (foregroundColors.isNotEmpty()) {
            // Check for authentic chromatic (colored) ink (ballpoint blue, red stamp, green ink, violet seal)
            val chromaticPixels = foregroundColors.filter { c ->
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val chroma = maxOf(r, g, b) - minOf(r, g, b)
                val isDistinctColor = (b > r + 18 && b > g + 12) || // Blue ink
                    (r > g + 22 && r > b + 22) || // Red stamp
                    (g > r + 18 && g > b + 18)    // Green ink
                chroma > 25 || (chroma > 18 && isDistinctColor)
            }

            val targetSamples = if (chromaticPixels.size >= maxOf(4, (foregroundColors.size * 0.10f).toInt())) {
                chromaticPixels.sortedByDescending { c ->
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    maxOf(r, g, b) - minOf(r, g, b)
                }.take(maxOf(1, (chromaticPixels.size * 0.40f).toInt()))
            } else {
                // Monochrome/grayscale ink (charcoal, laser toner, pencil): sort by luminance
                // Sample interquartile core (10th to 45th percentile) to reject sensor noise pits and anti-aliased edge falloff
                val sortedByLuma = foregroundColors.sortedBy { c ->
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                }
                val startIdx = if (textIsDarker) {
                    (sortedByLuma.size * 0.10f).toInt().coerceIn(0, sortedByLuma.size - 1)
                } else {
                    (sortedByLuma.size * 0.55f).toInt().coerceIn(0, sortedByLuma.size - 1)
                }
                val endIdx = if (textIsDarker) {
                    (sortedByLuma.size * 0.45f).toInt().coerceIn(startIdx + 1, sortedByLuma.size)
                } else {
                    (sortedByLuma.size * 0.90f).toInt().coerceIn(startIdx + 1, sortedByLuma.size)
                }
                sortedByLuma.subList(startIdx, endIdx)
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
            Color.rgb(34, 36, 40)
        }

        val paperBgColor = if (backgroundColors.isNotEmpty()) {
            averageColors(backgroundColors)
        } else {
            Color.WHITE
        }

        val inkLuma = getLuma(dominantColor)
        val paperLuma = getLuma(paperBgColor).coerceAtLeast(1f)
        val contrastRatio = (paperLuma - inkLuma) / paperLuma

        val foregroundRatio = foregroundColors.size.toFloat() / max(1, pixels.size)

        return InkSampleResult(
            dominantRgb = dominantColor,
            foregroundRatio = foregroundRatio,
            foregroundMask = mask,
            cropWidth = width,
            cropHeight = height,
            paperBackgroundRgb = paperBgColor,
            contrastRatio = contrastRatio.coerceIn(0.1f, 1.0f)
        )
    }

    private fun getLuma(rgb: Int): Float {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun averageColors(colors: List<Int>): Int {
        if (colors.isEmpty()) return Color.BLACK
        var r = 0L
        var g = 0L
        var b = 0L
        for (c in colors) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val count = colors.size
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun blendRgb(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (((c1 shr 16) and 0xFF) * ratio + ((c2 shr 16) and 0xFF) * inv).toInt().coerceIn(0, 255)
        val g = (((c1 shr 8) and 0xFF) * ratio + ((c2 shr 8) and 0xFF) * inv).toInt().coerceIn(0, 255)
        val b = ((c1 and 0xFF) * ratio + (c2 and 0xFF) * inv).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
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
