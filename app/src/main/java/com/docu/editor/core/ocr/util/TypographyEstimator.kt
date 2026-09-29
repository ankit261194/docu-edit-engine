package com.docu.editor.core.ocr.util

import android.graphics.Rect
import com.docu.editor.core.ocr.model.FontWeightEstimate
import com.docu.editor.core.ocr.model.TypographyMetrics
import kotlin.math.max

object TypographyEstimator {

    fun estimateMetrics(
        text: String,
        bounds: Rect,
        foregroundResult: TextInkColorSampler.InkSampleResult
    ): TypographyMetrics {
        val height = max(1, bounds.height()).toFloat()
        val width = max(1, bounds.width()).toFloat()
        val charCount = max(1, text.replace(" ", "").length)

        val strokeWidth = estimateHorizontalStrokeWidth(
            foregroundResult.foregroundMask,
            foregroundResult.cropWidth,
            foregroundResult.cropHeight
        )
        val strokeRatio = strokeWidth / height

        val density = foregroundResult.foregroundRatio
        val weight = when {
            strokeRatio > 0.20f || density > 0.35f -> FontWeightEstimate.EXTRA_BOLD
            strokeRatio > 0.13f || density > 0.23f -> FontWeightEstimate.BOLD
            strokeRatio > 0.09f || density > 0.17f -> FontWeightEstimate.MEDIUM
            strokeRatio < 0.07f && density < 0.13f -> FontWeightEstimate.LIGHT
            else -> FontWeightEstimate.REGULAR
        }

        val expectedGlyphWidth = height * 0.52f
        val spaceCount = text.count { it == ' ' }
        val expectedSpaceWidth = height * 0.25f
        val expectedTotalNaturalWidth = (charCount * expectedGlyphWidth) + (spaceCount * expectedSpaceWidth)

        val excessWidth = width - expectedTotalNaturalWidth
        val trackingEm = if (charCount > 1) {
            (excessWidth / (charCount - 1)) / height
        } else {
            0f
        }.coerceIn(-0.1f, 0.6f)

        return TypographyMetrics(
            estimatedFontWeight = weight,
            strokeWidthRatio = strokeRatio,
            glyphDensity = density,
            letterSpacingEm = trackingEm,
            estimatedFontSizePx = height * 1.35f
        )
    }

    private fun estimateHorizontalStrokeWidth(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): Float {
        if (width <= 0 || height <= 0 || mask.isEmpty()) return 2f

        val runLengths = mutableListOf<Int>()
        val stepY = max(1, height / 10)

        for (y in 0 until height step stepY) {
            var currentRun = 0
            val rowOffset = y * width
            for (x in 0 until width) {
                if (mask[rowOffset + x]) {
                    currentRun++
                } else if (currentRun > 0) {
                    if (currentRun > 1 && currentRun < width * 0.4f) {
                        runLengths.add(currentRun)
                    }
                    currentRun = 0
                }
            }
            if (currentRun in 2 until (width * 0.4f).toInt()) {
                runLengths.add(currentRun)
            }
        }

        if (runLengths.isEmpty()) return 2f
        runLengths.sort()
        return runLengths[runLengths.size / 2].toFloat()
    }
}
