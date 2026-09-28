package com.docu.editor.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MultiPageBatchPdfExporter(private val context: Context) {

    data class ExportProgress(
        val currentPage: Int,
        val totalPages: Int,
        val percentage: Float,
        val statusMessage: String
    )

    fun exportBatchPdfFlow(
        sourcePdfUri: Uri,
        editedPagesMap: Map<Int, File>,
        outputFile: File,
        targetDpi: Int = 300
    ): Flow<ExportProgress> = flow {
        val pfd = context.contentResolver.openFileDescriptor(sourcePdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF descriptor")

        val outputPdf = PdfDocument()
        val scale = targetDpi / 72.0f

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val totalPages = renderer.pageCount

                for (pageIndex in 0 until totalPages) {
                    emit(
                        ExportProgress(
                            currentPage = pageIndex + 1,
                            totalPages = totalPages,
                            percentage = (pageIndex.toFloat() / totalPages) * 0.90f,
                            statusMessage = "Processing page ${pageIndex + 1} of $totalPages..."
                        )
                    )

                    val page = renderer.openPage(pageIndex)
                    val widthPt = page.width
                    val heightPt = page.height
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, pageIndex + 1).create()
                    val pdfPage = outputPdf.startPage(pageInfo)
                    val canvas = pdfPage.canvas

                    val pageBitmap = if (editedPagesMap.containsKey(pageIndex)) {
                        BitmapFactory.decodeFile(editedPagesMap[pageIndex]!!.absolutePath)
                    } else {
                        val widthPx = (widthPt * scale).roundToInt()
                        val heightPx = (heightPt * scale).roundToInt()
                        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        val p = renderer.openPage(pageIndex)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        p.close()
                        bmp
                    }

                    val srcRect = android.graphics.Rect(0, 0, pageBitmap.width, pageBitmap.height)
                    val dstRect = android.graphics.Rect(0, 0, widthPt, heightPt)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(pageBitmap, srcRect, dstRect, paint)

                    outputPdf.finishPage(pdfPage)
                    pageBitmap.recycle()
                }
            }
        }

        emit(ExportProgress(totalPages = 1, currentPage = 1, percentage = 0.92f, statusMessage = "Writing PDF file..."))

        withContext(Dispatchers.IO) {
            FileOutputStream(outputFile).use { out ->
                outputPdf.writeTo(out)
            }
            outputPdf.close()
            SecurityMetadataSanitizer.sanitizePdfDocument(outputFile)
        }

        emit(ExportProgress(totalPages = 1, currentPage = 1, percentage = 1.0f, statusMessage = "Export Complete!"))
    }.flowOn(Dispatchers.Default)
}
