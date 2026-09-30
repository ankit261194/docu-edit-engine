package com.docu.editor.core.export

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Enterprise CamScanner-Grade Native Microsoft Word (.docx) Generator.
 * Converts OCR extracted text, handwriting notes, structured markdown headings,
 * lists, and tables into 100% authentic, standard OpenXML (.docx) files.
 * Zero external heavyweight dependencies (avoids POI / multidex bloat).
 * Fully compatible with Microsoft Word (Desktop/Android), Google Docs, and WPS Office.
 */
object DocxExportEngine {

    fun generateDocx(
        title: String,
        content: String,
        outputFile: File
    ): Boolean {
        return try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    // 1. [Content_Types].xml
                    writeZipEntry(zos, "[Content_Types].xml", buildContentTypesXml())

                    // 2. _rels/.rels
                    writeZipEntry(zos, "_rels/.rels", buildPackageRelsXml())

                    // 3. word/_rels/document.xml.rels
                    writeZipEntry(zos, "word/_rels/document.xml.rels", buildDocumentRelsXml())

                    // 4. word/styles.xml
                    writeZipEntry(zos, "word/styles.xml", buildStylesXml())

                    // 5. word/document.xml
                    writeZipEntry(zos, "word/document.xml", buildDocumentXml(title, content))
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, entryName: String, xmlContent: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val bytes = xmlContent.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""
    }

    private fun buildPackageRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
    }

    private fun buildDocumentRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
        <w:sz w:val="22"/>
        <w:szCs w:val="22"/>
        <w:color w:val="1E293B"/>
        <w:lang w:val="en-US"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:after="160" w:line="276" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>

  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:pPr>
      <w:spacing w:before="360" w:after="160"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:b/>
      <w:color w:val="1E3A8A"/>
      <w:sz w:val="36"/>
      <w:szCs w:val="36"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:pPr>
      <w:spacing w:before="240" w:after="120"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:b/>
      <w:color w:val="2563EB"/>
      <w:sz w:val="28"/>
      <w:szCs w:val="28"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="Heading3">
    <w:name w:val="heading 3"/>
    <w:pPr>
      <w:spacing w:before="180" w:after="80"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:b/>
      <w:color w:val="334155"/>
      <w:sz w:val="24"/>
      <w:szCs w:val="24"/>
    </w:rPr>
  </w:style>

  <w:style w:type="paragraph" w:styleId="ListBullet">
    <w:name w:val="List Bullet"/>
    <w:pPr>
      <w:ind w:left="480" w:hanging="240"/>
      <w:spacing w:after="80"/>
    </w:pPr>
  </w:style>
