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
    val estimatedFontWeight: FontWeightEstimate = FontWeightEstimate.REGULAR,
    val strokeWidthRatio: Float = 0.12f,
    val glyphDensity: Float = 0.45f,
    val letterSpacingEm: Float = 0.05f,
    val estimatedFontSizePx: Float = 16f,
    val isSerif: Boolean = false,
    val strokeThicknessPx: Float = 2.0f,
    val numericFontWeight: Int = 400,
    val terminalFlareRatio: Float = 1.0f
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
