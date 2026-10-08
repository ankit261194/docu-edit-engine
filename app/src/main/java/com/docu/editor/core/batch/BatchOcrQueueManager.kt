package com.docu.editor.core.batch

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import com.docu.editor.core.ocr.OcrAnalyzer
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import com.docu.editor.core.pdf.PdfExportEngine
import com.docu.editor.core.pdf.PdfPageLoader
import com.docu.editor.core.util.ExifBitmapUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class BatchProgressState(
    val isRunning: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val successCount: Int = 0,
    val failedCount: Int = 0,
    val isFinished: Boolean = false,
    val message: String = ""
)

/**
 * Enterprise Bulk Multi-Document Batch OCR Queue.
 * Processes 10-50+ multi-page documents/photos sequentially in background,
 * executes OCR, embeds searchable text layers, and exports standardized PDFs.
 */
class BatchOcrQueueManager(private val context: Context) {

    private val _progress = MutableStateFlow(BatchProgressState())
    val progress: StateFlow<BatchProgressState> = _progress.asStateFlow()

    private val ocrAnalyzer = OcrAnalyzer()

    suspend fun processBatch(
        documentUris: List<Uri>,
        fitToA4: Boolean = true
    ): List<File> = withContext(Dispatchers.IO) {
        val total = documentUris.size
        val outputFiles = mutableListOf<File>()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        _progress.value = BatchProgressState(
            isRunning = true,
            currentFileIndex = 0,
            totalFiles = total,
            message = "Starting batch processing of $total documents..."
        )

        var successCount = 0
        var failedCount = 0

        for ((index, uri) in documentUris.withIndex()) {
            val fileNum = index + 1
            val rawName = getFileNameFromUri(uri) ?: "Doc_$fileNum"
            val cleanName = rawName.substringBeforeLast(".")

            _progress.value = _progress.value.copy(
                currentFileIndex = fileNum,
                currentFileName = rawName,
                message = "Processing ($fileNum/$total): $rawName..."
            )

            try {
                val outFile = File(downloadsDir, "DocuEdit_Batch_${System.currentTimeMillis()}_$cleanName.pdf")
                val isPdf = uri.toString().endsWith(".pdf", ignoreCase = true) ||
                            context.contentResolver.getType(uri)?.contains("pdf", ignoreCase = true) == true

                if (isPdf) {
                    val pageCount = PdfPageLoader.getPageCount(context, uri)
                    if (pageCount > 0) {
                        PdfExportEngine.exportPagesStreamingToPdf(
                            pageCount = pageCount,
                            pageBitmapProvider = { pIdx ->
                                PdfPageLoader.renderPageToBitmap(context, uri, pIdx)
                            },
                            outputFile = outFile,
                            fitToA4 = fitToA4,
                            pagesDetectedItems = emptyMap(),
                            autoRecycleBitmaps = true,
                            ocrFallbackProvider = { bmp ->
                                val items = ocrAnalyzer.detectTextBlocks(bmp, TextHierarchyLevel.LINE)
                                val pageText = items.joinToString(" ") { it.text }
                                if (pageText.isNotBlank()) {
                                    kotlinx.coroutines.runBlocking {
                                        DocumentFtsIndexManager.getInstance(context).indexPage(
                                            docId = outFile.name,
                                            filePath = outFile.absolutePath,
                                            fileName = cleanName,
                                            extractedText = pageText,
                                            pageNumber = 1
                                        )
                                    }
                                }
                                items
                            }
                        )
                        outputFiles.add(outFile)
                        successCount++
                    } else {
                        failedCount++
                    }
                } else {
                    // Image format
                    val bmp = ExifBitmapUtil.decodeUriWithExif(context, uri, 2880)
                    if (bmp != null) {
                        PdfExportEngine.exportBitmapToPdf(
                            bitmap = bmp,
                            outputFile = outFile,
                            fitToA4 = fitToA4,
                            detectedItems = emptyList(),
                            ocrFallbackProvider = { b ->
                                val items = ocrAnalyzer.detectTextBlocks(b, TextHierarchyLevel.LINE)
                                val pageText = items.joinToString(" ") { it.text }
                                if (pageText.isNotBlank()) {
                                    kotlinx.coroutines.runBlocking {
                                        DocumentFtsIndexManager.getInstance(context).indexPage(
                                            docId = outFile.name,
                                            filePath = outFile.absolutePath,
                                            fileName = cleanName,
                                            extractedText = pageText,
                                            pageNumber = 1
                                        )
                                    }
                                }
                                items
                            }
                        )
                        bmp.recycle()
                        outputFiles.add(outFile)
                        successCount++
                    } else {
                        failedCount++
                    }
                }
            } catch (_: Exception) {
                failedCount++
            }

            _progress.value = _progress.value.copy(
                successCount = successCount,
                failedCount = failedCount
            )
        }

        _progress.value = _progress.value.copy(
            isRunning = false,
            isFinished = true,
            message = "Batch complete! $successCount saved to Downloads, $failedCount failed."
        )

        outputFiles
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    /**
     * Sub-millisecond SQLite FTS full-text search across all batch OCR indexed documents.
     */
    suspend fun searchDocuments(query: String): List<FtsSearchResult> {
        return DocumentFtsIndexManager.getInstance(context).search(query)
    }

    suspend fun getIndexedDocumentCount(): Int {
        return DocumentFtsIndexManager.getInstance(context).getIndexedCount()
    }
}