</w:styles>"""
    }

    private fun buildDocumentXml(title: String, content: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
""")

        // Title Header
        val safeTitle = escapeXml(title.ifBlank { "Scanned Document" })
        sb.append("""    <w:p>
      <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
      <w:r><w:t>$safeTitle</w:t></w:r>
    </w:p>
""")

        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i].trim()

            // 1. Table Detection (markdown table rows starting with '|')
            if (rawLine.startsWith("|") && rawLine.endsWith("|") && rawLine.count { it == '|' } >= 2) {
                val tableRows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    val rowText = lines[i].trim()
                    // Check if it's separator row like |---|---|
                    if (!rowText.matches(Regex("""^\|[\s\-:|]+\|$"""))) {
                        val cells = rowText.split("|")
                            .filterIndexed { index, _ -> index != 0 && index != rowText.split("|").lastIndex }
                            .map { it.trim() }
                        tableRows.add(cells)
                    }
                    i++
                }
                sb.append(renderDocxTable(tableRows))
                continue
            }

            // 2. Heading 1 (# Heading)
            if (rawLine.startsWith("# ")) {
                val text = escapeXml(rawLine.removePrefix("# ").trim())
                sb.append("""    <w:p>
      <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
      <w:r><w:t>$text</w:t></w:r>
    </w:p>
""")
                i++
                continue
            }

            // 3. Heading 2 (## Heading)
            if (rawLine.startsWith("## ")) {
                val text = escapeXml(rawLine.removePrefix("## ").trim())
                sb.append("""    <w:p>
      <w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
      <w:r><w:t>$text</w:t></w:r>
    </w:p>
""")
                i++
                continue
            }

            // 4. Heading 3 (### Heading)
            if (rawLine.startsWith("### ")) {
                val text = escapeXml(rawLine.removePrefix("### ").trim())
                sb.append("""    <w:p>
      <w:pPr><w:pStyle w:val="Heading3"/></w:pPr>
      <w:r><w:t>$text</w:t></w:r>
    </w:p>
""")
                i++
                continue
            }

            // 5. Bullet List (- item or * item)
            if (rawLine.startsWith("- ") || rawLine.startsWith("* ")) {
                val itemText = rawLine.substring(2).trim()
                sb.append("""    <w:p>
      <w:pPr><w:pStyle w:val="ListBullet"/></w:pPr>
      <w:r><w:t>• </w:t></w:r>
""")
                appendFormattedRuns(sb, itemText)
                sb.append("    </w:p>\n")
                i++
                continue
            }

            // 6. Numbered List (1. item, 2. item)
            val numMatch = Regex("""^(\d+)\.\s+(.*)$""").find(rawLine)
            if (numMatch != null) {
                val prefix = numMatch.groupValues[1]
                val itemText = numMatch.groupValues[2]
                sb.append("""    <w:p>
      <w:pPr><w:pStyle w:val="ListBullet"/></w:pPr>
      <w:r><w:rPr><w:b/></w:rPr><w:t>$prefix. </w:t></w:r>
""")
                appendFormattedRuns(sb, itemText)
                sb.append("    </w:p>\n")
                i++
                continue
            }

            // 7. Empty Line / Paragraph Spacing
            if (rawLine.isEmpty()) {
                sb.append("    <w:p/>\n")
                i++
                continue
            }

            // 8. Normal Body Paragraph
            sb.append("    <w:p>\n")
            appendFormattedRuns(sb, rawLine)
            sb.append("    </w:p>\n")
            i++
        }

        // Section standard A4 page dimensions (595x842 pt = 11906x16838 twips) + 1-inch margins (1440 twips)
        sb.append("""    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>""")

        return sb.toString()
    }

    private fun renderDocxTable(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("""    <w:tbl>
      <w:tblPr>
        <w:tblW w:w="5000" w:type="pct"/>
        <w:tblBorders>
          <w:top w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:left w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:right w:val="single" w:sz="6" w:space="0" w:color="CBD5E1"/>
          <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
          <w:insideV w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
        </w:tblBorders>
      </w:tblPr>
""")

        rows.forEachIndexed { rowIndex, row ->
            val isHeader = (rowIndex == 0)
            sb.append("      <w:tr>\n")
            for (cell in row) {
                sb.append("""        <w:tc>
          <w:tcPr>
            <w:tcMar>
              <w:top w:w="120" w:type="dxa"/>
              <w:left w:w="160" w:type="dxa"/>
              <w:bottom w:w="120" w:type="dxa"/>
              <w:right w:w="160" w:type="dxa"/>
            </w:tcMar>
""")
                if (isHeader) {
                    sb.append("            <w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F1F5F9\"/>\n")
                }
                sb.append("""          </w:tcPr>
          <w:p>
            <w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>
""")
                if (isHeader) {
                    sb.append("            <w:r><w:rPr><w:b/><w:color w:val=\"0F172A\"/></w:rPr><w:t>${escapeXml(cell)}</w:t></w:r>\n")
                } else {
                    sb.append("            <w:r><w:t>${escapeXml(cell)}</w:t></w:r>\n")
                }
                sb.append("""          </w:p>
        </w:tc>
""")
            }
            sb.append("      </w:tr>\n")
        }

        sb.append("    </w:tbl>\n")
        return sb.toString()
    }

    private fun appendFormattedRuns(sb: StringBuilder, line: String) {
        // Parse bold formatting **text**
        val parts = line.split("**")
        if (parts.size == 1) {
            sb.append("      <w:r><w:t>${escapeXml(line)}</w:t></w:r>\n")
            return
        }

        for (idx in parts.indices) {
            val part = parts[idx]
            if (part.isEmpty()) continue
            val isBold = (idx % 2 == 1)
            sb.append("      <w:r>")
            if (isBold) {
                sb.append("<w:rPr><w:b/></w:rPr>")
            }
            sb.append("<w:t>${escapeXml(part)}</w:t></w:r>\n")
        }
    }

    /**
     * Reconstructs text items into clean paragraphs and authentic multi-column table grids.
     */
    fun formatItemsToStructuredDocument(items: List<com.docu.editor.core.ocr.model.DetectedTextItem>): String {
        if (items.isEmpty()) return ""

        val sorted = items.sortedBy { it.boundingBox.top }
        val rows = mutableListOf<MutableList<com.docu.editor.core.ocr.model.DetectedTextItem>>()

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

        rows.forEach { it.sortBy { item -> item.boundingBox.left } }

        val sb = StringBuilder()
        val tableBuffer = mutableListOf<List<String>>()

        fun flushTable() {
            if (tableBuffer.isNotEmpty()) {
                if (tableBuffer.size >= 2) {
                    val maxCols = tableBuffer.maxOf { it.size }
                    for ((rIdx, rowCells) in tableBuffer.withIndex()) {
                        val padded = rowCells.toMutableList()
                        while (padded.size < maxCols) padded.add("")
                        sb.append("| ").append(padded.joinToString(" | ")).append(" |\n")
                        if (rIdx == 0) {
                            sb.append("|").append("---|".repeat(maxCols)).append("\n")
                        }
                    }
                    sb.append("\n")
                } else {
                    tableBuffer.forEach { cells ->
                        sb.append(cells.joinToString("   ")).append("\n\n")
                    }
                }
                tableBuffer.clear()
            }
        }

        for (row in rows) {
            val isMultiColumn = row.size >= 2 && row.zipWithNext().any { (a, b) ->
                (b.boundingBox.left - a.boundingBox.right) >= 24
            }

            if (isMultiColumn) {
                val cells = row.map { it.text.trim() }
                tableBuffer.add(cells)
            } else {
                flushTable()
                val lineText = row.joinToString(" ") { it.text.trim() }
                if (lineText.isNotBlank()) {
                    sb.append(lineText).append("\n\n")
                }
            }
        }
        flushTable()

        return sb.toString()
    }
}
