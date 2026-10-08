package com.docu.editor.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.docu.editor.core.pdf.PdfExportEngine
import com.docu.editor.core.pdf.PdfPageLoader
import com.docu.editor.core.util.DocuStorageUtil
import com.docu.editor.core.util.DocuNotificationHelper
import com.docu.editor.core.util.ExifBitmapUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Enterprise Batch Target Size Resizer & Format Converter Engine.
 * 
 * Capability Highlights:
 * 1. Multi-Document Ingestion: Ingests 1 to 50+ mixed files (JPG, PNG, PDF, WEBP).
 * 2. Strict Target File Size Enforcement:
 *    - Decrease Mode: Advanced binary-search JPEG/PDF compression + resolution downscaling.
 *    - Increase Mode: Standard-compliant lossless padding injection for Govt portals (SSC/UPSC/IBPS).
 * 3. Cross-Format Batch Conversion: Converts all to PDF, all to JPG, PNG, WEBP, or retains original.
 * 4. Error Isolation: Corrupt single file does not fail the batch.
 * 5. 1-Click ZIP Packaging & Direct Multi-Share via FileProvider.
 * 6. Zero Memory Leaks: Explicit Bitmap recycling on every loop iteration.
 */
object BatchTargetResizeConverterEngine {

    data class BatchInputItem(
        val id: String,
        val uri: Uri,
        val originalName: String,
        val mimeType: String,
        val originalSizeBytes: Long
    )

    enum class BatchOutputFormat(val label: String, val extension: String) {
        KEEP_ORIGINAL("Original Format", ""),
        TO_JPG("JPG Photo", "jpg"),
        TO_PDF("PDF Document", "pdf"),
        TO_PNG("PNG Image", "png"),
        TO_WEBP("WEBP Image", "webp")
    }

    enum class BatchAdjustMode(val label: String) {
        DECREASE_TO_MAX("Compress to <= Target"),
        EXACT_TARGET("Exact Target KB"),
        INCREASE_TO_MIN("Pad to >= Target")
    }

    data class BatchProgressState(
        val currentIndex: Int,
        val totalCount: Int,
        val currentFileName: String,
        val progressPercent: Int,
        val isCompleted: Boolean = false,
        val errorMessage: String? = null
    )

    data class BatchSingleResult(
        val id: String,
        val originalName: String,
        val originalSizeBytes: Long,
        val outputFile: File,
        val outputSizeBytes: Long,
        val outputFormat: String,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    data class BatchOverallResult(
        val results: List<BatchSingleResult>,
        val totalOriginalBytes: Long,
        val totalOutputBytes: Long,
        val spaceReductionPercent: Float,
        val successCount: Int,
        val failureCount: Int,
        val outputDirectory: File
    )

    /**
     * Resolves metadata (display name, mime type, byte size) from an Android content Uri.
     */
    fun createInputItemFromUri(context: Context, uri: Uri): BatchInputItem {
        var name = "Document_${System.currentTimeMillis()}"
        var sizeBytes = 0L
        val contentResolver = context.contentResolver

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        if (sizeBytes <= 0L) {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    sizeBytes = pfd.statSize
                }
            } catch (_: Exception) {}
        }

        val mime = contentResolver.getType(uri) ?: when {
            name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            name.endsWith(".png", ignoreCase = true) -> "image/png"
            name.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }

