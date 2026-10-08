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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PdfCompressionEngine(private val context: Context) {

    enum class CompressionPreset(val dpi: Int, val jpegQuality: Int, val minSsim: Double, val description: String) {
        MAXIMUM_COMPRESSION(96, 58, 0.84, "Ultra low size, ideal for email & portal uploads (~80% reduction)"),
        BALANCED_OFFICE(150, 75, 0.90, "Medium size, clear text & balanced imagery (~60% reduction)"),
        HIGH_QUALITY_PRINT(240, 86, 0.95, "Sharp print quality with subtle compression (~35% reduction)")
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
        val outputFile: File,
        val averageSsim: Double = 0.92
    )

    /**
     * Calculates the Structural Similarity Index (SSIM) between original rendered page and compressed candidate.
     * Evaluates luminance, contrast, and structure preservation on fine text, signatures, and document details.
     */
    fun calculateSsim(original: Bitmap, compressed: Bitmap): Double {
        if (original.width != compressed.width || original.height != compressed.height) {
            return 0.5
        }

        val w = original.width
        val h = original.height
        val c1 = (0.01 * 255.0) * (0.01 * 255.0) // 6.5025
        val c2 = (0.03 * 255.0) * (0.03 * 255.0) // 58.5225

        val blockSize = 16
        val step = max(blockSize, min(w, h) / 32)
        var totalSsim = 0.0
        var blockCount = 0

        val origPixels = IntArray(blockSize * blockSize)
        val compPixels = IntArray(blockSize * blockSize)

        var y = 0
        while (y + blockSize <= h) {
            var x = 0
            while (x + blockSize <= w) {
                original.getPixels(origPixels, 0, blockSize, x, y, blockSize, blockSize)
                compressed.getPixels(compPixels, 0, blockSize, x, y, blockSize, blockSize)

                var sumX = 0.0
                var sumY = 0.0
                var sumSqX = 0.0
                var sumSqY = 0.0
                var sumXY = 0.0
                val n = (blockSize * blockSize).toDouble()

                for (i in 0 until (blockSize * blockSize)) {
                    val pX = origPixels[i]
                    val lumX = ((pX shr 16 and 0xFF) * 0.299 + (pX shr 8 and 0xFF) * 0.587 + (pX and 0xFF) * 0.114)
                    val pY = compPixels[i]
                    val lumY = ((pY shr 16 and 0xFF) * 0.299 + (pY shr 8 and 0xFF) * 0.587 + (pY and 0xFF) * 0.114)

                    sumX += lumX
                    sumY += lumY
                    sumSqX += lumX * lumX
                    sumSqY += lumY * lumY
                    sumXY += lumX * lumY
                }

                val muX = sumX / n
                val muY = sumY / n
                val sigmaSqX = (sumSqX / n) - (muX * muX)
                val sigmaSqY = (sumSqY / n) - (muY * muY)
                val sigmaXY = (sumXY / n) - (muX * muY)

                val numerator = (2.0 * muX * muY + c1) * (2.0 * sigmaXY + c2)
                val denominator = (muX * muX + muY * muY + c1) * (sigmaSqX + sigmaSqY + c2)

                val blockSsim = if (denominator > 0.0) numerator / denominator else 1.0
                totalSsim += blockSsim.coerceIn(0.0, 1.0)
                blockCount++

                x += step
            }
            y += step
        }

        return if (blockCount > 0) (totalSsim / blockCount).coerceIn(0.0, 1.0) else 1.0
    }

    /**
     * Adaptively compresses bitmap using Perceptual Structural Similarity (SSIM) monitoring.
     * Prevents small text or fine characters from becoming faded/blurry even on extreme compression.
     */
    private fun compressWithAdaptiveSsim(
        rawBmp: Bitmap,
        baseQuality: Int,
        minSsim: Double
    ): Pair<Bitmap, Double> {
        var quality = baseQuality.coerceIn(35, 95)
        var byteStream = ByteArrayOutputStream()
        rawBmp.compress(Bitmap.CompressFormat.JPEG, quality, byteStream)
        var candidateBmp = android.graphics.BitmapFactory.decodeByteArray(byteStream.toByteArray(), 0, byteStream.size())
            ?: rawBmp.copy(Bitmap.Config.ARGB_8888, true)

        var ssim = calculateSsim(rawBmp, candidateBmp)

        // If SSIM falls below target threshold, dynamically step up quality to preserve fine text edges
        var attempts = 0
        while (ssim < minSsim && quality < 92 && attempts < 3) {
            candidateBmp.recycle()
            quality = (quality + 9).coerceAtMost(94)
            byteStream = ByteArrayOutputStream()
            rawBmp.compress(Bitmap.CompressFormat.JPEG, quality, byteStream)
            candidateBmp = android.graphics.BitmapFactory.decodeByteArray(byteStream.toByteArray(), 0, byteStream.size())
                ?: break
            ssim = calculateSsim(rawBmp, candidateBmp)
            attempts++
        }

        return Pair(candidateBmp, ssim)
    }

    fun compressPdfFlow(
        sourceUri: Uri,
        outputFile: File,
        preset: CompressionPreset = CompressionPreset.BALANCED_OFFICE
    ): Flow<CompressionProgress> = compressPdfCustomFlow(
        sourceUri = sourceUri,
        outputFile = outputFile,
        dpi = preset.dpi,
        jpegQuality = preset.jpegQuality,
        minTargetSsim = preset.minSsim
    )

    fun compressPdfCustomFlow(
        sourceUri: Uri,
        outputFile: File,
        dpi: Int,
        jpegQuality: Int,
        minTargetSsim: Double = 0.88
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

                    // Perceptual SSIM adaptive compression
                    val (compressedBmp, _) = compressWithAdaptiveSsim(rawBmp, jpegQuality, minTargetSsim)
                    rawBmp.recycle()

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
        jpegQuality: Int = 75,
        minTargetSsim: Double = 0.88
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
        var totalSsimSum = 0.0

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

                    // Perceptual SSIM adaptive compression
                    val (compressedBmp, pageSsim) = compressWithAdaptiveSsim(rawBmp, jpegQuality, minTargetSsim)
                    totalSsimSum += pageSsim
                    rawBmp.recycle()

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

        val avgSsim = if (totalPagesCount > 0) totalSsimSum / totalPagesCount else 0.92

        CompressionResult(
            originalBytes = originalBytes,
            compressedBytes = compressedBytes,
            reductionPercent = reduction,
            pageCount = totalPagesCount,
            outputFile = outputFile,
            averageSsim = avgSsim
        )
    }
}
