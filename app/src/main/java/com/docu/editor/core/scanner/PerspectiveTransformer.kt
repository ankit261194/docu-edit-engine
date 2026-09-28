package com.docu.editor.core.scanner

import android.graphics.Bitmap
import com.docu.editor.core.scanner.model.DocumentCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PerspectiveTransformer {

    /**
     * Applies 4-point perspective warp to deskew, straighten, and crop the document.
     */
    suspend fun warpPerspective(
        source: Bitmap,
        corners: DocumentCorners
    ): Bitmap = withContext(Dispatchers.Default) {
        val tl = corners.topLeft
        val tr = corners.topRight
        val br = corners.bottomRight
        val bl = corners.bottomLeft

        // Calculate destination width: maximum of top and bottom edge lengths
        val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthBottom = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val targetWidth = max(widthTop, widthBottom).roundToInt().coerceAtLeast(100)

        // Calculate destination height: maximum of left and right edge lengths
        val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        val targetHeight = max(heightLeft, heightRight).roundToInt().coerceAtLeast(100)

        val srcMat = Mat()
        val dstMat = Mat()
        val transformMat = Mat()

        try {
            Utils.bitmapToMat(source, srcMat)

            // Source points matrix (4x2 CV_32FC2)
            val srcPoints = Mat(4, 1, CvType.CV_32FC2)
            srcPoints.put(
                0, 0,
                tl.x.toDouble(), tl.y.toDouble(),
                tr.x.toDouble(), tr.y.toDouble(),
                br.x.toDouble(), br.y.toDouble(),
                bl.x.toDouble(), bl.y.toDouble()
            )

            // Destination canonical rectangular points
            val dstPoints = Mat(4, 1, CvType.CV_32FC2)
            dstPoints.put(
                0, 0,
                0.0, 0.0,
                targetWidth.toDouble(), 0.0,
                targetWidth.toDouble(), targetHeight.toDouble(),
                0.0, targetHeight.toDouble()
            )

            val pTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            pTransform.copyTo(transformMat)
            pTransform.release()
            srcPoints.release()
            dstPoints.release()

            // Warp perspective
            Imgproc.warpPerspective(
                srcMat,
                dstMat,
                transformMat,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                Imgproc.INTER_CUBIC,
                org.opencv.core.Core.BORDER_REPLICATE
            )

            val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dstMat, resultBitmap)
            resultBitmap
        } finally {
            srcMat.release()
            dstMat.release()
            transformMat.release()
        }
    }
}
