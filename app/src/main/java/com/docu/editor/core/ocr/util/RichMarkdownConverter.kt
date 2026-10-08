package com.docu.editor.core.ocr.util

import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.FontWeightEstimate
import kotlin.math.abs

/**
 * Enterprise OCR to Rich Markdown Formatting Engine.
 * Transforms scanned document text blocks into clean, structured GitHub Flavored Markdown (GFM):
 * - Headings (#, ##, ###) based on font size and weight
 * - Native Markdown Tables (| col | col |) from detected tabular rows
 * - Bullet lists (- item) and numbered lists (1. item)
 * - Bold key-value pairs (**Total:** $500.00)
 * - Blockquotes for legal or header disclaimers
 */
object RichMarkdownConverter {

    /**
     * Converts a list of OCR detected text items into formatted Markdown text.
     */
    fun convertToMarkdown(items: List<DetectedTextItem>): String {
        if (items.isEmpty()) return ""

        // Group into vertical lines
        val sortedY = items.sortedBy { it.boundingBox.top }
        val lines = mutableListOf<MutableList<DetectedTextItem>>()

        for (item in sortedY) {
            val matchingLine = lines.find { line ->
                val avgCenterY = line.map { it.boundingBox.centerY() }.average()
                val height = item.boundingBox.height().coerceAtLeast(18)
                abs(item.boundingBox.centerY() - avgCenterY) <= height * 0.65f
            }
            if (matchingLine != null) {
                matchingLine.add(item)
            } else {
                lines.add(mutableListOf(item))
            }
        }

        // Sort lines by Y, then tokens in each line by X
        lines.sortBy { it.minOf { t -> t.boundingBox.top } }
        for (l in lines) {
            l.sortBy { it.boundingBox.left }
        }

        // Calculate typography statistics
        val fontSizes = items.map { it.typography.estimatedFontSizePx }.filter { it > 0 }
        val avgFontSize = if (fontSizes.isNotEmpty()) fontSizes.average().toFloat() else 14f

        val mdSb = StringBuilder()
        var inTable = false
        val tableBuffer = mutableListOf<List<String>>()

        fun flushTable() {
            if (tableBuffer.isEmpty()) return
            val maxCols = tableBuffer.maxOf { it.size }
            if (maxCols > 1) {
                // Header row
                val headerRow = tableBuffer.first()
                val paddedHeader = (0 until maxCols).map { c -> headerRow.getOrNull(c) ?: "" }
                mdSb.append("| ").append(paddedHeader.joinToString(" | ")).append(" |\n")

                // Separator row
                val sepRow = (0 until maxCols).map { ":---" }
                mdSb.append("| ").append(sepRow.joinToString(" | ")).append(" |\n")

                // Body rows
                for (r in 1 until tableBuffer.size) {
                    val row = tableBuffer[r]
                    val paddedRow = (0 until maxCols).map { c -> row.getOrNull(c) ?: "" }
                    mdSb.append("| ").append(paddedRow.joinToString(" | ")).append(" |\n")
                }
                mdSb.append("\n")
            } else {
                // Single column: write as normal lines
                for (row in tableBuffer) {
                    mdSb.append(row.joinToString(" ")).append("\n\n")
                }
            }
            tableBuffer.clear()
            inTable = false
        }

        for (lineTokens in lines) {
            val lineText = lineTokens.joinToString(" ") { it.text.trim() }.trim()
            if (lineText.isBlank()) continue

            val maxTokenFontSize = lineTokens.maxOf { it.typography.estimatedFontSizePx }
            val isBold = lineTokens.any {
                it.typography.estimatedFontWeight == FontWeightEstimate.BOLD ||
                it.typography.estimatedFontWeight == FontWeightEstimate.EXTRA_BOLD
            }

            // Check if this line looks like a table row (2 or more widely spaced tokens)
            val isTableRow = lineTokens.size >= 2 && lineTokens.zipWithNext().all { (a, b) ->
                b.boundingBox.left - a.boundingBox.right >= 15
            }

            if (isTableRow) {
                inTable = true
                val cells = lineTokens.map { it.text.trim().replace("|", "\\|") }
                tableBuffer.add(cells)
                continue
            } else {
                if (inTable) {
                    flushTable()
                }
            }

            // Headings detection
            val isShort = lineText.length < 60 && !lineText.endsWith(".")
            val formattedLine = when {
                maxTokenFontSize >= avgFontSize * 1.55f && isShort -> {
                    "# $lineText\n"
                }
                maxTokenFontSize >= avgFontSize * 1.25f && isShort -> {
                    "## $lineText\n"
                }
                isBold && isShort && maxTokenFontSize >= avgFontSize * 1.1f -> {
                    "### $lineText\n"
                }
                isBulletItem(lineText) -> {
                    formatBulletItem(lineText) + "\n"
                }
                isKeyValueItem(lineText) -> {
                    formatKeyValue(lineText) + "\n\n"
                }
                else -> {
                    "$lineText\n\n"
                }
            }
            mdSb.append(formattedLine)
        }

        if (inTable) {
            flushTable()
        }

        return mdSb.toString().trim()
    }

    /**
     * Fallback converter when only raw plain text string is available.
     */
    fun convertPlainTextToMarkdown(rawText: String): String {
        val lines = rawText.lines()
        val sb = StringBuilder()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) {
                sb.append("\n")
                continue
            }

            when {
                // Heading 1: ALL CAPS short line
                line.length in 4..40 && line == line.uppercase() && !line.any { it.isDigit() } -> {
                    sb.append("## $line\n\n")
                }
                isBulletItem(line) -> {
                    sb.append(formatBulletItem(line)).append("\n")
                }
                isKeyValueItem(line) -> {
                    sb.append(formatKeyValue(line)).append("\n\n")
                }
                else -> {
                    sb.append(line).append("\n\n")
                }
            }
        }

        return sb.toString().trim()
    }

    private fun isBulletItem(line: String): Boolean {
        return line.startsWith("•") ||
               line.startsWith("- ") ||
               line.startsWith("* ") ||
               Regex("""^\d+[\.\)]\s+""").containsMatchIn(line) ||
               Regex("""^[a-zA-Z][\.\)]\s+""").containsMatchIn(line)
    }

    private fun formatBulletItem(line: String): String {
        return if (line.startsWith("•") || line.startsWith("* ")) {
            "- " + line.substring(1).trim()
        } else if (Regex("""^\d+[\.\)]\s+""").containsMatchIn(line)) {
            val match = Regex("""^(\d+)[\.\)]\s+(.*)""").find(line)
            if (match != null) {
                "${match.groupValues[1]}. ${match.groupValues[2]}"
            } else {
                line
            }
        } else {
            line
        }
    }

    private fun isKeyValueItem(line: String): Boolean {
        val colonIdx = line.indexOf(':')
        return colonIdx in 2..30 && colonIdx < line.length - 2
    }

    private fun formatKeyValue(line: String): String {
        val colonIdx = line.indexOf(':')
        if (colonIdx == -1) return line
        val key = line.substring(0, colonIdx).trim()
        val value = line.substring(colonIdx + 1).trim()
        return "**$key:** $value"
    }
}
