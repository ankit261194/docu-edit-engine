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
 * - Aadhaar Card 12-digit detection & legal masking (first 8 digits masked: XXXX XXXX 1234) complying with UIDAI guidelines.
 * - PAN Card 10-char alphanumeric detection & privacy box redaction.
 * - Credit/Debit Card 16-digit / 15-digit detection.
 * - Passport, Voter ID (EPIC), and Driving License detection.
 * - Mobile Phone & Email Address privacy redaction.
 * - Solid Blackout Box, Mosaic Pixelation, Clean Erasure, or Masked Typography.
 * - Background color auto-sampling for seamless masked typography.
 */
object IdRedactionEngine {

    enum class RedactionMode {
        AADHAAR_MASK,      // Mask first 8 digits: XXXX XXXX 1234
        SOLID_BLACKOUT,    // Solid black confidentiality box
        PIXELATE_BLUR,     // Adaptive mosaic pixelation
        WHITE_ERASURE      // Clean background erase
    }

    data class RedactionOptions(
        val mode: RedactionMode = RedactionMode.AADHAAR_MASK,
        val redactAadhaar: Boolean = true,
        val redactPan: Boolean = true,
        val redactCards: Boolean = true,
        val redactPassport: Boolean = true,
        val redactVoterId: Boolean = true,
        val redactDrivingLicense: Boolean = true,
        val redactPhone: Boolean = false,
        val redactEmail: Boolean = false,
        val redactSsn: Boolean = false
    )

    data class RedactionResult(
        val redactedBitmap: Bitmap,
        val redactedCount: Int,
        val details: List<String>
    )

    // Regex patterns for sensitive documents & credentials
    private val AADHAAR_REGEX = Regex("""\b[2-9]\d{3}\s?\d{4}\s?\d{4}\b""")
    private val PAN_REGEX = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
    private val CARD_REGEX = Regex("""\b(?:\d{4}[-\s]?){3}\d{4}\b|\b\d{4}[-\s]?\d{6}[-\s]?\d{5}\b""")
    private val PASSPORT_REGEX = Regex("""\b[A-PR-WYa-pr-wy][1-9]\d{7}\b""")
    private val VOTER_ID_REGEX = Regex("""\b[A-Z]{3}[0-9]{7}\b""")
    private val DRIVING_LICENSE_REGEX = Regex("""\b[A-Z]{2}[-\s]?[0-9]{2}[-\s]?(?:19|20)\d{2}[-\s]?[0-9]{7}\b|\b[A-Z]{2}\d{2}\s?\d{11}\b""")
    private val PHONE_REGEX = Regex("""\b(?:\+91[\-\s]?|0)?[6-9]\d{9}\b""")
    private val EMAIL_REGEX = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    private val SSN_REGEX = Regex("""\b\d{3}[-\s]\d{2}[-\s]\d{4}\b""")

    suspend fun autoRedactSensitiveData(
        sourceBitmap: Bitmap,
        detectedItems: List<DetectedTextItem>,
        mode: RedactionMode = RedactionMode.AADHAAR_MASK
    ): RedactionResult = autoRedactSensitiveData(
        sourceBitmap = sourceBitmap,
        detectedItems = detectedItems,
        options = RedactionOptions(mode = mode)
    )

