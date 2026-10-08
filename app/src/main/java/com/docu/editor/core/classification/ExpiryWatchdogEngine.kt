package com.docu.editor.core.classification

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Enterprise Expiry & Renewal Date Extractor.
 * Scans OCR text from IDs, Passports, Driving Licenses, Insurance Policies, and Utility Bills
 * to automatically detect expiration and payment due dates.
 */
object ExpiryWatchdogEngine {

    enum class ExpiryStatus(val label: String, val badgeColor: Color) {
        EXPIRED("Expired", Color(0xFFDC2626)),
        URGENT("Urgent Expiry", Color(0xFFEA580C)),
        EXPIRING_SOON("Expiring Soon", Color(0xFFD97706)),
        VALID("Active / Valid", Color(0xFF16A34A)),
        NONE("No Expiry", Color(0xFF64748B))
    }

    data class ExpiryDetectionResult(
        val hasExpiry: Boolean,
        val formattedDate: String = "",
        val expiryEpochMs: Long = 0L,
        val daysRemaining: Int = 0,
        val status: ExpiryStatus = ExpiryStatus.NONE,
        val keywordFound: String = "",
        val contextSnippet: String = ""
    )

    // Trigger Keywords that precede an Expiry or Due Date
    private val EXPIRY_KEYWORDS = listOf(
        "valid till", "valid upto", "valid up to", "validity", "valid through",
        "expiry date", "date of expiry", "expires on", "expires", "exp date", "exp.",
        "due date", "payment due", "bill due date", "pay before", "pay by",
        "renewal date", "renewal due", "renew before", "renew by",
        "coverage end date", "policy end date", "maturity date"
    )

    private val NUMERIC_DATE_REGEX = Pattern.compile("\\b(\\d{1,2})[\\/\\-\\.](\\d{1,2})[\\/\\-\\.](\\d{2,4})\\b")
    private val ISO_DATE_REGEX = Pattern.compile("\\b(\\d{4})[\\/\\-](\\d{1,2})[\\/\\-](\\d{1,2})\\b")
    private val TEXT_DATE_REGEX_1 = Pattern.compile("\\b(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s,]+(\\d{2,4})\\b", Pattern.CASE_INSENSITIVE)
    private val TEXT_DATE_REGEX_2 = Pattern.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{1,2})[\\s,]+(\\d{2,4})\\b", Pattern.CASE_INSENSITIVE)

    fun extractExpiry(ocrText: String): ExpiryDetectionResult {
        if (ocrText.isBlank()) return ExpiryDetectionResult(hasExpiry = false)

        val lines = ocrText.lines()
        for (lineIdx in lines.indices) {
            val line = lines[lineIdx].trim()
            val lowerLine = line.lowercase(Locale.ROOT)

            for (kw in EXPIRY_KEYWORDS) {
                if (lowerLine.contains(kw)) {
                    // 1. Search in the same line
                    var foundDate = findDateInString(line)
                    var context = line

                    // 2. If not in the same line, check the next line (often values are on the next line)
                    if (foundDate == null && lineIdx + 1 < lines.size) {
                        val nextLine = lines[lineIdx + 1].trim()
                        foundDate = findDateInString(nextLine)
                        if (foundDate != null) {
                            context = "$line: $nextLine"
                        }
                    }

                    if (foundDate != null) {
                        val epochMs = foundDate.time
                        val now = System.currentTimeMillis()
                        val diffDays = ((epochMs - now) / (1000L * 60L * 60L * 24L)).toInt()

                        val status = when {
                            diffDays < 0 -> ExpiryStatus.EXPIRED
                            diffDays in 0..7 -> ExpiryStatus.URGENT
                            diffDays in 8..30 -> ExpiryStatus.EXPIRING_SOON
                            else -> ExpiryStatus.VALID
                        }

                        val formatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(foundDate)

                        return ExpiryDetectionResult(
                            hasExpiry = true,
                            formattedDate = formatted,
                            expiryEpochMs = epochMs,
                            daysRemaining = diffDays,
                            status = status,
                            keywordFound = kw.replaceFirstChar { it.uppercase() },
                            contextSnippet = context
                        )
                    }
                }
            }
        }

        return ExpiryDetectionResult(hasExpiry = false)
    }

    private fun findDateInString(text: String): Date? {
        // Try Text formats first (e.g. 14 May 2028)
        val textMatcher1 = TEXT_DATE_REGEX_1.matcher(text)
        if (textMatcher1.find()) {
            val dateStr = textMatcher1.group(0)
            dateStr?.let { parseAnyDate(it) }?.let { return it }
        }

        val textMatcher2 = TEXT_DATE_REGEX_2.matcher(text)
        if (textMatcher2.find()) {
            val dateStr = textMatcher2.group(0)
            dateStr?.let { parseAnyDate(it) }?.let { return it }
        }

        // Try numeric formats (e.g. 14/05/2028 or 14-05-2028)
        val numMatcher = NUMERIC_DATE_REGEX.matcher(text)
        if (numMatcher.find()) {
            val dateStr = numMatcher.group(0)
            dateStr?.let { parseAnyDate(it) }?.let { return it }
        }

        // Try ISO format (e.g. 2028-05-14)
        val isoMatcher = ISO_DATE_REGEX.matcher(text)
        if (isoMatcher.find()) {
            val dateStr = isoMatcher.group(0)
            dateStr?.let { parseAnyDate(it) }?.let { return it }
        }

        return null
    }

    private fun parseAnyDate(input: String): Date? {
        val clean = input.replace(',', ' ').trim().replace("\\s+".toRegex(), " ")
        val patterns = listOf(
            "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
            "d/M/yyyy", "d-M-yyyy", "d.M.yyyy",
            "dd/MM/yy", "dd-MM-yy", "dd.MM.yy",
            "yyyy-MM-dd", "yyyy/MM/dd",
            "dd MMM yyyy", "d MMM yyyy",
            "MMM dd yyyy", "MMM d yyyy"
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(clean)
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply { time = parsed }
                    val year = cal.get(Calendar.YEAR)
                    // Plausible document expiry years between 2000 and 2099
                    if (year in 2000..2099) {
                        return parsed
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }
}
