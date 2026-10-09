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

/**
 * Enterprise CamScanner-Grade 4-Corner Document Edge Detector.
 * Features:
 * - Cascaded Multi-Strategy Edge Detection:
 *     1. Canny Edge Detection (high/medium contrast backgrounds)
 *     2. Morphological Gradient + Otsu (white paper on light/beige desks)
 *     3. Adaptive Gaussian Thresholding (harsh shadows, non-uniform ambient light)
 *     4. Global Otsu Binary Thresholding (dark receipts/cards on light desks)
 * - Multi-Scale Polygon Approximation (approxPolyDP with epsilon sweep 0.012..0.070)
 * - Convex Hull Diagonal Extrema Projections (guaranteed 4-corner quad fitting)
 * - Rotated Rect Fallback
 * - Relaxed Perspective Angle Validation (handles realistic handheld shots 35°..145°)
 * - Sub-pixel corner gradient refinement (cornerSubPix)
 */
object DocumentEdgeDetector {
    /**
     * Detects 4 document corners on a Bitmap, falling back to centered ISO frame if no paper is detected.
     */
    suspend fun detectCorners(bitmap: Bitmap): DocumentCorners = withContext(Dispatchers.Default) {
        detectCornersOrNull(bitmap) ?: computeCenteredA4Corners(bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    /**
     * Detects 4 document corners on a Bitmap, returning null if no authentic document quadrilateral is found.
     */
    suspend fun detectCornersOrNull(bitmap: Bitmap): DocumentCorners? = withContext(Dispatchers.Default) {
        val srcMat = Mat()
        val grayMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            detectCornersFromGrayMat(grayMat, bitmap.width, bitmap.height)
        } catch (_: Exception) {
            null
        } finally {
            srcMat.release()
            grayMat.release()
        }
    }

    /**
     * Specialized Enterprise 4-Corner Edge Detector optimized for ID Cards (Aadhaar, PAN, DL, Voter ID, PVC Cards).
     * Accommodates:
     * - Small card footprint in smartphone frame (area as low as 1.5% to 98% of frame).
     * - ISO/IEC 7810 ID-1 standard canonical aspect ratio (85.60mm x 53.98mm = 1.5858).
     * - Multi-epsilon approxPolyDP + minAreaRect fallback for rounded card corners (standard 3.18mm radius).
     * - Scoring that prioritizes quads matching ID-1 card proportions.
     */
    suspend fun detectCardCornersOrNull(bitmap: Bitmap): DocumentCorners? = withContext(Dispatchers.Default) {
        val srcMat = Mat()
        val grayMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcMat)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            detectCardCornersFromGrayMat(grayMat, bitmap.width, bitmap.height)
        } catch (_: Exception) {
            null
        } finally {
            srcMat.release()
            grayMat.release()
        }
    }

    /**
     * Detects 4 corners of an ID Card from a grayscale matrix.
     */
    fun detectCardCornersFromGrayMat(grayMat: Mat, origWidth: Int, origHeight: Int): DocumentCorners? {
        val targetWidth = 640.0
        val scale = if (origWidth > targetWidth) targetWidth / origWidth.toDouble() else 1.0
        val procWidth = (origWidth * scale).toInt().coerceAtLeast(10)
        val procHeight = (origHeight * scale).toInt().coerceAtLeast(10)

        val smallGray = Mat()
        val blurredMat = Mat()
        val claheMat = Mat()
        val k3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))

        try {
            if (scale < 1.0) {
                Imgproc.resize(grayMat, smallGray, Size(procWidth.toDouble(), procHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            } else {
                grayMat.copyTo(smallGray)
            }

            Imgproc.GaussianBlur(smallGray, blurredMat, Size(5.0, 5.0), 0.0)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(blurredMat, claheMat)

            var bestQuad: Array<Point>? = null

            // Strategy 1: Canny
            val cannyMat = Mat()
            val dilatedCanny = Mat()
            try {
                Imgproc.Canny(claheMat, cannyMat, 30.0, 100.0)
                Imgproc.dilate(cannyMat, dilatedCanny, k3)
                bestQuad = extractBestCardQuadFromEdgeMap(dilatedCanny, procWidth, procHeight, smallGray)
            } finally {
                cannyMat.release()
                dilatedCanny.release()
            }

            // Strategy 2: Morphological Gradient + Otsu
            if (bestQuad == null) {
                val gradMat = Mat()
                val otsuGrad = Mat()
                val dilatedGrad = Mat()
                val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                try {
                    Imgproc.morphologyEx(blurredMat, gradMat, Imgproc.MORPH_GRADIENT, k5)
                    Imgproc.threshold(gradMat, otsuGrad, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                    Imgproc.dilate(otsuGrad, dilatedGrad, k3)
                    bestQuad = extractBestCardQuadFromEdgeMap(dilatedGrad, procWidth, procHeight, smallGray)
                } finally {
                    k5.release()
                    gradMat.release()
                    otsuGrad.release()
                    dilatedGrad.release()
                }
            }

            // Strategy 3: Adaptive Gaussian Thresholding
            if (bestQuad == null) {
                val adaptMat = Mat()
                val closedAdapt = Mat()
                val kClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                try {
                    Imgproc.adaptiveThreshold(
                        blurredMat,
                        adaptMat,
                        255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY_INV,
                        21,
                        5.0
                    )
                    Imgproc.morphologyEx(adaptMat, closedAdapt, Imgproc.MORPH_CLOSE, kClose)
                    bestQuad = extractBestCardQuadFromEdgeMap(closedAdapt, procWidth, procHeight, smallGray)
                } finally {
                    kClose.release()
                    adaptMat.release()
                    closedAdapt.release()
                }
            }

            if (bestQuad != null) {
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
                    val refined = cornersMat.toArray()
                    cornersMat.release()
                    if (isValidCardQuad(refined, procWidth, procHeight)) {
                        bestQuad = refined
                    }
                } catch (_: Exception) {}

                val invScale = 1.0 / scale
                val scaledPoints = bestQuad.map {
                    Point(
                        (it.x * invScale).coerceIn(0.0, origWidth.toDouble()),
                        (it.y * invScale).coerceIn(0.0, origHeight.toDouble())
                    )
                }.toTypedArray()

                return sortCorners(scaledPoints)
            }

            return null
        } catch (_: Exception) {
            return null
        } finally {
            smallGray.release()
            blurredMat.release()
            claheMat.release()
            k3.release()
        }
    }

    private fun extractBestCardQuadFromEdgeMap(
        edgeMap: Mat,
        procW: Int,
        procH: Int,
        smallGray: Mat
    ): Array<Point>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                edgeMap,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val frameArea = procW.toDouble() * procH.toDouble()
            val minArea = frameArea * 0.015
            val maxAllowedArea = frameArea * 0.98

            val candidateContours = contours
                .map { Pair(it, Imgproc.contourArea(it)) }
                .filter { it.second in minArea..maxAllowedArea }
                .sortedByDescending { it.second }
                .take(10)

            var bestScore = -1.0
            var bestQuad: Array<Point>? = null

            for ((contour, contourArea) in candidateContours) {
                val hullIndices = MatOfInt()
                Imgproc.convexHull(contour, hullIndices)
                val contourPoints = contour.toArray()
                val hullPoints = hullIndices.toArray().map { contourPoints[it] }.toTypedArray()
                hullIndices.release()

                if (hullPoints.size < 4) continue

                val hullMat = MatOfPoint2f(*hullPoints)
                val peri = Imgproc.arcLength(hullMat, true)
                var foundQuad: Array<Point>? = null

                // 1. approxPolyDP with sweep
                for (eps in doubleArrayOf(0.012, 0.018, 0.025, 0.035, 0.05, 0.065)) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(hullMat, approx, eps * peri, true)
                    if (approx.total() == 4L) {
                        val mop = MatOfPoint(*approx.toArray())
                        if (Imgproc.isContourConvex(mop)) {
                            val pts = approx.toArray()
                            if (isValidCardQuad(pts, procW, procH)) {
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

                // 2. minAreaRect fallback for rounded corner cards
                if (foundQuad == null) {
                    val rotRect = Imgproc.minAreaRect(hullMat)
                    val boxPts = Array(4) { Point() }
                    rotRect.points(boxPts)
                    if (isValidCardQuad(boxPts, procW, procH)) {
                        val rectArea = rotRect.size.width * rotRect.size.height
                        if (rectArea > 0 && (contourArea / rectArea) > 0.65) {
                            foundQuad = boxPts
                        }
                    }
                }

                hullMat.release()

                if (foundQuad != null) {
                    val edge1 = hypot(foundQuad[0].x - foundQuad[1].x, foundQuad[0].y - foundQuad[1].y)
                    val edge2 = hypot(foundQuad[1].x - foundQuad[2].x, foundQuad[1].y - foundQuad[2].y)
                    val maxEdge = maxOf(edge1, edge2)
                    val minEdge = minOf(edge1, edge2).coerceAtLeast(1.0)
                    val ar = maxEdge / minEdge
                    val arPenalty = kotlin.math.abs(ar - 1.5858)
                    val score = contourArea / (1.0 + arPenalty * 4.0)

                    if (score > bestScore) {
                        bestScore = score
                        bestQuad = foundQuad
                    }
                }
            }

            contours.forEach { it.release() }
            return bestQuad
        } catch (_: Exception) {
            return null
        } finally {
            hierarchy.release()
        }
    }

    private fun isValidCardQuad(pts: Array<Point>, w: Int, h: Int): Boolean {
        if (pts.size != 4) return false
        val edge1 = hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
        val edge2 = hypot(pts[1].x - pts[2].x, pts[1].y - pts[2].y)
        val edge3 = hypot(pts[2].x - pts[3].x, pts[2].y - pts[3].y)
        val edge4 = hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y)

        val minEdge = minOf(edge1, edge2, edge3, edge4)
        val maxEdge = maxOf(edge1, edge2, edge3, edge4)
        if (minEdge < 18.0) return false
        val ar = maxEdge / minEdge
        if (ar < 1.10 || ar > 2.8) return false

        val quadArea = computePolygonArea(pts)
        val totalArea = w.toDouble() * h.toDouble()
        if (quadArea < totalArea * 0.015 || quadArea > totalArea * 0.98) return false

        return true
    }

    /**
     * Real-time high-speed corner detector operating directly on CameraX Y-plane Mat (0ms bitmap conversion).
     * Returns null if no document is detected.
     */
    fun detectCornersFromGrayMat(grayMat: Mat, origWidth: Int, origHeight: Int): DocumentCorners? {
        val targetWidth = 540.0
        val scale = if (origWidth > targetWidth) targetWidth / origWidth.toDouble() else 1.0
        val procWidth = (origWidth * scale).toInt().coerceAtLeast(10)
        val procHeight = (origHeight * scale).toInt().coerceAtLeast(10)

        val smallGray = Mat()
        val blurredMat = Mat()
        val claheMat = Mat()
        val k3 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))

        try {
            if (scale < 1.0) {
                Imgproc.resize(grayMat, smallGray, Size(procWidth.toDouble(), procHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            } else {
                grayMat.copyTo(smallGray)
            }

            // 1. Gentle Gaussian blur to eliminate text ink dots, screen patterns, and camera sensor noise
            Imgproc.GaussianBlur(smallGray, blurredMat, Size(5.0, 5.0), 0.0)

            // 2. Mild CLAHE to balance dynamic range across shadows without destroying paper border gradient
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(blurredMat, claheMat)

            var bestQuad: Array<Point>? = null

            // --- STRATEGY 1: Canny Edge Detection (Optimal for standard paper on contrasting desks) ---
            val cannyMat = Mat()
            val dilatedCanny = Mat()
            try {
                Imgproc.Canny(claheMat, cannyMat, 30.0, 100.0)
                Imgproc.dilate(cannyMat, dilatedCanny, k3)
                bestQuad = extractBestQuadFromEdgeMap(dilatedCanny, procWidth, procHeight, smallGray)
            } finally {
                cannyMat.release()
                dilatedCanny.release()
            }

            // --- STRATEGY 2: Morphological Gradient + Otsu (Optimal for white paper on white/light/wooden desks) ---
            if (bestQuad == null) {
                val gradMat = Mat()
                val otsuGrad = Mat()
                val dilatedGrad = Mat()
                val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                try {
                    Imgproc.morphologyEx(blurredMat, gradMat, Imgproc.MORPH_GRADIENT, k5)
                    Imgproc.threshold(gradMat, otsuGrad, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                    Imgproc.dilate(otsuGrad, dilatedGrad, k3)
                    bestQuad = extractBestQuadFromEdgeMap(dilatedGrad, procWidth, procHeight, smallGray)
                } finally {
                    k5.release()
                    gradMat.release()
                    otsuGrad.release()
                    dilatedGrad.release()
                }
            }

            // --- STRATEGY 3: Adaptive Gaussian Thresholding (Optimal for strong shadows, ambient gradients) ---
            if (bestQuad == null) {
                val adaptMat = Mat()
                val closedAdapt = Mat()
                val kClose = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                try {
                    Imgproc.adaptiveThreshold(
                        blurredMat,
                        adaptMat,
                        255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY_INV,
                        21,
                        5.0
                    )
                    Imgproc.morphologyEx(adaptMat, closedAdapt, Imgproc.MORPH_CLOSE, kClose)
                    bestQuad = extractBestQuadFromEdgeMap(closedAdapt, procWidth, procHeight, smallGray)
                } finally {
                    kClose.release()
                    adaptMat.release()
                    closedAdapt.release()
                }
            }

            // --- STRATEGY 4: Global Otsu Thresholding (Optimal for dark cards/receipts on light surfaces) ---
            if (bestQuad == null) {
                val otsuMat = Mat()
                val dilatedOtsu = Mat()
                try {
                    Imgproc.threshold(blurredMat, otsuMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
                    val otsuEdges = Mat()
                    Imgproc.Canny(otsuMat, otsuEdges, 50.0, 150.0)
                    Imgproc.dilate(otsuEdges, dilatedOtsu, k3)
                    otsuEdges.release()
                    bestQuad = extractBestQuadFromEdgeMap(dilatedOtsu, procWidth, procHeight, smallGray)
                } finally {
                    otsuMat.release()
                    dilatedOtsu.release()
                }
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
                    val refined = cornersMat.toArray()
                    cornersMat.release()
                    if (isValidDocumentQuad(refined, procWidth, procHeight)) {
                        bestQuad = refined
                    }
                } catch (_: Exception) {}

                // Scale back to original resolution and sort into canonical clockwise order
                val invScale = 1.0 / scale
                val scaledPoints = bestQuad.map {
                    Point(
                        (it.x * invScale).coerceIn(0.0, origWidth.toDouble()),
                        (it.y * invScale).coerceIn(0.0, origHeight.toDouble())
                    )
                }.toTypedArray()

                return sortCorners(scaledPoints)
            }

            return null
        } catch (_: Exception) {
            return null
        } finally {
            smallGray.release()
            blurredMat.release()
            claheMat.release()
            k3.release()
        }
    }

    /**
     * Extracts the highest quality 4-corner document quad from a binary edge/threshold map.
     */
    private fun extractBestQuadFromEdgeMap(
        edgeMap: Mat,
        procW: Int,
        procH: Int,
        smallGray: Mat
    ): Array<Point>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(
                edgeMap,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val frameArea = procW.toDouble() * procH.toDouble()
            val minArea = frameArea * 0.06 // Support smaller receipts, cards, and documents
            val maxAllowedArea = frameArea * 0.99

            // Filter contours by area and sort descending
            val candidateContours = contours
                .map { Pair(it, Imgproc.contourArea(it)) }
                .filter { it.second in minArea..maxAllowedArea }
                .sortedByDescending { it.second }
                .take(6)

            var bestArea = 0.0
            var bestQuad: Array<Point>? = null

            for ((contour, contourArea) in candidateContours) {
                // 1. Compute Convex Hull to smooth finger holds, staple marks, or minor boundary notches
                val hullIndices = MatOfInt()
                Imgproc.convexHull(contour, hullIndices)
                val contourPoints = contour.toArray()
                val hullPoints = hullIndices.toArray().map { contourPoints[it] }.toTypedArray()
                hullIndices.release()

                if (hullPoints.size < 4) continue

                val hullMat = MatOfPoint2f(*hullPoints)
                val peri = Imgproc.arcLength(hullMat, true)
                var foundQuad: Array<Point>? = null

                // 2. Multi-epsilon approxPolyDP sweep
                for (eps in doubleArrayOf(0.012, 0.016, 0.02, 0.025, 0.03, 0.038, 0.048, 0.06, 0.075)) {
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(hullMat, approx, eps * peri, true)
                    if (approx.total() == 4L) {
                        val mop = MatOfPoint(*approx.toArray())
                        if (Imgproc.isContourConvex(mop)) {
                            val pts = approx.toArray()
                            if (isValidDocumentQuad(pts, procW, procH)) {
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

                // 3. Guaranteed 4-Corner Diagonal Extrema Projection from Convex Hull
                if (foundQuad == null) {
                    val tl = hullPoints.minByOrNull { it.x + it.y }
                    val br = hullPoints.maxByOrNull { it.x + it.y }
                    val tr = hullPoints.maxByOrNull { it.x - it.y }
                    val bl = hullPoints.minByOrNull { it.x - it.y }

                    if (tl != null && br != null && tr != null && bl != null) {
                        // Verify all 4 points are distinct with minimum separation
                        val d1 = hypot(tl.x - tr.x, tl.y - tr.y)
                        val d2 = hypot(tr.x - br.x, tr.y - br.y)
                        val d3 = hypot(br.x - bl.x, br.y - bl.y)
                        val d4 = hypot(bl.x - tl.x, bl.y - tl.y)

                        if (d1 >= 25.0 && d2 >= 25.0 && d3 >= 25.0 && d4 >= 25.0) {
                            val candidateQuad = arrayOf(tl, tr, br, bl)
                            if (isValidDocumentQuad(candidateQuad, procW, procH)) {
                                foundQuad = candidateQuad
                            }
                        }
                    }
                }

                // 4. Rotated Bounding Box Fallback (minAreaRect)
                if (foundQuad == null) {
                    val rotRect = Imgproc.minAreaRect(hullMat)
                    val boxPts = Array(4) { Point() }
                    rotRect.points(boxPts)
                    if (isValidDocumentQuad(boxPts, procW, procH)) {
                        val rectArea = rotRect.size.width * rotRect.size.height
                        if (rectArea > 0 && (contourArea / rectArea) > 0.60) {
                            foundQuad = boxPts
                        }
                    }
                }

                hullMat.release()

                if (foundQuad != null && contourArea > bestArea) {
                    bestArea = contourArea
                    bestQuad = foundQuad
                }
            }

            if (bestQuad == null) {
                bestQuad = detectQuadFromHoughLines(edgeMap, procW, procH, smallGray)
            }

            return bestQuad
        } finally {
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /**
     * Calculates an authentic, centered ISO A4 / Document aspect ratio bounding frame
     * used as fallback when contrast is completely absent (e.g., pointed at a plain ceiling).
     */
    fun computeCenteredA4Corners(w: Float, h: Float): DocumentCorners {
        val isPortrait = h >= w
        val a4Ratio = 1.414f

        val docW: Float
        val docH: Float
        if (isPortrait) {
            docW = w * 0.88f
            docH = (docW * a4Ratio).coerceAtMost(h * 0.90f)
        } else {
            docH = h * 0.88f
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
     * Validates that the 4 points form a realistic, non-degenerate document quadrilateral.
     */
    private fun isValidDocumentQuad(pts: Array<Point>, w: Int, h: Int): Boolean {
        if (pts.size != 4) return false

        val edge1 = hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
        val edge2 = hypot(pts[1].x - pts[2].x, pts[1].y - pts[2].y)
        val edge3 = hypot(pts[2].x - pts[3].x, pts[2].y - pts[3].y)
        val edge4 = hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y)

        val minEdge = minOf(edge1, edge2, edge3, edge4)
        val maxEdge = maxOf(edge1, edge2, edge3, edge4)

        if (minEdge < 24.0) return false
        // Aspect ratio between max edge and min edge should accommodate receipts/banners (< 5.5)
        if (maxEdge / minEdge > 5.5) return false

        // Compute polygon area via Shoelace formula
        val quadArea = computePolygonArea(pts)
        val totalArea = w.toDouble() * h.toDouble()
        if (quadArea < totalArea * 0.05 || quadArea > totalArea * 0.99) return false

        // Check strict convexity: cross products of consecutive edges must maintain the same sign
        var expectedSign = 0
        for (i in 0..3) {
            val p1 = pts[i]
            val p2 = pts[(i + 1) % 4]
            val p3 = pts[(i + 2) % 4]

            val v1x = p2.x - p1.x
            val v1y = p2.y - p1.y
            val v2x = p3.x - p2.x
            val v2y = p3.y - p2.y

            val cross = v1x * v2y - v1y * v2x
            if (cross > 0.0) {
                if (expectedSign < 0) return false
                expectedSign = 1
            } else if (cross < 0.0) {
                if (expectedSign > 0) return false
                expectedSign = -1
            }

            // Angle Validation: corner angles in realistic perspective range between 35° and 145°
            val vaX = p1.x - p2.x
            val vaY = p1.y - p2.y
            val vbX = p3.x - p2.x
            val vbY = p3.y - p2.y

            val dot = vaX * vbX + vaY * vbY
            val mag = hypot(vaX, vaY) * hypot(vbX, vbY)
            if (mag > 0.0) {
                val cosTheta = kotlin.math.abs(dot / mag)
                // Relaxed to 0.82 to allow perspective handheld angled camera shots (~35° to ~145°)
                if (cosTheta > 0.82) return false
            }
        }

        return true
    }

    /**
     * Shoelace formula for quadrilateral area.
     */
    private fun computePolygonArea(pts: Array<Point>): Double {
        var area = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return kotlin.math.abs(area) / 2.0
    }

    /**
     * Orders 4 vertices into canonical [Top-Left, Top-Right, Bottom-Right, Bottom-Left].
     */
    private fun sortCorners(pts: Array<Point>): DocumentCorners {
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val tl = sortedBySum.first()
        val br = sortedBySum.last()

        val remaining = pts.filter { it != tl && it != br }
        val bl = remaining.minByOrNull { it.x - it.y } ?: remaining.first()
        val tr = remaining.maxByOrNull { it.x - it.y } ?: remaining.last()

        return DocumentCorners(
            topLeft = PointF(tl.x.toFloat(), tl.y.toFloat()),
            topRight = PointF(tr.x.toFloat(), tr.y.toFloat()),
            bottomRight = PointF(br.x.toFloat(), br.y.toFloat()),
            bottomLeft = PointF(bl.x.toFloat(), bl.y.toFloat())
        )
    }

    /**
     * Contrast-Weighted Hough Lines + Quad Corner Regression.
     * Detects straight line segments from Canny edge map, clusters lines into
     * horizontal/vertical boundary candidates, and computes intersections to regress
     * a clean quadrilateral, robust against bedsheet wrinkles, textured desk surfaces, and floor lines.
     */
    fun detectQuadFromHoughLines(
        edgeMap: Mat,
        procW: Int,
        procH: Int,
        smallGray: Mat
    ): Array<Point>? {
        val lines = Mat()
        try {
            Imgproc.HoughLinesP(
                edgeMap,
                lines,
                1.0,
                Math.PI / 180.0,
                38,
                procW * 0.16,
                16.0
            )

            val numLines = lines.rows()
            if (numLines < 4) return null

            data class Segment(
                val x1: Double,
                val y1: Double,
                val x2: Double,
                val y2: Double,
                val angleDeg: Double,
                val length: Double,
                val contrastWeight: Double
            )

            val segments = mutableListOf<Segment>()
            val lineData = IntArray(4)

            for (i in 0 until minOf(numLines, 60)) {
                lines.get(i, 0, lineData)
                val x1 = lineData[0].toDouble()
                val y1 = lineData[1].toDouble()
                val x2 = lineData[2].toDouble()
                val y2 = lineData[3].toDouble()

                val len = hypot(x2 - x1, y2 - y1)
                if (len < procW * 0.14) continue

                var angle = Math.toDegrees(kotlin.math.atan2(y2 - y1, x2 - x1))
                if (angle < 0.0) angle += 180.0

                val midX = ((x1 + x2) / 2.0).toInt().coerceIn(2, procW - 3)
                val midY = ((y1 + y2) / 2.0).toInt().coerceIn(2, procH - 3)
                val vY1 = smallGray.get(midY + 2, midX)?.get(0) ?: 0.0
                val vY2 = smallGray.get(midY - 2, midX)?.get(0) ?: 0.0
                val vX1 = smallGray.get(midY, midX + 2)?.get(0) ?: 0.0
                val vX2 = smallGray.get(midY, midX - 2)?.get(0) ?: 0.0
                val contrast = kotlin.math.abs(vY1 - vY2) + kotlin.math.abs(vX1 - vX2)

                segments.add(Segment(x1, y1, x2, y2, angle, len, contrast))
            }

            if (segments.size < 4) return null

            // Classify into horizontals (angle in [0..38] or [142..180]) and verticals (angle in [52..128])
            val horizontals = segments.filter { it.angleDeg <= 38.0 || it.angleDeg >= 142.0 }
                .sortedByDescending { it.length * (it.contrastWeight + 10.0) }
            val verticals = segments.filter { it.angleDeg in 52.0..128.0 }
                .sortedByDescending { it.length * (it.contrastWeight + 10.0) }

            if (horizontals.size < 2 || verticals.size < 2) return null

            val topLines = horizontals.filter { (it.y1 + it.y2) / 2.0 < procH * 0.55 }
            val bottomLines = horizontals.filter { (it.y1 + it.y2) / 2.0 >= procH * 0.45 }
            val leftLines = verticals.filter { (it.x1 + it.x2) / 2.0 < procW * 0.55 }
            val rightLines = verticals.filter { (it.x1 + it.x2) / 2.0 >= procW * 0.45 }

            val top = topLines.firstOrNull() ?: return null
            val bottom = bottomLines.firstOrNull() ?: return null
            val left = leftLines.firstOrNull() ?: return null
            val right = rightLines.firstOrNull() ?: return null

            fun intersect(s1: Segment, s2: Segment): Point? {
                val denom = (s1.x1 - s1.x2) * (s2.y1 - s2.y2) - (s1.y1 - s1.y2) * (s2.x1 - s2.x2)
                if (kotlin.math.abs(denom) < 1e-4) return null
                val t = ((s1.x1 - s2.x1) * (s2.y1 - s2.y2) - (s1.y1 - s2.y1) * (s2.x1 - s2.x2)) / denom
                val px = s1.x1 + t * (s1.x2 - s1.x1)
                val py = s1.y1 + t * (s1.y2 - s1.y1)
                if (px < -procW * 0.1 || px > procW * 1.1 || py < -procH * 0.1 || py > procH * 1.1) return null
                return Point(px.coerceIn(0.0, procW.toDouble()), py.coerceIn(0.0, procH.toDouble()))
            }

            val tl = intersect(top, left) ?: return null
            val tr = intersect(top, right) ?: return null
            val br = intersect(bottom, right) ?: return null
            val bl = intersect(bottom, left) ?: return null

            val quad = arrayOf(tl, tr, br, bl)
            if (isValidDocumentQuad(quad, procW, procH)) {
                return quad
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            lines.release()
        }
    }

    /**
     * Enterprise 5-Frame Temporal Moving Average & Kalman-Style Smoothing Filter.
     * Guarantees zero frame-to-frame green box jitter and triggers magnetic snap lock
     * when quad corners remain stable within 5.0px variance across 5 frames.
     */
    class TemporalQuadFilter(private val maxHistory: Int = 5) {
        private val history = java.util.ArrayDeque<DocumentCorners>(maxHistory)
        private var smoothedCorners: DocumentCorners? = null
        var isStable: Boolean = false
            private set
        var isLocked: Boolean = false
            private set
        private var lockTriggered: Boolean = false

        fun reset() {
            history.clear()
            smoothedCorners = null
            isStable = false
            isLocked = false
            lockTriggered = false
        }

        fun process(detected: DocumentCorners?): FilterResult {
            if (detected == null) {
                reset()
                return FilterResult(null, isStable = false, isLocked = false, isJustLocked = false)
            }

            val prev = smoothedCorners
            val smoothed = if (prev == null) {
                detected
            } else {
                val maxCornerDist = maxOf(
                    hypot((detected.topLeft.x - prev.topLeft.x).toDouble(), (detected.topLeft.y - prev.topLeft.y).toDouble()),
                    hypot((detected.topRight.x - prev.topRight.x).toDouble(), (detected.topRight.y - prev.topRight.y).toDouble()),
                    hypot((detected.bottomRight.x - prev.bottomRight.x).toDouble(), (detected.bottomRight.y - prev.bottomRight.y).toDouble()),
                    hypot((detected.bottomLeft.x - prev.bottomLeft.x).toDouble(), (detected.bottomLeft.y - prev.bottomLeft.y).toDouble())
                )

                if (maxCornerDist > 140.0) {
                    history.clear()
                    isStable = false
                    isLocked = false
                    lockTriggered = false
                    detected
                } else {
                    val alpha = if (maxCornerDist < 25.0) 0.28f else 0.45f
                    DocumentCorners(
                        topLeft = PointF(prev.topLeft.x * (1f - alpha) + detected.topLeft.x * alpha, prev.topLeft.y * (1f - alpha) + detected.topLeft.y * alpha),
                        topRight = PointF(prev.topRight.x * (1f - alpha) + detected.topRight.x * alpha, prev.topRight.y * (1f - alpha) + detected.topRight.y * alpha),
                        bottomRight = PointF(prev.bottomRight.x * (1f - alpha) + detected.bottomRight.x * alpha, prev.bottomRight.y * (1f - alpha) + detected.bottomRight.y * alpha),
                        bottomLeft = PointF(prev.bottomLeft.x * (1f - alpha) + detected.bottomLeft.x * alpha, prev.bottomLeft.y * (1f - alpha) + detected.bottomLeft.y * alpha)
                    )
                }
            }

            smoothedCorners = smoothed
            if (history.size >= maxHistory) {
                history.removeFirst()
            }
            history.addLast(smoothed)

            if (history.size >= 4) {
                val variance = computeMaxCornerVariance(history.toList())
                isStable = variance < 8.5
                isLocked = isStable && history.size >= 5 && variance < 5.0
            } else {
                isStable = false
                isLocked = false
            }

            val isJustLocked = isLocked && !lockTriggered
            if (isJustLocked) {
                lockTriggered = true
            } else if (!isLocked) {
                lockTriggered = false
            }

            return FilterResult(smoothedCorners, isStable, isLocked, isJustLocked)
        }

        private fun computeMaxCornerVariance(list: List<DocumentCorners>): Double {
            if (list.size < 2) return 0.0
            val avgTLX = list.map { it.topLeft.x }.average()
            val avgTLY = list.map { it.topLeft.y }.average()
            val avgTRX = list.map { it.topRight.x }.average()
            val avgTRY = list.map { it.topRight.y }.average()
            val avgBRX = list.map { it.bottomRight.x }.average()
            val avgBRY = list.map { it.bottomRight.y }.average()
            val avgBLX = list.map { it.bottomLeft.x }.average()
            val avgBLY = list.map { it.bottomLeft.y }.average()

            return list.maxOf { c ->
                maxOf(
                    hypot((c.topLeft.x - avgTLX), (c.topLeft.y - avgTLY)),
                    hypot((c.topRight.x - avgTRX), (c.topRight.y - avgTRY)),
                    hypot((c.bottomRight.x - avgBRX), (c.bottomRight.y - avgBRY)),
                    hypot((c.bottomLeft.x - avgBLX), (c.bottomLeft.y - avgBLY))
                )
            }
        }

        data class FilterResult(
            val corners: DocumentCorners?,
            val isStable: Boolean,
            val isLocked: Boolean,
            val isJustLocked: Boolean
        )
    }
}
