package com.docu.editor.core.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.docu.editor.core.rendering.model.NoiseMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ArtifactBlendingEngine {

    private val random = Random()

    suspend fun sampleNoiseMetrics(
        sourceBitmap: Bitmap,
        targetBounds: Rect,
        samplingMargin: Int = 18
    ): NoiseMetrics = withContext(Dispatchers.Default) {
        val left = max(0, targetBounds.left - samplingMargin)
        val top = max(0, targetBounds.top - samplingMargin)
        val right = min(sourceBitmap.width, targetBounds.right + samplingMargin)
        val bottom = min(sourceBitmap.height, targetBounds.bottom + samplingMargin)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return@withContext NoiseMetrics(4.0f, 240f, 0.6f, true)
        }

        val pixels = IntArray(width * height)
        sourceBitmap.getPixels(pixels, 0, width, left, top, width, height)

        val sampleLumas = mutableListOf<Float>()
        var lumaSum = 0.0

        for (y in 0 until height) {
            val isPerimeterY = y < samplingMargin || y >= height - samplingMargin
            val rowOffset = y * width
            for (x in 0 until width) {
                val isPerimeterX = x < samplingMargin || x >= width - samplingMargin
                if (isPerimeterX || isPerimeterY) {
                    val c = pixels[rowOffset + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    sampleLumas.add(luma)
                    lumaSum += luma
                }
            }
        }

        if (sampleLumas.size < 10) {
            return@withContext NoiseMetrics(4.5f, 235f, 0.6f, true)
        }

        val mean = (lumaSum / sampleLumas.size).toFloat()
        var varianceSum = 0.0
        for (lum in sampleLumas) {
            val diff = lum - mean
            varianceSum += diff * diff
        }
        val rawStdDev = sqrt(varianceSum / sampleLumas.size).toFloat()
        val recommendedBlur = (0.45f + (rawStdDev / 30f)).coerceIn(0.4f, 1.2f)

        NoiseMetrics(
            noiseStdDev = rawStdDev.coerceIn(2.0f, 22.0f),
            meanLuminance = mean,
            recommendedBlurSigma = recommendedBlur,
            isMonochromeNoise = true
        )
    }

    suspend fun blendText(
        cleanedBackground: Bitmap,
        isolatedTextLayer: Bitmap,
        targetBounds: Rect
    ): Bitmap = withContext(Dispatchers.Default) {
        val noiseMetrics = sampleNoiseMetrics(cleanedBackground, targetBounds)

        val padding = 20
        val left = max(0, targetBounds.left - padding)
        val top = max(0, targetBounds.top - padding)
        val right = min(isolatedTextLayer.width, targetBounds.right + padding)
        val bottom = min(isolatedTextLayer.height, targetBounds.bottom + padding)
        val roiWidth = right - left
        val roiHeight = bottom - top

        if (roiWidth <= 0 || roiHeight <= 0) return@withContext cleanedBackground

        val textRoi = Bitmap.createBitmap(isolatedTextLayer, left, top, roiWidth, roiHeight)
        val blurredTextRoi = applyMicroBlur(textRoi, noiseMetrics.recommendedBlurSigma)
        textRoi.recycle()

        injectGaussianNoise(blurredTextRoi, noiseMetrics.noiseStdDev)

        val outputBitmap = cleanedBackground.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }

        canvas.drawBitmap(blurredTextRoi, left.toFloat(), top.toFloat(), paint)
        blurredTextRoi.recycle()

        outputBitmap
    }

    private fun applyMicroBlur(srcBitmap: Bitmap, sigma: Float): Bitmap {
        val srcMat = Mat()
        val blurredMat = Mat()

        return try {
            Utils.bitmapToMat(srcBitmap, srcMat)
            var kSize = (sigma * 3).roundToInt() * 2 + 1
            kSize = kSize.coerceIn(3, 7)

            Imgproc.GaussianBlur(srcMat, blurredMat, Size(kSize.toDouble(), kSize.toDouble()), sigma.toDouble())

            val output = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(blurredMat, output)
            output
        } finally {
            srcMat.release()
            blurredMat.release()
        }
    }

    private fun injectGaussianNoise(textBitmap: Bitmap, stdDev: Float) {
        val width = textBitmap.width
        val height = textBitmap.height
        val pixels = IntArray(width * height)
        textBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = (color ushr 24) and 0xFF

            if (alpha > 8) {
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                val noise = (random.nextGaussian() * stdDev).toFloat()
                val effectiveNoise = if (alpha < 240) (noise * 0.80f).roundToInt() else noise.roundToInt()

                val newR = (r + effectiveNoise).coerceIn(0, 255)
                val newG = (g + effectiveNoise).coerceIn(0, 255)
                val newB = (b + effectiveNoise).coerceIn(0, 255)

                val newAlpha = if (alpha in 12..245) {
                    val alphaJitter = (random.nextGaussian() * (stdDev * 0.5f)).roundToInt()
                    (alpha + alphaJitter).coerceIn(0, 255)
                } else alpha

                pixels[i] = (newAlpha shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }
        textBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
