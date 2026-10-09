package com.docu.editor.core.expense

import com.docu.editor.core.ocr.model.DetectedTextItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

/**
 * Intelligent Receipt Spatial Entity Extractor.
 * Parses scanned physical invoices, petrol pump slips, restaurant receipts, grocery bills,
 * and GST tax invoices with high spatial precision and robust Indian accounting heuristics.
 */
object ReceiptEntityExtractor {

    private val GSTIN_REGEX = Pattern.compile(
        "\\b([0-9]{2}[\\s\\-]?[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}[\\s\\-]?Z[0-9A-Z]{1})\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val INVOICE_NO_REGEX = Pattern.compile(
        "(?:Invoice\\s*(?:No\\.?|Number|#)|Bill\\s*(?:No\\.?|Number|#)|Inv\\s*(?:No\\.?|#)|Receipt\\s*(?:No\\.?|#)|Tax\\s*Inv\\s*No\\.?|Order\\s*(?:No\\.?|#)|Memo\\s*No\\.?|Slip\\s*No\\.?)\\s*[:\\-]?\\s*([A-Za-z0-9\\/\\-_#]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val DATE_PATTERNS = listOf(
        // dd/MM/yyyy or dd-MM-yyyy or dd.MM.yyyy
        Pattern.compile("\\b([0-3]?[0-9])[\\/\\.\\-]([0-1]?[0-9])[\\/\\.\\-]((?:20)?[0-9]{2,4})\\b"),
        // dd MMM yyyy (e.g. 15 Oct 2026, 05-Nov-2025)
        Pattern.compile("\\b([0-3]?[0-9])[\\s\\-\\/]+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[\\s\\-\\/,]+((?:20)?[0-9]{2,4})\\b", Pattern.CASE_INSENSITIVE),
        // yyyy-MM-dd
        Pattern.compile("\\b(20[0-9]{2})[\\/\\.\\-]([0-1]?[0-9])[\\/\\.\\-]([0-3]?[0-9])\\b")
    )

