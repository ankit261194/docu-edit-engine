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
 * Enterprise Signature Extraction Engine with Illumination Division & Auto-Crop.
 * Normalizes background lighting across unevenly lit paper photos,
 * eliminates shadows, converts paper to 100% transparency,
 * produces anti-aliased vectorized ink strokes, and crops to exact bounding bounds.
 */
object SignatureExtractor {

    enum class InkColorOption(val rgb: Int) {
        ORIGINAL(0),
        ROYAL_BLUE(Color.rgb(18, 48, 140)),
        BALLPOINT_BLUE(Color.rgb(25, 75, 180)),
        CLASSIC_BLACK(Color.rgb(20, 20, 22)),
        STAMP_RED(Color.rgb(180, 25, 30)),
        EMERALD_GREEN(Color.rgb(16, 120, 70))
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

            // 3. Notebook Ruled Line Detection (Detects faint printed horizontal lines on notebook paper)
            val hLineKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(45.0, 1.0))
            val hLinesMat = Mat()
            val binNorm = Mat()
            Imgproc.threshold(norm8, binNorm, 220.0, 255.0, Imgproc.THRESH_BINARY_INV)
            Imgproc.morphologyEx(binNorm, hLinesMat, Imgproc.MORPH_OPEN, hLineKernel)
            val hLineBytes = ByteArray(width * height)
            hLinesMat.get(0, 0, hLineBytes)
            hLineKernel.release()
            hLinesMat.release()
            binNorm.release()

            val origPixels = IntArray(width * height)
            sourceBitmap.getPixels(origPixels, 0, width, 0, 0, width, height)

            val outputPixels = IntArray(width * height)
            val paperThreshold = (235 - contrastThresholdOffset / 2).coerceIn(200, 245)

            val targetR = (targetInkColor.rgb shr 16) and 0xFF
            val targetG = (targetInkColor.rgb shr 8) and 0xFF
            val targetB = targetInkColor.rgb and 0xFF

            for (i in outputPixels.indices) {
                val v = normBytes[i].toInt() and 0xFF
                val isRuledLine = (hLineBytes[i].toInt() and 0xFF) > 128

                // Suppress faint notebook ruled lines unless overlaid with heavy signature ink
                if (v >= paperThreshold || (isRuledLine && v > 150)) {
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
            cropToTransparentBounds(transparentSig, paddingPx = 16)
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

    /**
     * Crops transparent bitmap to content bounding box eliminating huge empty padding.
     */
    fun cropToTransparentBounds(bitmap: Bitmap, paddingPx: Int = 16): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha > 20) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return bitmap
        }

        val cropLeft = (minX - paddingPx).coerceAtLeast(0)
        val cropTop = (minY - paddingPx).coerceAtLeast(0)
        val cropRight = (maxX + paddingPx).coerceAtMost(w - 1)
        val cropBottom = (maxY + paddingPx).coerceAtMost(h - 1)
        val cropW = (cropRight - cropLeft + 1).coerceAtLeast(1)
        val cropH = (cropBottom - cropTop + 1).coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
    }
}
