package com.docu.editor.core.export

import com.docu.editor.core.ocr.model.DetectedTextItem
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise CamScanner & Adobe Acrobat-Grade Native Excel & CSV Spreadsheet Engine.
 * Converts scanned invoices, receipts, marksheets, bills, and tabular documents into:
 * 1. Microsoft Excel OpenXML (.xlsx) Workbooks with genuine styling (xl/styles.xml),
 *    auto-fitted column widths, typed numeric cells (t="n") for native Excel formulas (=SUM),
 *    gridlines, and multi-sheet page support.
 * 2. Standard RFC-4180 CSV (.csv) spreadsheets with UTF-8 BOM for cross-platform Unicode support.
 * 
 * 100% lightweight, offline, and zero external dependency footprint.
 */
object SpreadsheetExportEngine {

    // --- Cell Value Types for Typed OpenXML Generation ---
    private sealed class CellValue {
        data class Numeric(val formattedValue: String, val styleId: Int) : CellValue()
        data class Text(val text: String, val styleId: Int) : CellValue()
    }

    /**
     * Clusters OCR text items into geometrically aligned table rows and columns.
     */
    fun extractTableRows(items: List<DetectedTextItem>): List<List<String>> {
        if (items.isEmpty()) return emptyList()

        // 1. Try high-fidelity spatial grid clustering from TableGridDetector
        val gridTable = com.docu.editor.core.layout.TableGridDetector.detectBorderlessTable(items)
        if (gridTable != null && gridTable.rowCount >= 2 && gridTable.colCount >= 2) {
            val extracted = gridTable.rows.map { rowCells ->
                rowCells.map { it.text.trim() }
            }.filter { row -> row.any { it.isNotBlank() } }

            if (extracted.isNotEmpty()) {
                return normalizeTableColumns(extracted)
            }
        }

        // 2. High-Precision Spatial Baseline & Column Anchor Clustering
        val sorted = items.sortedBy { it.boundingBox.top }
        val rowClusters = mutableListOf<MutableList<DetectedTextItem>>()

        for (item in sorted) {
            val matchedRow = rowClusters.find { row ->
                val avgTop = row.map { it.boundingBox.top }.average()
                val avgHeight = row.map { it.boundingBox.height() }.average().coerceAtLeast(10.0)
                abs(item.boundingBox.top - avgTop) < (avgHeight * 0.55)
            }
            if (matchedRow != null) {
                matchedRow.add(item)
            } else {
                rowClusters.add(mutableListOf(item))
            }
        }

        // Sort items left-to-right within each row
        rowClusters.forEach { it.sortBy { item -> item.boundingBox.left } }

        // Discover horizontal column anchors across multi-item rows
        val multiItemRows = rowClusters.filter { it.size >= 2 }
        if (multiItemRows.isEmpty()) {
            return rowClusters.map { row ->
                listOf(row.joinToString(" ") { it.text.trim() })
            }.filter { it.first().isNotBlank() }
        }

        val columnAnchors = mutableListOf<Double>()
        for (row in multiItemRows) {
            for (item in row) {
                val left = item.boundingBox.left.toDouble()
                val matched = columnAnchors.indexOfFirst { abs(it - left) <= 40.0 }
                if (matched >= 0) {
                    columnAnchors[matched] = (columnAnchors[matched] + left) / 2.0
                } else {
                    columnAnchors.add(left)
                }
            }
        }
        columnAnchors.sort()

        val numCols = max(2, columnAnchors.size)
        val resultRows = mutableListOf<List<String>>()

        for (row in rowClusters) {
            val cells = MutableList(numCols) { mutableListOf<String>() }
            for (item in row) {
                val cx = item.boundingBox.centerX().toDouble()
                var closestCol = 0
                var minDist = Double.MAX_VALUE
                for (cIdx in 0 until numCols) {
                    val anchor = columnAnchors[cIdx]
                    val dist = abs(cx - anchor)
                    if (dist < minDist) {
                        minDist = dist
                        closestCol = cIdx
                    }
                }
                cells[closestCol].add(item.text.trim())
            }

            val rowStrings = cells.map { it.joinToString(" ").trim() }
            if (rowStrings.any { it.isNotBlank() }) {
                resultRows.add(rowStrings)
            }
        }

        return normalizeTableColumns(resultRows)
    }

