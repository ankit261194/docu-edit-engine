package com.docu.editor.core.rendering

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

object LightingGradientShader {

    data class GradientSpec(
        val startColor: Int,
        val endColor: Int,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    )

    /**
     * Samples the 2D lighting slope of surrounding paper and applies a LinearGradient shader
     * to Paint so newly rendered text darkens or brightens across letters matching ambient lighting.
     */
    fun applyAmbientLightingShader(
        source: Bitmap,
        targetBounds: Rect,
        baseInkColor: Int,
        paint: Paint
    ) {
        val margin = 16
        val left = max(0, targetBounds.left - margin)
        val top = max(0, targetBounds.top - margin)
        val right = min(source.width, targetBounds.right + margin)
        val bottom = min(source.height, targetBounds.bottom + margin)

        if (right <= left || bottom <= top) return

        // Sample background paper brightness at 4 perimeter quadrants
        val lumaTopLeft = sampleRegionLuma(source, left, top, targetBounds.left, targetBounds.top)
        val lumaTopRight = sampleRegionLuma(source, targetBounds.right, top, right, targetBounds.top)
        val lumaBottomLeft = sampleRegionLuma(source, left, targetBounds.bottom, targetBounds.left, bottom)
        val lumaBottomRight = sampleRegionLuma(source, targetBounds.right, targetBounds.bottom, right, bottom)

        val avgLumaLeft = (lumaTopLeft + lumaBottomLeft) / 2f
        val avgLumaRight = (lumaTopRight + lumaBottomRight) / 2f

        val deltaLuma = avgLumaRight - avgLumaLeft

        // If lighting is uniform (delta < 5 luma), standard flat color is fine
        if (kotlin.math.abs(deltaLuma) < 5f) {
            paint.shader = null
            paint.color = baseInkColor
            return
        }

        // Modulate base ink brightness proportionally
        // Darker paper = deeper shadow; Brighter paper = more flash reflection
        val factorLeft = (avgLumaLeft / 240f).coerceIn(0.65f, 1.35f)
        val factorRight = (avgLumaRight / 240f).coerceIn(0.65f, 1.35f)

        val startColor = modulateColorBrightness(baseInkColor, factorLeft)
        val endColor = modulateColorBrightness(baseInkColor, factorRight)

        val shader = LinearGradient(
            targetBounds.left.toFloat(),
            targetBounds.centerY().toFloat(),
            targetBounds.right.toFloat(),
            targetBounds.centerY().toFloat(),
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )

        paint.shader = shader
    }

    private fun sampleRegionLuma(src: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val w = max(1, x2 - x1)
        val h = max(1, y2 - y1)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, x1, y1, w, h)

        var sumLuma = 0.0
        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            sumLuma += (0.299 * r + 0.587 * g + 0.114 * b)
        }
        return (sumLuma / pixels.size).toFloat()
    }

    private fun modulateColorBrightness(rgb: Int, factor: Float): Int {
        val r = (((rgb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * factor).toInt().coerceIn(0, 255)
        val a = (rgb ushr 24) and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
