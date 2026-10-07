package com.docu.editor.core.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Enterprise Anti-Counterfeiting Security Watermark, Logo & Legal Bates Numbering Engine.
 * Supports:
 * - Text Watermarks with dynamic metadata macros ({DATE}, {TIME}, {PAGE}, {TOTAL_PAGES}).
 * - Tiled repeating security grid vs Single prominent center watermark.
 * - Image / Logo watermarks with custom opacity, scaling, and rotation.
 * - Legal Bates Numbering with custom prefix, digit padding, 6-zone positioning, and contrast badge.
 */
object WatermarkEngine {

    enum class WatermarkFontFamily(val displayName: String) {
        SANS_BOLD("Bold Sans"),
        SERIF("Official Serif"),
        MONOSPACE("Security Mono")
    }

    enum class BatesPosition(val displayName: String) {
        TOP_LEFT("Top Left"),
        TOP_CENTER("Top Center"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_CENTER("Bottom Center"),
        BOTTOM_RIGHT("Bottom Right")
    }

    data class WatermarkConfig(
        val text: String,
        val isTiled: Boolean = true,
        val opacityPercent: Int = 25, // 10% - 80%
        val colorRgb: Int = Color.rgb(120, 120, 120),
        val rotationDegrees: Float = -35f,
        val fontFamily: WatermarkFontFamily = WatermarkFontFamily.SANS_BOLD,
        val isOutlineOnly: Boolean = false
    )

    data class ImageWatermarkConfig(
        val logoBitmap: Bitmap,
        val isTiled: Boolean = false,
        val opacityPercent: Int = 30, // 10% - 90%
        val rotationDegrees: Float = 0f,
        val scalePercent: Int = 35 // 10% - 100% of doc width
    )

    data class BatesNumberingConfig(
        val prefix: String = "CONFIDENTIAL-",
        val startNumber: Int = 1,
        val digitCount: Int = 6,
        val suffix: String = "",
        val position: BatesPosition = BatesPosition.BOTTOM_RIGHT,
        val fontSizeSp: Float = 16f,
        val colorRgb: Int = Color.BLACK,
        val includeBackgroundBox: Boolean = true,
        val includePageCounter: Boolean = false
    )

    /**
     * Resolves dynamic template variables ({DATE}, {TIME}, {PAGE}, {TOTAL_PAGES}, {DOC_TITLE}).
     */
    fun resolveMacros(
        rawText: String,
        pageIndex: Int = 0,
        totalPages: Int = 1,
        docTitle: String = ""
    ): String {
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = Date()

        return rawText
            .replace("{DATE}", dateFormat.format(now), ignoreCase = true)
            .replace("{TIME}", timeFormat.format(now), ignoreCase = true)
            .replace("{PAGE}", (pageIndex + 1).toString(), ignoreCase = true)
            .replace("{TOTAL_PAGES}", totalPages.toString(), ignoreCase = true)
            .replace("{DOC_TITLE}", docTitle, ignoreCase = true)
    }

    suspend fun applyWatermark(
        sourceBitmap: Bitmap,
        config: WatermarkConfig,
        pageIndex: Int = 0,
        totalPages: Int = 1,
        docTitle: String = ""
    ): Bitmap = withContext(Dispatchers.Default) {
        if (config.text.isBlank()) return@withContext sourceBitmap

        val resolvedText = resolveMacros(config.text, pageIndex, totalPages, docTitle)
        if (resolvedText.isBlank()) return@withContext sourceBitmap

        val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val alpha = (config.opacityPercent * 255 / 100).coerceIn(15, 240)
        val paintColor = Color.argb(
            alpha,
            Color.red(config.colorRgb),
            Color.green(config.colorRgb),
            Color.blue(config.colorRgb)
        )

        val typeface = when (config.fontFamily) {
            WatermarkFontFamily.SANS_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            WatermarkFontFamily.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            WatermarkFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = paintColor
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            if (config.isOutlineOnly) {
                style = Paint.Style.STROKE
                strokeWidth = (output.width * 0.003f).coerceIn(2f, 8f)
            } else {
                style = Paint.Style.FILL
            }
        }

        val width = output.width.toFloat()
        val height = output.height.toFloat()

        if (config.isTiled) {
            // Tiled diagonal security grid across entire document
            val targetFontSize = (max(width, height) * 0.035f).coerceIn(24f, 80f)
            paint.textSize = targetFontSize

            val bounds = Rect()
            paint.getTextBounds(resolvedText, 0, resolvedText.length, bounds)
            val textWidth = max(bounds.width().toFloat(), 80f)
            val textHeight = max(bounds.height().toFloat(), 30f)

            val stepX = textWidth + 140f
            val stepY = textHeight + 180f

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
                    canvas.drawText(resolvedText, x, y, paint)
                    canvas.restore()
                    x += stepX
                }
                y += stepY
                row++
            }
        } else {
            // Single prominent diagonal watermark centered on document
            val diagonal = kotlin.math.sqrt(width * width + height * height)
            val targetFontSize = (diagonal * 0.085f).coerceIn(36f, 160f)
            paint.textSize = targetFontSize

            val centerX = width / 2f
            val centerY = height / 2f

            canvas.save()
            canvas.rotate(config.rotationDegrees, centerX, centerY)
            canvas.drawText(resolvedText, centerX, centerY, paint)
            canvas.restore()
        }

