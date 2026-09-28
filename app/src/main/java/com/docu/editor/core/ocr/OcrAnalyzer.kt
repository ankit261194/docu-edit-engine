package com.docu.editor.core.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import com.docu.editor.core.ocr.util.TextInkColorSampler
import com.docu.editor.core.ocr.util.TypographyEstimator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class OcrAnalyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun detectTextBlocks(
        bitmap: Bitmap,
        hierarchyLevel: TextHierarchyLevel = TextHierarchyLevel.LINE
    ): List<DetectedTextItem> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText: Text = recognizer.process(image).await()

        val results = mutableListOf<DetectedTextItem>()

        when (hierarchyLevel) {
            TextHierarchyLevel.BLOCK -> {
                for (block in visionText.textBlocks) {
                    val bounds = block.boundingBox ?: continue
                    val item = processRegion(
                        bitmap = bitmap,
                        rawText = block.text,
                        bounds = bounds,
                        cornerPoints = block.cornerPoints?.toList() ?: emptyList(),
                        angle = block.lines.firstOrNull()?.angle ?: 0f,
                        confidence = 1.0f,
                        level = TextHierarchyLevel.BLOCK
                    )
                    results.add(item)
                }
            }

            TextHierarchyLevel.LINE -> {
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val bounds = line.boundingBox ?: continue
                        val item = processRegion(
                            bitmap = bitmap,
                            rawText = line.text,
                            bounds = bounds,
                            cornerPoints = line.cornerPoints?.toList() ?: emptyList(),
                            angle = line.angle,
                            confidence = line.confidence ?: 1.0f,
                            level = TextHierarchyLevel.LINE
                        )
                        results.add(item)
                    }
                }
            }

            TextHierarchyLevel.ELEMENT -> {
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            val bounds = element.boundingBox ?: continue
                            val item = processRegion(
                                bitmap = bitmap,
                                rawText = element.text,
                                bounds = bounds,
                                cornerPoints = element.cornerPoints?.toList() ?: emptyList(),
                                angle = element.angle,
                                confidence = element.confidence ?: 1.0f,
                                level = TextHierarchyLevel.ELEMENT
                            )
                            results.add(item)
                        }
                    }
                }
            }
        }

        results
    }

    private fun processRegion(
        bitmap: Bitmap,
        rawText: String,
        bounds: Rect,
        cornerPoints: List<android.graphics.Point>,
        angle: Float,
        confidence: Float,
        level: TextHierarchyLevel
    ): DetectedTextItem {
        val inkSample = TextInkColorSampler.sampleInk(bitmap, bounds)
        val typography = TypographyEstimator.estimateMetrics(rawText, bounds, inkSample)

        return DetectedTextItem(
            id = UUID.randomUUID().toString(),
            text = rawText,
            boundingBox = bounds,
            cornerPoints = cornerPoints,
            rotationAngle = angle,
            inkColor = Color(inkSample.dominantRgb),
            inkColorRgb = inkSample.dominantRgb,
            typography = typography,
            confidence = confidence,
            level = level
        )
    }

    fun close() {
        recognizer.close()
    }
}
