package com.docu.editor.core.expense

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

/**
 * Enterprise Chartered Accountant (CA) & Tax Filing Microsoft Excel (.xlsx) Exporter.
 * Generates genuine OpenXML multi-sheet workbooks with native Excel calculation formulas (=SUM),
 * typed numeric cells, auto-fitted columns, and 1-tap WhatsApp/Email sharing for tax compliance.
 */
object ExpenseExcelExporter {

    /**
     * Generates a fully formatted .xlsx file containing the Expense Ledger & Category Analysis.
     */
    suspend fun generateTaxReportFile(
        context: Context,
        receipts: List<ExpenseReceiptItem>,
        periodTitle: String = "All Receipts"
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val sanitizedPeriod = periodTitle.replace(" ", "_").replace("/", "-")
        val outputFile = File(exportDir, "DocuEdit_Tax_Expense_Report_${sanitizedPeriod}_$timeStamp.xlsx")

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            // 1. [Content_Types].xml
            addZipEntry(zip, "[Content_Types].xml", generateContentTypesXml())

            // 2. _rels/.rels
            addZipEntry(zip, "_rels/.rels", generateGlobalRelsXml())

            // 3. xl/_rels/workbook.xml.rels
            addZipEntry(zip, "xl/_rels/workbook.xml.rels", generateWorkbookRelsXml())

            // 4. xl/workbook.xml
            addZipEntry(zip, "xl/workbook.xml", generateWorkbookXml())

            // 5. xl/styles.xml
            addZipEntry(zip, "xl/styles.xml", generateStylesXml())

            // 6. xl/worksheets/sheet1.xml (Main Tax Ledger)
            val sheet1Xml = generateLedgerSheetXml(receipts, periodTitle)
            addZipEntry(zip, "xl/worksheets/sheet1.xml", sheet1Xml)

            // 7. xl/worksheets/sheet2.xml (Category Breakdown)
            val sheet2Xml = generateCategorySheetXml(receipts)
            addZipEntry(zip, "xl/worksheets/sheet2.xml", sheet2Xml)
        }

