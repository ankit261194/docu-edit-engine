package com.docu.editor.core.dewarp

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Book 2-Page Auto-Split Engine.
 * CamScanner-Grade Features:
 * - Detects vertical spine gutter luminance trough in 35%..65% span of open book spread.
 * - Auto-cuts into 2 distinct ordered pages (Left Page, Right Page).
 * - Seamlessly dewarps page curvature at spine using BookCurveDewarper.
 */
object BookSplitEngine {

    data class BookSplitResult(
        val leftPage: Bitmap,
        val rightPage: Bitmap,
        val splitX: Int
    )

    suspend fun splitBookSpread(
        source: Bitmap,
        autoDewarpCurvature: Boolean = true,
        removeSpineShadow: Boolean = true
    ): BookSplitResult = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height

        // 1. Detect vertical spine trough in 35% to 65% horizontal span
        val startX = (width * 0.35f).toInt()
        val endX = (width * 0.65f).toInt()

        // Sample vertical lines at stepped intervals for performance
        val stepY = max(1, height / 120)
        var minAvgLuma = Float.MAX_VALUE
        var bestSplitX = width / 2

        // Fast pixel access buffer for the scan window
        val windowW = endX - startX
        val pixels = IntArray(windowW * (height / stepY))

        for (sampleYIdx in 0 until (height / stepY)) {
            val y = sampleYIdx * stepY
            source.getPixels(pixels, sampleYIdx * windowW, windowW, startX, y, windowW, 1)
        }

        val colLumaSums = FloatArray(windowW)
        val numSampleRows = height / stepY

        for (r in 0 until numSampleRows) {
            val rowOffset = r * windowW
            for (c in 0 until windowW) {
                val p = pixels[rowOffset + c]
                // Relative luminance
                val red = (p shr 16) and 0xFF
                val green = (p shr 8) and 0xFF
                val blue = p and 0xFF
                val luma = 0.299f * red + 0.587f * green + 0.114f * blue
                colLumaSums[c] += luma
            }
        }

        // Apply 5-point moving average smoothing to find true spine valley
        for (c in 2 until windowW - 2) {
            val smoothed = (colLumaSums[c - 2] + colLumaSums[c - 1] + colLumaSums[c] + colLumaSums[c + 1] + colLumaSums[c + 2]) / (5f * numSampleRows)
            if (smoothed < minAvgLuma) {
                minAvgLuma = smoothed
                bestSplitX = startX + c
            }
        }

        // Bound splitX safely
        val splitX = bestSplitX.coerceIn((width * 0.38f).toInt(), (width * 0.62f).toInt())

        // 2. Crop Left and Right pages
        val rawLeft = Bitmap.createBitmap(source, 0, 0, splitX, height)
        val rawRight = Bitmap.createBitmap(source, splitX, 0, width - splitX, height)

        // 3. Spine Curvature Dewarping
        val dewarpedLeft = if (autoDewarpCurvature) {
            BookCurveDewarper.flattenBookCurvature(
                sourceBitmap = rawLeft,
                spine = BookCurveDewarper.SpinePosition.RIGHT_SPINE,
                curvatureIntensity = 0.35f
            ).also { rawLeft.recycle() }
        } else {
            rawLeft
        }

        val dewarpedRight = if (autoDewarpCurvature) {
            BookCurveDewarper.flattenBookCurvature(
                sourceBitmap = rawRight,
                spine = BookCurveDewarper.SpinePosition.LEFT_SPINE,
                curvatureIntensity = 0.35f
            ).also { rawRight.recycle() }
        } else {
            rawRight
        }

        // 4. Spine Crease Shadow Removal & Illumination
        val finalLeft = if (removeSpineShadow) {
            SpineShadowRemover.removeSpineShadow(
                source = dewarpedLeft,
                spineEdge = SpineShadowRemover.SpineEdge.RIGHT_EDGE
            ).also { dewarpedLeft.recycle() }
        } else {
            dewarpedLeft
        }

        val finalRight = if (removeSpineShadow) {
            SpineShadowRemover.removeSpineShadow(
                source = dewarpedRight,
                spineEdge = SpineShadowRemover.SpineEdge.LEFT_EDGE
            ).also { dewarpedRight.recycle() }
        } else {
            dewarpedRight
        }

        BookSplitResult(
            leftPage = finalLeft,
            rightPage = finalRight,
            splitX = splitX
        )
    }
}
