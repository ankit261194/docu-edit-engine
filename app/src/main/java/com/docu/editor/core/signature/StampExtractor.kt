package com.docu.editor.core.signature

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

object StampExtractor {

    enum class StampColorTarget {
        RED_STAMP,
        BLUE_PURPLE_STAMP,
        GREEN_STAMP
    }

    /**
     * Extracts circular/rectangular stamps or seals using HSV chrominance segmentation,
     * discarding paper background and black document text, and crops to stamp bounds.
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
                    // Violet/Blue stamp ink (typically H: 95 to 155)
                    Core.inRange(hsv, Scalar(95.0, 40.0, 40.0), Scalar(155.0, 255.0, 255.0), finalMask)
                }
                StampColorTarget.GREEN_STAMP -> {
                    // Green stamp ink (typically H: 35 to 85)
                    Core.inRange(hsv, Scalar(35.0, 45.0, 45.0), Scalar(85.0, 255.0, 255.0), finalMask)
                }
            }

            // Morphological smoothing to clean micro-holes
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(3.0, 3.0))
            Imgproc.morphologyEx(finalMask, finalMask, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            val smoothMask = Mat()
            Imgproc.GaussianBlur(finalMask, smoothMask, org.opencv.core.Size(3.0, 3.0), 0.8)
            val maskBytes = ByteArray((smoothMask.total() * smoothMask.channels()).toInt())
            smoothMask.get(0, 0, maskBytes)
            smoothMask.release()

            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val pixels = IntArray(width * height)
            sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val outPixels = IntArray(width * height)
            for (i in pixels.indices) {
                val alpha = maskBytes[i].toInt() and 0xFF
                if (alpha > 12) {
                    val orig = pixels[i]
                    outPixels[i] = (alpha shl 24) or (orig and 0x00FFFFFF)
                } else {
                    outPixels[i] = Color.TRANSPARENT
                }
            }

            val transparentStamp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            transparentStamp.setPixels(outPixels, 0, width, 0, 0, width, height)
            SignatureExtractor.cropToTransparentBounds(transparentStamp, paddingPx = 16)
        } finally {
            srcRgba.release()
            hsv.release()
            mask1.release()
            mask2.release()
            finalMask.release()
        }
    }
}
