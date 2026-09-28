package com.docu.editor.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class PdfDocumentManager(private val context: Context) {

    data class PdfMeta(
        val totalPages: Int,
        val pageWidthPt: Int,
        val pageHeightPt: Int
    )

    private val cacheDir = File(context.cacheDir, "pdf_page_cache").apply { mkdirs() }
    private val modifiedPagesCache = mutableMapOf<Int, File>()

    suspend fun getPdfMetadata(pdfUri: Uri): PdfMeta = withContext(Dispatchers.IO) {
        openPfd(pdfUri).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val firstPage = renderer.openPage(0)
                val width = firstPage.width
                val height = firstPage.height
                val count = renderer.pageCount
                firstPage.close()
                PdfMeta(count, width, height)
            }
        }
    }

    suspend fun renderPageAt300Dpi(
        pdfUri: Uri,
        pageIndex: Int,
        targetDpi: Int = 300
    ): Bitmap = withContext(Dispatchers.IO) {
        modifiedPagesCache[pageIndex]?.let { cachedFile ->
            if (cachedFile.exists()) {
                return@withContext BitmapFactory.decodeFile(cachedFile.absolutePath)
            }
        }

        openPfd(pdfUri).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val page = renderer.openPage(pageIndex)
                val scale = targetDpi.toFloat() / 72.0f
                val widthPx = (page.width * scale).roundToInt()
                val heightPx = (page.height * scale).roundToInt()

                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                bitmap
            }
        }
    }

    suspend fun commitEditedPage(pageIndex: Int, editedBitmap: Bitmap) = withContext(Dispatchers.IO) {
        val cacheFile = File(cacheDir, "page_${pageIndex}_${System.currentTimeMillis()}.tmp")
        FileOutputStream(cacheFile).use { out ->
            editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        modifiedPagesCache[pageIndex]?.delete()
        modifiedPagesCache[pageIndex] = cacheFile
    }

    private fun openPfd(uri: Uri): ParcelFileDescriptor {
        return context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF URI: $uri")
    }
}
