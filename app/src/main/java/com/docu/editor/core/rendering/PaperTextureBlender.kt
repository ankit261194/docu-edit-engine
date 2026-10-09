package com.docu.editor.core.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.util.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Enterprise Paper Grain, Optical Camera Blur & Toner Texture Blending Engine.
 *
 * Solves the "Digital Paste" / "Sticker" problem on real-world smartphone photos:
 * 1. Optical Camera Blur (PSF Matching): Real camera photos have natural lens point
 *    spread function and Bayer demosaicing softness (1.0px - 2.2px blur). Canvas.drawText
 *    produces 0.0px razor-sharp vector edges that look artificial. This engine applies
 *    calibrated 2-pass separable Gaussian convolution matching the camera optics.
 * 2. Sensor ISO Noise Matching: Analyzes local paper variance (sigma) and injects
 *    matching grain into ink strokes.
 * 3. Toner Edge Bleed: Sub-pixel fiber scattering at stroke boundaries.
 * 4. Ink Tone Modulation: Allows dynamic lightening/darkening to match faded toner or dark ink.
 */
object PaperTextureBlender {

    data class BackgroundStats(
        val meanLuma: Float,
        val noiseSigma: Float,
        val meanR: Int,
        val meanG: Int,
        val meanB: Int,
        val estimatedBlurSigma: Float = 1.2f
    )

    /**
     * Samples local paper background surrounding targetBounds to measure authentic paper noise
     * and estimate camera lens point spread function (PSF) softness.
     */
    fun analyzeLocalPaperBackground(
        backgroundBitmap: Bitmap,
        targetBounds: Rect
    ): BackgroundStats {
        val w = backgroundBitmap.width
        val h = backgroundBitmap.height

        val padX = max(4, targetBounds.width() / 8)
        val padY = max(4, targetBounds.height() / 4)

        val left = (targetBounds.left - padX).coerceIn(0, w - 1)
        val top = (targetBounds.top - padY).coerceIn(0, h - 1)
        val right = (targetBounds.right + padX).coerceIn(0, w)
        val bottom = (targetBounds.bottom + padY).coerceIn(0, h)

        val sampleW = right - left
        val sampleH = bottom - top

        if (sampleW <= 0 || sampleH <= 0) {
            return BackgroundStats(240f, 3f, 240, 240, 240, 1.2f)
        }

        val pixels = IntArray(sampleW * sampleH)
        backgroundBitmap.getPixels(pixels, 0, sampleW, left, top, sampleW, sampleH)

        var sumLuma = 0.0
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0

        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = 0.299 * r + 0.587 * g + 0.114 * b

            // Only consider paper background pixels (luma > 150)
            if (luma > 150) {
                sumLuma += luma
                sumR += r
                sumG += g
                sumB += b
                count++
            }
        }

        if (count < 10) {
            return BackgroundStats(240f, 3.5f, 240, 240, 240, 1.2f)
        }

        val meanLuma = (sumLuma / count).toFloat()
        val meanR = (sumR / count).toInt()
        val meanG = (sumG / count).toInt()
        val meanB = (sumB / count).toInt()

