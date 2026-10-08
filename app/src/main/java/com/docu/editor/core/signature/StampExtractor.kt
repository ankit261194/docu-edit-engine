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
 * Enterprise Rubber Stamp & Official Seal Extraction Engine with Text-Under-Stamp De-Occlusion.
 * Isolates official circular/oval/rectangular stamps and seals from complex scanned paperwork.
 * Separates overlapping low-saturation black printed text pixels (S < 38, V < 135)
 * and reconstructs/inpaints pure authentic stamp ink so printed letters or ledger lines
 * do not contaminate the extracted transparent seal.
 */
object StampExtractor {

    enum class StampColorTarget(val defaultRgb: Int) {
        RED_STAMP(Color.rgb(210, 38, 45)),
        BLUE_PURPLE_STAMP(Color.rgb(32, 68, 195)),
        GREEN_STAMP(Color.rgb(16, 135, 75))
    }

    /**
     * Extracts circular/rectangular stamps or seals using HSV chrominance segmentation,
     * performs Text-Under-Stamp De-Occlusion to eliminate overlapping black text contamination,
     * and crops to exact stamp bounds with 100% alpha transparency.
     */
    suspend fun extractStamp(
        sourceBitmap: Bitmap,
        targetColor: StampColorTarget = StampColorTarget.RED_STAMP,
        deOccludeOverlappingText: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        val srcRgba = Mat()
        val rgbMat = Mat()
        val hsv = Mat()
        val mask1 = Mat()
        val mask2 = Mat()
        val stampMask = Mat()
        val blackTextMask = Mat()
        val dilatedStamp = Mat()
        val occludedPixels = Mat()
        val combinedMask = Mat()
        val smoothMask = Mat()

        try {
            Utils.bitmapToMat(sourceBitmap, srcRgba)
            Imgproc.cvtColor(srcRgba, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgbMat, hsv, Imgproc.COLOR_RGB2HSV)

            when (targetColor) {
                StampColorTarget.RED_STAMP -> {
                    // Red wraps around 0 and 180 in HSV
                    Core.inRange(hsv, Scalar(0.0, 42.0, 40.0), Scalar(12.0, 255.0, 255.0), mask1)
                    Core.inRange(hsv, Scalar(168.0, 42.0, 40.0), Scalar(180.0, 255.0, 255.0), mask2)
                    Core.bitwise_or(mask1, mask2, stampMask)
                }
                StampColorTarget.BLUE_PURPLE_STAMP -> {
                    // Violet/Blue/Indigo stamp ink (typically H: 92 to 158)
                    Core.inRange(hsv, Scalar(92.0, 35.0, 35.0), Scalar(158.0, 255.0, 255.0), stampMask)
                }
                StampColorTarget.GREEN_STAMP -> {
                    // Green stamp ink (typically H: 35 to 88)
                    Core.inRange(hsv, Scalar(35.0, 38.0, 35.0), Scalar(88.0, 255.0, 255.0), stampMask)
                }
            }

            // Morphological smoothing to clean micro-holes in stamp strokes
            val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.morphologyEx(stampMask, stampMask, Imgproc.MORPH_CLOSE, closeKernel)
            closeKernel.release()

            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val totalPixels = width * height
            val origPixels = IntArray(totalPixels)
            sourceBitmap.getPixels(origPixels, 0, width, 0, 0, width, height)

            // Sample authentic stamp ink color from un-occluded stamp pixels
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var sampleCount = 0L
            val stampBytes = ByteArray(totalPixels)
            stampMask.get(0, 0, stampBytes)

            for (i in 0 until totalPixels) {
                if ((stampBytes[i].toInt() and 0xFF) > 128) {
                    val p = origPixels[i]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val brightness = (r + g + b) / 3
                    if (brightness > 40) {
                        sumR += r
                        sumG += g
                        sumB += b
                        sampleCount++
                    }
                }
            }

            val restoredStampColor = if (sampleCount > 50) {
                Color.rgb((sumR / sampleCount).toInt(), (sumG / sampleCount).toInt(), (sumB / sampleCount).toInt())
            } else {
                targetColor.defaultRgb
            }

            val restoredR = (restoredStampColor shr 16) and 0xFF
            val restoredG = (restoredStampColor shr 8) and 0xFF
            val restoredB = restoredStampColor and 0xFF

            val occludedBytes = ByteArray(totalPixels)

            if (deOccludeOverlappingText) {
                // Low saturation printed black text: S < 38, V between 15 and 135
                Core.inRange(hsv, Scalar(0.0, 0.0, 15.0), Scalar(180.0, 38.0, 135.0), blackTextMask)

                // Dilate stamp stroke region by 7px to find intersecting text
                val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
                Imgproc.dilate(stampMask, dilatedStamp, dilateKernel)
                dilateKernel.release()

                // Intersect dilated stamp with black text mask -> exact occluded stamp pixels
                Core.bitwise_and(dilatedStamp, blackTextMask, occludedPixels)
                occludedPixels.get(0, 0, occludedBytes)

                // Combine un-occluded stamp mask with de-occluded pixels
                Core.bitwise_or(stampMask, occludedPixels, combinedMask)
            } else {
                stampMask.copyTo(combinedMask)
            }

            // Anti-aliased Gaussian feathering for smooth stamp perimeter
            Imgproc.GaussianBlur(combinedMask, smoothMask, Size(3.0, 3.0), 0.75)
            val smoothBytes = ByteArray(totalPixels)
            smoothMask.get(0, 0, smoothBytes)

            val outPixels = IntArray(totalPixels)
            for (i in 0 until totalPixels) {
                val alpha = smoothBytes[i].toInt() and 0xFF
                if (alpha > 14) {
                    val isOccluded = (occludedBytes[i].toInt() and 0xFF) > 100
                    if (isOccluded) {
                        // De-occlude: replace dark text pixel with pure authentic stamp ink
                        outPixels[i] = (alpha shl 24) or (restoredR shl 16) or (restoredG shl 8) or restoredB
                    } else {
                        val orig = origPixels[i]
                        outPixels[i] = (alpha shl 24) or (orig and 0x00FFFFFF)
                    }
                } else {
                    outPixels[i] = Color.TRANSPARENT
                }
            }

            val transparentStamp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            transparentStamp.setPixels(outPixels, 0, width, 0, 0, width, height)
            SignatureExtractor.cropToTransparentBounds(transparentStamp, paddingPx = 16)
        } finally {
            srcRgba.release()
            rgbMat.release()
            hsv.release()
            mask1.release()
            mask2.release()
            stampMask.release()
            blackTextMask.release()
            dilatedStamp.release()
            occludedPixels.release()
            combinedMask.release()
            smoothMask.release()
        }
    }
}
