package com.docu.editor.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import com.docu.editor.core.ocr.model.DetectedTextItem
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
import kotlin.math.min

data class AcroFormFieldItem(
    val name: String,
    val type: String, // "TEXT", "CHECKBOX", "COMBOBOX", "RADIO", "OTHER"
    val value: String,
    val isReadOnly: Boolean = false,
    val isFlatField: Boolean = false,
    val boundsLeft: Int = 0,
    val boundsTop: Int = 0,
    val boundsRight: Int = 0,
    val boundsBottom: Int = 0,
    val baselineY: Float = 0f
)

/**
 * Enterprise Interactive AcroForm & Flat Form PDF Manager — 100% Pro.
 * Detects, inspects, and digitally populates interactive government/business PDF form fields,
 * and automatically detects underlined fillable blanks and checkboxes on non-fillable flat scanned forms.
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
                                isReadOnly = field.isReadOnly,
                                isFlatField = false
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

    /**
     * Non-Fillable Flat PDF & Paper Form Auto-Detector.
     * Scans scanned document pages and detects fillable blanks (e.g. "Name: ______", "DOB: ___/___/_____"),
     * check-boxes (e.g. "[ ]", "□"), and prompt labels followed by blank writing space.
     * Generates interactive field overlays that can be filled and burnt cleanly onto the page.
     */
    fun detectFlatFormFields(
        context: Context,
        bitmap: Bitmap,
        detectedItems: List<DetectedTextItem>
    ): List<AcroFormFieldItem> {
        val formFields = mutableListOf<AcroFormFieldItem>()
        val seenNames = mutableSetOf<String>()

        val underlineRegex = Regex("""(?i)([A-Za-z0-9\s/&.'()#-]{2,35}?)(?:[:\-–]\s*)(_{2,}|\.{3,}|…+)""")
        val checkboxRegex1 = Regex("""(?i)(\[[\s_xX]?\]|\([\s_xX]?\)|□|■|◻|▫)\s*([A-Za-z0-9\s/&.'()#-]{2,30})""")
        val checkboxRegex2 = Regex("""(?i)([A-Za-z0-9\s/&.'()#-]{2,30}?)\s*(\[[\s_xX]?\]|\([\s_xX]?\)|□|■|◻|▫)""")

        val commonPrompts = listOf(
            "Name", "Full Name", "Applicant Name", "Student Name", "Candidate Name", "Father's Name", "Mother's Name",
            "Date of Birth", "DOB", "Age", "Gender", "Sex", "Nationality", "Category", "Marital Status",
            "Address", "Permanent Address", "Current Address", "Residential Address", "Street", "City", "State", "Pin Code", "Postal Code",
            "Mobile", "Phone", "Mobile No", "Phone No", "Contact No", "Telephone", "Email", "Email ID",
            "Aadhaar No", "PAN No", "Roll No", "Registration No", "Application No", "ID No", "Passport No",
            "Designation", "Occupation", "Department", "Institution", "College", "School", "Qualification",
            "Date", "Place", "Signature", "Sign", "Authorized Signatory", "Remarks"
        )

        val bmpW = bitmap.width
        val bmpH = bitmap.height

        for (item in detectedItems) {
            val text = item.text.trim()
            if (text.isEmpty()) continue
            val b = item.boundingBox

            // 1. Check for underline run embedded in text: "Name: ____________"
            val underMatch = underlineRegex.find(text)
            if (underMatch != null) {
                val rawLabel = underMatch.groupValues[1].trim()
                if (rawLabel.length >= 2 && seenNames.add(rawLabel.lowercase())) {
                    val labelRatio = (rawLabel.length.toFloat() / text.length.toFloat()).coerceIn(0.1f, 0.9f)
                    val blankLeft = (b.left + (b.width() * labelRatio)).toInt().coerceIn(0, bmpW - 1)
                    val blankRight = b.right.coerceIn(blankLeft + 20, bmpW)
                    val baseline = (b.bottom - 4).toFloat().coerceIn(0f, bmpH.toFloat())

                    formFields.add(
                        AcroFormFieldItem(
                            name = rawLabel,
                            type = "TEXT",
                            value = "",
                            isReadOnly = false,
                            isFlatField = true,
                            boundsLeft = blankLeft,
                            boundsTop = b.top,
                            boundsRight = blankRight,
                            boundsBottom = b.bottom,
                            baselineY = baseline
                        )
                    )
                    continue
                }
            }

            // 2. Check for check-boxes in text
            val cbMatch1 = checkboxRegex1.find(text)
            if (cbMatch1 != null) {
                val cbLabel = cbMatch1.groupValues[2].trim()
                if (cbLabel.length >= 2 && seenNames.add(cbLabel.lowercase())) {
                    formFields.add(
                        AcroFormFieldItem(
                            name = cbLabel,
                            type = "CHECKBOX",
                            value = "false",
                            isReadOnly = false,
                            isFlatField = true,
                            boundsLeft = b.left,
                            boundsTop = b.top,
                            boundsRight = b.left + (b.height()).coerceIn(16, 40),
                            boundsBottom = b.bottom,
                            baselineY = (b.bottom - 2).toFloat()
                        )
                    )
                    continue
                }
            }

            val cbMatch2 = checkboxRegex2.find(text)
            if (cbMatch2 != null) {
                val cbLabel = cbMatch2.groupValues[1].trim()
                if (cbLabel.length >= 2 && seenNames.add(cbLabel.lowercase())) {
                    formFields.add(
                        AcroFormFieldItem(
                            name = cbLabel,
                            type = "CHECKBOX",
                            value = "false",
                            isReadOnly = false,
                            isFlatField = true,
                            boundsLeft = b.right - (b.height()).coerceIn(16, 40),
                            boundsTop = b.top,
                            boundsRight = b.right,
                            boundsBottom = b.bottom,
                            baselineY = (b.bottom - 2).toFloat()
                        )
                    )
                    continue
                }
            }

            // 3. Prompt label with colon or keyword followed by empty writing area
            val isPromptEndingWithColon = text.endsWith(":") && text.length in 3..40
            val matchingKeyword = commonPrompts.firstOrNull { kw ->
                text.equals(kw, ignoreCase = true) ||
                text.equals("$kw:", ignoreCase = true) ||
                text.startsWith("$kw:", ignoreCase = true) ||
                text.startsWith("$kw -", ignoreCase = true)
            }

            if ((isPromptEndingWithColon || matchingKeyword != null) && b.right < (bmpW - 80)) {
                val cleanLabel = (matchingKeyword ?: text.removeSuffix(":")).trim()
                if (cleanLabel.length >= 2 && seenNames.add(cleanLabel.lowercase())) {
                    val fillWidth = (b.width() * 2.2f).toInt().coerceIn(180, (bmpW - b.right - 20).coerceAtLeast(180))
                    val blankLeft = b.right + 10
                    val blankRight = (blankLeft + fillWidth).coerceAtMost(bmpW - 10)
                    val baseline = (b.bottom - 4).toFloat().coerceIn(0f, bmpH.toFloat())

                    formFields.add(
                        AcroFormFieldItem(
                            name = cleanLabel,
                            type = if (cleanLabel.equals("Gender", true) || cleanLabel.equals("Sex", true)) "CHECKBOX" else "TEXT",
                            value = "",
                            isReadOnly = false,
                            isFlatField = true,
                            boundsLeft = blankLeft,
                            boundsTop = b.top,
                            boundsRight = blankRight,
                            boundsBottom = b.bottom,
                            baselineY = baseline
                        )
                    )
                }
            }
        }

        return formFields
    }

    /**
     * Stamps entered form field values permanently into the document page bitmap at the detected blank positions.
     * Formats text using crisp document ink typography and marks checkboxes with authentic pen checkmarks.
     */
    fun burnFlatFormFields(
        bitmap: Bitmap,
        fields: List<AcroFormFieldItem>,
        fieldValues: Map<String, String>
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(15, 23, 42) // Crisp midnight ink
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(29, 78, 216) // Blue ballpoint pen ink
            strokeWidth = 3.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (field in fields) {
            val enteredValue = fieldValues[field.name]?.trim() ?: continue
            if (enteredValue.isEmpty()) continue

            if (field.type == "CHECKBOX") {
                val isChecked = enteredValue.equals("true", ignoreCase = true) ||
                                enteredValue == "1" ||
                                enteredValue.equals("yes", ignoreCase = true)
                if (isChecked) {
                    val cx = (field.boundsLeft + field.boundsRight) / 2f
                    val cy = (field.boundsTop + field.boundsBottom) / 2f
                    val boxW = (field.boundsRight - field.boundsLeft).coerceAtLeast(20)
                    val boxH = (field.boundsBottom - field.boundsTop).coerceAtLeast(20)
                    val size = min(boxW, boxH).toFloat().coerceIn(16f, 36f)

                    val path = Path().apply {
                        moveTo(cx - size * 0.35f, cy)
                        lineTo(cx - size * 0.05f, cy + size * 0.32f)
                        lineTo(cx + size * 0.40f, cy - size * 0.30f)
                    }
                    canvas.drawPath(path, checkPaint)
                }
            } else {
                val boxHeight = (field.boundsBottom - field.boundsTop).coerceAtLeast(18)
                val targetSize = (boxHeight * 0.78f).coerceIn(14f, 40f)
                textPaint.textSize = targetSize

                val drawX = (field.boundsLeft + 4).toFloat()
                val drawY = if (field.baselineY > 0f) field.baselineY else (field.boundsBottom - 4).toFloat()

                canvas.drawText(enteredValue, drawX, drawY, textPaint)
            }
        }

        return result
    }
}
