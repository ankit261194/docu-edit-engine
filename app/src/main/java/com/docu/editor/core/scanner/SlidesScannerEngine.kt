package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import com.docu.editor.core.scanner.model.DocumentCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Enterprise Presentation Slides & Screen Scanner Engine.
 * CamScanner-Grade Features:
 * 1. Moire Pattern Spatial Notch Filter: Suppresses high-frequency rainbow color bands
 *    and LCD pixel grid interference caused by camera sensor and display panel aliasing.
 * 2. Keystone Correction to 16:9 Widescreen: Straightens angled photos of projection screens
 *    into standard 16:9 (1920x1080) presentation slides.
 * 3. Projector Color Temperature & Contrast Equalization.
 */
object SlidesScannerEngine {

    enum class SlideAspect(val width: Int, val height: Int, val label: String) {
        WIDESCREEN_16_9(1920, 1080, "16:9 Widescreen (Modern)"),
        STANDARD_4_3(1600, 1200, "4:3 Standard (Classic)")
    }

    suspend fun processSlideScan(
        sourceBitmap: Bitmap,
        corners: DocumentCorners? = null,
        targetAspect: SlideAspect = SlideAspect.WIDESCREEN_16_9
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcMat = Mat()
        val rgbMat = Mat()
        val warpedMat = Mat()
        val finalMat = Mat()

        try {
            Utils.bitmapToMat(sourceBitmap, srcMat)
            Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

            // Step 1: 16:9 Keystone Correction (Perspective Warp)
            val keystoneCorrected = if (corners != null) {
                applyKeystoneCorrection(rgbMat, corners, targetAspect)
            } else {
                val autoCorners = DocumentEdgeDetector.detectCornersOrNull(sourceBitmap)
                if (autoCorners != null) {
                    applyKeystoneCorrection(rgbMat, autoCorners, targetAspect)
                } else {
                    val copy = Mat()
                    rgbMat.copyTo(copy)
                    copy
                }
            }

            // Step 2: Screen Moiré Pattern Notch Filtering (Rainbow ripple & pixel grid removal)
            val moireFree = removeMoirePattern(keystoneCorrected)
            keystoneCorrected.release()

            // Step 3: Projector Color Temperature & Contrast Equalization
            val balanced = equalizeProjectorColorBalance(moireFree)
            moireFree.release()

            Imgproc.cvtColor(balanced, finalMat, Imgproc.COLOR_RGB2RGBA)
            val resultBitmap = Bitmap.createBitmap(balanced.cols(), balanced.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(finalMat, resultBitmap)

            balanced.release()
            resultBitmap
        } catch (_: Exception) {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            srcMat.release()
            rgbMat.release()
            warpedMat.release()
            finalMat.release()
        }
    }

    /**
     * Warps slanted screen quadrilateral into standard 16:9 widescreen or 4:3 presentation slide.
     */
    fun applyKeystoneCorrection(
        srcMat: Mat,
        corners: DocumentCorners,
        targetAspect: SlideAspect
    ): Mat {
        val targetW = targetAspect.width.toDouble()
        val targetH = targetAspect.height.toDouble()

        val srcPoints = MatOfPoint2f(
            Point(corners.topLeft.x.toDouble(), corners.topLeft.y.toDouble()),
            Point(corners.topRight.x.toDouble(), corners.topRight.y.toDouble()),
            Point(corners.bottomRight.x.toDouble(), corners.bottomRight.y.toDouble()),
            Point(corners.bottomLeft.x.toDouble(), corners.bottomLeft.y.toDouble())
        )

        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(targetW, 0.0),
            Point(targetW, targetH),
            Point(0.0, targetH)
        )

        val transformMat = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val dstMat = Mat()

        Imgproc.warpPerspective(
            srcMat,
            dstMat,
            transformMat,
            Size(targetW, targetH),
            Imgproc.INTER_LANCZOS4,
            Core.BORDER_REPLICATE
        )

        srcPoints.release()
        dstPoints.release()
        transformMat.release()

        return dstMat
    }