        outputFile
    }

    /**
     * Shares the generated tax spreadsheet via WhatsApp, Gmail, or CA portal.
     */
    fun shareReport(context: Context, file: File, subject: String = "DocuEdit GST & Expense Tax Ledger") {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is the audited Monthly Expense & GST Tax Report generated via DocuEdit Vyapar/Khatabook Auditor."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Tax Report via"))
        } catch (_: Exception) {
        }
    }

    private fun addZipEntry(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zip.write(bytes, 0, bytes.size)
        zip.closeEntry()
    }

    private fun generateContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
    }

    private fun generateGlobalRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun generateWorkbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun generateWorkbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="GST_Expense_Ledger" sheetId="1" r:id="rId1"/>
    <sheet name="Category_Analytics" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
    }

    /**
     * Styles configuration:
     * 0: Normal Text
     * 1: Table Header (Bold, Navy fill #1E3A8A, White text)
     * 2: Currency #,##0.00
     * 3: Currency Bold Total Row
     * 4: Title Banner (14pt Bold)
     * 5: Integer Number
     */
    private fun generateStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="₹#,##0.00"/>
  </numFmts>
  <fonts count="4">
    <font><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
    <font><b/><sz val="14"/><color rgb="FF1E3A8A"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E3A8A"/><bgColor indexed="64"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="3">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
      <diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FF94A3B8"/></left>
      <right style="thin"><color rgb="FF94A3B8"/></right>
      <top style="medium"><color rgb="FF1E3A8A"/></top>
      <bottom style="double"><color rgb="FF1E3A8A"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="6">
    <!-- 0: Normal Text -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <!-- 1: Table Header -->
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <!-- 2: Currency #,##0.00 -->
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center"/>
    </xf>
    <!-- 3: Bold Total Row Currency -->
    <xf numFmtId="164" fontId="2" fillId="3" borderId="2" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="right" vertical="center"/>
    </xf>
    <!-- 4: Title Banner -->
    <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <!-- 5: Integer Number Centered -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>"""
    }

    private fun generateLedgerSheetXml(receipts: List<ExpenseReceiptItem>, periodTitle: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<dimension ref="A1:N${receipts.size + 10}"/>""")
        sb.append("""<sheetViews><sheetView tabSelected="1" workbookViewId="0" showGridLines="1"/></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="20"/>""")

        // Column widths
        sb.append("""<cols>""")
        val widths = listOf(7.0, 14.0, 16.0, 26.0, 18.0, 20.0, 13.0, 16.0, 13.0, 13.0, 13.0, 14.0, 16.0, 14.0)
        widths.forEachIndexed { i, w ->
            sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
        }
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")

        // Row 1: Title Banner
        sb.append("""<row r="1" ht="28" customHeight="1">""")
        sb.append("""<c r="A1" t="inlineStr" s="4"><is><t>DOCUEDIT VYAPAR &amp; KHATABOOK GST EXPENSE LEDGER</t></is></c>""")
        sb.append("""</row>""")

        // Row 2: Subtitle
        sb.append("""<row r="2" ht="20" customHeight="1">""")
        sb.append("""<c r="A2" t="inlineStr" s="0"><is><t>Period: $periodTitle | Generated: ${SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date())}</t></is></c>""")
        sb.append("""</row>""")

        // Row 3: Blank
        sb.append("""<row r="3" ht="10" customHeight="1"></row>""")

        // Row 4: Table Headers
        sb.append("""<row r="4" ht="26" customHeight="1">""")
        val headers = listOf(
            "S.No", "Date", "Invoice No", "Merchant / Vendor", "GSTIN",
            "Category", "Pay Mode", "Subtotal (₹)", "CGST (₹)", "SGST (₹)",
            "IGST (₹)", "Total GST (₹)", "Grand Total (₹)", "Status"
        )
        headers.forEachIndexed { idx, h ->
            val colLetter = getColLetter(idx)
            sb.append("""<c r="${colLetter}4" t="inlineStr" s="1"><is><t>$h</t></is></c>""")
        }
        sb.append("""</row>""")

        // Data Rows starting at Row 5
        var currentR = 5
        receipts.forEachIndexed { idx, r ->
            sb.append("""<row r="$currentR" ht="21" customHeight="1">""")
            // S.No
            sb.append("""<c r="A$currentR" t="n" s="5"><v>${idx + 1}</v></c>""")
            // Date
            sb.append("""<c r="B$currentR" t="inlineStr" s="5"><is><t>${sanitizeXml(r.billDate)}</t></is></c>""")
            // Invoice No
            sb.append("""<c r="C$currentR" t="inlineStr" s="0"><is><t>${sanitizeXml(r.invoiceNumber.ifBlank { "N/A" })}</t></is></c>""")
            // Merchant
            sb.append("""<c r="D$currentR" t="inlineStr" s="0"><is><t>${sanitizeXml(r.merchantName)}</t></is></c>""")
            // GSTIN
            sb.append("""<c r="E$currentR" t="inlineStr" s="5"><is><t>${sanitizeXml(r.gstin.ifBlank { "-" })}</t></is></c>""")
            // Category
            sb.append("""<c r="F$currentR" t="inlineStr" s="0"><is><t>${r.category.iconEmoji} ${r.category.displayName}</t></is></c>""")
            // Payment Mode
            sb.append("""<c r="G$currentR" t="inlineStr" s="5"><is><t>${sanitizeXml(r.paymentMode)}</t></is></c>""")
            // Subtotal
            sb.append("""<c r="H$currentR" t="n" s="2"><v>${"%.2f".format(Locale.ROOT, r.subtotal)}</v></c>""")
            // CGST
            sb.append("""<c r="I$currentR" t="n" s="2"><v>${"%.2f".format(Locale.ROOT, r.cgst)}</v></c>""")
            // SGST
            sb.append("""<c r="J$currentR" t="n" s="2"><v>${"%.2f".format(Locale.ROOT, r.sgst)}</v></c>""")
            // IGST
            sb.append("""<c r="K$currentR" t="n" s="2"><v>${"%.2f".format(Locale.ROOT, r.igst)}</v></c>""")
            // Total GST
            sb.append("""<c r="L$currentR" t="n" s="2"><v>${"%.2f".format(Locale.ROOT, r.totalGst)}</v></c>""")
            // Grand Total
            sb.append("""<c r="M$currentR" t="n" s="2"><v>${"%.2f".format(Locale.ROOT, r.grandTotal)}</v></c>""")
            // Status
            val status = if (r.hasValidGstin) "GST Verified" else if (r.verifiedByUser) "Reviewed" else "Auto Scanned"
            sb.append("""<c r="N$currentR" t="inlineStr" s="5"><is><t>$status</t></is></c>""")
            sb.append("""</row>""")
            currentR++
        }

        // Summary Total Row
        val totalR = currentR
        val startDataR = 5
        val endDataR = max(5, totalR - 1)
        val hasData = receipts.isNotEmpty()

        sb.append("""<row r="$totalR" ht="24" customHeight="1">""")
        sb.append("""<c r="A$totalR" t="inlineStr" s="3"><is><t></t></is></c>""")
        sb.append("""<c r="B$totalR" t="inlineStr" s="3"><is><t></t></is></c>""")
        sb.append("""<c r="C$totalR" t="inlineStr" s="3"><is><t></t></is></c>""")
        sb.append("""<c r="D$totalR" t="inlineStr" s="3"><is><t>TOTAL AUDITED SPEND</t></is></c>""")
        sb.append("""<c r="E$totalR" t="inlineStr" s="3"><is><t></t></is></c>""")
        sb.append("""<c r="F$totalR" t="inlineStr" s="3"><is><t></t></is></c>""")
        sb.append("""<c r="G$totalR" t="inlineStr" s="3"><is><t></t></is></c>""")

        if (hasData) {
            // Native Excel formulas
            sb.append("""<c r="H$totalR" s="3"><f>SUM(H$startDataR:H$endDataR)</f><v>${"%.2f".format(Locale.ROOT, receipts.sumOf { it.subtotal })}</v></c>""")
            sb.append("""<c r="I$totalR" s="3"><f>SUM(I$startDataR:I$endDataR)</f><v>${"%.2f".format(Locale.ROOT, receipts.sumOf { it.cgst })}</v></c>""")
            sb.append("""<c r="J$totalR" s="3"><f>SUM(J$startDataR:J$endDataR)</f><v>${"%.2f".format(Locale.ROOT, receipts.sumOf { it.sgst })}</v></c>""")
            sb.append("""<c r="K$totalR" s="3"><f>SUM(K$startDataR:K$endDataR)</f><v>${"%.2f".format(Locale.ROOT, receipts.sumOf { it.igst })}</v></c>""")
            sb.append("""<c r="L$totalR" s="3"><f>SUM(L$startDataR:L$endDataR)</f><v>${"%.2f".format(Locale.ROOT, receipts.sumOf { it.totalGst })}</v></c>""")
            sb.append("""<c r="M$totalR" s="3"><f>SUM(M$startDataR:M$endDataR)</f><v>${"%.2f".format(Locale.ROOT, receipts.sumOf { it.grandTotal })}</v></c>""")
        } else {
            sb.append("""<c r="H$totalR" t="n" s="3"><v>0.00</v></c>""")
            sb.append("""<c r="I$totalR" t="n" s="3"><v>0.00</v></c>""")
            sb.append("""<c r="J$totalR" t="n" s="3"><v>0.00</v></c>""")
            sb.append("""<c r="K$totalR" t="n" s="3"><v>0.00</v></c>""")
            sb.append("""<c r="L$totalR" t="n" s="3"><v>0.00</v></c>""")
            sb.append("""<c r="M$totalR" t="n" s="3"><v>0.00</v></c>""")
        }
        sb.append("""<c r="N$totalR" t="inlineStr" s="3"><is><t></t></is></c>""")
        sb.append("""</row>""")

        sb.append("""</sheetData>""")
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun generateCategorySheetXml(receipts: List<ExpenseReceiptItem>): String {
        val totalOverall = receipts.sumOf { it.grandTotal }
        val categoryBreakdown = ExpenseCategory.entries.map { cat ->
            val matching = receipts.filter { it.category == cat }
            val sum = matching.sumOf { it.grandTotal }
            val count = matching.size
            val pct = if (totalOverall > 0) (sum / totalOverall) * 100.0 else 0.0
            Triple(cat, sum, Pair(count, pct))
        }.sortedByDescending { it.second }

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<dimension ref="A1:E${categoryBreakdown.size + 6}"/>""")
        sb.append("""<sheetViews><sheetView tabSelected="0" workbookViewId="0" showGridLines="1"/></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="20"/>""")

        sb.append("""<cols>""")
        val widths = listOf(26.0, 12.0, 18.0, 14.0, 18.0)
        widths.forEachIndexed { i, w ->
            sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
        }
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")
        sb.append("""<row r="1" ht="26" customHeight="1">""")
        sb.append("""<c r="A1" t="inlineStr" s="4"><is><t>EXPENSE CATEGORY ANALYTICS</t></is></c>""")
        sb.append("""</row>""")

        sb.append("""<row r="3" ht="24" customHeight="1">""")
        val headers = listOf("Category", "Bill Count", "Total Spent (₹)", "% Share", "GST Credit Eligible")
        headers.forEachIndexed { idx, h ->
            val colLetter = getColLetter(idx)
            sb.append("""<c r="${colLetter}3" t="inlineStr" s="1"><is><t>$h</t></is></c>""")
        }
        sb.append("""</row>""")

        var curR = 4
        categoryBreakdown.forEach { (cat, sum, meta) ->
            val count = meta.first
            val pct = meta.second
            sb.append("""<row r="$curR" ht="20" customHeight="1">""")
            sb.append("""<c r="A$curR" t="inlineStr" s="0"><is><t>${cat.iconEmoji} ${cat.displayName}</t></is></c>""")
            sb.append("""<c r="B$curR" t="n" s="5"><v>$count</v></c>""")
            sb.append("""<c r="C$curR" t="n" s="2"><v>${"%.2f".format(Locale.ROOT, sum)}</v></c>""")
            sb.append("""<c r="D$curR" t="inlineStr" s="5"><is><t>${"%.1f".format(pct)}%</t></is></c>""")
            val eligibleStr = if (cat.isGstEligible) "Yes (ITC Allowed)" else "No"
            sb.append("""<c r="E$curR" t="inlineStr" s="5"><is><t>$eligibleStr</t></is></c>""")
            sb.append("""</row>""")
            curR++
        }

        sb.append("""</sheetData>""")
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun getColLetter(idx: Int): String {
        return ('A'.code + idx).toChar().toString()
    }

    private fun sanitizeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
