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
            detected ?: computeCenteredA4Corners(bitmap.width.toFloat(), bitmap.height.toFloat())
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
        val claheMat = Mat()
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

            // 0. Morphological Illumination Division (Shadow Annihilation)
            val bgMat = Mat()
            val illumKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(35.0, 35.0))
            Imgproc.morphologyEx(smallGray, bgMat, Imgproc.MORPH_CLOSE, illumKernel)
            illumKernel.release()

            val normGray = Mat()
            org.opencv.core.Core.divide(smallGray, bgMat, normGray, 255.0)
            bgMat.release()

            // 1. CLAHE Contrast Equalization on shadow-normalized surface
            val clahe = Imgproc.createCLAHE(3.5, Size(8.0, 8.0))
            clahe.apply(normGray, claheMat)
            normGray.release()

            // 2. Morphological Close with 15x15 kernel to completely blend black text characters into white paper
            val textEraseKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
            Imgproc.morphologyEx(claheMat, closedMat, Imgproc.MORPH_CLOSE, textEraseKernel)
            textEraseKernel.release()

            // 3. Gaussian blur to remove any remaining background high frequencies
            Imgproc.GaussianBlur(closedMat, blurredMat, Size(7.0, 7.0), 0.0)

            // 4A. Canny edge detection focused on strong paper borders
            Imgproc.Canny(blurredMat, cannedMat, 20.0, 80.0)

            // 4B. Morphological Gradient + Otsu Thresholding (Guarantees detection on white paper on white/light desks)
            val gradKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.morphologyEx(blurredMat, gradMat, Imgproc.MORPH_GRADIENT, gradKernel)
            gradKernel.release()
            Imgproc.threshold(gradMat, otsuMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            // 4C. Bitwise OR: Combine Canny edges with shadow step boundary edges
            org.opencv.core.Core.bitwise_or(cannedMat, otsuMat, combinedEdges)

            // 5. Dilate to seal any slight gaps on the paper edge
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

                // Adaptive multi-scale approximation: 0.015, 0.02, 0.03, 0.045
                for (eps in doubleArrayOf(0.015, 0.02, 0.03, 0.045)) {
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

                // If exact 4 vertices weren't formed (e.g. slight corner fold or shadow curve),
                // extract 4 corner extrema projections from the convex hull
                if (foundQuad == null && hullPoints.size >= 4) {
                    val tl = hullPoints.minByOrNull { it.x + it.y }
                    val br = hullPoints.maxByOrNull { it.x + it.y }
                    val tr = hullPoints.maxByOrNull { it.x - it.y }
                    val bl = hullPoints.minByOrNull { it.x - it.y }
                    if (tl != null && br != null && tr != null && bl != null) {
                        val candidateQuad = arrayOf(tl, tr, br, bl)
                        if (isValidDocumentQuad(candidateQuad, procWidth, procHeight)) {
                            foundQuad = candidateQuad
                        }
                    }
                }

                if (foundQuad != null && contourArea > maxArea) {
                    maxArea = contourArea
                    bestQuad = foundQuad
                }

                hullMat.release()
                contour.release()
            }

            // Hough Line Fallback if contours were fragmented by low-contrast lighting
            if (bestQuad == null) {
                bestQuad = detectCornersFromHoughLines(dilatedMat, procWidth, procHeight)
            }

            if (bestQuad != null) {
                // Sub-pixel corner refinement against smallGray gradient
                try {
                    val cornersMat = MatOfPoint2f(*bestQuad)
                    val term = org.opencv.core.TermCriteria(
                        org.opencv.core.TermCriteria.EPS or org.opencv.core.TermCriteria.COUNT,
                        30,
                        0.05
                    )
                    Imgproc.cornerSubPix(
                        smallGray,
                        cornersMat,
                        Size(5.0, 5.0),
                        Size(-1.0, -1.0),
                        term
                    )
                    bestQuad = cornersMat.toArray()
                    cornersMat.release()
                } catch (_: Exception) {}

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
            claheMat.release()
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
     * Calculates an authentic, centered ISO A4 / Document aspect ratio bounding frame
     * used when contrast is flat (e.g., white paper on white sheets or glossy surfaces).
     */
    fun computeCenteredA4Corners(w: Float, h: Float): DocumentCorners {
        val isPortrait = h >= w
        val a4Ratio = 1.414f

        val docW: Float
        val docH: Float
        if (isPortrait) {
            docW = w * 0.85f
            docH = (docW * a4Ratio).coerceAtMost(h * 0.90f)
        } else {
            docH = h * 0.85f
            docW = (docH * a4Ratio).coerceAtMost(w * 0.90f)
        }

        val left = (w - docW) / 2f
        val top = (h - docH) / 2f
        val right = left + docW
        val bottom = top + docH

        return DocumentCorners(
            topLeft = PointF(left, top),
            topRight = PointF(right, top),
            bottomRight = PointF(right, bottom),
            bottomLeft = PointF(left, bottom)
        )
    }

    /**
     * Probabilistic Hough Line detector fallback: detects straight boundary segments and computes their
     * bounding quadrilateral. Critical for white paper on white desks/bedsheets.
     */
    private fun detectCornersFromHoughLines(edgeMat: Mat, w: Int, h: Int): Array<Point>? {
        val lines = Mat()
        try {
            Imgproc.HoughLinesP(edgeMat, lines, 1.0, Math.PI / 180.0, 40, 50.0, 15.0)
            if (lines.rows() < 4) return null

            var minX = w.toDouble()
            var maxX = 0.0
            var minY = h.toDouble()
            var maxY = 0.0

            val frameMargin = w * 0.03
            for (i in 0 until lines.rows()) {
                val data = lines.get(i, 0) ?: continue
                val x1 = data[0]
                val y1 = data[1]
                val x2 = data[2]
                val y2 = data[3]

                if (x1 > frameMargin && x1 < w - frameMargin && y1 > frameMargin && y1 < h - frameMargin) {
                    minX = minOf(minX, x1)
                    maxX = maxOf(maxX, x1)
                    minY = minOf(minY, y1)
                    maxY = maxOf(maxY, y1)
                }
                if (x2 > frameMargin && x2 < w - frameMargin && y2 > frameMargin && y2 < h - frameMargin) {
                    minX = minOf(minX, x2)
                    maxX = maxOf(maxX, x2)
                    minY = minOf(minY, y2)
                    maxY = maxOf(maxY, y2)
                }
            }

            if (maxX - minX > w * 0.35 && maxY - minY > h * 0.35) {
                val candidate = arrayOf(
                    Point(minX, minY),
                    Point(maxX, minY),
                    Point(maxX, maxY),
                    Point(minX, maxY)
                )
                if (isValidDocumentQuad(candidate, w, h)) {
                    return candidate
                }
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            lines.release()
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

        // Orthogonal Angle Validation: All 4 corner angles must be realistic document angles (~65° to ~115°)
        for (i in 0..3) {
            val pPrev = pts[(i + 3) % 4]
            val pCurr = pts[i]
            val pNext = pts[(i + 1) % 4]

            val v1x = pPrev.x - pCurr.x
            val v1y = pPrev.y - pCurr.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val dot = v1x * v2x + v1y * v2y
            val mag = hypot(v1x, v1y) * hypot(v2x, v2y)
            if (mag > 0.0) {
                val cosTheta = kotlin.math.abs(dot / mag)
                // If cosTheta > 0.45, angle deviates by > 27° from 90° (degenerate or severe perspective artifact)
                if (cosTheta > 0.45) return false
            }
        }

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
