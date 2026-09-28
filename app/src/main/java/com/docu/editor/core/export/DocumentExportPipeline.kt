package com.docu.editor.core.export

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentExportPipeline(private val context: Context) {

    data class ExportResult(
        val file: File,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val dpi: Int = 300
    )

    suspend fun exportImage(
        bitmap: Bitmap,
        quality: Int = 96
    ): ExportResult = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, "DOC_${System.currentTimeMillis()}.jpg")

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }

        MetadataSanitizer.sanitizeJpeg(outputFile)

        ExportResult(
            file = outputFile,
            mimeType = "image/jpeg",
            width = bitmap.width,
            height = bitmap.height,
            dpi = 300
        )
    }
}
