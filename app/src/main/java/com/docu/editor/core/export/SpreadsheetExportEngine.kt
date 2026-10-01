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

        // 1. Try high-fidelity spatial grid clustering from TableGridDetector
        val gridTable = com.docu.editor.core.layout.TableGridDetector.detectBorderlessTable(items)
        if (gridTable != null && gridTable.rowCount >= 2 && gridTable.colCount >= 2) {
            return gridTable.rows.map { rowCells ->
                rowCells.map { it.text.trim() }
            }
        }

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

    /**
     * Exports rows to genuine Microsoft Excel OpenXML Workbook (.xlsx) format
     * with each page mapped to its own distinct worksheet tab (Page 1, Page 2, etc.).
     * 100% lightweight and zero external library dependencies.
     */
    fun exportToXlsx(
        pagesItems: Map<Int, List<DetectedTextItem>>,
        outputFile: File
    ): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            val sortedPageKeys = pagesItems.keys.sorted()
            val pageCount = if (sortedPageKeys.isEmpty()) 1 else sortedPageKeys.size

            java.util.zip.ZipOutputStream(java.io.FileOutputStream(outputFile)).use { zip ->
                // 1. [Content_Types].xml
                zip.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
                val ctSb = StringBuilder()
                ctSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                ctSb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                ctSb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                ctSb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
                ctSb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                for (i in 1..pageCount) {
                    ctSb.append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                ctSb.append("""</Types>""")
                zip.write(ctSb.toString().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 2. _rels/.rels
                zip.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
                val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
                zip.write(rootRels.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 3. xl/workbook.xml
                zip.putNextEntry(java.util.zip.ZipEntry("xl/workbook.xml"))
                val wbSb = StringBuilder()
                wbSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                wbSb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                wbSb.append("""<sheets>""")
                for (i in 1..pageCount) {
                    wbSb.append("""<sheet name="Page $i" sheetId="$i" r:id="rId$i"/>""")
                }
                wbSb.append("""</sheets></workbook>""")
                zip.write(wbSb.toString().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 4. xl/_rels/workbook.xml.rels
                zip.putNextEntry(java.util.zip.ZipEntry("xl/_rels/workbook.xml.rels"))
                val wbRelsSb = StringBuilder()
                wbRelsSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                wbRelsSb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                for (i in 1..pageCount) {
                    wbRelsSb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
                }
                wbRelsSb.append("""</Relationships>""")
                zip.write(wbRelsSb.toString().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 5. xl/worksheets/sheet{N}.xml
                val keysToProcess = if (sortedPageKeys.isEmpty()) listOf(0) else sortedPageKeys
                for ((idx, pageKey) in keysToProcess.withIndex()) {
                    val sheetNum = idx + 1
                    val items = pagesItems[pageKey] ?: emptyList()
                    val rows = extractTableRows(items)

                    zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet$sheetNum.xml"))
                    val sheetSb = StringBuilder()
                    sheetSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    sheetSb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                    sheetSb.append("""<sheetData>""")

                    for ((rIdx, row) in rows.withIndex()) {
                        val rowNum = rIdx + 1
                        sheetSb.append("""<row r="$rowNum">""")
                        for ((cIdx, cellText) in row.withIndex()) {
                            if (cellText.isNotBlank()) {
                                val colRef = getColumnLetter(cIdx)
                                val cellRef = "$colRef$rowNum"
                                val escaped = escapeXml(cellText)
                                sheetSb.append("""<c r="$cellRef" t="inlineStr"><is><t>$escaped</t></is></c>""")
                            }
                        }
                        sheetSb.append("""</row>""")
                    }

                    sheetSb.append("""</sheetData></worksheet>""")
                    zip.write(sheetSb.toString().toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getColumnLetter(colIndex: Int): String {
        var num = colIndex + 1
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A'.code + rem).toChar())
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
