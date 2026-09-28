package com.docu.editor.domain.model

import android.graphics.Bitmap
import com.docu.editor.core.ocr.model.DetectedTextItem

data class DocumentEditorUiState(
    val originalBitmap: Bitmap? = null,
    val currentBitmap: Bitmap? = null,
    val detectedItems: List<DetectedTextItem> = emptyList(),
    val selectedItem: DetectedTextItem? = null,
    val isScanning: Boolean = false,
    val isApplyingEdit: Boolean = false,
    val processingMessage: String? = null,
    val errorMessage: String? = null,
    val canUndo: Boolean = false
)
