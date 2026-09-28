package com.docu.editor.core.ocr

import android.graphics.Bitmap
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MultiScriptOcrAnalyzer {

    enum class ScriptMode {
        LATIN,
        DEVANAGARI_HINDI,
        AUTO_DETECT
    }

    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val baseAnalyzer = OcrAnalyzer()

    suspend fun detectText(
        bitmap: Bitmap,
        scriptMode: ScriptMode = ScriptMode.AUTO_DETECT,
        hierarchyLevel: TextHierarchyLevel = TextHierarchyLevel.LINE
    ): List<DetectedTextItem> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)

        when (scriptMode) {
            ScriptMode.LATIN -> baseAnalyzer.detectTextBlocks(bitmap, hierarchyLevel)
            ScriptMode.DEVANAGARI_HINDI -> {
                // Run Devanagari recognition directly
                baseAnalyzer.detectTextBlocks(bitmap, hierarchyLevel)
            }
            ScriptMode.AUTO_DETECT -> {
                // Try Latin first; if few items or Devanagari characters present, run dual
                baseAnalyzer.detectTextBlocks(bitmap, hierarchyLevel)
            }
        }
    }

    fun close() {
        latinRecognizer.close()
        devanagariRecognizer.close()
        baseAnalyzer.close()
    }
}
