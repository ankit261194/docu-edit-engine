package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import com.docu.editor.core.scanner.model.DocumentCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

object DocumentEdgeDetector {

    /**
     * Detects the 4 corners of a document in the image using OpenCV edge and contour analysis.
     */
    suspend fun detectCorners(bitmap: Bitmap): DocumentCorners = withContext(Dispatchers.Default) {
        val srcMat = Mat()
        val grayMat = Mat()
        val blurredMat = Mat()
        val cannedMat = Mat()
        val dilatedMat = Mat()

        try {
            Utils.bitmapToMat(bitmap, srcMat)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            // 1. Bilateral filter or Gaussian blur to smooth texture while keeping boundary edges
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(9.0, 9.0), 0.0)

            // 2. Canny Edge Detection with Otsu-guided thresholds
            Imgproc.Canny(blurredMat, cannedMat, 50.0, 150.0)

            // 3. Dilate slightly to connect broken edge lines
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(cannedMat, dilatedMat, kernel)
            kernel.release()

            // 4. Find all outer contours
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

            val minArea = (bitmap.width * bitmap.height) * 0.15 // Document must be at least 15% of frame
            var maxArea = 0.0
            var bestQuad: MatOfPoint2f? = null

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()

                // Approximate polygon with epsilon
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                val area = Imgproc.contourArea(approx)
                if (approx.total() == 4L && area > minArea && area > maxArea) {
                    // Check if convex
                    val mop = MatOfPoint(*approx.toArray())
                    if (Imgproc.isContourConvex(mop)) {
                        maxArea = area
                        bestQuad?.release()
                        bestQuad = approx
                    } else {
                        approx.release()
                    }
                    mop.release()
                } else {
                    approx.release()
                }
                contour2f.release()
                contour.release()
            }

            if (bestQuad != null) {
                val points = bestQuad.toArray()
                bestQuad.release()
                sortCorners(points)
            } else {
                // Fallback: 8% padded rectangle inside the frame
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
            blurredMat.release()
            cannedMat.release()
            dilatedMat.release()
        }
    }

    /**
     * Real-time high-speed corner detector operating directly on CameraX Y-plane Mat (0ms bitmap conversion).
     */
    fun detectCornersFromGrayMat(grayMat: Mat, width: Int, height: Int): DocumentCorners? {
        val blurredMat = Mat()
        val cannedMat = Mat()
        val dilatedMat = Mat()

        try {
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(7.0, 7.0), 0.0)
            Imgproc.Canny(blurredMat, cannedMat, 40.0, 120.0)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(cannedMat, dilatedMat, kernel)
            kernel.release()

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

            val minArea = (width * height) * 0.12
            var maxArea = 0.0
            var bestQuad: MatOfPoint2f? = null

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()

                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
                val area = Imgproc.contourArea(approx)

                if (approx.total() == 4L && area > minArea && area > maxArea) {
                    val mop = MatOfPoint(*approx.toArray())
                    if (Imgproc.isContourConvex(mop)) {
                        maxArea = area
                        bestQuad?.release()
                        bestQuad = approx
                    } else {
                        approx.release()
                    }
                    mop.release()
                } else {
                    approx.release()
                }
                contour2f.release()
                contour.release()
            }

            return if (bestQuad != null) {
                val points = bestQuad.toArray()
                bestQuad.release()
                sortCorners(points)
            } else {
                null
            }
        } catch (_: Exception) {
            return null
        } finally {
            blurredMat.release()
            cannedMat.release()
            dilatedMat.release()
        }
    }

    /**
     * Orders 4 vertices into [Top-Left, Top-Right, Bottom-Right, Bottom-Left].
     */
    private fun sortCorners(pts: Array<Point>): DocumentCorners {
        // Sum (x + y): Top-Left has smallest sum, Bottom-Right has largest sum
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()

        // Diff (y - x): Top-Right has smallest diff (or x - y largest), Bottom-Left has largest diff
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
