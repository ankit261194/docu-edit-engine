package com.docu.editor.domain.model

import android.graphics.Bitmap
import com.docu.editor.core.ocr.model.DetectedTextItem

enum class EditorToolMode {
    TEXT_EDIT,
    LASSO_SELECT,
    ADD_TEXT,
    WHITEOUT,
    FILTERS,
    CROP_DESKEW,
    BOOK_DEWARP,
    WATERMARK,
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
    val selectedItems: List<DetectedTextItem> = emptyList(),
    val isNewTextInsertion: Boolean = false,
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
    val showWatermarkDialog: Boolean = false,
    val showBookDewarpDialog: Boolean = false,
    val idCardFrontBitmap: Bitmap? = null,
    val idCardBackBitmap: Bitmap? = null,
    val extractedSignature: Bitmap? = null,
    val activeOverlayBitmap: Bitmap? = null,
    val overlayPositionX: Float = 100f,
    val overlayPositionY: Float = 100f,
    val overlayScale: Float = 1.0f,
    val overlayRotation: Float = 0f,
    val whiteoutBrushRadius: Float = 22f,
    val hasUnsavedChanges: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val searchMatchingIndices: List<Int> = emptyList(),
    val showPagesOverviewDialog: Boolean = false,
    val showExitConfirmationDialog: Boolean = false,
    val showPasswordPromptDialog: Boolean = false,
    val pendingEncryptedPdfUri: android.net.Uri? = null,
    val currentDocHistoryId: String? = null,
    val exportUri: String? = null,
    val canvasRevision: Long = 0L,
    val pdfPageCount: Int = 1,
    val currentPdfPageIndex: Int = 0,
    val activePdfUri: android.net.Uri? = null,
    val batchScannedPaths: List<String> = emptyList(),
    val currentBatchIndex: Int = 0
)
