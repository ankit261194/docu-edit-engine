package com.docu.editor.domain.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint

/**
 * Canva Pro Style Interactive Layer Element.
 * Supports multiple concurrent photo, logo, badge, and sticker overlays
 * AS WELL AS live editable vector typography layers with individual drag,
 * pinch-to-scale, rotate, reordering, styling, and opacity control.
 */
data class DocumentCanvasLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val x: Float = 60f,
    val y: Float = 100f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val alpha: Float = 1.0f,
    val title: String = "Layer",
    val isTextLayer: Boolean = false,
    val text: String = "",
    val textColor: Int = android.graphics.Color.BLACK,
    val backgroundColor: Int? = null,
    val fontSize: Float = 36f,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val fontFamily: String = "Sans-Serif",
    val isShapeLayer: Boolean = false,
    val shapeType: ShapeType = ShapeType.RECTANGLE,
    val shapeStrokeColor: Int = android.graphics.Color.rgb(220, 38, 38),
    val shapeStrokeWidth: Float = 6f,
    val shapeFillColor: Int? = null,
    val shapeWidth: Int = 300,
    val shapeHeight: Int = 300,
    val isLocked: Boolean = false,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val shadowRadius: Float = 0f,
    val shadowColor: Int = android.graphics.Color.argb(100, 0, 0, 0),
    val cornerRadius: Float = 0f,
    val arrowHeadSize: Float = 36f,
    val arrowStemWidth: Float = 6f,
    val frameType: CanvaFrameType = CanvaFrameType.NONE,
    val textEffect: TextEffectType = TextEffectType.NONE,
    val animationType: CanvaAnimationType = CanvaAnimationType.NONE,
    val letterSpacingEm: Float = 0.04f,
    val lineHeightMultiplier: Float = 1.2f
) {
    fun hitTest(docX: Float, docY: Float): Boolean {
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val centerX = x + drawW / 2f
        val centerY = y + drawH / 2f
        val rad = -Math.toRadians(rotation.toDouble())
        val cos = Math.cos(rad)
        val sin = Math.sin(rad)
        val dx = docX - centerX
        val dy = docY - centerY
        val unrotX = (centerX + (dx * cos - dy * sin)).toFloat()
        val unrotY = (centerY + (dx * sin + dy * cos)).toFloat()

        val left = x
        val right = x + drawW
        val top = y
        val bottom = y + drawH
        return unrotX in left..right && unrotY in top..bottom
    }

    companion object {
        /**
         * Renders crisp vector typography into an ARGB_8888 bitmap with optional rounded background pill,
         * multi-line leading support, and custom letter spacing tracking.
         */
        fun createTypographyBitmap(
            text: String,
            textColor: Int = android.graphics.Color.BLACK,
            backgroundColor: Int? = null,
            fontSize: Float = 36f,
            isBold: Boolean = true,
            isItalic: Boolean = false,
            fontFamily: String = "Sans-Serif",
            letterSpacingEm: Float = 0.04f,
            lineHeightMultiplier: Float = 1.2f
        ): Bitmap {
            val safeText = if (text.isBlank()) "Type Here" else text

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = textColor
                textSize = fontSize
                val style = when {
                    isBold && isItalic -> Typeface.BOLD_ITALIC
                    isBold -> Typeface.BOLD
                    isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                val base = when (fontFamily) {
                    "Serif" -> Typeface.SERIF
                    "Monospace" -> Typeface.MONOSPACE
                    "Cursive" -> Typeface.create("cursive", Typeface.NORMAL)
                    else -> Typeface.SANS_SERIF
                }
                typeface = Typeface.create(base, style)
                letterSpacing = letterSpacingEm
            }

            val lines = safeText.split("\n")
            val fontMetrics = paint.fontMetrics
            val singleLineHeight = fontMetrics.descent - fontMetrics.ascent
            val lineStep = singleLineHeight * lineHeightMultiplier.coerceIn(0.7f, 2.5f)

            var maxLineWidth = 0f
            for (line in lines) {
                val w = paint.measureText(line)
                if (w > maxLineWidth) maxLineWidth = w
            }

            val padX = if (backgroundColor != null) 36f else 12f
            val padY = if (backgroundColor != null) 20f else 8f

            val totalContentHeight = if (lines.size <= 1) {
                singleLineHeight
            } else {
                (lines.size - 1) * lineStep + singleLineHeight
            }

            val totalWidth = (maxLineWidth + padX * 2).toInt().coerceAtLeast(40)
            val totalHeight = (totalContentHeight + padY * 2).toInt().coerceAtLeast(30)

            val bmp = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            if (backgroundColor != null) {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = backgroundColor
                    this.style = Paint.Style.FILL
                }
                val rect = RectF(0f, 0f, totalWidth.toFloat(), totalHeight.toFloat())
                val cornerRadius = 14f
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
            }

            var currentBaseline = padY - fontMetrics.ascent
            for (line in lines) {
                canvas.drawText(line, padX, currentBaseline, paint)
                currentBaseline += lineStep
            }

            return bmp
        }
    }
}
