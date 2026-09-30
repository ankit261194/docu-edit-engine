package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import com.docu.editor.core.scanner.model.DocumentCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object DocumentEdgeDetector {

    /**
     * Detects the 4 corners of a document in the image using OpenCV edge and contour analysis.
     */
    suspend fun detectCorners(bitmap: Bitmap): DocumentCorners = withContext(Dispatchers.Default) {
        val srcMat = Mat()
        val grayMat = Mat()

        try {
            Utils.bitmapToMat(bitmap, srcMat)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val detected = detectCornersFromGrayMat(grayMat, bitmap.width, bitmap.height)
            if (detected != null) {
                detected
            } else {
                // Fallback: 6% padded rectangle inside the frame
                val padX = bitmap.width * 0.06f
                val padY = bitmap.height * 0.06f
                DocumentCorners(
                    topLeft = PointF(padX, padY),
                    topRight = PointF(bitmap.width - padX, padY),
                    bottomRight = PointF(bitmap.width - padX, bitmap.height - padY),
                    bottomLeft = PointF(padX, bitmap.height - padY)
                )
            }
        } finally {
            srcMat.release()
            grayMat.release()
        }
    }

    /**
     * Real-time high-speed corner detector operating directly on CameraX Y-plane Mat (0ms bitmap conversion).
     * Eliminates internal text interference via morphological closing and convex hulling.
     */
    fun detectCornersFromGrayMat(grayMat: Mat, origWidth: Int, origHeight: Int): DocumentCorners? {
        val targetWidth = 480.0
        val scale = if (origWidth > targetWidth) targetWidth / origWidth else 1.0
        val procWidth = (origWidth * scale).toInt()
        val procHeight = (origHeight * scale).toInt()

        val smallGray = Mat()
        val closedMat = Mat()
        val blurredMat = Mat()
        val cannedMat = Mat()
        val gradMat = Mat()
        val otsuMat = Mat()
        val combinedEdges = Mat()
        val dilatedMat = Mat()

        try {
            if (scale < 1.0) {
                Imgproc.resize(grayMat, smallGray, Size(procWidth.toDouble(), procHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            } else {
                grayMat.copyTo(smallGray)
            }

            // 1. Morphological Close with 15x15 kernel to completely blend black text characters into white paper
            val textEraseKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
            Imgproc.morphologyEx(smallGray, closedMat, Imgproc.MORPH_CLOSE, textEraseKernel)
            textEraseKernel.release()

            // 2. Gaussian blur to remove any remaining background high frequencies
            Imgproc.GaussianBlur(closedMat, blurredMat, Size(7.0, 7.0), 0.0)

            // 3A. Canny edge detection focused on strong paper borders
            Imgproc.Canny(blurredMat, cannedMat, 25.0, 90.0)

            // 3B. Morphological Gradient + Otsu Thresholding (Guarantees detection on white paper on white/light desks)
            val gradKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.morphologyEx(blurredMat, gradMat, Imgproc.MORPH_GRADIENT, gradKernel)
            gradKernel.release()
            Imgproc.threshold(gradMat, otsuMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            // 3C. Bitwise OR: Combine Canny edges with shadow step boundary edges
            org.opencv.core.Core.bitwise_or(cannedMat, otsuMat, combinedEdges)

            // 4. Dilate to seal any slight gaps on the paper edge
            val edgeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(combinedEdges, dilatedMat, edgeKernel)
            edgeKernel.release()

            // 5. Find outer contours
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilatedMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            hierarchy.release()

            val frameArea = procWidth * procHeight
            val minArea = frameArea * 0.12 // Paper must occupy at least 12% of frame
            val maxAllowedArea = frameArea * 0.98

            var maxArea = 0.0
            var bestQuad: Array<Point>? = null

            for (contour in contours) {
                val contourArea = Imgproc.contourArea(contour)
                if (contourArea < minArea || contourArea > maxAllowedArea) {
                    contour.release()
                    continue
                }

                // Compute Convex Hull to smooth finger grips or small boundary jaggedness
                val hullIndices = MatOfInt()
                Imgproc.convexHull(contour, hullIndices)
                val contourPoints = contour.toArray()
                val hullPoints = hullIndices.toArray().map { contourPoints[it] }.toTypedArray()
                hullIndices.release()

                val hullMat = MatOfPoint2f(*hullPoints)
                val peri = Imgproc.arcLength(hullMat, true)
                var foundQuad: Array<Point>? = null

                // Adaptive multi-scale approximation: 0.02, 0.03, 0.045
                for (eps in doubleArrayOf(0.02, 0.03, 0.045)) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(hullMat, approx, eps * peri, true)
                    if (approx.total() == 4L) {
                        val mop = MatOfPoint(*approx.toArray())
                        if (Imgproc.isContourConvex(mop)) {
                            val pts = approx.toArray()
                            if (isValidDocumentQuad(pts, procWidth, procHeight)) {
                                foundQuad = pts
                                mop.release()
                                approx.release()
                                break
                            }
                        }
                        mop.release()
                    }
                    approx.release()
                }

                if (foundQuad != null && contourArea > maxArea) {
                    maxArea = contourArea
                    bestQuad = foundQuad
                }

                hullMat.release()
                contour.release()
            }

            if (bestQuad != null) {
                // Scale back to original resolution
                val invScale = 1.0 / scale
                val scaledPoints = bestQuad.map { Point(it.x * invScale, it.y * invScale) }.toTypedArray()
                return sortCorners(scaledPoints)
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            smallGray.release()
            closedMat.release()
            blurredMat.release()
            cannedMat.release()
            gradMat.release()
            otsuMat.release()
            combinedEdges.release()
            dilatedMat.release()
        }
    }

    /**
     * Validates that the 4 points form a realistic document quadrilateral (not a narrow sliver or degenerate box).
     */
    private fun isValidDocumentQuad(pts: Array<Point>, w: Int, h: Int): Boolean {
        if (pts.size != 4) return false
        val edge1 = hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
        val edge2 = hypot(pts[1].x - pts[2].x, pts[1].y - pts[2].y)
        val edge3 = hypot(pts[2].x - pts[3].x, pts[2].y - pts[3].y)
        val edge4 = hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y)

        val minEdge = minOf(edge1, edge2, edge3, edge4)
        val maxEdge = maxOf(edge1, edge2, edge3, edge4)

        if (minEdge < 40.0) return false
        // Aspect ratio between max edge and min edge should be reasonable (< 3.0)
        if (maxEdge / minEdge > 3.0) return false

        return true
    }

    /**
     * Orders 4 vertices into [Top-Left, Top-Right, Bottom-Right, Bottom-Left].
     */
    private fun sortCorners(pts: Array<Point>): DocumentCorners {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()

        val remaining = pts.filter { it != tl && it != br }
        val sortedByDiff = remaining.sortedBy { it.y - it.x }
        val tr = sortedByDiff.first()
        val bl = sortedByDiff.last()

        return DocumentCorners(
            topLeft = PointF(tl.x.toFloat(), tl.y.toFloat()),
            topRight = PointF(tr.x.toFloat(), tr.y.toFloat()),
            bottomRight = PointF(br.x.toFloat(), br.y.toFloat()),
            bottomLeft = PointF(bl.x.toFloat(), bl.y.toFloat())
        )
    }
}