    suspend fun autoRedactSensitiveData(
        sourceBitmap: Bitmap,
        detectedItems: List<DetectedTextItem>,
        options: RedactionOptions
    ): RedactionResult = withContext(Dispatchers.Default) {
        val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        var count = 0
        val details = mutableListOf<String>()

        for (item in detectedItems) {
            val text = item.text.trim()
            if (text.isEmpty()) continue

            var matchedType: String? = null
            var maskedReplacement: String? = null

            // 1. Aadhaar Card Pattern
            if (options.redactAadhaar && AADHAAR_REGEX.containsMatchIn(text)) {
                matchedType = "Aadhaar Card"
                val match = AADHAAR_REGEX.find(text)?.value ?: text
                val digitsOnly = match.filter { it.isDigit() }
                val last4 = if (digitsOnly.length >= 4) digitsOnly.takeLast(4) else "XXXX"
                maskedReplacement = "XXXX XXXX $last4"
            }
            // 2. PAN Card Pattern
            else if (options.redactPan && PAN_REGEX.containsMatchIn(text)) {
                matchedType = "PAN Card"
                val match = PAN_REGEX.find(text)?.value ?: text
                val last4 = if (match.length >= 4) match.takeLast(4) else "XXXX"
                maskedReplacement = "XXXXXX$last4"
            }
            // 3. Payment Card Pattern
            else if (options.redactCards && CARD_REGEX.containsMatchIn(text)) {
                matchedType = "Payment Card"
                val match = CARD_REGEX.find(text)?.value ?: text
                val digitsOnly = match.filter { it.isDigit() }
                val last4 = if (digitsOnly.length >= 4) digitsOnly.takeLast(4) else "XXXX"
                maskedReplacement = "XXXX-XXXX-XXXX-$last4"
            }
            // 4. Passport Pattern
            else if (options.redactPassport && PASSPORT_REGEX.containsMatchIn(text)) {
                matchedType = "Passport"
                val match = PASSPORT_REGEX.find(text)?.value ?: text
                val last3 = if (match.length >= 3) match.takeLast(3) else "XXX"
                maskedReplacement = "XXXXX$last3"
            }
            // 5. Voter ID Pattern
            else if (options.redactVoterId && VOTER_ID_REGEX.containsMatchIn(text)) {
                matchedType = "Voter ID"
                val match = VOTER_ID_REGEX.find(text)?.value ?: text
                val last4 = if (match.length >= 4) match.takeLast(4) else "XXXX"
                maskedReplacement = "XXXXXX$last4"
            }
            // 6. Driving License Pattern
            else if (options.redactDrivingLicense && DRIVING_LICENSE_REGEX.containsMatchIn(text)) {
                matchedType = "Driving License"
                val match = DRIVING_LICENSE_REGEX.find(text)?.value ?: text
                val last4 = if (match.length >= 4) match.takeLast(4) else "XXXX"
                maskedReplacement = "DL-XXXXXX-$last4"
            }
            // 7. US SSN Pattern
            else if (options.redactSsn && SSN_REGEX.containsMatchIn(text)) {
                matchedType = "SSN"
                val match = SSN_REGEX.find(text)?.value ?: text
                val last4 = if (match.length >= 4) match.takeLast(4) else "XXXX"
                maskedReplacement = "XXX-XX-$last4"
            }
            // 8. Phone Number
            else if (options.redactPhone && PHONE_REGEX.containsMatchIn(text)) {
                matchedType = "Phone Number"
                val match = PHONE_REGEX.find(text)?.value ?: text
                val last4 = if (match.length >= 4) match.takeLast(4) else "XXXX"
                maskedReplacement = "XXXXXX$last4"
            }
            // 9. Email Address
            else if (options.redactEmail && EMAIL_REGEX.containsMatchIn(text)) {
                matchedType = "Email"
                val match = EMAIL_REGEX.find(text)?.value ?: text
                val parts = match.split("@")
                maskedReplacement = if (parts.size == 2) "${parts[0].take(1)}***@${parts[1]}" else "******"
            }

            if (matchedType != null) {
                count++
                details.add("$matchedType ($text)")
                applyRedaction(
                    canvas = canvas,
                    bitmap = output,
                    box = item.boundingBox,
                    mode = options.mode,
                    replacementText = maskedReplacement ?: "XXXXXXXX"
                )
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
        replacementText: String
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
                val sampledBg = sampleBackgroundColor(bitmap, safeBox)
                val r = RectF(safeBox)
                val bgPaint = Paint().apply {
                    color = sampledBg
                    style = Paint.Style.FILL
                }
                canvas.drawRect(r, bgPaint)

                val textColor = if (isColorDark(sampledBg)) Color.WHITE else Color.BLACK
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = (safeBox.height() * 0.72f).coerceIn(16f, 64f)
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                val baseline = safeBox.centerY() + (textPaint.textSize * 0.35f)
                canvas.drawText(replacementText, safeBox.centerX().toFloat(), baseline, textPaint)
            }

            RedactionMode.WHITE_ERASURE -> {
                val sampledBg = sampleBackgroundColor(bitmap, safeBox)
                val r = RectF(safeBox)
                val bgPaint = Paint().apply {
                    color = sampledBg
                    style = Paint.Style.FILL
                }
                canvas.drawRect(r, bgPaint)
            }

            RedactionMode.PIXELATE_BLUR -> {
                val adaptiveBlock = (safeBox.height() / 4).coerceIn(8, 24)
                pixelateRect(bitmap, safeBox, pixelSize = adaptiveBlock)
            }
        }
    }

    private fun sampleBackgroundColor(bitmap: Bitmap, box: Rect): Int {
        val samplePoints = listOf(
            Pair((box.left - 4).coerceAtLeast(0), (box.top - 4).coerceAtLeast(0)),
            Pair((box.right + 4).coerceAtMost(bitmap.width - 1), (box.top - 4).coerceAtLeast(0)),
            Pair((box.left - 4).coerceAtLeast(0), (box.bottom + 4).coerceAtMost(bitmap.height - 1)),
            Pair((box.right + 4).coerceAtMost(bitmap.width - 1), (box.bottom + 4).coerceAtMost(bitmap.height - 1))
        )
        var totalR = 0; var totalG = 0; var totalB = 0; var sampleCount = 0
        for ((px, py) in samplePoints) {
            val color = bitmap.getPixel(px, py)
            totalR += Color.red(color)
            totalG += Color.green(color)
            totalB += Color.blue(color)
            sampleCount++
        }
        return if (sampleCount > 0) {
            Color.rgb(totalR / sampleCount, totalG / sampleCount, totalB / sampleCount)
        } else {
            Color.WHITE
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun pixelateRect(bitmap: Bitmap, box: Rect, pixelSize: Int) {
        val w = box.width()
        val h = box.height()
        if (w <= 0 || h <= 0) return

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, box.left, box.top, w, h)

        for (y in 0 until h step pixelSize) {
            for (x in 0 until w step pixelSize) {
                var sumR = 0; var sumG = 0; var sumB = 0; var count = 0
                val blockW = pixelSize.coerceAtMost(w - x)
                val blockH = pixelSize.coerceAtMost(h - y)

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
