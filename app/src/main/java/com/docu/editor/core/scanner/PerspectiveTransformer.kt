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
import kotlin.math.min
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

        // 2. Aspect Ratio Auto-Resolution (Compensates perspective foreshortening & snaps canonical ratios)
        val (rawTargetWidth, rawTargetHeight) = resolveCanonicalDimensions(sortedCorners)

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

            // 4. Ultra-high-fidelity Lanczos-4 Bicubic Resampling (sharp anti-aliased text)
            Imgproc.warpPerspective(
                srcMat,
                dstMat,
                transformMat,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                Imgproc.INTER_LANCZOS4,
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

    /**
     * Resolves canonical document aspect ratio (A4 1:1.414, US Legal 1:1.647, ID-1 1:1.586, US Letter 1:1.294)
     * by compensating for perspective foreshortening.
     */
    fun resolveCanonicalDimensions(corners: DocumentCorners): Pair<Int, Int> {
        val sorted = sortCornersClockwise(corners)
        val tl = sorted.topLeft
        val tr = sorted.topRight
        val br = sorted.bottomRight
        val bl = sorted.bottomLeft

        val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthBottom = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())

        val avgWidth = (widthTop + widthBottom) / 2.0
        val avgHeight = (heightLeft + heightRight) / 2.0

        val convergenceRatio = if (widthTop > 0 && widthBottom > 0) {
            max(widthTop, widthBottom) / min(widthTop, widthBottom)
        } else 1.0

        val correctedHeight = avgHeight * (1.0 + (convergenceRatio - 1.0) * 0.45)
        val rawRatio = if (avgWidth > 0) max(avgWidth, correctedHeight) / min(avgWidth, correctedHeight) else 1.414

        val isoA4Ratio = 1.4142        // ISO 216 A4 / A5 (297 / 210)
        val usLegalRatio = 1.6470      // US Legal (14.0 / 8.5)
        val idCardRatio = 1.5857       // ISO/IEC 7810 ID-1 (85.60 / 53.98)
        val usLetterRatio = 1.2941     // US Letter (11.0 / 8.5)

        val isHeightDominant = correctedHeight >= avgWidth

        val targetRatio = when {
            kotlin.math.abs(rawRatio - isoA4Ratio) / isoA4Ratio <= 0.085 -> isoA4Ratio
            kotlin.math.abs(rawRatio - usLegalRatio) / usLegalRatio <= 0.080 -> usLegalRatio
            kotlin.math.abs(rawRatio - idCardRatio) / idCardRatio <= 0.075 -> idCardRatio
            kotlin.math.abs(rawRatio - usLetterRatio) / usLetterRatio <= 0.075 -> usLetterRatio
            else -> rawRatio
        }

        return if (isHeightDominant) {
            val w = max(widthTop, widthBottom).roundToInt().coerceAtLeast(100)
            val h = (w * targetRatio).roundToInt().coerceAtLeast(100)
            Pair(w, h)
        } else {
            val h = max(heightLeft, heightRight).roundToInt().coerceAtLeast(100)
            val w = (h * targetRatio).roundToInt().coerceAtLeast(100)
            Pair(w, h)
        }
    }
}
