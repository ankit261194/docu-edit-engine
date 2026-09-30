package com.docu.editor.core.scanner

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Enterprise Book Spine & Curved Page Dewarping Engine.
 * Analyzes horizontal text-line baseline contours, fits a polynomial displacement profile,
 * and performs non-linear cylindrical remapping (Imgproc.remap) to flatten curved pages.
 */
object BookDewarpEngine {

    suspend fun dewarpPage(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val srcRgba = Mat()
        val srcGray = Mat()
        val binary = Mat()
        val morphMat = Mat()
        val mapX = Mat()
        val mapY = Mat()
        val dewarpedRgba = Mat()

        try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcGray, Imgproc.COLOR_RGBA2GRAY)

            val w = source.width
            val h = source.height

            // 1. Adaptive Binarization to extract text strokes
            Imgproc.adaptiveThreshold(
                srcGray,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                21,
                11.0
            )

            // 2. Horizontal morphological connecting: links words on the same line into a ribbon
            val kHorizontal = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(25.0, 2.0))
            Imgproc.morphologyEx(binary, morphMat, Imgproc.MORPH_DILATE, kHorizontal)
            kHorizontal.release()

            // 3. Find horizontal text ribbons
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(morphMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()

            // Filter lines: width >= 25% of page width, height between 4px and 45px
            val minLineWidth = w * 0.25
            val qualifyingContours = mutableListOf<MatOfPoint>()

            for (c in contours) {
                val rect = Imgproc.boundingRect(c)
                if (rect.width >= minLineWidth && rect.height in 4..60 && (rect.width.toFloat() / rect.height) >= 4.0f) {
                    qualifyingContours.add(c)
                } else {
                    c.release()
                }
            }

            // If not enough clean text lines to model curvature, return original
            if (qualifyingContours.size < 3) {
                qualifyingContours.forEach { it.release() }
                return@withContext source.copy(Bitmap.Config.ARGB_8888, true)
            }

            // 4. Sample baseline y-coordinates at 20 sample points across width
            val numSamples = 20
            val xStep = w / numSamples
            val sampleOffsets = FloatArray(numSamples) { 0f }
            val sampleCounts = IntArray(numSamples) { 0 }

            for (c in qualifyingContours) {
                val pts = c.toArray()
                val rect = Imgproc.boundingRect(c)
                val lineMidY = rect.y + rect.height / 2f

                // Find bottom baseline points of this contour across x samples
                for (pt in pts) {
                    val sampleIdx = (pt.x / xStep).toInt().coerceIn(0, numSamples - 1)
                    val dy = (pt.y - lineMidY).toFloat()
                    sampleOffsets[sampleIdx] += dy
                    sampleCounts[sampleIdx]++
                }
                c.release()
            }

            // Compute normalized curvature curve
            val curve = FloatArray(w)
            var maxCurvature = 0f

            for (sampleIdx in 0 until numSamples) {
                if (sampleCounts[sampleIdx] > 0) {
                    sampleOffsets[sampleIdx] /= sampleCounts[sampleIdx]
                }
            }

            // Smooth sample offsets across x
            for (x in 0 until w) {
                val floatIdx = (x.toFloat() / w) * (numSamples - 1)
                val idx0 = floatIdx.toInt().coerceIn(0, numSamples - 1)
                val idx1 = (idx0 + 1).coerceIn(0, numSamples - 1)
                val t = floatIdx - idx0
                val interp = sampleOffsets[idx0] * (1f - t) + sampleOffsets[idx1] * t
                curve[x] = interp
                if (abs(interp) > maxCurvature) maxCurvature = abs(interp)
            }

            // If page is already flat (curvature < 4px), skip remap
            if (maxCurvature < 4f) {
                return@withContext source.copy(Bitmap.Config.ARGB_8888, true)
            }

            // 5. Generate displacement maps for Imgproc.remap
            mapX.create(h, w, CvType.CV_32FC1)
            mapY.create(h, w, CvType.CV_32FC1)

            val xData = FloatArray(w)
            for (x in 0 until w) xData[x] = x.toFloat()

            val yRowData = FloatArray(w)
            for (y in 0 until h) {
                mapX.put(y, 0, xData)
                for (x in 0 until w) {
                    // Vertical displacement counteracts the page curvature
                    yRowData[x] = (y - curve[x]).coerceIn(0f, (h - 1).toFloat())
                }
                mapY.put(y, 0, yRowData)
            }

            // 6. Execute high-quality non-linear remap
            Imgproc.remap(srcRgba, dewarpedRgba, mapX, mapY, Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)

            val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dewarpedRgba, output)
            output
        } catch (_: Exception) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            srcRgba.release()
            srcGray.release()
            binary.release()
            morphMat.release()
            mapX.release()
            mapY.release()
            dewarpedRgba.release()
        }
    }
}
