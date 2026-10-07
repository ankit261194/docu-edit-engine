package com.docu.editor.core.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class AcroFormFieldItem(
    val name: String,
    val type: String, // "TEXT", "CHECKBOX", "COMBOBOX", "RADIO", "OTHER"
    val value: String,
    val isReadOnly: Boolean
)

/**
 * Enterprise Interactive AcroForm PDF Manager.
 * Detects, inspects, and digitally populates interactive government/business PDF form fields
 * directly into the native PDF object structure without rasterization.
 */
object AcroFormManager {

    suspend fun hasAcroForm(context: Context, pdfUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { doc ->
                    val acroForm = doc.documentCatalog.acroForm
                    acroForm != null && acroForm.fields.isNotEmpty()
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getFormFields(context: Context, pdfUri: Uri): List<AcroFormFieldItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AcroFormFieldItem>()
        try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { doc ->
                    val acroForm = doc.documentCatalog.acroForm ?: return@withContext emptyList()
                    for (field in acroForm.fields) {
                        val fieldName = field.fullyQualifiedName ?: field.partialName ?: "field"
                        val fieldType = when (field) {
                            is PDTextField -> "TEXT"
                            is PDCheckBox -> "CHECKBOX"
                            is PDComboBox -> "COMBOBOX"
                            is PDRadioButton -> "RADIO"
                            else -> "OTHER"
                        }
                        val value = field.valueAsString ?: ""
                        list.add(
                            AcroFormFieldItem(
                                name = fieldName,
                                type = fieldType,
                                value = value,
                                isReadOnly = field.isReadOnly
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun saveFormFields(
        context: Context,
        pdfUri: Uri,
        outputFile: File,
        fieldValues: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { doc ->
                    val acroForm = doc.documentCatalog.acroForm ?: return@withContext false
                    for ((name, newVal) in fieldValues) {
                        val field = acroForm.getField(name)
                        if (field != null) {
                            try {
                                when (field) {
                                    is PDTextField -> field.setValue(newVal)
                                    is PDCheckBox -> {
                                        if (newVal.equals("true", ignoreCase = true) || newVal == "1" || newVal.equals("yes", ignoreCase = true)) {
                                            field.check()
                                        } else {
                                            field.unCheck()
                                        }
                                    }
                                    else -> field.setValue(newVal)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    FileOutputStream(outputFile).use { out ->
                        doc.save(out)
                    }
                    true
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Bakes all interactive AcroForm form fields permanently into the PDF page stream,
     * converting filled data into non-editable, tamper-proof archival print vectors.
     */
    suspend fun flattenFormFields(
        context: Context,
        pdfUri: Uri,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { doc ->
                    val acroForm = doc.documentCatalog.acroForm ?: return@withContext false
                    acroForm.flatten()
                    FileOutputStream(outputFile).use { out ->
                        doc.save(out)
                    }
                    true
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