    /**
     * Normalizes all rows to uniform column count and strips empty trailing columns.
     */
    private fun normalizeTableColumns(rows: List<List<String>>): List<List<String>> {
        if (rows.isEmpty()) return emptyList()
        val maxCols = rows.maxOfOrNull { it.size } ?: 1

        var effectiveCols = maxCols
        while (effectiveCols > 1) {
            val colIndex = effectiveCols - 1
            val hasData = rows.any { it.getOrNull(colIndex)?.isNotBlank() == true }
            if (hasData) break
            effectiveCols--
        }

        return rows.map { row ->
            val padded = MutableList(effectiveCols) { "" }
            for (i in 0 until min(row.size, effectiveCols)) {
                padded[i] = row[i]
            }
            padded
        }
    }

    /**
     * Exports rows to RFC 4180 compliant CSV with UTF-8 BOM for MS Excel compatibility.
     */
    fun exportToCsv(
        pagesItems: Map<Int, List<DetectedTextItem>>,
        outputFile: File
    ): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    // Write UTF-8 BOM so Excel on Windows, Mac, and Android recognizes Unicode correctly
                    writer.write("\uFEFF")

                    val sortedPageKeys = pagesItems.keys.sorted()
                    for ((pIdx, pageKey) in sortedPageKeys.withIndex()) {
                        val items = pagesItems[pageKey] ?: emptyList()
                        if (sortedPageKeys.size > 1) {
                            writer.write("\"--- PAGE ${pageKey + 1} ---\"\r\n")
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
     * Exports rows to genuine Microsoft Excel OpenXML Workbook (.xlsx) format.
     * Features:
     * - Fully compliant OpenXML structure with xl/styles.xml, avoiding any Excel repair warnings.
     * - Distinct worksheet tabs for each page (Page 1, Page 2, etc.).
     * - Dynamic auto-fitted column widths (<cols>) preventing text clipping and numeric ### overflow.
     * - Visible gridlines (<sheetView showGridLines="1"/>).
     * - Professional Header Row styling (bold, Slate-100 fill, medium bottom border).
     * - Native numeric cell typing (t="n") with formatting styles so Excel functions (=SUM) work immediately.
     * - Protection for phone numbers and codes with leading zeros (preserved as text).
     */
    fun exportToXlsx(
        pagesItems: Map<Int, List<DetectedTextItem>>,
        outputFile: File
    ): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            val sortedPageKeys = if (pagesItems.isEmpty()) listOf(0) else pagesItems.keys.sorted()
            val pageCount = sortedPageKeys.size

            ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
                // 1. [Content_Types].xml
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                val ctSb = StringBuilder()
                ctSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                ctSb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                ctSb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                ctSb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
                ctSb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                ctSb.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                for (i in 1..pageCount) {
                    ctSb.append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                ctSb.append("""</Types>""")
                zip.write(ctSb.toString().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 2. _rels/.rels
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
                zip.write(rootRels.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 3. xl/styles.xml (Complete styles table with fonts, fills, borders, cellXfs)
                zip.putNextEntry(ZipEntry("xl/styles.xml"))
                zip.write(generateStylesXml().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 4. xl/workbook.xml
                zip.putNextEntry(ZipEntry("xl/workbook.xml"))
                val wbSb = StringBuilder()
                wbSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                wbSb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                wbSb.append("""<bookViews><workbookView xWindow="0" yWindow="0" windowWidth="25600" windowHeight="14400"/></bookViews>""")
                wbSb.append("""<sheets>""")
                for (i in 1..pageCount) {
                    wbSb.append("""<sheet name="Page $i" sheetId="$i" r:id="rId$i"/>""")
                }
                wbSb.append("""</sheets></workbook>""")
                zip.write(wbSb.toString().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 5. xl/_rels/workbook.xml.rels
                zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                val wbRelsSb = StringBuilder()
                wbRelsSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                wbRelsSb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                wbRelsSb.append("""<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                for (i in 1..pageCount) {
                    wbRelsSb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
                }
                wbRelsSb.append("""</Relationships>""")
                zip.write(wbRelsSb.toString().toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // 6. xl/worksheets/sheet{N}.xml
                for ((idx, pageKey) in sortedPageKeys.withIndex()) {
                    val sheetNum = idx + 1
                    val items = pagesItems[pageKey] ?: emptyList()
                    val rows = extractTableRows(items)

                    zip.putNextEntry(ZipEntry("xl/worksheets/sheet$sheetNum.xml"))
                    val sheetXml = generateWorksheetXml(rows, isFirstSheet = (sheetNum == 1))
                    zip.write(sheetXml.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Generates standard OpenXML styles table with fonts, fills, borders, and number formats.
     * Style indices:
     * 0: Standard Body Text (Calibri 11pt, thin border, left aligned)
     * 1: Table Header (Calibri 11pt Bold, Slate-100 fill, medium bottom border, center/left aligned)
     * 2: Integer Number (Calibri 11pt, thin border, right aligned, #,##0)
     * 3: Decimal/Currency (Calibri 11pt, thin border, right aligned, #,##0.00)
     * 4: Percentage (Calibri 11pt, thin border, right aligned, 0.00%)
     * 5: Date (Calibri 11pt, thin border, center aligned, yyyy-mm-dd)
     * 6: Total Row (Calibri 11pt Bold, Slate-50 fill, medium bottom border, right aligned, #,##0.00)
     */
    private fun generateStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font>
      <sz val="11"/>
      <color rgb="FF0F172A"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FF0F172A"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FF0F172A"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
  </fonts>
  <fills count="4">
    <fill>
      <patternFill patternType="none"/>
    </fill>
    <fill>
      <patternFill patternType="gray125"/>
    </fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFF1F5F9"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
    <fill>
      <patternFill patternType="solid">
        <fgColor rgb="FFF8FAFC"/>
        <bgColor indexed="64"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="3">
    <border>
      <left/>
      <right/>
      <top/>
      <bottom/>
      <diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
      <diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="medium"><color rgb="FF64748B"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="7">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="3" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center"/>
    </xf>
    <xf numFmtId="4" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center"/>
    </xf>
    <xf numFmtId="10" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center"/>
    </xf>
    <xf numFmtId="14" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="4" fontId="2" fillId="3" borderId="2" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center"/>
    </xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>"""
    }

    /**
     * Builds individual worksheet XML following exact OpenXML schema:
     * <dimension> -> <sheetViews> -> <sheetFormatPr> -> <cols> -> <sheetData> -> <pageMargins>
     */
    private fun generateWorksheetXml(rows: List<List<String>>, isFirstSheet: Boolean): String {
        val totalRows = rows.size
        val totalCols = if (rows.isNotEmpty()) rows.maxOf { it.size } else 1

        val lastColLetter = getColumnLetter(max(0, totalCols - 1))
        val maxCellRef = "$lastColLetter${max(1, totalRows)}"

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<dimension ref="A1:$maxCellRef"/>""")

        // Gridlines enabled by default
        sb.append("""<sheetViews>""")
        sb.append("""<sheetView tabSelected="${if (isFirstSheet) "1" else "0"}" workbookViewId="0" showGridLines="1"/>""")
        sb.append("""</sheetViews>""")

        sb.append("""<sheetFormatPr defaultRowHeight="20"/>""")

        // Calculate dynamic auto-fitted column widths based on maximum text content length
        if (totalCols > 0) {
            sb.append("""<cols>""")
            for (cIdx in 0 until totalCols) {
                val maxCharLen = rows.maxOfOrNull { row ->
                    row.getOrNull(cIdx)?.length ?: 0
                } ?: 0
                val computedWidth = ((maxCharLen * 1.25) + 3.5).coerceIn(12.0, 55.0)
                val colNumber = cIdx + 1
                sb.append("""<col min="$colNumber" max="$colNumber" width="$computedWidth" customWidth="1"/>""")
            }
            sb.append("""</cols>""")
        }

        // Sheet data with styled and typed cells
        sb.append("""<sheetData>""")
        for ((rIdx, row) in rows.withIndex()) {
            val rowNum = rIdx + 1
            val isHeaderRow = (rIdx == 0)
            val isTotalRow = !isHeaderRow && row.firstOrNull { it.isNotBlank() }?.let { firstText ->
                firstText.contains(Regex("""(?i)\b(total|grand total|subtotal|sum|net|balance|कुल|योग)\b"""))
            } ?: false

            val rowHeight = if (isHeaderRow) "26" else "20"
            sb.append("""<row r="$rowNum" ht="$rowHeight" customHeight="1">""")

            for ((cIdx, cellText) in row.withIndex()) {
                if (cellText.isNotBlank()) {
                    val colRef = getColumnLetter(cIdx)
                    val cellRef = "$colRef$rowNum"

                    if (isHeaderRow) {
                        val escaped = sanitizeForXml(cellText)
                        sb.append("""<c r="$cellRef" t="inlineStr" s="1"><is><t>$escaped</t></is></c>""")
                    } else {
                        val parsed = parseCellContent(cellText, isTotalRow)
                        when (parsed) {
                            is CellValue.Numeric -> {
                                sb.append("""<c r="$cellRef" t="n" s="${parsed.styleId}"><v>${parsed.formattedValue}</v></c>""")
                            }
                            is CellValue.Text -> {
                                val escaped = sanitizeForXml(parsed.text)
                                sb.append("""<c r="$cellRef" t="inlineStr" s="${parsed.styleId}"><is><t>$escaped</t></is></c>""")
                            }
                        }
                    }
                }
            }
            sb.append("""</row>""")
        }
        sb.append("""</sheetData>""")

        sb.append("""<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
        sb.append("""</worksheet>""")

        return sb.toString()
    }

    /**
     * Parses raw cell string into typed numeric value or formatted text.
     */
    private fun parseCellContent(rawText: String, isTotalRow: Boolean): CellValue {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return CellValue.Text("", if (isTotalRow) 6 else 0)
        }

        if (isTotalRow) {
            val numClean = stripCurrencyAndCommas(trimmed)
            if (numClean != null) {
                return CellValue.Numeric(numClean, 6)
            }
            return CellValue.Text(trimmed, 6)
        }

        // 1. Percentage (e.g. "18%", "18.5%", "2.5 %")
        if (trimmed.endsWith("%")) {
            val numPart = trimmed.removeSuffix("%").trim().replace(",", "")
            val d = numPart.toDoubleOrNull()
            if (d != null) {
                val fraction = d / 100.0
                return CellValue.Numeric(fraction.toString(), 4)
            }
        }

        // 2. Date format (e.g. "2024-05-15", "15/05/2024", "15-05-2024")
        if (isDateString(trimmed)) {
            return CellValue.Text(trimmed, 5)
        }

        // 3. Preserve serial numbers, phone numbers, or codes with leading zero as text
        if (trimmed.startsWith("0") && trimmed.length > 1 && trimmed.all { it.isDigit() }) {
            return CellValue.Text(trimmed, 0)
        }
        if (trimmed.startsWith("+") || (trimmed.contains("-") && !trimmed.startsWith("-"))) {
            return CellValue.Text(trimmed, 0)
        }

        // 4. Currency or standard integer/decimal numbers
        val numClean = stripCurrencyAndCommas(trimmed)
        if (numClean != null) {
            return if (numClean.contains(".")) {
                CellValue.Numeric(numClean, 3) // Decimal / Currency
            } else {
                CellValue.Numeric(numClean, 2) // Integer
            }
        }

        // Default to body text
        return CellValue.Text(trimmed, 0)
    }

    /**
     * Strips currency symbols and thousands separators if the string represents a valid number.
     */
    private fun stripCurrencyAndCommas(str: String): String? {
        var s = str.trim()
        s = s.replace(Regex("""^(\$|€|£|¥|₹|Rs\.?|INR|USD|EUR)\s*"""), "")
             .replace(Regex("""\s*(\$|€|£|¥|₹|Rs\.?|INR|USD|EUR)$"""), "")
             .trim()

        if (s.matches(Regex("""^-?\d{1,3}(,\d{3})+(\.\d+)?$"""))) {
            s = s.replace(",", "")
        }

        return if (s.matches(Regex("""^-?\d+(\.\d+)?$"""))) {
            s
        } else {
            null
        }
    }

    private fun isDateString(str: String): Boolean {
        return str.matches(Regex("""^\d{4}[-/.]\d{1,2}[-/.]\d{1,2}$""")) ||
               str.matches(Regex("""^\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}$"""))
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

    /**
     * Sanitizes strings to eliminate XML 1.0 illegal control characters and escapes entities.
     */
    private fun sanitizeForXml(str: String): String {
        val clean = str.filter { ch ->
            ch == '\t' || ch == '\n' || ch == '\r' ||
            (ch.code in 0x20..0xD7FF) ||
            (ch.code in 0xE000..0xFFFD)
        }
        return clean
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
