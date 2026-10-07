package com.docu.editor.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class PdfCompressionEngine(private val context: Context) {

    enum class CompressionPreset(val dpi: Int, val jpegQuality: Int, val description: String) {
        MAXIMUM_COMPRESSION(96, 58, "Ultra low size, ideal for email & portal uploads (~80% reduction)"),
        BALANCED_OFFICE(150, 75, "Medium size, clear text & balanced imagery (~60% reduction)"),
        HIGH_QUALITY_PRINT(240, 86, "Sharp print quality with subtle compression (~35% reduction)")
    }

    data class CompressionProgress(
        val currentPage: Int,
        val totalPages: Int,
        val percentage: Float
    )

    data class CompressionResult(
        val originalBytes: Long,
        val compressedBytes: Long,
        val reductionPercent: Float,
        val pageCount: Int,
        val outputFile: File
    )

    fun compressPdfFlow(
        sourceUri: Uri,
        outputFile: File,
        preset: CompressionPreset = CompressionPreset.BALANCED_OFFICE
    ): Flow<CompressionProgress> = compressPdfCustomFlow(
        sourceUri = sourceUri,
        outputFile = outputFile,
        dpi = preset.dpi,
        jpegQuality = preset.jpegQuality
    )

    fun compressPdfCustomFlow(
        sourceUri: Uri,
        outputFile: File,
        dpi: Int,
        jpegQuality: Int
    ): Flow<CompressionProgress> = flow {
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF file descriptor")

        val outputPdf = PdfDocument()

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val totalPages = renderer.pageCount
                val scale = dpi / 72.0f

                for (pageIndex in 0 until totalPages) {
                    emit(
                        CompressionProgress(
                            currentPage = pageIndex + 1,
                            totalPages = totalPages,
                            percentage = (pageIndex.toFloat() / totalPages)
                        )
                    )

                    val page = renderer.openPage(pageIndex)
                    val widthPt = page.width
                    val heightPt = page.height

                    val widthPx = (widthPt * scale).roundToInt()
                    val heightPx = (heightPt * scale).roundToInt()

                    val rawBmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    rawBmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    // Compress to JPEG byte stream at specified quality
                    val byteStream = ByteArrayOutputStream()
                    rawBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(30, 100), byteStream)
                    rawBmp.recycle()

                    val compressedBmp = android.graphics.BitmapFactory.decodeByteArray(
                        byteStream.toByteArray(), 0, byteStream.size()
                    )

                    val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, pageIndex + 1).create()
                    val pdfPage = outputPdf.startPage(pageInfo)
                    val canvas = pdfPage.canvas

                    val srcRect = android.graphics.Rect(0, 0, compressedBmp.width, compressedBmp.height)
                    val dstRect = android.graphics.Rect(0, 0, widthPt, heightPt)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(compressedBmp, srcRect, dstRect, paint)

                    outputPdf.finishPage(pdfPage)
                    compressedBmp.recycle()
                }
            }
        }

        withContext(Dispatchers.IO) {
            FileOutputStream(outputFile).use { out ->
                outputPdf.writeTo(out)
            }
            outputPdf.close()
        }

        emit(CompressionProgress(totalPages = 1, currentPage = 1, percentage = 1.0f))
    }.flowOn(Dispatchers.Default)

    suspend fun compressPdfDirect(
        sourceUri: Uri,
        outputFile: File,
        dpi: Int = 150,
        jpegQuality: Int = 75
    ): CompressionResult = withContext(Dispatchers.Default) {
        val originalBytes = try {
            context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) {
            0L
        }

        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF file descriptor")

        val outputPdf = PdfDocument()
        var totalPagesCount = 1

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                totalPagesCount = renderer.pageCount
                val scale = dpi / 72.0f

                for (pageIndex in 0 until totalPagesCount) {
                    val page = renderer.openPage(pageIndex)
                    val widthPt = page.width
                    val heightPt = page.height

                    val widthPx = (widthPt * scale).roundToInt()
                    val heightPx = (heightPt * scale).roundToInt()

                    val rawBmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    rawBmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    val byteStream = ByteArrayOutputStream()
                    rawBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(30, 100), byteStream)
                    rawBmp.recycle()

                    val compressedBmp = android.graphics.BitmapFactory.decodeByteArray(
                        byteStream.toByteArray(), 0, byteStream.size()
                    )

                    val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, pageIndex + 1).create()
                    val pdfPage = outputPdf.startPage(pageInfo)
                    val canvas = pdfPage.canvas

                    val srcRect = android.graphics.Rect(0, 0, compressedBmp.width, compressedBmp.height)
                    val dstRect = android.graphics.Rect(0, 0, widthPt, heightPt)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(compressedBmp, srcRect, dstRect, paint)

                    outputPdf.finishPage(pdfPage)
                    compressedBmp.recycle()
                }
            }
        }

        withContext(Dispatchers.IO) {
            FileOutputStream(outputFile).use { out ->
                outputPdf.writeTo(out)
            }
            outputPdf.close()
        }

        val compressedBytes = outputFile.length()
        val reduction = if (originalBytes > 0 && compressedBytes < originalBytes) {
            ((originalBytes - compressedBytes).toFloat() / originalBytes) * 100f
        } else {
            0f
        }

        CompressionResult(
            originalBytes = originalBytes,
            compressedBytes = compressedBytes,
            reductionPercent = reduction,
            pageCount = totalPagesCount,
            outputFile = outputFile
        )
    }
}
