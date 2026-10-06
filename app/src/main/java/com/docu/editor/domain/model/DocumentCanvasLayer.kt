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
    val isItalic: Boolean = false
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
         * Renders crisp vector typography into an ARGB_8888 bitmap with optional rounded background pill.
         */
        fun createTypographyBitmap(
            text: String,
            textColor: Int = android.graphics.Color.BLACK,
            backgroundColor: Int? = null,
            fontSize: Float = 36f,
            isBold: Boolean = true,
            isItalic: Boolean = false
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
                typeface = Typeface.create(Typeface.SANS_SERIF, style)
            }

            val fontMetrics = paint.fontMetrics
            val textWidth = paint.measureText(safeText)
            val textHeight = fontMetrics.descent - fontMetrics.ascent

            val padX = if (backgroundColor != null) 36f else 12f
            val padY = if (backgroundColor != null) 20f else 8f

            val totalWidth = (textWidth + padX * 2).toInt().coerceAtLeast(40)
            val totalHeight = (textHeight + padY * 2).toInt().coerceAtLeast(30)

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

            val baseline = padY - fontMetrics.ascent
            canvas.drawText(safeText, padX, baseline, paint)

            return bmp
        }
    }
}
