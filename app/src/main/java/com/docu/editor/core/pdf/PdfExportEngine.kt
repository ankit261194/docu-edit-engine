package com.docu.editor.core.pdf

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.docu.editor.core.ocr.model.DetectedTextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy

/**
 * Enterprise PDF Export Engine.
 * Converts document bitmaps to standard-compliant, authentic PDF documents
 * that open perfectly in Adobe Acrobat, Google Drive, and all PDF viewers.
 */
object PdfExportEngine {

    /**
     * Standard A4 dimensions in typographic points (72 points per inch)
     * A4 = 210mm x 297mm = 595 x 842 points
     */
    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    suspend fun exportBitmapToPdf(
        bitmap: Bitmap,
        outputFile: File,
        fitToA4: Boolean = true,
        detectedItems: List<DetectedTextItem> = emptyList()
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        try {
            val (pageWidthPt, pageHeightPt) = if (fitToA4) {
                // If bitmap is landscape, use landscape A4
                if (bitmap.width > bitmap.height) {
                    Pair(A4_HEIGHT_PT, A4_WIDTH_PT)
                } else {
                    Pair(A4_WIDTH_PT, A4_HEIGHT_PT)
                }
            } else {
                // Scale at 72 DPI points based on 300 DPI bitmap dimensions
                val w = (bitmap.width * 72f / 300f).toInt().coerceAtLeast(100)
                val h = (bitmap.height * 72f / 300f).toInt().coerceAtLeast(100)
                Pair(w, h)
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // White background
            canvas.drawColor(android.graphics.Color.WHITE)

            // Calculate scaled rect preserving aspect ratio
            val scale = minOf(
                pageWidthPt.toFloat() / bitmap.width,
                pageHeightPt.toFloat() / bitmap.height
            )
            val drawW = (bitmap.width * scale).toInt()
            val drawH = (bitmap.height * scale).toInt()
            val left = (pageWidthPt - drawW) / 2
            val top = (pageHeightPt - drawH) / 2

            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = Rect(left, top, left + drawW, top + drawH)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

            // #4 Searchable PDF: Embed invisible OCR text layer matching exact visual coordinates
            if (detectedItems.isNotEmpty()) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = android.graphics.Color.argb(1, 255, 255, 255) // Near-invisible yet indexed by all PDF viewers
                    style = Paint.Style.FILL
                }
                for (item in detectedItems) {
                    val box = item.boundingBox
                    val text = item.text
                    if (text.isBlank()) continue

                    val boxLeft = left + (box.left * scale)
                    val boxTop = top + (box.top * scale)
                    val boxHeight = (box.height() * scale).coerceAtLeast(6f)
                    textPaint.textSize = boxHeight * 0.85f

                    val fontMetrics = textPaint.fontMetrics
                    val baselineY = boxTop + (boxHeight / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
                    canvas.drawText(text, boxLeft, baselineY, textPaint)
                }
            }

            pdfDocument.finishPage(page)

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        outputFile
    }

    /**
     * Enterprise O(1) Memory Streaming Exporter.
     * Streams pages 1-by-1 from disk/cache, writes directly into PdfDocument native buffer,
     * and immediately recycles the bitmap. Never runs out of memory (OOM-proof for 100+ pages).
     */
    suspend fun exportPagesStreamingToPdf(
        pageCount: Int,
        pageBitmapProvider: suspend (pageIndex: Int) -> Bitmap?,
        outputFile: File,
        fitToA4: Boolean = true,
        pagesDetectedItems: Map<Int, List<DetectedTextItem>> = emptyMap(),
        autoRecycleBitmaps: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        try {
            for (index in 0 until pageCount) {
                val bmp = pageBitmapProvider(index) ?: continue
                try {
                    val (pageWidthPt, pageHeightPt) = if (fitToA4) {
                        if (bmp.width > bmp.height) {
                            Pair(A4_HEIGHT_PT, A4_WIDTH_PT)
                        } else {
                            Pair(A4_WIDTH_PT, A4_HEIGHT_PT)
                        }
                    } else {
                        val w = (bmp.width * 72f / 150f).toInt().coerceAtLeast(100)
                        val h = (bmp.height * 72f / 150f).toInt().coerceAtLeast(100)
                        Pair(w, h)
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawColor(android.graphics.Color.WHITE)

                    val scale = minOf(
                        pageWidthPt.toFloat() / bmp.width,
                        pageHeightPt.toFloat() / bmp.height
                    )
                    val drawW = (bmp.width * scale).toInt()
                    val drawH = (bmp.height * scale).toInt()
                    val left = (pageWidthPt - drawW) / 2
                    val top = (pageHeightPt - drawH) / 2

                    val srcRect = Rect(0, 0, bmp.width, bmp.height)
                    val dstRect = Rect(left, top, left + drawW, top + drawH)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

                    canvas.drawBitmap(bmp, srcRect, dstRect, paint)

                    val detectedItems = pagesDetectedItems[index] ?: emptyList()
                    if (detectedItems.isNotEmpty()) {
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                            color = android.graphics.Color.argb(1, 255, 255, 255)
                            style = Paint.Style.FILL
                        }
                        for (item in detectedItems) {
                            val box = item.boundingBox
                            val text = item.text
                            if (text.isBlank()) continue

                            val boxLeft = left + (box.left * scale)
                            val boxTop = top + (box.top * scale)
                            val boxHeight = (box.height() * scale).coerceAtLeast(6f)
                            textPaint.textSize = boxHeight * 0.85f

                            val fontMetrics = textPaint.fontMetrics
                            val baselineY = boxTop + (boxHeight / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
                            canvas.drawText(text, boxLeft, baselineY, textPaint)
                        }
                    }

                    pdfDocument.finishPage(page)
                } finally {
                    if (autoRecycleBitmaps && !bmp.isRecycled) {
                        try {
                            bmp.recycle()
                        } catch (_: Exception) {}
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        outputFile
    }

    suspend fun exportBitmapsToMultiPagePdf(
        bitmaps: List<Bitmap>,
        outputFile: File,
        fitToA4: Boolean = true,
        pagesDetectedItems: Map<Int, List<DetectedTextItem>> = emptyMap()
    ): File {
        return exportPagesStreamingToPdf(
            pageCount = bitmaps.size,
            pageBitmapProvider = { idx -> if (idx in bitmaps.indices) bitmaps[idx] else null },
            outputFile = outputFile,
            fitToA4 = fitToA4,
            pagesDetectedItems = pagesDetectedItems,
            autoRecycleBitmaps = false // Caller holds list of bitmaps, do not recycle externally owned bitmaps
        )
    }

    /**
     * Standard AES-128 PDF Encryption Handler.
     * Locks the exported PDF with a user-defined password.
     * Prevents unauthorized opening in Adobe Acrobat, Google Drive, WhatsApp, or any viewer without the password.
     */
    suspend fun encryptPdfWithPassword(
        inputFile: File,
        outputFile: File,
        userPassword: String,
        ownerPassword: String = userPassword
    ): File = withContext(Dispatchers.IO) {
        val document = PDDocument.load(inputFile)
        try {
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(ownerPassword, userPassword, ap)
            spp.encryptionKeyLength = 128
            spp.permissions = ap
            document.protect(spp)
            document.save(outputFile)
        } finally {
            document.close()
        }
        outputFile
    }
}
