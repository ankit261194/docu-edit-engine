package com.docu.editor.domain.model

import android.graphics.Bitmap
import com.docu.editor.core.ocr.model.DetectedTextItem

enum class EditorToolMode {
    TEXT_EDIT,
    FILTERS,
    CROP_DESKEW,
    SIGNATURE,
    PDF_TOOLS
}

enum class DocumentFilterMode(val displayName: String) {
    ORIGINAL("Original"),
    MAGIC_COLOR("Magic Color"),
    SHADOW_REMOVER("Remove Shadow"),
    CLEAN_BW("Clean B&W"),
    GRAYSCALE("Grayscale")
}

data class DocumentEditorUiState(
    val originalBitmap: Bitmap? = null,
    val currentBitmap: Bitmap? = null,
    val detectedItems: List<DetectedTextItem> = emptyList(),
    val selectedItem: DetectedTextItem? = null,
    val isScanning: Boolean = false,
    val isApplyingEdit: Boolean = false,
    val processingMessage: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeToolMode: EditorToolMode = EditorToolMode.TEXT_EDIT,
    val activeFilter: DocumentFilterMode = DocumentFilterMode.ORIGINAL,
    val showFiltersSheet: Boolean = false,
    val showIdCardDialog: Boolean = false,
    val showSignatureDialog: Boolean = false,
    val showPdfToolboxDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val idCardFrontBitmap: Bitmap? = null,
    val idCardBackBitmap: Bitmap? = null,
    val extractedSignature: Bitmap? = null,
    val exportUri: String? = null
)