        return BatchInputItem(
            id = "batch_item_${System.currentTimeMillis()}_${(100..999).random()}",
            uri = uri,
            originalName = name,
            mimeType = mime,
            originalSizeBytes = max(0L, sizeBytes)
        )
    }

    /**
     * Processes a batch of documents sequentially, adjusting sizes and formats as requested.
     */
    suspend fun processBatch(
        context: Context,
        items: List<BatchInputItem>,
        targetKb: Int,
        outputFormat: BatchOutputFormat,
        adjustMode: BatchAdjustMode,
        targetDpi: Int = 300,
        onProgress: (BatchProgressState) -> Unit
    ): BatchOverallResult = withContext(Dispatchers.Default) {
        val timeStamp = System.currentTimeMillis()
        val outDir = File(context.cacheDir, "batch_processed_$timeStamp").apply { mkdirs() }
        val results = mutableListOf<BatchSingleResult>()
        val totalCount = items.size

        for (index in items.indices) {
            val item = items[index]
            val percent = (((index).toFloat() / totalCount.toFloat()) * 100f).roundToInt()
            DocuNotificationHelper.showProgressNotification(context, index + 1, totalCount, item.originalName)
            onProgress(
                BatchProgressState(
                    currentIndex = index + 1,
                    totalCount = totalCount,
                    currentFileName = item.originalName,
                    progressPercent = percent
                )
            )

            val baseName = item.originalName.substringBeforeLast(".")
            val isSourcePdf = item.mimeType == "application/pdf" || item.originalName.endsWith(".pdf", ignoreCase = true)

            try {
                val singleResult = when (outputFormat) {
                    BatchOutputFormat.KEEP_ORIGINAL -> {
                        if (isSourcePdf) {
                            processPdfToPdf(context, item, targetKb, adjustMode, outDir, "$baseName.pdf")
                        } else {
                            processImageToImage(context, item, targetKb, adjustMode, outDir, "$baseName.jpg", targetDpi)
                        }
                    }
                    BatchOutputFormat.TO_JPG -> {
                        if (isSourcePdf) {
                            processPdfToImage(context, item, targetKb, adjustMode, outDir, "$baseName.jpg", Bitmap.CompressFormat.JPEG, targetDpi)
                        } else {
                            processImageToImage(context, item, targetKb, adjustMode, outDir, "$baseName.jpg", targetDpi)
                        }
                    }
                    BatchOutputFormat.TO_PDF -> {
                        if (isSourcePdf) {
                            processPdfToPdf(context, item, targetKb, adjustMode, outDir, "$baseName.pdf")
                        } else {
                            processImageToPdf(context, item, targetKb, adjustMode, outDir, "$baseName.pdf")
                        }
                    }
                    BatchOutputFormat.TO_PNG -> {
                        if (isSourcePdf) {
                            processPdfToImage(context, item, targetKb, adjustMode, outDir, "$baseName.png", Bitmap.CompressFormat.PNG)
                        } else {
                            processImageToPng(context, item, targetKb, outDir, "$baseName.png")
                        }
                    }
                    BatchOutputFormat.TO_WEBP -> {
                        if (isSourcePdf) {
                            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                            processPdfToImage(context, item, targetKb, adjustMode, outDir, "$baseName.webp", format)
                        } else {
                            processImageToWebp(context, item, targetKb, outDir, "$baseName.webp")
                        }
                    }
                }
                results.add(singleResult)
            } catch (e: Exception) {
                val dummyFail = File(outDir, "${baseName}_error.txt")
                results.add(
                    BatchSingleResult(
                        id = item.id,
                        originalName = item.originalName,
                        originalSizeBytes = item.originalSizeBytes,
                        outputFile = dummyFail,
                        outputSizeBytes = 0L,
                        outputFormat = "ERROR",
                        isSuccess = false,
                        errorMessage = e.message ?: "Processing failed"
                    )
                )
            }

            // Periodic garbage collection every 8 items to keep heap footprint low for 48MP photos
            if ((index + 1) % 8 == 0) {
                System.gc()
            }
        }

        val successCount = results.count { it.isSuccess }
        val failureCount = results.count { !it.isSuccess }
        DocuNotificationHelper.showCompleteNotification(context, successCount, failureCount, targetKb)

        onProgress(
            BatchProgressState(
                currentIndex = totalCount,
                totalCount = totalCount,
                currentFileName = "Done",
                progressPercent = 100,
                isCompleted = true
            )
        )

        val totalOriginal = results.sumOf { it.originalSizeBytes }
        val totalOutput = results.filter { it.isSuccess }.sumOf { it.outputSizeBytes }
        val reduction = if (totalOriginal > 0L) {
            val diff = totalOriginal - totalOutput
            max(0f, (diff.toFloat() / totalOriginal.toFloat()) * 100f)
        } else 0f

        BatchOverallResult(
            results = results,
            totalOriginalBytes = totalOriginal,
            totalOutputBytes = totalOutput,
            spaceReductionPercent = reduction,
            successCount = successCount,
            failureCount = failureCount,
            outputDirectory = outDir
        )
    }

    // --- Optimal Dimension Calculator for 48MP / High-Resolution Memory Safety ---
    private fun getOptimalDecodeDimension(targetKb: Int): Int {
        return when {
            targetKb <= 25 -> 1024
            targetKb <= 60 -> 1440
            targetKb <= 120 -> 1920
            targetKb <= 300 -> 2400
            else -> 2880
        }
    }

    private suspend fun processImageToImage(
        context: Context,
        item: BatchInputItem,
        targetKb: Int,
        adjustMode: BatchAdjustMode,
        outDir: File,
        outFileName: String,
        targetDpi: Int = 300
    ): BatchSingleResult = withContext(Dispatchers.IO) {
        val outFile = File(outDir, outFileName)
        val optimalMaxDim = getOptimalDecodeDimension(targetKb)
        val bitmap = ExifBitmapUtil.decodeUriWithExif(context, item.uri, maxDim = optimalMaxDim)
            ?: throw IllegalStateException("Could not decode image")

        try {
            when (adjustMode) {
                BatchAdjustMode.INCREASE_TO_MIN -> {
                    val tempJpg = File.createTempFile("temp_inc_", ".jpg", context.cacheDir)
                    FileOutputStream(tempJpg).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    }
                    TargetFileSizeEngine.increaseJpegToTargetKb(tempJpg, targetKb, outFile, targetDpi)
                    tempJpg.delete()
                }
                BatchAdjustMode.EXACT_TARGET -> {
                    TargetFileSizeEngine.compressBitmapToTargetKb(bitmap, targetKb, outFile, targetDpi, exactMatch = true)
                }
                BatchAdjustMode.DECREASE_TO_MAX -> {
                    TargetFileSizeEngine.compressBitmapToTargetKb(bitmap, targetKb, outFile, targetDpi, exactMatch = false)
                }
            }
        } finally {
            bitmap.recycle()
        }

        BatchSingleResult(
            id = item.id,
            originalName = item.originalName,
            originalSizeBytes = item.originalSizeBytes,
            outputFile = outFile,
            outputSizeBytes = outFile.length(),
            outputFormat = "JPG",
            isSuccess = true
        )
    }

    private suspend fun processImageToPdf(
        context: Context,
        item: BatchInputItem,
        targetKb: Int,
        adjustMode: BatchAdjustMode,
        outDir: File,
        outFileName: String
    ): BatchSingleResult = withContext(Dispatchers.IO) {
        val outFile = File(outDir, outFileName)
        val optimalMaxDim = getOptimalDecodeDimension(targetKb)
        val bitmap = ExifBitmapUtil.decodeUriWithExif(context, item.uri, maxDim = optimalMaxDim)
            ?: throw IllegalStateException("Could not decode image")

        val tempPdf = File.createTempFile("temp_img_pdf_", ".pdf", context.cacheDir)
        try {
            PdfExportEngine.exportBitmapToPdf(bitmap, tempPdf, fitToA4 = true)
            if (adjustMode == BatchAdjustMode.INCREASE_TO_MIN) {
                TargetFileSizeEngine.increasePdfToTargetKb(context, Uri.fromFile(tempPdf), targetKb, outFile)
            } else {
                TargetFileSizeEngine.compressPdfToTargetKb(context, Uri.fromFile(tempPdf), targetKb, outFile)
            }
        } finally {
            bitmap.recycle()
            tempPdf.delete()
        }

        BatchSingleResult(
            id = item.id,
            originalName = item.originalName,
            originalSizeBytes = item.originalSizeBytes,
            outputFile = outFile,
            outputSizeBytes = outFile.length(),
            outputFormat = "PDF",
            isSuccess = true
        )
    }

    private suspend fun processPdfToPdf(
        context: Context,
        item: BatchInputItem,
        targetKb: Int,
        adjustMode: BatchAdjustMode,
        outDir: File,
        outFileName: String
    ): BatchSingleResult = withContext(Dispatchers.IO) {
        val outFile = File(outDir, outFileName)
        if (adjustMode == BatchAdjustMode.INCREASE_TO_MIN) {
            TargetFileSizeEngine.increasePdfToTargetKb(context, item.uri, targetKb, outFile)
        } else {
            TargetFileSizeEngine.compressPdfToTargetKb(context, item.uri, targetKb, outFile)
        }

        BatchSingleResult(
            id = item.id,
            originalName = item.originalName,
            originalSizeBytes = item.originalSizeBytes,
            outputFile = outFile,
            outputSizeBytes = outFile.length(),
            outputFormat = "PDF",
            isSuccess = true
        )
    }

    private suspend fun processPdfToImage(
        context: Context,
        item: BatchInputItem,
        targetKb: Int,
        adjustMode: BatchAdjustMode,
        outDir: File,
        outFileName: String,
        compressFormat: Bitmap.CompressFormat,
        targetDpi: Int = 300
    ): BatchSingleResult = withContext(Dispatchers.IO) {
        val outFile = File(outDir, outFileName)
        val bitmap = PdfPageLoader.renderPageToBitmap(context, item.uri, 0)

        try {
            if (compressFormat == Bitmap.CompressFormat.JPEG) {
                when (adjustMode) {
                    BatchAdjustMode.INCREASE_TO_MIN -> {
                        val tempJpg = File.createTempFile("temp_pdf_jpg_", ".jpg", context.cacheDir)
                        FileOutputStream(tempJpg).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        }
                        TargetFileSizeEngine.increaseJpegToTargetKb(tempJpg, targetKb, outFile, targetDpi)
                        tempJpg.delete()
                    }
                    BatchAdjustMode.EXACT_TARGET -> {
                        TargetFileSizeEngine.compressBitmapToTargetKb(bitmap, targetKb, outFile, targetDpi, exactMatch = true)
                    }
                    BatchAdjustMode.DECREASE_TO_MAX -> {
                        TargetFileSizeEngine.compressBitmapToTargetKb(bitmap, targetKb, outFile, targetDpi, exactMatch = false)
                    }
                }
            } else if (compressFormat == Bitmap.CompressFormat.PNG) {
                processBitmapToPngStrict(bitmap, targetKb, outFile)
            } else {
                processBitmapToWebpStrict(bitmap, targetKb, outFile)
            }
        } finally {
            bitmap.recycle()
        }

        val fmtName = when (compressFormat) {
            Bitmap.CompressFormat.PNG -> "PNG"
            Bitmap.CompressFormat.JPEG -> "JPG"
            else -> "WEBP"
        }

        BatchSingleResult(
            id = item.id,
            originalName = item.originalName,
            originalSizeBytes = item.originalSizeBytes,
            outputFile = outFile,
            outputSizeBytes = outFile.length(),
            outputFormat = fmtName,
            isSuccess = true
        )
    }

    private suspend fun processImageToPng(
        context: Context,
        item: BatchInputItem,
        targetKb: Int,
        outDir: File,
        outFileName: String
    ): BatchSingleResult = withContext(Dispatchers.IO) {
        val outFile = File(outDir, outFileName)
        val optimalMaxDim = getOptimalDecodeDimension(targetKb)
        val bitmap = ExifBitmapUtil.decodeUriWithExif(context, item.uri, maxDim = optimalMaxDim)
            ?: throw IllegalStateException("Could not decode image")

        try {
            processBitmapToPngStrict(bitmap, targetKb, outFile)
        } finally {
            bitmap.recycle()
        }

        BatchSingleResult(
            id = item.id,
            originalName = item.originalName,
            originalSizeBytes = item.originalSizeBytes,
            outputFile = outFile,
            outputSizeBytes = outFile.length(),
            outputFormat = "PNG",
            isSuccess = true
        )
    }

    private fun processBitmapToPngStrict(sourceBitmap: Bitmap, targetKb: Int, outFile: File) {
        val targetBytes = targetKb * 1024L
        var current = sourceBitmap
        var isRecyclable = false
        try {
            var stream = ByteArrayOutputStream()
            current.compress(Bitmap.CompressFormat.PNG, 100, stream)

            while (stream.size() > targetBytes && current.width > 40 && current.height > 40) {
                val ratio = (kotlin.math.sqrt(targetBytes.toDouble() / stream.size().toDouble()) * 0.88).coerceIn(0.1, 0.85)
                val nw = max(30, (current.width * ratio).toInt())
                val nh = max(30, (current.height * ratio).toInt())
                val scaled = Bitmap.createScaledBitmap(current, nw, nh, true)
                if (isRecyclable) current.recycle()
                current = scaled
                isRecyclable = true
                stream = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }

            FileOutputStream(outFile).use { fos ->
                fos.write(stream.toByteArray())
            }
        } finally {
            if (isRecyclable) current.recycle()
        }
    }

    private suspend fun processImageToWebp(
        context: Context,
        item: BatchInputItem,
        targetKb: Int,
        outDir: File,
        outFileName: String
    ): BatchSingleResult = withContext(Dispatchers.IO) {
        val outFile = File(outDir, outFileName)
        val optimalMaxDim = getOptimalDecodeDimension(targetKb)
        val bitmap = ExifBitmapUtil.decodeUriWithExif(context, item.uri, maxDim = optimalMaxDim)
            ?: throw IllegalStateException("Could not decode image")

        try {
            processBitmapToWebpStrict(bitmap, targetKb, outFile)
        } finally {
            bitmap.recycle()
        }

        BatchSingleResult(
            id = item.id,
            originalName = item.originalName,
            originalSizeBytes = item.originalSizeBytes,
            outputFile = outFile,
            outputSizeBytes = outFile.length(),
            outputFormat = "WEBP",
            isSuccess = true
        )
    }

    private fun processBitmapToWebpStrict(sourceBitmap: Bitmap, targetKb: Int, outFile: File) {
        val targetBytes = targetKb * 1024L
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        var current = sourceBitmap
        var isRecyclable = false
        var bestBytes: ByteArray? = null

        try {
            var attempt = 0
            while (attempt < 8) {
                attempt++
                var lowQ = 5
                var highQ = 95
                var found: ByteArray? = null

                while (lowQ <= highQ) {
                    val midQ = (lowQ + highQ) / 2
                    val bos = ByteArrayOutputStream()
                    current.compress(format, midQ, bos)
                    if (bos.size().toLong() <= targetBytes) {
                        found = bos.toByteArray()
                        lowQ = midQ + 1
                    } else {
                        highQ = midQ - 1
                    }
                }

                if (found != null) {
                    bestBytes = found
                    break
                }

                val testBos = ByteArrayOutputStream()
                current.compress(format, 10, testBos)
                val ratio = (kotlin.math.sqrt(targetBytes.toDouble() / testBos.size().toDouble().coerceAtLeast(1.0)) * 0.88).coerceIn(0.1, 0.85)
                val nw = max(40, (current.width * ratio).toInt())
                val nh = max(40, (current.height * ratio).toInt())
                val scaled = Bitmap.createScaledBitmap(current, nw, nh, true)
                if (isRecyclable) current.recycle()
                current = scaled
                isRecyclable = true
            }

            if (bestBytes == null) {
                val bos = ByteArrayOutputStream()
                current.compress(format, 10, bos)
                bestBytes = bos.toByteArray()
            }

            FileOutputStream(outFile).use { fos ->
                fos.write(bestBytes)
            }
        } finally {
            if (isRecyclable) current.recycle()
        }
    }

    // --- Packaging & Export Helpers ---

    /**
     * Packs all processed files into a single ZIP archive.
     */
    suspend fun createZipArchive(
        files: List<File>,
        zipOutputFile: File
    ): File = withContext(Dispatchers.IO) {
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipOutputFile))).use { zos ->
            val buffer = ByteArray(16 * 1024)
            for (file in validFiles) {
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                BufferedInputStream(FileInputStream(file)).use { bis ->
                    var count: Int
                    while (bis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
            }
        }
        zipOutputFile
    }

    /**
     * Saves a single processed file to the device Gallery if it is an image,
     * or to public Downloads if it is a PDF without re-encoding or size loss.
     */
    fun saveSingleFileToGallery(context: Context, file: File): Uri? {
        if (!file.exists() || file.length() == 0L) return null
        val name = file.name.lowercase()
        val isImage = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
        return if (isImage) {
            val mime = when {
                name.endsWith(".png") -> "image/png"
                name.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }
            DocuStorageUtil.saveImageFileToGallery(context, file, file.name, mime)
        } else {
            DocuStorageUtil.saveFileToPublicDownloads(context, file, file.name, "application/pdf")
        }
    }

    /**
     * Saves all processed image files to the device Gallery (Pictures/DocuEdit),
     * and any PDF files to public Downloads (Downloads/DocuEdit).
     * Returns the count of successfully saved files.
     */
    suspend fun saveAllToGallery(context: Context, files: List<File>): Int = withContext(Dispatchers.IO) {
        var count = 0
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        for (file in validFiles) {
            val uri = saveSingleFileToGallery(context, file)
            if (uri != null) count++
        }
        count
    }

    /**
     * Copies all converted files into public Downloads using Android Scoped Storage.
     * Returns the count of successfully saved files.
     */
    suspend fun saveAllToDownloads(
        context: Context,
        files: List<File>,
        customFolderName: String = "DocuEdit_Batch_${System.currentTimeMillis()}"
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        for (file in validFiles) {
            val mime = when {
                file.name.endsWith(".pdf", true) -> "application/pdf"
                file.name.endsWith(".png", true) -> "image/png"
                file.name.endsWith(".webp", true) -> "image/webp"
                else -> "image/jpeg"
            }
            val uri = DocuStorageUtil.saveFileToPublicDownloads(context, file, file.name, mime)
            if (uri != null) count++
        }
        count
    }

    /**
     * Resolves FileProvider Uris for batch sharing via Intent.ACTION_SEND_MULTIPLE.
     */
    fun getShareUris(context: Context, files: List<File>): ArrayList<Uri> {
        val uris = ArrayList<Uri>()
        val authority = "${context.packageName}.fileprovider"
        for (file in files) {
            if (file.exists() && file.length() > 0L) {
                try {
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    uris.add(uri)
                } catch (_: Exception) {}
            }
        }
        return uris
    }
}
