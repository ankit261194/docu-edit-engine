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

        val items = when (scriptMode) {
            ScriptMode.LATIN -> baseAnalyzer.detectTextBlocks(bitmap, hierarchyLevel)
            ScriptMode.DEVANAGARI_HINDI, ScriptMode.AUTO_DETECT -> {
                baseAnalyzer.detectTextBlocks(bitmap, hierarchyLevel)
            }
        }
        items.map { item ->
            val cleaned = com.docu.editor.core.ocr.util.DevanagariPostProcessor.postProcess(item.text)
            if (cleaned != item.text) item.copy(text = cleaned) else item
        }
    }

    fun close() {
        latinRecognizer.close()
        devanagariRecognizer.close()
        baseAnalyzer.close()
    }
}
