package com.docu.editor.core.pdf

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
        fitToA4: Boolean = true
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
                // Scale at 72 DPI points based on bitmap dimensions
                val w = (bitmap.width * 72f / 150f).toInt().coerceAtLeast(100)
                val h = (bitmap.height * 72f / 150f).toInt().coerceAtLeast(100)
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
            pdfDocument.finishPage(page)

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
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        try {
            bitmaps.forEachIndexed { index, bmp ->
                val (pageWidthPt, pageHeightPt) = if (bmp.width > bmp.height) {
                    Pair(A4_HEIGHT_PT, A4_WIDTH_PT)
                } else {
                    Pair(A4_WIDTH_PT, A4_HEIGHT_PT)
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
                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        outputFile
    }
}
