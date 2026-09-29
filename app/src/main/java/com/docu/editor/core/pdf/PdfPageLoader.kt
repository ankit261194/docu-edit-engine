package com.docu.editor.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfPageLoader {

    suspend fun renderPageToBitmap(
        context: Context,
        pdfUri: Uri,
        pageIndex: Int = 0,
        renderScale: Float = 4.167f // 300 DPI Ultra-HD Commercial Print Quality (72 * 4.167 = 300)
    ): Bitmap = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val fileDescriptor = contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF file descriptor")

        val pdfRenderer = PdfRenderer(fileDescriptor)
        val page = pdfRenderer.openPage(pageIndex.coerceIn(0, pdfRenderer.pageCount - 1))

        // Ensure max bounds fit within memory guard
        val maxDim = 3200
        val rawW = (page.width * renderScale).toInt()
        val rawH = (page.height * renderScale).toInt()
        val scale = if (rawW > maxDim || rawH > maxDim) {
            minOf(maxDim.toFloat() / rawW, maxDim.toFloat() / rawH)
        } else 1.0f

        val width = (rawW * scale).toInt().coerceAtLeast(100)
        val height = (rawH * scale).toInt().coerceAtLeast(100)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

        page.close()
        pdfRenderer.close()
        fileDescriptor.close()
        bitmap
    }

    suspend fun getPageCount(context: Context, pdfUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileDescriptor = contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext 1
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val count = pdfRenderer.pageCount
            pdfRenderer.close()
            fileDescriptor.close()
            count
        } catch (_: Exception) {
            1
        }
    }
}
