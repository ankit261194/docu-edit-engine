package com.docu.editor.core.export

import com.docu.editor.core.ocr.model.DetectedTextItem
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Enterprise CamScanner-Grade Native Excel & CSV Spreadsheet Generator.
 * Converts scanned invoices, receipts, marksheets, and tables into structured
 * CSV (.csv) and Microsoft Excel Spreadsheet (.xml / .xls) files.
 * Zero external heavyweight dependencies (100% lightweight & offline).
 */
object SpreadsheetExportEngine {

    /**
     * Clusters OCR text items into aligned rows and columns.
     */
    fun extractTableRows(items: List<DetectedTextItem>): List<List<String>> {
        if (items.isEmpty()) return emptyList()

        val sorted = items.sortedBy { it.boundingBox.top }
        val rows = mutableListOf<MutableList<DetectedTextItem>>()

        for (item in sorted) {
            val matchedRow = rows.find { row ->
                val avgTop = row.map { it.boundingBox.top }.average()
                val avgHeight = row.map { it.boundingBox.height() }.average().coerceAtLeast(10.0)
                kotlin.math.abs(item.boundingBox.top - avgTop) < (avgHeight * 0.55)
            }
            if (matchedRow != null) {
                matchedRow.add(item)
            } else {
                rows.add(mutableListOf(item))
            }
        }

        // Sort items left-to-right within each row
        rows.forEach { it.sortBy { item -> item.boundingBox.left } }

        val resultRows = mutableListOf<List<String>>()
        for (row in rows) {
            val isMultiColumn = row.size >= 2 && row.zipWithNext().any { (a, b) ->
                (b.boundingBox.left - a.boundingBox.right) >= 20
            }

            if (isMultiColumn) {
                resultRows.add(row.map { it.text.trim() })
            } else {
                val fullText = row.joinToString(" ") { it.text.trim() }
                if (fullText.isNotBlank()) {
                    resultRows.add(listOf(fullText))
                }
            }
        }

        // Normalize column count for clean rectangular table
        val maxCols = resultRows.maxOfOrNull { it.size } ?: 1
        return resultRows.map { row ->
            val padded = row.toMutableList()
            while (padded.size < maxCols) {
                padded.add("")
            }
            padded
        }
    }

    /**
     * Exports rows to standard RFC 4180 CSV with UTF-8 BOM for seamless MS Excel compatibility.
     */
    fun exportToCsv(
        pagesItems: Map<Int, List<DetectedTextItem>>,
        outputFile: File
    ): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // Write UTF-8 BOM so MS Excel on Windows & Android recognizes Unicode Hindi/accented characters
                    writer.write("\uFEFF")

                    val sortedPageKeys = pagesItems.keys.sorted()
                    for ((pIdx, pageKey) in sortedPageKeys.withIndex()) {
                        val items = pagesItems[pageKey] ?: emptyList()
                        if (sortedPageKeys.size > 1) {
                            writer.write("\"--- PAGE ${pageKey + 1} ---\"\n")
                        }

                        val rows = extractTableRows(items)
                        for (row in rows) {
                            val line = row.joinToString(",") { escapeCsvCell(it) }
                            writer.write(line)
                            writer.write("\r\n")
                        }

                        if (pIdx < sortedPageKeys.size - 1) {
                            writer.write("\r\n")
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun escapeCsvCell(cell: String): String {
        var str = cell.trim()
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }
}
