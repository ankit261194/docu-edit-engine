package com.docu.editor.core.rendering

import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max

object AutoFitFontCondenser {

    data class AdjustedTypography(
        val fontSize: Float,
        val letterSpacingEm: Float,
        val scaleX: Float,
        val baselineY: Float
    )

    fun condenseToFit(
        text: String,
        targetBounds: Rect,
        paint: Paint,
        originalText: String = "",
        sizeMultiplier: Float = 1.0f
    ): AdjustedTypography {
        val lines = text.split("\n")
        val lineCount = max(1, lines.size)
        val targetWidth = max(10, targetBounds.width()).toFloat()
        val targetHeight = (max(8, targetBounds.height()).toFloat() / lineCount)

        // 1. Initial font size estimate based on EM box vs visual cap-height.
        // In standard fonts (Arial, Roboto, etc.), Cap-Height is ~0.71 of textSize.
        var fontSize = (targetHeight * 1.35f) * sizeMultiplier
        paint.textSize = fontSize
        paint.letterSpacing = 0f
        paint.textScaleX = 1.0f

        // 2. Measure actual glyph ink height using Paint.getTextBounds
        val sampleGlyph = when {
            text.any { it in '\u0900'..'\u097F' } -> text.filter { it in '\u0900'..'\u097F' }.take(2)
            text.any { it.isUpperCase() } -> text.filter { it.isUpperCase() }.take(2)
            text.any { it.isDigit() } -> text.filter { it.isDigit() }.take(2)
            text.any { it.isLowerCase() } -> text.filter { it.isLowerCase() }.take(2)
            else -> "H"
        }

        val glyphBounds = Rect()
        paint.getTextBounds(sampleGlyph, 0, sampleGlyph.length, glyphBounds)
        val measuredInkH = glyphBounds.height().toFloat()

        if (measuredInkH > 2f) {
            // Target ink height matches original bounding box height with 4% breathing margin
            val desiredInkH = targetHeight * 0.94f * sizeMultiplier
            val calibrationRatio = desiredInkH / measuredInkH
            fontSize = (fontSize * calibrationRatio).coerceIn(6f, targetHeight * 2.5f)
            paint.textSize = fontSize
        }

        val measuredWidth = if (lineCount > 1) {
            lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(text)
        } else {
            paint.measureText(text)
        }

        var scaleX = 1.0f
        var trackingEm = 0f

        if (measuredWidth > targetWidth) {
            val ratio = targetWidth / measuredWidth
            when {
                ratio >= 0.85f -> {
                    scaleX = ratio.coerceIn(0.85f, 1.0f)
                    trackingEm = -0.012f
                }
                ratio in 0.65f..0.85f -> {
                    scaleX = 0.85f
                    trackingEm = -0.02f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val remeasured = paint.measureText(text)
                    if (remeasured > targetWidth) {
                        fontSize *= (targetWidth / remeasured).coerceAtLeast(0.68f)
                    }
                }
                else -> {
                    scaleX = 0.82f
                    trackingEm = -0.025f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val remeasured = paint.measureText(text)
                    if (remeasured > targetWidth) {
                        fontSize *= (targetWidth / remeasured).coerceAtLeast(0.50f)
                    }
                }
            }
        }

        // Indic / Devanagari script protection:
        // Letter-spacing breaks the continuous top line (shirorekha) in Hindi/Devanagari.
        val hasIndicScript = text.any { it.code in 0x0900..0x0D7F }
        if (hasIndicScript) {
            trackingEm = 0f
        }

        paint.textSize = fontSize
        paint.letterSpacing = trackingEm
        paint.textScaleX = scaleX

        // 3. Pixel-perfect baseline alignment
        val fontMetrics = paint.fontMetrics
        val lineHeight = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading
        val totalTextHeight = if (lineCount > 1) {
            (lineCount - 1) * lineHeight + (fontMetrics.descent - fontMetrics.ascent)
        } else {
            fontMetrics.descent - fontMetrics.ascent
        }

        val topY = targetBounds.centerY().toFloat() - totalTextHeight / 2f
        val baselineY = topY - fontMetrics.ascent

        return AdjustedTypography(
            fontSize = fontSize,
            letterSpacingEm = trackingEm,
            scaleX = scaleX,
            baselineY = baselineY
        )
    }
}