    private val CURRENCY_REGEX = Pattern.compile(
        "(?:(?:Rs\\.?|INR|₹)\\s*)?([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)(?:\\s*(?:\\/=|Rs\\.?|INR))?",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts full structured expense entity from OCR text and optional spatial layout items.
     */
    fun extract(
        ocrText: String,
        spatialItems: List<DetectedTextItem> = emptyList(),
        documentTitle: String = "Scanned Receipt",
        documentId: String? = null,
        imagePath: String? = null
    ): ExpenseReceiptItem {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        
        // 1. Merchant Extraction
        val merchantName = extractMerchantName(lines, spatialItems)

        // 2. GSTIN Extraction
        val gstin = extractGstin(ocrText)

        // 3. Invoice Number Extraction
        val invoiceNumber = extractInvoiceNumber(lines)

        // 4. Date Extraction
        val (billDateStr, billEpoch) = extractDate(lines, ocrText)

        // 5. Financial Entities (Subtotal, GST breakdown, Discount, Grand Total)
        val financialData = extractFinancials(lines)

        // 6. Expense Category Classification
        val category = classifyCategory(merchantName, ocrText)

        // 7. Payment Mode
        val paymentMode = extractPaymentMode(ocrText)

        return ExpenseReceiptItem(
            documentId = documentId,
            documentTitle = documentTitle,
            imagePath = imagePath,
            merchantName = merchantName,
            invoiceNumber = invoiceNumber,
            billDate = billDateStr,
            billEpochMs = billEpoch,
            gstin = gstin,
            subtotal = financialData.subtotal,
            cgst = financialData.cgst,
            sgst = financialData.sgst,
            igst = financialData.igst,
            totalGst = financialData.totalGst,
            discount = financialData.discount,
            grandTotal = financialData.grandTotal,
            category = category,
            paymentMode = paymentMode,
            rawOcrSnippet = ocrText.take(600),
            verifiedByUser = false
        )
    }

    /**
     * Extracts Merchant / Vendor Name using spatial position and semantic filtering.
     */

    private fun extractMerchantName(lines: List<String>, spatialItems: List<DetectedTextItem>): String {
        if (spatialItems.isNotEmpty()) {
            val topItems = spatialItems
                .filter { it.boundingBox.top < 650 }
                .sortedWith(compareByDescending<DetectedTextItem> { it.typography.estimatedFontSizePx }.thenBy { it.boundingBox.top })

            for (item in topItems) {
                val cleaned = cleanMerchantCandidate(item.text)
                if (isValidMerchantName(cleaned)) {
                    return cleaned
                }
            }
        }

        for (i in 0 until minOf(8, lines.size)) {
            val candidate = cleanMerchantCandidate(lines[i])
            if (isValidMerchantName(candidate)) {
                return candidate
            }
        }

        return "Retail Vendor"
    }

    private fun cleanMerchantCandidate(raw: String): String {
        var str = raw.trim()
        val prefixesToRemove = listOf(
            "Welcome to", "Welcome", "Thank You For Visiting", "Thank You", "Visit Again",
            "M/S", "M/s", "M/s.", "Store:", "Branch:", "Outlet:"
        )
        for (prefix in prefixesToRemove) {
            if (str.startsWith(prefix, ignoreCase = true)) {
                str = str.substring(prefix.length).trim()
            }
        }
        return str
    }

    private fun isValidMerchantName(text: String): Boolean {
        if (text.length < 3 || text.length > 55) return false
        val lower = text.lowercase(Locale.ROOT)
        val ignoreKeywords = listOf(
            "tax invoice", "retail invoice", "cash memo", "bill of supply", "estimate",
            "original for recipient", "duplicate", "gstin", "tel:", "phone:", "email:",
            "mobile", "date:", "time:", "inv no", "table no", "customer", "address",
            "pos transaction", "pos", "token", "receipt", "welcome"
        )
        if (ignoreKeywords.any { lower.contains(it) }) return false
        // Merchant should contain alphabetic characters
        return text.any { it.isLetter() }
    }

    /**
     * Extracts 15-digit Indian GSTIN from text.
     */
    private fun extractGstin(text: String): String {
        val matcher = GSTIN_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.replace("[\\s\\-]".toRegex(), "")?.uppercase(Locale.ROOT) ?: ""
        }
        return ""
    }

    /**
     * Extracts Invoice / Bill number.
     */
    private fun extractInvoiceNumber(lines: List<String>): String {
        for (line in lines) {
            val matcher = INVOICE_NO_REGEX.matcher(line)
            if (matcher.find()) {
                val found = matcher.group(1)?.trim() ?: ""
                val cleaned = found.trimEnd('.', ',', ';', ':')
                if (cleaned.length in 2..30) {
                    return cleaned
                }
            }
        }
        return ""
    }

