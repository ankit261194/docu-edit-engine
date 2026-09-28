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
        renderScale: Float = 2.5f
    ): Bitmap = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val fileDescriptor = contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF file descriptor")

        val pdfRenderer = PdfRenderer(fileDescriptor)
        val page = pdfRenderer.openPage(pageIndex.coerceIn(0, pdfRenderer.pageCount - 1))

        val width = (page.width * renderScale).toInt()
        val height = (page.height * renderScale).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        page.close()
        pdfRenderer.close()
        fileDescriptor.close()

        bitmap
    }
}
