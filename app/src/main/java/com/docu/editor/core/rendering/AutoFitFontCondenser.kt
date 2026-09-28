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
        val targetWidth = max(10, targetBounds.width()).toFloat()
        val targetHeight = max(8, targetBounds.height()).toFloat()

        // Calibrated typographic height (matches standard document font cap-height to avoid tall overflow)
        var fontSize = targetHeight * 0.80f * sizeMultiplier
        var trackingEm = 0f
        var scaleX = 1.0f

        paint.textSize = fontSize
        paint.letterSpacing = 0f
        paint.textScaleX = 1.0f

        val measuredWidth = paint.measureText(text)
        val origLen = if (originalText.isNotEmpty()) originalText.length else text.length
        val newLen = max(1, text.length)

        if (measuredWidth > targetWidth) {
            // Text is LONGER than bounding box: condense naturally like CamScanner
            val ratio = targetWidth / measuredWidth

            when {
                ratio >= 0.88f -> {
                    // Mild condensation: slight horizontal squeeze
                    scaleX = ratio.coerceIn(0.88f, 1.0f)
                    trackingEm = -0.015f
                }
                ratio in 0.70f..0.88f -> {
                    // Moderate condensation: squeeze width + slight font reduction
                    scaleX = 0.88f
                    trackingEm = -0.025f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val newMeasured = paint.measureText(text)
                    fontSize *= (targetWidth / newMeasured).coerceIn(0.80f, 1.0f)
                }
                else -> {
                    // Significantly longer text: scale font size down proportionally
                    scaleX = 0.85f
                    trackingEm = -0.03f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val newMeasured = paint.measureText(text)
                    fontSize *= (targetWidth / newMeasured).coerceAtLeast(0.55f)
                }
            }
        } else {
            // Text is shorter or equal length:
            // If character count is identical or very close (e.g. replacing a name with another name),
            // match the exact character pitch so it aligns naturally with the document's grid.
            if (abs(origLen - newLen) <= 1 && origLen > 2) {
                val naturalPitchRatio = targetWidth / measuredWidth
                if (naturalPitchRatio in 0.92f..1.12f) {
                    scaleX = naturalPitchRatio
                }
            }
            trackingEm = 0f
        }

        paint.textSize = fontSize
        paint.letterSpacing = trackingEm
        paint.textScaleX = scaleX

        // Accurate baseline alignment centering glyphs vertically in bounding box
        val fontMetrics = paint.fontMetrics
        val centerY = targetBounds.centerY().toFloat()
        val baselineY = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f

        return AdjustedTypography(
            fontSize = fontSize,
            letterSpacingEm = trackingEm,
            scaleX = scaleX,
            baselineY = baselineY
        )
    }
}
