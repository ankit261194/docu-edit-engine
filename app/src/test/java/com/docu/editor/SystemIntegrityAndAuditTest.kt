package com.docu.editor

import com.docu.editor.core.classification.DocumentClassificationEngine
import com.docu.editor.core.classification.ExpiryWatchdogEngine
import com.docu.editor.core.expense.ExpenseCategory
import com.docu.editor.core.expense.ReceiptEntityExtractor
import com.docu.editor.core.font.FontClassification
import com.docu.editor.core.layout.StandardPageSize
import com.docu.editor.core.ocr.model.TypographyMetrics
import com.docu.editor.core.ocr.util.DevanagariPostProcessor
import com.docu.editor.core.ocr.util.RichMarkdownConverter
import com.docu.editor.domain.model.DocumentFilterMode
import com.docu.editor.domain.model.EditorToolMode
import com.docu.editor.domain.model.ShapeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemIntegrityAndAuditTest {

    @Test
    fun testAllEditorToolModesHaveNames() {
        for (mode in EditorToolMode.values()) {
            assertNotNull(mode.name)
            assertTrue(mode.name.isNotBlank())
        }
    }

    @Test
    fun testAllDocumentFilterModesHaveDisplayNames() {
        for (filter in DocumentFilterMode.values()) {
            assertNotNull(filter.displayName)
            assertTrue("Filter display name should not be blank: ${filter.name}", filter.displayName.isNotBlank())
        }
    }

    @Test
    fun testAllShapeTypesHaveIconsAndNames() {
        for (shape in ShapeType.values()) {
            assertTrue(shape.displayName.isNotBlank())
            assertTrue(shape.icon.isNotBlank())
        }
    }

    @Test
    fun testStandardPageSizesAreValid() {
        for (size in StandardPageSize.values()) {
            assertTrue("Width in mm must be > 0: ${size.name}", size.widthMm > 0f)
            assertTrue("Height in mm must be > 0: ${size.name}", size.heightMm > 0f)
            assertTrue("Pixel width must be > 0: ${size.name}", size.pixelWidth300Dpi > 0)
            assertTrue("Pixel height must be > 0: ${size.name}", size.pixelHeight300Dpi > 0)
        }
    }

    @Test
    fun testReceiptExtractorWithMultipleTaxComponents() {
        val ocr = """
            CROMA ELECTRONICS MEGA STORE
            GSTIN: 27AABCM8821R1ZX
            Invoice No: INV-99021
            Subtotal: 1000.00
            CGST @ 9%: 90.00
            SGST @ 9%: 90.00
            Net Total: 1,180.00
        """.trimIndent()
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals("27AABCM8821R1ZX", receipt.gstin)
        assertEquals("INV-99021", receipt.invoiceNumber)
        assertEquals(1180.00, receipt.grandTotal, 0.01)
        assertEquals(ExpenseCategory.SHOPPING, receipt.category)
    }

    @Test
    fun testReceiptExtractorTravelCategory() {
        val ocr = """
            AIR INDIA BOARDING PASS
            FLIGHT AI-802 DEL TO BOM
            SEAT 14B PASSENGER SHARMA/R
            FARE TOTAL: INR 6,450.00
        """.trimIndent()
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals(ExpenseCategory.TRAVEL, receipt.category)
        assertEquals(6450.00, receipt.grandTotal, 0.01)
    }

    @Test
    fun testReceiptExtractorHotelCategory() {
        val ocr = """
            TAJ MAHAL PALACE HOTEL
            CHECK-IN 10/10/2026 CHECK-OUT 12/10/2026
            ROOM CHARGES: 18500.00
            TOTAL AMOUNT: 18500.00
        """.trimIndent()
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals(ExpenseCategory.TRAVEL, receipt.category)
        assertEquals(18500.00, receipt.grandTotal, 0.01)
    }

    @Test
    fun testReceiptExtractorRestaurantNamedHotelCategory() {
        val ocr = """
            HOTEL SARAVANA BHAVAN
            TABLE NO: 4
            MASALA DOSA 1 80.00
            FILTER COFFEE 1 40.00
            TOTAL AMOUNT: 120.00
        """.trimIndent()
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals(ExpenseCategory.FOOD, receipt.category)
        assertEquals(120.00, receipt.grandTotal, 0.01)
    }

    @Test
    fun testDevanagariComplexSentenceCorrection() {
        val raw = "भ ारतीय स ंविधान क े अन ुच्छेद |"
        val corrected = DevanagariPostProcessor.postProcess(raw)
        assertTrue("Pipe should convert to Purna Viram", corrected.contains("।"))
        assertTrue("Matras should join to consonant", corrected.contains("भारतीय"))
        assertTrue("Matras should join to consonant", corrected.contains("संविधान"))
    }

    @Test
    fun testRichMarkdownHeaderFormatting() {
        val raw = "TAX INVOICE CUM BILL OF SUPPLY\nSupplier: XYZ\nTotal: 500"
        val md = RichMarkdownConverter.convertPlainTextToMarkdown(raw)
        assertTrue(md.startsWith("## TAX INVOICE CUM BILL OF SUPPLY"))
        assertTrue(md.contains("**Supplier:** XYZ"))
        assertTrue(md.contains("**Total:** 500"))
    }

    @Test
    fun testExpiryWatchdogIsoFormat() {
        val ocr = "Card Exp Date: 2032-08-31 Valid"
        val res = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue(res.hasExpiry)
        assertEquals(ExpiryWatchdogEngine.ExpiryStatus.VALID, res.status)
        assertTrue(res.daysRemaining > 0)
    }

    @Test
    fun testExpiryWatchdogDotSeparatorFormat() {
        val ocr = "Certificate Validity: 15.06.2029"
        val res = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue(res.hasExpiry)
        assertTrue(res.daysRemaining > 0)
    }

    @Test
    fun testFontClassificationValues() {
        val values = FontClassification.values()
        assertTrue(values.contains(FontClassification.SERIF))
        assertTrue(values.contains(FontClassification.SANS_SERIF))
        assertTrue(values.contains(FontClassification.MONOSPACE))
        assertTrue(values.contains(FontClassification.DEVANAGARI))
    }
}
