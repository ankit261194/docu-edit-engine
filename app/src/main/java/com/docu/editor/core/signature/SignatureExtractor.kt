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
 * Enterprise Signature Extraction Engine with Euclidean Distance Transform Anti-Aliasing & Ink Chemistry.
 * Normalizes background lighting across unevenly lit paper photos,
 * eliminates shadows and paper grain, converts paper to 100% transparency,
 * produces vector-smooth anti-aliased pen strokes via Euclidean signed distance fields,
 * and models authentic ink chemistry (ballpoint blue, gel black, fountain royal blue).
 */
object SignatureExtractor {

    enum class InkColorOption(
        val rgb: Int,
        val coreRgb: Int = rgb,
        val edgeRgb: Int = rgb
    ) {
        ORIGINAL(0, 0, 0),
        ROYAL_BLUE(
            Color.rgb(18, 48, 140),
            Color.rgb(12, 32, 115),
            Color.rgb(40, 80, 195)
        ),
        BALLPOINT_BLUE(
            Color.rgb(25, 75, 180),
            Color.rgb(15, 45, 135),
            Color.rgb(38, 92, 215)
        ),
        CLASSIC_BLACK(
            Color.rgb(20, 20, 22),
            Color.rgb(12, 12, 14),
            Color.rgb(45, 45, 48)
        ),
        STAMP_RED(
            Color.rgb(180, 25, 30),
            Color.rgb(160, 20, 25),
            Color.rgb(225, 40, 42)
        ),
        EMERALD_GREEN(
            Color.rgb(16, 120, 70),
            Color.rgb(10, 85, 45),
            Color.rgb(24, 145, 88)
        )
    }

    suspend fun extractSignature(
        sourceBitmap: Bitmap,
        targetInkColor: InkColorOption = InkColorOption.BALLPOINT_BLUE,
        contrastThresholdOffset: Int = 18,
        vectorSmoothing: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcRgba = Mat()
        val gray = Mat()
        val bgIllum = Mat()
        val gray32 = Mat()
        val bg32 = Mat()
        val norm32 = Mat()
        val norm8 = Mat()
        val binNorm = Mat()
        val hLinesMat = Mat()
        val binClean = Mat()
        val distInside = Mat()
        val invBin = Mat()
        val distOutside = Mat()

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
            val paperThreshold = (235 - contrastThresholdOffset / 2).coerceIn(200, 245)

            // 3. Ink Binarization: Ink pixels (< paperThreshold) become 255
            Imgproc.threshold(norm8, binNorm, paperThreshold.toDouble(), 255.0, Imgproc.THRESH_BINARY_INV)

            // 4. Notebook Ruled Line Detection & Suppression
            val hLineKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(45.0, 1.0))
            Imgproc.morphologyEx(binNorm, hLinesMat, Imgproc.MORPH_OPEN, hLineKernel)
            hLineKernel.release()

            // Suppress isolated ruled lines while keeping real signature strokes
            Core.subtract(binNorm, hLinesMat, binClean)

            val outputPixels = IntArray(width * height)
            val origPixels = IntArray(width * height)
            sourceBitmap.getPixels(origPixels, 0, width, 0, 0, width, height)

            if (vectorSmoothing) {
                // 5. Euclidean Distance Transform (L2) for Sub-Pixel Vector Anti-Aliasing
                Imgproc.distanceTransform(binClean, distInside, Imgproc.DIST_L2, 5)

                Core.bitwise_not(binClean, invBin)
                Imgproc.distanceTransform(invBin, distOutside, Imgproc.DIST_L2, 5)

                val distInFloats = FloatArray(width * height)
                distInside.get(0, 0, distInFloats)

                val distOutFloats = FloatArray(width * height)
                distOutside.get(0, 0, distOutFloats)

                val transitionHalfWidth = 1.25f

                val edgeR = (targetInkColor.edgeRgb shr 16) and 0xFF
                val edgeG = (targetInkColor.edgeRgb shr 8) and 0xFF
                val edgeB = targetInkColor.edgeRgb and 0xFF

                val coreR = (targetInkColor.coreRgb shr 16) and 0xFF
                val coreG = (targetInkColor.coreRgb shr 8) and 0xFF
                val coreB = targetInkColor.coreRgb and 0xFF

                for (i in outputPixels.indices) {
                    val dIn = distInFloats[i]
                    val dOut = distOutFloats[i]
                    val sdf = dIn - dOut // Positive inside ink, negative outside ink

                    // Vector Smoothstep / Hermite interpolation across boundary [-w, +w]
                    val u = ((sdf + transitionHalfWidth) / (2f * transitionHalfWidth)).coerceIn(0f, 1f)
                    val smoothAlpha = u * u * (3f - 2f * u)

                    if (smoothAlpha <= 0.04f) {
                        outputPixels[i] = Color.TRANSPARENT
                    } else {
                        val alpha = (smoothAlpha * 255f).toInt().coerceIn(0, 255)

                        if (targetInkColor == InkColorOption.ORIGINAL) {
                            val orig = origPixels[i]
                            outputPixels[i] = (alpha shl 24) or (orig and 0x00FFFFFF)
                        } else {
                            // Authentic Ink Chemistry: dense saturated core blending into translucent edge pooling
                            val coreFactor = (dIn / 2.2f).coerceIn(0f, 1f)
                            val r = ((1f - coreFactor) * edgeR + coreFactor * coreR).toInt().coerceIn(0, 255)
                            val g = ((1f - coreFactor) * edgeG + coreFactor * coreR).toInt().coerceIn(0, 255)
                            val b = ((1f - coreFactor) * edgeB + coreFactor * coreB).toInt().coerceIn(0, 255)

                            outputPixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }
            } else {
                // Fallback direct feathered alpha
                val normBytes = ByteArray(width * height)
                norm8.get(0, 0, normBytes)

                val targetR = (targetInkColor.rgb shr 16) and 0xFF
                val targetG = (targetInkColor.rgb shr 8) and 0xFF
                val targetB = targetInkColor.rgb and 0xFF

                for (i in outputPixels.indices) {
                    val v = normBytes[i].toInt() and 0xFF
                    if (v >= paperThreshold) {
                        outputPixels[i] = Color.TRANSPARENT
                    } else {
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
            binNorm.release()
            hLinesMat.release()
            binClean.release()
            distInside.release()
            invBin.release()
            distOutside.release()
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
