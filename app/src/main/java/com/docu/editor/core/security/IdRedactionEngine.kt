package com.docu.editor.core.security

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.docu.editor.core.ocr.model.DetectedTextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enterprise ID & Sensitive Document Auto-Redaction Engine.
 * Features:
 * - Aadhaar Card 12-digit detection & legal masking (first 8 digits masked: XXXX XXXX 1234).
 * - PAN Card 10-char alphanumeric detection & privacy box redaction.
 * - Credit/Debit Card 16-digit detection.
 * - Solid Blackout Box, Mosaic Pixelation, or Masked Typography.
 */
object IdRedactionEngine {

    enum class RedactionMode {
        AADHAAR_MASK,      // Mask first 8 digits: XXXX XXXX 1234
        SOLID_BLACKOUT,    // Solid black confidentiality box
        PIXELATE_BLUR      // Gaussian mosaic pixelation
    }

    data class RedactionResult(
        val redactedBitmap: Bitmap,
        val redactedCount: Int,
        val details: List<String>
    )

    // Regex patterns for sensitive data
    private val AADHAAR_REGEX = Regex("""\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b""")
    private val PAN_REGEX = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
    private val CARD_REGEX = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b""")

    suspend fun autoRedactSensitiveData(
        sourceBitmap: Bitmap,
        detectedItems: List<DetectedTextItem>,
        mode: RedactionMode = RedactionMode.AADHAAR_MASK
    ): RedactionResult = withContext(Dispatchers.Default) {
        val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        var count = 0
        val details = mutableListOf<String>()

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }

        val maskTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }

        val whiteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        for (item in detectedItems) {
            val text = item.text.trim()

            // 1. Check Aadhaar Card Pattern
            if (AADHAAR_REGEX.containsMatchIn(text)) {
                count++
                details.add("Aadhaar Card: $text")
                applyRedaction(canvas, output, item.boundingBox, mode, isAadhaar = true, text = text)
                continue
            }

            // 2. Check PAN Card Pattern
            if (PAN_REGEX.containsMatchIn(text)) {
                count++
                details.add("PAN Card: $text")
                applyRedaction(canvas, output, item.boundingBox, mode, isAadhaar = false, text = text)
                continue
            }

            // 3. Check Payment Card Pattern
            if (CARD_REGEX.containsMatchIn(text)) {
                count++
                details.add("Card Number: $text")
                applyRedaction(canvas, output, item.boundingBox, mode, isAadhaar = false, text = text)
            }
        }

        RedactionResult(
            redactedBitmap = output,
            redactedCount = count,
            details = details
        )
    }

    private fun applyRedaction(
        canvas: Canvas,
        bitmap: Bitmap,
        box: Rect,
        mode: RedactionMode,
        isAadhaar: Boolean,
        text: String
    ) {
        val safeBox = Rect(
            box.left.coerceAtLeast(0),
            box.top.coerceAtLeast(0),
            box.right.coerceAtMost(bitmap.width),
            box.bottom.coerceAtMost(bitmap.height)
        )
        if (safeBox.width() <= 0 || safeBox.height() <= 0) return

        when (mode) {
            RedactionMode.SOLID_BLACKOUT -> {
                val pad = 4f
                val r = RectF(safeBox.left - pad, safeBox.top - pad, safeBox.right + pad, safeBox.bottom + pad)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(r, 6f, 6f, paint)
            }

            RedactionMode.AADHAAR_MASK -> {
                // Erase background area with white
                val r = RectF(safeBox)
                val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
                canvas.drawRect(r, bgPaint)

                // Draw masked text: "XXXX XXXX " + last 4 digits
                val cleanDigits = text.replace(" ", "").replace("-", "")
                val last4 = if (cleanDigits.length >= 4) cleanDigits.takeLast(4) else "XXXX"
                val maskedStr = "XXXX XXXX $last4"

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = (safeBox.height() * 0.78f).coerceAtLeast(18f)
                    isFakeBoldText = true
                }
                val baseline = safeBox.bottom - (safeBox.height() * 0.20f)
                canvas.drawText(maskedStr, safeBox.left.toFloat(), baseline, textPaint)
            }

            RedactionMode.PIXELATE_BLUR -> {
                pixelateRect(bitmap, safeBox, pixelSize = 14)
            }
        }
    }

    private fun pixelateRect(bitmap: Bitmap, box: Rect, pixelSize: Int) {
        val w = box.width()
        val h = box.height()
        if (w <= 0 || h <= 0) return

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, box.left, box.top, w, h)

        for (y in 0 until h step pixelSize) {
            for (x in 0 until w step pixelSize) {
                // Compute average block color
                var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                val blockW = (pixelSize).coerceAtMost(w - x)
                val blockH = (pixelSize).coerceAtMost(h - y)

                for (by in 0 until blockH) {
                    for (bx in 0 until blockW) {
                        val p = pixels[(y + by) * w + (x + bx)]
                        sumR += (p shr 16) and 0xFF
                        sumG += (p shr 8) and 0xFF
                        sumB += p and 0xFF
                        count++
                    }
                }

                if (count > 0) {
                    val avgColor = Color.rgb(sumR / count, sumG / count, sumB / count)
                    for (by in 0 until blockH) {
                        for (bx in 0 until blockW) {
                            pixels[(y + by) * w + (x + bx)] = avgColor
                        }
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, w, box.left, box.top, w, h)
    }
}
