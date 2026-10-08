package com.docu.editor.core.classification

import androidx.compose.ui.graphics.Color
import java.util.Locale
import java.util.regex.Pattern

/**
 * Enterprise AI Document Auto-Classifier.
 * Categorizes documents into 6 canonical categories with specific subtypes:
 * 1. Identity (Aadhaar, PAN, Voter, Passport, Driving License)
 * 2. Health (Prescription, Lab Test Report, Medical Certificates)
 * 3. Academics (Mark Sheet, Degree, Admit Card, Study Notes)
 * 4. Bills & Finance (Electricity, Rent, Invoice, Salary Slip, Bank Statement)
 * 5. Legal (Affidavit, Agreement, Stamp Paper, Contract)
 * 6. General (Other scanned documents)
 */
object DocumentClassificationEngine {

    enum class CanonicalCategory(val displayName: String, val badgeColor: Color) {
        ALL("All", Color(0xFF64748B)),
        IDENTITY("Identity", Color(0xFF2563EB)),
        FINANCE("Bills & Finance", Color(0xFFD97706)),
        HEALTH("Health", Color(0xFF059669)),
        ACADEMICS("Academics", Color(0xFF7C3AED)),
        LEGAL("Legal", Color(0xFFDC2626)),
        GENERAL("General", Color(0xFF6B7280));

        companion object {
            fun fromString(name: String): CanonicalCategory {
                return values().find {
                    it.displayName.equals(name, ignoreCase = true) ||
                    it.name.equals(name, ignoreCase = true)
                } ?: GENERAL
            }
        }
    }

    data class ClassificationResult(
        val category: CanonicalCategory,
        val subtype: String,
        val confidence: Float,
        val matchedKeywords: List<String>
    )

    // Regex Patterns for Indian & Global Identity Documents
    private val AADHAAR_REGEX = Pattern.compile("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b")
    private val PAN_REGEX = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b")
    private val PASSPORT_REGEX = Pattern.compile("\\b[A-Z][0-9]{7}\\b")
    private val DL_REGEX = Pattern.compile("\\b[A-Z]{2}[0-9]{2}\\s?[0-9]{11}\\b")
    private val GSTIN_REGEX = Pattern.compile("\\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b")

