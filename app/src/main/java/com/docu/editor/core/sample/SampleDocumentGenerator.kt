package com.docu.editor.core.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.random.Random

object SampleDocumentGenerator {

    fun generateSampleInvoice(): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Warm document background
        val bgPaint = Paint().apply {
            color = Color.rgb(250, 248, 243)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Subtle micro-paper grain
        val noisePaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        val random = Random(42)
        for (i in 0 until 6000) {
            val nx = random.nextFloat() * width
            val ny = random.nextFloat() * height
            val alpha = random.nextInt(8, 22)
            noisePaint.color = Color.argb(alpha, 120, 110, 95)
            canvas.drawPoint(nx, ny, noisePaint)
        }

        // 3. Document Border
        val borderPaint = Paint().apply {
            color = Color.rgb(60, 70, 85)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRect(60f, 60f, (width - 60).toFloat(), (height - 60).toFloat(), borderPaint)
        borderPaint.strokeWidth = 1f
        canvas.drawRect(70f, 70f, (width - 70).toFloat(), (height - 70).toFloat(), borderPaint)

        // 4. Header Text
        val titlePaint = Paint().apply {
            color = Color.rgb(20, 30, 45)
            textSize = 52f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TAX INVOICE & RECEIPT", (width / 2).toFloat(), 180f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.rgb(85, 95, 110)
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("GLOBAL ENTERPRISES PRIVATE LIMITED", (width / 2).toFloat(), 230f, subTitlePaint)
        canvas.drawText("Invoice No: INV-2026-8891  |  Date: 28 Sep 2026", (width / 2).toFloat(), 270f, subTitlePaint)

        // Divider line
        val linePaint = Paint().apply {
            color = Color.rgb(180, 185, 195)
            strokeWidth = 2f
        }
        canvas.drawLine(100f, 310f, (width - 100).toFloat(), 310f, linePaint)

        // 5. Bill To Section
        val headerPaint = Paint().apply {
            color = Color.rgb(30, 40, 55)
            textSize = 32f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("BILLED TO:", 100f, 380f, headerPaint)

        val bodyPaint = Paint().apply {
            color = Color.rgb(40, 45, 55)
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("Client Name: Ankit Sharma", 100f, 430f, bodyPaint)
        canvas.drawText("Company: Apex Digital Solutions", 100f, 480f, bodyPaint)
        canvas.drawText("Location: New Delhi, India - 110001", 100f, 530f, bodyPaint)
        canvas.drawText("GSTIN: 07AAAAA0000A1Z5", 100f, 580f, bodyPaint)

        // 6. Items Table
        val tableTop = 660f
        canvas.drawLine(100f, tableTop, (width - 100).toFloat(), tableTop, linePaint)

        val thPaint = Paint().apply {
            color = Color.rgb(20, 25, 35)
            textSize = 26f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Item Description", 120f, tableTop + 45f, thPaint)
        canvas.drawText("Qty", 700f, tableTop + 45f, thPaint)
        canvas.drawText("Amount (USD)", 920f, tableTop + 45f, thPaint)

        canvas.drawLine(100f, tableTop + 70f, (width - 100).toFloat(), tableTop + 70f, linePaint)

        // Table Rows
        var rowY = tableTop + 130f
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
            rowY += 65f
        }

        canvas.drawLine(100f, rowY, (width - 100).toFloat(), rowY, linePaint)

        // Total
        val totalPaint = Paint().apply {
            color = Color.rgb(15, 20, 30)
            textSize = 34f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Total Amount Due: $ 11,500.00", 620f, rowY + 70f, totalPaint)

        // 7. Signature & Official Stamp area
        val signLabelPaint = Paint().apply {
            color = Color.rgb(90, 100, 115)
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        canvas.drawLine(120f, 1380f, 400f, 1380f, linePaint)
        canvas.drawText("Authorized Signatory", 140f, 1420f, signLabelPaint)

        // Stamp circle
        val stampPaint = Paint().apply {
            color = Color.argb(190, 200, 35, 45)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(880f, 1340f, 80f, stampPaint)
        stampPaint.strokeWidth = 1.5f
        canvas.drawCircle(880f, 1340f, 72f, stampPaint)

        val stampTextPaint = Paint().apply {
            color = Color.argb(210, 200, 35, 45)
            textSize = 20f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("OFFICIALLY", 880f, 1335f, stampTextPaint)
        canvas.drawText("VERIFIED", 880f, 1365f, stampTextPaint)

        return bitmap
    }
}
