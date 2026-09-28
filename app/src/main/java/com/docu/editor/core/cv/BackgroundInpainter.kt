package com.docu.editor.core.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min

class BackgroundInpainter {

    suspend fun inpaint(
        sourceBitmap: Bitmap,
        targetBounds: Rect,
        inpaintRadius: Double = 3.5,
        roiPaddingPx: Int = 24
    ): Bitmap = withContext(Dispatchers.Default) {
        val roi = calculatePaddedRoi(sourceBitmap, targetBounds, roiPaddingPx)
        val roiWidth = roi.width()
        val roiHeight = roi.height()

        if (roiWidth <= 0 || roiHeight <= 0) {
            return@withContext sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val roiBitmap = Bitmap.createBitmap(sourceBitmap, roi.left, roi.top, roiWidth, roiHeight)

        val srcRgba = Mat()
        val srcRgb = Mat()
        val mask = Mat()
        val inpaintedRgb = Mat()
        val resultRgba = Mat()

        try {
            Utils.bitmapToMat(roiBitmap, srcRgba)
            org.opencv.imgproc.Imgproc.cvtColor(srcRgba, srcRgb, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)

            val relativeTarget = Rect(
                targetBounds.left - roi.left,
                targetBounds.top - roi.top,
                targetBounds.right - roi.left,
                targetBounds.bottom - roi.top
            )

            PrecisionMaskBuilder.buildShadowFreeMask(srcRgb, relativeTarget, mask)

            Photo.inpaint(
                srcRgb,
                mask,
                inpaintedRgb,
                inpaintRadius,
                Photo.INPAINT_TELEA
            )

            org.opencv.imgproc.Imgproc.cvtColor(inpaintedRgb, resultRgba, org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA)
            val cleanedRoiBitmap = Bitmap.createBitmap(roiWidth, roiHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, cleanedRoiBitmap)

            val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(outputBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(cleanedRoiBitmap, roi.left.toFloat(), roi.top.toFloat(), paint)

            roiBitmap.recycle()
            cleanedRoiBitmap.recycle()

            outputBitmap
        } finally {
            srcRgba.release()
            srcRgb.release()
            mask.release()
            inpaintedRgb.release()
            resultRgba.release()
        }
    }

    private fun calculatePaddedRoi(source: Bitmap, target: Rect, padding: Int): Rect {
        val left = max(0, target.left - padding)
        val top = max(0, target.top - padding)
        val right = min(source.width, target.right + padding)
        val bottom = min(source.height, target.bottom + padding)
        return Rect(left, top, right, bottom)
    }
}
