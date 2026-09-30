package com.docu.editor.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Enterprise Hybrid Vector-Preserving PDF Exporter.
 * For unedited pages: Preserves 100% original PostScript vector text, embedded subset fonts,
 * and vector curves with ZERO rasterization.
 * For edited pages: Embeds 300 DPI high-resolution canvas with full OCR text layer.
 */
object PdfHybridExporter {

    suspend fun exportHybridPdf(
        context: Context,
        sourcePdfUri: Uri,
        editedPagesMap: Map<Int, Bitmap>,
        pagesDetectedItems: Map<Int, List<DetectedTextItem>>,
        outputFile: File,
        ocrFallbackProvider: (suspend (Bitmap) -> List<DetectedTextItem>)? = null
    ): File = withContext(Dispatchers.IO) {
        val tempEditedFiles = mutableListOf<File>()

        try {
            val sourceStream = context.contentResolver.openInputStream(sourcePdfUri)
                ?: throw IllegalStateException("Cannot open source PDF URI: $sourcePdfUri")

            val sourceDoc = PDDocument.load(sourceStream)
            val outputDoc = PDDocument()

            val totalPages = sourceDoc.numberOfPages

            for (pageIdx in 0 until totalPages) {
                val editedBitmap = editedPagesMap[pageIdx]
                if (editedBitmap == null) {
                    // Page was NEVER touched or edited: Import original crisp vector page 100% untouched!
                    val pristinePage = sourceDoc.getPage(pageIdx)
                    outputDoc.importPage(pristinePage)
                } else {
                    // Page was edited: Export single page with 300 DPI canvas + OCR layer
                    val tempPagePdf = File(context.cacheDir, "hybrid_page_${pageIdx}_${System.currentTimeMillis()}.pdf")
                    tempEditedFiles.add(tempPagePdf)

                    PdfExportEngine.exportBitmapToPdf(
                        bitmap = editedBitmap,
                        outputFile = tempPagePdf,
                        fitToA4 = true,
                        detectedItems = pagesDetectedItems[pageIdx] ?: emptyList(),
                        ocrFallbackProvider = ocrFallbackProvider
                    )

                    PDDocument.load(tempPagePdf).use { editedPageDoc ->
                        if (editedPageDoc.numberOfPages > 0) {
                            outputDoc.importPage(editedPageDoc.getPage(0))
                        }
                    }
                }
            }

            sourceDoc.close()

            FileOutputStream(outputFile).use { fos ->
                outputDoc.save(fos)
            }
            outputDoc.close()

            outputFile
        } finally {
            tempEditedFiles.forEach { it.delete() }
        }
    }
}
