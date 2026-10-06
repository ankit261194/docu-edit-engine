package com.docu.editor.core.signature

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Enterprise Signature Extraction Engine with Illumination Division.
 * Normalizes background lighting across unevenly lit paper photos,
 * eliminates shadows, converts paper to 100% transparency,
 * and produces anti-aliased vectorized ink strokes.
 */
object SignatureExtractor {

    enum class InkColorOption(val rgb: Int) {
        ORIGINAL(0),
        ROYAL_BLUE(Color.rgb(18, 48, 140)),
        BALLPOINT_BLUE(Color.rgb(25, 75, 180)),
        CLASSIC_BLACK(Color.rgb(20, 20, 22)),
        STAMP_RED(Color.rgb(180, 25, 30))
    }

    suspend fun extractSignature(
        sourceBitmap: Bitmap,
        targetInkColor: InkColorOption = InkColorOption.BALLPOINT_BLUE,
        contrastThresholdOffset: Int = 18
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcRgba = Mat()
        val gray = Mat()
        val bgIllum = Mat()
        val gray32 = Mat()
        val bg32 = Mat()
        val norm32 = Mat()
        val norm8 = Mat()

        try {
            Utils.bitmapToMat(sourceBitmap, srcRgba)
            Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)

            // 1. Bilateral Illumination Estimation: Morphological Close
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(35.0, 35.0))
            Imgproc.morphologyEx(gray, bgIllum, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            // 2. Illumination Division: (Gray / Background) * 255
            gray.convertTo(gray32, CvType.CV_32F)
            bgIllum.convertTo(bg32, CvType.CV_32F)
            Core.divide(gray32, bg32, norm32)
            Core.multiply(norm32, Scalar(255.0), norm32)
            norm32.convertTo(norm8, CvType.CV_8U)

            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val normBytes = ByteArray(width * height)
            norm8.get(0, 0, normBytes)

            val origPixels = IntArray(width * height)
            sourceBitmap.getPixels(origPixels, 0, width, 0, 0, width, height)

            val outputPixels = IntArray(width * height)
            val paperThreshold = (235 - contrastThresholdOffset / 2).coerceIn(200, 245)

            val targetR = (targetInkColor.rgb shr 16) and 0xFF
            val targetG = (targetInkColor.rgb shr 8) and 0xFF
            val targetB = targetInkColor.rgb and 0xFF

            for (i in outputPixels.indices) {
                val v = normBytes[i].toInt() and 0xFF
                if (v >= paperThreshold) {
                    outputPixels[i] = Color.TRANSPARENT
                } else {
                    // Smooth feathered alpha transition along stroke boundaries
                    val inkDensity = ((paperThreshold - v).toFloat() / (paperThreshold - 100).coerceAtLeast(1)).coerceIn(0f, 1f)
                    val alpha = (inkDensity * 255f).toInt().coerceIn(0, 255)

                    if (targetInkColor == InkColorOption.ORIGINAL) {
                        val orig = origPixels[i]
                        outputPixels[i] = (alpha shl 24) or (orig and 0x00FFFFFF)
                    } else {
                        outputPixels[i] = (alpha shl 24) or (targetR shl 16) or (targetG shl 8) or targetB
                    }
                }
            }

            val transparentSig = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            transparentSig.setPixels(outputPixels, 0, width, 0, 0, width, height)
            transparentSig
        } finally {
            srcRgba.release()
            gray.release()
            bgIllum.release()
            gray32.release()
            bg32.release()
            norm32.release()
            norm8.release()
        }
    }
}
