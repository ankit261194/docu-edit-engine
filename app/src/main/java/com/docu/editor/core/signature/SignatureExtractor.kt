package com.docu.editor.core.signature

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

object SignatureExtractor {

    enum class InkColorOption(val rgb: Int) {
        ORIGINAL(0),
        ROYAL_BLUE(Color.rgb(18, 48, 140)),
        BALLPOINT_BLUE(Color.rgb(25, 75, 180)),
        CLASSIC_BLACK(Color.rgb(20, 20, 22)),
        STAMP_RED(Color.rgb(180, 25, 30))
    }

    /**
     * Extracts signature strokes from paper, converts paper to 100% transparency,
     * and optionally recolors the ink to standard ballpoint blue or black.
     */
    suspend fun extractSignature(
        sourceBitmap: Bitmap,
        targetInkColor: InkColorOption = InkColorOption.BALLPOINT_BLUE,
        contrastThresholdOffset: Int = 18
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val pixels = IntArray(width * height)
        sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Calculate average paper background luminance
        var lumaSum = 0.0
        val luminances = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            luminances[i] = luma
            lumaSum += luma
        }

        val avgLuma = (lumaSum / pixels.size).toFloat()
        // Threshold: anything close to paper brightness becomes transparent
        val paperThreshold = avgLuma - contrastThresholdOffset

        val outputPixels = IntArray(pixels.size)

        for (i in pixels.indices) {
            val lum = luminances[i]

            if (lum >= paperThreshold) {
                // Background paper -> 100% transparent
                outputPixels[i] = Color.TRANSPARENT
            } else {
                // Ink stroke -> Calculate alpha based on how dark the stroke is
                val inkDensity = ((paperThreshold - lum) / paperThreshold).coerceIn(0f, 1f)
                val alpha = (inkDensity * 255f).toInt().coerceIn(0, 255)

                if (targetInkColor == InkColorOption.ORIGINAL) {
                    val orig = pixels[i]
                    outputPixels[i] = (alpha shl 24) or (orig and 0x00FFFFFF)
                } else {
                    val inkR = (targetInkColor.rgb shr 16) and 0xFF
                    val inkG = (targetInkColor.rgb shr 8) and 0xFF
                    val inkB = targetInkColor.rgb and 0xFF
                    outputPixels[i] = (alpha shl 24) or (inkR shl 16) or (inkG shl 8) or inkB
                }
            }
        }

        val transparentSig = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        transparentSig.setPixels(outputPixels, 0, width, 0, 0, width, height)
        transparentSig
    }
}