        output
    }

    suspend fun applyImageWatermark(
        sourceBitmap: Bitmap,
        config: ImageWatermarkConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        if (config.logoBitmap.isRecycled) return@withContext sourceBitmap

        val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val docW = output.width.toFloat()
        val docH = output.height.toFloat()

        // Calculate scaled dimensions of logo
        val targetLogoW = (docW * (config.scalePercent / 100f)).coerceAtLeast(40f)
        val aspect = config.logoBitmap.height.toFloat() / config.logoBitmap.width.toFloat()
        val targetLogoH = targetLogoW * aspect

        val scaledLogo = Bitmap.createScaledBitmap(
            config.logoBitmap,
            targetLogoW.toInt(),
            targetLogoH.toInt(),
            true
        )

        val alpha = (config.opacityPercent * 255 / 100).coerceIn(15, 240)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }

        if (config.isTiled) {
            val stepX = targetLogoW * 1.5f
            val stepY = targetLogoH * 1.5f

            val diagonal = kotlin.math.sqrt(docW * docW + docH * docH)
            val startX = -diagonal * 0.4f
            val endX = docW + diagonal * 0.4f
            val startY = -diagonal * 0.4f
            val endY = docH + diagonal * 0.4f

            var y = startY
            var row = 0
            while (y <= endY) {
                val rowOffset = if (row % 2 == 1) stepX * 0.5f else 0f
                var x = startX + rowOffset
                while (x <= endX) {
                    canvas.save()
                    canvas.rotate(config.rotationDegrees, x + targetLogoW / 2f, y + targetLogoH / 2f)
                    canvas.drawBitmap(scaledLogo, x, y, paint)
                    canvas.restore()
                    x += stepX
                }
                y += stepY
                row++
            }
        } else {
            val centerX = docW / 2f
            val centerY = docH / 2f
            val left = centerX - (targetLogoW / 2f)
            val top = centerY - (targetLogoH / 2f)

            canvas.save()
            canvas.rotate(config.rotationDegrees, centerX, centerY)
            canvas.drawBitmap(scaledLogo, left, top, paint)
            canvas.restore()
        }

        if (scaledLogo != config.logoBitmap && !scaledLogo.isRecycled) {
            scaledLogo.recycle()
        }

        output
    }

    suspend fun applyBatesNumbering(
        sourceBitmap: Bitmap,
        config: BatesNumberingConfig,
        pageOffset: Int = 0,
        totalPages: Int = 1
    ): Bitmap = withContext(Dispatchers.Default) {
        val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val docW = output.width.toFloat()
        val docH = output.height.toFloat()

        val num = config.startNumber + pageOffset
        val paddedNum = num.toString().padStart(config.digitCount, '0')
        var batesText = "${config.prefix}$paddedNum${config.suffix}"
        if (config.includePageCounter) {
            batesText += " | Page ${pageOffset + 1} of $totalPages"
        }

        val scaleFactor = (max(docW, docH) / 1200f).coerceIn(0.8f, 3.5f)
        val textSizePx = config.fontSizeSp * scaleFactor

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = config.colorRgb
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = textSizePx
        }

        val textBounds = Rect()
        textPaint.getTextBounds(batesText, 0, batesText.length, textBounds)
        val textW = textBounds.width().toFloat()
        val textH = textBounds.height().toFloat()

        val paddingX = 16f * scaleFactor
        val paddingY = 10f * scaleFactor
        val margin = 32f * scaleFactor

        val boxW = textW + (paddingX * 2f)
        val boxH = textH + (paddingY * 2f)

        // Compute box position based on BatesPosition
        val (boxLeft, boxTop) = when (config.position) {
            BatesPosition.TOP_LEFT -> Pair(margin, margin)
            BatesPosition.TOP_CENTER -> Pair((docW - boxW) / 2f, margin)
            BatesPosition.TOP_RIGHT -> Pair(docW - boxW - margin, margin)
            BatesPosition.BOTTOM_LEFT -> Pair(margin, docH - boxH - margin)
            BatesPosition.BOTTOM_CENTER -> Pair((docW - boxW) / 2f, docH - boxH - margin)
            BatesPosition.BOTTOM_RIGHT -> Pair(docW - boxW - margin, docH - boxH - margin)
        }

        val boxRect = RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH)

        if (config.includeBackgroundBox) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(235, 255, 255, 255) // Clean white contrast pill
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 148, 163, 184) // Slate border
                style = Paint.Style.STROKE
                strokeWidth = 2f * scaleFactor
            }
            val radius = 8f * scaleFactor
            canvas.drawRoundRect(boxRect, radius, radius, bgPaint)
            canvas.drawRoundRect(boxRect, radius, radius, borderPaint)
        }

        val textX = boxLeft + paddingX
        val textY = boxTop + paddingY + textH - (textBounds.bottom.toFloat())

        canvas.drawText(batesText, textX, textY, textPaint)

        output
    }
}
