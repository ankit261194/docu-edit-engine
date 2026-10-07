package com.docu.editor.core.cv

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

/**
 * Enterprise OpenCV Ink & Rubber Stamp Erase Engine.
 * CamScanner-Grade Features:
 * - Color Segmentation in HSV/LAB space targeting non-printed colored inks.
 * - Strict Preservation of Underlying Printed Black Text (Protects letters under stamps).
 * - Anti-Aliased Edge Feathering using Morphological Dilation.
 * - Fast Marching Telea Background Inpainting with ambient paper texture synthesis.
 */
object EraseMarksEngine {

    enum class EraseMode {
        ALL_MARKS,       // Blue, green, red pens & stamps
        RUBBER_STAMPS,   // Red, purple, magenta seals only
        BLUE_PEN,        // Blue & cyan ballpoint/gel signatures
        BLACK_PEN,       // Black handwritten pen marks (preserving printed font)
        CUSTOM_COLOR     // Specific ink color
    }

    suspend fun eraseMarks(
        source: Bitmap,
        mode: EraseMode = EraseMode.ALL_MARKS,
        customColor: Int = Color.BLUE
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val hsvMat = Mat()
        val grayMat = Mat()
        val combinedMask = Mat()
        val dilatedMask = Mat()
        val textProtectionMask = Mat()
        val inpaintedRgb = Mat()
        val resultRgba = Mat()

        try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcRgb, hsvMat, Imgproc.COLOR_RGB2HSV)
            Imgproc.cvtColor(srcRgb, grayMat, Imgproc.COLOR_RGB2GRAY)

            combinedMask.create(srcRgb.rows(), srcRgb.cols(), CvType.CV_8UC1)
            combinedMask.setTo(Scalar(0.0))

            when (mode) {
                EraseMode.ALL_MARKS -> {
                    // 1. Blue / Cyan ballpoint & gel pen: H [95..135], S [50..255], V [40..255]
                    addHsvRangeToMask(hsvMat, combinedMask, 95.0, 135.0, 50.0, 255.0, 40.0, 255.0)

                    // 2. Red pen & rubber stamps: H [0..15] and [165..180], S [55..255], V [40..255]
                    addHsvRangeToMask(hsvMat, combinedMask, 0.0, 15.0, 55.0, 255.0, 40.0, 255.0)
                    addHsvRangeToMask(hsvMat, combinedMask, 165.0, 180.0, 55.0, 255.0, 40.0, 255.0)

                    // 3. Purple / Violet stamps: H [135..165], S [45..255], V [40..255]
                    addHsvRangeToMask(hsvMat, combinedMask, 135.0, 165.0, 45.0, 255.0, 40.0, 255.0)

                    // 4. Green annotations: H [35..85], S [50..255], V [40..255]
                    addHsvRangeToMask(hsvMat, combinedMask, 35.0, 85.0, 50.0, 255.0, 40.0, 255.0)
                }

                EraseMode.RUBBER_STAMPS -> {
                    // Red stamps (two hue wraps in OpenCV HSV)
                    addHsvRangeToMask(hsvMat, combinedMask, 0.0, 15.0, 55.0, 255.0, 40.0, 255.0)
                    addHsvRangeToMask(hsvMat, combinedMask, 165.0, 180.0, 55.0, 255.0, 40.0, 255.0)
                    // Purple official ink stamps
                    addHsvRangeToMask(hsvMat, combinedMask, 135.0, 165.0, 45.0, 255.0, 40.0, 255.0)
                }

                EraseMode.BLUE_PEN -> {
                    // Blue & cyan ink strokes
                    addHsvRangeToMask(hsvMat, combinedMask, 95.0, 135.0, 50.0, 255.0, 40.0, 255.0)
                }

                EraseMode.BLACK_PEN -> {
                    // Identify thin handwriting strokes that deviate from regular printed text
                    val binary = Mat()
                    Imgproc.adaptiveThreshold(
                        grayMat, binary, 255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY_INV, 15, 8.0
                    )
                    // Morphological open with small kernel isolates thin pen strokes
                    val kThin = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
                    val thinStrokes = Mat()
                    Imgproc.morphologyEx(binary, thinStrokes, Imgproc.MORPH_OPEN, kThin)
                    kThin.release()
                    binary.release()

                    // Exclude thick printed fonts by subtracting dilated text blocks
                    val kThick = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                    val thickBlocks = Mat()
                    Imgproc.morphologyEx(thinStrokes, thickBlocks, Imgproc.MORPH_OPEN, kThick)
                    kThick.release()

                    Core.subtract(thinStrokes, thickBlocks, combinedMask)
                    thinStrokes.release()
                    thickBlocks.release()
                }

                EraseMode.CUSTOM_COLOR -> {
                    val hsv = FloatArray(3)
                    Color.colorToHSV(customColor, hsv)
                    val targetH = (hsv[0] / 2f).toDouble()
                    val minH = (targetH - 18.0).coerceAtLeast(0.0)
                    val maxH = (targetH + 18.0).coerceAtMost(180.0)
                    val minS = (hsv[1] * 255.0 * 0.60).coerceIn(40.0, 255.0)
                    addHsvRangeToMask(hsvMat, combinedMask, minH, maxH, minS, 255.0, 40.0, 255.0)
                }
            }

