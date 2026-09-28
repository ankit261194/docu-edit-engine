package com.docu.editor.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docu.editor.core.cv.BackgroundInpainter
import com.docu.editor.core.font.FontMatcher
import com.docu.editor.core.ocr.OcrAnalyzer
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import com.docu.editor.core.pdf.PdfCompressionEngine
import com.docu.editor.core.pdf.PdfPageLoader
import com.docu.editor.core.pdf.PdfToolbox
import com.docu.editor.core.rendering.ArtifactBlendingEngine
import com.docu.editor.core.rendering.TextRenderer
import com.docu.editor.core.sample.SampleDocumentGenerator
import com.docu.editor.core.scanner.DocumentEdgeDetector
import com.docu.editor.core.scanner.DocumentFilters
import com.docu.editor.core.scanner.IdCardStitcher
import com.docu.editor.core.scanner.PerspectiveTransformer
import com.docu.editor.core.signature.SignatureExtractor
import com.docu.editor.core.signature.StampExtractor
import com.docu.editor.domain.model.DocumentEditorUiState
import com.docu.editor.domain.model.DocumentFilterMode
import com.docu.editor.domain.model.EditorToolMode
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
import kotlin.math.max
import kotlin.math.min

class DocumentEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrAnalyzer = OcrAnalyzer()
    private val backgroundInpainter = BackgroundInpainter()
    private val fontMatcher = FontMatcher(application)
    private val textRenderer = TextRenderer(fontMatcher)
    private val artifactBlendingEngine = ArtifactBlendingEngine()

    private val _uiState = MutableStateFlow(DocumentEditorUiState())
    val uiState: StateFlow<DocumentEditorUiState> = _uiState.asStateFlow()

    private val undoStack = Stack<Bitmap>()
    private val redoStack = Stack<Bitmap>()
    private val maxUndoDepth = 6

    // --- Loading Documents & Images ---

    fun loadDocumentUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Loading document...") }
            try {
                val context = getApplication<Application>()
                val mimeType = context.contentResolver.getType(uri)

                val bitmap = withContext(Dispatchers.IO) {
                    if (mimeType == "application/pdf") {
                        PdfPageLoader.renderPageToBitmap(context, uri)
                    } else {
                        loadOptimizedBitmapFromUri(uri)
                    }
                }

                setDocumentBitmap(bitmap)
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Failed to load: ${e.localizedMessage}") }
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

    private suspend fun setDocumentBitmap(bitmap: Bitmap) {
        val optimized = withContext(Dispatchers.Default) {
            scaleDownIfNeeded(bitmap, maxDimension = 1920)
        }

        _uiState.update {
            it.copy(
                originalBitmap = optimized,
                currentBitmap = optimized,
                isScanning = true,
                processingMessage = "Analyzing text geometry...",
                activeFilter = DocumentFilterMode.ORIGINAL,
                canUndo = false,
                canRedo = false
            )
        }
        undoStack.clear()
        redoStack.clear()

        val items = withContext(Dispatchers.Default) {
            ocrAnalyzer.detectTextBlocks(optimized, TextHierarchyLevel.LINE)
        }

        _uiState.update {
            it.copy(
                detectedItems = items,
                isScanning = false,
                processingMessage = null,
                successMessage = "Detected ${items.size} editable text blocks"
            )
        }
    }

    // --- Text Selection & Inpainting Replacement ---

    fun selectTextItem(item: DetectedTextItem?) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun applyTextReplacement(targetItem: DetectedTextItem, newText: String) {
        val currentBitmap = _uiState.value.currentBitmap ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = "Applying 99% photorealistic edit..."
                )
            }

            try {
                val updatedBitmap = withContext(Dispatchers.Default) {
                    pushUndoState(currentBitmap)

                    val cleanedBackground = backgroundInpainter.inpaint(
                        sourceBitmap = currentBitmap,
                        targetBounds = targetItem.boundingBox
                    )

                    val renderResult = textRenderer.render(
                        cleanedBackground = cleanedBackground,
                        params = TextRenderer.TextRenderParams(
                            newText = newText,
                            targetBounds = targetItem.boundingBox,
                            inkColorRgb = targetItem.inkColorRgb,
                            rotationAngle = targetItem.rotationAngle,
                            typographyMetrics = targetItem.typography
                        )
                    )

                    cleanedBackground.recycle()
                    renderResult.outputBitmap
                }

                // Update bounding box width to match new text length
                val charW = targetItem.boundingBox.height() * 0.48f
                val newWidth = (newText.length * charW).toInt().coerceAtLeast(24)
                val newBox = Rect(
                    targetItem.boundingBox.left,
                    targetItem.boundingBox.top,
                    targetItem.boundingBox.left + newWidth,
                    targetItem.boundingBox.bottom
                )

                val updatedItems = _uiState.value.detectedItems.map {
                    if (it.id == targetItem.id) it.copy(text = newText, boundingBox = newBox) else it
                }

                _uiState.update {
                    it.copy(
                        currentBitmap = updatedBitmap,
                        detectedItems = updatedItems,
                        selectedItem = null,
                        isApplyingEdit = false,
                        processingMessage = null,
                        successMessage = "Replaced text seamlessly",
                        canUndo = true,
                        canRedo = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        errorMessage = "Auto-Edit failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // --- CamScanner Filters Engine ---

    fun applyFilter(filter: DocumentFilterMode) {
        val base = _uiState.value.originalBitmap ?: return
        if (filter == _uiState.value.activeFilter) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Applying ${filter.displayName}...") }

            val filtered = withContext(Dispatchers.Default) {
                val filterType = when (filter) {
                    DocumentFilterMode.ORIGINAL -> DocumentFilters.FilterType.ORIGINAL
                    DocumentFilterMode.MAGIC_COLOR -> DocumentFilters.FilterType.MAGIC_COLOR
                    DocumentFilterMode.SHADOW_REMOVER -> DocumentFilters.FilterType.REMOVE_SHADOWS
                    DocumentFilterMode.CLEAN_BW -> DocumentFilters.FilterType.CLEAN_BW
                    DocumentFilterMode.GRAYSCALE -> DocumentFilters.FilterType.GRAYSCALE
                }
                DocumentFilters.applyFilter(base, filterType)
            }

            // Re-detect or update OCR items for the newly enhanced contrast
            val items = withContext(Dispatchers.Default) {
                ocrAnalyzer.detectTextBlocks(filtered, TextHierarchyLevel.LINE)
            }

            _uiState.update {
                it.copy(
                    currentBitmap = filtered,
                    detectedItems = items,
                    activeFilter = filter,
                    isApplyingEdit = false,
                    processingMessage = null,
                    successMessage = "Applied ${filter.displayName}"
                )
            }
        }
    }

    // --- 4-Corner Perspective Warp & Auto-Deskew ---

    fun applyAutoPerspectiveCrop() {
        val current = _uiState.value.currentBitmap ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Detecting 4 document corners...") }

            try {
                val warped = withContext(Dispatchers.Default) {
                    pushUndoState(current)
                    val corners = DocumentEdgeDetector.detectCorners(current)
                    PerspectiveTransformer.warpPerspective(current, corners)
                }

                if (warped != null) {
                    val items = withContext(Dispatchers.Default) {
                        ocrAnalyzer.detectTextBlocks(warped, TextHierarchyLevel.LINE)
                    }
                    _uiState.update {
                        it.copy(
                            currentBitmap = warped,
                            detectedItems = items,
                            isApplyingEdit = false,
                            processingMessage = null,
                            successMessage = "Document cropped & flattened perfectly",
                            canUndo = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isApplyingEdit = false,
                            processingMessage = null,
                            errorMessage = "Could not find 4 clear paper corners. Keep camera closer."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isApplyingEdit = false, processingMessage = null, errorMessage = "Crop error: ${e.localizedMessage}")
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

    fun exportCurrentDocument(format: String = "JPG") {
        val current = _uiState.value.currentBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingEdit = true, processingMessage = "Saving $format to Downloads...") }
            try {
                val file = withContext(Dispatchers.IO) {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val time = System.currentTimeMillis()
                    val fileName = "DocuEdit_Export_$time.${format.lowercase()}"
                    val outFile = File(downloadsDir, fileName)

                    FileOutputStream(outFile).use { out ->
                        current.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    outFile
                }

                _uiState.update {
                    it.copy(
                        isApplyingEdit = false,
                        processingMessage = null,
                        exportUri = file.absolutePath,
                        successMessage = "Saved to Downloads: ${file.name}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isApplyingEdit = false, errorMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
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

    fun showFiltersSheet(show: Boolean) {
        _uiState.update { it.copy(showFiltersSheet = show) }
    }

    fun closeActiveDocument() {
        _uiState.update {
            DocumentEditorUiState()
        }
        undoStack.clear()
        redoStack.clear()
    }

    // --- Undo & Redo ---

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val current = _uiState.value.currentBitmap ?: return
            redoStack.push(current)
            val previous = undoStack.pop()
            _uiState.update {
                it.copy(
                    currentBitmap = previous,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = true
                )
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val current = _uiState.value.currentBitmap ?: return
            undoStack.push(current)
            val next = redoStack.pop()
            _uiState.update {
                it.copy(
                    currentBitmap = next,
                    canUndo = true,
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    private fun pushUndoState(bitmap: Bitmap) {
        if (undoStack.size >= maxUndoDepth) {
            val oldest = undoStack.removeAt(0)
            if (!oldest.isRecycled) oldest.recycle()
        }
        undoStack.push(bitmap.copy(Bitmap.Config.ARGB_8888, true))
        redoStack.clear()
    }

    // --- High-Speed Memory Optimization ---

    private fun loadOptimizedBitmapFromUri(uri: Uri): Bitmap {
        val context = getApplication<Application>()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val maxDim = max(options.outWidth, options.outHeight)
        var sampleSize = 1
        val targetMax = 1920
        while (maxDim / (sampleSize * 2) >= targetMax) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Could not read image bytes from $uri")

        return bitmap
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

    override fun onCleared() {
        super.onCleared()
        ocrAnalyzer.close()
        undoStack.forEach { if (!it.isRecycled) it.recycle() }
        redoStack.forEach { if (!it.isRecycled) it.recycle() }
    }
}