        var sumSqDiff = 0.0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = 0.299 * r + 0.587 * g + 0.114 * b
            if (luma > 150) {
                val diff = luma - meanLuma
                sumSqDiff += diff * diff
            }
        }

        val variance = sumSqDiff / count
        val noiseSigma = sqrt(variance).toFloat().coerceIn(1.0f, 18.0f)

        // Digital PDF & Clean Computer Print: noiseSigma < 5.0 and high luma (> 220) -> 0.0px blur.
        // Camera Photo under shadows: noiseSigma >= 5.0 or meanLuma <= 220 -> lens softness 0.9 to 1.8px.
        val estimatedBlur = if (noiseSigma < 5.0f && meanLuma > 220f) {
            0.0f
        } else {
            (noiseSigma * 0.15f + 0.50f).coerceIn(0.8f, 1.8f)
        }

        return BackgroundStats(meanLuma, noiseSigma, meanR, meanG, meanB, estimatedBlur)
    }

    /**
     * Applies authentic Optical Camera Blur (PSF), laser toner grain, edge bleed,
     * and ink tone matching to the isolated text layer before drawing onto the master canvas.
     */
    fun blendTextWithPaperTexture(
        masterCanvas: Canvas,
        textLayerBitmap: Bitmap,
        targetBounds: Rect,
        stats: BackgroundStats,
        cameraBlurSigma: Float = 0.0f,
        paperBlendStrength: Float = 0.0f,
        inkToneDarkness: Float = 1.0f,
        renderBounds: Rect? = null
    ) {
        val w = textLayerBitmap.width
        val h = textLayerBitmap.height

        // 1. Digital Vector / Crisp Print Bypass: If blur <= 0.2 and blend strength <= 0.2,
        // preserve 100% pristine vector sharpness with zero blur or degradation!
        if (cameraBlurSigma <= 0.20f && paperBlendStrength <= 0.20f && inkToneDarkness == 1.0f) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            masterCanvas.drawBitmap(textLayerBitmap, 0f, 0f, paint)
            return
        }

        val effectiveBounds = if (renderBounds != null) {
            Rect(
                minOf(targetBounds.left, renderBounds.left),
                minOf(targetBounds.top, renderBounds.top),
                maxOf(targetBounds.right, renderBounds.right),
                maxOf(targetBounds.bottom, renderBounds.bottom)
            )
        } else {
            targetBounds
        }

        val pad = (max(cameraBlurSigma, 1.0f) * 4f).toInt() + 16
        val roiL = max(0, effectiveBounds.left - pad)
        val roiT = max(0, effectiveBounds.top - pad)
        val roiR = min(w, effectiveBounds.right + pad)
        val roiB = min(h, effectiveBounds.bottom + pad)
        val roiW = roiR - roiL
        val roiH = roiB - roiT

        if (roiW <= 0 || roiH <= 0) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            masterCanvas.drawBitmap(textLayerBitmap, 0f, 0f, paint)
            return
        }

        val roiPixels = IntArray(roiW * roiH)
        textLayerBitmap.getPixels(roiPixels, 0, roiW, roiL, roiT, roiW, roiH)

        // 2. Optical Camera Blur (PSF Matching) via 2-Pass Separable Alpha-Weighted Gaussian Convolution
        if (cameraBlurSigma > 0.15f) {
            applySeparableGaussianBlur(roiPixels, roiW, roiH, cameraBlurSigma)
        }

        val rng = Random(targetBounds.hashCode().toLong())
        val effectiveNoiseStrength = (stats.noiseSigma * 0.40f * paperBlendStrength).coerceIn(0f, 10f)

        // 3. Ink Tone Darkness & Laser Toner Edge Bleed / Micro-Grain
        for (i in roiPixels.indices) {
            val p = roiPixels[i]
            val a = (p ushr 24)
            if (a == 0) continue

            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF

            // Ink tone darkness modulation
            if (inkToneDarkness != 1.0f) {
                if (inkToneDarkness > 1.0f) {
                    // Darken ink towards deep charcoal/black
                    val factor = 1.0f - (inkToneDarkness - 1.0f) * 0.5f
                    r = (r * factor).toInt().coerceIn(0, 255)
                    g = (g * factor).toInt().coerceIn(0, 255)
                    b = (b * factor).toInt().coerceIn(0, 255)
                } else {
                    // Lighten ink towards faded toner / ambient paper tone
                    val blendToPaper = (1.0f - inkToneDarkness) * 0.6f
                    r = (r * (1f - blendToPaper) + stats.meanR * blendToPaper).toInt().coerceIn(0, 255)
                    g = (g * (1f - blendToPaper) + stats.meanG * blendToPaper).toInt().coerceIn(0, 255)
                    b = (b * (1f - blendToPaper) + stats.meanB * blendToPaper).toInt().coerceIn(0, 255)
                }
            }

            // Micro-grain noise calculation
            if (effectiveNoiseStrength > 0.5f) {
                val grain = ((rng.nextGaussian() * effectiveNoiseStrength)).toInt()
                r = (r + grain).coerceIn(0, 255)
                g = (g + grain).coerceIn(0, 255)
                b = (b + grain).coerceIn(0, 255)
            }

            // Laser toner outer boundary feathering & sub-pixel fiber scattering
            val na = if (paperBlendStrength > 0.1f && a in 8..240) {
                val scatter = (rng.nextFloat() - 0.48f) * 0.25f * paperBlendStrength
                val feathered = (a * (0.93f + scatter)).toInt()
                feathered.coerceIn(0, 255)
            } else {
                a
            }

            roiPixels[i] = (na shl 24) or (r shl 16) or (g shl 8) or b
        }

        val processedRoi = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888)
        processedRoi.setPixels(roiPixels, 0, roiW, 0, 0, roiW, roiH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        masterCanvas.drawBitmap(processedRoi, roiL.toFloat(), roiT.toFloat(), paint)
        processedRoi.recycle()
    }

    /**
     * Fast, accurate 2-pass separable Gaussian blur (horizontal then vertical)
     * operating with alpha weighting to prevent dark fringing/halos around edges.
     */
    private fun applySeparableGaussianBlur(pixels: IntArray, width: Int, height: Int, sigma: Float) {
        val radius = (2.5f * sigma).toInt().coerceIn(1, 5)
        val kernelSize = 2 * radius + 1
        val kernel = FloatArray(kernelSize)
        var kernelSum = 0f
        val twoSigmaSq = 2f * sigma * sigma

        for (i in -radius..radius) {
            val weight = exp(-(i * i).toFloat() / twoSigmaSq)
            kernel[i + radius] = weight
            kernelSum += weight
        }
        for (i in kernel.indices) {
            kernel[i] /= kernelSum
        }

        val tempPixels = IntArray(width * height)

        // Pass 1: Horizontal 1D Alpha-Weighted Gaussian Convolution
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                var aSum = 0f
                var rSum = 0f
                var gSum = 0f
                var bSum = 0f

                for (k in -radius..radius) {
                    val kx = (x + k).coerceIn(0, width - 1)
                    val p = pixels[rowOffset + kx]
                    val a = (p ushr 24) and 0xFF
                    val w = kernel[k + radius]
                    val aw = a * w

                    aSum += aw
                    rSum += ((p shr 16) and 0xFF) * aw
                    gSum += ((p shr 8) and 0xFF) * aw
                    bSum += (p and 0xFF) * aw
                }

                val outA = aSum.toInt().coerceIn(0, 255)
                val outR = if (aSum > 0.001f) (rSum / aSum).toInt().coerceIn(0, 255) else 0
                val outG = if (aSum > 0.001f) (gSum / aSum).toInt().coerceIn(0, 255) else 0
                val outB = if (aSum > 0.001f) (bSum / aSum).toInt().coerceIn(0, 255) else 0

                tempPixels[rowOffset + x] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }

        // Pass 2: Vertical 1D Alpha-Weighted Gaussian Convolution
        for (x in 0 until width) {
            for (y in 0 until height) {
                var aSum = 0f
                var rSum = 0f
                var gSum = 0f
                var bSum = 0f

                for (k in -radius..radius) {
                    val ky = (y + k).coerceIn(0, height - 1)
                    val p = tempPixels[ky * width + x]
                    val a = (p ushr 24) and 0xFF
                    val w = kernel[k + radius]
                    val aw = a * w

                    aSum += aw
                    rSum += ((p shr 16) and 0xFF) * aw
                    gSum += ((p shr 8) and 0xFF) * aw
                    bSum += (p and 0xFF) * aw
                }

                val outA = aSum.toInt().coerceIn(0, 255)
                val outR = if (aSum > 0.001f) (rSum / aSum).toInt().coerceIn(0, 255) else 0
                val outG = if (aSum > 0.001f) (gSum / aSum).toInt().coerceIn(0, 255) else 0
                val outB = if (aSum > 0.001f) (bSum / aSum).toInt().coerceIn(0, 255) else 0

                pixels[y * width + x] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
    }
}