            // Morphological dilation expands mask by 2-3 pixels to swallow anti-aliased edge halos
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.dilate(combinedMask, dilatedMask, kernel)
            kernel.release()

            // Enterprise Protection: Preserve underlying printed black text under stamps/pens
            if (mode != EraseMode.BLACK_PEN) {
                Core.inRange(hsvMat, Scalar(0.0, 0.0, 0.0), Scalar(180.0, 40.0, 60.0), textProtectionMask)
                Core.subtract(dilatedMask, textProtectionMask, dilatedMask)
            }

            // Run OpenCV Telea Fast Marching Inpainting
            Photo.inpaint(srcRgb, dilatedMask, inpaintedRgb, 3.5, Photo.INPAINT_TELEA)

            // Inject micro paper grain matching paper standard deviation
            val meanMat = MatOfDouble()
            val stddevMat = MatOfDouble()
            Core.meanStdDev(srcRgb, meanMat, stddevMat)
            val stddevArr = stddevMat.toArray()
            val grainSigma = if (stddevArr.isNotEmpty()) stddevArr[0].coerceIn(0.5, 6.0) else 2.0
            meanMat.release()
            stddevMat.release()

            if (grainSigma > 1.2) {
                val floatDst = Mat()
                inpaintedRgb.convertTo(floatDst, CvType.CV_32FC3)

                val noiseMat = Mat(inpaintedRgb.size(), CvType.CV_32FC3)
                Core.randn(noiseMat, 0.0, grainSigma * 0.50)

                val floatMask = Mat()
                dilatedMask.convertTo(floatMask, CvType.CV_32FC1, 1.0 / 255.0)
                val channels = mutableListOf<Mat>()
                Core.split(noiseMat, channels)
                for (ch in channels) {
                    Core.multiply(ch, floatMask, ch)
                }
                Core.merge(channels, noiseMat)
                channels.forEach { it.release() }
                floatMask.release()

                Core.add(floatDst, noiseMat, floatDst)
                noiseMat.release()

                floatDst.convertTo(inpaintedRgb, CvType.CV_8UC3)
                floatDst.release()
            }

            Imgproc.cvtColor(inpaintedRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            hsvMat.release()
            grayMat.release()
            combinedMask.release()
            dilatedMask.release()
            textProtectionMask.release()
            inpaintedRgb.release()
            resultRgba.release()
        }
    }

    private fun addHsvRangeToMask(
        hsv: Mat,
        destMask: Mat,
        hMin: Double,
        hMax: Double,
        sMin: Double,
        sMax: Double,
        vMin: Double,
        vMax: Double
    ) {
        val rangeMask = Mat()
        Core.inRange(
            hsv,
            Scalar(hMin, sMin, vMin),
            Scalar(hMax, sMax, vMax),
            rangeMask
        )
        Core.bitwise_or(destMask, rangeMask, destMask)
        rangeMask.release()
    }
}