    fun classify(ocrText: String, title: String = ""): ClassificationResult {
        val combinedText = "${title.lowercase(Locale.ROOT)} \n ${ocrText.lowercase(Locale.ROOT)}"
        if (combinedText.isBlank()) {
            return ClassificationResult(CanonicalCategory.GENERAL, "General Document", 0.5f, emptyList())
        }

        val scores = mutableMapOf<CanonicalCategory, Float>()
        val matchedWords = mutableMapOf<CanonicalCategory, MutableList<String>>()

        fun addScore(cat: CanonicalCategory, points: Float, keyword: String) {
            scores[cat] = (scores[cat] ?: 0f) + points
            matchedWords.getOrPut(cat) { mutableListOf() }.add(keyword)
        }

        // --- 1. IDENTITY CLASSIFICATION ---
        if (AADHAAR_REGEX.matcher(ocrText).find()) {
            addScore(CanonicalCategory.IDENTITY, 4.0f, "12-digit UID pattern")
        }
        if (PAN_REGEX.matcher(ocrText).find()) {
            addScore(CanonicalCategory.IDENTITY, 4.0f, "10-char PAN pattern")
        }
        if (PASSPORT_REGEX.matcher(ocrText).find() && (combinedText.contains("passport") || combinedText.contains("republic of india"))) {
            addScore(CanonicalCategory.IDENTITY, 4.0f, "Passport number pattern")
        }

        val identityKeywords = mapOf(
            "aadhaar" to 3.5f, "uidai" to 3.5f, "unique identification" to 3.0f, "mera aadhaar" to 3.0f,
            "income tax department" to 3.5f, "permanent account number" to 3.5f, "father's name" to 1.5f,
            "driving licence" to 3.5f, "driving license" to 3.5f, "transport department" to 3.0f, "authorisation to drive" to 3.0f,
            "election commission" to 3.5f, "voter" to 3.0f, "epic no" to 3.5f, "elector" to 2.5f,
            "republic of india" to 2.0f, "date of birth" to 1.5f, "nationality" to 2.0f
        )
        for ((kw, wt) in identityKeywords) {
            if (combinedText.contains(kw)) addScore(CanonicalCategory.IDENTITY, wt, kw)
        }

        // --- 2. HEALTH CLASSIFICATION ---
        val healthKeywords = mapOf(
            "prescription" to 3.5f, "rx" to 3.0f, "doctor" to 2.0f, "dr." to 1.5f, "clinic" to 2.0f,
            "hospital" to 2.5f, "diagnosis" to 2.5f, "tablet" to 2.0f, "capsule" to 2.0f, "dosage" to 2.0f,
            "pathology" to 3.5f, "blood test" to 3.0f, "laboratory" to 2.5f, "hemoglobin" to 3.0f,
            "cbc" to 3.0f, "urine analysis" to 3.0f, "reference range" to 3.0f, "normal value" to 2.5f,
            "discharge summary" to 3.5f, "patient" to 2.0f, "consultant" to 2.0f, "opd" to 2.0f, "mg" to 1.0f
        )
        for ((kw, wt) in healthKeywords) {
            if (combinedText.contains(kw)) addScore(CanonicalCategory.HEALTH, wt, kw)
        }

        // --- 3. ACADEMICS CLASSIFICATION ---
        val academicKeywords = mapOf(
            "mark sheet" to 3.5f, "marksheet" to 3.5f, "grade sheet" to 3.5f, "roll no" to 2.5f, "enrollment no" to 2.5f,
            "semester" to 2.5f, "university" to 2.5f, "board of education" to 3.0f, "cbse" to 3.0f, "icse" to 3.0f,
            "examination" to 2.0f, "cgpa" to 3.0f, "sgpa" to 3.0f, "maximum marks" to 2.5f, "marks obtained" to 3.0f,
            "admit card" to 3.5f, "hall ticket" to 3.5f, "candidate name" to 2.0f, "exam centre" to 2.5f,
            "degree certificate" to 3.5f, "bachelor of" to 3.0f, "master of" to 3.0f, "conferred upon" to 3.0f,
            "subject" to 1.5f, "theory" to 1.5f, "practical" to 1.5f, "grade" to 1.5f
        )
        for ((kw, wt) in academicKeywords) {
            if (combinedText.contains(kw)) addScore(CanonicalCategory.ACADEMICS, wt, kw)
        }

        // --- 4. BILLS & FINANCE CLASSIFICATION ---
        if (GSTIN_REGEX.matcher(ocrText).find()) {
            addScore(CanonicalCategory.FINANCE, 3.5f, "GSTIN pattern")
        }
        val financeKeywords = mapOf(
            "tax invoice" to 3.5f, "invoice" to 3.0f, "bill" to 2.0f, "hsn" to 2.5f, "sac" to 2.0f,
            "subtotal" to 2.5f, "cgst" to 3.0f, "sgst" to 3.0f, "igst" to 3.0f, "total amount" to 2.5f, "net amount" to 2.5f,
            "electricity bill" to 3.5f, "power supply" to 2.5f, "consumer no" to 3.0f, "meter reading" to 3.0f,
            "due date" to 2.5f, "pay by" to 2.5f, "salary slip" to 3.5f, "payslip" to 3.5f, "basic pay" to 3.0f,
            "provident fund" to 3.0f, "hra" to 2.5f, "bank statement" to 3.5f, "account no" to 2.0f, "ifsc" to 2.5f,
            "water bill" to 3.5f, "rent receipt" to 3.5f, "purchase order" to 3.0f
        )
        for ((kw, wt) in financeKeywords) {
            if (combinedText.contains(kw)) addScore(CanonicalCategory.FINANCE, wt, kw)
        }

        // --- 5. LEGAL CLASSIFICATION ---
        val legalKeywords = mapOf(
            "affidavit" to 3.5f, "sworn before me" to 3.5f, "deponent" to 3.5f, "solemnly affirm" to 3.5f,
            "notary public" to 3.5f, "agreement" to 2.5f, "non-disclosure" to 3.5f, "memorandum" to 2.5f,
            "whereas" to 2.5f, "in witness whereof" to 3.0f, "hereinafter referred to" to 3.0f, "terms and conditions" to 2.0f,
            "stamp duty" to 3.5f, "non judicial" to 3.5f, "stamp paper" to 3.5f, "e-stamp" to 3.5f,
            "court of" to 3.0f, "advocate" to 2.5f, "power of attorney" to 3.5f
        )
        for ((kw, wt) in legalKeywords) {
            if (combinedText.contains(kw)) addScore(CanonicalCategory.LEGAL, wt, kw)
        }

        // Determine Highest Scoring Category
        val bestEntry = scores.maxByOrNull { it.value }
        val category = if (bestEntry != null && bestEntry.value >= 2.0f) bestEntry.key else CanonicalCategory.GENERAL
        val matched = matchedWords[category]?.distinct() ?: emptyList()
        val score = bestEntry?.value ?: 0f
        val confidence = (score / (score + 3.0f)).coerceIn(0.5f, 0.98f)

        // Refine Subtype
        val subtype = determineSubtype(category, combinedText)

        return ClassificationResult(
            category = category,
            subtype = subtype,
            confidence = confidence,
            matchedKeywords = matched
        )
    }

