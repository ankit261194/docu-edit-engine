package com.docu.editor.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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
}
