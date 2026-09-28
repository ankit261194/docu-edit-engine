package com.docu.editor.core.export

import java.io.File

object SecurityMetadataSanitizer {
    suspend fun sanitizeJpegImage(jpegFile: File) = MetadataSanitizer.sanitizeJpeg(jpegFile)
    suspend fun sanitizePdfDocument(pdfFile: File) = MetadataSanitizer.sanitizePdf(pdfFile)
}
