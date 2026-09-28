package com.docu.editor.core.ocr.model

import android.graphics.Point
import android.graphics.Rect
import androidx.compose.ui.graphics.Color

enum class TextHierarchyLevel {
    BLOCK,
    LINE,
    ELEMENT
}

enum class FontWeightEstimate {
    LIGHT,
    REGULAR,
    MEDIUM,
    BOLD,
    EXTRA_BOLD
}

data class TypographyMetrics(
    val estimatedFontWeight: FontWeightEstimate,
    val strokeWidthRatio: Float,
    val glyphDensity: Float,
    val letterSpacingEm: Float,
    val estimatedFontSizePx: Float
)

data class DetectedTextItem(
    val id: String,
    val text: String,
    val boundingBox: Rect,
    val cornerPoints: List<Point>,
    val rotationAngle: Float,
    val inkColor: Color,
    val inkColorRgb: Int,
    val typography: TypographyMetrics,
    val confidence: Float,
    val level: TextHierarchyLevel = TextHierarchyLevel.LINE
)