    /**
     * Screen Moiré Pattern Spatial Notch Filter:
     * Separates image into LAB color channels.
     * In chrominance (a*, b*) channels: applies bilateral filtering to eliminate rainbow bands.
     * In luminance (L*) channel: applies Difference of Gaussians (DoG) band-stop ripple suppression
     * to smooth LED pixel grid raster lines while preserving sharp slide text edges.
     */
    fun removeMoirePattern(rgbMat: Mat): Mat {
        val labMat = Mat()
        Imgproc.cvtColor(rgbMat, labMat, Imgproc.COLOR_RGB2Lab)

        val labChannels = ArrayList<Mat>()
        Core.split(labMat, labChannels)

        val lChan = labChannels[0]
        val aChan = labChannels[1]
        val bChan = labChannels[2]

        // 1. Bilateral filtering on chrominance channels (eradicates colored rainbow moiré fringes)
        val aFiltered = Mat()
        val bFiltered = Mat()
        Imgproc.bilateralFilter(aChan, aFiltered, 9, 65.0, 65.0)
        Imgproc.bilateralFilter(bChan, bFiltered, 9, 65.0, 65.0)

        aFiltered.copyTo(aChan)
        bFiltered.copyTo(bChan)
        aFiltered.release()
        bFiltered.release()

        // 2. Luminance DoG Band-Stop Filter for screen pixel grid ripple
        val blurSmall = Mat()
        val blurMed = Mat()
        Imgproc.GaussianBlur(lChan, blurSmall, Size(3.0, 3.0), 0.8)
        Imgproc.GaussianBlur(lChan, blurMed, Size(7.0, 7.0), 2.2)

        // Ripple component = blurSmall - blurMed
        val ripple = Mat()
        Core.subtract(blurSmall, blurMed, ripple)

        // Subtract 65% of the high-frequency pixel grid ripple from L channel
        val lFloat = Mat()
        val rippleFloat = Mat()
        lChan.convertTo(lFloat, CvType.CV_32F)
        ripple.convertTo(rippleFloat, CvType.CV_32F)

        Core.multiply(rippleFloat, Scalar(0.60), rippleFloat)
        Core.subtract(lFloat, rippleFloat, lFloat)
        lFloat.convertTo(labChannels[0], CvType.CV_8U)

        lFloat.release()
        rippleFloat.release()
        ripple.release()
        blurSmall.release()
        blurMed.release()

        Core.merge(labChannels, labMat)
        val outRgb = Mat()
        Imgproc.cvtColor(labMat, outRgb, Imgproc.COLOR_Lab2RGB)

        for (c in labChannels) c.release()
        labMat.release()

        return outRgb
    }

    /**
     * Balances projector color cast (blueish/yellowish tint) and improves contrast.
     */
    private fun equalizeProjectorColorBalance(rgbMat: Mat): Mat {
        val channels = ArrayList<Mat>()
        Core.split(rgbMat, channels)

        // Gray-world white balance normalization
        val meanR = Core.mean(channels[0]).`val`[0]
        val meanG = Core.mean(channels[1]).`val`[0]
        val meanB = Core.mean(channels[2]).`val`[0]
        val avgGray = (meanR + meanG + meanB) / 3.0

        if (meanR > 10.0 && meanG > 10.0 && meanB > 10.0) {
            val scaleR = (avgGray / meanR).coerceIn(0.85, 1.20)
            val scaleG = (avgGray / meanG).coerceIn(0.85, 1.20)
            val scaleB = (avgGray / meanB).coerceIn(0.85, 1.20)

            Core.multiply(channels[0], Scalar(scaleR), channels[0])
            Core.multiply(channels[1], Scalar(scaleG), channels[1])
            Core.multiply(channels[2], Scalar(scaleB), channels[2])
        }

        val balanced = Mat()
        Core.merge(channels, balanced)
        for (c in channels) c.release()

        // Gentle unsharp mask on text
        val blurred = Mat()
        Imgproc.GaussianBlur(balanced, blurred, Size(0.0, 0.0), 1.8)
        val sharpened = Mat()
        Core.addWeighted(balanced, 1.30, blurred, -0.30, 0.0, sharpened)

        blurred.release()
        balanced.release()

        return sharpened
    }
}