    /**
     * Extracts invoice creation date and converts to Epoch MS.
     */
    private fun extractDate(lines: List<String>, fullText: String): Pair<String, Long> {
        val now = System.currentTimeMillis()
        val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

        // First check lines containing date keywords
        val datePrefixedLines = lines.filter { line ->
            val lower = line.lowercase(Locale.ROOT)
            lower.contains("date") || lower.contains("dated") || lower.contains("dt") || lower.contains("time")
        }

        for (line in datePrefixedLines + lines) {
            for (pattern in DATE_PATTERNS) {
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    val rawMatch = matcher.group(0) ?: continue
                    val parsedEpoch = parseDateToEpoch(rawMatch)
                    if (parsedEpoch != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = parsedEpoch }
                        return Pair(sdfDisplay.format(cal.time), parsedEpoch)
                    }
                }
            }
        }

        return Pair(sdfDisplay.format(now), now)
    }

    private fun parseDateToEpoch(rawDateStr: String): Long? {
        val clean = rawDateStr.replace(",", " ").trim()
        val formats = listOf(
            "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
            "dd/MM/yy", "dd-MM-yy", "dd.MM.yy",
            "dd MMM yyyy", "dd-MMM-yyyy", "dd MMM, yyyy",
            "yyyy-MM-dd", "yyyy/MM/dd"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH).apply { isLenient = false }
                val date = sdf.parse(clean)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    // Sanity check year between 2000 and 2035
                    if (cal.get(Calendar.YEAR) in 2000..2035) {
                        return date.time
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private data class ParsedFinancials(
        val subtotal: Double,
        val cgst: Double,
        val sgst: Double,
        val igst: Double,
        val totalGst: Double,
        val discount: Double,
        val grandTotal: Double
    )

    /**
     * Scans lines for amounts, subtotals, taxes, and grand totals.
     */
    private fun extractFinancials(lines: List<String>): ParsedFinancials {
        var subtotal = 0.0
        var cgst = 0.0
        var sgst = 0.0
        var igst = 0.0
        var totalGst = 0.0
        var discount = 0.0
        var grandTotal = 0.0

        val grandTotalKeywords = listOf(
            "grand total", "net payable", "amount payable", "total amount",
            "net amount", "total inr", "bill total", "invoice total", "final total",
            "total value", "amount due", "paid amount", "total paid", "total:"
        )

        val subtotalKeywords = listOf(
            "sub total", "subtotal", "taxable value", "taxable amount",
            "total excl tax", "basic amount", "net taxable"
        )

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)

            // 1. CGST
            if (lower.contains("cgst") || lower.contains("central gst") || lower.contains("c-gst")) {
                var amt = parseAmountFromLine(line)
                if (amt == 0.0 && i + 1 < lines.size) amt = parseAmountFromLine(lines[i + 1])
                if (amt > 0) cgst = amt
            }

            // 2. SGST / UTGST
            if (lower.contains("sgst") || lower.contains("state gst") || lower.contains("s-gst") || lower.contains("utgst")) {
                var amt = parseAmountFromLine(line)
                if (amt == 0.0 && i + 1 < lines.size) amt = parseAmountFromLine(lines[i + 1])
                if (amt > 0) sgst = amt
            }

            // 3. IGST
            if (lower.contains("igst") || lower.contains("integrated gst") || lower.contains("i-gst")) {
                var amt = parseAmountFromLine(line)
                if (amt == 0.0 && i + 1 < lines.size) amt = parseAmountFromLine(lines[i + 1])
                if (amt > 0) igst = amt
            }

            // 4. Generic GST / Tax Total
            if ((lower.contains("total gst") || lower.contains("tax amount") || lower.contains("total tax")) && totalGst == 0.0) {
                var amt = parseAmountFromLine(line)
                if (amt == 0.0 && i + 1 < lines.size) amt = parseAmountFromLine(lines[i + 1])
                if (amt > 0) totalGst = amt
            }

            // 5. Discount
            if (lower.contains("discount") || lower.contains("disc.") || lower.contains("less disc")) {
                var amt = parseAmountFromLine(line)
                if (amt == 0.0 && i + 1 < lines.size) amt = parseAmountFromLine(lines[i + 1])
                if (amt > 0) discount = amt
            }

            // 6. Subtotal
            if (subtotalKeywords.any { lower.contains(it) } && subtotal == 0.0) {
                var amt = parseAmountFromLine(line)
                if (amt == 0.0 && i + 1 < lines.size) amt = parseAmountFromLine(lines[i + 1])
                if (amt > 0) subtotal = amt
            }

            // 7. Grand Total
            val isTotalLine = grandTotalKeywords.any { lower.contains(it) } ||
                (lower.contains("total") && !lower.contains("sub") && !lower.contains("item") && !lower.contains("qty") && !lower.contains("tax") && !lower.contains("gst"))
            if (isTotalLine) {
                var amt = parseAmountFromLine(line)
                if (amt == 0.0 && i + 1 < lines.size) amt = parseAmountFromLine(lines[i + 1])
                if (amt > 0) grandTotal = amt
            }
        }

        // Calculate combined GST
        val calculatedGstSum = cgst + sgst + igst
        if (calculatedGstSum > 0) {
            totalGst = calculatedGstSum
        } else if (totalGst > 0 && cgst == 0.0 && sgst == 0.0 && igst == 0.0) {
            // Split generic GST equally into CGST & SGST (standard 50/50 intra-state rule)
            cgst = totalGst / 2.0
            sgst = totalGst / 2.0
        }

        // Fallback: If grandTotal is 0.0, find highest parsed number in the bottom 40% of the document
        if (grandTotal <= 0.0) {
            var highestBottomAmt = 0.0
            val bottomLines = lines.takeLast(max(5, (lines.size * 0.4).toInt()))
            for (bLine in bottomLines) {
                val amt = parseAmountFromLine(bLine)
                if (amt > highestBottomAmt && amt < 5000000.0) { // filter out phone numbers or invoice numbers
                    highestBottomAmt = amt
                }
            }
            if (highestBottomAmt > 0) {
                grandTotal = highestBottomAmt
            }
        }

        // Reconcile Subtotal if still 0.0
        if (subtotal <= 0.0 && grandTotal > 0) {
            subtotal = max(0.0, grandTotal - totalGst + discount)
        }

        return ParsedFinancials(
            subtotal = subtotal,
            cgst = cgst,
            sgst = sgst,
            igst = igst,
            totalGst = totalGst,
            discount = discount,
            grandTotal = grandTotal
        )
    }

    private fun parseAmountFromLine(line: String): Double {
        val matcher = CURRENCY_REGEX.matcher(line)
        var bestAmount = 0.0
        var bestPriority = 0

        while (matcher.find()) {
            val fullMatch = matcher.group(0) ?: ""
            val numStr = matcher.group(1)?.replace(",", "") ?: continue
            val num = numStr.toDoubleOrNull() ?: continue

            // Heuristic check: Filter out PIN codes (6 digits starting with 1-9) or years (2020..2026)
            if (num in 110000.0..999999.0 && !numStr.contains(".")) {
                continue // Likely Indian PIN code
            }
            if (num in 2020.0..2030.0 && !numStr.contains(".") && (line.contains("202") || line.contains("Date"))) {
                continue // Likely Year
            }

            val hasCurrencyPrefix = fullMatch.contains("Rs", ignoreCase = true) ||
                    fullMatch.contains("INR", ignoreCase = true) ||
                    fullMatch.contains("₹")
            val hasDecimals = numStr.contains(".")

            val priority = when {
                hasCurrencyPrefix -> 3
                hasDecimals -> 2
                else -> 1
            }

            if (priority >= bestPriority) {
                bestAmount = num
                bestPriority = priority
            }
        }
        return bestAmount
    }

    /**
     * Determines the appropriate Expense Category using keyword recognition.
     */
    private fun classifyCategory(merchant: String, fullText: String): ExpenseCategory {
        val combined = "$merchant $fullText".lowercase(Locale.ROOT)

        // 1. Fuel & Transport
        if (combined.contains("petrol") || combined.contains("diesel") || combined.contains("cng") ||
            combined.contains("fuel") || combined.contains("iocl") || combined.contains("bpcl") ||
            combined.contains("hpcl") || combined.contains("indian oil") || combined.contains("bharat petroleum") ||
            combined.contains("hindustan petroleum") || combined.contains("shell") || combined.contains("dispenser") ||
            combined.contains("pump") || combined.contains("nozzle") || combined.contains("density")
        ) {
            return ExpenseCategory.FUEL
        }

        // 2. Food & Dining
        if (combined.contains("restaurant") || combined.contains("cafe") || combined.contains("dining") ||
            combined.contains("food") || combined.contains("kitchen") || combined.contains("zomato") ||
            combined.contains("swiggy") || combined.contains("coffee") || combined.contains("pizza") ||
            combined.contains("burger") || combined.contains("biryani") || combined.contains("bakery") ||
            combined.contains("sweets") || combined.contains("haldiram") || combined.contains("mcdonald") ||
            combined.contains("starbucks") || combined.contains("dhaba") || combined.contains("hotel") ||
            combined.contains("bar") || combined.contains("thali") || combined.contains("tea") || combined.contains("chai")
        ) {
            return ExpenseCategory.FOOD
        }

        // 3. Medical & Health
        if (combined.contains("pharmacy") || combined.contains("chemist") || combined.contains("hospital") ||
            combined.contains("clinic") || combined.contains("diagnostic") || combined.contains("pathology") ||
            combined.contains("medplus") || combined.contains("apollo") || combined.contains("medicine") ||
            combined.contains("tablets") || combined.contains("capsules") || combined.contains("dr.") ||
            combined.contains("doctor") || combined.contains("healthcare") || combined.contains("pharmeasy") ||
            combined.contains("lab report") || combined.contains("rx")
        ) {
            return ExpenseCategory.MEDICAL
        }

        // 4. Groceries & Provisions
        if (combined.contains("supermarket") || combined.contains("grocery") || combined.contains("kirana") ||
            combined.contains("mart") || combined.contains("provisions") || combined.contains("milk") ||
            combined.contains("vegetables") || combined.contains("fruits") || combined.contains("d-mart") ||
            combined.contains("dmart") || combined.contains("reliance fresh") || combined.contains("blinkit") ||
            combined.contains("zepto") || combined.contains("bigbasket") || combined.contains("nature's basket")
        ) {
            return ExpenseCategory.GROCERIES
        }

        // 5. Office & Business Supplies
        if (combined.contains("stationery") || combined.contains("xerox") || combined.contains("print") ||
            combined.contains("hardware") || combined.contains("software") || combined.contains("courier") ||
            combined.contains("postage") || combined.contains("pen") || combined.contains("paper") ||
            combined.contains("stapler") || combined.contains("coworking") || combined.contains("office") ||
            combined.contains("aws") || combined.contains("google cloud") || combined.contains("domain") ||
            combined.contains("hosting")
        ) {
            return ExpenseCategory.OFFICE
        }

        // 6. Travel & Lodging
        if (combined.contains("flight") || combined.contains("airline") || combined.contains("train") ||
            combined.contains("irctc") || combined.contains("cab") || combined.contains("taxi") ||
            combined.contains("uber") || combined.contains("ola") || combined.contains("bus") ||
            combined.contains("toll") || combined.contains("fastag") || combined.contains("indigo") ||
            combined.contains("air india") || combined.contains("makemytrip") || combined.contains("stay")
        ) {
            return ExpenseCategory.TRAVEL
        }

        // 7. Utilities & Bills
        if (combined.contains("electricity") || combined.contains("water bill") || combined.contains("gas") ||
            combined.contains("internet") || combined.contains("wi-fi") || combined.contains("broadband") ||
            combined.contains("mobile recharge") || combined.contains("jio") || combined.contains("airtel") ||
            combined.contains("vodafone") || combined.contains("tata power") || combined.contains("adani") ||
            combined.contains("bescom") || combined.contains("msedcl") || combined.contains("torrent power")
        ) {
            return ExpenseCategory.UTILITIES
        }

        // 8. Retail Shopping
        if (combined.contains("clothing") || combined.contains("apparels") || combined.contains("footwear") ||
            combined.contains("electronics") || combined.contains("mall") || combined.contains("lifestyle") ||
            combined.contains("zara") || combined.contains("h&m") || combined.contains("trends") ||
            combined.contains("croma") || combined.contains("vijay sales") || combined.contains("reliance digital")
        ) {
            return ExpenseCategory.SHOPPING
        }

        // 9. Maintenance & Repairs
        if (combined.contains("repair") || combined.contains("service centre") || combined.contains("spare") ||
            combined.contains("maintenance") || combined.contains("workshop") || combined.contains("mechanic")
        ) {
            return ExpenseCategory.MAINTENANCE
        }

        return ExpenseCategory.OTHER
    }

    /**
     * Extracts Payment Mode (UPI, Cash, Card, NetBanking).
     */
    private fun extractPaymentMode(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") ||
            lower.contains("paytm") || lower.contains("bhim") || lower.contains("vpa") -> "UPI"
            lower.contains("card") || lower.contains("visa") || lower.contains("mastercard") ||
            lower.contains("rupay") || lower.contains("pos") || lower.contains("swipe") -> "Card"
            lower.contains("net banking") || lower.contains("neft") || lower.contains("rtgs") ||
            lower.contains("imps") || lower.contains("transfer") -> "Net Banking"
            lower.contains("cash") || lower.contains("tendered") || lower.contains("change") -> "Cash"
            else -> "Cash"
        }
    }
}
