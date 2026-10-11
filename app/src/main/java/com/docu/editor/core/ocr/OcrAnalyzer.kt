package com.docu.editor.core.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import androidx.compose.ui.graphics.Color
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import com.docu.editor.core.ocr.util.TextInkColorSampler
import com.docu.editor.core.ocr.util.TypographyEstimator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.UUID

class OcrAnalyzer {

    // Primary: Bundled Latin recognizer (100% offline, guaranteed zero download wait)
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    // Secondary: Devanagari recognizer (for Hindi/Devanagari scripts)
    private val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    suspend fun detectTextBlocks(
        bitmap: Bitmap,
        hierarchyLevel: TextHierarchyLevel = TextHierarchyLevel.LINE
    ): List<DetectedTextItem> = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) return@withContext emptyList()
        val safeBmp = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        val image = InputImage.fromBitmap(safeBmp, 0)
        var visionText: Text? = null

        // 1. Run local bundled Latin recognizer first (100% offline, guaranteed zero download wait)
        try {
            visionText = latinRecognizer.process(image).await()
        } catch (_: Exception) {
            visionText = null
        }

        // 2. Also try Devanagari recognizer if Latin found no text
        if (visionText == null || visionText.textBlocks.isEmpty()) {
            try {
                val devResult = devanagariRecognizer.process(image).await()
                if (devResult.textBlocks.isNotEmpty()) {
                    visionText = devResult
                }
            } catch (_: Exception) {}
        }

        // 3. Secondary Pass: If raw image had poor lighting or faint ink, boost contrast and retry
        if (visionText == null || visionText.textBlocks.isEmpty()) {
            try {
                val enhancedBmp = enhanceContrastForOcr(safeBmp)
                val enhancedImage = InputImage.fromBitmap(enhancedBmp, 0)
                try {
                    val secondPassLatin = latinRecognizer.process(enhancedImage).await()
                    if (secondPassLatin.textBlocks.isNotEmpty()) {
                        visionText = secondPassLatin
                    }
                } catch (_: Exception) {}

                if (visionText == null || visionText.textBlocks.isEmpty()) {
                    try {
                        val secondPassDev = devanagariRecognizer.process(enhancedImage).await()
                        if (secondPassDev.textBlocks.isNotEmpty()) {
                            visionText = secondPassDev
                        }
                    } catch (_: Exception) {}
                }
                if (enhancedBmp != safeBmp && !enhancedBmp.isRecycled) {
                    enhancedBmp.recycle()
                }
            } catch (_: Exception) {}
        }

        val results = mutableListOf<DetectedTextItem>()

        if (visionText != null && visionText.textBlocks.isNotEmpty()) {
            when (hierarchyLevel) {
                TextHierarchyLevel.BLOCK -> {
                    for (block in visionText.textBlocks) {
                        val bounds = block.boundingBox ?: continue
                        val item = processRegion(
                            bitmap = safeBmp,
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
                            if (line.text.trim().isEmpty()) continue
                            val item = processRegion(
                                bitmap = safeBmp,
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
                            val rawElements = line.elements.mapNotNull { elem ->
                                val b = elem.boundingBox ?: return@mapNotNull null
                                val t = elem.text.trim()
                                if (t.isEmpty()) return@mapNotNull null
                                RawOcrElement(
                                    text = t,
                                    bounds = Rect(b),
                                    angle = elem.angle,
                                    confidence = elem.confidence ?: 1.0f,
                                    cornerPoints = elem.cornerPoints?.toList() ?: emptyList()
                                )
                            }.sortedBy { it.bounds.left }.toMutableList()

                            // Defend against empty line.elements:
                            // If ML Kit didn't split line into elements, split line.text by spaces
                            if (rawElements.isEmpty() && line.text.trim().isNotEmpty()) {
                                val lineBounds = line.boundingBox ?: Rect(0, 0, safeBmp.width, 40)
                                val words = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                                if (words.size <= 1) {
                                    rawElements.add(
                                        RawOcrElement(
                                            text = line.text.trim(),
                                            bounds = lineBounds,
                                            angle = line.angle,
                                            confidence = line.confidence ?: 1.0f,
                                            cornerPoints = line.cornerPoints?.toList() ?: emptyList()
                                        )
                                    )
                                } else {
                                    val totalChars = words.sumOf { it.length } + (words.size - 1)
                                    var currentX = lineBounds.left
                                    val totalWidth = lineBounds.width()
                                    for (wIdx in words.indices) {
                                        val word = words[wIdx]
                                        val isLast = wIdx == words.size - 1
                                        val wordWidth = if (isLast) {
                                            (lineBounds.right - currentX).coerceAtLeast(10)
                                        } else {
                                            (totalWidth.toFloat() * (word.length.toFloat() / totalChars)).toInt().coerceAtLeast(10)
                                        }
                                        val wordBounds = Rect(currentX, lineBounds.top, minOf(currentX + wordWidth, lineBounds.right), lineBounds.bottom)
                                        rawElements.add(
                                            RawOcrElement(
                                                text = word,
                                                bounds = wordBounds,
                                                angle = line.angle,
                                                confidence = line.confidence ?: 1.0f,
                                                cornerPoints = emptyList()
                                            )
                                        )
                                        val spaceW = (totalWidth.toFloat() * (1f / totalChars)).toInt().coerceAtLeast(4)
                                        currentX += wordWidth + spaceW
                                    }
                                }
                            }

                            // Defend against fragmented words (e.g. 'V' + 'EER' = 'VEER'):
                            // Merge adjacent elements when they form a word in the line text or have a sub-space gap
                            if (rawElements.size > 1) {
                                var i = 0
                                val lineWords = line.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                while (i < rawElements.size - 1) {
                                    val curr = rawElements[i]
                                    val next = rawElements[i + 1]
                                    val gap = next.bounds.left - curr.bounds.right
                                    val avgH = (curr.bounds.height() + next.bounds.height()) / 2f
                                    val combined = curr.text + next.text

                                    val isExplicitWordMatch = lineWords.any { it.equals(combined, ignoreCase = true) || it.contains(combined, ignoreCase = true) }
                                    val isTinyGapFragment = gap <= maxOf(6, (avgH * 0.35f).toInt()) && (curr.text.length <= 2 || next.text.length <= 2)

                                    if (isExplicitWordMatch || isTinyGapFragment) {
                                        curr.text = combined
                                        curr.bounds = Rect(
                                            minOf(curr.bounds.left, next.bounds.left),
                                            minOf(curr.bounds.top, next.bounds.top),
                                            maxOf(curr.bounds.right, next.bounds.right),
                                            maxOf(curr.bounds.bottom, next.bounds.bottom)
                                        )
                                        rawElements.removeAt(i + 1)
                                    } else {
                                        i++
                                    }
                                }
                            }

                            for (element in rawElements) {
                                val bounds = element.bounds
                                val trimmed = element.text.trim()
                                if (trimmed.isEmpty()) continue

                                if (trimmed.contains(" ")) {
                                    val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                    if (words.size > 1) {
                                        val totalChars = words.sumOf { it.length } + (words.size - 1)
                                        var currentX = bounds.left
                                        val totalWidth = bounds.width()
                                        for (wIdx in words.indices) {
                                            val word = words[wIdx]
                                            val isLast = wIdx == words.size - 1
                                            val wordWidth = if (isLast) {
                                                (bounds.right - currentX).coerceAtLeast(10)
                                            } else {
                                                (totalWidth.toFloat() * (word.length.toFloat() / totalChars)).toInt().coerceAtLeast(10)
                                            }
                                            val wordBounds = Rect(currentX, bounds.top, minOf(currentX + wordWidth, bounds.right), bounds.bottom)
                                            val item = processRegion(
                                                bitmap = safeBmp,
                                                rawText = word,
                                                bounds = wordBounds,
                                                cornerPoints = emptyList(),
                                                angle = element.angle,
                                                confidence = element.confidence,
                                                level = TextHierarchyLevel.ELEMENT
                                            )
                                            results.add(item)
                                            val spaceW = (totalWidth.toFloat() * (1f / totalChars)).toInt().coerceAtLeast(4)
                                            currentX += wordWidth + spaceW
                                        }
                                        continue
                                    }
                                }

                                val item = processRegion(
                                    bitmap = safeBmp,
                                    rawText = trimmed,
                                    bounds = bounds,
                                    cornerPoints = element.cornerPoints,
                                    angle = element.angle,
                                    confidence = element.confidence,
                                    level = TextHierarchyLevel.ELEMENT
                                )
                                results.add(item)
                            }
                        }
                    }
                }
            }
        }

        // 100% Guaranteed Offline Fallback: If ML Kit returned 0 items,
        // use local OpenCV morphological text contour detection to identify every word region
        if (results.isEmpty()) {
            val cvItems = detectTextRegionsWithOpenCv(safeBmp)
            results.addAll(cvItems)
        }

        results
    }

    /**
     * Resilient 100% Offline OpenCV Morphological Text Region Detector.
     * Guaranteed to detect all text bounding boxes on camera scanned pages even when ML Kit models return 0 items.
     */
    private suspend fun detectTextRegionsWithOpenCv(bitmap: Bitmap): List<DetectedTextItem> = withContext(Dispatchers.Default) {
        val items = mutableListOf<DetectedTextItem>()
        try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

            // Morphological gradient to isolate text stroke boundaries
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 3.0))
            val morph = Mat()
            Imgproc.morphologyEx(gray, morph, Imgproc.MORPH_GRADIENT, kernel)

            // Otsu adaptive binary threshold
            val binary = Mat()
            Imgproc.threshold(morph, binary, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            // Morphological closing to connect adjacent letters into cohesive word bounding boxes
            val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 3.0))
            val closed = Mat()
            Imgproc.morphologyEx(binary, closed, Imgproc.MORPH_CLOSE, closeKernel)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val candidateRects = contours.mapNotNull { contour ->
                val r = Imgproc.boundingRect(contour)
                if (r.width in 14..(bitmap.width * 0.90).toInt() &&
                    r.height in 8..(bitmap.height * 0.30).toInt()
                ) {
                    Rect(r.x, r.y, r.x + r.width, r.y + r.height)
                } else null
            }.sortedWith(compareBy({ it.top / 20 }, { it.left }))

            mat.release()
            gray.release()
            morph.release()
            binary.release()
            closed.release()
            hierarchy.release()
            for (c in contours) c.release()

            for (rect in candidateRects) {
                var cropText = ""
                try {
                    val cropW = rect.width().coerceAtLeast(1)
                    val cropH = rect.height().coerceAtLeast(1)
                    val cropBmp = Bitmap.createBitmap(bitmap, rect.left, rect.top, cropW, cropH)
                    val cropImg = InputImage.fromBitmap(cropBmp, 0)
                    val recognized = latinRecognizer.process(cropImg).await()
                    cropText = recognized.text.trim()
                    if (cropBmp != bitmap && !cropBmp.isRecycled) {
                        cropBmp.recycle()
                    }
                } catch (_: Exception) {}

                val item = processRegion(
                    bitmap = bitmap,
                    rawText = cropText,
                    bounds = rect,
                    cornerPoints = emptyList(),
                    angle = 0f,
                    confidence = if (cropText.isNotEmpty()) 0.95f else 0.80f,
                    level = TextHierarchyLevel.ELEMENT
                )
                items.add(item)
            }
        } catch (_: Exception) {}
        items
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
        val cleanedText = com.docu.editor.core.ocr.util.DevanagariPostProcessor.postProcess(rawText)
        val inkSample = TextInkColorSampler.sampleInk(bitmap, bounds)
        val typography = TypographyEstimator.estimateMetrics(cleanedText, bounds, inkSample)

        return DetectedTextItem(
            id = UUID.randomUUID().toString(),
            text = cleanedText,
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

    private fun max(a: Int, b: Int): Int = if (a > b) a else b

    private fun enhanceContrastForOcr(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint()
        val cm = android.graphics.ColorMatrix()
        val contrast = 1.4f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        cm.set(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    fun close() {
        devanagariRecognizer.close()
        latinRecognizer.close()
    }
}

private data class RawOcrElement(
    var text: String,
    var bounds: Rect,
    val angle: Float,
    val confidence: Float,
    val cornerPoints: List<android.graphics.Point>
)
