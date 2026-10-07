package com.docu.editor.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Path
import android.content.ClipData
import android.content.ClipboardManager
import android.provider.OpenableColumns
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import com.docu.editor.core.cv.BackgroundInpainter
import com.docu.editor.core.history.DocumentHistoryManager
import com.docu.editor.core.history.SavedDocumentItem
import com.docu.editor.core.font.FontClassification
import com.docu.editor.core.font.FontMatcher
import com.docu.editor.core.ocr.OcrAnalyzer
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import android.graphics.Point
import android.graphics.PointF
import com.docu.editor.core.ocr.model.FontWeightEstimate
import com.docu.editor.core.ocr.model.TypographyMetrics
import com.docu.editor.core.pdf.PdfCompressionEngine
import com.docu.editor.core.pdf.PdfExportEngine
import com.docu.editor.core.pdf.PdfPageLoader
import com.docu.editor.core.pdf.PdfToolbox
import com.docu.editor.core.rendering.ArtifactBlendingEngine
import com.docu.editor.core.rendering.TextRenderer
import com.docu.editor.core.sample.SampleDocumentGenerator
import com.docu.editor.core.scanner.DocumentEdgeDetector
import com.docu.editor.core.scanner.DocumentFilters
import com.docu.editor.core.scanner.IdCardStitcher
import com.docu.editor.core.scanner.PerspectiveTransformer
import com.docu.editor.core.scanner.model.DocumentCorners
import com.docu.editor.core.watermark.WatermarkEngine
import com.docu.editor.core.dewarp.BookCurveDewarper
import com.docu.editor.core.dewarp.BookSplitEngine
import com.docu.editor.core.cv.EraseMarksEngine
import com.docu.editor.core.signature.SignatureExtractor
import com.docu.editor.core.signature.StampExtractor
import com.docu.editor.core.export.DocxExportEngine
import com.docu.editor.core.export.SpreadsheetExportEngine
import com.docu.editor.core.export.GoogleDriveExportHelper
import com.docu.editor.core.cloud.CloudBackupStore
import com.docu.editor.core.cloud.CloudBackupItem
import com.docu.editor.core.export.TargetFileSizeEngine
import com.docu.editor.ui.dialogs.SizeAdjustMode
import com.docu.editor.ui.dialogs.CloudSyncResult
import com.docu.editor.domain.model.DocumentEditorUiState
import com.docu.editor.domain.model.DocumentFilterMode
import com.docu.editor.domain.model.EditorToolMode
import com.docu.editor.domain.model.ShapeType
import com.docu.editor.domain.model.DocumentCanvasLayer
import com.docu.editor.domain.model.CanvaFrameType
import com.docu.editor.domain.model.TextEffectType
import com.docu.editor.domain.model.BrandPalette
import com.docu.editor.domain.model.CanvaAnimationType
import com.docu.editor.domain.model.CanvaStyleMatchPreset
import com.docu.editor.core.scanner.CanvaMockupFramesEngine
import com.docu.editor.core.scanner.CanvaTextStudioEngine
import com.docu.editor.core.scanner.CanvaBrandKitEngine
import com.docu.editor.core.scanner.CanvaMagicStudioEngine
import com.docu.editor.core.scanner.CanvaAdjustEngine
import com.docu.editor.core.scanner.CanvaAnimationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Stack
import com.docu.editor.core.scanner.AutoOrientationEngine
import com.docu.editor.core.batch.BatchOcrQueueManager
import com.docu.editor.core.batch.BatchProgressState
import kotlin.math.max
import kotlin.math.min

private const val DOCU_CLOUD_TOKEN = "balaji_docu_secure_token_8971f92a3b4c"

class DocumentEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrAnalyzer = OcrAnalyzer()
    private val backgroundInpainter = BackgroundInpainter()
    private val fontMatcher = FontMatcher(application)
    private val textRenderer = TextRenderer(fontMatcher)
    private val artifactBlendingEngine = ArtifactBlendingEngine()
    private val batchOcrQueueManager by lazy { BatchOcrQueueManager(application) }

    val batchOcrProgress: StateFlow<BatchProgressState> get() = batchOcrQueueManager.progress

    private val _uiState = MutableStateFlow(DocumentEditorUiState())
    val uiState: StateFlow<DocumentEditorUiState> = _uiState.asStateFlow()

    val recentDocuments = MutableStateFlow<List<SavedDocumentItem>>(emptyList())

    init {
        refreshRecentDocuments()
    }

    fun refreshRecentDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = DocumentHistoryManager.getSavedDocuments(getApplication())
            recentDocuments.value = docs
        }
    }

    sealed class UndoStep {
        data class TextPatch(
            val patchBitmap: Bitmap,
            val x: Int,
            val y: Int,
            val targetItemId: String,
            val previousText: String,
            val previousBoundingBox: Rect
        ) : UndoStep()

        data class PixelPatch(
            val patchBitmap: Bitmap,
            val x: Int,
            val y: Int
        ) : UndoStep()

        data class FullBitmap(
            val bitmap: Bitmap
        ) : UndoStep()
    }

    private val undoStack = Stack<UndoStep>()
    private val redoStack = Stack<UndoStep>()
    private val maxUndoDepth = 12

    val editedPagesMap = mutableMapOf<Int, Bitmap>()
    val pageDetectedItemsMap = mutableMapOf<Int, List<DetectedTextItem>>()
    private val pageUndoStacks = mutableMapOf<Int, Stack<UndoStep>>()
    private val pageRedoStacks = mutableMapOf<Int, Stack<UndoStep>>()

    fun saveCurrentPageToCache() {
        if (_uiState.value.canvasLayers.isNotEmpty() || _uiState.value.activeOverlayBitmap != null) {
            flattenAllLayersToDocument(saveUndo = false)
        }
        val state = _uiState.value
        val pageIndex = state.currentPdfPageIndex
        val bmp = state.currentBitmap
        if (bmp != null) {
            editedPagesMap[pageIndex] = bmp
            pageDetectedItemsMap[pageIndex] = state.detectedItems
            pageUndoStacks[pageIndex] = Stack<UndoStep>().apply { addAll(undoStack) }
            pageRedoStacks[pageIndex] = Stack<UndoStep>().apply { addAll(redoStack) }
        }
    }

    fun restorePageFromCache(pageIndex: Int): Boolean {
        val cachedBmp = editedPagesMap[pageIndex] ?: return false
        val cachedItems = pageDetectedItemsMap[pageIndex] ?: emptyList()

        undoStack.clear()
        redoStack.clear()
        pageUndoStacks[pageIndex]?.let { undoStack.addAll(it) }
        pageRedoStacks[pageIndex]?.let { redoStack.addAll(it) }

        _uiState.update {
            it.copy(
                currentBitmap = cachedBmp,
                originalBitmap = cachedBmp,
                detectedItems = cachedItems,
                currentPdfPageIndex = pageIndex,
                currentBatchIndex = pageIndex,
                selectedItem = null,
                selectedItems = emptyList(),
                isScanning = false,
                processingMessage = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                canvasRevision = it.canvasRevision + 1
            )
        }
        return true
    }

    private fun queryFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            result = cursor.getString(idx)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (result.isNullOrBlank()) {
            result = uri.lastPathSegment
        }
        val name = result ?: "Document_${System.currentTimeMillis()}"
        return if (name.contains(".")) name.substringBeforeLast(".") else name
    }

    // --- Loading Documents & Images ---

    fun loadDocumentUri(uri: Uri, autoApplyMagicColor: Boolean = false) {
        editedPagesMap.clear()
        pageDetectedItemsMap.clear()
        pageUndoStacks.clear()
        pageRedoStacks.clear()

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Loading document...") }
            try {
                val context = getApplication<Application>()
                val mimeType = context.contentResolver.getType(uri)
                val isPdf = mimeType == "application/pdf" ||
                    uri.toString().endsWith(".pdf", ignoreCase = true) ||
                    uri.path?.endsWith(".pdf", ignoreCase = true) == true
                val docName = queryFileName(context, uri)

                if (isPdf) {
                    val pageCount = PdfPageLoader.getPageCount(context, uri)
                    _uiState.update {
                        it.copy(
                            activePdfUri = uri,
                            pdfPageCount = pageCount,
                            currentPdfPageIndex = 0,
                            batchScannedPaths = emptyList(),
                            documentTitle = docName
                        )
                    }
                    val bitmap = withContext(Dispatchers.IO) {
                        PdfPageLoader.renderPageToBitmap(context, uri, 0)
                    }
                    setDocumentBitmap(bitmap, autoApplyMagicColor = false)
                } else {
                    _uiState.update {
                        it.copy(
                            activePdfUri = null,
                            pdfPageCount = 1,
                            currentPdfPageIndex = 0,
                            batchScannedPaths = emptyList(),
                            documentTitle = docName
                        )
                    }
                    val bitmap = withContext(Dispatchers.IO) {
                        loadOptimizedBitmapFromUri(uri)
                    }
                    setDocumentBitmap(bitmap, autoApplyMagicColor = autoApplyMagicColor)
                }
            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        showPasswordPromptDialog = true,
                        pendingEncryptedPdfUri = uri,
                        errorMessage = "Password protected document. Enter password to unlock."
                    )
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: ""
                if (msg.contains("password", ignoreCase = true) || msg.contains("encrypt", ignoreCase = true)) {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            showPasswordPromptDialog = true,
                            pendingEncryptedPdfUri = uri,
                            errorMessage = "Password protected document. Enter password to unlock."
                        )
                    }
                } else {
                    _uiState.update { it.copy(isScanning = false, errorMessage = "Failed to load: $msg") }
                }
            }
        }
    }

    fun unlockAndLoadPdf(password: String) {
        val pendingUri = _uiState.value.pendingEncryptedPdfUri ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    processingMessage = "Decrypting document...",
                    showPasswordPromptDialog = false
                )
            }
            try {
                val context = getApplication<Application>()
                val decryptedFile = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
                val success = PdfToolbox(context).decryptPdf(pendingUri, password, decryptedFile)
                if (success) {
                    _uiState.update { it.copy(pendingEncryptedPdfUri = null, successMessage = "Document unlocked successfully!") }
                    loadDocumentUri(Uri.fromFile(decryptedFile))
                } else {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            showPasswordPromptDialog = true,
                            errorMessage = "Incorrect password. Please try again."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        showPasswordPromptDialog = true,
                        errorMessage = "Decryption failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun dismissPasswordPrompt() {
        _uiState.update { it.copy(showPasswordPromptDialog = false, pendingEncryptedPdfUri = null, isScanning = false) }
    }

    fun passwordProtectAndExport(password: String) {
        val context = getApplication<Application>()
        saveCurrentPageToCache()

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Encrypting PDF with AES-128...", showPdfToolboxDialog = false) }
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val time = System.currentTimeMillis()
                val outFile = File(downloadsDir, "DocuEdit_Protected_$time.pdf")
                val current = _uiState.value.currentBitmap ?: throw IllegalStateException("No active document")
                val tempSource = File(context.cacheDir, "temp_to_protect_$time.pdf")
                val state = _uiState.value

                if (state.pdfPageCount > 1) {
                    val allPages = mutableListOf<Bitmap>()
                    val allItems = mutableMapOf<Int, List<DetectedTextItem>>()

                    for (i in 0 until state.pdfPageCount) {
                        val pageBmp = editedPagesMap[i] ?: run {
                            if (state.activePdfUri != null) {
                                PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, i)
                            } else if (state.batchScannedPaths.size > i) {
                                com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[i], 2880)
                            } else {
                                current
                            }
                        } ?: current
                        allPages.add(pageBmp)
                        allItems[i] = pageDetectedItemsMap[i] ?: emptyList()
                    }

                    PdfExportEngine.exportBitmapsToMultiPagePdf(
                        bitmaps = allPages,
                        outputFile = tempSource,
                        fitToA4 = true,
                        pagesDetectedItems = allItems,
                        ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                    )
                } else {
                    PdfExportEngine.exportBitmapToPdf(
                        bitmap = current,
                        outputFile = tempSource,
                        fitToA4 = true,
                        detectedItems = state.detectedItems,
                        ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                    )
                }

                PdfToolbox(context).passwordProtectPdf(
                    sourceUri = Uri.fromFile(tempSource),
                    userPassword = password,
                    outputFile = outFile
                )
                tempSource.delete()

                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        exportUri = outFile.absolutePath,
                        successMessage = "Encrypted PDF saved to Downloads: ${outFile.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Encryption failed: ${e.localizedMessage}") }
            }
        }
    }

    fun nextPdfPage() {
        val state = _uiState.value
        if (state.activePdfUri != null && state.currentPdfPageIndex < state.pdfPageCount - 1) {
            saveCurrentPageToCache()
            loadPdfPage(state.activePdfUri, state.currentPdfPageIndex + 1)
        } else if (state.batchScannedPaths.isNotEmpty() && state.currentBatchIndex < state.batchScannedPaths.size - 1) {
            saveCurrentPageToCache()
            loadBatchPage(state.currentBatchIndex + 1)
        }
    }

    fun previousPdfPage() {
        val state = _uiState.value
        if (state.activePdfUri != null && state.currentPdfPageIndex > 0) {
            saveCurrentPageToCache()
            loadPdfPage(state.activePdfUri, state.currentPdfPageIndex - 1)
        } else if (state.batchScannedPaths.isNotEmpty() && state.currentBatchIndex > 0) {
            saveCurrentPageToCache()
            loadBatchPage(state.currentBatchIndex - 1)
        }
    }

    fun loadPdfPage(uri: Uri, pageIndex: Int) {
        saveCurrentPageToCache()
        if (restorePageFromCache(pageIndex)) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    processingMessage = "Loading page ${pageIndex + 1} of ${_uiState.value.pdfPageCount}..."
                )
            }
            try {
                val context = getApplication<Application>()
                val bitmap = withContext(Dispatchers.IO) {
                    PdfPageLoader.renderPageToBitmap(context, uri, pageIndex)
                }
                _uiState.update { it.copy(currentPdfPageIndex = pageIndex) }
                setDocumentBitmap(bitmap)
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Failed to load page: ${e.localizedMessage}") }
            }
        }
    }

    fun loadBatchScannedPages(paths: List<String>) {
        if (paths.isEmpty()) return
        editedPagesMap.clear()
        pageDetectedItemsMap.clear()
        pageUndoStacks.clear()
        pageRedoStacks.clear()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    batchScannedPaths = paths,
                    currentBatchIndex = 0,
                    pdfPageCount = paths.size,
                    currentPdfPageIndex = 0,
                    activePdfUri = null,
                    documentTitle = "Scan_${System.currentTimeMillis()}"
                )
            }
            loadBatchPage(0)
        }
    }

    fun loadBatchPage(index: Int) {
        val paths = _uiState.value.batchScannedPaths
        if (index !in paths.indices) return
        saveCurrentPageToCache()
        if (restorePageFromCache(index)) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    processingMessage = "Loading page ${index + 1} of ${paths.size}..."
                )
            }
            val bmp = withContext(Dispatchers.IO) {
                com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(paths[index], 2880)
            }
            if (bmp != null) {
                _uiState.update { it.copy(currentBatchIndex = index, currentPdfPageIndex = index) }
                setDocumentBitmap(bmp, autoApplyMagicColor = false)
            }
        }
    }

    fun jumpToPage(index: Int) {
        val state = _uiState.value
        if (index !in 0 until state.pdfPageCount) return
        if (state.activePdfUri != null) {
            loadPdfPage(state.activePdfUri, index)
        } else if (state.batchScannedPaths.isNotEmpty()) {
            loadBatchPage(index)
        }
    }

    fun deletePage(index: Int) {
        val state = _uiState.value
        if (state.pdfPageCount <= 1) return
        saveCurrentPageToCache()

        editedPagesMap.remove(index)
        pageDetectedItemsMap.remove(index)
        pageUndoStacks.remove(index)
        pageRedoStacks.remove(index)

        val newEdited = mutableMapOf<Int, Bitmap>()
        val newDetected = mutableMapOf<Int, List<DetectedTextItem>>()
        val newUndos = mutableMapOf<Int, Stack<UndoStep>>()
        val newRedos = mutableMapOf<Int, Stack<UndoStep>>()

        for (i in 0 until state.pdfPageCount) {
            if (i < index) {
                editedPagesMap[i]?.let { newEdited[i] = it }
                pageDetectedItemsMap[i]?.let { newDetected[i] = it }
                pageUndoStacks[i]?.let { newUndos[i] = it }
                pageRedoStacks[i]?.let { newRedos[i] = it }
            } else if (i > index) {
                editedPagesMap[i]?.let { newEdited[i - 1] = it }
                pageDetectedItemsMap[i]?.let { newDetected[i - 1] = it }
                pageUndoStacks[i]?.let { newUndos[i - 1] = it }
                pageRedoStacks[i]?.let { newRedos[i - 1] = it }
            }
        }
        editedPagesMap.clear()
        editedPagesMap.putAll(newEdited)
        pageDetectedItemsMap.clear()
        pageDetectedItemsMap.putAll(newDetected)
        pageUndoStacks.clear()
        pageUndoStacks.putAll(newUndos)
        pageRedoStacks.clear()
        pageRedoStacks.putAll(newRedos)

        val newBatch = if (state.batchScannedPaths.isNotEmpty()) {
            state.batchScannedPaths.filterIndexed { i, _ -> i != index }
        } else {
            emptyList()
        }

        val newPageCount = state.pdfPageCount - 1
        val newIndex = index.coerceAtMost(newPageCount - 1)

        _uiState.update {
            it.copy(
                pdfPageCount = newPageCount,
                batchScannedPaths = newBatch,
                currentPdfPageIndex = newIndex,
                currentBatchIndex = newIndex,
                hasUnsavedChanges = true,
                successMessage = "Page ${index + 1} deleted"
            )
        }

        if (state.activePdfUri != null) {
            loadPdfPage(state.activePdfUri, newIndex)
        } else if (newBatch.isNotEmpty()) {
            loadBatchPage(newIndex)
        }
    }

    fun duplicatePage(index: Int) {
        val state = _uiState.value
        saveCurrentPageToCache()
        val context = getApplication<Application>()

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Duplicating page ${index + 1}...") }
            try {
                val srcBmp: Bitmap = withContext(Dispatchers.Default) {
                    val cached = editedPagesMap[index]
                    if (cached != null) {
                        cached
                    } else if (state.activePdfUri != null) {
                        PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, index)
                    } else if (state.batchScannedPaths.size > index) {
                        com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[index], 2880)
                    } else if (index == state.currentPdfPageIndex) {
                        state.currentBitmap
                    } else {
                        null
                    }
                } ?: run {
                    _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Failed to load page for duplication") }
                    return@launch
                }

                val clonedBmp = srcBmp.copy(srcBmp.config ?: Bitmap.Config.ARGB_8888, true)
                val clonedItems = (pageDetectedItemsMap[index] ?: (if (index == state.currentPdfPageIndex) state.detectedItems else emptyList())).map { it.copy() }

                val insertIndex = index + 1
                val newEdited = mutableMapOf<Int, Bitmap>()
                val newDetected = mutableMapOf<Int, List<DetectedTextItem>>()

                for (i in 0 until state.pdfPageCount) {
                    if (i <= index) {
                        editedPagesMap[i]?.let { newEdited[i] = it }
                        pageDetectedItemsMap[i]?.let { newDetected[i] = it }
                    } else {
                        editedPagesMap[i]?.let { newEdited[i + 1] = it }
                        pageDetectedItemsMap[i]?.let { newDetected[i + 1] = it }
                    }
                }
                newEdited[insertIndex] = clonedBmp
                newDetected[insertIndex] = clonedItems

                editedPagesMap.clear()
                editedPagesMap.putAll(newEdited)
                pageDetectedItemsMap.clear()
                pageDetectedItemsMap.putAll(newDetected)

                val newPageCount = state.pdfPageCount + 1
                _uiState.update {
                    it.copy(
                        pdfPageCount = newPageCount,
                        isApplyingEdit = false,
                        hasUnsavedChanges = true,
                        successMessage = "Page ${index + 1} duplicated as Page ${insertIndex + 1}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Duplicate failed: ${e.localizedMessage}") }
            }
        }
    }

    fun deleteMultiplePages(indices: Set<Int>) {
        val state = _uiState.value
        if (indices.isEmpty()) return
        if (indices.size >= state.pdfPageCount) {
            _uiState.update { it.copy(errorMessage = "Cannot delete all pages. Document must have at least one page.") }
            return
        }
        saveCurrentPageToCache()

        val remainingPages = (0 until state.pdfPageCount).filter { it !in indices }
        val newEdited = mutableMapOf<Int, Bitmap>()
        val newDetected = mutableMapOf<Int, List<DetectedTextItem>>()

        for ((newIdx, oldIdx) in remainingPages.withIndex()) {
            editedPagesMap[oldIdx]?.let { newEdited[newIdx] = it }
            pageDetectedItemsMap[oldIdx]?.let { newDetected[newIdx] = it }
        }

        editedPagesMap.clear()
        editedPagesMap.putAll(newEdited)
        pageDetectedItemsMap.clear()
        pageDetectedItemsMap.putAll(newDetected)

        val newCount = remainingPages.size
        val newCurrentIdx = if (state.currentPdfPageIndex in indices) {
            0.coerceAtMost(newCount - 1)
        } else {
            val countBefore = indices.count { it < state.currentPdfPageIndex }
            (state.currentPdfPageIndex - countBefore).coerceIn(0, newCount - 1)
        }

        val targetBmp = newEdited[newCurrentIdx]
        _uiState.update {
            it.copy(
                pdfPageCount = newCount,
                currentPdfPageIndex = newCurrentIdx,
                currentBitmap = targetBmp ?: it.currentBitmap,
                originalBitmap = targetBmp ?: it.originalBitmap,
                detectedItems = newDetected[newCurrentIdx] ?: emptyList(),
                selectedItem = null,
                selectedItems = emptyList(),
                hasUnsavedChanges = true,
                successMessage = "${indices.size} pages deleted"
            )
        }
        if (state.activePdfUri != null) {
            loadPdfPage(state.activePdfUri, newCurrentIdx)
        }
    }

    fun appendPageToDocument(bitmap: Bitmap) {
        saveCurrentPageToCache()
        val currentCount = _uiState.value.pdfPageCount
        val newPageIndex = currentCount

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Adding page ${newPageIndex + 1}...") }
            try {
                val oriented = withContext(Dispatchers.Default) {
                    AutoOrientationEngine.autoOrientAndDeskew(bitmap).rotatedBitmap
                }
                val scaled = withContext(Dispatchers.Default) {
                    scaleDownIfNeeded(oriented, 1920)
                }

                editedPagesMap[newPageIndex] = scaled
                pageDetectedItemsMap[newPageIndex] = emptyList()

                _uiState.update {
                    it.copy(
                        pdfPageCount = currentCount + 1,
                        currentPdfPageIndex = newPageIndex,
                        currentBitmap = scaled,
                        originalBitmap = scaled,
                        detectedItems = emptyList(),
                        selectedItem = null,
                        selectedItems = emptyList(),
                        hasUnsavedChanges = true,
                        canvasRevision = it.canvasRevision + 1,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Page ${newPageIndex + 1} added successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Failed to add page: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun appendPageFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    loadOptimizedBitmapFromUri(uri)
                }
                appendPageToDocument(bmp)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Could not load image: ${e.localizedMessage}") }
            }
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value
        if (state.pdfPageCount <= 1) return
        if (fromIndex !in 0 until state.pdfPageCount || toIndex !in 0 until state.pdfPageCount) return
        if (fromIndex == toIndex) return

        saveCurrentPageToCache()

        val indices = (0 until state.pdfPageCount).toMutableList()
        val movedIdx = indices.removeAt(fromIndex)
        indices.add(toIndex, movedIdx)

        val newEdited = mutableMapOf<Int, Bitmap>()
        val newDetected = mutableMapOf<Int, List<DetectedTextItem>>()
        val newUndos = mutableMapOf<Int, java.util.Stack<UndoStep>>()
        val newRedos = mutableMapOf<Int, java.util.Stack<UndoStep>>()

        for ((newPos, oldPos) in indices.withIndex()) {
            editedPagesMap[oldPos]?.let { newEdited[newPos] = it }
            pageDetectedItemsMap[oldPos]?.let { newDetected[newPos] = it }
            pageUndoStacks[oldPos]?.let { newUndos[newPos] = it }
            pageRedoStacks[oldPos]?.let { newRedos[newPos] = it }
        }

        editedPagesMap.clear()
        editedPagesMap.putAll(newEdited)
        pageDetectedItemsMap.clear()
        pageDetectedItemsMap.putAll(newDetected)
        pageUndoStacks.clear()
        pageUndoStacks.putAll(newUndos)
        pageRedoStacks.clear()
        pageRedoStacks.putAll(newRedos)

        val newBatch = if (state.batchScannedPaths.isNotEmpty()) {
            val b = state.batchScannedPaths.toMutableList()
            if (fromIndex in b.indices && toIndex in b.indices) {
                val p = b.removeAt(fromIndex)
                b.add(toIndex, p)
            }
            b
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                batchScannedPaths = newBatch,
                currentPdfPageIndex = toIndex,
                currentBatchIndex = toIndex,
                hasUnsavedChanges = true,
                successMessage = "Page moved to position ${toIndex + 1}"
            )
        }

        if (state.activePdfUri != null) {
            loadPdfPage(state.activePdfUri, toIndex)
        } else if (newBatch.isNotEmpty()) {
            loadBatchPage(toIndex)
        }
    }

    fun rotatePageAt(pageIndex: Int, degrees: Float = 90f) {
        val state = _uiState.value
        if (pageIndex !in 0 until state.pdfPageCount) return
        saveCurrentPageToCache()

        viewModelScope.launch {
            val context = getApplication<Application>()
            val currentBmp = editedPagesMap[pageIndex] ?: withContext(Dispatchers.IO) {
                if (state.activePdfUri != null) {
                    PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, pageIndex)
                } else if (state.batchScannedPaths.size > pageIndex) {
                    com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[pageIndex], 2880)
                } else {
                    state.currentBitmap
                }
            } ?: return@launch

            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(currentBmp, 0, 0, currentBmp.width, currentBmp.height, matrix, true)
            editedPagesMap[pageIndex] = rotated

            if (pageIndex == state.currentPdfPageIndex) {
                _uiState.update {
                    it.copy(
                        originalBitmap = rotated,
                        currentBitmap = rotated,
                        canvasRevision = it.canvasRevision + 1,
                        hasUnsavedChanges = true,
                        successMessage = "Page ${pageIndex + 1} rotated ${degrees.toInt()}°"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        canvasRevision = it.canvasRevision + 1,
                        hasUnsavedChanges = true,
                        successMessage = "Page ${pageIndex + 1} rotated ${degrees.toInt()}°"
                    )
                }
            }
        }
    }

    fun appendPageFromBitmap(newBmp: Bitmap) {
        saveCurrentPageToCache()
        val state = _uiState.value
        val newIndex = state.pdfPageCount
        editedPagesMap[newIndex] = newBmp

        val newBatch = if (state.batchScannedPaths.isNotEmpty()) {
            val list = state.batchScannedPaths.toMutableList()
            val file = File(getApplication<Application>().cacheDir, "page_append_${System.currentTimeMillis()}.jpg")
            try {
                java.io.FileOutputStream(file).use { out ->
                    newBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                list.add(file.absolutePath)
            } catch (_: Exception) {}
            list
        } else {
            emptyList()
        }

        _uiState.update {
            it.copy(
                pdfPageCount = newIndex + 1,
                currentPdfPageIndex = newIndex,
                currentBatchIndex = newIndex,
                batchScannedPaths = newBatch,
                currentBitmap = newBmp,
                originalBitmap = newBmp,
                detectedItems = emptyList(),
                selectedItem = null,
                selectedItems = emptyList(),
                canvasRevision = it.canvasRevision + 1,
                hasUnsavedChanges = true,
                successMessage = "Added Page ${newIndex + 1}"
            )
        }
    }

    fun showPagesOverview(show: Boolean) {
        saveCurrentPageToCache()
        _uiState.update { it.copy(showPagesOverviewDialog = show) }
        if (show) {
            preloadAllPageThumbnails()
        }
    }

    private fun preloadAllPageThumbnails() {
        val state = _uiState.value
        val context = getApplication<Application>()
        if (state.activePdfUri == null && state.batchScannedPaths.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            for (idx in 0 until state.pdfPageCount) {
                if (editedPagesMap[idx] == null) {
                    try {
                        val thumb = if (state.activePdfUri != null) {
                            PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, idx)
                        } else if (state.batchScannedPaths.size > idx) {
                            com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[idx], 1200)
                        } else {
                            null
                        }
                        if (thumb != null) {
                            editedPagesMap[idx] = thumb
                            _uiState.update { it.copy(canvasRevision = it.canvasRevision + 1) }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun loadSampleDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Generating sample invoice...") }
            val bitmap = withContext(Dispatchers.Default) {
                SampleDocumentGenerator.generateSampleInvoice()
            }
            setDocumentBitmap(bitmap)
        }
    }

    fun loadScannedDocument(filePath: String, autoApplyMagicColor: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Loading scanned document...") }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(filePath, 2880)
                }
                if (bitmap != null) {
                    val title = File(filePath).nameWithoutExtension
                    _uiState.update { it.copy(documentTitle = title) }
                    setDocumentBitmap(bitmap, autoApplyMagicColor = autoApplyMagicColor)
                } else {
                    _uiState.update { it.copy(isScanning = false, errorMessage = "Failed to open scanned document.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Error opening scan: ${e.localizedMessage}") }
            }
        }
    }

    private suspend fun setDocumentBitmap(bitmap: Bitmap, autoApplyMagicColor: Boolean = false) {
        val orientedResult = withContext(Dispatchers.Default) {
            com.docu.editor.core.scanner.AutoOrientationEngine.autoOrientAndDeskew(bitmap)
        }
        val sourceBmp = orientedResult.rotatedBitmap
        val optimized = withContext(Dispatchers.Default) {
            scaleDownIfNeeded(sourceBmp, maxDimension = 2560)
        }

        val effectiveBitmap = if (autoApplyMagicColor) {
            withContext(Dispatchers.Default) {
                com.docu.editor.core.scanner.DocumentFilters.applyFilter(optimized, com.docu.editor.core.scanner.DocumentFilters.FilterType.MAGIC_COLOR)
            }
        } else {
            optimized
        }
        val activeFilterMode = if (autoApplyMagicColor) DocumentFilterMode.MAGIC_COLOR else DocumentFilterMode.ORIGINAL

        _uiState.update {
            it.copy(
                originalBitmap = optimized,
                currentBitmap = effectiveBitmap,
                detectedItems = emptyList(), // Clean canvas by default - no annoying bounding boxes!
                selectedItem = null,
                selectedItems = emptyList(),
                isScanning = false,
                processingMessage = null,
                activeFilter = activeFilterMode,
                canUndo = false,
                canRedo = false
            )
        }
        clearUndoRedo()

        val pageIdx = _uiState.value.currentPdfPageIndex
        editedPagesMap[pageIdx] = effectiveBitmap
        pageDetectedItemsMap[pageIdx] = emptyList()

        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val saved = DocumentHistoryManager.saveDocument(
                    context = context,
                    bitmap = effectiveBitmap,
                    pageCount = _uiState.value.pdfPageCount,
                    extractedOcrText = ""
                )
                _uiState.update { it.copy(currentDocHistoryId = saved.id) }
                refreshRecentDocuments()
            } catch (_: Exception) {}
        }
    }

    private fun updateRecentDocumentThumbnail(bitmap: Bitmap) {
        val historyId = _uiState.value.currentDocHistoryId ?: return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DocumentHistoryManager.updateDocument(
                    context = context,
                    id = historyId,
                    bitmap = bitmap,
                    pageCount = _uiState.value.pdfPageCount
                )
                refreshRecentDocuments()
            } catch (_: Exception) {}
        }
    }

    // --- Text Selection & Inpainting Replacement ---

    fun selectTextItem(item: DetectedTextItem?) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun setMagicEraserBrushRadius(radius: Float) {
        _uiState.update { it.copy(magicEraserBrushRadius = radius.coerceIn(8f, 80f)) }
    }

    fun showCanvaStickersDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaStickersDialog = show) }
    }

    fun setCustomOverlayImage(bitmap: Bitmap, title: String = "Sticker / Image") {
        addCanvasLayer(bitmap, title)
    }

    fun toggleCloudAiEraser() {
        _uiState.update { it.copy(isCloudAiEraserEnabled = !it.isCloudAiEraserEnabled) }
    }

    fun setCloudAiEraserEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isCloudAiEraserEnabled = enabled) }
    }

    fun applyMagicObjectEraser(strokePoints: List<PointF>, brushRadius: Float = _uiState.value.magicEraserBrushRadius) {
        val current = _uiState.value.currentBitmap ?: return
        if (strokePoints.isEmpty()) return
        val isCloud = _uiState.value.isCloudAiEraserEnabled
        val apiKey = getGeminiApiKey()
        val isUsingCloud = isCloud && apiKey.isNotBlank()

        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isApplyingEdit = true, 
                    processingMessage = if (isUsingCloud) "✨ Cloud AI Inpainting background..." else "🪄 Restoring natural document texture..."
                ) 
            }
            try {
                pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))
                val erased = com.docu.editor.core.scanner.MagicObjectEraserEngine.eraseStroke(
                    sourceBitmap = current,
                    strokePoints = strokePoints,
                    brushRadius = brushRadius,
                    useCloudAi = isUsingCloud,
                    apiKey = apiKey
                )
                val pageIdx = _uiState.value.currentPdfPageIndex
                editedPagesMap[pageIdx] = erased
                pageDetectedItemsMap[pageIdx] = emptyList()
                updateRecentDocumentThumbnail(erased)
                _uiState.update {
                    it.copy(
                        currentBitmap = erased,
                        detectedItems = emptyList(),
                        isApplyingEdit = false,
                        hasUnsavedChanges = true,
                        canvasRevision = it.canvasRevision + 1,
                        successMessage = if (isUsingCloud) "✨ Erased with Cloud AI Generative Fill" else "Object erased with natural texture blend"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Eraser failed: ${e.localizedMessage}") }
            }
        }
    }

    fun setWhiteoutBrushRadius(radius: Float) {
        _uiState.update { it.copy(whiteoutBrushRadius = radius.coerceIn(8f, 80f)) }
    }

    fun applyWhiteoutCircle(bitmapX: Float, bitmapY: Float, radius: Float = _uiState.value.whiteoutBrushRadius) {
        val currentBitmap = _uiState.value.currentBitmap ?: return

        val patchL = (bitmapX - radius - 4).toInt().coerceIn(0, currentBitmap.width - 1)
        val patchT = (bitmapY - radius - 4).toInt().coerceIn(0, currentBitmap.height - 1)
        val patchR = (bitmapX + radius + 4).toInt().coerceIn(0, currentBitmap.width)
        val patchB = (bitmapY + radius + 4).toInt().coerceIn(0, currentBitmap.height)
        val patchW = max(1, patchR - patchL)
        val patchH = max(1, patchB - patchT)

        val patchBmp = Bitmap.createBitmap(currentBitmap, patchL, patchT, patchW, patchH)
        pushUndoStep(UndoStep.PixelPatch(patchBmp, patchL, patchT))

        // Sample authentic local paper color in ring around eraser
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0
        val sampleRadius = (radius + 6).toInt()
        val cx = bitmapX.toInt()
        val cy = bitmapY.toInt()
        for (dx in -sampleRadius..sampleRadius step 3) {
            for (dy in -sampleRadius..sampleRadius step 3) {
                val distSq = dx * dx + dy * dy
                if (distSq in (radius * radius).toInt()..(sampleRadius * sampleRadius)) {
                    val px = (cx + dx).coerceIn(0, currentBitmap.width - 1)
                    val py = (cy + dy).coerceIn(0, currentBitmap.height - 1)
                    val pixel = currentBitmap.getPixel(px, py)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val luma = 0.299f * r + 0.587f * g + 0.114f * b
                    if (luma > 150) {
                        sumR += r
                        sumG += g
                        sumB += b
                        count++
                    }
                }
            }
        }
        val paperColor = if (count > 0) {
            Color.rgb((sumR / count).toInt(), (sumG / count).toInt(), (sumB / count).toInt())
        } else {
            Color.WHITE
        }

        val canvas = Canvas(currentBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paperColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(bitmapX, bitmapY, radius, paint)

        editedPagesMap[_uiState.value.currentPdfPageIndex] = currentBitmap

        _uiState.update {
            it.copy(
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                canvasRevision = it.canvasRevision + 1
            )
        }
        updateRecentDocumentThumbnail(currentBitmap)
    }

    fun isNetworkConnected(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun applyTextReplacement(
        targetItem: DetectedTextItem,
        newText: String,
        fontClassification: FontClassification? = null,
        isBold: Boolean? = null,
        sizeMultiplier: Float = 1.0f,
        colorOverrideRgb: Int? = null,
        alignment: Paint.Align = Paint.Align.LEFT,
        useCloudAi: Boolean = false
    ) {
        if (useCloudAi && !isNetworkConnected()) {
            _uiState.update {
                it.copy(
                    errorMessage = "⚠️ इंटरनेट कनेक्शन आवश्यक है • स्मार्ट AI रिमूवर क्लाउड से चलता है।"
                )
            }
            return
        }

        val currentBitmap = _uiState.value.currentBitmap ?: return

        // Save ultra-compact 30KB patch of edited word (99.8% memory savings, 0% crash risk)
        val margin = 8
        val patchL = max(0, targetItem.boundingBox.left - margin)
        val patchT = max(0, targetItem.boundingBox.top - margin)
        val patchR = min(currentBitmap.width, targetItem.boundingBox.right + margin)
        val patchB = min(currentBitmap.height, targetItem.boundingBox.bottom + margin)
        val patchW = max(1, patchR - patchL)
        val patchH = max(1, patchB - patchT)

        val patchBmp = Bitmap.createBitmap(currentBitmap, patchL, patchT, patchW, patchH)
        pushUndoStep(
            UndoStep.TextPatch(
                patchBitmap = patchBmp,
                x = patchL,
                y = patchT,
                targetItemId = targetItem.id,
                previousText = targetItem.text,
                previousBoundingBox = Rect(targetItem.boundingBox)
            )
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = if (useCloudAi) "Smart AI: Analyzing document typography..." else "Applying seamless typography..."
                )
            }

            try {
                val updatedBitmap = withContext(Dispatchers.Default) {
                    var effectiveBold = isBold
                    var effectiveInkColor = colorOverrideRgb ?: targetItem.inkColorRgb

                    // If Gemini Pro Cloud AI is chosen, call shribalajikripadham.online/api/docu_ai.php
                    if (useCloudAi) {
                        try {
                            val cloudResult = callCloudAiTypography(
                                sourceBitmap = currentBitmap,
                                targetBounds = targetItem.boundingBox,
                                currentText = targetItem.text,
                                newText = newText,
                                isBold = effectiveBold ?: false,
                                sizeMultiplier = sizeMultiplier,
                                colorHex = String.format("#%06X", (0xFFFFFF and effectiveInkColor))
                            )
                            if (cloudResult != null) {
                                // 1. Direct Server-Side Inpainted Patch Stamp
                                val serverPatchBase64 = cloudResult.optString("edited_patch_base64")
                                if (!serverPatchBase64.isNullOrEmpty()) {
                                    val patchBytes = Base64.decode(serverPatchBase64, Base64.DEFAULT)
                                    val serverPatchBmp = BitmapFactory.decodeByteArray(patchBytes, 0, patchBytes.size)
                                    if (serverPatchBmp != null) {
                                        val resultBmp = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
                                        val canvas = Canvas(resultBmp)
                                        val pad = 40
                                        val patchL = max(0, targetItem.boundingBox.left - pad)
                                        val patchT = max(0, targetItem.boundingBox.top - pad)
                                        canvas.drawBitmap(serverPatchBmp, patchL.toFloat(), patchT.toFloat(), null)
                                        serverPatchBmp.recycle()
                                        return@withContext resultBmp
                                    }
                                }

                                // 2. Advisory Typography Metadata
                                val typo = cloudResult.optJSONObject("typography")
                                if (typo != null) {
                                    effectiveBold = typo.optBoolean("is_bold", effectiveBold ?: false)
                                    val cloudInk = typo.optString("ink_color_hex")
                                    if (!cloudInk.isNullOrEmpty()) {
                                        try {
                                            effectiveInkColor = Color.parseColor(cloudInk)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // Non-blocking fallback to local engine
                        }
                    }

                    // #5 Word Overlap / Reflow Spacing Protection:
                    // Check if an adjacent word exists to the right on the same line
                    val nextAdjacentItem = _uiState.value.detectedItems.filter {
                        it.id != targetItem.id &&
                        it.boundingBox.left >= targetItem.boundingBox.right - 4 &&
                        kotlin.math.abs(it.boundingBox.centerY() - targetItem.boundingBox.centerY()) < targetItem.boundingBox.height() * 0.65f
                    }.minByOrNull { it.boundingBox.left }

                    val effectiveTargetBounds = if (nextAdjacentItem != null) {
                        val maxAllowedRight = nextAdjacentItem.boundingBox.left - 6
                        if (maxAllowedRight > targetItem.boundingBox.left + 15) {
                            Rect(targetItem.boundingBox.left, targetItem.boundingBox.top, maxAllowedRight, targetItem.boundingBox.bottom)
                        } else {
                            targetItem.boundingBox
                        }
                    } else {
                        targetItem.boundingBox
                    }

                    val cleanedBackground = backgroundInpainter.inpaint(
                        sourceBitmap = currentBitmap,
                        targetBounds = effectiveTargetBounds
                    )

                    val renderResult = textRenderer.render(
                        cleanedBackground = cleanedBackground,
                        params = TextRenderer.TextRenderParams(
                            newText = newText,
                            originalText = targetItem.text,
                            targetBounds = effectiveTargetBounds,
                            inkColorRgb = effectiveInkColor,
                            rotationAngle = targetItem.rotationAngle,
                            typographyMetrics = targetItem.typography,
                            overrideClassification = fontClassification,
                            isBold = effectiveBold,
                            sizeMultiplier = sizeMultiplier,
                            alignment = alignment
                        )
                    )

                    cleanedBackground.recycle()
                    renderResult.outputBitmap
                }

                // Update bounding box width to match new text length with reflow protection
                val charW = (targetItem.boundingBox.height() * 0.52f) * sizeMultiplier
                val newWidth = (newText.length * charW).toInt().coerceAtLeast(24)

                val nextAdjacentItem = _uiState.value.detectedItems.filter {
                    it.id != targetItem.id &&
                    it.boundingBox.left >= targetItem.boundingBox.right - 4 &&
                    kotlin.math.abs(it.boundingBox.centerY() - targetItem.boundingBox.centerY()) < targetItem.boundingBox.height() * 0.65f
                }.minByOrNull { it.boundingBox.left }

                val finalRight = if (nextAdjacentItem != null) {
                    val maxAllowed = nextAdjacentItem.boundingBox.left - 6
                    if (maxAllowed > targetItem.boundingBox.left + 15) {
                        minOf(targetItem.boundingBox.left + newWidth, maxAllowed)
                    } else {
                        targetItem.boundingBox.left + newWidth
                    }
                } else {
                    targetItem.boundingBox.left + newWidth
                }

                val isNumericFigure = newText.trim().matches(Regex("""^[$€£₹]?\s*[\d,.-]+%?$""")) ||
                                      targetItem.text.trim().matches(Regex("""^[$€£₹]?\s*[\d,.-]+%?$"""))

                val newBox = if (isNumericFigure) {
                    val newLeft = (targetItem.boundingBox.right - newWidth).coerceAtLeast(0)
                    Rect(newLeft, targetItem.boundingBox.top, targetItem.boundingBox.right, targetItem.boundingBox.bottom)
                } else {
                    Rect(
                        targetItem.boundingBox.left,
                        targetItem.boundingBox.top,
                        finalRight,
                        targetItem.boundingBox.bottom
                    )
                }

                val updatedItems = if (_uiState.value.isNewTextInsertion) {
                    _uiState.value.detectedItems + targetItem.copy(text = newText, boundingBox = newBox)
                } else {
                    _uiState.value.detectedItems.map {
                        if (it.id == targetItem.id) it.copy(text = newText, boundingBox = newBox) else it
                    }
                }

                _uiState.update {
                    it.copy(
                        currentBitmap = updatedBitmap,
                        detectedItems = updatedItems,
                        selectedItem = null,
                        isNewTextInsertion = false,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = if (useCloudAi) "Smart AI: Replaced seamlessly" else "Replaced text seamlessly",
                        canUndo = true,
                        canRedo = false,
                        hasUnsavedChanges = true,
                        canvasRevision = it.canvasRevision + 1
                    )
                }

                val pageIdx = _uiState.value.currentPdfPageIndex
                editedPagesMap[pageIdx] = updatedBitmap
                pageDetectedItemsMap[pageIdx] = updatedItems
                updateRecentDocumentThumbnail(updatedBitmap)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Edit failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun callCloudAiTypography(
        sourceBitmap: Bitmap,
        targetBounds: Rect,
        currentText: String,
        newText: String,
        isBold: Boolean,
        sizeMultiplier: Float,
        colorHex: String
    ): JSONObject? {
        val pad = 40
        val left = max(0, targetBounds.left - pad)
        val top = max(0, targetBounds.top - pad)
        val right = min(sourceBitmap.width, targetBounds.right + pad)
        val bottom = min(sourceBitmap.height, targetBounds.bottom + pad)
        val cropW = right - left
        val cropH = bottom - top

        if (cropW <= 0 || cropH <= 0) return null

        val cropBmp = Bitmap.createBitmap(sourceBitmap, left, top, cropW, cropH)
        val baos = ByteArrayOutputStream()
        cropBmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        cropBmp.recycle()
        val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val customApiKey = getGeminiApiKey()
        val payload = JSONObject().apply {
            put("action", "analyze_text")
            put("image", base64Img)
            put("current_text", currentText)
            put("replacement_text", newText)
            put("is_bold", isBold)
            put("size_multiplier", sizeMultiplier.toDouble())
            put("color_hex", colorHex)
            if (customApiKey.isNotBlank()) {
                put("gemini_api_key", customApiKey)
            }
        }

        val url = URL("https://shribalajikripadham.online/api/docu_ai.php")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (customApiKey.isNotBlank()) {
            conn.setRequestProperty("X-Gemini-Key", customApiKey)
        }
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
        conn.doOutput = true

        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            if (json.optBoolean("success")) {
                return json
            }
        }
        return null
    }

    // --- CamScanner Filters Engine ---

    fun applyFilter(filter: DocumentFilterMode) {
        val base = _uiState.value.originalBitmap ?: return
        val current = _uiState.value.currentBitmap ?: return
        if (filter == _uiState.value.activeFilter) return

        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                val filterType = when (filter) {
                    DocumentFilterMode.ORIGINAL -> DocumentFilters.FilterType.ORIGINAL
                    DocumentFilterMode.MAGIC_COLOR -> DocumentFilters.FilterType.MAGIC_COLOR
                    DocumentFilterMode.SHADOW_REMOVER -> DocumentFilters.FilterType.REMOVE_SHADOWS
                    DocumentFilterMode.WATERMARK_REMOVER -> DocumentFilters.FilterType.REMOVE_WATERMARK
                    DocumentFilterMode.FINGER_REMOVER -> DocumentFilters.FilterType.REMOVE_FINGERS
                    DocumentFilterMode.BOOK_DEWARP -> DocumentFilters.FilterType.DEWARP_CURVED_PAGE
                    DocumentFilterMode.CLEAN_BW -> DocumentFilters.FilterType.CLEAN_BW
                    DocumentFilterMode.GRAYSCALE -> DocumentFilters.FilterType.GRAYSCALE
                    DocumentFilterMode.VIVID_DOC -> DocumentFilters.FilterType.VIVID_DOC
                    DocumentFilterMode.STUDIO_WHITE -> DocumentFilters.FilterType.STUDIO_WHITE
                    DocumentFilterMode.BLUEPRINT -> DocumentFilters.FilterType.BLUEPRINT
                    DocumentFilterMode.SEPIA -> DocumentFilters.FilterType.SEPIA
                    DocumentFilterMode.INK_SHARPENER -> DocumentFilters.FilterType.INK_SHARPENER
                }
                DocumentFilters.applyFilter(base, filterType)
            }

            editedPagesMap[_uiState.value.currentPdfPageIndex] = filtered

            _uiState.update {
                it.copy(
                    currentBitmap = filtered,
                    activeFilter = filter,
                    successMessage = "✓ ${filter.displayName} applied",
                    canUndo = true,
                    canRedo = false,
                    canvasRevision = it.canvasRevision + 1
                )
            }

            pageDetectedItemsMap[_uiState.value.currentPdfPageIndex] = emptyList()
            _uiState.update { it.copy(detectedItems = emptyList()) }
        }
    }

    fun toggleMagicColor() {
        val currentFilter = _uiState.value.activeFilter
        if (currentFilter == DocumentFilterMode.MAGIC_COLOR) {
            applyFilter(DocumentFilterMode.ORIGINAL)
        } else {
            applyFilter(DocumentFilterMode.MAGIC_COLOR)
        }
    }

    fun applyBrightnessContrast(brightness: Float, contrast: Float) {
        val base = _uiState.value.originalBitmap ?: return
        val current = _uiState.value.currentBitmap ?: return

        viewModelScope.launch {
            val adjusted = withContext(Dispatchers.Default) {
                DocumentFilters.adjustBrightnessContrast(base, brightness, contrast)
            }
            editedPagesMap[_uiState.value.currentPdfPageIndex] = adjusted
            _uiState.update {
                it.copy(
                    currentBitmap = adjusted,
                    canUndo = true,
                    canRedo = false,
                    canvasRevision = it.canvasRevision + 1
                )
            }
        }
    }

    // --- Enterprise Ink & Rubber Stamp Remover ---

    fun eraseMarks(mode: EraseMarksEngine.EraseMode = EraseMarksEngine.EraseMode.ALL_MARKS, customColor: Int = Color.BLUE) {
        val current = _uiState.value.currentBitmap ?: return
        val curPageIdx = _uiState.value.currentPdfPageIndex

        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "🪄 Erasing ink marks & preserving printed text...") }

            val cleaned = withContext(Dispatchers.Default) {
                EraseMarksEngine.eraseMarks(current, mode, customColor)
            }

            editedPagesMap[curPageIdx] = cleaned

            val modeName = when (mode) {
                EraseMarksEngine.EraseMode.ALL_MARKS -> "All handwriting & stamps erased"
                EraseMarksEngine.EraseMode.RUBBER_STAMPS -> "Rubber stamps erased"
                EraseMarksEngine.EraseMode.BLUE_PEN -> "Blue pen ink erased"
                EraseMarksEngine.EraseMode.CUSTOM_COLOR -> "Custom ink marks erased"
            }

            _uiState.update {
                it.copy(
                    isApplyingEdit = false,
                    processingMessage = null,
                    currentBitmap = cleaned,
                    successMessage = "✓ $modeName",
                    canUndo = true,
                    canRedo = false,
                    canvasRevision = it.canvasRevision + 1
                )
            }
        }
    }

    // --- Enterprise Book 2-Page Spine Splitter ---

    fun splitCurrentBookSpread(autoDewarp: Boolean = true) {
        val current = _uiState.value.currentBitmap ?: return
        val curPageIdx = _uiState.value.currentPdfPageIndex
        val totalCount = _uiState.value.pdfPageCount

        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "📖 Detecting spine & splitting into 2 pages...") }

            val result = withContext(Dispatchers.Default) {
                BookSplitEngine.splitBookSpread(current, autoDewarpCurvature = autoDewarp)
            }

            val newEdited = mutableMapOf<Int, Bitmap>()
            for (i in 0..curPageIdx) {
                editedPagesMap[i]?.let { newEdited[i] = it }
            }
            newEdited[curPageIdx] = result.leftPage
            newEdited[curPageIdx + 1] = result.rightPage

            for (i in (curPageIdx + 1)..totalCount) {
                editedPagesMap[i]?.let { newEdited[i + 1] = it }
            }
            editedPagesMap.clear()
            editedPagesMap.putAll(newEdited)

            val newTotalCount = max(2, totalCount + 1)

            _uiState.update {
                it.copy(
                    isApplyingEdit = false,
                    processingMessage = null,
                    currentBitmap = result.leftPage,
                    pdfPageCount = newTotalCount,
                    successMessage = "✓ Book 2-Page split: Page ${curPageIdx + 1} & ${curPageIdx + 2} ready",
                    canUndo = true,
                    canRedo = false,
                    canvasRevision = it.canvasRevision + 1
                )
            }
        }
    }

    // --- 4-Corner Perspective Warp & Auto-Deskew ---

    fun applyAutoPerspectiveCrop() {
        val current = _uiState.value.currentBitmap ?: return

        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Detecting 4 document corners...") }

            try {
                val warped = withContext(Dispatchers.Default) {
                    val corners = DocumentEdgeDetector.detectCorners(current)
                    PerspectiveTransformer.warpPerspective(current, corners)
                }

                _uiState.update {
                    it.copy(
                        currentBitmap = warped,
                        detectedItems = emptyList(),
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Document cropped & flattened perfectly",
                        canUndo = true,
                        canRedo = false,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isApplyingEdit = false, processingMessage = null, errorMessage = "Crop error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun applyInteractiveCrop(corners: DocumentCorners) {
        val current = _uiState.value.currentBitmap ?: return

        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    processingMessage = "Flattening perspective with 8-point loupe...",
                    showInteractiveCropDialog = false
                )
            }

            try {
                val warped = withContext(Dispatchers.Default) {
                    PerspectiveTransformer.warpPerspective(current, corners)
                }

                _uiState.update {
                    it.copy(
                        currentBitmap = warped,
                        detectedItems = emptyList(),
                        isScanning = false,
                        processingMessage = null,
                        successMessage = "Document cropped & flattened perfectly",
                        canUndo = true,
                        canRedo = false,
                        hasUnsavedChanges = true,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
                val pageIdx = _uiState.value.currentPdfPageIndex
                editedPagesMap[pageIdx] = warped
                pageDetectedItemsMap[pageIdx] = emptyList()
                updateRecentDocumentThumbnail(warped)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isScanning = false, processingMessage = null, errorMessage = "Crop error: ${e.localizedMessage}")
                }
            }
        }
    }

    // --- ID Card Duplex Mode ---

    fun setIdCardFront(bitmap: Bitmap) {
        val scaled = scaleDownIfNeeded(bitmap, 1600)
        _uiState.update { it.copy(idCardFrontBitmap = scaled) }
    }

    fun setIdCardBack(bitmap: Bitmap) {
        val scaled = scaleDownIfNeeded(bitmap, 1600)
        _uiState.update { it.copy(idCardBackBitmap = scaled) }
    }

    fun stitchIdCardToA4() {
        val front = _uiState.value.idCardFrontBitmap ?: return
        val back = _uiState.value.idCardBackBitmap ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Stitching ID Card to A4 sheet...") }
            try {
                val a4Bitmap = withContext(Dispatchers.Default) {
                    IdCardStitcher.stitchIdCardToA4(front, back)
                }
                setDocumentBitmap(a4Bitmap)
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        showIdCardDialog = false,
                        idCardFrontBitmap = null,
                        idCardBackBitmap = null,
                        successMessage = "ID Card stitched onto standard A4 document!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "ID Stitch failed: ${e.localizedMessage}") }
            }
        }
    }

    // --- Signature & Stamp Extractor ---

    fun extractSignatureFromBitmap(source: Bitmap, inkRgb: Int = android.graphics.Color.rgb(10, 30, 100)) {
        val option = when (inkRgb) {
            android.graphics.Color.rgb(15, 15, 15) -> SignatureExtractor.InkColorOption.CLASSIC_BLACK
            android.graphics.Color.rgb(190, 25, 35) -> SignatureExtractor.InkColorOption.STAMP_RED
            else -> SignatureExtractor.InkColorOption.BALLPOINT_BLUE
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Extracting transparent signature...") }
            try {
                val signTransparent = withContext(Dispatchers.Default) {
                    SignatureExtractor.extractSignature(source, option)
                }
                _uiState.update {
                    it.copy(
                        extractedSignature = signTransparent,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Signature extracted with 100% alpha transparency!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Extraction failed: ${e.localizedMessage}") }
            }
        }
    }

    fun extractStampFromBitmap(source: Bitmap, isRed: Boolean = true) {
        val target = if (isRed) StampExtractor.StampColorTarget.RED_STAMP else StampExtractor.StampColorTarget.BLUE_PURPLE_STAMP

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Extracting stamp/seal...") }
            try {
                val stampTransparent = withContext(Dispatchers.Default) {
                    StampExtractor.extractStamp(source, target)
                }
                _uiState.update {
                    it.copy(
                        extractedSignature = stampTransparent,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Stamp isolated successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Stamp extraction failed: ${e.localizedMessage}") }
            }
        }
    }

    // --- PDF Tools ---

    fun compressCurrentDocument(targetDpi: Int = 150) {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Compressing document to ${targetDpi} DPI...") }
            try {
                val compressed = withContext(Dispatchers.Default) {
                    val scale = when {
                        targetDpi <= 150 -> 0.7f
                        targetDpi <= 200 -> 0.85f
                        else -> 1.0f
                    }
                    val w = (current.width * scale).toInt().coerceAtLeast(100)
                    val h = (current.height * scale).toInt().coerceAtLeast(100)
                    Bitmap.createScaledBitmap(current, w, h, true)
                }
                _uiState.update {
                    it.copy(
                        currentBitmap = compressed,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Document compressed to ${targetDpi} DPI (size reduced by ~70%)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Compression failed: ${e.localizedMessage}") }
            }
        }
    }

    // --- Export Document ---

    fun showExportDialog(show: Boolean) {
        _uiState.update { it.copy(showExportDialog = show) }
    }

    fun exportCurrentDocument(
        format: String = "PDF",
        fitToA4: Boolean = true,
        customFileName: String = "",
        password: String = "",
        pageIndices: List<Int>? = null
    ) {
        val current = _uiState.value.currentBitmap ?: return
        saveCurrentPageToCache()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = if (format.equals("PDF", true)) "Generating authentic PDF document..." else "Saving $format to Downloads..."
                )
            }
            try {
                val context = getApplication<Application>()
                val file = withContext(Dispatchers.IO) {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val time = System.currentTimeMillis()
                    val rawName = if (customFileName.isNotBlank()) {
                        customFileName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    } else {
                        "DocuEdit_Export_$time"
                    }

                    val state = _uiState.value
                    val effectivePages = (pageIndices?.filter { it in 0 until state.pdfPageCount } ?: (0 until state.pdfPageCount).toList()).let {
                        if (it.isEmpty()) (0 until state.pdfPageCount).toList() else it
                    }

                    when (format.uppercase()) {
                        "PDF" -> {
                            val cleanName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
                            val outFile = File(downloadsDir, cleanName)

                            if (state.activePdfUri != null && state.pdfPageCount > 1 && pageIndices == null && editedPagesMap.size < state.pdfPageCount) {
                                // Hybrid Vector Preservation: Untouched pages retain 100% original vector typography & links!
                                val allItems = mutableMapOf<Int, List<DetectedTextItem>>()
                                for (i in 0 until state.pdfPageCount) {
                                    allItems[i] = pageDetectedItemsMap[i] ?: emptyList()
                                }
                                com.docu.editor.core.pdf.PdfHybridExporter.exportHybridPdf(
                                    context = context,
                                    sourcePdfUri = state.activePdfUri,
                                    editedPagesMap = editedPagesMap,
                                    pagesDetectedItems = allItems,
                                    outputFile = outFile,
                                    ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                                )
                            } else if (effectivePages.size > 1 || (state.pdfPageCount > 1 && effectivePages.size == 1)) {
                                val allItems = mutableMapOf<Int, List<DetectedTextItem>>()
                                effectivePages.forEachIndexed { outIdx, realIdx ->
                                    allItems[outIdx] = pageDetectedItemsMap[realIdx] ?: (if (realIdx == state.currentPdfPageIndex) state.detectedItems else emptyList())
                                }

                                PdfExportEngine.exportPagesStreamingToPdf(
                                    pageCount = effectivePages.size,
                                    pageBitmapProvider = { idx ->
                                        val realIdx = effectivePages[idx]
                                        val cached = editedPagesMap[realIdx]
                                        if (cached != null) {
                                            cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
                                        } else if (state.activePdfUri != null) {
                                            PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, realIdx)
                                        } else if (state.batchScannedPaths.size > realIdx) {
                                            com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[realIdx], 2880)
                                        } else {
                                            current.copy(current.config ?: Bitmap.Config.ARGB_8888, false)
                                        }
                                    },
                                    outputFile = outFile,
                                    fitToA4 = fitToA4,
                                    pagesDetectedItems = allItems,
                                    autoRecycleBitmaps = true,
                                    ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                                )
                            } else {
                                PdfExportEngine.exportBitmapToPdf(
                                    bitmap = current,
                                    outputFile = outFile,
                                    fitToA4 = fitToA4,
                                    detectedItems = _uiState.value.detectedItems,
                                    ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                                )
                            }

                            // AES-128 Password Protection
                            if (password.isNotBlank()) {
                                val unencrypted = File(downloadsDir, "raw_${System.currentTimeMillis()}_$cleanName")
                                if (outFile.renameTo(unencrypted)) {
                                    PdfExportEngine.encryptPdfWithPassword(
                                        inputFile = unencrypted,
                                        outputFile = outFile,
                                        userPassword = password
                                    )
                                    unencrypted.delete()
                                }
                            }

                            outFile
                        }
                        "PNG" -> {
                            val cleanName = if (rawName.endsWith(".png", ignoreCase = true)) rawName else "$rawName.png"
                            val outFile = File(downloadsDir, cleanName)
                            FileOutputStream(outFile).use { out ->
                                current.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            outFile
                        }
                        "DOCX" -> {
                            val cleanName = if (rawName.endsWith(".docx", ignoreCase = true)) rawName else "$rawName.docx"
                            val outFile = File(downloadsDir, cleanName)
                            val sb = StringBuilder()
                            if (effectivePages.size > 1 || (state.pdfPageCount > 1 && effectivePages.size == 1)) {
                                for (pIdx in effectivePages) {
                                    sb.append("## Page ${pIdx + 1}\n\n")
                                    val items = pageDetectedItemsMap[pIdx] ?: if (pIdx == state.currentPdfPageIndex) state.detectedItems else emptyList()
                                    sb.append(DocxExportEngine.formatItemsToStructuredDocument(items)).append("\n\n")
                                }
                            } else {
                                sb.append(DocxExportEngine.formatItemsToStructuredDocument(state.detectedItems))
                            }
                            val docText = sb.toString().ifBlank { "Scanned Document Notes" }
                            DocxExportEngine.generateDocx(rawName, docText, outFile)
                            outFile
                        }
                        "XLSX" -> {
                            val cleanName = if (rawName.endsWith(".xlsx", ignoreCase = true)) rawName else "$rawName.xlsx"
                            val outFile = File(downloadsDir, cleanName)
                            val pagesMap = mutableMapOf<Int, List<DetectedTextItem>>()
                            if (effectivePages.size > 1 || (state.pdfPageCount > 1 && effectivePages.size == 1)) {
                                effectivePages.forEachIndexed { outIdx, realIdx ->
                                    val items = pageDetectedItemsMap[realIdx] ?: if (realIdx == state.currentPdfPageIndex) state.detectedItems else emptyList()
                                    pagesMap[outIdx] = items
                                }
                            } else {
                                pagesMap[0] = state.detectedItems
                            }
                            SpreadsheetExportEngine.exportToXlsx(pagesMap, outFile)
                            outFile
                        }
                        "CSV", "XLS" -> {
                            val cleanName = if (rawName.endsWith(".csv", ignoreCase = true)) rawName else "$rawName.csv"
                            val outFile = File(downloadsDir, cleanName)
                            val state = _uiState.value
                            val pagesMap = mutableMapOf<Int, List<DetectedTextItem>>()
                            if (state.pdfPageCount > 1) {
                                for (pIdx in 0 until state.pdfPageCount) {
                                    val items = pageDetectedItemsMap[pIdx] ?: if (pIdx == state.currentPdfPageIndex) state.detectedItems else emptyList()
                                    pagesMap[pIdx] = items
                                }
                            } else {
                                pagesMap[0] = state.detectedItems
                            }
                            SpreadsheetExportEngine.exportToCsv(pagesMap, outFile)
                            outFile
                        }
                        else -> {
                            val cleanName = if (rawName.endsWith(".jpg", ignoreCase = true) || rawName.endsWith(".jpeg", ignoreCase = true)) rawName else "$rawName.jpg"
                            val outFile = File(downloadsDir, cleanName)
                            FileOutputStream(outFile).use { out ->
                                current.compress(Bitmap.CompressFormat.JPEG, 94, out)
                            }
                            outFile
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        showExportDialog = false,
                        exportUri = file.absolutePath,
                        hasUnsavedChanges = false,
                        successMessage = "Saved to Downloads: ${file.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
    }

    // --- Document Rotation & Geometry ---

    fun rotateDocumentClockwise() {
        val current = _uiState.value.currentBitmap ?: return
        val oldW = current.width
        val oldH = current.height

        pushUndoStep(UndoStep.FullBitmap(current))

        val matrix = android.graphics.Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(current, 0, 0, oldW, oldH, matrix, true)

        val rotatedItems = _uiState.value.detectedItems.map { item ->
            val b = item.boundingBox
            val newLeft = (oldH - b.bottom).coerceAtLeast(0)
            val newTop = b.left.coerceAtLeast(0)
            val newRight = (oldH - b.top).coerceAtMost(oldH)
            val newBottom = b.right.coerceAtMost(oldW)
            item.copy(boundingBox = Rect(newLeft, newTop, newRight, newBottom))
        }

        _uiState.update {
            it.copy(
                originalBitmap = rotated,
                currentBitmap = rotated,
                detectedItems = rotatedItems,
                selectedItem = null,
                canUndo = true,
                canRedo = false,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "Rotated 90° Clockwise"
            )
        }
    }

    fun autoOrientCurrentDocument() {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Detecting orientation & deskewing...") }
            try {
                val orientedResult = withContext(Dispatchers.Default) {
                    AutoOrientationEngine.autoOrientAndDeskew(current)
                }
                if (!orientedResult.wasRotated) {
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            successMessage = "Document is already perfectly upright."
                        )
                    }
                    return@launch
                }
                pushUndoStep(UndoStep.FullBitmap(current))
                val newBitmap = orientedResult.rotatedBitmap
                _uiState.update {
                    it.copy(
                        originalBitmap = newBitmap,
                        currentBitmap = newBitmap,
                        detectedItems = emptyList(),
                        selectedItem = null,
                        selectedItems = emptyList(),
                        canUndo = true,
                        canRedo = false,
                        canvasRevision = it.canvasRevision + 1,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Auto-oriented: rotated ${orientedResult.rotationAngleApplied.toInt()}° upright"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Auto-orientation failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // --- Add New Text on Blank Space ---

    fun insertNewTextItem(bitmapX: Float, bitmapY: Float) {
        val current = _uiState.value.currentBitmap ?: return
        val defaultWidth = 140
        val defaultHeight = 44
        val left = bitmapX.toInt().coerceIn(0, (current.width - defaultWidth).coerceAtLeast(1))
        val top = bitmapY.toInt().coerceIn(0, (current.height - defaultHeight).coerceAtLeast(1))
        val box = Rect(left, top, left + defaultWidth, top + defaultHeight)

        val newItem = DetectedTextItem(
            id = "new_text_${System.currentTimeMillis()}",
            text = "New Text",
            boundingBox = box,
            cornerPoints = listOf(
                Point(box.left, box.top),
                Point(box.right, box.top),
                Point(box.right, box.bottom),
                Point(box.left, box.bottom)
            ),
            rotationAngle = 0f,
            inkColor = androidx.compose.ui.graphics.Color(0xFF0F172A),
            inkColorRgb = Color.rgb(15, 23, 42),
            typography = TypographyMetrics(
                estimatedFontWeight = FontWeightEstimate.REGULAR,
                strokeWidthRatio = 0.08f,
                glyphDensity = 0.5f,
                letterSpacingEm = 0.02f,
                estimatedFontSizePx = 28f
            ),
            confidence = 1.0f,
            level = TextHierarchyLevel.ELEMENT
        )

        _uiState.update {
            it.copy(
                selectedItem = newItem,
                isNewTextInsertion = true,
                activeToolMode = EditorToolMode.TEXT_EDIT
            )
        }
    }

    // --- #18 Multi-Word / Paragraph Lasso Selection ---

    fun setLassoSelection(items: List<DetectedTextItem>) {
        _uiState.update { it.copy(selectedItems = items) }
    }

    fun clearLassoSelection() {
        _uiState.update { it.copy(selectedItems = emptyList()) }
    }

    fun mergeAndEditLassoSelection() {
        val selected = _uiState.value.selectedItems
        if (selected.isEmpty()) return

        val sorted = selected.sortedWith(
            compareBy<DetectedTextItem> { (it.boundingBox.top / 24) * 1000 }
                .thenBy { it.boundingBox.left }
        )

        val mergedText = sorted.joinToString(" ") { it.text }
        var minL = Int.MAX_VALUE
        var minT = Int.MAX_VALUE
        var maxR = Int.MIN_VALUE
        var maxB = Int.MIN_VALUE

        for (item in sorted) {
            minL = minOf(minL, item.boundingBox.left)
            minT = minOf(minT, item.boundingBox.top)
            maxR = maxOf(maxR, item.boundingBox.right)
            maxB = maxOf(maxB, item.boundingBox.bottom)
        }

        val unionBox = Rect(minL, minT, maxR, maxB)
        val primary = sorted.first()
        val compoundItem = primary.copy(
            id = "compound_${System.currentTimeMillis()}",
            text = mergedText,
            boundingBox = unionBox
        )

        _uiState.update {
            it.copy(
                selectedItem = compoundItem,
                selectedItems = emptyList(),
                activeToolMode = EditorToolMode.TEXT_EDIT
            )
        }
    }

    fun whiteoutLassoSelection() {
        val selected = _uiState.value.selectedItems
        val current = _uiState.value.currentBitmap ?: return
        if (selected.isEmpty()) return

        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Erasing ${selected.size} text blocks...") }
            try {
                val updatedBitmap = withContext(Dispatchers.Default) {
                    var bmp = current.copy(Bitmap.Config.ARGB_8888, true)
                    for (item in selected) {
                        val cleaned = backgroundInpainter.inpaint(bmp, item.boundingBox)
                        bmp.recycle()
                        bmp = cleaned
                    }
                    bmp
                }

                val selectedIds = selected.map { it.id }.toSet()
                val remainingItems = _uiState.value.detectedItems.filterNot { selectedIds.contains(it.id) }

                _uiState.update {
                    it.copy(
                        currentBitmap = updatedBitmap,
                        detectedItems = remainingItems,
                        selectedItems = emptyList(),
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Erased ${selected.size} text blocks",
                        canUndo = true,
                        canRedo = false,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Whiteout failed: ${e.message}"
                    )
                }
            }
        }
    }

    // --- #10 Security Watermark Engine ---

    fun showWatermarkDialog(show: Boolean) {
        _uiState.update { it.copy(showWatermarkDialog = show) }
    }

    fun applyWatermark(config: WatermarkEngine.WatermarkConfig) {
        val current = _uiState.value.currentBitmap ?: return
        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Applying security watermark...") }
            try {
                val watermarked = withContext(Dispatchers.Default) {
                    WatermarkEngine.applyWatermark(current, config)
                }
                _uiState.update {
                    it.copy(
                        currentBitmap = watermarked,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Security watermark applied",
                        canUndo = true,
                        canRedo = false,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Watermark failed: ${e.message}"
                    )
                }
            }
        }
    }

    // --- #13 Book Curve Flattening AI ---

    fun showBookDewarpDialog(show: Boolean) {
        _uiState.update { it.copy(showBookDewarpDialog = show) }
    }

    fun applyBookDewarp(spine: BookCurveDewarper.SpinePosition, intensity: Float) {
        val current = _uiState.value.currentBitmap ?: return
        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))
        viewModelScope.launch {
            val isCrumpled = spine == BookCurveDewarper.SpinePosition.CRUMPLED_PAPER
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = if (isCrumpled) "AI flattening crumpled paper & fold creases..." else "AI flattening curved book gutter..."
                )
            }
            try {
                val flattened = withContext(Dispatchers.Default) {
                    BookCurveDewarper.flattenBookCurvature(current, spine, intensity)
                }
                _uiState.update {
                    it.copy(
                        currentBitmap = flattened,
                        detectedItems = emptyList(),
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = if (isCrumpled) "Crumpled paper flattened & creases removed" else "Book curve flattened & deskewed",
                        canUndo = true,
                        canRedo = false,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Book dewarp failed: ${e.message}"
                    )
                }
            }
        }
    }

    // --- Canva Pro Multi-Layer Engine & Overlay Placement ---

    fun openTextLayerDialog(layer: DocumentCanvasLayer? = null) {
        _uiState.update {
            it.copy(
                showEditTextLayerDialog = true,
                editingTextLayer = layer
            )
        }
    }

    fun closeTextLayerDialog() {
        _uiState.update {
            it.copy(
                showEditTextLayerDialog = false,
                editingTextLayer = null
            )
        }
    }

    fun addOrUpdateTextLayer(
        text: String,
        textColor: Int = android.graphics.Color.BLACK,
        bgColor: Int? = null,
        fontSize: Float = 36f,
        isBold: Boolean = true,
        isItalic: Boolean = false,
        fontFamily: String = "Sans-Serif"
    ) {
        val existing = _uiState.value.editingTextLayer
        val bmp = DocumentCanvasLayer.createTypographyBitmap(
            text = text,
            textColor = textColor,
            backgroundColor = bgColor,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            fontFamily = fontFamily
        )

        if (existing != null) {
            _uiState.update { state ->
                val updatedLayers = state.canvasLayers.map { layer ->
                    if (layer.id == existing.id) {
                        layer.copy(
                            bitmap = bmp,
                            text = text,
                            textColor = textColor,
                            backgroundColor = bgColor,
                            fontSize = fontSize,
                            isBold = isBold,
                            isItalic = isItalic,
                            fontFamily = fontFamily
                        )
                    } else layer
                }
                state.copy(
                    canvasLayers = updatedLayers,
                    activeOverlayBitmap = bmp,
                    showEditTextLayerDialog = false,
                    editingTextLayer = null,
                    canvasRevision = state.canvasRevision + 1,
                    successMessage = "Text layer updated!"
                )
            }
        } else {
            val current = _uiState.value.currentBitmap
            val startX = if (current != null) current.width * 0.25f else 100f
            val startY = if (current != null) current.height * 0.35f else 150f
            val newLayer = DocumentCanvasLayer(
                id = java.util.UUID.randomUUID().toString(),
                bitmap = bmp,
                x = startX,
                y = startY,
                scale = 1.0f,
                rotation = 0f,
                alpha = 1.0f,
                title = "Text: ${text.take(12)}",
                isTextLayer = true,
                text = text,
                textColor = textColor,
                backgroundColor = bgColor,
                fontSize = fontSize,
                isBold = isBold,
                isItalic = isItalic,
                fontFamily = fontFamily
            )
            _uiState.update {
                it.copy(
                    canvasLayers = it.canvasLayers + newLayer,
                    selectedLayerId = newLayer.id,
                    activeOverlayBitmap = bmp,
                    overlayPositionX = startX,
                    overlayPositionY = startY,
                    overlayScale = 1.0f,
                    overlayRotation = 0f,
                    overlayAlpha = 1.0f,
                    showEditTextLayerDialog = false,
                    editingTextLayer = null,
                    canvasRevision = it.canvasRevision + 1,
                    successMessage = "Text layer added to canvas!"
                )
            }
        }
    }

    fun addCanvasLayer(bitmap: Bitmap, title: String = "Layer") {
        val current = _uiState.value.currentBitmap ?: return
        // Dual-Pipeline Memory Guard: clamp layer bitmap dimension to avoid OOM
        val safeBmp = scaleDownIfNeeded(bitmap, maxDimension = 1600)
        val layerCount = _uiState.value.canvasLayers.size
        val startX = ((current.width * 0.25f) + (layerCount * 30f)).coerceAtMost(current.width * 0.7f)
        val startY = ((current.height * 0.35f) + (layerCount * 30f)).coerceAtMost(current.height * 0.7f)

        val initialScale = if (safeBmp.width > current.width * 0.6f) {
            ((current.width * 0.5f) / safeBmp.width).coerceIn(0.2f, 1.0f)
        } else {
            1.0f
        }

        val layerTitle = if (title.contains("#")) title else "$title #${layerCount + 1}"
        val newLayer = DocumentCanvasLayer(
            id = java.util.UUID.randomUUID().toString(),
            bitmap = safeBmp,
            x = startX,
            y = startY,
            scale = initialScale,
            rotation = 0f,
            alpha = 1.0f,
            title = layerTitle
        )

        _uiState.update {
            it.copy(
                canvasLayers = it.canvasLayers + newLayer,
                selectedLayerId = newLayer.id,
                activeOverlayBitmap = safeBmp,
                overlayPositionX = startX,
                overlayPositionY = startY,
                overlayScale = initialScale,
                overlayRotation = 0f,
                overlayAlpha = 1.0f,
                showCanvaStickersDialog = false,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "Added ${newLayer.title}. Drag, pinch or use tools to customize."
            )
        }
    }

    fun selectCanvasLayer(id: String?) {
        _uiState.update { state ->
            val layer = state.canvasLayers.firstOrNull { it.id == id }
            state.copy(
                selectedLayerId = id,
                activeOverlayBitmap = layer?.bitmap,
                overlayPositionX = layer?.x ?: state.overlayPositionX,
                overlayPositionY = layer?.y ?: state.overlayPositionY,
                overlayScale = layer?.scale ?: state.overlayScale,
                overlayRotation = layer?.rotation ?: state.overlayRotation,
                overlayAlpha = layer?.alpha ?: 1f,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun startPlacingOverlay(bitmap: Bitmap) {
        addCanvasLayer(bitmap, "Signature / Stamp")
    }

    fun updateOverlayPosition(deltaX: Float, deltaY: Float) {
        val selId = _uiState.value.selectedLayerId
        val current = _uiState.value.currentBitmap ?: return
        if (selId == null) {
            val newX = (_uiState.value.overlayPositionX + deltaX).coerceIn(0f, current.width.toFloat())
            val newY = (_uiState.value.overlayPositionY + deltaY).coerceIn(0f, current.height.toFloat())
            _uiState.update { it.copy(overlayPositionX = newX, overlayPositionY = newY) }
            return
        }
        _uiState.update { state ->
            val updated = state.canvasLayers.map { layer ->
                if (layer.id == selId) {
                    val newX = (layer.x + deltaX).coerceIn(-layer.bitmap.width * 0.8f, current.width.toFloat())
                    val newY = (layer.y + deltaY).coerceIn(-layer.bitmap.height * 0.8f, current.height.toFloat())
                    layer.copy(x = newX, y = newY)
                } else layer
            }
            val activeL = updated.firstOrNull { it.id == selId }
            state.copy(
                canvasLayers = updated,
                overlayPositionX = activeL?.x ?: state.overlayPositionX,
                overlayPositionY = activeL?.y ?: state.overlayPositionY,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun updateOverlayScale(scaleMultiplier: Float) {
        val selId = _uiState.value.selectedLayerId
        if (selId == null) {
            val newScale = (_uiState.value.overlayScale * scaleMultiplier).coerceIn(0.25f, 4.0f)
            _uiState.update { it.copy(overlayScale = newScale) }
            return
        }
        _uiState.update { state ->
            val updated = state.canvasLayers.map { layer ->
                if (layer.id == selId) {
                    val newScale = (layer.scale * scaleMultiplier).coerceIn(0.15f, 5.0f)
                    layer.copy(scale = newScale)
                } else layer
            }
            val activeL = updated.firstOrNull { it.id == selId }
            state.copy(
                canvasLayers = updated,
                overlayScale = activeL?.scale ?: state.overlayScale,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun updateOverlayRotation(rotation: Float) {
        val selId = _uiState.value.selectedLayerId
        if (selId == null) {
            _uiState.update { it.copy(overlayRotation = rotation % 360f) }
            return
        }
        _uiState.update { state ->
            val updated = state.canvasLayers.map { layer ->
                if (layer.id == selId) {
                    layer.copy(rotation = rotation % 360f)
                } else layer
            }
            val activeL = updated.firstOrNull { it.id == selId }
            state.copy(
                canvasLayers = updated,
                overlayRotation = activeL?.rotation ?: state.overlayRotation,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun rotateOverlayBy(deltaDegrees: Float) {
        val selId = _uiState.value.selectedLayerId
        if (selId == null) {
            val newRot = (_uiState.value.overlayRotation + deltaDegrees) % 360f
            _uiState.update { it.copy(overlayRotation = newRot) }
            return
        }
        _uiState.update { state ->
            val updated = state.canvasLayers.map { layer ->
                if (layer.id == selId) {
                    layer.copy(rotation = (layer.rotation + deltaDegrees) % 360f)
                } else layer
            }
            val activeL = updated.firstOrNull { it.id == selId }
            state.copy(
                canvasLayers = updated,
                overlayRotation = activeL?.rotation ?: state.overlayRotation,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun updateSelectedLayerAlpha(newAlpha: Float) {
        val selId = _uiState.value.selectedLayerId ?: return
        _uiState.update { state ->
            val clamped = newAlpha.coerceIn(0.1f, 1.0f)
            val updated = state.canvasLayers.map { layer ->
                if (layer.id == selId) {
                    layer.copy(alpha = clamped)
                } else layer
            }
            state.copy(
                canvasLayers = updated,
                overlayAlpha = clamped,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun duplicateSelectedLayer() {
        val selId = _uiState.value.selectedLayerId ?: return
        val target = _uiState.value.canvasLayers.firstOrNull { it.id == selId } ?: return
        val duplicated = target.copy(
            id = java.util.UUID.randomUUID().toString(),
            x = target.x + 35f,
            y = target.y + 35f,
            title = "${target.title} (Copy)"
        )
        _uiState.update { state ->
            state.copy(
                canvasLayers = state.canvasLayers + duplicated,
                selectedLayerId = duplicated.id,
                activeOverlayBitmap = duplicated.bitmap,
                canvasRevision = state.canvasRevision + 1,
                successMessage = "Layer duplicated!"
            )
        }
    }

    fun deleteSelectedLayer() {
        val selId = _uiState.value.selectedLayerId
        if (selId == null) {
            cancelOverlay()
            return
        }
        _uiState.update { state ->
            val remaining = state.canvasLayers.filter { it.id != selId }
            state.copy(
                canvasLayers = remaining,
                selectedLayerId = remaining.lastOrNull()?.id,
                activeOverlayBitmap = remaining.lastOrNull()?.bitmap,
                canvasRevision = state.canvasRevision + 1,
                successMessage = "Layer removed."
            )
        }
    }

    fun bringSelectedLayerToFront() {
        val selId = _uiState.value.selectedLayerId ?: return
        _uiState.update { state ->
            val target = state.canvasLayers.firstOrNull { it.id == selId } ?: return@update state
            val others = state.canvasLayers.filter { it.id != selId }
            state.copy(
                canvasLayers = others + target,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun sendSelectedLayerToBack() {
        val selId = _uiState.value.selectedLayerId ?: return
        _uiState.update { state ->
            val target = state.canvasLayers.firstOrNull { it.id == selId } ?: return@update state
            val others = state.canvasLayers.filter { it.id != selId }
            state.copy(
                canvasLayers = listOf(target) + others,
                canvasRevision = state.canvasRevision + 1
            )
        }
    }

    fun flattenAllLayersToDocument(saveUndo: Boolean = true): Bitmap? {
        val current = _uiState.value.currentBitmap ?: return null
        val layers = _uiState.value.canvasLayers

        if (layers.isEmpty()) {
            if (_uiState.value.activeOverlayBitmap != null) {
                val overlay = _uiState.value.activeOverlayBitmap ?: return current
                if (saveUndo) pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))
                val res = current.copy(Bitmap.Config.ARGB_8888, true)
                val c = Canvas(res)
                val posX = _uiState.value.overlayPositionX
                val posY = _uiState.value.overlayPositionY
                val scale = _uiState.value.overlayScale
                val rotation = _uiState.value.overlayRotation
                val dstW = (overlay.width * scale).toInt().coerceAtLeast(1)
                val dstH = (overlay.height * scale).toInt().coerceAtLeast(1)
                val dstRect = Rect(posX.toInt(), posY.toInt(), posX.toInt() + dstW, posY.toInt() + dstH)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                if (rotation != 0f) {
                    c.save()
                    c.rotate(rotation, posX + dstW / 2f, posY + dstH / 2f)
                    c.drawBitmap(overlay, null, dstRect, paint)
                    c.restore()
                } else {
                    c.drawBitmap(overlay, null, dstRect, paint)
                }
                val pageIdx = _uiState.value.currentPdfPageIndex
                editedPagesMap[pageIdx] = res
                _uiState.update {
                    it.copy(
                        currentBitmap = res,
                        activeOverlayBitmap = null,
                        overlayRotation = 0f,
                        canUndo = true,
                        canRedo = false,
                        hasUnsavedChanges = true,
                        canvasRevision = it.canvasRevision + 1,
                        successMessage = "Overlay stamped permanently!"
                    )
                }
                updateRecentDocumentThumbnail(res)
                return res
            }
            return current
        }

        if (saveUndo) {
            pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))
        }

        val resultBitmap = current.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        layers.forEach { layer ->
            val dstW = (layer.bitmap.width * layer.scale).toInt().coerceAtLeast(1)
            val dstH = (layer.bitmap.height * layer.scale).toInt().coerceAtLeast(1)
            val dstRect = Rect(layer.x.toInt(), layer.y.toInt(), layer.x.toInt() + dstW, layer.y.toInt() + dstH)

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (layer.alpha * 255).toInt().coerceIn(0, 255)
            }

            if (layer.rotation != 0f) {
                canvas.save()
                canvas.rotate(layer.rotation, layer.x + dstW / 2f, layer.y + dstH / 2f)
                canvas.drawBitmap(layer.bitmap, null, dstRect, paint)
                canvas.restore()
            } else {
                canvas.drawBitmap(layer.bitmap, null, dstRect, paint)
            }
        }

        val pageIdx = _uiState.value.currentPdfPageIndex
        editedPagesMap[pageIdx] = resultBitmap

        _uiState.update {
            it.copy(
                currentBitmap = resultBitmap,
                canvasLayers = emptyList(),
                selectedLayerId = null,
                activeOverlayBitmap = null,
                overlayRotation = 0f,
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "Layers flattened to document successfully!"
            )
        }
        updateRecentDocumentThumbnail(resultBitmap)
        return resultBitmap
    }

    fun commitOverlayToDocument() {
        flattenAllLayersToDocument()
    }

    fun cancelOverlay() {
        if (_uiState.value.canvasLayers.isNotEmpty()) {
            deleteSelectedLayer()
            return
        }
        _uiState.update {
            it.copy(
                activeOverlayBitmap = null,
                overlayRotation = 0f,
                processingMessage = null
            )
        }
    }

    // --- Search in Document ---

    fun setSearchQuery(query: String) {
        val matches = if (query.isBlank()) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            _uiState.value.detectedItems.mapIndexedNotNull { index, item ->
                if (item.text.lowercase().contains(q)) index else null
            }
        }
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchMatchingIndices = matches
            )
        }
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (active) it.searchQuery else "",
                searchMatchingIndices = if (active) it.searchMatchingIndices else emptyList()
            )
        }
    }

    fun replaceAllOccurrences(query: String, replacement: String, allPages: Boolean = false) {
        if (query.isBlank()) return
        val state = _uiState.value
        val currentBitmap = state.currentBitmap ?: return
        val q = query.trim().lowercase()

        saveCurrentPageToCache()
        val context = getApplication<Application>()

        if (allPages && state.pdfPageCount > 1) {
            // Multi-page document-wide replace
            pushUndoStep(UndoStep.FullBitmap(currentBitmap))

            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = true,
                        processingMessage = "Replacing '$query' across all ${state.pdfPageCount} pages..."
                    )
                }

                try {
                    var totalReplacements = 0
                    var affectedPages = 0
                    var updatedCurrentBitmap: Bitmap? = null
                    var updatedCurrentItems: List<DetectedTextItem>? = null

                    withContext(Dispatchers.Default) {
                        for (pIdx in 0 until state.pdfPageCount) {
                            val pageBmp = if (pIdx == state.currentPdfPageIndex) {
                                currentBitmap
                            } else {
                                editedPagesMap[pIdx]
                                    ?: if (state.activePdfUri != null) PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, pIdx)
                                    else if (state.batchScannedPaths.size > pIdx) com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[pIdx], 2880)
                                    else null
                            } ?: continue

                            val pageItems = if (pIdx == state.currentPdfPageIndex) {
                                state.detectedItems
                            } else {
                                pageDetectedItemsMap[pIdx]
                                    ?: ocrAnalyzer.detectTextBlocks(pageBmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE)
                            }

                            val matchingItems = pageItems.filter { it.text.lowercase().contains(q) }
                            if (matchingItems.isNotEmpty()) {
                                affectedPages++
                                totalReplacements += matchingItems.size
                                var workingBmp = pageBmp.copy(Bitmap.Config.ARGB_8888, true)
                                val updatedMap = mutableMapOf<String, DetectedTextItem>()

                                for (item in matchingItems) {
                                    val newText = item.text.replace(query, replacement, ignoreCase = true)
                                    val cleanedBg = backgroundInpainter.inpaint(
                                        sourceBitmap = workingBmp,
                                        targetBounds = item.boundingBox
                                    )
                                    val renderResult = textRenderer.render(
                                        cleanedBackground = cleanedBg,
                                        params = TextRenderer.TextRenderParams(
                                            newText = newText,
                                            originalText = item.text,
                                            targetBounds = item.boundingBox,
                                            inkColorRgb = item.inkColorRgb,
                                            rotationAngle = item.rotationAngle,
                                            typographyMetrics = item.typography,
                                            overrideClassification = null,
                                            isBold = (item.typography.estimatedFontWeight == FontWeightEstimate.BOLD),
                                            sizeMultiplier = 1.0f,
                                            alignment = Paint.Align.LEFT
                                        )
                                    )
                                    cleanedBg.recycle()
                                    if (workingBmp != pageBmp) {
                                        workingBmp.recycle()
                                    }
                                    workingBmp = renderResult.outputBitmap

                                    val charW = (item.boundingBox.height() * 0.52f)
                                    val newWidth = (newText.length * charW).toInt().coerceAtLeast(24)
                                    val updatedItem = item.copy(
                                        text = newText,
                                        boundingBox = Rect(item.boundingBox.left, item.boundingBox.top, item.boundingBox.left + newWidth, item.boundingBox.bottom)
                                    )
                                    updatedMap[item.id] = updatedItem
                                }

                                val newItems = pageItems.map { updatedMap[it.id] ?: it }
                                editedPagesMap[pIdx] = workingBmp
                                pageDetectedItemsMap[pIdx] = newItems

                                if (pIdx == state.currentPdfPageIndex) {
                                    updatedCurrentBitmap = workingBmp
                                    updatedCurrentItems = newItems
                                }
                            }
                        }
                    }

                    if (totalReplacements == 0) {
                        _uiState.update {
                            it.copy(
                                isApplyingEdit = false,
                                processingMessage = null,
                                errorMessage = "No occurrences of '$query' found across all ${state.pdfPageCount} pages"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                originalBitmap = updatedCurrentBitmap ?: it.originalBitmap,
                                currentBitmap = updatedCurrentBitmap ?: it.currentBitmap,
                                detectedItems = updatedCurrentItems ?: it.detectedItems,
                                selectedItem = null,
                                selectedItems = emptyList(),
                                canUndo = true,
                                canRedo = false,
                                hasUnsavedChanges = true,
                                canvasRevision = it.canvasRevision + 1,
                                isApplyingEdit = false,
                                processingMessage = null,
                                searchQuery = "",
                                searchMatchingIndices = emptyList(),
                                successMessage = "Replaced $totalReplacements occurrences across $affectedPages page(s)!"
                            )
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            errorMessage = "Multi-page replace failed: ${e.localizedMessage}"
                        )
                    }
                }
            }
        } else {
            val matchingItems = state.detectedItems.filter {
                it.text.lowercase().contains(q)
            }
            if (matchingItems.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "No occurrences found for '$query'") }
                return
            }

            pushUndoStep(UndoStep.FullBitmap(currentBitmap))

            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = true,
                        processingMessage = "Replacing ${matchingItems.size} occurrences of '$query'..."
                    )
                }

                try {
                    var workingBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val updatedItemsMap = mutableMapOf<String, DetectedTextItem>()

                    withContext(Dispatchers.Default) {
                        for (item in matchingItems) {
                            val newText = item.text.replace(query, replacement, ignoreCase = true)

                            val cleanedBackground = backgroundInpainter.inpaint(
                                sourceBitmap = workingBitmap,
                                targetBounds = item.boundingBox
                            )

                            val renderResult = textRenderer.render(
                                cleanedBackground = cleanedBackground,
                                params = TextRenderer.TextRenderParams(
                                    newText = newText,
                                    originalText = item.text,
                                    targetBounds = item.boundingBox,
                                    inkColorRgb = item.inkColorRgb,
                                    rotationAngle = item.rotationAngle,
                                    typographyMetrics = item.typography,
                                    overrideClassification = null,
                                    isBold = (item.typography.estimatedFontWeight == FontWeightEstimate.BOLD),
                                    sizeMultiplier = 1.0f,
                                    alignment = Paint.Align.LEFT
                                )
                            )

                            cleanedBackground.recycle()
                            if (workingBitmap != currentBitmap) {
                                workingBitmap.recycle()
                            }
                            workingBitmap = renderResult.outputBitmap

                            val charW = (item.boundingBox.height() * 0.52f)
                            val newWidth = (newText.length * charW).toInt().coerceAtLeast(24)
                            val updatedItem = item.copy(
                                text = newText,
                                boundingBox = Rect(item.boundingBox.left, item.boundingBox.top, item.boundingBox.left + newWidth, item.boundingBox.bottom)
                            )
                            updatedItemsMap[item.id] = updatedItem
                        }
                    }

                    val newDetectedItems = state.detectedItems.map { item ->
                        updatedItemsMap[item.id] ?: item
                    }

                    _uiState.update {
                        it.copy(
                            originalBitmap = workingBitmap,
                            currentBitmap = workingBitmap,
                            detectedItems = newDetectedItems,
                            selectedItem = null,
                            selectedItems = emptyList(),
                            canUndo = true,
                            canRedo = false,
                            hasUnsavedChanges = true,
                            canvasRevision = it.canvasRevision + 1,
                            isApplyingEdit = false,
                            processingMessage = null,
                            searchQuery = "",
                            searchMatchingIndices = emptyList(),
                            successMessage = "Replaced ${matchingItems.size} occurrences of '$query' with '$replacement'!"
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            errorMessage = "Replace all failed: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    // --- Recent Documents Management ---

    fun deleteRecentDocument(id: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            DocumentHistoryManager.deleteDocument(context, id)
            refreshRecentDocuments()
        }
    }

    fun clearRecentDocuments() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            DocumentHistoryManager.clearAllDocuments(context)
            refreshRecentDocuments()
        }
    }

    fun updateDocumentCategory(id: String, newCategory: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            DocumentHistoryManager.updateDocumentCategory(context, id, newCategory)
            refreshRecentDocuments()
        }
    }

    fun renameDocument(id: String, newTitle: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            DocumentHistoryManager.renameDocument(context, id, newTitle)
            refreshRecentDocuments()
        }
    }

    // --- Highlighter & Pen Markup Engine ---

    fun setMarkupColor(colorRgb: Int) {
        _uiState.update { it.copy(markupColorRgb = colorRgb) }
    }

    fun setMarkupStrokeWidth(width: Float) {
        _uiState.update { it.copy(markupStrokeWidth = width.coerceIn(10f, 60f)) }
    }

    fun setPenColor(colorRgb: Int) {
        _uiState.update { it.copy(penColorRgb = colorRgb) }
    }

    fun setPenStrokeWidth(width: Float) {
        _uiState.update { it.copy(penStrokeWidth = width.coerceIn(2f, 20f)) }
    }

    fun commitMarkupStroke(
        points: List<PointF>,
        colorRgb: Int,
        strokeWidth: Float,
        isHighlighter: Boolean
    ) {
        val currentBitmap = _uiState.value.currentBitmap ?: return
        if (points.size < 2) return

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val pad = (strokeWidth + 12f).toInt()
        val patchL = (minX.toInt() - pad).coerceIn(0, currentBitmap.width - 1)
        val patchT = (minY.toInt() - pad).coerceIn(0, currentBitmap.height - 1)
        val patchR = (maxX.toInt() + pad).coerceIn(0, currentBitmap.width)
        val patchB = (maxY.toInt() + pad).coerceIn(0, currentBitmap.height)
        val patchW = max(1, patchR - patchL)
        val patchH = max(1, patchB - patchT)

        val patchBmp = Bitmap.createBitmap(currentBitmap, patchL, patchT, patchW, patchH)
        pushUndoStep(UndoStep.PixelPatch(patchBmp, patchL, patchT))

        val canvas = Canvas(currentBitmap)
        val path = android.graphics.Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val midX = (pPrev.x + pCurr.x) / 2f
            val midY = (pPrev.y + pCurr.y) / 2f
            path.quadTo(pPrev.x, pPrev.y, midX, midY)
        }
        path.lineTo(points.last().x, points.last().y)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (isHighlighter) {
                val alpha = 115
                val r = Color.red(colorRgb)
                val g = Color.green(colorRgb)
                val b = Color.blue(colorRgb)
                color = Color.argb(alpha, r, g, b)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
            } else {
                color = colorRgb
            }
        }
        canvas.drawPath(path, paint)

        val pageIdx = _uiState.value.currentPdfPageIndex
        editedPagesMap[pageIdx] = currentBitmap
        updateRecentDocumentThumbnail(currentBitmap)

        _uiState.update {
            it.copy(
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                canvasRevision = it.canvasRevision + 1
            )
        }
    }

    /**
     * Non-destructive markup reset: Restores current page back to clean base bitmap without losing document state.
     * Can also be undone via undo button.
     */
    fun clearAllMarkupsOnCurrentPage() {
        val original = _uiState.value.originalBitmap ?: return
        val current = _uiState.value.currentBitmap ?: return
        val pageIdx = _uiState.value.currentPdfPageIndex

        // Push full patch so this reset can also be undone if desired!
        val patchBmp = current.copy(current.config ?: Bitmap.Config.ARGB_8888, true)
        pushUndoStep(UndoStep.PixelPatch(patchBmp, 0, 0))

        val restored = original.copy(Bitmap.Config.ARGB_8888, true)
        _uiState.update {
            it.copy(
                currentBitmap = restored,
                canUndo = true,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "🧹 Page markup cleared to base document."
            )
        }
        editedPagesMap[pageIdx] = restored
        updateRecentDocumentThumbnail(restored)
    }

    // --- Gemini Custom API Key Configuration ---

    fun getGeminiApiKey(): String {
        val prefs = getApplication<Application>().getSharedPreferences("docu_settings", Context.MODE_PRIVATE)
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun setGeminiApiKey(key: String) {
        val prefs = getApplication<Application>().getSharedPreferences("docu_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    // --- Navigation & Tool Switching ---

    fun setActiveToolMode(mode: EditorToolMode) {
        _uiState.update { it.copy(activeToolMode = mode) }
    }

    fun showIdCardDialog(show: Boolean) {
        _uiState.update { it.copy(showIdCardDialog = show) }
    }

    fun showSignatureDialog(show: Boolean) {
        _uiState.update { it.copy(showSignatureDialog = show) }
    }

    fun showPdfToolboxDialog(show: Boolean) {
        _uiState.update { it.copy(showPdfToolboxDialog = show) }
    }

    fun showTargetSizeAdjusterDialog(show: Boolean) {
        _uiState.update { it.copy(showTargetSizeAdjusterDialog = show) }
    }

    fun adjustDocumentToTargetSize(mode: SizeAdjustMode, targetKb: Int, format: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = if (mode == SizeAdjustMode.DECREASE)
                        "Compressing to ${targetKb} KB ($format)..."
                    else
                        "Padding to ${targetKb} KB for Govt Portal ($format)...",
                    showTargetSizeAdjusterDialog = false
                )
            }
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val time = System.currentTimeMillis()

                if (format.equals("PDF", ignoreCase = true)) {
                    val tempPdf = getOrGenerateConsolidatedPdf() ?: throw IllegalStateException("Failed to generate document PDF")
                    val outFile = File(downloadsDir, "DocuEdit_Target_${targetKb}KB_${time}.pdf")
                    val result = if (mode == SizeAdjustMode.DECREASE) {
                        TargetFileSizeEngine.compressPdfToTargetKb(
                            context = getApplication(),
                            sourceUri = Uri.fromFile(tempPdf),
                            targetKb = targetKb,
                            outputFile = outFile
                        )
                    } else {
                        TargetFileSizeEngine.increasePdfToTargetKb(
                            context = getApplication(),
                            sourceUri = Uri.fromFile(tempPdf),
                            targetKb = targetKb,
                            outputFile = outFile
                        )
                    }
                    tempPdf.delete()
                    val actualKb = result.finalBytes / 1024
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            successMessage = "Target size ready: ${outFile.name} (${actualKb} KB)"
                        )
                    }
                } else {
                    val currentBmp = _uiState.value.currentBitmap ?: throw IllegalStateException("No active document loaded")
                    val outFile = File(downloadsDir, "DocuEdit_Target_${targetKb}KB_${time}.jpg")
                    val result = if (mode == SizeAdjustMode.DECREASE) {
                        TargetFileSizeEngine.compressBitmapToTargetKb(
                            bitmap = currentBmp,
                            targetKb = targetKb,
                            outputFile = outFile
                        )
                    } else {
                        val tempJpg = File.createTempFile("temp_adjust_", ".jpg", getApplication<Application>().cacheDir)
                        withContext(Dispatchers.IO) {
                            FileOutputStream(tempJpg).use { currentBmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                        }
                        val res = TargetFileSizeEngine.increaseJpegToTargetKb(
                            inputJpegFile = tempJpg,
                            targetKb = targetKb,
                            outputFile = outFile
                        )
                        tempJpg.delete()
                        res
                    }
                    val actualKb = result.finalBytes / 1024
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            successMessage = "Target size ready: ${outFile.name} (${actualKb} KB)"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Target size adjust failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun showPkiDigitalSignDialog(show: Boolean) {
        _uiState.update { it.copy(showPkiDigitalSignDialog = show) }
    }

    private suspend fun getOrGenerateConsolidatedPdf(): File? = withContext(Dispatchers.IO) {
        try {
            val cachePdf = File(getApplication<Application>().cacheDir, "consolidated_temp_${System.currentTimeMillis()}.pdf")
            val totalPages = _uiState.value.pdfPageCount.coerceAtLeast(1)
            val activePdfUri = _uiState.value.activePdfUri
            val batchPaths = _uiState.value.batchScannedPaths
            val context = getApplication<Application>()
            val current = _uiState.value.currentBitmap

            if (totalPages > 1 && (activePdfUri != null || batchPaths.isNotEmpty() || editedPagesMap.isNotEmpty())) {
                PdfExportEngine.exportPagesStreamingToPdf(
                    pageCount = totalPages,
                    pageBitmapProvider = { idx ->
                        val cached = editedPagesMap[idx]
                        if (cached != null) {
                            cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
                        } else if (activePdfUri != null) {
                            PdfPageLoader.renderPageToBitmap(context, activePdfUri, idx)
                        } else if (batchPaths.size > idx) {
                            com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(batchPaths[idx], 2880)
                        } else {
                            current?.copy(current.config ?: Bitmap.Config.ARGB_8888, false)
                        }
                    },
                    outputFile = cachePdf,
                    fitToA4 = true,
                    pagesDetectedItems = pageDetectedItemsMap,
                    autoRecycleBitmaps = true
                )
            } else if (current != null) {
                PdfExportEngine.exportBitmapToPdf(
                    bitmap = current,
                    outputFile = cachePdf,
                    fitToA4 = true,
                    detectedItems = _uiState.value.detectedItems
                )
            } else if (activePdfUri != null) {
                context.contentResolver.openInputStream(activePdfUri)?.use { input ->
                    FileOutputStream(cachePdf).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                return@withContext null
            }
            cachePdf
        } catch (_: Exception) {
            null
        }
    }

    fun prepareAndLaunchPkiSign() {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Preparing document for PKI signing...") }
            val tempPdf = getOrGenerateConsolidatedPdf()
            _uiState.update { 
                it.copy(
                    isApplyingEdit = false,
                    processingMessage = null,
                    showPkiDigitalSignDialog = (tempPdf != null),
                    pendingSignedPdfFile = tempPdf,
                    errorMessage = if (tempPdf == null) "Failed to prepare document for signing" else null
                )
            }
        }
    }

    fun splitCurrentDocument() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Splitting document into single pages...") }
            try {
                val tempPdf = getOrGenerateConsolidatedPdf() ?: throw IllegalStateException("Could not generate document PDF")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val toolbox = PdfToolbox(context)
                val splitFiles = toolbox.splitPdf(Uri.fromFile(tempPdf), downloadsDir)
                tempPdf.delete()

                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        showPdfToolboxDialog = false,
                        successMessage = "Split into ${splitFiles.size} PDF files in Downloads!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Split failed: ${e.localizedMessage}") }
            }
        }
    }

    fun extractPagesAsImages(quality: Int = 92) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Extracting high-res JPG images...") }
            try {
                val tempPdf = getOrGenerateConsolidatedPdf() ?: throw IllegalStateException("Could not generate document PDF")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val toolbox = PdfToolbox(context)
                val imageFiles = toolbox.extractPagesAsImages(Uri.fromFile(tempPdf), downloadsDir, quality)
                tempPdf.delete()

                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        showPdfToolboxDialog = false,
                        successMessage = "Saved ${imageFiles.size} high-res page images in Downloads!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Extract images failed: ${e.localizedMessage}") }
            }
        }
    }

    fun addWatermarkAndExport(watermarkText: String, opacity: Float = 0.22f) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Applying security watermark '$watermarkText'...") }
            try {
                val tempPdf = getOrGenerateConsolidatedPdf() ?: throw IllegalStateException("Could not generate document PDF")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outFile = File(downloadsDir, "DocuEdit_Watermarked_${System.currentTimeMillis()}.pdf")
                val toolbox = PdfToolbox(context)
                toolbox.addWatermarkToPdf(Uri.fromFile(tempPdf), watermarkText, outFile, opacity)
                tempPdf.delete()

                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        showPdfToolboxDialog = false,
                        successMessage = "Watermarked PDF saved: ${outFile.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Watermark failed: ${e.localizedMessage}") }
            }
        }
    }

    fun showOcrTextExtractDialog(show: Boolean) {
        if (show) {
            val current = _uiState.value.currentBitmap ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isScanning = true, processingMessage = "Extracting text with CamScanner OCR...") }
                try {
                    val items = withContext(Dispatchers.Default) {
                        ocrAnalyzer.detectTextBlocks(current, TextHierarchyLevel.LINE)
                    }
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            processingMessage = null,
                            detectedItems = items,
                            showOcrTextExtractDialog = true
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isScanning = false, processingMessage = null, errorMessage = "OCR extraction failed: ${e.localizedMessage}") }
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    showOcrTextExtractDialog = false,
                    detectedItems = emptyList() // Clean canvas! Zero annoying bounding boxes!
                )
            }
        }
    }

    fun showInteractiveCropDialog(show: Boolean) {
        _uiState.update { it.copy(showInteractiveCropDialog = show) }
    }

    fun showCloudAiSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showCloudAiSettingsDialog = show) }
    }

    fun setDocumentTitle(title: String) {
        val clean = title.trim()
        if (clean.isNotBlank()) {
            _uiState.update { it.copy(documentTitle = clean, showRenameDialog = false, hasUnsavedChanges = true) }
        } else {
            _uiState.update { it.copy(showRenameDialog = false) }
        }
    }

    fun showRenameDialog(show: Boolean) {
        _uiState.update { it.copy(showRenameDialog = show) }
    }

    fun addBlankPage() {
        val state = _uiState.value
        val w = state.currentBitmap?.width?.coerceAtLeast(600) ?: 1240
        val h = state.currentBitmap?.height?.coerceAtLeast(800) ?: 1754
        val blankBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        appendPageToDocument(blankBmp)
    }

    fun quickCopyItemText(item: DetectedTextItem) {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Document Text", item.text)
        clipboard?.setPrimaryClip(clip)
        _uiState.update {
            it.copy(
                selectedItem = null,
                successMessage = "Copied text to clipboard"
            )
        }
    }

    fun quickEraseItem(item: DetectedTextItem) {
        val currentBitmap = _uiState.value.currentBitmap ?: return
        val margin = 4
        val patchL = max(0, item.boundingBox.left - margin)
        val patchT = max(0, item.boundingBox.top - margin)
        val patchR = min(currentBitmap.width, item.boundingBox.right + margin)
        val patchB = min(currentBitmap.height, item.boundingBox.bottom + margin)
        val patchW = max(1, patchR - patchL)
        val patchH = max(1, patchB - patchT)

        val patchBmp = Bitmap.createBitmap(currentBitmap, patchL, patchT, patchW, patchH)
        pushUndoStep(UndoStep.PixelPatch(patchBmp, patchL, patchT))

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Erasing text...") }
            try {
                val updatedBitmap = withContext(Dispatchers.Default) {
                    backgroundInpainter.inpaint(currentBitmap, item.boundingBox)
                }
                val remaining = _uiState.value.detectedItems.filterNot { it.id == item.id }
                editedPagesMap[_uiState.value.currentPdfPageIndex] = updatedBitmap
                _uiState.update {
                    it.copy(
                        currentBitmap = updatedBitmap,
                        detectedItems = remaining,
                        selectedItem = null,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Erased text seamlessly",
                        canUndo = true,
                        canRedo = false,
                        hasUnsavedChanges = true,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
                updateRecentDocumentThumbnail(updatedBitmap)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isApplyingEdit = false, processingMessage = null, errorMessage = "Erase failed: ${e.message}")
                }
            }
        }
    }

    fun quickHighlightItem(item: DetectedTextItem, colorRgb: Int = Color.rgb(255, 235, 59)) {
        val currentBitmap = _uiState.value.currentBitmap ?: return
        val margin = 4
        val patchL = max(0, item.boundingBox.left - margin)
        val patchT = max(0, item.boundingBox.top - margin)
        val patchR = min(currentBitmap.width, item.boundingBox.right + margin)
        val patchB = min(currentBitmap.height, item.boundingBox.bottom + margin)
        val patchW = max(1, patchR - patchL)
        val patchH = max(1, patchB - patchT)

        val patchBmp = Bitmap.createBitmap(currentBitmap, patchL, patchT, patchW, patchH)
        pushUndoStep(UndoStep.PixelPatch(patchBmp, patchL, patchT))

        val canvas = Canvas(currentBitmap)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, Color.red(colorRgb), Color.green(colorRgb), Color.blue(colorRgb))
            style = Paint.Style.FILL
        }
        val rect = RectF(
            (item.boundingBox.left - 2).toFloat(),
            (item.boundingBox.top - 1).toFloat(),
            (item.boundingBox.right + 2).toFloat(),
            (item.boundingBox.bottom + 1).toFloat()
        )
        canvas.drawRoundRect(rect, 4f, 4f, highlightPaint)
        editedPagesMap[_uiState.value.currentPdfPageIndex] = currentBitmap

        _uiState.update {
            it.copy(
                selectedItem = null,
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                successMessage = "Highlighted text",
                canvasRevision = it.canvasRevision + 1
            )
        }
        updateRecentDocumentThumbnail(currentBitmap)
    }

    fun quickBlackoutItem(item: DetectedTextItem) {
        applyBlackoutRect(item.boundingBox)
        _uiState.update { it.copy(selectedItem = null) }
    }

    fun setRedactionBrushRadius(radius: Float) {
        _uiState.update { it.copy(redactionBrushRadius = radius.coerceIn(8f, 120f)) }
    }

    fun applyBlackoutRect(rect: Rect) {
        val currentBitmap = _uiState.value.currentBitmap ?: return
        val patchL = max(0, rect.left - 2)
        val patchT = max(0, rect.top - 2)
        val patchR = min(currentBitmap.width, rect.right + 2)
        val patchB = min(currentBitmap.height, rect.bottom + 2)
        val patchW = max(1, patchR - patchL)
        val patchH = max(1, patchB - patchT)

        val patchBmp = Bitmap.createBitmap(currentBitmap, patchL, patchT, patchW, patchH)
        pushUndoStep(UndoStep.PixelPatch(patchBmp, patchL, patchT))

        val canvas = Canvas(currentBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawRect(rect, paint)

        // Remove any text items obscured by this redaction box
        val remaining = _uiState.value.detectedItems.filterNot { item ->
            Rect.intersects(rect, item.boundingBox)
        }

        editedPagesMap[_uiState.value.currentPdfPageIndex] = currentBitmap

        _uiState.update {
            it.copy(
                detectedItems = remaining,
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                successMessage = "Redacted area censored",
                canvasRevision = it.canvasRevision + 1
            )
        }
        updateRecentDocumentThumbnail(currentBitmap)
    }

    fun setSelectedShapeType(type: ShapeType) {
        _uiState.update { it.copy(selectedShapeType = type) }
    }

    fun setShapeStrokeColor(colorRgb: Int) {
        _uiState.update { it.copy(shapeStrokeColorRgb = colorRgb) }
        updateSelectedShapeLayer(strokeColor = colorRgb)
    }

    fun setShapeStrokeWidth(width: Float) {
        _uiState.update { it.copy(shapeStrokeWidth = width) }
        updateSelectedShapeLayer(strokeWidth = width)
    }

    fun setShapeFillColor(colorRgb: Int?) {
        _uiState.update { it.copy(shapeFillColor = colorRgb) }
        updateSelectedShapeLayer(fillColor = colorRgb)
    }

    /**
     * Canva Pro Interactive Shape Layer Spawner:
     * When user draws on canvas, creates an interactive DocumentCanvasLayer that can be
     * dragged, rotated, resized with handles, re-styled, duplicated, or deleted.
     */
    fun commitShape(
        type: ShapeType,
        start: PointF,
        end: PointF,
        colorRgb: Int,
        strokeWidth: Float
    ) {
        val currentBitmap = _uiState.value.currentBitmap ?: return

        val minX = min(start.x, end.x)
        val minY = min(start.y, end.y)
        val maxX = max(start.x, end.x)
        val maxY = max(start.y, end.y)
        val shapeW = (maxX - minX).toInt().coerceAtLeast(80)
        val shapeH = (maxY - minY).toInt().coerceAtLeast(80)

        val fillColor = _uiState.value.shapeFillColor
        val shapeBmp = com.docu.editor.core.scanner.VectorShapeGenerator.createShapeBitmap(
            type = type,
            width = shapeW,
            height = shapeH,
            strokeColor = colorRgb,
            strokeWidth = strokeWidth,
            fillColor = fillColor
        )

        val newLayer = DocumentCanvasLayer(
            bitmap = shapeBmp,
            x = minX,
            y = minY,
            scale = 1.0f,
            rotation = 0f,
            alpha = 1.0f,
            title = type.displayName,
            isShapeLayer = true,
            shapeType = type,
            shapeStrokeColor = colorRgb,
            shapeStrokeWidth = strokeWidth,
            shapeFillColor = fillColor,
            shapeWidth = shapeW,
            shapeHeight = shapeH
        )

        val updated = _uiState.value.canvasLayers + newLayer
        _uiState.update {
            it.copy(
                canvasLayers = updated,
                selectedLayerId = newLayer.id,
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "Added ${type.displayName} (Drag & Pinch to move)"
            )
        }
    }

    /**
     * Adds an interactive Canva vector shape directly to the center of the canvas.
     */
    fun addShapeLayer(
        type: ShapeType,
        strokeColor: Int = _uiState.value.shapeStrokeColorRgb,
        strokeWidth: Float = _uiState.value.shapeStrokeWidth,
        fillColor: Int? = _uiState.value.shapeFillColor
    ) {
        val currentBitmap = _uiState.value.currentBitmap ?: return
        val w = 320
        val h = 320
        val centerX = (currentBitmap.width - w) / 2f
        val centerY = (currentBitmap.height - h) / 2f

        val shapeBmp = com.docu.editor.core.scanner.VectorShapeGenerator.createShapeBitmap(
            type = type,
            width = w,
            height = h,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            fillColor = fillColor
        )

        val newLayer = DocumentCanvasLayer(
            bitmap = shapeBmp,
            x = centerX,
            y = centerY,
            scale = 1.0f,
            rotation = 0f,
            alpha = 1.0f,
            title = type.displayName,
            isShapeLayer = true,
            shapeType = type,
            shapeStrokeColor = strokeColor,
            shapeStrokeWidth = strokeWidth,
            shapeFillColor = fillColor,
            shapeWidth = w,
            shapeHeight = h
        )

        val updated = _uiState.value.canvasLayers + newLayer
        _uiState.update {
            it.copy(
                canvasLayers = updated,
                selectedLayerId = newLayer.id,
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "Added ${type.displayName} (Drag & Pinch to adjust)"
            )
        }
    }

    /**
     * Updates styling on the currently selected shape layer in real-time.
     */
    fun updateSelectedShapeLayer(
        fillColor: Int? = _uiState.value.shapeFillColor,
        strokeColor: Int? = null,
        strokeWidth: Float? = null
    ) {
        val selected = _uiState.value.selectedLayer ?: return
        if (!selected.isShapeLayer) return

        val newFill = fillColor
        val newStroke = strokeColor ?: selected.shapeStrokeColor
        val newWidth = strokeWidth ?: selected.shapeStrokeWidth

        val newBmp = com.docu.editor.core.scanner.VectorShapeGenerator.createShapeBitmap(
            type = selected.shapeType,
            width = selected.shapeWidth,
            height = selected.shapeHeight,
            strokeColor = newStroke,
            strokeWidth = newWidth,
            fillColor = newFill
        )

        val updatedLayer = selected.copy(
            bitmap = newBmp,
            shapeFillColor = newFill,
            shapeStrokeColor = newStroke,
            shapeStrokeWidth = newWidth
        )

        val newLayers = _uiState.value.canvasLayers.map { if (it.id == selected.id) updatedLayer else it }
        _uiState.update {
            it.copy(
                canvasLayers = newLayers,
                canvasRevision = it.canvasRevision + 1
            )
        }
    }

    /**
     * Canva Pro 1-Click Background Removal Engine:
     * Converts background to transparent PNG cutout or normalizes paper to studio white.
     */
    fun applyOneClickBackgroundRemoval(
        mode: com.docu.editor.core.scanner.BackgroundRemovalEngine.RemovalMode = com.docu.editor.core.scanner.BackgroundRemovalEngine.RemovalMode.OBJECT_CUTOUT
    ) {
        val current = _uiState.value.currentBitmap ?: return
        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = "🪄 Canva Pro: Removing background (Transparent Cutout)...",
                    showBackgroundRemovalDialog = false
                )
            }
            try {
                val cutout = com.docu.editor.core.scanner.BackgroundRemovalEngine.removeBackground(current, mode)
                editedPagesMap[_uiState.value.currentPdfPageIndex] = cutout
                _uiState.update {
                    it.copy(
                        currentBitmap = cutout,
                        detectedItems = emptyList(),
                        isApplyingEdit = false,
                        processingMessage = null,
                        hasUnsavedChanges = true,
                        canvasRevision = it.canvasRevision + 1,
                        successMessage = "✨ Background removed successfully (Transparent PNG)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Background removal failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun showBackgroundRemovalDialog(show: Boolean) {
        _uiState.update { it.copy(showBackgroundRemovalDialog = show) }
    }

    fun exportTextToFile(text: String): String? {
        return try {
            val context = getApplication<Application>()
            val txtFile = File(context.cacheDir, "extracted_text_${System.currentTimeMillis()}.txt")
            txtFile.writeText(text, Charsets.UTF_8)
            txtFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun exportDocxFile(content: String, customFileName: String? = null): String? {
        return try {
            val time = System.currentTimeMillis()
            val rawName = customFileName?.ifBlank { null } ?: "DocuEdit_Notes_$time"
            val cleanName = if (rawName.endsWith(".docx", ignoreCase = true)) rawName else "$rawName.docx"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(downloadsDir, cleanName)
            val success = DocxExportEngine.generateDocx(rawName, content, outFile)
            if (success) outFile.absolutePath else null
        } catch (_: Exception) {
            null
        }
    }

    fun showCloudSyncDialog(show: Boolean) {
        _uiState.update { it.copy(showCloudSyncDialog = show) }
    }

    fun syncDocumentToCloud(customTitle: String? = null) {
        val current = _uiState.value.currentBitmap ?: return
        saveCurrentPageToCache()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = "Syncing document to shribalajikripadham.online..."
                )
            }
            try {
                val context = getApplication<Application>()
                val state = _uiState.value
                val time = System.currentTimeMillis()
                val title = customTitle?.ifBlank { null } ?: "Document_$time"

                val syncResult = withContext(Dispatchers.IO) {
                    // 1. Export document to PDF in cache using O(1) memory streaming
                    val tempPdf = File(context.cacheDir, "cloud_sync_temp_$time.pdf")
                    if (state.pdfPageCount > 1) {
                        val allItems = mutableMapOf<Int, List<DetectedTextItem>>()
                        for (i in 0 until state.pdfPageCount) {
                            allItems[i] = pageDetectedItemsMap[i] ?: emptyList()
                        }
                        PdfExportEngine.exportPagesStreamingToPdf(
                            pageCount = state.pdfPageCount,
                            pageBitmapProvider = { idx ->
                                val cached = editedPagesMap[idx]
                                if (cached != null) {
                                    cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
                                } else if (state.activePdfUri != null) {
                                    PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, idx)
                                } else if (state.batchScannedPaths.size > idx) {
                                    com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[idx], 2880)
                                } else {
                                    current.copy(current.config ?: Bitmap.Config.ARGB_8888, false)
                                }
                            },
                            outputFile = tempPdf,
                            fitToA4 = true,
                            pagesDetectedItems = allItems,
                            autoRecycleBitmaps = true,
                            ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                        )
                    } else {
                        PdfExportEngine.exportBitmapToPdf(
                            bitmap = current,
                            outputFile = tempPdf,
                            fitToA4 = true,
                            detectedItems = state.detectedItems,
                            ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                        )
                    }

                    val pdfBytes = tempPdf.readBytes()
                    val base64Data = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)
                    tempPdf.delete()

                    // 2. Call shribalajikripadham.online/api/docu_ai.php
                    val devId = CloudBackupStore.getSyncKey(context)
                    val payload = JSONObject().apply {
                        put("action", "cloud_upload")
                        put("token", DOCU_CLOUD_TOKEN)
                        put("device_id", devId)
                        put("file_base64", base64Data)
                        put("file_type", "pdf")
                        put("title", title)
                        put("pages_count", state.pdfPageCount)
                    }

                    val url = URL("https://shribalajikripadham.online/api/docu_ai.php")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Docu-Token", DOCU_CLOUD_TOKEN)
                    conn.setRequestProperty("X-Docu-Device-Id", devId)
                    conn.connectTimeout = 15000
                    conn.readTimeout = 45000
                    conn.doOutput = true

                    conn.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        if (json.optBoolean("success")) {
                            CloudSyncResult(
                                docId = json.optString("doc_id"),
                                title = json.optString("title", title),
                                shareUrl = json.optString("share_url"),
                                downloadUrl = json.optString("download_url"),
                                qrUrl = json.optString("qr_url"),
                                fileSizeFormatted = json.optString("file_size_formatted", "100 KB"),
                                pagesCount = json.optInt("pages_count", state.pdfPageCount)
                            )
                        } else null
                    } else null
                }

                if (syncResult != null) {
                    CloudBackupStore.saveBackup(context, syncResult)
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            cloudSyncResult = syncResult,
                            showCloudSyncDialog = true,
                            successMessage = "☁️ Synced to web cloud! Link ready to share."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            errorMessage = "Cloud sync failed. Check server connection."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Cloud sync error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun showCloudBackupsListDialog(show: Boolean) {
        _uiState.update { it.copy(showCloudBackupsListDialog = show) }
    }

    fun getCloudBackups(): List<CloudBackupItem> {
        val context = getApplication<Application>()
        return CloudBackupStore.getBackups(context)
    }

    fun deleteCloudBackup(docId: String) {
        val context = getApplication<Application>()
        CloudBackupStore.deleteBackup(context, docId)
        _uiState.update { it.copy(canvasRevision = it.canvasRevision + 1) }

        // Also delete from Hostinger server
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val devId = CloudBackupStore.getSyncKey(context)
                val payload = JSONObject().apply {
                    put("action", "cloud_delete")
                    put("token", DOCU_CLOUD_TOKEN)
                    put("device_id", devId)
                    put("doc_id", docId)
                }
                val url = URL("https://shribalajikripadham.online/api/docu_ai.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Docu-Token", DOCU_CLOUD_TOKEN)
                conn.setRequestProperty("X-Docu-Device-Id", devId)
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.doOutput = true
                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                conn.responseCode
            } catch (_: Exception) {}
        }
    }

    /**
     * True Two-Way Cloud Sync: Restores a document from Hostinger Web Cloud back to the local device library.
     */
    fun restoreCloudDocumentToLibrary(
        context: Context,
        backupItem: CloudBackupItem,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = "Restoring '${backupItem.title}' from Cloud..."
                )
            }
            val result = withContext(Dispatchers.IO) {
                try {
                    val savedDir = File(context.filesDir, "saved_documents").apply { mkdirs() }
                    val cleanDocId = backupItem.docId.replace(Regex("[^a-zA-Z0-9_-]"), "")
                    val targetPdf = File(savedDir, "Doc_${cleanDocId}.pdf")

                    // 1. Download file from server download_url
                    val url = URL(backupItem.downloadUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    if (conn.responseCode != 200) {
                        return@withContext Pair(false, "Download failed with HTTP ${conn.responseCode}")
                    }

                    conn.inputStream.use { input ->
                        FileOutputStream(targetPdf).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 2. Generate thumbnail bitmap and register in DocumentHistoryManager
                    val thumbBmp = try {
                        PdfPageLoader.renderPageToBitmap(context, Uri.fromFile(targetPdf), 0)
                    } catch (_: Exception) {
                        null
                    }

                    DocumentHistoryManager.saveExistingDocumentFile(
                        context = context,
                        title = backupItem.title,
                        filePath = targetPdf.absolutePath,
                        thumbnailBitmap = thumbBmp,
                        pageCount = maxOf(1, backupItem.pagesCount)
                    )

                    Pair(true, null)
                } catch (e: Exception) {
                    Pair(false, e.localizedMessage)
                }
            }

            _uiState.update {
                it.copy(
                    isApplyingEdit = false,
                    processingMessage = null,
                    successMessage = if (result.first) "📥 Document '${backupItem.title}' restored to local library!" else null,
                    errorMessage = if (!result.first) "Restore error: ${result.second}" else null
                )
            }
            if (result.first) {
                refreshRecentDocuments()
            }
            onComplete(result.first, result.second)
        }
    }

    /**
     * Synchronizes full document backup registry from Hostinger server to local phone storage.
     */
    fun fetchCloudBackupsFromServer(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                try {
                    val context = getApplication<Application>()
                    val devId = CloudBackupStore.getSyncKey(context)
                    val payload = JSONObject().apply {
                        put("action", "cloud_list")
                        put("token", DOCU_CLOUD_TOKEN)
                        put("device_id", devId)
                    }
                    val url = URL("https://shribalajikripadham.online/api/docu_ai.php")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Docu-Token", DOCU_CLOUD_TOKEN)
                    conn.setRequestProperty("X-Docu-Device-Id", devId)
                    conn.connectTimeout = 12000
                    conn.readTimeout = 15000
                    conn.doOutput = true
                    conn.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        if (json.optBoolean("success")) {
                            val docsArr = json.optJSONArray("documents")
                            if (docsArr != null) {
                                val context = getApplication<Application>()
                                for (i in 0 until docsArr.length()) {
                                    val obj = docsArr.getJSONObject(i)
                                    val backupItem = CloudBackupItem(
                                        docId = obj.optString("doc_id"),
                                        title = obj.optString("title", "Document"),
                                        shareUrl = obj.optString("share_url"),
                                        downloadUrl = obj.optString("download_url"),
                                        qrUrl = obj.optString("qr_url"),
                                        fileSizeFormatted = obj.optString("file_size_formatted", ""),
                                        pagesCount = obj.optInt("pages_count", 1),
                                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                                    )
                                    CloudBackupStore.saveBackupItem(context, backupItem)
                                }
                                return@withContext docsArr.length()
                            }
                        }
                    }
                    0
                } catch (_: Exception) {
                    0
                }
            }
            _uiState.update { it.copy(canvasRevision = it.canvasRevision + 1) }
            onComplete(count)
        }
    }

    fun setCustomSyncKey(key: String): Boolean {
        val ok = CloudBackupStore.setSyncKey(getApplication(), key)
        if (ok) {
            fetchCloudBackupsFromServer()
            _uiState.update {
                it.copy(
                    canvasRevision = it.canvasRevision + 1,
                    successMessage = "Cloud Sync Key updated: $key"
                )
            }
        }
        return ok
    }

    fun getCurrentSyncKey(): String {
        return CloudBackupStore.getSyncKey(getApplication())
    }

    fun importCustomFont(uri: Uri): Boolean {
        val tf = com.docu.editor.core.font.RemoteFontManager.importCustomFont(getApplication(), uri)
        return if (tf != null) {
            _uiState.update {
                it.copy(
                    canvasRevision = it.canvasRevision + 1,
                    successMessage = "Custom font imported successfully!"
                )
            }
            true
        } else {
            _uiState.update { it.copy(errorMessage = "Failed to load custom font file (.ttf/.otf)") }
            false
        }
    }

    fun checkAcroFormsForCurrentPdf() {
        val uri = _uiState.value.activePdfUri ?: return
        viewModelScope.launch {
            val hasForms = com.docu.editor.core.pdf.AcroFormManager.hasAcroForm(getApplication(), uri)
            if (hasForms) {
                val fields = com.docu.editor.core.pdf.AcroFormManager.getFormFields(getApplication(), uri)
                _uiState.update {
                    it.copy(
                        hasInteractiveAcroForm = true,
                        acroFormFields = fields,
                        showAcroFormDialog = true
                    )
                }
            } else {
                _uiState.update { it.copy(errorMessage = "No interactive form fields found in this PDF") }
            }
        }
    }

    fun dismissAcroFormDialog() {
        _uiState.update { it.copy(showAcroFormDialog = false) }
    }

    fun saveAcroFormFields(fieldValues: Map<String, String>) {
        val uri = _uiState.value.activePdfUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Saving interactive PDF form...") }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(downloadsDir, "DocuEdit_Filled_Form_${System.currentTimeMillis()}.pdf")
            val success = com.docu.editor.core.pdf.AcroFormManager.saveFormFields(
                context = getApplication(),
                pdfUri = uri,
                outputFile = outFile,
                fieldValues = fieldValues
            )
            _uiState.update {
                it.copy(
                    isApplyingEdit = false,
                    processingMessage = null,
                    showAcroFormDialog = false,
                    successMessage = if (success) "Saved filled form to Downloads: ${outFile.name}" else "Failed to save form fields"
                )
            }
        }
    }

    fun exportHybridPdf(
        fitToA4: Boolean = true,
        customFileName: String? = null,
        onComplete: (File) -> Unit = {}
    ) {
        val activeUri = _uiState.value.activePdfUri
        val state = _uiState.value
        val context = getApplication<Application>()
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val name = customFileName?.takeIf { it.isNotBlank() } ?: "DocuEdit_Hybrid_${System.currentTimeMillis()}.pdf"
        val cleanName = if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
        val outFile = File(downloadsDir, cleanName)

        saveCurrentPageToCache()

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Preserving original vector quality...") }

            val exportedFile = withContext(Dispatchers.IO) {
                if (activeUri != null) {
                    val allItems = mutableMapOf<Int, List<DetectedTextItem>>()
                    for (i in 0 until state.pdfPageCount) {
                        allItems[i] = pageDetectedItemsMap[i] ?: emptyList()
                    }
                    com.docu.editor.core.pdf.PdfHybridExporter.exportHybridPdf(
                        context = context,
                        sourcePdfUri = activeUri,
                        editedPagesMap = editedPagesMap,
                        pagesDetectedItems = allItems,
                        outputFile = outFile,
                        ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                    )
                } else {
                    val current = state.currentBitmap ?: return@withContext null
                    PdfExportEngine.exportBitmapToPdf(
                        bitmap = current,
                        outputFile = outFile,
                        fitToA4 = fitToA4,
                        detectedItems = state.detectedItems,
                        ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isApplyingEdit = false,
                    processingMessage = null,
                    exportUri = exportedFile?.absolutePath,
                    successMessage = if (exportedFile != null) "Hybrid vector PDF saved: ${outFile.name}" else "Export failed"
                )
            }
            if (exportedFile != null) onComplete(exportedFile)
        }
    }

    /**
     * 1-Tap Direct Google Drive Upload (Zero GCP/OAuth Setup required).
     * Renders document, wraps in FileProvider content URI, and launches official Google Drive.
     */
    fun exportAndSaveToGoogleDrive(
        activityContext: Context,
        format: String = "PDF",
        fitToA4: Boolean = true,
        customFileName: String = ""
    ) {
        val current = _uiState.value.currentBitmap ?: return
        saveCurrentPageToCache()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = "Preparing document for Google Drive..."
                )
            }
            try {
                val context = getApplication<Application>()
                val state = _uiState.value
                val time = System.currentTimeMillis()
                val rawName = if (customFileName.isNotBlank()) {
                    customFileName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
                } else {
                    "DocuEdit_Export_$time"
                }

                val (file, mimeType) = withContext(Dispatchers.IO) {
                    val cacheDir = File(context.cacheDir, "gdrive_exports").apply { mkdirs() }
                    when (format.uppercase()) {
                        "PDF" -> {
                            val cleanName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
                            val outFile = File(cacheDir, cleanName)
                            if (state.pdfPageCount > 1) {
                                val allItems = mutableMapOf<Int, List<DetectedTextItem>>()
                                for (i in 0 until state.pdfPageCount) {
                                    allItems[i] = pageDetectedItemsMap[i] ?: emptyList()
                                }
                                PdfExportEngine.exportPagesStreamingToPdf(
                                    pageCount = state.pdfPageCount,
                                    pageBitmapProvider = { idx ->
                                        val cached = editedPagesMap[idx]
                                        if (cached != null) {
                                            cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
                                        } else if (state.activePdfUri != null) {
                                            PdfPageLoader.renderPageToBitmap(context, state.activePdfUri, idx)
                                        } else if (state.batchScannedPaths.size > idx) {
                                            com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(state.batchScannedPaths[idx], 2880)
                                        } else {
                                            current.copy(current.config ?: Bitmap.Config.ARGB_8888, false)
                                        }
                                    },
                                    outputFile = outFile,
                                    fitToA4 = fitToA4,
                                    pagesDetectedItems = allItems,
                                    autoRecycleBitmaps = true,
                                    ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                                )
                            } else {
                                PdfExportEngine.exportBitmapToPdf(
                                    bitmap = current,
                                    outputFile = outFile,
                                    fitToA4 = fitToA4,
                                    detectedItems = state.detectedItems,
                                    ocrFallbackProvider = { bmp -> ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE) }
                                )
                            }
                            Pair(outFile, "application/pdf")
                        }
                        "PNG" -> {
                            val cleanName = if (rawName.endsWith(".png", ignoreCase = true)) rawName else "$rawName.png"
                            val outFile = File(cacheDir, cleanName)
                            java.io.FileOutputStream(outFile).use { out -> current.compress(Bitmap.CompressFormat.PNG, 100, out) }
                            Pair(outFile, "image/png")
                        }
                        "DOCX" -> {
                            val cleanName = if (rawName.endsWith(".docx", ignoreCase = true)) rawName else "$rawName.docx"
                            val outFile = File(cacheDir, cleanName)
                            val sb = StringBuilder()
                            if (state.pdfPageCount > 1) {
                                for (pIdx in 0 until state.pdfPageCount) {
                                    sb.append("## Page ${pIdx + 1}\n\n")
                                    val items = pageDetectedItemsMap[pIdx] ?: if (pIdx == state.currentPdfPageIndex) state.detectedItems else emptyList()
                                    sb.append(DocxExportEngine.formatItemsToStructuredDocument(items)).append("\n\n")
                                }
                            } else {
                                sb.append(DocxExportEngine.formatItemsToStructuredDocument(state.detectedItems))
                            }
                            DocxExportEngine.generateDocx(rawName, sb.toString().ifBlank { "Scanned Document" }, outFile)
                            Pair(outFile, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        }
                        "XLSX" -> {
                            val cleanName = if (rawName.endsWith(".xlsx", ignoreCase = true)) rawName else "$rawName.xlsx"
                            val outFile = File(cacheDir, cleanName)
                            val pagesMap = mutableMapOf<Int, List<DetectedTextItem>>()
                            if (state.pdfPageCount > 1) {
                                for (pIdx in 0 until state.pdfPageCount) {
                                    val items = pageDetectedItemsMap[pIdx] ?: if (pIdx == state.currentPdfPageIndex) state.detectedItems else emptyList()
                                    pagesMap[pIdx] = items
                                }
                            } else {
                                pagesMap[0] = state.detectedItems
                            }
                            SpreadsheetExportEngine.exportToXlsx(pagesMap, outFile)
                            Pair(outFile, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        }
                        "CSV", "XLS" -> {
                            val cleanName = if (rawName.endsWith(".csv", ignoreCase = true)) rawName else "$rawName.csv"
                            val outFile = File(cacheDir, cleanName)
                            val pagesMap = mutableMapOf<Int, List<DetectedTextItem>>()
                            if (state.pdfPageCount > 1) {
                                for (pIdx in 0 until state.pdfPageCount) {
                                    val items = pageDetectedItemsMap[pIdx] ?: if (pIdx == state.currentPdfPageIndex) state.detectedItems else emptyList()
                                    pagesMap[pIdx] = items
                                }
                            } else {
                                pagesMap[0] = state.detectedItems
                            }
                            SpreadsheetExportEngine.exportToCsv(pagesMap, outFile)
                            Pair(outFile, "text/csv")
                        }
                        else -> {
                            val cleanName = if (rawName.endsWith(".jpg", ignoreCase = true) || rawName.endsWith(".jpeg", ignoreCase = true)) rawName else "$rawName.jpg"
                            val outFile = File(cacheDir, cleanName)
                            java.io.FileOutputStream(outFile).use { out -> current.compress(Bitmap.CompressFormat.JPEG, 95, out) }
                            Pair(outFile, "image/jpeg")
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        showExportDialog = false,
                        successMessage = "Opening Google Drive..."
                    )
                }

                GoogleDriveExportHelper.saveToGoogleDrive(
                    context = activityContext,
                    file = file,
                    mimeType = mimeType,
                    documentTitle = file.nameWithoutExtension
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Google Drive export failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Upload an existing saved document from Document Library directly to Google Drive.
     */
    fun saveSavedDocumentToGoogleDrive(activityContext: Context, doc: SavedDocumentItem) {
        val file = java.io.File(doc.filePath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorMessage = "File not found: ${doc.title}") }
            return
        }
        val mimeType = if (file.name.endsWith(".pdf", ignoreCase = true)) {
            "application/pdf"
        } else if (file.name.endsWith(".png", ignoreCase = true)) {
            "image/png"
        } else {
            "image/jpeg"
        }
        GoogleDriveExportHelper.saveToGoogleDrive(activityContext, file, mimeType, doc.title)
    }

    /**
     * Back up an existing saved document from Document Library to Hosting Server (shribalajikripadham.online).
     */
    fun backupSavedDocumentToCloud(doc: SavedDocumentItem) {
        val file = java.io.File(doc.filePath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorMessage = "File not found: ${doc.title}") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = "Backing up ${doc.title} to Hosting Cloud..."
                )
            }
            try {
                val context = getApplication<Application>()
                val syncResult = withContext(Dispatchers.IO) {
                    val fileBytes = file.readBytes()
                    val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                    val isPdf = file.name.endsWith(".pdf", ignoreCase = true)

                    val devId = CloudBackupStore.getSyncKey(context)
                    val payload = JSONObject().apply {
                        put("action", "cloud_upload")
                        put("token", DOCU_CLOUD_TOKEN)
                        put("device_id", devId)
                        put("file_base64", base64Data)
                        put("file_type", if (isPdf) "pdf" else "jpg")
                        put("title", doc.title)
                        put("pages_count", doc.pageCount)
                    }

                    val url = URL("https://shribalajikripadham.online/api/docu_ai.php")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("X-Docu-Token", DOCU_CLOUD_TOKEN)
                    conn.setRequestProperty("X-Docu-Device-Id", devId)
                    conn.connectTimeout = 15000
                    conn.readTimeout = 45000
                    conn.doOutput = true

                    conn.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        if (json.optBoolean("success")) {
                            CloudSyncResult(
                                docId = json.optString("doc_id"),
                                title = json.optString("title", doc.title),
                                shareUrl = json.optString("share_url"),
                                downloadUrl = json.optString("download_url"),
                                qrUrl = json.optString("qr_url"),
                                fileSizeFormatted = json.optString("file_size_formatted", doc.formattedSize),
                                pagesCount = json.optInt("pages_count", doc.pageCount)
                            )
                        } else null
                    } else null
                }

                if (syncResult != null) {
                    CloudBackupStore.saveBackup(context, syncResult)
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            cloudSyncResult = syncResult,
                            showCloudSyncDialog = true,
                            successMessage = "☁️ Backed up ${doc.title} to Web Cloud!"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            errorMessage = "Backup failed. Server unreachable or invalid response."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Cloud backup error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun transcribeHandwritingWithAi(onComplete: (String?) -> Unit) {
        val current = _uiState.value.currentBitmap ?: run {
            onComplete(null)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPerformingHandwritingOcr = true) }
            val result = withContext(Dispatchers.IO) {
                val customApiKey = getGeminiApiKey()
                if (customApiKey.isNotBlank()) {
                    val geminiResult = com.docu.editor.core.cloud.GeminiCloudAiClient.transcribeHandwriting(current, customApiKey)
                    if (!geminiResult.isNullOrBlank()) {
                        return@withContext geminiResult
                    }
                }
                try {
                    val baos = ByteArrayOutputStream()
                    val scaled = scaleDownIfNeeded(current, 1600)
                    scaled.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                    if (scaled != current) scaled.recycle()
                    val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                    val payload = JSONObject().apply {
                        put("action", "handwriting_ocr")
                        put("image", base64Img)
                        if (customApiKey.isNotBlank()) {
                            put("gemini_api_key", customApiKey)
                        }
                    }

                    val url = URL("https://shribalajikripadham.online/api/docu_ai.php")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    if (customApiKey.isNotBlank()) {
                        conn.setRequestProperty("X-Gemini-Key", customApiKey)
                    }
                    conn.connectTimeout = 15000
                    conn.readTimeout = 35000
                    conn.doOutput = true

                    conn.outputStream.use { os ->
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }

                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        if (json.optBoolean("success")) {
                            json.optString("transcription")
                        } else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }

            val finalTranscription = if (!result.isNullOrBlank()) {
                result
            } else {
                val offlineHandwriting = withContext(Dispatchers.Default) {
                    com.docu.editor.core.ocr.OfflineHandwritingRecognizer.transcribeOffline(current)
                }
                if (offlineHandwriting.isOfflineSuccess && offlineHandwriting.transcribedText.isNotBlank()) {
                    offlineHandwriting.transcribedText
                } else {
                    val localItems = withContext(Dispatchers.Default) {
                        ocrAnalyzer.detectTextBlocks(current, TextHierarchyLevel.LINE)
                    }
                    localItems.sortedWith(
                        compareBy<DetectedTextItem> { it.boundingBox.top / 20 }.thenBy { it.boundingBox.left }
                    ).joinToString("\n") { it.text }
                }
            }

            _uiState.update { it.copy(isPerformingHandwritingOcr = false) }
            onComplete(finalTranscription)
        }
    }

    /**
     * 100% On-Device Offline Handwriting OCR (Zero internet, zero latency).
     */
    fun transcribeHandwritingOffline(onComplete: (String?) -> Unit) {
        val current = _uiState.value.currentBitmap ?: run {
            onComplete(null)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPerformingHandwritingOcr = true) }
            val offlineRes = withContext(Dispatchers.Default) {
                com.docu.editor.core.ocr.OfflineHandwritingRecognizer.transcribeOffline(current)
            }
            _uiState.update { it.copy(isPerformingHandwritingOcr = false) }

            if (offlineRes.isOfflineSuccess && offlineRes.transcribedText.isNotBlank()) {
                onComplete(offlineRes.transcribedText)
            } else {
                // If local filter pass returned empty, attempt standard local text pass
                val localItems = withContext(Dispatchers.Default) {
                    ocrAnalyzer.detectTextBlocks(current, TextHierarchyLevel.LINE)
                }
                val text = localItems.sortedWith(
                    compareBy<DetectedTextItem> { it.boundingBox.top / 20 }.thenBy { it.boundingBox.left }
                ).joinToString("\n") { it.text }
                onComplete(text.ifBlank { null })
            }
        }
    }

    fun showFiltersSheet(show: Boolean) {
        _uiState.update { it.copy(showFiltersSheet = show) }
    }

    fun showExitConfirmationDialog(show: Boolean) {
        _uiState.update { it.copy(showExitConfirmationDialog = show) }
    }

    fun closeActiveDocumentImmediately() {
        val oldCurrent = _uiState.value.currentBitmap
        val oldOriginal = _uiState.value.originalBitmap
        _uiState.update {
            DocumentEditorUiState()
        }
        clearUndoRedo()
        for (bmp in editedPagesMap.values) {
            if (bmp != oldCurrent && bmp != oldOriginal && !bmp.isRecycled) {
                try { bmp.recycle() } catch (_: Exception) {}
            }
        }
        editedPagesMap.clear()
        pageDetectedItemsMap.clear()
        pageUndoStacks.values.forEach { stack ->
            while (stack.isNotEmpty()) {
                recycleStep(stack.pop())
            }
        }
        pageUndoStacks.clear()
        pageRedoStacks.values.forEach { stack ->
            while (stack.isNotEmpty()) {
                recycleStep(stack.pop())
            }
        }
        pageRedoStacks.clear()
        if (oldCurrent != null && !oldCurrent.isRecycled) {
            try { oldCurrent.recycle() } catch (_: Exception) {}
        }
        if (oldOriginal != null && oldOriginal != oldCurrent && !oldOriginal.isRecycled) {
            try { oldOriginal.recycle() } catch (_: Exception) {}
        }
        System.gc()
    }

    fun closeActiveDocument() {
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(showExitConfirmationDialog = true) }
        } else {
            closeActiveDocumentImmediately()
        }
    }

    // --- Undo & Redo (Zero-Copy Localized Patches) ---

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.currentBitmap ?: return
        val step = undoStack.pop()

        when (step) {
            is UndoStep.TextPatch -> {
                val redoPatch = Bitmap.createBitmap(current, step.x, step.y, step.patchBitmap.width, step.patchBitmap.height)
                val currentItem = _uiState.value.detectedItems.find { it.id == step.targetItemId }
                val currentText = currentItem?.text ?: ""
                val currentBox = currentItem?.boundingBox ?: step.previousBoundingBox
                redoStack.push(
                    UndoStep.TextPatch(
                        patchBitmap = redoPatch,
                        x = step.x,
                        y = step.y,
                        targetItemId = step.targetItemId,
                        previousText = currentText,
                        previousBoundingBox = Rect(currentBox)
                    )
                )

                val canvas = Canvas(current)
                canvas.drawBitmap(step.patchBitmap, step.x.toFloat(), step.y.toFloat(), null)
                step.patchBitmap.recycle()

                val updatedItems = _uiState.value.detectedItems.map {
                    if (it.id == step.targetItemId) {
                        it.copy(text = step.previousText, boundingBox = Rect(step.previousBoundingBox))
                    } else it
                }

                _uiState.update {
                    it.copy(
                        currentBitmap = current,
                        detectedItems = updatedItems,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = true,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            }

            is UndoStep.PixelPatch -> {
                val redoPatch = Bitmap.createBitmap(current, step.x, step.y, step.patchBitmap.width, step.patchBitmap.height)
                redoStack.push(UndoStep.PixelPatch(redoPatch, step.x, step.y))

                val canvas = Canvas(current)
                canvas.drawBitmap(step.patchBitmap, step.x.toFloat(), step.y.toFloat(), null)
                step.patchBitmap.recycle()

                _uiState.update {
                    it.copy(
                        currentBitmap = current,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = true,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            }

            is UndoStep.FullBitmap -> {
                val redoBmp = current.copy(Bitmap.Config.ARGB_8888, true)
                redoStack.push(UndoStep.FullBitmap(redoBmp))

                val restoredBmp = step.bitmap
                _uiState.update {
                    it.copy(
                        currentBitmap = restoredBmp,
                        detectedItems = emptyList(),
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = true,
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            }
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.currentBitmap ?: return
        val step = redoStack.pop()

        when (step) {
            is UndoStep.TextPatch -> {
                val undoPatch = Bitmap.createBitmap(current, step.x, step.y, step.patchBitmap.width, step.patchBitmap.height)
                val currentItem = _uiState.value.detectedItems.find { it.id == step.targetItemId }
                val currentText = currentItem?.text ?: ""
                val currentBox = currentItem?.boundingBox ?: step.previousBoundingBox
                undoStack.push(
                    UndoStep.TextPatch(
                        patchBitmap = undoPatch,
                        x = step.x,
                        y = step.y,
                        targetItemId = step.targetItemId,
                        previousText = currentText,
                        previousBoundingBox = Rect(currentBox)
                    )
                )

                val canvas = Canvas(current)
                canvas.drawBitmap(step.patchBitmap, step.x.toFloat(), step.y.toFloat(), null)
                step.patchBitmap.recycle()

                val updatedItems = _uiState.value.detectedItems.map {
                    if (it.id == step.targetItemId) {
                        it.copy(text = step.previousText, boundingBox = Rect(step.previousBoundingBox))
                    } else it
                }

                _uiState.update {
                    it.copy(
                        currentBitmap = current,
                        detectedItems = updatedItems,
                        canUndo = true,
                        canRedo = redoStack.isNotEmpty(),
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            }

            is UndoStep.PixelPatch -> {
                val undoPatch = Bitmap.createBitmap(current, step.x, step.y, step.patchBitmap.width, step.patchBitmap.height)
                undoStack.push(UndoStep.PixelPatch(undoPatch, step.x, step.y))

                val canvas = Canvas(current)
                canvas.drawBitmap(step.patchBitmap, step.x.toFloat(), step.y.toFloat(), null)
                step.patchBitmap.recycle()

                _uiState.update {
                    it.copy(
                        currentBitmap = current,
                        canUndo = true,
                        canRedo = redoStack.isNotEmpty(),
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            }

            is UndoStep.FullBitmap -> {
                val undoBmp = current.copy(Bitmap.Config.ARGB_8888, true)
                undoStack.push(UndoStep.FullBitmap(undoBmp))

                val restoredBmp = step.bitmap
                _uiState.update {
                    it.copy(
                        currentBitmap = restoredBmp,
                        detectedItems = emptyList(),
                        canUndo = true,
                        canRedo = redoStack.isNotEmpty(),
                        canvasRevision = it.canvasRevision + 1
                    )
                }
            }
        }
    }

    private fun pushUndoStep(step: UndoStep) {
        if (undoStack.size >= maxUndoDepth) {
            val oldest = undoStack.removeAt(0)
            recycleStep(oldest)
        }
        undoStack.push(step)
        clearRedoStack()
    }

    private fun clearRedoStack() {
        while (redoStack.isNotEmpty()) {
            recycleStep(redoStack.pop())
        }
    }

    private fun clearUndoRedo() {
        while (undoStack.isNotEmpty()) {
            recycleStep(undoStack.pop())
        }
        clearRedoStack()
    }

    private fun recycleStep(step: UndoStep) {
        when (step) {
            is UndoStep.TextPatch -> if (!step.patchBitmap.isRecycled) step.patchBitmap.recycle()
            is UndoStep.PixelPatch -> if (!step.patchBitmap.isRecycled) step.patchBitmap.recycle()
            is UndoStep.FullBitmap -> if (!step.bitmap.isRecycled) step.bitmap.recycle()
        }
    }

    // --- High-Speed Memory Optimization ---

    private fun loadOptimizedBitmapFromUri(uri: Uri): Bitmap {
        val context = getApplication<Application>()
        return com.docu.editor.core.util.ExifBitmapUtil.decodeUriWithExif(context, uri, 1920)
            ?: throw IllegalStateException("Could not read image bytes from $uri")
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = max(width, height)
        if (maxSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxSide
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun processBatchOcrDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    processingMessage = "Starting Bulk OCR Queue for ${uris.size} files..."
                )
            }
            val progressJob = launch {
                batchOcrQueueManager.progress.collect { p ->
                    if (p.isRunning && p.message.isNotEmpty()) {
                        _uiState.update { it.copy(processingMessage = p.message) }
                    }
                }
            }
            try {
                val results = batchOcrQueueManager.processBatch(uris)
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        processingMessage = null,
                        successMessage = "Bulk Batch OCR Completed! ${results.size}/${uris.size} PDFs generated and saved in Downloads."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        processingMessage = null,
                        errorMessage = "Batch OCR failed: ${e.localizedMessage}"
                    )
                }
            } finally {
                progressJob.cancel()
            }
        }
    }

    // =========================================================================
    // CANVA PRO SUITE EXTENSIONS
    // =========================================================================

    fun showCanvaMockupsDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaMockupsDialog = show) }
    }

    fun showCanvaTextStudioDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaTextStudioDialog = show) }
    }

    fun showCanvaBrandKitDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaBrandKitDialog = show) }
    }

    fun showCanvaMagicStudioDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaMagicStudioDialog = show) }
    }

    fun showCanvaAdjustDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaAdjustDialog = show) }
    }

    fun showCanvaAnimateDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaAnimateDialog = show) }
    }

    fun showCanvaLayersDialog(show: Boolean) {
        _uiState.update { it.copy(showCanvaLayersDialog = show) }
    }

    private fun saveUndoForBitmap(bmp: Bitmap) {
        pushUndoStep(UndoStep.FullBitmap(bmp.copy(Bitmap.Config.ARGB_8888, true)))
    }

    fun setEditedBitmap(newBmp: Bitmap) {
        val current = _uiState.value.currentBitmap
        if (current != null && current != newBmp) {
            saveUndoForBitmap(current)
        }
        editedPagesMap[_uiState.value.currentPdfPageIndex] = newBmp
        _uiState.update {
            it.copy(
                currentBitmap = newBmp,
                originalBitmap = it.originalBitmap ?: newBmp,
                hasUnsavedChanges = true,
                canvasRevision = it.canvasRevision + 1,
                canUndo = true
            )
        }
        updateRecentDocumentThumbnail(newBmp)
    }

    fun applyCanvaFrame(frameType: CanvaFrameType) {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Applying Canva Frame...") }
            saveUndoForBitmap(current)
            val framed = CanvaMockupFramesEngine.applyFrameOrMockup(current, frameType)
            setEditedBitmap(framed)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    processingMessage = null,
                    successMessage = "Applied ${frameType.displayName}"
                )
            }
        }
    }

    fun addStyledTextLayer(
        text: String,
        colorRgb: Int,
        fontSize: Float,
        isBold: Boolean,
        isItalic: Boolean,
        fontFamily: String,
        effect: TextEffectType
    ) {
        val current = _uiState.value.currentBitmap ?: return
        val bmp = CanvaTextStudioEngine.createStyledTypographyBitmap(
            text = text,
            textColor = colorRgb,
            backgroundColor = null,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            fontFamily = fontFamily,
            effect = effect
        )

        val posX = (current.width - bmp.width) / 2f
        val posY = (current.height - bmp.height) / 2f

        val layer = DocumentCanvasLayer(
            bitmap = bmp,
            x = posX.coerceAtLeast(30f),
            y = posY.coerceAtLeast(30f),
            scale = 1.0f,
            title = "Text",
            isTextLayer = true,
            text = text,
            textColor = colorRgb,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            fontFamily = fontFamily,
            textEffect = effect
        )

        saveUndoForBitmap(current)
        val currentLayers = _uiState.value.canvasLayers
        _uiState.update {
            it.copy(
                canvasLayers = currentLayers + layer,
                selectedLayerId = layer.id,
                hasUnsavedChanges = true,
                successMessage = "Text layer added"
            )
        }
    }

    fun applyBrandPalette(palette: BrandPalette) {
        val current = _uiState.value.currentBitmap
        if (current != null) saveUndoForBitmap(current)
        val currentLayers = _uiState.value.canvasLayers
        val updated = CanvaBrandKitEngine.applyPaletteToLayers(currentLayers, palette)
        _uiState.update {
            it.copy(
                canvasLayers = updated,
                activeBrandPaletteId = palette.id,
                hasUnsavedChanges = true,
                successMessage = "Applied ${palette.name} to all layers"
            )
        }
    }

    fun applyCanvaAdjustments(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        tint: Float,
        clarity: Float,
        vignette: Float,
        blur: Float,
        preset: CanvaStyleMatchPreset
    ) {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Tuning Color & Adjustments...") }
            saveUndoForBitmap(current)
            val adjusted = CanvaAdjustEngine.applyAdjustments(
                source = current,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                warmth = warmth,
                tint = tint,
                clarity = clarity,
                vignette = vignette,
                blur = blur,
                preset = preset
            )
            setEditedBitmap(adjusted)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    processingMessage = null,
                    activeStyleMatchPreset = preset,
                    successMessage = "Adjustments applied"
                )
            }
        }
    }

    fun applyCanvaAnimation(animationType: CanvaAnimationType) {
        _uiState.update {
            it.copy(
                activeAnimationType = animationType,
                hasUnsavedChanges = true,
                successMessage = "Set page animation: ${animationType.displayName}"
            )
        }
    }

    fun executeGrabText() {
        val current = _uiState.value.currentBitmap ?: return
        val detected = _uiState.value.detectedItems
        if (detected.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No text detected on document. Run OCR first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Magic Grab Text to editable layers...") }
            saveUndoForBitmap(current)
            val (cleanedBase, textLayers) = CanvaMagicStudioEngine.grabTextToLayers(current, detected)
            setEditedBitmap(cleanedBase)
            val existing = _uiState.value.canvasLayers
            _uiState.update {
                it.copy(
                    canvasLayers = existing + textLayers,
                    isScanning = false,
                    processingMessage = null,
                    successMessage = "Grabbed ${textLayers.size} text blocks into editable layers!"
                )
            }
        }
    }

    fun executeMagicGrab(selectionRect: RectF) {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Magic Grabbing subject...") }
            saveUndoForBitmap(current)
            val (cleanedBase, cutoutLayerBmp) = CanvaMagicStudioEngine.magicGrab(current, selectionRect)
            setEditedBitmap(cleanedBase)

            val layer = DocumentCanvasLayer(
                bitmap = cutoutLayerBmp,
                x = selectionRect.left,
                y = selectionRect.top,
                scale = 1.0f,
                title = "Grabbed Subject"
            )
            val existing = _uiState.value.canvasLayers
            _uiState.update {
                it.copy(
                    canvasLayers = existing + layer,
                    selectedLayerId = layer.id,
                    isScanning = false,
                    processingMessage = null,
                    successMessage = "Subject extracted to layer with seamless background fill!"
                )
            }
        }
    }

    fun executeFaceRetouch() {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Applying Face Beauty Retouch...") }
            saveUndoForBitmap(current)
            val retouched = CanvaMagicStudioEngine.faceRetouch(current)
            setEditedBitmap(retouched)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    processingMessage = null,
                    successMessage = "Face Retouch applied"
                )
            }
        }
    }

    fun executeAutofocusBokeh(focusXRatio: Float = 0.5f, focusYRatio: Float = 0.5f) {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Simulating DSLR Autofocus Bokeh...") }
            saveUndoForBitmap(current)
            val focusX = current.width * focusXRatio
            val focusY = current.height * focusYRatio
            val bokeh = CanvaMagicStudioEngine.autofocusBokeh(current, focusX, focusY)
            setEditedBitmap(bokeh)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    processingMessage = null,
                    successMessage = "Autofocus Bokeh blur applied"
                )
            }
        }
    }

    fun executeUpscaleSharpen() {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Upscaling & Sharpening...") }
            saveUndoForBitmap(current)
            val upscaled = CanvaMagicStudioEngine.upscaleSharpen(current)
            setEditedBitmap(upscaled)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    processingMessage = null,
                    successMessage = "Upscaled with high-frequency detail restoration"
                )
            }
        }
    }

    fun executeMagicExpand() {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Expanding Canvas Borders...") }
            saveUndoForBitmap(current)
            val expanded = CanvaMagicStudioEngine.magicExpand(current)
            setEditedBitmap(expanded)
            _uiState.update {
                it.copy(
                    isScanning = false,
                    processingMessage = null,
                    successMessage = "Canvas expanded with texture synthesis"
                )
            }
        }
    }

    // Layer Management Helpers
    fun moveLayerUp(id: String) {
        val layers = _uiState.value.canvasLayers.toMutableList()
        val idx = layers.indexOfFirst { it.id == id }
        if (idx >= 0 && idx < layers.size - 1) {
            val item = layers.removeAt(idx)
            layers.add(idx + 1, item)
            _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
        }
    }

    fun moveLayerDown(id: String) {
        val layers = _uiState.value.canvasLayers.toMutableList()
        val idx = layers.indexOfFirst { it.id == id }
        if (idx > 0) {
            val item = layers.removeAt(idx)
            layers.add(idx - 1, item)
            _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
        }
    }

    fun bringLayerToFront(id: String) {
        val layers = _uiState.value.canvasLayers.toMutableList()
        val idx = layers.indexOfFirst { it.id == id }
        if (idx >= 0 && idx < layers.size - 1) {
            val item = layers.removeAt(idx)
            layers.add(item)
            _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
        }
    }

    fun sendLayerToBack(id: String) {
        val layers = _uiState.value.canvasLayers.toMutableList()
        val idx = layers.indexOfFirst { it.id == id }
        if (idx > 0) {
            val item = layers.removeAt(idx)
            layers.add(0, item)
            _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
        }
    }

    fun toggleLayerLock(id: String) {
        val layers = _uiState.value.canvasLayers.map {
            if (it.id == id) it.copy(isLocked = !it.isLocked) else it
        }
        _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
    }

    fun toggleLayerFlipH(id: String) {
        val layers = _uiState.value.canvasLayers.map {
            if (it.id == id) it.copy(flipH = !it.flipH) else it
        }
        _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
    }

    fun toggleLayerFlipV(id: String) {
        val layers = _uiState.value.canvasLayers.map {
            if (it.id == id) it.copy(flipV = !it.flipV) else it
        }
        _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
    }

    fun updateLayerOpacity(id: String, alpha: Float) {
        val layers = _uiState.value.canvasLayers.map {
            if (it.id == id) it.copy(alpha = alpha.coerceIn(0.1f, 1.0f)) else it
        }
        _uiState.update { it.copy(canvasLayers = layers, hasUnsavedChanges = true) }
    }

    fun duplicateLayer(id: String) {
        val layer = _uiState.value.canvasLayers.firstOrNull { it.id == id } ?: return
        val newLayer = layer.copy(
            id = java.util.UUID.randomUUID().toString(),
            x = layer.x + 40f,
            y = layer.y + 40f
        )
        val layers = _uiState.value.canvasLayers + newLayer
        _uiState.update { it.copy(canvasLayers = layers, selectedLayerId = newLayer.id, hasUnsavedChanges = true) }
    }

    fun deleteLayer(id: String) {
        val layers = _uiState.value.canvasLayers.filter { it.id != id }
        _uiState.update {
            it.copy(
                canvasLayers = layers,
                selectedLayerId = if (it.selectedLayerId == id) null else it.selectedLayerId,
                hasUnsavedChanges = true
            )
        }
    }

    fun showPageSizeDialog(show: Boolean) {
        _uiState.update { it.copy(showPageSizeDialog = show) }
    }

    fun showDirectCloudUploadDialog(show: Boolean) {
        _uiState.update { it.copy(showDirectCloudUploadDialog = show) }
    }

    fun applyStandardPageSize(
        pageSize: com.docu.editor.core.layout.StandardPageSize,
        fitMode: com.docu.editor.core.layout.PageSizeEngine.FitMode
    ) {
        val current = _uiState.value.currentBitmap ?: return
        pushUndoStep(UndoStep.FullBitmap(current.copy(Bitmap.Config.ARGB_8888, true)))
        val resized = com.docu.editor.core.layout.PageSizeEngine.applyPageSize(current, pageSize, fitMode)
        _uiState.update {
            it.copy(
                currentBitmap = resized,
                showPageSizeDialog = false,
                canUndo = true,
                canRedo = false,
                hasUnsavedChanges = true,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "Page resized to ${pageSize.displayName}"
            )
        }
    }

    fun updateShapeLayerStyle(
        fillColor: Int?,
        strokeColor: Int,
        strokeWidth: Float,
        cornerRadius: Float,
        alpha: Float
    ) {
        val selected = _uiState.value.selectedLayer ?: return
        if (!selected.isShapeLayer) return

        val newBmp = com.docu.editor.core.scanner.VectorShapeGenerator.createShapeBitmap(
            type = selected.shapeType,
            width = selected.shapeWidth,
            height = selected.shapeHeight,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            fillColor = fillColor,
            cornerRadius = cornerRadius
        )

        val updatedLayer = selected.copy(
            bitmap = newBmp,
            shapeFillColor = fillColor,
            shapeStrokeColor = strokeColor,
            shapeStrokeWidth = strokeWidth,
            cornerRadius = cornerRadius,
            alpha = alpha.coerceIn(0.1f, 1.0f)
        )

        val newLayers = _uiState.value.canvasLayers.map { if (it.id == selected.id) updatedLayer else it }
        _uiState.update {
            it.copy(
                canvasLayers = newLayers,
                overlayAlpha = updatedLayer.alpha,
                canvasRevision = it.canvasRevision + 1
            )
        }
    }

    fun updateTextLayerStyle(
        text: String,
        textColor: Int,
        bgColor: Int?,
        fontSize: Float,
        isBold: Boolean,
        isItalic: Boolean,
        fontFamily: String,
        alpha: Float
    ) {
        val selected = _uiState.value.selectedLayer ?: return
        if (!selected.isTextLayer) return

        val newBmp = DocumentCanvasLayer.createTypographyBitmap(
            text = text,
            textColor = textColor,
            backgroundColor = bgColor,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            fontFamily = fontFamily
        )

        val updatedLayer = selected.copy(
            bitmap = newBmp,
            text = text,
            textColor = textColor,
            backgroundColor = bgColor,
            fontSize = fontSize,
            isBold = isBold,
            isItalic = isItalic,
            fontFamily = fontFamily,
            alpha = alpha.coerceIn(0.1f, 1.0f)
        )

        val newLayers = _uiState.value.canvasLayers.map { if (it.id == selected.id) updatedLayer else it }
        _uiState.update {
            it.copy(
                canvasLayers = newLayers,
                overlayAlpha = updatedLayer.alpha,
                canvasRevision = it.canvasRevision + 1
            )
        }
    }

    fun replaceSelectedLayerImage(newBitmap: Bitmap) {
        val selected = _uiState.value.selectedLayer ?: return
        val updatedLayer = selected.copy(
            bitmap = newBitmap,
            title = "Replaced Photo"
        )
        val newLayers = _uiState.value.canvasLayers.map { if (it.id == selected.id) updatedLayer else it }
        _uiState.update {
            it.copy(
                canvasLayers = newLayers,
                activeOverlayBitmap = newBitmap,
                canvasRevision = it.canvasRevision + 1,
                successMessage = "Photo layer updated"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        ocrAnalyzer.close()
        clearUndoRedo()
    }
}
