package com.docu.editor.core.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.random.Random

enum class SampleTemplateCategory(val displayName: String) {
    BUSINESS("💼 Business & Finance"),
    STUDENTS("🎓 Students & Academics"),
    LEGAL("⚖️ Legal & Govt"),
    MEDICAL("🏥 Medical & Health")
}

data class SampleTemplateItem(
    val id: String,
    val title: String,
    val category: SampleTemplateCategory,
    val description: String,
    val generator: () -> Bitmap
)

/**
 * Enterprise Multi-Category Interactive Sample Document Generator.
 * Renders authentic, high-resolution document templates for trial without needing a physical camera:
 * - 10 Real-world templates across Business, Academics, Legal, and Healthcare.
 */
object SampleDocumentGenerator {

    fun getAllTemplates(): List<SampleTemplateItem> = listOf(
        // Business
        SampleTemplateItem(
            id = "biz_invoice",
            title = "Tax Invoice & Commercial Bill",
            category = SampleTemplateCategory.BUSINESS,
            description = "Billed to, items table, GSTIN, amount due, and official company stamp.",
            generator = { generateSampleInvoice() }
        ),
        SampleTemplateItem(
            id = "biz_po",
            title = "Corporate Purchase Order",
            category = SampleTemplateCategory.BUSINESS,
            description = "Vendor details, delivery schedule, itemized supply list, and approvals.",
            generator = { generatePurchaseOrder() }
        ),
        SampleTemplateItem(
            id = "biz_cash_memo",
            title = "Retail Cash Memo & Receipt",
            category = SampleTemplateCategory.BUSINESS,
            description = "Store details, scanned barcode, item quantities, and return policy.",
            generator = { generateCashMemo() }
        ),

        // Students / Academics
        SampleTemplateItem(
            id = "acad_marksheet",
            title = "University Mark Sheet & Grade Card",
            category = SampleTemplateCategory.STUDENTS,
            description = "Semester grades, credits, subjects list, percentage, and controller stamp.",
            generator = { generateUniversityMarksheet() }
        ),
        SampleTemplateItem(
            id = "acad_notes",
            title = "Handwritten Assignment & Notes",
            category = SampleTemplateCategory.STUDENTS,
            description = "Lined paper notebook, math formulas, handwritten ink equations.",
            generator = { generateHandwrittenNotes() }
        ),
        SampleTemplateItem(
            id = "acad_admit_card",
            title = "National Exam Admit Card",
            category = SampleTemplateCategory.STUDENTS,
            description = "Roll number, examination venue, candidate photo slot, and instructions.",
            generator = { generateExamAdmitCard() }
        ),

        // Legal & Govt
        SampleTemplateItem(
            id = "legal_nda",
            title = "Non-Disclosure Agreement (NDA)",
            category = SampleTemplateCategory.LEGAL,
            description = "Legal contract clauses, confidentiality terms, and dual signature blocks.",
            generator = { generateNdaAgreement() }
        ),
        SampleTemplateItem(
            id = "legal_affidavit",
            title = "Notary Sworn Affidavit",
            category = SampleTemplateCategory.LEGAL,
            description = "Government stamp paper header, verification clause, and notary gold seal.",
            generator = { generateAffidavit() }
        ),

        // Medical & Healthcare
        SampleTemplateItem(
            id = "med_rx",
            title = "Doctor Clinic Prescription Slip",
            category = SampleTemplateCategory.MEDICAL,
            description = "Physician header, patient vitals, Rx medicine dosage, and clinic seal.",
            generator = { generateDoctorPrescription() }
        ),
        SampleTemplateItem(
            id = "med_lab",
            title = "Pathology Diagnostic Lab Report",
            category = SampleTemplateCategory.MEDICAL,
            description = "Blood biochemistry test table, normal reference ranges, and pathologist signature.",
            generator = { generatePathologyReport() }
        )
    )

