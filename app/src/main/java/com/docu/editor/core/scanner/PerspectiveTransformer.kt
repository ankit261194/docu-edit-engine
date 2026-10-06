package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import com.docu.editor.core.scanner.model.DocumentCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PerspectiveTransformer {

    private const val MAX_PRINT_DIMENSION = 2880 // 4K CamScanner print grade (OOM-Safe)

    /**
     * Applies 4-point perspective warp to deskew, straighten, and crop the document.
     * Features:
     * - Automatic geometric sorting to prevent inverted quad distortions.
     * - Aspect-ratio preserving dimension clamping to eliminate OutOfMemory crashes on 50MP+ sensors.
     * - High-fidelity INTER_CUBIC perspective interpolation with border replication.
     */
    suspend fun warpPerspective(
        source: Bitmap,
        corners: DocumentCorners
    ): Bitmap = withContext(Dispatchers.Default) {
        // 1. Geometrically re-order corners to guarantee proper Quad topology
        val sortedCorners = sortCornersClockwise(corners)
        val tl = sortedCorners.topLeft
        val tr = sortedCorners.topRight
        val br = sortedCorners.bottomRight
        val bl = sortedCorners.bottomLeft

        // 2. Calculate canonical destination dimensions
        val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthBottom = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        var rawTargetWidth = max(widthTop, widthBottom).roundToInt().coerceAtLeast(100)

        val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        var rawTargetHeight = max(heightLeft, heightRight).roundToInt().coerceAtLeast(100)

        // 3. Clamp dimensions to MAX_PRINT_DIMENSION to prevent JVM OOM crash
        val maxDim = max(rawTargetWidth, rawTargetHeight)
        val scale = if (maxDim > MAX_PRINT_DIMENSION) {
            MAX_PRINT_DIMENSION.toDouble() / maxDim
        } else {
            1.0
        }
        val targetWidth = (rawTargetWidth * scale).roundToInt().coerceAtLeast(100)
        val targetHeight = (rawTargetHeight * scale).roundToInt().coerceAtLeast(100)

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

            // 4. High-fidelity perspective warp
            Imgproc.warpPerspective(
                srcMat,
                dstMat,
                transformMat,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                Imgproc.INTER_CUBIC,
                org.opencv.core.Core.BORDER_REPLICATE
            )

            // 5. Memory-safe bitmap allocation
            try {
                val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(dstMat, resultBitmap)
                resultBitmap
            } catch (oom: OutOfMemoryError) {
                // Graceful fallback to half-resolution if device memory is critically constrained
                val safeW = targetWidth / 2
                val safeH = targetHeight / 2
                val halfMat = Mat()
                Imgproc.resize(dstMat, halfMat, Size(safeW.toDouble(), safeH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                val safeBitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(halfMat, safeBitmap)
                halfMat.release()
                safeBitmap
            }
        } finally {
            srcMat.release()
            dstMat.release()
            transformMat.release()
        }
    }

    /**
     * Sorts 4 points into canonical order (TopLeft, TopRight, BottomRight, BottomLeft)
     * using sum (x + y) and difference (x - y) projections to guarantee convex topology.
     */
    private fun sortCornersClockwise(corners: DocumentCorners): DocumentCorners {
        val pts = listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
        val tl = pts.minByOrNull { it.x + it.y } ?: corners.topLeft
        val br = pts.maxByOrNull { it.x + it.y } ?: corners.bottomRight
        val tr = pts.maxByOrNull { it.x - it.y } ?: corners.topRight
        val bl = pts.minByOrNull { it.x - it.y } ?: corners.bottomLeft

        return DocumentCorners(topLeft = tl, topRight = tr, bottomRight = br, bottomLeft = bl)
    }
}
