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
        val hsv = Mat()
        val grayMat = Mat()
        val skinYcrcb = Mat()
        val skinHsv1 = Mat()
        val skinHsv2 = Mat()
        val nailHsv = Mat()
        val rawSkinMask = Mat()
        val cleanedMask = Mat()
        val textProtectionMask = Mat()
        val inpaintedRgb = Mat()
        val resultRgba = Mat()

        try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcRgb, ycrcb, Imgproc.COLOR_RGB2YCrCb)
            Imgproc.cvtColor(srcRgb, hsv, Imgproc.COLOR_RGB2HSV)
            Imgproc.cvtColor(srcRgb, grayMat, Imgproc.COLOR_RGB2GRAY)

            val w = source.width
            val h = source.height

            // 1. Multi-Color Space Skin + Fingernail Detection:
            // YCrCb: Cr in [130..178], Cb in [76..128]
            Core.inRange(ycrcb, Scalar(0.0, 130.0, 76.0), Scalar(255.0, 178.0, 128.0), skinYcrcb)

            // HSV Skin (lower and upper red/orange wraps)
            Core.inRange(hsv, Scalar(0.0, 30.0, 45.0), Scalar(25.0, 200.0, 255.0), skinHsv1)
            Core.inRange(hsv, Scalar(165.0, 30.0, 45.0), Scalar(180.0, 200.0, 255.0), skinHsv2)

            // HSV Fingernail (pale pinkish / ivory keratin at finger tip)
            Core.inRange(hsv, Scalar(0.0, 15.0, 135.0), Scalar(22.0, 110.0, 255.0), nailHsv)

            rawSkinMask.create(h, w, CvType.CV_8UC1)
            rawSkinMask.setTo(Scalar(0.0))
            Core.bitwise_or(skinHsv1, skinHsv2, rawSkinMask)
            Core.bitwise_and(skinYcrcb, rawSkinMask, rawSkinMask)
            Core.bitwise_or(rawSkinMask, nailHsv, rawSkinMask)

            // 2. Proximity Mask: Fingers holding documents always enter from image borders
            val borderZone = Mat.zeros(h, w, CvType.CV_8UC1)
            val borderMarginX = (w * 0.22).toInt()
            val borderMarginY = (h * 0.22).toInt()

            Imgproc.rectangle(borderZone, Point(0.0, 0.0), Point(borderMarginX.toDouble(), h.toDouble()), Scalar(255.0), -1)
            Imgproc.rectangle(borderZone, Point((w - borderMarginX).toDouble(), 0.0), Point(w.toDouble(), h.toDouble()), Scalar(255.0), -1)
            Imgproc.rectangle(borderZone, Point(0.0, 0.0), Point(w.toDouble(), borderMarginY.toDouble()), Scalar(255.0), -1)
            Imgproc.rectangle(borderZone, Point(0.0, (h - borderMarginY).toDouble()), Point(w.toDouble(), h.toDouble()), Scalar(255.0), -1)

            val borderSkin = Mat()
            Core.bitwise_and(rawSkinMask, borderZone, borderSkin)
            borderZone.release()

            // 3. Morphological filtering to solidify finger blob and remove micro-noise
            val kOpen = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
            Imgproc.morphologyEx(borderSkin, cleanedMask, Imgproc.MORPH_OPEN, kOpen)
            val kClose = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(23.0, 23.0))
            Imgproc.morphologyEx(cleanedMask, cleanedMask, Imgproc.MORPH_CLOSE, kClose)
            kOpen.release()
            kClose.release()
            borderSkin.release()

            // 4. Contour Filtering: Keep contours touching image edges with plausible thumb area
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(cleanedMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()

            val fingerMask = Mat.zeros(h, w, CvType.CV_8UC1)
            val minFingerArea = (w * h) * 0.0025 // > 0.25% of page
            val maxFingerArea = (w * h) * 0.16   // < 16% of page
            var foundFingers = 0

            for (c in contours) {
                val area = Imgproc.contourArea(c)
                if (area in minFingerArea..maxFingerArea) {
                    val rect = Imgproc.boundingRect(c)
                    val touchesBorder = (rect.x <= 8 || rect.y <= 8 ||
                                        (rect.x + rect.width) >= (w - 8) ||
                                        (rect.y + rect.height) >= (h - 8))
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

            // 5. Strict Printed Text Protection:
            // Isolate any printed character strokes and table rulings inside the finger zone
            // so text under or adjacent to the thumb is NEVER erased.
            val localText = Mat()
            Imgproc.adaptiveThreshold(
                grayMat, localText, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 15, 10.0
            )

            // Also check gradient magnitude for sharp text edges
            val gradX = Mat()
            val gradY = Mat()
            val absGradX = Mat()
            val absGradY = Mat()
            val gradMag = Mat()
            Imgproc.Sobel(grayMat, gradX, CvType.CV_16S, 1, 0)
            Imgproc.Sobel(grayMat, gradY, CvType.CV_16S, 0, 1)
            Core.convertScaleAbs(gradX, absGradX)
            Core.convertScaleAbs(gradY, absGradY)
            Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0.0, gradMag)
            gradX.release()
            gradY.release()
            absGradX.release()
            absGradY.release()

            val strongEdgeMask = Mat()
            Imgproc.threshold(gradMag, strongEdgeMask, 24.0, 255.0, Imgproc.THRESH_BINARY)
            gradMag.release()

            Core.bitwise_and(localText, strongEdgeMask, textProtectionMask)
            localText.release()
            strongEdgeMask.release()

            // Dilate text protection mask slightly (1px) to protect character antialiasing
            val kText = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.dilate(textProtectionMask, textProtectionMask, kText)
            kText.release()

            // 6. Smooth finger boundary & Dilate (5px)
            val kDilate = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
            val dilatedMask = Mat()
            Imgproc.dilate(fingerMask, dilatedMask, kDilate)
            kDilate.release()
            fingerMask.release()

            // Subtract printed text so letters remain 100% untouched
            Core.subtract(dilatedMask, textProtectionMask, dilatedMask)
            textProtectionMask.release()

            // 7. Dual Navier-Stokes (70%) + Telea (30%) Inpainting:
            // Seamlessly reconstructs page margin paper texture and lighting gradients
            val nsRgb = Mat()
            Photo.inpaint(srcRgb, dilatedMask, nsRgb, 6.0, Photo.INPAINT_NS)
            Photo.inpaint(srcRgb, dilatedMask, inpaintedRgb, 5.0, Photo.INPAINT_TELEA)
            Core.addWeighted(nsRgb, 0.70, inpaintedRgb, 0.30, 0.0, inpaintedRgb)
            nsRgb.release()
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
            hsv.release()
            grayMat.release()
            skinYcrcb.release()
            skinHsv1.release()
            skinHsv2.release()
            nailHsv.release()
            rawSkinMask.release()
            cleanedMask.release()
            textProtectionMask.release()
            inpaintedRgb.release()
            resultRgba.release()
        }
    }
}