    // =========================================================================
    // 1. BUSINESS: Tax Invoice
    // =========================================================================
    fun generateSampleInvoice(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(250, 248, 243))
        drawDoubleBorder(canvas, width, height, Color.rgb(60, 70, 85))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 30, 45)
            textSize = 50f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TAX INVOICE & RECEIPT", width / 2f, 180f, titlePaint)

        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(85, 95, 110)
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("GLOBAL ENTERPRISES PRIVATE LIMITED", width / 2f, 225f, subTitlePaint)
        canvas.drawText("Invoice No: INV-2026-8891  |  Date: 28 Sep 2026", width / 2f, 265f, subTitlePaint)

        val linePaint = Paint().apply { color = Color.rgb(180, 185, 195); strokeWidth = 2f }
        canvas.drawLine(100f, 305f, width - 100f, 305f, linePaint)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 40, 55); textSize = 30f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("BILLED TO:", 100f, 365f, headerPaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 45, 55); textSize = 26f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("Client Name: Ankit Sharma", 100f, 410f, bodyPaint)
        canvas.drawText("Company: Apex Digital Solutions", 100f, 455f, bodyPaint)
        canvas.drawText("Location: New Delhi, India - 110001", 100f, 500f, bodyPaint)
        canvas.drawText("GSTIN: 07AAAAA0000A1Z5", 100f, 545f, bodyPaint)

        // Table
        val tableTop = 620f
        canvas.drawLine(100f, tableTop, width - 100f, tableTop, linePaint)
        canvas.drawText("Item Description", 120f, tableTop + 45f, headerPaint)
        canvas.drawText("Qty", 700f, tableTop + 45f, headerPaint)
        canvas.drawText("Amount (USD)", 920f, tableTop + 45f, headerPaint)
        canvas.drawLine(100f, tableTop + 70f, width - 100f, tableTop + 70f, linePaint)

        var rowY = tableTop + 125f
        val items = listOf(
            Triple("Cloud Architecture Consulting", "1", "$ 4,500.00"),
            Triple("Document AI Engine License", "1", "$ 2,800.00"),
            Triple("Annual Security Maintenance", "1", "$ 1,200.00"),
            Triple("Full Stack Integration Services", "2", "$ 3,000.00")
        )
        for (item in items) {
            canvas.drawText(item.first, 120f, rowY, bodyPaint)
            canvas.drawText(item.second, 720f, rowY, bodyPaint)
            canvas.drawText(item.third, 920f, rowY, bodyPaint)
            rowY += 60f
        }
        canvas.drawLine(100f, rowY, width - 100f, rowY, linePaint)

        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 20, 30); textSize = 32f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Total Amount Due: $ 11,500.00", 600f, rowY + 65f, totalPaint)

        drawStampAndSignature(canvas, width, 1380f, "OFFICIALLY", "VERIFIED")
        return bitmap
    }

    // =========================================================================
    // 2. BUSINESS: Corporate Purchase Order
    // =========================================================================
    fun generatePurchaseOrder(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(252, 252, 253))
        drawDoubleBorder(canvas, width, height, Color.rgb(30, 58, 138))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 58, 138); textSize = 48f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("PURCHASE ORDER", width / 2f, 170f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105); textSize = 24f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("PO NUMBER: PO-2026-9042  |  DATE: October 08, 2026", width / 2f, 215f, subPaint)

        val linePaint = Paint().apply { color = Color.rgb(203, 213, 225); strokeWidth = 2f }
        canvas.drawLine(100f, 250f, width - 100f, 250f, linePaint)

        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = 28f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85); textSize = 24f
        }

        canvas.drawText("VENDOR DETAILS:", 100f, 310f, hPaint)
        canvas.drawText("Logitech Electronics International", 100f, 350f, bPaint)
        canvas.drawText("Ship To: DocuEdit Technology HQ, Floor 4", 100f, 390f, bPaint)
        canvas.drawText("Payment Terms: Net 30 Days", 100f, 430f, bPaint)

        var y = 520f
        canvas.drawLine(100f, y, width - 100f, y, linePaint)
        canvas.drawText("Item / SKU", 120f, y + 40f, hPaint)
        canvas.drawText("Qty", 720f, y + 40f, hPaint)
        canvas.drawText("Unit Price", 900f, y + 40f, hPaint)
        canvas.drawLine(100f, y + 65f, width - 100f, y + 65f, linePaint)

        val poItems = listOf(
            Triple("Dell UltraSharp 27\" 4K Monitors", "10", "$ 5,200.00"),
            Triple("Ergonomic Mesh Office Chairs", "15", "$ 4,350.00"),
            Triple("Mechanical Keyboards & Mice", "20", "$ 1,800.00"),
            Triple("High-Speed Wi-Fi 6 Routers", "4", "$ 1,120.00")
        )
        y += 120f
        for (item in poItems) {
            canvas.drawText(item.first, 120f, y, bPaint)
            canvas.drawText(item.second, 740f, y, bPaint)
            canvas.drawText(item.third, 910f, y, bPaint)
            y += 65f
        }
        canvas.drawLine(100f, y, width - 100f, y, linePaint)
        canvas.drawText("Grand Total: $ 12,470.00", 660f, y + 65f, hPaint)

        drawStampAndSignature(canvas, width, 1380f, "APPROVED", "PURCHASE")
        return bitmap
    }

    // =========================================================================
    // 3. BUSINESS: Retail Cash Memo
    // =========================================================================
    fun generateCashMemo(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(255, 255, 250))
        drawDoubleBorder(canvas, width, height, Color.rgb(15, 118, 110))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 118, 110); textSize = 46f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SUPERMART RETAIL CASH MEMO", width / 2f, 160f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139); textSize = 22f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Store #048  •  GSTIN: 07BBNPP8899K1Z2  •  Tel: 011-23456789", width / 2f, 205f, subPaint)

        val linePaint = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 2f }
        canvas.drawLine(100f, 240f, width - 100f, 240f, linePaint)

        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 41, 59); textSize = 26f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 65, 85); textSize = 23f }

        var y = 310f
        val memoItems = listOf(
            Triple("A4 Printer Paper (500 Sheets)", "4 Reams", "₹ 1,200"),
            Triple("Highlighter Pen Set (6 Colors)", "2 Packs", "₹ 360"),
            Triple("Permanent Marker Black", "5 Pcs", "₹ 250"),
            Triple("Document Folder Case", "3 Pcs", "₹ 450"),
            Triple("Correction Fluid Pen", "2 Pcs", "₹ 140")
        )
        for (item in memoItems) {
            canvas.drawText(item.first, 120f, y, bPaint)
            canvas.drawText(item.second, 680f, y, bPaint)
            canvas.drawText(item.third, 940f, y, hPaint)
            y += 55f
        }

        canvas.drawLine(100f, y + 20f, width - 100f, y + 20f, linePaint)
        canvas.drawText("NET TOTAL PAID: ₹ 2,400.00", 600f, y + 75f, hPaint)

        // Draw Barcode lines for retail feel
        val barcodePaint = Paint().apply { color = Color.rgb(15, 23, 42); strokeWidth = 4f }
        val r = Random(99)
        val bcY = y + 160f
        for (bx in 250 until 950 step 8) {
            barcodePaint.strokeWidth = if (r.nextBoolean()) 5f else 2f
            canvas.drawLine(bx.toFloat(), bcY, bx.toFloat(), bcY + 80f, barcodePaint)
        }
        val bcText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105); textSize = 22f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("8 901030 456789", width / 2f, bcY + 115f, bcText)

        return bitmap
    }

    // =========================================================================
    // 4. STUDENTS: University Mark Sheet
    // =========================================================================
    fun generateUniversityMarksheet(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(254, 252, 245))
        drawDoubleBorder(canvas, width, height, Color.rgb(120, 53, 15))

        val uniPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 53, 15); textSize = 44f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("DELHI CENTRAL UNIVERSITY", width / 2f, 160f, uniPaint)

        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(67, 56, 202); textSize = 32f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("STATEMENT OF MARKS & GRADES", width / 2f, 210f, markPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(78, 60, 40); textSize = 24f; textAlign = Paint.Align.CENTER }
        canvas.drawText("Bachelor of Technology (Computer Science) - Semester VI", width / 2f, 250f, subPaint)

        val linePaint = Paint().apply { color = Color.rgb(217, 119, 6); strokeWidth = 2f }
        canvas.drawLine(100f, 280f, width - 100f, 280f, linePaint)

        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 30, 20); textSize = 24f }
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 20); textSize = 26f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }

        canvas.drawText("Student Name: Rahul Verma", 100f, 330f, hPaint)
        canvas.drawText("Roll Number: 2023CS88910", 100f, 370f, bPaint)
        canvas.drawText("Enrollment No: EN-1982736", 700f, 370f, bPaint)

        var y = 440f
        canvas.drawLine(100f, y, width - 100f, y, linePaint)
        canvas.drawText("Subject Code & Name", 120f, y + 35f, hPaint)
        canvas.drawText("Max", 680f, y + 35f, hPaint)
        canvas.drawText("Obt", 820f, y + 35f, hPaint)
        canvas.drawText("Grade", 960f, y + 35f, hPaint)
        canvas.drawLine(100f, y + 60f, width - 100f, y + 60f, linePaint)

        val subjects = listOf(
            listOf("CS601: Distributed Systems", "100", "88", "A+"),
            listOf("CS602: Computer Vision & AI", "100", "94", "O"),
            listOf("CS603: Compiler Design", "100", "82", "A"),
            listOf("CS604: Information Security", "100", "89", "A+"),
            listOf("CS605: Software Engineering", "100", "91", "O")
        )
        y += 110f
        for (sub in subjects) {
            canvas.drawText(sub[0], 120f, y, bPaint)
            canvas.drawText(sub[1], 685f, y, bPaint)
            canvas.drawText(sub[2], 825f, y, bPaint)
            canvas.drawText(sub[3], 970f, y, hPaint)
            y += 55f
        }
        canvas.drawLine(100f, y, width - 100f, y, linePaint)
        canvas.drawText("RESULT: PASS WITH DISTINCTION (SGPA: 9.28)", 120f, y + 60f, hPaint)

        drawStampAndSignature(canvas, width, 1380f, "CONTROLLER", "OF EXAMS")
        return bitmap
    }

    // =========================================================================
    // 5. STUDENTS: Handwritten Notes
    // =========================================================================
    fun generateHandwrittenNotes(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Lined notebook page
        drawPaperBackground(canvas, width, height, Color.rgb(253, 252, 248))

        // Red margin line on left
        val marginPaint = Paint().apply { color = Color.rgb(239, 68, 68); strokeWidth = 3f }
        canvas.drawLine(160f, 0f, 160f, height.toFloat(), marginPaint)

        // Notebook blue horizontal lines
        val linePaint = Paint().apply { color = Color.rgb(191, 219, 254); strokeWidth = 1.5f }
        for (ly in 120 until height step 45) {
            canvas.drawLine(0f, ly.toFloat(), width.toFloat(), ly.toFloat(), linePaint)
        }

        // Handwritten Ink (Blue Fountain Pen)
        val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 64, 175)
            textSize = 34f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val headingPen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 38f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        canvas.drawText("Physics: Quantum Mechanics & Wave Theory", 190f, 110f, headingPen)
        canvas.drawText("Date: 08-10-2026  |  Lecture 14", 800f, 110f, penPaint)

        var y = 200f
        val notesLines = listOf(
            "1. De Broglie Wavelength Equation:",
            "   lambda = h / p = h / (m * v)",
            "2. Schrodinger Wave Equation (Time Independent):",
            "   -(hbar^2 / 2m) * (d^2 psi / dx^2) + V * psi = E * psi",
            "3. Heisenberg Uncertainty Principle:",
            "   Delta x * Delta p >= hbar / 2",
            "Key Note: The wave function psi represents probability amplitude."
        )
        for (line in notesLines) {
            canvas.drawText(line, 190f, y, penPaint)
            y += 90f
        }

        return bitmap
    }

    // =========================================================================
    // 6. STUDENTS: Examination Admit Card
    // =========================================================================
    fun generateExamAdmitCard(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(255, 255, 255))
        drawDoubleBorder(canvas, width, height, Color.rgb(30, 41, 59))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = 44f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("NATIONAL TESTING AGENCY (NTA)", width / 2f, 160f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(2, 132, 199); textSize = 32f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("E-ADMIT CARD & HALL TICKET 2026", width / 2f, 210f, subPaint)

        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 65, 85); textSize = 25f }
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42); textSize = 26f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }

        canvas.drawText("Candidate Name: Priya Sharma", 100f, 310f, hPaint)
        canvas.drawText("Roll Number: DL01092837", 100f, 355f, hPaint)
        canvas.drawText("Date of Birth: 14-07-2003", 100f, 400f, bPaint)
        canvas.drawText("Exam Center: Delhi Public School, R.K. Puram", 100f, 445f, bPaint)
        canvas.drawText("Reporting Time: 08:30 AM IST", 100f, 490f, hPaint)

        // Photo slot box on top right
        val photoPaint = Paint().apply { color = Color.rgb(203, 213, 225); style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawRect(880f, 290f, 1080f, 540f, photoPaint)
        val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(148, 163, 184); textSize = 22f; textAlign = Paint.Align.CENTER }
        canvas.drawText("Affix Passport", 980f, 410f, pText)
        canvas.drawText("Photo Here", 980f, 440f, pText)

        drawStampAndSignature(canvas, width, 1380f, "EXAM CENTER", "SUPERINTENDENT")
        return bitmap
    }

    // =========================================================================
    // 7. LEGAL: Non-Disclosure Agreement (NDA)
    // =========================================================================
    fun generateNdaAgreement(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(250, 249, 245))
        drawDoubleBorder(canvas, width, height, Color.rgb(74, 4, 4))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(69, 10, 10); textSize = 44f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("MUTUAL NON-DISCLOSURE AGREEMENT", width / 2f, 160f, titlePaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59); textSize = 23f; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        var y = 260f
        val paragraphs = listOf(
            "This Agreement is entered into on 08 October 2026 by and between Party A",
            "and Party B, collectively referred to as the 'Parties'.",
            "",
            "1. Confidential Information: Each Party agrees to protect proprietary code,",
            "   business trade secrets, and technical designs disclosed hereunder.",
            "",
            "2. Non-Disclosure Obligations: The receiving party shall hold all data",
            "   in strict confidence for a period of five (5) years from disclosure.",
            "",
            "3. Governing Law: This Agreement shall be governed by the laws of India.",
            "",
            "IN WITNESS WHEREOF, the Parties have executed this Agreement."
        )
        for (p in paragraphs) {
            canvas.drawText(p, 100f, y, bodyPaint)
            y += 45f
        }

        drawStampAndSignature(canvas, width, 1380f, "SEALED &", "EXECUTED")
        return bitmap
    }

    // =========================================================================
    // 8. LEGAL: Notary Sworn Affidavit
    // =========================================================================
    fun generateAffidavit(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(253, 251, 244))
        drawDoubleBorder(canvas, width, height, Color.rgb(161, 98, 7))

        // Stamp Paper Header
        val stampHeaderPaint = Paint().apply { color = Color.rgb(180, 83, 9); style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawRect(80f, 80f, width - 80f, 260f, stampHeaderPaint)

        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 83, 9); textSize = 38f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("GOVERNMENT OF INDIA • NON-JUDICIAL STAMP", width / 2f, 150f, headerText)
        val valText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 53, 15); textSize = 26f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("INDIA STAMP DUTY RS. 100/-", width / 2f, 200f, valText)

        val affText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = 40f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SWORN AFFIDAVIT", width / 2f, 350f, affText)

        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59); textSize = 24f; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        var y = 440f
        val affLines = listOf(
            "I, Vikram Malhotra, aged 32 years, residing at Vasant Vihar, New Delhi,",
            "do hereby solemnly affirm and state on oath as follows:",
            "",
            "1. That I am the rightful applicant and legal holder of Passport Z891029.",
            "2. That all representations and submissions made herein are true and correct.",
            "3. That no relevant material fact has been concealed or omitted.",
            "",
            "Verified at New Delhi on this 8th day of October, 2026."
        )
        for (l in affLines) {
            canvas.drawText(l, 100f, y, bPaint)
            y += 50f
        }

        drawStampAndSignature(canvas, width, 1380f, "NOTARY PUBLIC", "GOVT OF INDIA")
        return bitmap
    }

    // =========================================================================
    // 9. MEDICAL: Doctor Clinic Prescription
    // =========================================================================
    fun generateDoctorPrescription(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(255, 255, 255))
        drawDoubleBorder(canvas, width, height, Color.rgb(2, 132, 199))

        val docPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(3, 105, 161); textSize = 42f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("DR. ARVIND MEHTA, M.D. (MEDICINE)", width / 2f, 150f, docPaint)

        val qualPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105); textSize = 22f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Senior Consultant Physician  •  Reg No: DMC-89210", width / 2f, 195f, qualPaint)
        canvas.drawText("Apollo Clinic & Heart Center, Saket, New Delhi", width / 2f, 230f, qualPaint)

        val linePaint = Paint().apply { color = Color.rgb(2, 132, 199); strokeWidth = 2f }
        canvas.drawLine(100f, 260f, width - 100f, 260f, linePaint)

        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 41, 59); textSize = 24f }
        canvas.drawText("Patient Name: Mrs. Sunita Roy (Age: 46 / F)", 100f, 310f, bPaint)
        canvas.drawText("Date: 08-Oct-2026  |  BP: 124/82 mmHg  |  Pulse: 74 bpm", 100f, 350f, bPaint)
        canvas.drawLine(100f, 380f, width - 100f, 380f, linePaint)

        // Rx Symbol
        val rxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(3, 105, 161); textSize = 68f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("℞", 100f, 470f, rxPaint)

        val medPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = 27f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val dosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105); textSize = 22f
        }

        var y = 520f
        val meds = listOf(
            Pair("1. Tab. Pantoprazole 40 mg", "1 tablet daily empty stomach in the morning x 14 days"),
            Pair("2. Tab. Amoxicillin + Clavulanic Acid 625 mg", "1 tablet twice daily after meals x 5 days"),
            Pair("3. Tab. Paracetamol 650 mg", "1 tablet SOS if fever > 100°F (max 3/day)"),
            Pair("4. Syrup Ambroxol + Levosalbutamol", "10 ml thrice daily after meals x 7 days")
        )
        for ((m, d) in meds) {
            canvas.drawText(m, 120f, y, medPaint)
            canvas.drawText(d, 150f, y + 36f, dosePaint)
            y += 85f
        }

        drawStampAndSignature(canvas, width, 1380f, "APOLLO CLINIC", "VERIFIED Rx")
        return bitmap
    }

    // =========================================================================
    // 10. MEDICAL: Pathology Diagnostic Lab Report
    // =========================================================================
    fun generatePathologyReport(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaperBackground(canvas, width, height, Color.rgb(255, 255, 255))
        drawDoubleBorder(canvas, width, height, Color.rgb(22, 101, 52))

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 101, 52); textSize = 44f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("METROPOLIS DIAGNOSTICS & LABS", width / 2f, 150f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105); textSize = 22f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ISO 15189 Accredited Laboratory  •  NABL Accredited", width / 2f, 195f, subPaint)

        val linePaint = Paint().apply { color = Color.rgb(22, 101, 52); strokeWidth = 2f }
        canvas.drawLine(100f, 240f, width - 100f, 240f, linePaint)

        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 23, 42); textSize = 24f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 65, 85); textSize = 22f }

        canvas.drawText("Patient: Amit Singhal  |  Age: 38/M", 100f, 290f, hPaint)
        canvas.drawText("Ref By: Dr. S. Kapoor  |  Sample: Serum Blood", 100f, 330f, bPaint)

        var y = 410f
        canvas.drawLine(100f, y, width - 100f, y, linePaint)
        canvas.drawText("Investigation Test", 120f, y + 35f, hPaint)
        canvas.drawText("Observed Value", 620f, y + 35f, hPaint)
        canvas.drawText("Biological Ref. Range", 880f, y + 35f, hPaint)
        canvas.drawLine(100f, y + 60f, width - 100f, y + 60f, linePaint)

        val tests = listOf(
            listOf("Fasting Blood Sugar (Glucose)", "96.4 mg/dL", "70.0 - 100.0 mg/dL"),
            listOf("Serum Creatinine", "0.92 mg/dL", "0.70 - 1.30 mg/dL"),
            listOf("Uric Acid", "5.1 mg/dL", "3.5 - 7.2 mg/dL"),
            listOf("Total Cholesterol", "182 mg/dL", "< 200 mg/dL (Desirable)"),
            listOf("HDL Cholesterol", "48 mg/dL", "> 40 mg/dL"),
            listOf("Triglycerides", "142 mg/dL", "< 150 mg/dL")
        )
        y += 110f
        for (t in tests) {
            canvas.drawText(t[0], 120f, y, bPaint)
            canvas.drawText(t[1], 625f, y, hPaint)
            canvas.drawText(t[2], 885f, y, bPaint)
            y += 55f
        }
        canvas.drawLine(100f, y, width - 100f, y, linePaint)

        drawStampAndSignature(canvas, width, 1380f, "METROPOLIS LABS", "AUTHORIZED")
        return bitmap
    }

    // =========================================================================
    // HELPER RENDERING METHODS
    // =========================================================================
    private fun drawPaperBackground(canvas: Canvas, w: Int, h: Int, bgColor: Int) {
        val bgPaint = Paint().apply { color = bgColor; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val noisePaint = Paint().apply { style = Paint.Style.FILL }
        val random = Random(42)
        for (i in 0 until 5000) {
            val nx = random.nextFloat() * w
            val ny = random.nextFloat() * h
            val alpha = random.nextInt(6, 18)
            noisePaint.color = Color.argb(alpha, 100, 95, 85)
            canvas.drawPoint(nx, ny, noisePaint)
        }
    }

    private fun drawDoubleBorder(canvas: Canvas, w: Int, h: Int, borderColor: Int) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor; style = Paint.Style.STROKE; strokeWidth = 3f
        }
        canvas.drawRect(60f, 60f, (w - 60).toFloat(), (h - 60).toFloat(), borderPaint)
        borderPaint.strokeWidth = 1f
        canvas.drawRect(70f, 70f, (w - 70).toFloat(), (h - 70).toFloat(), borderPaint)
    }

    private fun drawStampAndSignature(canvas: Canvas, w: Int, signY: Float, stampLine1: String, stampLine2: String) {
        val linePaint = Paint().apply { color = Color.rgb(180, 185, 195); strokeWidth = 2f }
        canvas.drawLine(120f, signY, 400f, signY, linePaint)

        val signLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 100, 115); textSize = 22f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        canvas.drawText("Authorized Signatory", 140f, signY + 35f, signLabelPaint)

        // Stamp circle
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 220, 38, 38); style = Paint.Style.STROKE; strokeWidth = 4f
        }
        canvas.drawCircle(880f, signY - 40f, 75f, stampPaint)
        stampPaint.strokeWidth = 1.5f
        canvas.drawCircle(880f, signY - 40f, 68f, stampPaint)

        val stampTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 220, 38, 38); textSize = 18f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER
        }
        canvas.drawText(stampLine1, 880f, signY - 45f, stampTextPaint)
        canvas.drawText(stampLine2, 880f, signY - 20f, stampTextPaint)
    }
}
