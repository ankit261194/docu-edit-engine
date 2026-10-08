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
     * Features:
     * - Dominant Text Line Angle Voting across top text lines weighted by length.
     * - Image EXIF sensor fusion to reliably resolve upside-down (180°) and sideways (90°/270°) orientations.
     * - Devanagari & Latin dual-engine OCR support.
     * - Sub-degree micro-deskew precision.
     */
    suspend fun autoOrientAndDeskew(
        source: Bitmap,
        exifOrientationDegrees: Int = 0
    ): OrientationResult = withContext(Dispatchers.Default) {
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

            val lines = visionText?.textBlocks?.flatMap { it.lines } ?: emptyList()
            if (lines.size < 2) {
                var working = source
                var angleApplied = 0f
                var wasRotated = false
                val normExif = ((exifOrientationDegrees % 360) + 360) % 360
                if (normExif != 0) {
                    val matrix = Matrix().apply { postRotate(normExif.toFloat()) }
                    working = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                    angleApplied = normExif.toFloat()
                    wasRotated = true
                }
                val straightened = DocumentFilters.detectAndStraightenDocument(working)
                return@withContext OrientationResult(straightened, angleApplied, wasRotated || (straightened != working))
            }

            var weight0 = 0.0
            var weight90 = 0.0
            var weight180 = 0.0
            var weight270 = 0.0

            var sumAngle0 = 0.0
            var sumAngle90 = 0.0
            var sumAngle180 = 0.0
            var sumAngle270 = 0.0

            // Apply EXIF sensor prior weight if known
            when (((exifOrientationDegrees % 360) + 360) % 360) {
                90 -> weight90 += 12.0
                180 -> weight180 += 16.0
                270 -> weight270 += 12.0
                else -> {}
            }

            // Top text blocks sorted by length (longer sentences provide high confidence orientation)
            val topLines = lines.sortedByDescending { it.text.length }.take(35)
            for (line in topLines) {
                val a = line.angle
                val lineWeight = maxOf(1.0, line.text.length.toDouble() * 0.5)
                val normalized = ((a % 360f) + 360f) % 360f
                when {
                    normalized in 315f..360f || normalized in 0f..45f -> {
                        weight0 += lineWeight
                        val dev = if (normalized > 180f) normalized - 360f else normalized
                        sumAngle0 += dev * lineWeight
                    }
                    normalized in 45f..135f -> {
                        weight90 += lineWeight
                        sumAngle90 += (normalized - 90f) * lineWeight
                    }
                    normalized in 135f..225f -> {
                        weight180 += lineWeight
                        sumAngle180 += (normalized - 180f) * lineWeight
                    }
                    normalized in 225f..315f -> {
                        weight270 += lineWeight
                        sumAngle270 += (normalized - 270f) * lineWeight
                    }
                }
            }

            val maxWeight = maxOf(weight0, weight90, weight180, weight270)
            var correctionAngle = 0f

            when (maxWeight) {
                weight90 -> {
                    val fineDrift = if (weight90 > 0.0) (sumAngle90 / weight90).toFloat() else 0f
                    correctionAngle = -90f - fineDrift
                }
                weight180 -> {
                    val fineDrift = if (weight180 > 0.0) (sumAngle180 / weight180).toFloat() else 0f
                    correctionAngle = 180f - fineDrift
                }
                weight270 -> {
                    val fineDrift = if (weight270 > 0.0) (sumAngle270 / weight270).toFloat() else 0f
                    correctionAngle = 90f - fineDrift
                }
                weight0 -> {
                    val fineDrift = if (weight0 > 0.0) (sumAngle0 / weight0).toFloat() else 0f
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
