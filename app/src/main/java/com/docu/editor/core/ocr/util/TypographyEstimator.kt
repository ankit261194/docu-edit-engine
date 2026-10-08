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

        val verticalStemWidth = estimateHorizontalStrokeWidth(
            foregroundResult.foregroundMask,
            foregroundResult.cropWidth,
            foregroundResult.cropHeight
        )
        val horizontalBarWidth = estimateVerticalStrokeWidth(
            foregroundResult.foregroundMask,
            foregroundResult.cropWidth,
            foregroundResult.cropHeight
        )
        val strokeRatio = verticalStemWidth / height
        val strokeContrast = if (horizontalBarWidth > 0.5f) verticalStemWidth / horizontalBarWidth else 1.0f
        val isSerif = (strokeContrast >= 1.18f && charCount >= 2) || (strokeContrast >= 1.14f && charCount >= 4)

        val density = foregroundResult.foregroundRatio
        val weight = when {
            strokeRatio > 0.22f || (strokeRatio > 0.17f && density > 0.32f) -> FontWeightEstimate.EXTRA_BOLD
            strokeRatio > 0.16f || (strokeRatio > 0.14f && density > 0.26f) -> FontWeightEstimate.BOLD
            strokeRatio > 0.12f && density > 0.20f -> FontWeightEstimate.MEDIUM
            strokeRatio < 0.07f && density < 0.12f -> FontWeightEstimate.LIGHT
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

        val numericWeight = when (weight) {
            FontWeightEstimate.EXTRA_BOLD -> 850
            FontWeightEstimate.BOLD -> 700
            FontWeightEstimate.MEDIUM -> 550
            FontWeightEstimate.LIGHT -> 300
            FontWeightEstimate.REGULAR -> {
                ((strokeRatio * 2200f) + (density * 450f)).toInt().coerceIn(350, 490)
            }
        }.coerceIn(100, 900)

        return TypographyMetrics(
            estimatedFontWeight = weight,
            strokeWidthRatio = strokeRatio,
            glyphDensity = density,
            letterSpacingEm = trackingEm,
            estimatedFontSizePx = height * 0.82f,
            isSerif = isSerif,
            strokeThicknessPx = verticalStemWidth,
            numericFontWeight = numericWeight
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

    private fun estimateVerticalStrokeWidth(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): Float {
        if (width <= 0 || height <= 0 || mask.isEmpty()) return 2f

        val runLengths = mutableListOf<Int>()
        val stepX = max(1, width / 10)

        for (x in 0 until width step stepX) {
            var currentRun = 0
            for (y in 0 until height) {
                if (mask[y * width + x]) {
                    currentRun++
                } else if (currentRun > 0) {
                    if (currentRun > 1 && currentRun < height * 0.4f) {
                        runLengths.add(currentRun)
                    }
                    currentRun = 0
                }
            }
            if (currentRun in 2 until (height * 0.4f).toInt()) {
                runLengths.add(currentRun)
            }
        }

        if (runLengths.isEmpty()) return 2f
        runLengths.sort()
        return runLengths[runLengths.size / 2].toFloat()
    }
}
