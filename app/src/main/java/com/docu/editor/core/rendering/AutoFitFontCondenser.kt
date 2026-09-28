package com.docu.editor.core.rendering

import android.graphics.Paint
import android.graphics.Rect
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
        sizeMultiplier: Float = 1.0f
    ): AdjustedTypography {
        val targetWidth = max(10, targetBounds.width()).toFloat()
        val targetHeight = max(8, targetBounds.height()).toFloat()

        // Capital letters cap-height is ~72% of EM size.
        // targetHeight is bounding box of capital letters, so EM size = targetHeight / 0.72f.
        var fontSize = (targetHeight / 0.72f) * sizeMultiplier
        var trackingEm = 0f
        var scaleX = 1.0f

        paint.textSize = fontSize
        paint.letterSpacing = 0f
        paint.textScaleX = 1.0f

        val measuredWidth = paint.measureText(text)

        if (measuredWidth > targetWidth) {
            // Text is LONGER than original: condense naturally
            val ratio = targetWidth / measuredWidth

            when {
                ratio >= 0.85f -> {
                    // Mild condensation: slight horizontal squeeze
                    scaleX = ratio.coerceIn(0.85f, 1.0f)
                    trackingEm = -0.02f
                }
                ratio in 0.65f..0.85f -> {
                    // Moderate condensation: squeeze width + slight font reduction
                    scaleX = 0.85f
                    trackingEm = -0.03f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val newMeasured = paint.measureText(text)
                    fontSize *= (targetWidth / newMeasured).coerceIn(0.75f, 1.0f)
                }
                else -> {
                    // Significantly longer text: scale font size down proportionally
                    scaleX = 0.82f
                    trackingEm = -0.03f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val newMeasured = paint.measureText(text)
                    fontSize *= (targetWidth / newMeasured).coerceAtLeast(0.50f)
                }
            }
        } else {
            // Text is SHORTER than original:
            // CRITICAL: NEVER stretch letter-spacing accordion-style! Keep natural typography!
            trackingEm = 0f
            scaleX = 1.0f
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
