package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
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
 * with sub-degree micro-deskew precision.
 */
object AutoOrientationEngine {

    data class OrientationResult(
        val rotatedBitmap: Bitmap,
        val rotationAngleApplied: Float,
        val wasRotated: Boolean
    )

    private val recognizer by lazy {
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
            val visionText = recognizer.process(inputImage).await()

            if (analysisBmp != source) {
                analysisBmp.recycle()
            }

            val lines = visionText.textBlocks.flatMap { it.lines }
            if (lines.size < 2) {
                // Not enough text lines to determine orientation
                return@withContext OrientationResult(source, 0f, false)
            }

            // Collect all line angles
            val angles = lines.map { it.angle }

            // Group into 4 main orientation quadrants:
            // Quadrant 0: around 0° / 360° (Normal upright)
            // Quadrant 90: around 90° / -270° (Clockwise sideways)
            // Quadrant 180: around 180° / -180° (Upside down)
            // Quadrant 270: around 270° / -90° (Counter-clockwise sideways)
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
                    // Lines are at ~90°, rotate by -90° (or 270°) to make them upright
                    val fineDrift = if (count90 > 0) sumAngle90 / count90 else 0f
                    correctionAngle = -90f - fineDrift
                }
                count180 -> {
                    // Lines are upside down at ~180°, rotate 180°
                    val fineDrift = if (count180 > 0) sumAngle180 / count180 else 0f
                    correctionAngle = 180f - fineDrift
                }
                count270 -> {
                    // Lines are at ~270°, rotate by +90°
                    val fineDrift = if (count270 > 0) sumAngle270 / count270 else 0f
                    correctionAngle = 90f - fineDrift
                }
                count0 -> {
                    // Already mostly upright, check fine micro-deskew (±1° to ±12°)
                    val fineDrift = if (count0 > 0) sumAngle0 / count0 else 0f
                    if (abs(fineDrift) >= 0.8f && abs(fineDrift) <= 15f) {
                        correctionAngle = -fineDrift
                    }
                }
            }

            // If no correction needed
            if (abs(correctionAngle) < 0.5f) {
                return@withContext OrientationResult(source, 0f, false)
            }

            // Rotate bitmap
            val matrix = Matrix().apply { postRotate(correctionAngle) }
            val rotated = Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true
            )

            OrientationResult(rotated, correctionAngle, true)
        } catch (_: Exception) {
            OrientationResult(source, 0f, false)
        }
    }
}
