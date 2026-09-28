package com.docu.editor.core.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Enterprise Anti-Counterfeiting Security Watermark Engine.
 * Generates official anti-tamper watermark overlays with adjustable
 * opacity, diagonal rotation, and tiled repeating security grid.
 */
object WatermarkEngine {

    data class WatermarkConfig(
        val text: String,
        val isTiled: Boolean = true,
        val opacityPercent: Int = 25, // 10% - 80%
        val colorRgb: Int = Color.rgb(120, 120, 120),
        val rotationDegrees: Float = -35f
    )

    suspend fun applyWatermark(
        sourceBitmap: Bitmap,
        config: WatermarkConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        if (config.text.isBlank()) return@withContext sourceBitmap

        val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val alpha = (config.opacityPercent * 255 / 100).coerceIn(15, 240)
        val paintColor = Color.argb(
            alpha,
            Color.red(config.colorRgb),
            Color.green(config.colorRgb),
            Color.blue(config.colorRgb)
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = paintColor
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
        }

        val width = output.width.toFloat()
        val height = output.height.toFloat()

        if (config.isTiled) {
            // Tiled diagonal security grid across entire document
            val targetFontSize = (max(width, height) * 0.035f).coerceIn(24f, 72f)
            paint.textSize = targetFontSize

            val bounds = Rect()
            paint.getTextBounds(config.text, 0, config.text.length, bounds)
            val textWidth = max(bounds.width().toFloat(), 80f)
            val textHeight = max(bounds.height().toFloat(), 30f)

            val stepX = textWidth + 140f
            val stepY = textHeight + 180f

            // Expand bounds to ensure complete diagonal coverage when rotated
            val diagonal = kotlin.math.sqrt(width * width + height * height)
            val startX = -diagonal * 0.5f
            val endX = width + diagonal * 0.5f
            val startY = -diagonal * 0.5f
            val endY = height + diagonal * 0.5f

            var y = startY
            var row = 0
            while (y <= endY) {
                val rowOffset = if (row % 2 == 1) stepX * 0.5f else 0f
                var x = startX + rowOffset
                while (x <= endX) {
                    canvas.save()
                    canvas.rotate(config.rotationDegrees, x, y)
                    canvas.drawText(config.text, x, y, paint)
                    canvas.restore()
                    x += stepX
                }
                y += stepY
                row++
            }
        } else {
            // Single prominent diagonal watermark centered on document
            val diagonal = kotlin.math.sqrt(width * width + height * height)
            val targetFontSize = (diagonal * 0.09f).coerceIn(36f, 160f)
            paint.textSize = targetFontSize

            val centerX = width / 2f
            val centerY = height / 2f

            canvas.save()
            canvas.rotate(config.rotationDegrees, centerX, centerY)
            canvas.drawText(config.text, centerX, centerY, paint)
            canvas.restore()
        }

        output
    }
}
