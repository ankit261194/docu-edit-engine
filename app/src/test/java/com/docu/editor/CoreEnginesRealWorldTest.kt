package com.docu.editor

import com.docu.editor.core.ai.DocumentChatEngine
import com.docu.editor.core.classification.DocumentClassificationEngine
import com.docu.editor.core.classification.ExpiryWatchdogEngine
import com.docu.editor.core.expense.ExpenseCategory
import com.docu.editor.core.expense.ReceiptEntityExtractor
import com.docu.editor.core.export.AutoMergeCompressorEngine
import com.docu.editor.core.layout.StandardPageSize
import com.docu.editor.core.ocr.DocumentSearchEngine
import com.docu.editor.core.ocr.util.DevanagariPostProcessor
import com.docu.editor.core.ocr.util.RichMarkdownConverter
import com.docu.editor.core.vault.VaultCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEnginesRealWorldTest {

    // ---------------------------------------------------------
    // 1. DocumentClassificationEngine Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testClassifyAadhaarDocument() {
        val ocr = "Government of India Unique Identification Authority of India 1234 5678 9012 Mera Aadhaar Meri Pehchan"
        val result = DocumentClassificationEngine.classify(ocr, "My Aadhaar")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.IDENTITY, result.category)
        assertTrue(result.subtype.contains("Aadhaar", ignoreCase = true))
    }

    @Test
    fun testClassifyPanCard() {
        val ocr = "INCOME TAX DEPARTMENT GOVT OF INDIA Permanent Account Number ABCDE1234F Father's Name Date of Birth"
        val result = DocumentClassificationEngine.classify(ocr, "PAN_Card_Scan")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.IDENTITY, result.category)
        assertTrue(result.subtype.contains("PAN", ignoreCase = true))
    }

    @Test
    fun testClassifyDrivingLicense() {
        val ocr = "UNION OF INDIA DRIVING LICENCE DL14 20110012345 Valid Till 2035 Transport Department"
        val result = DocumentClassificationEngine.classify(ocr, "Driving Licence")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.IDENTITY, result.category)
        assertTrue(result.subtype.contains("Driving License", ignoreCase = true))
    }

    @Test
    fun testClassifyPassport() {
        val ocr = "REPUBLIC OF INDIA PASSPORT P<INDSHARMA<<RAHUL<<<<<<< Z1234567 19900101"
        val result = DocumentClassificationEngine.classify(ocr, "Passport Document")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.IDENTITY, result.category)
        assertTrue(result.subtype.contains("Passport", ignoreCase = true))
    }

    @Test
    fun testClassifyMedicalPrescription() {
        val ocr = "Dr. Mehta Clinic MBBS MD Rx Paracetamol 650mg TDS x 3 days Amoxicillin Diagnosis: Viral Fever"
        val result = DocumentClassificationEngine.classify(ocr, "Doctor Prescription")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.HEALTH, result.category)
        assertTrue(result.subtype.contains("Prescription", ignoreCase = true))
    }

    @Test
    fun testClassifyAcademicMarkSheet() {
        val ocr = "CENTRAL BOARD OF SECONDARY EDUCATION SENIOR SCHOOL CERTIFICATE EXAMINATION MARKS STATEMENT Roll No CGPA Grade"
        val result = DocumentClassificationEngine.classify(ocr, "Class 12 Marksheet")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.ACADEMICS, result.category)
    }

    @Test
    fun testClassifyTaxInvoice() {
        val ocr = "TAX INVOICE GSTIN: 27AAAAA0000A1Z5 HSN Code Description Qty Rate Total Amount CGST SGST Net Payable"
        val result = DocumentClassificationEngine.classify(ocr, "Tax Invoice 2026")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.FINANCE, result.category)
        assertTrue(result.subtype.contains("Invoice", ignoreCase = true))
    }

    @Test
    fun testClassifyEmptyAndUnknownText() {
        val emptyResult = DocumentClassificationEngine.classify("", "")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.GENERAL, emptyResult.category)

        val randomResult = DocumentClassificationEngine.classify("Random unformatted text without recognizable keywords", "Notes")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.GENERAL, randomResult.category)
    }

    // ---------------------------------------------------------
    // 2. ExpiryWatchdogEngine Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testDetectValidFutureExpiry() {
        val ocr = "Driving License DL-9921 Valid Till: 25/12/2030 Holder Name: Ankit"
        val result = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue("Should detect expiry keyword and date", result.hasExpiry)
        assertTrue("Days remaining should be positive for future date", result.daysRemaining > 0)
        assertEquals(ExpiryWatchdogEngine.ExpiryStatus.VALID, result.status)
    }

    @Test
    fun testDetectPastExpiredDate() {
        val ocr = "Insurance Policy #98231 Expiry Date: 01/01/2020 Premium Paid"
        val result = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue("Should detect expiry", result.hasExpiry)
        assertEquals(ExpiryWatchdogEngine.ExpiryStatus.EXPIRED, result.status)
        assertTrue("Days remaining should be negative for past date", result.daysRemaining < 0)
    }

    @Test
    fun testDetectDueDateInBill() {
        val ocr = "Electricity Board Bill Due Date: 2026-11-20 Total Rs. 1450"
        val result = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue("Should detect due date", result.hasExpiry)
    }

    @Test
    fun testEmptyExpiryTextReturnsFalse() {
        val result = ExpiryWatchdogEngine.extractExpiry("")
        assertFalse("Empty string must have no expiry", result.hasExpiry)
        assertEquals(ExpiryWatchdogEngine.ExpiryStatus.NONE, result.status)
    }

    // ---------------------------------------------------------
    // 3. ReceiptEntityExtractor Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testExtractReceiptEntities() {
        val ocr = """
            STARBUCKS COFFEE INDIA
            GSTIN: 27AABCS1429B1Z8
            Invoice No: INV-2026-8812
            Date: 15/08/2026
            Cappuccino Grande  Rs. 320.00
            Butter Croissant   Rs. 240.00
            Total Amount: Rs. 560.00
        """.trimIndent()

        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals("27AABCS1429B1Z8", receipt.gstin)
        assertEquals("INV-2026-8812", receipt.invoiceNumber)
        assertTrue(receipt.grandTotal > 0.0)
        assertEquals(560.00, receipt.grandTotal, 0.01)
        assertTrue(receipt.merchantName.contains("STARBUCKS", ignoreCase = true))
    }

    // ---------------------------------------------------------
    // 4. DocumentChatEngine Context & Citations Tests
    // ---------------------------------------------------------

    @Test
    fun testBuildDocumentContextMultiPage() {
        val pages = listOf(
            0 to "First page content with overview.",
            1 to "Second page content with financial numbers."
        )
        val context = DocumentChatEngine.buildDocumentContext(pages, "Annual Report")
        assertTrue(context.contains("=== DOCUMENT: Annual Report"))
        assertTrue(context.contains("--- [PAGE 1] ---"))
        assertTrue(context.contains("--- [PAGE 2] ---"))
        assertTrue(context.contains("First page content"))
        assertTrue(context.contains("Second page content"))
    }

    @Test
    fun testParseCitations() {
        val assistantResponse = "As stated in [Page 1], the policy begins immediately. Furthermore, [Page 2, Line 4] confirms the renewal criteria."
        val citations = DocumentChatEngine.parseCitations(assistantResponse, totalPages = 2)
        assertEquals(2, citations.size)
        assertEquals(0, citations[0].pageIndex)
        assertEquals("Page 1", citations[0].displayLabel)
        assertEquals(1, citations[1].pageIndex)
        assertEquals("Page 2", citations[1].displayLabel)
    }

    // ---------------------------------------------------------
    // 5. VaultCryptoEngine EncryptedPayload Tests
    // ---------------------------------------------------------

    @Test
    fun testEncryptedPayloadSerializationRoundtrip() {
        val iv = ByteArray(12) { (it + 1).toByte() }
        val cipher = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80)
        val payload = VaultCryptoEngine.EncryptedPayload(iv, cipher)

        val serialized = payload.toCombinedByteArray()
        assertEquals(20, serialized.size)

        val deserialized = VaultCryptoEngine.EncryptedPayload.fromCombinedByteArray(serialized)
        assertEquals(12, deserialized.iv.size)
        assertEquals(8, deserialized.ciphertext.size)
        for (i in 0 until 12) {
            assertEquals(iv[i], deserialized.iv[i])
        }
        for (i in 0 until 8) {
            assertEquals(cipher[i], deserialized.ciphertext[i])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEncryptedPayloadInvalidLengthThrows() {
        val corrupt = ByteArray(5) // Less than 12-byte IV
        VaultCryptoEngine.EncryptedPayload.fromCombinedByteArray(corrupt)
    }

    // ---------------------------------------------------------
    // 6. Additional DocumentClassification Edge Case Tests
    // ---------------------------------------------------------

    @Test
    fun testClassifyAadhaarWithHyphens() {
        val ocr = "Government of India Unique Identification Authority 1234-5678-9012"
        val result = DocumentClassificationEngine.classify(ocr, "My Identity")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.IDENTITY, result.category)
        assertTrue(result.subtype.contains("Aadhaar", ignoreCase = true))
    }

    @Test
    fun testClassifyPanCardLowercase() {
        val ocr = "income tax department govt of india abcde1234f father's name"
        val result = DocumentClassificationEngine.classify(ocr, "pan_scan")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.IDENTITY, result.category)
        assertTrue(result.subtype.contains("PAN", ignoreCase = true))
    }

    @Test
    fun testClassifyLegalAffidavit() {
        val ocr = "IN THE COURT OF NOTARY PUBLIC SWORN BEFORE ME DEPONENT SOLEMNLY AFFIRM AFFIDAVIT NON JUDICIAL STAMP PAPER"
        val result = DocumentClassificationEngine.classify(ocr, "Legal Doc")
        assertEquals(DocumentClassificationEngine.CanonicalCategory.LEGAL, result.category)
        assertTrue(result.subtype.contains("Affidavit", ignoreCase = true))
    }

    // ---------------------------------------------------------
    // 7. Additional ExpiryWatchdog Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testDetectMonthYearExpiryMedicine() {
        val ocr = "Crocin 650mg Paracetamol Tab B.No: 9912 Exp: 12/2028 Mfd: 01/2024"
        val result = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue("Should detect MM/yyyy expiry in medicine", result.hasExpiry)
        assertTrue("Days remaining should be positive", result.daysRemaining > 0)
        assertEquals(ExpiryWatchdogEngine.ExpiryStatus.VALID, result.status)
    }

    @Test
    fun testDetectMonthYearExpiryCard() {
        val ocr = "HDFC Bank Platinum Debit Card Valid Thru: 05/29 Cardholder Name"
        val result = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue("Should detect MM/yy expiry on debit card", result.hasExpiry)
        assertTrue(result.daysRemaining > 0)
    }

    @Test
    fun testDetectBestBeforeFMCG() {
        val ocr = "Amul Butter Pasteurized Best Before: 10/2030 Net Wt 500g"
        val result = ExpiryWatchdogEngine.extractExpiry(ocr)
        assertTrue("Should detect Best Before keyword", result.hasExpiry)
        assertTrue(result.daysRemaining > 0)
    }

    // ---------------------------------------------------------
    // 8. Additional ReceiptEntityExtractor Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testExtractReceiptWithTrailingQuantityOnTotalLine() {
        // Bug test: Trailing item count "Qty: 2" must not overwrite 560.00
        val ocr = """
            RESTAURANT DELIGHT
            Grand Total: 560.00 Qty: 2
            Table: 4
        """.trimIndent()
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals(560.00, receipt.grandTotal, 0.01)
    }

    @Test
    fun testExtractReceiptWithUnpunctuatedTotal() {
        // Bug test: "TOTAL 450.00" without colon must be correctly extracted
        val ocr = """
            CAFE COFFEE DAY
            TOTAL 450.00
            Cash Paid: 500.00
        """.trimIndent()
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals(450.00, receipt.grandTotal, 0.01)
    }

    @Test
    fun testExtractReceiptWithSpacedAndHyphenatedGSTIN() {
        // Bug test: OCR segmented GSTIN with spaces or hyphens
        val ocr1 = "Store Name GSTIN: 27 AABCS1429B1Z8 Total Rs. 100"
        val receipt1 = ReceiptEntityExtractor.extract(ocr1)
        assertEquals("27AABCS1429B1Z8", receipt1.gstin)

        val ocr2 = "Retail Shop GSTIN: 27-AABCS1429B1Z8 Total Rs. 100"
        val receipt2 = ReceiptEntityExtractor.extract(ocr2)
        assertEquals("27AABCS1429B1Z8", receipt2.gstin)
    }

    @Test
    fun testExtractReceiptMultiLineTotal() {
        // Lookahead test: "Total Amount" on line 1, "Rs. 1,450.00" on line 2
        val ocr = """
            RETAIL MART
            Total Amount
            Rs. 1,450.00
        """.trimIndent()
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals(1450.00, receipt.grandTotal, 0.01)
    }

    @Test
    fun testExtractReceiptFuelCategory() {
        val ocr = "INDIAN OIL CORPORATION LTD AUTO LPG PETROL PUMP NOZZLE 2 DENSITY 734 TOTAL Rs. 2000.00"
        val receipt = ReceiptEntityExtractor.extract(ocr)
        assertEquals(ExpenseCategory.FUEL, receipt.category)
        assertEquals(2000.00, receipt.grandTotal, 0.01)
    }

    // ---------------------------------------------------------
    // 9. DevanagariPostProcessor Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testDevanagariDisconnectedMatras() {
        val broken = "भ ारत क ो प्रमाण"
        val corrected = DevanagariPostProcessor.postProcess(broken)
        assertEquals("भारत को प्रमाण", corrected)
    }

    @Test
    fun testDevanagariGovtDictionaryAcrossNewline() {
        val broken = "प्रमाणपन्र\nपिता का नाम\nजन्मतिथी"
        val corrected = DevanagariPostProcessor.postProcess(broken)
        assertTrue(corrected.contains("प्रमाणपत्र"))
        assertTrue(corrected.contains("जन्मतिथि"))
    }

    @Test
    fun testDevanagariPipeToPurnaViram() {
        val raw = "सत्यमेव जयते |"
        val corrected = DevanagariPostProcessor.postProcess(raw)
        assertTrue("Pipe should convert to Purna Viram", corrected.contains("।"))
    }

    // ---------------------------------------------------------
    // 10. RichMarkdownConverter Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testRichMarkdownPlainTextConversion() {
        val input = """
            ANNUAL REPORT 2026
            • First objective completed
            • Second objective in progress
            Project Lead: Dr. Sharma
            Total Budget: INR 50,00,000
        """.trimIndent()

        val md = RichMarkdownConverter.convertPlainTextToMarkdown(input)
        assertTrue("Should detect ALL CAPS header", md.contains("## ANNUAL REPORT 2026"))
        assertTrue("Should format bullet points", md.contains("- First objective completed"))
        assertTrue("Should format bullet points", md.contains("- Second objective in progress"))
        assertTrue("Should format key-value bolding", md.contains("**Project Lead:** Dr. Sharma"))
        assertTrue("Should format key-value bolding", md.contains("**Total Budget:** INR 50,00,000"))
    }

    // ---------------------------------------------------------
    // 11. PageSizeEngine Dimension Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testStandardPageSizeAspectRatios() {
        // A4 210 x 297 mm
        val a4 = StandardPageSize.A4
        assertEquals(210f, a4.widthMm, 0.01f)
        assertEquals(297f, a4.heightMm, 0.01f)
        assertTrue("A4 height should exceed width", a4.pixelHeight300Dpi > a4.pixelWidth300Dpi)

        // Stamp Paper
        val stamp = StandardPageSize.GOVT_STAMP_PAPER
        assertEquals(215.9f, stamp.widthMm, 0.01f)
        assertEquals(355.6f, stamp.heightMm, 0.01f)

        // CR80 ID Card (85.6 x 54 mm)
        val cr80 = StandardPageSize.ID_CARD_CR80
        assertEquals(85.6f, cr80.widthMm, 0.01f)
        assertEquals(54f, cr80.heightMm, 0.01f)
    }

    // ---------------------------------------------------------
    // 12. AutoMergeCompressorEngine Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testAutoMergeCompressorPreFlightEstimates() {
        val fakeDoc = AutoMergeCompressorEngine.MergeInputDocument(
            uri = null,
            displayName = "Marksheet.pdf",
            fileSizeBytes = 500 * 1024L,
            mimeType = "application/pdf",
            pageCount = 2
        )

        // Generous budget: 2 pages in 500 KB (~250 KB/page) -> EXCELLENT
        val estimateGenerous = AutoMergeCompressorEngine.estimatePreFlightQuality(
            documents = listOf(fakeDoc),
            targetKb = 500,
            strategy = AutoMergeCompressorEngine.CompressionStrategy.SMART_ADAPTIVE
        )
        assertEquals(AutoMergeCompressorEngine.ReadabilityGrade.EXCELLENT, estimateGenerous.readabilityGrade)
        assertTrue(estimateGenerous.spaceReductionPercent >= 0f)

        // Tight budget: 2 pages in 30 KB (~15 KB/page) -> TIGHT_WARNING
        val estimateTight = AutoMergeCompressorEngine.estimatePreFlightQuality(
            documents = listOf(fakeDoc),
            targetKb = 30,
            strategy = AutoMergeCompressorEngine.CompressionStrategy.SMART_ADAPTIVE
        )
        assertEquals(AutoMergeCompressorEngine.ReadabilityGrade.TIGHT_WARNING, estimateTight.readabilityGrade)
    }

    // ---------------------------------------------------------
    // 13. DocumentSearchEngine Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testDocumentSearchEngineNormalization() {
        // Tolerates invisible zero-width chars and case sensitivity
        val raw = "Government\u200B of India"
        val normalized = DocumentSearchEngine.normalize(raw, isCaseSensitive = false, isHindiTolerant = true)
        assertEquals("government of india", normalized)

        // Case-sensitive preservation
        val caseNorm = DocumentSearchEngine.normalize("Tax Invoice", isCaseSensitive = true, isHindiTolerant = false)
        assertEquals("Tax Invoice", caseNorm)
    }

    // ---------------------------------------------------------
    // 14. WatermarkEngine Real-World Macro Tests
    // ---------------------------------------------------------

    @Test
    fun testWatermarkMacrosResolution() {
        val template = "CONFIDENTIAL - {DOC_TITLE} - Page {PAGE} of {TOTAL_PAGES}"
        val resolved = com.docu.editor.core.watermark.WatermarkEngine.resolveMacros(
            rawText = template,
            pageIndex = 2,
            totalPages = 10,
            docTitle = "Financial Audit"
        )
        assertTrue("Should replace doc title macro", resolved.contains("Financial Audit"))
        assertTrue("Should replace page index with 1-based page number", resolved.contains("Page 3 of 10"))
    }

    // ---------------------------------------------------------
    // 15. VaultCryptoEngine Salted Hashing Tests
    // ---------------------------------------------------------

    @Test
    fun testVaultPinSaltedHashing() {
        val salt1 = VaultCryptoEngine.generateSalt()
        val salt2 = VaultCryptoEngine.generateSalt()
        val pin = "8821"
        val hash1 = VaultCryptoEngine.hashPin(pin, salt1)
        val hash2 = VaultCryptoEngine.hashPin(pin, salt2)
        val hash1Repeat = VaultCryptoEngine.hashPin(pin, salt1)

        assertEquals("Same PIN and salt must produce identical hash", hash1, hash1Repeat)
        assertFalse("Different salts must produce distinct hashes", hash1 == hash2)
        assertEquals("SHA-256 Base64 hash length should be 44 chars", 44, hash1.length)
    }

    // ---------------------------------------------------------
    // 16. IdCardStitcher Paper Size Real-World Tests
    // ---------------------------------------------------------

    @Test
    fun testIdCardStitcherPaperDimensions() {
        val a4 = com.docu.editor.core.scanner.IdCardStitcher.PaperSize.A4
        assertEquals(2480, a4.widthPx)
        assertEquals(3508, a4.heightPx)
        val usLetter = com.docu.editor.core.scanner.IdCardStitcher.PaperSize.US_LETTER
        assertEquals(2550, usLetter.widthPx)
        assertEquals(3300, usLetter.heightPx)
    }
}
