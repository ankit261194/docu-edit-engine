package com.docu.editor.core.ocr.util

import android.graphics.Rect
import com.docu.editor.core.ocr.model.FontWeightEstimate
import com.docu.editor.core.ocr.model.TypographyMetrics
import kotlin.math.max

/**
 * Enterprise Camera-Resilient Typography Estimation Engine.
 *
 * Reliably classifies font weight, tracking, and Serif vs Sans-Serif
 * even on camera photos where lens Point Spread Function (PSF) and optical
 * softness smooth out fine serifs.
 *
 * Uses multi-zone vertical stem terminal flare analysis:
 * In Serif fonts (Times New Roman, Georgia), baseline feet and head serifs
 * flare out horizontally at stroke terminals (top and bottom) compared to
 * the narrow stem center, whereas Sans-Serif fonts (Arial, Calibri) maintain
 * uniform straight stems.
 */
object TypographyEstimator {

    fun estimateMetrics(
        text: String,
        bounds: Rect,
        foregroundResult: TextInkColorSampler.InkSampleResult,
        documentDominantSerif: Boolean = false,
        lineDominantSerif: Boolean = false
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

        // Terminal flare analysis: measures horizontal stroke width at top 15%, mid 50%, and bottom 15%
        val flareRatio = estimateTerminalFlare(
            foregroundResult.foregroundMask,
            foregroundResult.cropWidth,
            foregroundResult.cropHeight
        )

        val strokeRatio = verticalStemWidth / height
        val strokeContrast = if (horizontalBarWidth > 0.5f) verticalStemWidth / horizontalBarWidth else 1.0f

        // Camera photos soften horizontal bars. Multi-signal serif detector:
        val isSerif = documentDominantSerif ||
            lineDominantSerif ||
            (strokeContrast >= 1.12f && charCount >= 2) ||
            (flareRatio >= 1.13f && charCount >= 2) ||
            (strokeContrast >= 1.07f && flareRatio >= 1.09f)

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
            numericFontWeight = numericWeight,
            terminalFlareRatio = flareRatio
        )
    }

    private fun estimateTerminalFlare(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): Float {
        if (width <= 4 || height <= 6 || mask.isEmpty()) return 1.0f

        val topRuns = mutableListOf<Int>()
        val midRuns = mutableListOf<Int>()
        val botRuns = mutableListOf<Int>()

        val yTop1 = (height * 0.10f).toInt().coerceIn(0, height - 1)
        val yTop2 = (height * 0.22f).toInt().coerceIn(yTop1, height - 1)

        val yMid1 = (height * 0.42f).toInt().coerceIn(0, height - 1)
        val yMid2 = (height * 0.58f).toInt().coerceIn(yMid1, height - 1)

        val yBot1 = (height * 0.78f).toInt().coerceIn(0, height - 1)
        val yBot2 = (height * 0.90f).toInt().coerceIn(yBot1, height - 1)

        collectHorizontalRuns(mask, width, yTop1..yTop2, topRuns)
        collectHorizontalRuns(mask, width, yMid1..yMid2, midRuns)
        collectHorizontalRuns(mask, width, yBot1..yBot2, botRuns)

        if (midRuns.isEmpty()) return 1.0f
        midRuns.sort()
        val medianMid = midRuns[midRuns.size / 2].toFloat()
        if (medianMid < 1.0f) return 1.0f

        val medianTop = if (topRuns.isNotEmpty()) {
            topRuns.sort()
            topRuns[topRuns.size / 2].toFloat()
        } else medianMid

        val medianBot = if (botRuns.isNotEmpty()) {
            botRuns.sort()
            botRuns[botRuns.size / 2].toFloat()
        } else medianMid

        val topRatio = medianTop / medianMid
        val botRatio = medianBot / medianMid

        return max(topRatio, botRatio).coerceIn(0.8f, 2.0f)
    }

    private fun collectHorizontalRuns(
        mask: BooleanArray,
        width: Int,
        yRange: IntRange,
        outputRuns: MutableList<Int>
    ) {
        val maxAllowed = width * 0.45f
        for (y in yRange) {
            var run = 0
            val rowOffset = y * width
            for (x in 0 until width) {
                if (mask[rowOffset + x]) {
                    run++
                } else if (run > 0) {
                    if (run in 2..maxAllowed.toInt()) {
                        outputRuns.add(run)
                    }
                    run = 0
                }
            }
            if (run in 2..maxAllowed.toInt()) {
                outputRuns.add(run)
            }
        }
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
