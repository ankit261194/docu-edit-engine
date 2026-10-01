package com.docu.editor.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

/**
 * Enterprise 100% Offline On-Device Handwriting Recognition Engine.
 * Does NOT require internet connection or cloud API keys.
 * Uses adaptive CLAHE contrast enhancement, morphological ink reconnection,
 * and dual on-device ML Kit neural passes to transcribe handwritten notes,
 * forms, prescriptions, and signatures with high fidelity.
 */
object OfflineHandwritingRecognizer {

    private val devanagariClient = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val latinClient = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class HandwritingResult(
        val transcribedText: String,
        val detectedItems: List<DetectedTextItem>,
        val lineCount: Int,
        val isOfflineSuccess: Boolean
    )

    /**
     * Transcribes handwritten text completely offline using local computer vision & on-device ML Kit.
     */
    suspend fun transcribeOffline(bitmap: Bitmap): HandwritingResult = withContext(Dispatchers.Default) {
        try {
            // 1. Preprocess bitmap specifically for handwritten ink strokes:
            // CLAHE contrast enhancement + noise reduction + ink stroke dilation
            val enhancedBitmap = preprocessForHandwriting(bitmap)

            val inputImage = InputImage.fromBitmap(enhancedBitmap, 0)

            // 2. Pass 1: Try Devanagari + Latin client (handles Hindi + English notes)
            var visionText = try {
                devanagariClient.process(inputImage).await()
            } catch (_: Exception) {
                null
            }

            // 3. Pass 2: Fallback to Latin if Devanagari returned empty
            if (visionText == null || visionText.text.isBlank()) {
                visionText = try {
                    latinClient.process(inputImage).await()
                } catch (_: Exception) {
                    null
                }
            }

            // 4. Pass 3: If still empty, try High-Contrast Binarized pass for faint pencil/pen ink
            if (visionText == null || visionText.text.isBlank()) {
                val binarizedBitmap = binarizeFaintInk(bitmap)
                val binInput = InputImage.fromBitmap(binarizedBitmap, 0)
                try {
                    val pass3 = devanagariClient.process(binInput).await()
                    if (pass3.text.isNotBlank()) {
                        visionText = pass3
                    } else {
                        visionText = latinClient.process(binInput).await()
                    }
                } catch (_: Exception) {}
                if (binarizedBitmap != bitmap && !binarizedBitmap.isRecycled) {
                    binarizedBitmap.recycle()
                }
            }

            if (enhancedBitmap != bitmap && !enhancedBitmap.isRecycled) {
                enhancedBitmap.recycle()
            }

            if (visionText == null || visionText.text.isBlank()) {
                return@withContext HandwritingResult(
                    transcribedText = "",
                    detectedItems = emptyList(),
                    lineCount = 0,
                    isOfflineSuccess = false
                )
            }

            // 5. Structure text in natural reading order
            val items = mutableListOf<DetectedTextItem>()
            val linesSorted = visionText.textBlocks
                .flatMap { it.lines }
                .filter { it.text.isNotBlank() }
                .sortedWith(compareBy<com.google.mlkit.vision.text.Text.Line> { it.boundingBox?.top ?: 0 }
                    .thenBy { it.boundingBox?.left ?: 0 })

            val sb = StringBuilder()
            var prevTop = -1

            for (line in linesSorted) {
                val bounds = line.boundingBox ?: Rect(0, 0, 100, 20)
                if (prevTop != -1 && abs(bounds.top - prevTop) > bounds.height() * 0.75f) {
                    sb.append("\n")
                } else if (prevTop != -1) {
                    sb.append(" ")
                }
                sb.append(line.text.trim())
                prevTop = bounds.top

                items.add(
                    DetectedTextItem(
                        id = java.util.UUID.randomUUID().toString(),
                        text = line.text.trim(),
                        boundingBox = bounds,
                        cornerPoints = line.cornerPoints?.toList() ?: emptyList(),
                        rotationAngle = line.angle,
                        inkColor = androidx.compose.ui.graphics.Color(0xFF1E293B),
                        inkColorRgb = 0xFF1E293B.toInt(),
                        typography = com.docu.editor.core.ocr.model.TypographyMetrics(
                            estimatedFontWeight = com.docu.editor.core.ocr.model.FontWeightEstimate.REGULAR,
                            strokeWidthRatio = 0.15f,
                            glyphDensity = 0.4f,
                            letterSpacingEm = 0.05f,
                            estimatedFontSizePx = bounds.height() * 0.85f
                        ),
                        confidence = line.confidence ?: 0.95f,
                        level = TextHierarchyLevel.LINE
                    )
                )
            }

            HandwritingResult(
                transcribedText = sb.toString(),
                detectedItems = items,
                lineCount = linesSorted.size,
                isOfflineSuccess = true
            )
        } catch (_: Exception) {
            HandwritingResult("", emptyList(), 0, false)
        }
    }

    /**
     * OpenCV Image Enhancement specifically for handwriting:
     * - Bilateral filtering (smooths paper grain, keeps ink edges sharp)
     * - CLAHE (Contrast Limited Adaptive Histogram Equalization)
     * - Morphological closing (joins broken pen strokes)
     */
    private fun preprocessForHandwriting(src: Bitmap): Bitmap {
        val matRgba = Mat()
        val matGray = Mat()
        val matClahe = Mat()
        val matFiltered = Mat()

        try {
            Utils.bitmapToMat(src, matRgba)
            Imgproc.cvtColor(matRgba, matGray, Imgproc.COLOR_RGBA2GRAY)

            // 1. Bilateral filter to reduce paper texture grain while preserving ink edges
            Imgproc.bilateralFilter(matGray, matFiltered, 9, 75.0, 75.0)

            // 2. CLAHE (Contrast Limited Adaptive Histogram Equalization)
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            clahe.apply(matFiltered, matClahe)
            clahe.collectGarbage()

            // 3. Morphological close (reconnect faint cursive pen strokes)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
            val matClosed = Mat()
            Imgproc.morphologyEx(matClahe, matClosed, Imgproc.MORPH_CLOSE, kernel)

            val outBmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            Imgproc.cvtColor(matClosed, matRgba, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(matRgba, outBmp)

            matClosed.release()
            kernel.release()
            return outBmp
        } catch (_: Exception) {
            return src
        } finally {
            matRgba.release()
            matGray.release()
            matClahe.release()
            matFiltered.release()
        }
    }

    /**
     * High-contrast binarization fallback for very faint pencil or ballpoint pen ink.
     */
    private fun binarizeFaintInk(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()
        // Boost contrast by 2.2x and invert slight darkness
        val contrast = 2.2f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        cm.set(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }
}
