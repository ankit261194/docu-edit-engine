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
        paint: Paint
    ): AdjustedTypography {
        val targetWidth = max(10, targetBounds.width()).toFloat()
        val targetHeight = max(8, targetBounds.height()).toFloat()
        val charCount = max(1, text.length)

        var fontSize = targetHeight * 0.82f
        var trackingEm = 0f
        var scaleX = 1.0f

        paint.textSize = fontSize
        paint.letterSpacing = 0f
        paint.textScaleX = 1.0f

        var measuredWidth = paint.measureText(text)

        if (measuredWidth > targetWidth) {
            val deficitRatio = targetWidth / measuredWidth

            when {
                deficitRatio >= 0.85f && charCount > 1 -> {
                    val excessPx = measuredWidth - targetWidth
                    trackingEm = (-(excessPx / (charCount - 1)) / fontSize).coerceIn(-0.08f, 0f)
                    paint.letterSpacing = trackingEm
                }

                deficitRatio in 0.65f..0.85f -> {
                    trackingEm = -0.06f
                    paint.letterSpacing = trackingEm
                    val widthWithKerning = paint.measureText(text)
                    scaleX = (targetWidth / widthWithKerning).coerceIn(0.72f, 1.0f)
                    paint.textScaleX = scaleX
                }

                else -> {
                    trackingEm = -0.07f
                    scaleX = 0.72f
                    paint.letterSpacing = trackingEm
                    paint.textScaleX = scaleX

                    val widthCondensed = paint.measureText(text)
                    val fontScale = (targetWidth / widthCondensed).coerceAtLeast(0.40f)
                    fontSize *= fontScale
                    paint.textSize = fontSize
                }
            }
        } else {
            val deficit = targetWidth - measuredWidth
            if (charCount > 1 && deficit > 4f) {
                trackingEm = ((deficit / (charCount - 1)) / fontSize).coerceIn(0f, 0.20f)
                paint.letterSpacing = trackingEm
            }
        }

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
