package com.docu.editor.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfToolbox(private val context: Context) {

    /**
     * Merges multiple PDF files into a single unified document.
     */
    suspend fun mergePdfs(
        pdfUris: List<Uri>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath

        val tempFiles = mutableListOf<File>()
        try {
            for (uri in pdfUris) {
                val tempFile = File.createTempFile("merge_input_", ".pdf", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                merger.addSource(tempFile)
                tempFiles.add(tempFile)
            }

            merger.mergeDocuments(null)
            outputFile
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    /**
     * Encrypts and password-protects a PDF document with standard 128-bit AES encryption.
     */
    suspend fun passwordProtectPdf(
        sourceUri: Uri,
        userPassword: String,
        ownerPassword: String = userPassword + "_owner",
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("to_encrypt_", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val document = PDDocument.load(tempFile)
        try {
            val ap = AccessPermission().apply {
                setCanPrint(true)
                setCanExtractContent(false)
                setCanModify(false)
            }

            val spp = StandardProtectionPolicy(ownerPassword, userPassword, ap).apply {
                encryptionKeyLength = 128
            }

            document.protect(spp)
            document.save(outputFile)
            outputFile
        } finally {
            document.close()
            tempFile.delete()
        }
    }

    /**
     * Decrypts a password-protected PDF document and writes out an unlocked PDF file.
     */
    suspend fun decryptPdf(
        sourceUri: Uri,
        password: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("to_decrypt_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            val document = PDDocument.load(tempFile, password)
            try {
                if (document.isEncrypted) {
                    document.isAllSecurityToBeRemoved = true
                }
                document.save(outputFile)
                true
            } finally {
                document.close()
            }
        } catch (_: Exception) {
            false
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Splits every page of the source PDF into individual single-page PDF files.
     */
    suspend fun splitPdf(
        sourceUri: Uri,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("to_split_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val document = PDDocument.load(tempFile)
            val outputFiles = mutableListOf<File>()
            try {
                val splitter = Splitter()
                val splitDocs = splitter.split(document)
                val baseTime = System.currentTimeMillis()
                splitDocs.forEachIndexed { idx, doc ->
                    val pageFile = File(outputDir, "DocuEdit_Page_${idx + 1}_$baseTime.pdf")
                    doc.save(pageFile)
                    doc.close()
                    outputFiles.add(pageFile)
                }
            } finally {
                document.close()
            }
            outputFiles
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Extracts only selected page indices (0-indexed) into a new consolidated PDF document.
     */
    suspend fun extractPages(
        sourceUri: Uri,
        pageIndices: List<Int>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("to_extract_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val sourceDoc = PDDocument.load(tempFile)
            val newDoc = PDDocument()
            try {
                val total = sourceDoc.numberOfPages
                for (idx in pageIndices.sorted()) {
                    if (idx in 0 until total) {
                        newDoc.importPage(sourceDoc.getPage(idx))
                    }
                }
                newDoc.save(outputFile)
            } finally {
                sourceDoc.close()
                newDoc.close()
            }
            outputFile
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Extracts all pages of a PDF document as high-resolution JPEG images.
     */
    suspend fun extractPagesAsImages(
        sourceUri: Uri,
        outputDir: File,
        quality: Int = 92
    ): List<File> = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: return@withContext emptyList()
        val imageFiles = mutableListOf<File>()

        pfd.use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount
                val baseTime = System.currentTimeMillis()

                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    val scale = 2.5f
                    val w = (page.width * scale).toInt().coerceAtLeast(100)
                    val h = (page.height * scale).toInt().coerceAtLeast(100)

                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    val imgFile = File(outputDir, "DocuEdit_Page_${i + 1}_$baseTime.jpg")
                    FileOutputStream(imgFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                    }
                    bmp.recycle()
                    imageFiles.add(imgFile)
                }
            }
        }
        imageFiles
    }

    /**
     * Overlays a crisp, semi-transparent diagonal security watermark across every page of the PDF.
     */
    suspend fun addWatermarkToPdf(
        sourceUri: Uri,
        watermarkText: String,
        outputFile: File,
        opacity: Float = 0.22f
    ): File = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF file descriptor")

        val outputPdf = android.graphics.pdf.PdfDocument()

        pfd.use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                val totalPages = renderer.pageCount
                val scale = 2.0f

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb((opacity.coerceIn(0.05f, 0.8f) * 255).toInt(), 120, 120, 120)
                    textSize = 48f * scale
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }

                for (pageIndex in 0 until totalPages) {
                    val page = renderer.openPage(pageIndex)
                    val widthPt = page.width
                    val heightPt = page.height

                    val widthPx = (widthPt * scale).toInt()
                    val heightPx = (heightPt * scale).toInt()

                    val pageBmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    pageBmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(pageBmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    // Draw watermark on rendered bitmap
                    val canvas = Canvas(pageBmp)
                    canvas.save()
                    canvas.translate(widthPx / 2f, heightPx / 2f)
                    canvas.rotate(-40f)
                    canvas.drawText(watermarkText.ifBlank { "CONFIDENTIAL" }, 0f, 0f, textPaint)
                    canvas.restore()

                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(widthPt, heightPt, pageIndex + 1).create()
                    val pdfPage = outputPdf.startPage(pageInfo)
                    val docCanvas = pdfPage.canvas

                    val srcRect = android.graphics.Rect(0, 0, pageBmp.width, pageBmp.height)
                    val dstRect = android.graphics.Rect(0, 0, widthPt, heightPt)
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    docCanvas.drawBitmap(pageBmp, srcRect, dstRect, paint)

                    outputPdf.finishPage(pdfPage)
                    pageBmp.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { out ->
            outputPdf.writeTo(out)
        }
        outputPdf.close()

        outputFile
    }
}
