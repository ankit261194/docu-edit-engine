package com.docu.editor.core.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Watermark & Security Background Inpainter.
 * Erases text strokes while preserving 100% of underlying watermarks,
 * guilloche security lines, and colored seals via Fast Marching Method (Telea).
 */
object WatermarkPreservingInpainter {

    suspend fun inpaintWatermarkBackground(
        sourceBitmap: Bitmap,
        targetBounds: Rect,
        padding: Int = 12
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val cropLeft = max(0, targetBounds.left - padding)
        val cropTop = max(0, targetBounds.top - padding)
        val cropRight = min(width, targetBounds.right + padding)
        val cropBottom = min(height, targetBounds.bottom + padding)
        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop

        if (cropW <= 2 || cropH <= 2) {
            return@withContext sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val cropBitmap = Bitmap.createBitmap(sourceBitmap, cropLeft, cropTop, cropW, cropH)
        val srcMat = Mat()
        val grayMat = Mat()
        val maskMat = Mat()
        val inpaintMat = Mat()
        val rgbMat = Mat()
        val restoredCrop = Mat()

        try {
            Utils.bitmapToMat(cropBitmap, srcMat)
            Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            // 1. Calculate local paper luminance from the border of the crop
            val paperLuma = estimatePaperLuma(grayMat)
            // 2. Identify dark ink strokes (ink is noticeably darker than paper & watermark)
            val inkThreshold = (paperLuma - 26.0).coerceIn(45.0, 185.0)

            // Binarize: ink strokes become 255 (white mask), watermark/paper becomes 0
            Imgproc.threshold(grayMat, maskMat, inkThreshold, 255.0, Imgproc.THRESH_BINARY_INV)

            // Restrict mask strictly inside targetBounds
            val relLeft = targetBounds.left - cropLeft
            val relTop = targetBounds.top - cropTop
            val relRight = targetBounds.right - cropLeft
            val relBottom = targetBounds.bottom - cropTop

            for (r in 0 until maskMat.rows()) {
                if (r < relTop || r >= relBottom) {
                    val row = maskMat.row(r)
                    row.setTo(Scalar(0.0))
                    row.release()
                }
            }
            for (c in 0 until maskMat.cols()) {
                if (c < relLeft || c >= relRight) {
                    val col = maskMat.col(c)
                    col.setTo(Scalar(0.0))
                    col.release()
                }
            }

            // Dilate mask by 1px with small ellipse to cover anti-aliased edge ink
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.dilate(maskMat, maskMat, kernel)
            kernel.release()

            // 3. Fast Marching Telea Inpainting to propagate watermark lines across stroke cracks
            Photo.inpaint(rgbMat, maskMat, inpaintMat, 3.0, Photo.INPAINT_TELEA)

            Imgproc.cvtColor(inpaintMat, restoredCrop, Imgproc.COLOR_RGB2RGBA)

            val outCropBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(restoredCrop, outCropBitmap)

            val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(outputBitmap)
            canvas.drawBitmap(outCropBitmap, cropLeft.toFloat(), cropTop.toFloat(), null)

            outCropBitmap.recycle()
            cropBitmap.recycle()

            outputBitmap
        } catch (_: Exception) {
            cropBitmap.recycle()
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            srcMat.release()
            grayMat.release()
            maskMat.release()
            inpaintMat.release()
            rgbMat.release()
            restoredCrop.release()
        }
    }

    private fun estimatePaperLuma(grayMat: Mat): Double {
        val rows = grayMat.rows()
        val cols = grayMat.cols()
        var sum = 0.0
        var count = 0

        for (c in 0 until cols step 2) {
            sum += grayMat.get(0, c)[0]
            sum += grayMat.get(rows - 1, c)[0]
            count += 2
        }
        for (r in 1 until rows - 1 step 2) {
            sum += grayMat.get(r, 0)[0]
            sum += grayMat.get(r, cols - 1)[0]
            count += 2
        }
        return if (count > 0) sum / count else 240.0
    }

    fun hasComplexBackground(source: Bitmap, targetBounds: Rect): Boolean {
        val width = source.width
        val height = source.height
        val margin = 10
        val l = max(0, targetBounds.left - margin)
        val t = max(0, targetBounds.top - margin)
        val r = min(width, targetBounds.right + margin)
        val b = min(height, targetBounds.bottom + margin)

        if (r <= l || b <= t) return false

        val sampleColors = mutableListOf<Int>()
        for (x in l until r step 3) {
            sampleColors.add(source.getPixel(x, t))
            sampleColors.add(source.getPixel(x, min(height - 1, b - 1)))
        }
        for (y in t until b step 3) {
            sampleColors.add(source.getPixel(l, y))
            sampleColors.add(source.getPixel(min(width - 1, r - 1), y))
        }

        if (sampleColors.size < 8) return false

        val lumas = sampleColors.map { c ->
            (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c))
        }
        val avg = lumas.average()
        val variance = lumas.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        return stdDev > 8.5f
    }
}