    private fun determineSubtype(category: CanonicalCategory, text: String): String {
        return when (category) {
            CanonicalCategory.IDENTITY -> {
                when {
                    text.contains("aadhaar") || text.contains("uidai") -> "Aadhaar Card"
                    text.contains("permanent account") || text.contains("income tax") -> "PAN Card"
                    text.contains("driving licence") || text.contains("driving license") -> "Driving License"
                    text.contains("passport") -> "Passport"
                    text.contains("election commission") || text.contains("voter") -> "Voter ID Card"
                    else -> "Identity Document"
                }
            }
            CanonicalCategory.HEALTH -> {
                when {
                    text.contains("prescription") || text.contains("rx") -> "Doctor Prescription"
                    text.contains("pathology") || text.contains("blood") || text.contains("laboratory") -> "Lab Test Report"
                    text.contains("discharge summary") -> "Hospital Discharge Summary"
                    else -> "Medical Record"
                }
            }
            CanonicalCategory.ACADEMICS -> {
                when {
                    text.contains("mark sheet") || text.contains("marksheet") || text.contains("grades") -> "Academic Mark Sheet"
                    text.contains("degree") || text.contains("bachelor") || text.contains("master") -> "Degree Certificate"
                    text.contains("admit card") || text.contains("hall ticket") -> "Exam Admit Card"
                    else -> "Academic Document"
                }
            }
            CanonicalCategory.FINANCE -> {
                when {
                    text.contains("electricity") || text.contains("power supply") -> "Electricity Bill"
                    text.contains("tax invoice") || text.contains("gstin") -> "Tax Invoice"
                    text.contains("salary") || text.contains("payslip") || text.contains("basic pay") -> "Salary Pay Slip"
                    text.contains("bank statement") -> "Bank Statement"
                    text.contains("water bill") -> "Water Utility Bill"
                    text.contains("rent") -> "Rent Receipt"
                    else -> "Invoice / Bill"
                }
            }
            CanonicalCategory.LEGAL -> {
                when {
                    text.contains("affidavit") || text.contains("deponent") -> "Notarized Affidavit"
                    text.contains("non-disclosure") || text.contains("nda") -> "NDA Agreement"
                    text.contains("stamp paper") || text.contains("stamp duty") || text.contains("e-stamp") -> "Stamp Paper"
                    text.contains("power of attorney") -> "Power of Attorney"
                    else -> "Legal Agreement"
                }
            }
            CanonicalCategory.ALL,
            CanonicalCategory.GENERAL -> "General Document"
        }
    }
}
