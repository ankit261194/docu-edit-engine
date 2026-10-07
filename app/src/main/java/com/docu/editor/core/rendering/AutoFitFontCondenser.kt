package com.docu.editor.core.rendering

import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max

object AutoFitFontCondenser {

    data class AdjustedTypography(
        val fontSize: Float,
        val letterSpacingEm: Float,
        val scaleX: Float,
        val baselineY: Float,
        val wrappedText: String
    )

    fun autoWrapIfTooWide(text: String, targetWidth: Float, paint: Paint): String {
        val paragraphs = text.split("\n")
        val wrappedParagraphs = paragraphs.map { paragraph ->
            wrapSingleParagraph(paragraph, targetWidth, paint)
        }
        return wrappedParagraphs.joinToString("\n")
    }

    private fun wrapSingleParagraph(paragraph: String, targetWidth: Float, paint: Paint): String {
        if (paragraph.isBlank()) return paragraph
        val measured = paint.measureText(paragraph)
        if (measured <= targetWidth * 1.05f) return paragraph

        val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return paragraph

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val wordWidth = paint.measureText(word)
            if (wordWidth > targetWidth) {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                val brokenChunks = breakLongWord(word, targetWidth, paint)
                for (chunkIdx in brokenChunks.indices) {
                    val chunk = brokenChunks[chunkIdx]
                    if (chunkIdx < brokenChunks.size - 1) {
                        lines.add(chunk)
                    } else {
                        currentLine = StringBuilder(chunk)
                    }
                }
                continue
            }

            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= targetWidth || currentLine.isEmpty()) {
                currentLine = StringBuilder(testLine)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return if (lines.size > 1) lines.joinToString("\n") else paragraph
    }

    private fun breakLongWord(word: String, targetWidth: Float, paint: Paint): List<String> {
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (i in word.indices) {
            val c = word[i]
            val test = "$current$c-"
            if (paint.measureText(test) > targetWidth && current.isNotEmpty()) {
                chunks.add("$current-")
                current = StringBuilder(c.toString())
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }
        return if (chunks.isNotEmpty()) chunks else listOf(word)
    }

    fun condenseToFit(
        text: String,
        targetBounds: Rect,
        paint: Paint,
        originalText: String = "",
        sizeMultiplier: Float = 1.0f
    ): AdjustedTypography {
        val targetWidth = max(10, targetBounds.width()).toFloat()
        val originalLines = if (originalText.isNotEmpty()) originalText.split("\n").size else 1

        val effectiveText = autoWrapIfTooWide(text, targetWidth, paint)
        val lines = effectiveText.split("\n")
        val lineCount = max(1, lines.size)

        // Maintain consistent document typography: calculate per-line target height
        val targetHeight = (max(8, targetBounds.height()).toFloat() / max(1, originalLines))

        // 1. Initial font size estimate based on EM box vs visual cap-height.
        var fontSize = (targetHeight * 0.85f) * sizeMultiplier
        paint.textSize = fontSize
        paint.letterSpacing = 0f
        paint.textScaleX = 1.0f

        // 2. Measure actual standard reference glyph ink height using Paint.getTextBounds
        // Standard typographical reference 'H' (or Devanagari 'क') guarantees identical font size
        // regardless of whether user inputs lowercase, numbers, or symbols.
        val refChar = if (effectiveText.any { it in '\u0900'..'\u097F' }) "क" else "H"
        val refBounds = Rect()
        paint.getTextBounds(refChar, 0, 1, refBounds)
        val measuredCapH = refBounds.height().toFloat()

        if (measuredCapH > 2f) {
            val desiredCapH = targetHeight * 0.72f * sizeMultiplier
            val calibrationRatio = desiredCapH / measuredCapH
            fontSize = (fontSize * calibrationRatio).coerceIn(6f, targetHeight * 1.6f)
            paint.textSize = fontSize
        }

        val measuredWidth = if (lineCount > 1) {
            lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(effectiveText)
        } else {
            paint.measureText(effectiveText)
        }

        var scaleX = 1.0f
        var trackingEm = 0f

        if (measuredWidth > targetWidth) {
            val ratio = targetWidth / measuredWidth
            when {
                ratio >= 0.85f -> {
                    scaleX = ratio.coerceIn(0.85f, 1.0f)
                    trackingEm = -0.012f
                }
                ratio in 0.65f..0.85f -> {
                    scaleX = 0.85f
                    trackingEm = -0.02f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val remeasured = if (lineCount > 1) {
                        lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(effectiveText)
                    } else {
                        paint.measureText(effectiveText)
                    }
                    if (remeasured > targetWidth) {
                        fontSize *= (targetWidth / remeasured).coerceAtLeast(0.68f)
                    }
                }
                else -> {
                    scaleX = 0.82f
                    trackingEm = -0.025f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val remeasured = if (lineCount > 1) {
                        lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(effectiveText)
                    } else {
                        paint.measureText(effectiveText)
                    }
                    if (remeasured > targetWidth) {
                        fontSize *= (targetWidth / remeasured).coerceAtLeast(0.50f)
                    }
                }
            }
        }

        // Indic / Devanagari script protection:
        val hasIndicScript = effectiveText.any { it.code in 0x0900..0x0D7F }
        if (hasIndicScript) {
            trackingEm = 0f
        }

        paint.textSize = fontSize
        paint.letterSpacing = trackingEm
        paint.textScaleX = scaleX

        // 3. Pixel-perfect baseline alignment
        val fontMetrics = paint.fontMetrics
        val lineHeight = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading

        val baselineY = if (lineCount == 1 && originalLines == 1) {
            val totalTextHeight = fontMetrics.descent - fontMetrics.ascent
            val topY = targetBounds.centerY().toFloat() - totalTextHeight / 2f
            topY - fontMetrics.ascent
        } else {
            targetBounds.top.toFloat() - fontMetrics.ascent
        }

        return AdjustedTypography(
            fontSize = fontSize,
            letterSpacingEm = trackingEm,
            scaleX = scaleX,
            baselineY = baselineY,
            wrappedText = effectiveText
        )
    }
}
