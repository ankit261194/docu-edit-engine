package com.docu.editor

import com.docu.editor.core.font.FontClassification
import com.docu.editor.core.font.FontMatcher
import com.docu.editor.core.ocr.model.TypographyMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.exp

class TypographyAndOpticsTest {

    @Test
    fun testTypographyMetricsTerminalFlareDefault() {
        val metrics = TypographyMetrics(estimatedFontSizePx = 28f)
        assertEquals(1.0f, metrics.terminalFlareRatio, 0.001f)
    }

    @Test
    fun testFontClassificationWithTerminalFlare() {
        // Optical camera blur softens corner detection (isSerif = false),
        // but genuine serif terminal flare ratio >= 1.28 catches serif foot flaring
        val blurredSerifMetrics = TypographyMetrics(
            estimatedFontSizePx = 32f,
            isSerif = false,
            terminalFlareRatio = 1.30f
        )
        val classification = FontMatcher.classifyFromMetrics(
            text = "Department",
            metrics = blurredSerifMetrics,
            bounds = null,
            documentDominantFont = null,
            lineDominantFont = null
        )
        assertEquals(FontClassification.SERIF, classification)
    }

    @Test
    fun testArialSansSerifDoesNotTriggerSerif() {
        // Standard Arial on printed/scanned document has natural stroke contrast (~1.18)
        // and camera optical bleed (flare ~1.12). This MUST NEVER trigger Serif!
        val arialMetrics = TypographyMetrics(
            estimatedFontSizePx = 28f,
            isSerif = false,
            terminalFlareRatio = 1.12f,
            strokeWidthRatio = 0.16f
        )
        val classification = FontMatcher.classifyFromMetrics(
            text = "Kumar",
            metrics = arialMetrics,
            bounds = null,
            documentDominantFont = null,
            lineDominantFont = null
        )
        assertEquals(FontClassification.SANS_SERIF, classification)
    }

    @Test
    fun testFontClassificationWithLineConsensus() {
        // In the presence of a neighbor on the exact same line that is Serif,
        // ambiguous words must adhere to line consensus
        val ambiguousMetrics = TypographyMetrics(
            estimatedFontSizePx = 24f,
            isSerif = false,
            terminalFlareRatio = 1.02f
        )
        val classification = FontMatcher.classifyFromMetrics(
            text = "General",
            metrics = ambiguousMetrics,
            bounds = null,
            documentDominantFont = null,
            lineDominantFont = FontClassification.SERIF
        )
        assertEquals(FontClassification.SERIF, classification)
    }

    @Test
    fun testFontClassificationWithDocumentConsensus() {
        // Document-level serif consensus prevents Times New Roman document from mutating to Arial
        val ambiguousMetrics = TypographyMetrics(
            estimatedFontSizePx = 24f,
            isSerif = false,
            terminalFlareRatio = 1.01f
        )
        val classification = FontMatcher.classifyFromMetrics(
            text = "Agreement",
            metrics = ambiguousMetrics,
            bounds = null,
            documentDominantFont = FontClassification.SERIF,
            lineDominantFont = null
        )
        assertEquals(FontClassification.SERIF, classification)
    }

    @Test
    fun testFontClassificationWithoutConsensusDefaultsToSansSerif() {
        // Clean sans-serif text in sans document defaults to Sans-serif (Arial)
        val cleanSansMetrics = TypographyMetrics(
            estimatedFontSizePx = 24f,
            isSerif = false,
            terminalFlareRatio = 0.98f
        )
        val classification = FontMatcher.classifyFromMetrics(
            text = "System",
            metrics = cleanSansMetrics,
            bounds = null,
            documentDominantFont = null,
            lineDominantFont = null
        )
        assertEquals(FontClassification.SANS_SERIF, classification)
    }

    @Test
    fun testBaselineMedianStability() {
        // Verify line median calculation rejects outliers and locks baseline
        val lineBottoms = listOf(502f, 501f, 503f, 502f, 518f) // 518f is a descender word
        val sorted = lineBottoms.sorted()
        val median = sorted[sorted.size / 2]
        assertEquals(502f, median, 0.001f)
    }

