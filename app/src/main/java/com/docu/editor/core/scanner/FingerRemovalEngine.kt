package com.docu.editor.core.scanner

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

/**
 * Enterprise Automated Finger / Thumb Removal Engine.
 * Detects human fingers/thumbs holding the edges of a scanned document,
 * creates a precise boundary mask, and inpaints the area using Telea background synthesis.
 */
object FingerRemovalEngine {

    suspend fun removeFingers(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val ycrcb = Mat()
        val skinMask = Mat()
        val cleanedMask = Mat()
        val inpaintedRgb = Mat()
        val resultRgba = Mat()

        try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcRgb, ycrcb, Imgproc.COLOR_RGB2YCrCb)

            // 1. Skin Color Thresholding in YCrCb space (Cr: [133..173], Cb: [77..127])
            val lowerSkin = Scalar(0.0, 133.0, 77.0)
            val upperSkin = Scalar(255.0, 173.0, 127.0)
            Core.inRange(ycrcb, lowerSkin, upperSkin, skinMask)

            // 2. Proximity Mask: Fingers holding documents always enter from image borders
            // Outer 18% margin zone
            val w = source.width
            val h = source.height
            val borderZone = Mat.zeros(h, w, CvType.CV_8UC1)
            val borderMarginX = (w * 0.18).toInt()
            val borderMarginY = (h * 0.18).toInt()

            // Draw border zones (Left, Right, Top, Bottom)
            Imgproc.rectangle(borderZone, Point(0.0, 0.0), Point(borderMarginX.toDouble(), h.toDouble()), Scalar(255.0), -1)
            Imgproc.rectangle(borderZone, Point((w - borderMarginX).toDouble(), 0.0), Point(w.toDouble(), h.toDouble()), Scalar(255.0), -1)
            Imgproc.rectangle(borderZone, Point(0.0, 0.0), Point(w.toDouble(), borderMarginY.toDouble()), Scalar(255.0), -1)
            Imgproc.rectangle(borderZone, Point(0.0, (h - borderMarginY).toDouble()), Point(w.toDouble(), h.toDouble()), Scalar(255.0), -1)

            val borderSkin = Mat()
            Core.bitwise_and(skinMask, borderZone, borderSkin)
            borderZone.release()

            // 3. Morphological filter to remove noise and solidify finger blobs
            val kOpen = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
            Imgproc.morphologyEx(borderSkin, cleanedMask, Imgproc.MORPH_OPEN, kOpen)
            val kClose = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(21.0, 21.0))
            Imgproc.morphologyEx(cleanedMask, cleanedMask, Imgproc.MORPH_CLOSE, kClose)
            kOpen.release()
            kClose.release()
            borderSkin.release()

            // 4. Find Contours and keep only those touching the border with plausible finger area
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(cleanedMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()

            val fingerMask = Mat.zeros(h, w, CvType.CV_8UC1)
            val minFingerArea = (w * h) * 0.003 // > 0.3% of page
            val maxFingerArea = (w * h) * 0.15  // < 15% of page
            var foundFingers = 0

            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area in minFingerArea..maxFingerArea) {
                    val rect = Imgproc.boundingRect(c)
                    val touchesBorder = (rect.x <= 6 || rect.y <= 6 ||
                                        (rect.x + rect.width) >= (w - 6) ||
                                        (rect.y + rect.height) >= (h - 6))
                    if (touchesBorder) {
                        Imgproc.drawContours(fingerMask, listOf(c), -1, Scalar(255.0), -1)
                        foundFingers++
                    }
                }
                c.release()
            }

            if (foundFingers == 0) {
                fingerMask.release()
                return@withContext source.copy(Bitmap.Config.ARGB_8888, true)
            }

            // Dilate finger mask by 15px to cover finger cast shadows and soft skin edges
            val kDilate = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
            val dilatedMask = Mat()
            Imgproc.dilate(fingerMask, dilatedMask, kDilate)
            kDilate.release()
            fingerMask.release()

            // 5. Inpaint using OpenCV Photo.inpaint Telea
            Photo.inpaint(srcRgb, dilatedMask, inpaintedRgb, 7.0, Photo.INPAINT_TELEA)
            dilatedMask.release()

            Imgproc.cvtColor(inpaintedRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } catch (_: Exception) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            srcRgba.release()
            srcRgb.release()
            ycrcb.release()
            skinMask.release()
            cleanedMask.release()
            inpaintedRgb.release()
            resultRgba.release()
        }
    }
}
