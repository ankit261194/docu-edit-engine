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
import org.opencv.imgproc.Imgproc

object StampExtractor {

    enum class StampColorTarget {
        RED_STAMP,
        BLUE_PURPLE_STAMP
    }

    /**
     * Extracts circular/rectangular stamps or seals using HSV chrominance segmentation,
     * discarding paper background and black document text.
     */
    suspend fun extractStamp(
        sourceBitmap: Bitmap,
        targetColor: StampColorTarget = StampColorTarget.RED_STAMP
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcRgba = Mat()
        val hsv = Mat()
        val mask1 = Mat()
        val mask2 = Mat()
        val finalMask = Mat()

        try {
            Utils.bitmapToMat(sourceBitmap, srcRgba)
            Imgproc.cvtColor(srcRgba, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)

            when (targetColor) {
                StampColorTarget.RED_STAMP -> {
                    // Red wraps around 0 and 180 in HSV
                    Core.inRange(hsv, Scalar(0.0, 50.0, 50.0), Scalar(10.0, 255.0, 255.0), mask1)
                    Core.inRange(hsv, Scalar(170.0, 50.0, 50.0), Scalar(180.0, 255.0, 255.0), mask2)
                    Core.bitwise_or(mask1, mask2, finalMask)
                }
                StampColorTarget.BLUE_PURPLE_STAMP -> {
                    // Violet/Blue stamp ink (typically H: 100 to 150)
                    Core.inRange(hsv, Scalar(100.0, 45.0, 45.0), Scalar(155.0, 255.0, 255.0), finalMask)
                }
            }

            // Morphological smoothing to clean micro-holes
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(3.0, 3.0))
            Imgproc.morphologyEx(finalMask, finalMask, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            val maskBytes = ByteArray((finalMask.total() * finalMask.channels()).toInt())
            finalMask.get(0, 0, maskBytes)

            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val pixels = IntArray(width * height)
            sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val outPixels = IntArray(width * height)
            for (i in pixels.indices) {
                val isStamp = (maskBytes[i].toInt() and 0xFF) > 128
                if (isStamp) {
                    outPixels[i] = pixels[i] // Preserve original stamp ink color & texture
                } else {
                    outPixels[i] = Color.TRANSPARENT
                }
            }

            val transparentStamp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            transparentStamp.setPixels(outPixels, 0, width, 0, 0, width, height)
            transparentStamp
        } finally {
            srcRgba.release()
            hsv.release()
            mask1.release()
            mask2.release()
            finalMask.release()
        }
    }
}
