package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Enterprise Document Auto-Orientation & Auto-Deskew Engine.
 * Automatically analyzes document text baseline orientation angles
 * and rotates upside-down or sideways scans (90°, 180°, 270°) to 0° upright,
 * with Devanagari & Latin dual-engine recognition and sub-degree micro-deskew precision.
 */
object AutoOrientationEngine {

    data class OrientationResult(
        val rotatedBitmap: Bitmap,
        val rotationAngleApplied: Float,
        val wasRotated: Boolean
    )

    private val devanagariRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Inspects bitmap text angles and automatically rotates to upright orientation.
     */
    suspend fun autoOrientAndDeskew(source: Bitmap): OrientationResult = withContext(Dispatchers.Default) {
        try {
            // Downsample for fast analysis if image is massive
            val maxDim = 1200
            val scale = if (source.width > maxDim || source.height > maxDim) {
                maxDim.toFloat() / maxOf(source.width, source.height)
            } else 1.0f

            val analysisBmp = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).roundToInt(),
                    (source.height * scale).roundToInt(),
                    true
                )
            } else source

            val inputImage = InputImage.fromBitmap(analysisBmp, 0)
            var visionText = try {
                devanagariRecognizer.process(inputImage).await()
            } catch (_: Exception) {
                null
            }

            if (visionText == null || visionText.textBlocks.isEmpty()) {
                visionText = try {
                    latinRecognizer.process(inputImage).await()
                } catch (_: Exception) {
                    null
                }
            }

            if (analysisBmp != source) {
                analysisBmp.recycle()
            }

            if (visionText == null) {
                val straightened = DocumentFilters.detectAndStraightenDocument(source)
                return@withContext OrientationResult(straightened, 0f, straightened != source)
            }

            val lines = visionText.textBlocks.flatMap { it.lines }
            if (lines.size < 2) {
                val straightened = DocumentFilters.detectAndStraightenDocument(source)
                return@withContext OrientationResult(straightened, 0f, straightened != source)
            }

            // Collect all line angles
            val angles = lines.map { it.angle }

            var count0 = 0
            var count90 = 0
            var count180 = 0
            var count270 = 0

            var sumAngle0 = 0f
            var sumAngle90 = 0f
            var sumAngle180 = 0f
            var sumAngle270 = 0f

            for (a in angles) {
                val normalized = ((a % 360f) + 360f) % 360f
                when {
                    normalized in 315f..360f || normalized in 0f..45f -> {
                        count0++
                        val dev = if (normalized > 180f) normalized - 360f else normalized
                        sumAngle0 += dev
                    }
                    normalized in 45f..135f -> {
                        count90++
                        sumAngle90 += (normalized - 90f)
                    }
                    normalized in 135f..225f -> {
                        count180++
                        sumAngle180 += (normalized - 180f)
                    }
                    normalized in 225f..315f -> {
                        count270++
                        sumAngle270 += (normalized - 270f)
                    }
                }
            }

            // Determine dominant quadrant
            val maxCount = maxOf(count0, count90, count180, count270)
            var correctionAngle = 0f

            when (maxCount) {
                count90 -> {
                    val fineDrift = if (count90 > 0) sumAngle90 / count90 else 0f
                    correctionAngle = -90f - fineDrift
                }
                count180 -> {
                    val fineDrift = if (count180 > 0) sumAngle180 / count180 else 0f
                    correctionAngle = 180f - fineDrift
                }
                count270 -> {
                    val fineDrift = if (count270 > 0) sumAngle270 / count270 else 0f
                    correctionAngle = 90f - fineDrift
                }
                count0 -> {
                    val fineDrift = if (count0 > 0) sumAngle0 / count0 else 0f
                    if (abs(fineDrift) >= 0.8f && abs(fineDrift) <= 15f) {
                        correctionAngle = -fineDrift
                    }
                }
            }

            var workingBmp = source
            var totalAngle = 0f
            var rotated = false

            if (abs(correctionAngle) >= 0.5f) {
                val matrix = Matrix().apply { postRotate(correctionAngle) }
                workingBmp = Bitmap.createBitmap(
                    source,
                    0,
                    0,
                    source.width,
                    source.height,
                    matrix,
                    true
                )
                totalAngle = correctionAngle
                rotated = true
            }

            // Apply fine micro-straightening via OpenCV Hough line angle detection
            val straightened = DocumentFilters.detectAndStraightenDocument(workingBmp)
            if (straightened != workingBmp) {
                if (workingBmp != source) {
                    workingBmp.recycle()
                }
                workingBmp = straightened
                rotated = true
            }

            OrientationResult(workingBmp, totalAngle, rotated)
        } catch (_: Exception) {
            OrientationResult(source, 0f, false)
        }
    }
}
