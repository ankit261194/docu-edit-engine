package com.docu.editor.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docu.editor.core.cv.BackgroundInpainter
import com.docu.editor.core.font.FontMatcher
import com.docu.editor.core.ocr.OcrAnalyzer
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.TextHierarchyLevel
import com.docu.editor.core.pdf.PdfPageLoader
import com.docu.editor.core.rendering.ArtifactBlendingEngine
import com.docu.editor.core.rendering.TextRenderer
import com.docu.editor.domain.model.DocumentEditorUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack

class DocumentEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrAnalyzer = OcrAnalyzer()
    private val backgroundInpainter = BackgroundInpainter()
    private val fontMatcher = FontMatcher(application)
    private val textRenderer = TextRenderer(fontMatcher)
    private val artifactBlendingEngine = ArtifactBlendingEngine()

    private val _uiState = MutableStateFlow(DocumentEditorUiState())
    val uiState: StateFlow<DocumentEditorUiState> = _uiState.asStateFlow()

    private val undoStack = Stack<Bitmap>()

    fun loadDocumentUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, processingMessage = "Loading document...") }
            try {
                val context = getApplication<Application>()
                val mimeType = context.contentResolver.getType(uri)

                val bitmap = if (mimeType == "application/pdf") {
                    PdfPageLoader.renderPageToBitmap(context, uri)
                } else {
                    loadBitmapFromUri(uri)
                }

                setDocumentBitmap(bitmap)
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Failed to load: ${e.localizedMessage}") }
            }
        }
    }

    private suspend fun setDocumentBitmap(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                originalBitmap = bitmap,
                currentBitmap = bitmap,
                isScanning = true,
                processingMessage = "Scanning text & font geometry..."
            )
        }
        undoStack.clear()

        val items = ocrAnalyzer.detectTextBlocks(bitmap, TextHierarchyLevel.LINE)

        _uiState.update {
            it.copy(
                detectedItems = items,
                isScanning = false,
                processingMessage = null,
                canUndo = false
            )
        }
    }

    fun selectTextItem(item: DetectedTextItem?) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun applyTextReplacement(targetItem: DetectedTextItem, newText: String) {
        val currentBitmap = _uiState.value.currentBitmap ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplyingEdit = true,
                    processingMessage = "Inpainting paper texture & blending text..."
                )
            }

            try {
                val updatedBitmap = withContext(Dispatchers.Default) {
                    undoStack.push(currentBitmap.copy(Bitmap.Config.ARGB_8888, true))

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

                    val finalBlendedBitmap = artifactBlendingEngine.blendText(
                        cleanedBackground = cleanedBackground,
                        isolatedTextLayer = renderResult.isolatedTextLayer,
                        targetBounds = targetItem.boundingBox
                    )

                    renderResult.isolatedTextLayer.recycle()
                    cleanedBackground.recycle()

                    finalBlendedBitmap
                }

                val updatedItems = _uiState.value.detectedItems.map {
                    if (it.id == targetItem.id) it.copy(text = newText) else it
                }

                _uiState.update {
                    it.copy(
                        currentBitmap = updatedBitmap,
                        detectedItems = updatedItems,
                        selectedItem = null,
                        isApplyingEdit = false,
                        processingMessage = null,
                        canUndo = undoStack.isNotEmpty()
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

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.pop()
            _uiState.update {
                it.copy(
                    currentBitmap = previous,
                    canUndo = undoStack.isNotEmpty()
                )
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            bmp.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        ocrAnalyzer.close()
    }
}
