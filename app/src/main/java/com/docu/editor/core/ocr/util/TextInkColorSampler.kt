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
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var foregroundCount = 0

        for (i in pixels.indices) {
            val isForeground = if (textIsDarker) luminances[i] <= threshold else luminances[i] > threshold
            mask[i] = isForeground

            if (isForeground) {
                val c = pixels[i]
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
                foregroundCount++
            }
        }

        val dominantColor = if (foregroundCount > 0) {
            Color.rgb(
                (sumR / foregroundCount).toInt(),
                (sumG / foregroundCount).toInt(),
                (sumB / foregroundCount).toInt()
            )
        } else {
            Color.BLACK
        }

        val foregroundRatio = foregroundCount.toFloat() / max(1, pixels.size)

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