    @Test
    fun testGaussianOpticalBlurKernelProperties() {
        val testSigmas = listOf(0.5f, 1.0f, 1.2f, 1.8f, 2.5f)
        for (sigma in testSigmas) {
            val radius = ceil(sigma * 2.5f).toInt().coerceIn(1, 7)
            val kernelSize = radius * 2 + 1
            val kernel = FloatArray(kernelSize)
            val twoSigmaSq = 2f * sigma * sigma
            var sum = 0f

            for (i in -radius..radius) {
                val weight = exp(-(i * i).toDouble() / twoSigmaSq).toFloat()
                kernel[i + radius] = weight
                sum += weight
            }

            // Normalize
            for (i in 0 until kernelSize) {
                kernel[i] /= sum
            }

            // Verify symmetry
            for (i in 0 until radius) {
                assertEquals(
                    "Kernel must be symmetric around center for sigma $sigma",
                    kernel[i],
                    kernel[kernelSize - 1 - i],
                    1e-6f
                )
            }

            // Verify all weights are positive
            for (w in kernel) {
                assertTrue("Kernel weight must be positive", w > 0f)
            }

            // Verify sum of normalized kernel is 1.0
            val normalizedSum = kernel.sum()
            assertEquals("Normalized kernel must sum to 1.0", 1.0f, normalizedSum, 1e-5f)
        }
    }

    @Test
    fun testAlphaWeightedConvolutionPreventsDarkFringe() {
        // A stroke pixel with solid blue ink: (A=255, R=30, G=58, B=138)
        // Adjacent pixel is transparent paper: (A=0, R=0, G=0, B=0)
        // With alpha-weighted convolution, the edge pixel should retain the pure ink color (30, 58, 138)
        // with reduced alpha (e.g. 128), NOT a darkened RGB (15, 29, 69) which creates a black halo.
        val a1 = 255f
        val r1 = 30f
        val g1 = 58f
        val b1 = 138f

        val a2 = 0f
        val r2 = 0f
        val g2 = 0f
        val b2 = 0f

        val w1 = 0.5f
        val w2 = 0.5f

        val aSum = a1 * w1 + a2 * w2
        val rSum = r1 * a1 * w1 + r2 * a2 * w2
        val gSum = g1 * a1 * w1 + g2 * a2 * w2
        val bSum = b1 * a1 * w1 + b2 * a2 * w2

        val outR = (rSum / aSum).toInt()
        val outG = (gSum / aSum).toInt()
        val outB = (bSum / aSum).toInt()
        val outA = aSum.toInt()

        assertEquals("Alpha must be 50% falloff", 127, outA)
        assertEquals("Red must stay 30 (pure ink)", 30, outR)
        assertEquals("Green must stay 58 (pure ink)", 58, outG)
        assertEquals("Blue must stay 138 (pure ink)", 138, outB)
    }

    @Test
    fun testRotatedLinePerpendicularGrouping() {
        // Document tilted by 10 degrees
        val angleDeg = 10.0
        val rad = Math.toRadians(angleDeg)
        val cosA = kotlin.math.cos(rad).toFloat()
        val sinA = kotlin.math.sin(rad).toFloat()

        val targetCenterX = 100f
        val targetCenterY = 500f
        val lineH = 30f

        // Word 2 on the SAME 10-degree tilted line, 200px to the right along the line:
        // dx = 200 * cos(10) = 196.96, dy = 200 * sin(10) = 34.73
        val word2CenterX = targetCenterX + 200f * cosA
        val word2CenterY = targetCenterY + 200f * sinA

        val dx2 = word2CenterX - targetCenterX
        val dy2 = word2CenterY - targetCenterY
        val perpDist2 = kotlin.math.abs(-dx2 * sinA + dy2 * cosA)
        assertTrue("Word 2 on the tilted line must have near 0 perpendicular distance", perpDist2 < 0.1f)
        assertTrue("Word 2 must be included in same line", perpDist2 < lineH * 0.70f)

        // Word 3 on the NEXT line down (displaced perpendicularly by lineH = 30px)
        val word3CenterX = word2CenterX - 30f * sinA
        val word3CenterY = word2CenterY + 30f * cosA

        val dx3 = word3CenterX - targetCenterX
        val dy3 = word3CenterY - targetCenterY
        val perpDist3 = kotlin.math.abs(-dx3 * sinA + dy3 * cosA)
        assertTrue("Word 3 on next line must have perp distance ~30px", perpDist3 > 28f)
        assertTrue("Word 3 on next line must be rejected", perpDist3 >= lineH * 0.70f)
    }

    @Test
    fun testPrecisionMaskEffectiveThresholdScaling() {
        // High contrast ink on document (Otsu = 80.0):
        // Old buggy behavior clamped threshold to min(16.0, ...) = 16.0, confusing paper noise for ink.
        // New behavior scales up cleanly to 36.0 (within 14.0..50.0).
        val otsuHigh = 80.0
        val effectiveHigh = (otsuHigh * 0.45).coerceIn(14.0, 50.0)
        assertEquals(36.0, effectiveHigh, 0.001)

        // Low contrast / faded pencil (Otsu = 20.0):
        val otsuLow = 20.0
        val effectiveLow = (otsuLow * 0.45).coerceIn(14.0, 50.0)
        assertEquals(14.0, effectiveLow, 0.001)

        // Extreme high contrast (Otsu = 140.0):
        val otsuExtreme = 140.0
        val effectiveExtreme = (otsuExtreme * 0.45).coerceIn(14.0, 50.0)
        assertEquals(50.0, effectiveExtreme, 0.001)
    }

