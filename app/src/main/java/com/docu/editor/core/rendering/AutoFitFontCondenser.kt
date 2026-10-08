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
        // Try breaking at punctuation delimiters like '/', '-', '_', ',', '@' first
        val delimiterRegex = Regex("(?<=[/\\-_,@.])|(?=[/\\-_,@.])")
        val subTokens = word.split(delimiterRegex).filter { it.isNotEmpty() }
        if (subTokens.size > 1) {
            val chunks = mutableListOf<String>()
            var current = StringBuilder()
            for (sub in subTokens) {
                val test = "$current$sub"
                if (paint.measureText(test) <= targetWidth || current.isEmpty()) {
                    current.append(sub)
                } else {
                    chunks.add(current.toString())
                    current = StringBuilder(sub)
                }
            }
            if (current.isNotEmpty()) chunks.add(current.toString())
            if (chunks.all { paint.measureText(it) <= targetWidth }) {
                return chunks
            }
        }

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
        sizeMultiplier: Float = 1.0f,
        availableWidth: Float? = null
    ): AdjustedTypography {
        val targetWidth = max(16, targetBounds.width()).toFloat()
        val originalLines = if (originalText.isNotEmpty()) originalText.split("\n").size else 1
        val userWantsMultiLine = text.contains("\n") || originalLines > 1

        val effectiveAllowedWidth = (availableWidth ?: (targetWidth * 1.5f)).coerceAtLeast(targetWidth)

        val effectiveText = if (userWantsMultiLine) {
            autoWrapIfTooWide(text, effectiveAllowedWidth, paint)
        } else {
            text // Single-line document text must NEVER wrap onto a second line underneath
        }
        val lines = effectiveText.split("\n")
        val lineCount = max(1, lines.size)

        // Maintain consistent document typography: calculate per-line target height
        val effectiveLineCount = max(originalLines, lineCount)
        val targetHeight = (max(8, targetBounds.height()).toFloat() / effectiveLineCount)

        // 1. Initial font size estimate based on EM box vs visual cap-height.
        var fontSize = (targetHeight * 0.85f) * sizeMultiplier
        paint.textSize = fontSize
        paint.letterSpacing = 0f
        paint.textScaleX = 1.0f

        // 2. Measure actual standard reference glyph ink height using Paint.getTextBounds
        // Calibrate font size so cap-height matches original document characters exactly.
        val origHasDescenders = originalText.any { it in "qypgj" }
        val refChar = if (effectiveText.any { it in '\u0900'..'\u097F' }) "क" else "H"
        val refBounds = Rect()
        paint.getTextBounds(refChar, 0, 1, refBounds)
        val measuredCapH = refBounds.height().toFloat()

        if (measuredCapH > 2f) {
            val desiredCapH = if (origHasDescenders) {
                targetHeight * 0.80f * sizeMultiplier
            } else {
                targetHeight * 0.95f * sizeMultiplier
            }
            val calibrationRatio = desiredCapH / measuredCapH
            fontSize = (fontSize * calibrationRatio).coerceIn(6f, targetHeight * 1.8f)
            paint.textSize = fontSize
        }

        val measuredWidth = if (lineCount > 1) {
            lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(effectiveText)
        } else {
            paint.measureText(effectiveText)
        }

        var scaleX = 1.0f
        var trackingEm = 0f

        // Only condense if the text exceeds the available width on the document line
        if (measuredWidth > effectiveAllowedWidth) {
            val ratio = effectiveAllowedWidth / measuredWidth
            when {
                ratio >= 0.88f -> {
                    scaleX = ratio.coerceIn(0.88f, 1.0f)
                    trackingEm = -0.012f
                }
                ratio >= 0.72f && ratio < 0.88f -> {
                    scaleX = 0.88f
                    trackingEm = -0.018f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val remeasured = if (lineCount > 1) {
                        lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(effectiveText)
                    } else {
                        paint.measureText(effectiveText)
                    }
                    if (remeasured > effectiveAllowedWidth) {
                        fontSize *= (effectiveAllowedWidth / remeasured).coerceAtLeast(0.82f)
                    }
                }
                else -> {
                    scaleX = 0.85f
                    trackingEm = -0.022f
                    paint.textScaleX = scaleX
                    paint.letterSpacing = trackingEm
                    val remeasured = if (lineCount > 1) {
                        lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(effectiveText)
                    } else {
                        paint.measureText(effectiveText)
                    }
                    if (remeasured > effectiveAllowedWidth) {
                        fontSize *= (effectiveAllowedWidth / remeasured).coerceAtLeast(0.75f)
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

        // 3. Pixel-perfect document baseline alignment (zero vertical drift)
        val fontMetrics = paint.fontMetrics
        val lineHeight = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading

        val hasDevanagari = effectiveText.any { it.code in 0x0900..0x097F }
        val baselineY = if (lineCount == 1 && originalLines == 1) {
            if (hasDevanagari) {
                // Devanagari Shirorekha hanging top-line alignment
                targetBounds.top.toFloat() - fontMetrics.ascent
            } else if (origHasDescenders) {
                targetBounds.bottom.toFloat() - fontMetrics.descent
            } else {
                targetBounds.bottom.toFloat() - (fontMetrics.descent * 0.18f)
            }
        } else {
            // Multi-line block: anchor top line flush with top of target box
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
