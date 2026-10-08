package com.docu.editor.core.dewarp

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Spine Shadow Remover & Crease Equalizer.
 * CamScanner-Grade Shadow Inpainting:
 * Neutralizes the dark gradient shadow cast by open book binding creases
 * while preserving sharp black text ink contrast and paper texture.
 */
object SpineShadowRemover {

    enum class SpineEdge {
        LEFT_EDGE,     // Right page of book (spine is on the left)
        RIGHT_EDGE,    // Left page of book (spine is on the right)
        CENTER_GUTTER  // Unsplit 2-page book spread (spine is in the center)
    }

    /**
     * Removes binding gutter crease shadow from a single page bitmap or open spread.
     * @param source source page or spread bitmap
     * @param spineEdge which edge or center contains the binding gutter
     * @param shadowSpanFraction width fraction affected by shadow (typically 0.12f to 0.22f)
     */
    suspend fun removeSpineShadow(
        source: Bitmap,
        spineEdge: SpineEdge,
        shadowSpanFraction: Float = 0.18f
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height

        val shadowWidth = (width * shadowSpanFraction).toInt().coerceIn(20, width / 3)
        val result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)

        // Reference column from flat body of paper (just outside shadow zone)
        val refX = when (spineEdge) {
            SpineEdge.LEFT_EDGE -> min(width - 5, shadowWidth + (width * 0.08f).toInt())
            SpineEdge.RIGHT_EDGE -> max(4, width - shadowWidth - (width * 0.08f).toInt())
            SpineEdge.CENTER_GUTTER -> max(4, (width / 2) - shadowWidth - (width * 0.08f).toInt())
        }

        // Sample reference column paper luminance
        val stepY = max(1, height / 100)
        var refLumaSum = 0f
        var refSampleCount = 0

        for (y in 0 until height step stepY) {
            val p = source.getPixel(refX, y)
            val luma = getLuminance(p)
            if (luma > 140f) { // Only sample background paper, not text
                refLumaSum += luma
                refSampleCount++
            }
        }

        val targetPaperLuma = if (refSampleCount > 0) refLumaSum / refSampleCount else 235f

        // Process shadow columns
        val centerX = width / 2
        val xRange = when (spineEdge) {
            SpineEdge.LEFT_EDGE -> 0 until shadowWidth
            SpineEdge.RIGHT_EDGE -> (width - shadowWidth) until width
            SpineEdge.CENTER_GUTTER -> max(0, centerX - shadowWidth) until min(width, centerX + shadowWidth)
        }

        val rowPixels = IntArray(width)

        for (y in 0 until height) {
            source.getPixels(rowPixels, 0, width, 0, y, width, 1)

            for (x in xRange) {
                // Distance fraction from spine: 0.0 at spine, 1.0 at outer edge of shadow
                val distFromSpine = when (spineEdge) {
                    SpineEdge.LEFT_EDGE -> (x.toFloat() / shadowWidth).coerceIn(0f, 1f)
                    SpineEdge.RIGHT_EDGE -> ((width - 1 - x).toFloat() / shadowWidth).coerceIn(0f, 1f)
                    SpineEdge.CENTER_GUTTER -> (kotlin.math.abs(x - centerX).toFloat() / shadowWidth).coerceIn(0f, 1f)
                }

                // Smooth cosine falloff factor: 1.0 at spine, 0.0 at shadow boundary
                val shadowFactor = (0.5f * (1f + cos(Math.PI * distFromSpine))).toFloat()

                val pixel = rowPixels[x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = 0.299f * r + 0.587f * g + 0.114f * b

                // If pixel is background paper (not dark text ink), illuminate it
                // Dark ink threshold is typically < 110
                if (luma > 85f) {
                    val lumaDeficit = max(0f, targetPaperLuma - luma)
                    val boost = (lumaDeficit * shadowFactor * 0.95f)

                    val newR = min(255, (r + boost).toInt())
                    val newG = min(255, (g + boost).toInt())
                    val newB = min(255, (b + boost).toInt())

                    rowPixels[x] = (pixel and -0x1000000) or (newR shl 16) or (newG shl 8) or newB
                }
            }

            result.setPixels(rowPixels, 0, width, 0, y, width, 1)
        }

        result
    }

    private fun getLuminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