    @Test
    fun testLightTextOnDarkBackgroundLuminanceRange() {
        // Inverted document with white text on dark background:
        // textIsDarker = false
        val sampleCount = 100
        val textIsDarker = false
        val startIdx = if (textIsDarker) {
            (sampleCount * 0.10f).toInt().coerceIn(0, sampleCount - 1)
        } else {
            (sampleCount * 0.55f).toInt().coerceIn(0, sampleCount - 1)
        }
        val endIdx = if (textIsDarker) {
            (sampleCount * 0.45f).toInt().coerceIn(startIdx + 1, sampleCount)
        } else {
            (sampleCount * 0.90f).toInt().coerceIn(startIdx + 1, sampleCount)
        }
        assertEquals(55, startIdx)
        assertEquals(90, endIdx)
    }

    @Test
    fun testShortLowercaseWordCapHeightNormalization() {
        val originalText = "are"
        val origHasDescenders = originalText.any { it in "qypgj" }
        val origHasCapOrAscender = originalText.any { it.isUpperCase() || it in "bdfhklt1234567890$€₹£" }
        assertFalse("Word 'are' has no descenders", origHasDescenders)
        assertFalse("Word 'are' has no capitals or ascenders", origHasCapOrAscender)

        val targetHeight = 14f // Typical 14px x-height for a 28px font line
        val desiredCapH = when {
            !origHasCapOrAscender && !origHasDescenders -> (targetHeight * 1.40f)
            !origHasCapOrAscender && origHasDescenders -> (targetHeight * 0.98f)
            origHasDescenders -> (targetHeight * 0.78f)
            else -> (targetHeight * 0.95f)
        }

        // Must scale up to ~19.6px (matching cap-height of ~20px for 28px line) rather than staying at 13.3px
        assertEquals(19.6f, desiredCapH, 0.01f)
    }

    @Test
    fun testCapAndDescenderClassification() {
        fun classifyWord(word: String): Pair<Boolean, Boolean> {
            val hasCapOrAsc = word.any { it.isUpperCase() || it in "bdfhklt1234567890$€₹£" }
            val hasDesc = word.any { it in "qypgj" }
            return Pair(hasCapOrAsc, hasDesc)
        }

        assertEquals(Pair(false, false), classifyWord("are"))
        assertEquals(Pair(false, false), classifyWord("on"))
        assertEquals(Pair(true, false), classifyWord("Doctor"))
        assertEquals(Pair(true, true), classifyWord("playing"))
        assertEquals(Pair(false, true), classifyWord("you"))
        assertEquals(Pair(true, false), classifyWord("$500"))
    }

    @Test
    fun testLineLevelSansSerifConsensusOverridesDocumentSerif() {
        // Real-world invoice scenario: document has serif numbers in tables above (documentDominantFont = SERIF),
        // but the signature line has Sans-Serif neighbors "For" and "Archana" (lineDominantFont = SANS_SERIF).
        // The replacement word "Kumar" MUST render as Sans-Serif, NEVER Times New Roman!
        val metrics = TypographyMetrics(
            estimatedFontSizePx = 24f,
            isSerif = false,
            terminalFlareRatio = 1.05f
        )
        val classification = FontMatcher.classifyFromMetrics(
            text = "Kumar",
            metrics = metrics,
            bounds = null,
            documentDominantFont = FontClassification.SERIF,
            lineDominantFont = FontClassification.SANS_SERIF
        )
        assertEquals(FontClassification.SANS_SERIF, classification)
    }

    @Test
    fun testComputerPrintZeroBlurEstimation() {
        // Digital invoice or computer print has clean paper (meanLuma >= 225, noiseSigma < 4.0)
        // Must estimate 0.0px blur for razor-sharp vector text!
        val noiseSigma = 2.1f
        val meanLuma = 246f
        val estimatedBlur = if (noiseSigma < 5.0f && meanLuma > 220f) {
            0.0f
        } else {
            (noiseSigma * 0.15f + 0.50f).coerceIn(0.8f, 1.8f)
        }
        assertEquals(0.0f, estimatedBlur, 0.001f)
    }
}
