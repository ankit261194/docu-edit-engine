package com.docu.editor.core.expense

import android.content.Context
import com.docu.editor.core.history.DocumentHistoryManager
import com.docu.editor.core.history.SavedDocumentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

data class CategoryStats(
    val category: ExpenseCategory,
    val totalAmount: Double,
    val count: Int,
    val percentage: Double
)

data class MonthlyExpenseSummary(
    val year: Int,
    val month: Int, // 1 to 12
    val totalSpend: Double,
    val totalGstCredit: Double,
    val totalBillsCount: Int,
    val categoryBreakdown: List<CategoryStats>,
    val paymentModeBreakdown: Map<String, Double>,
    val topMerchant: String,
    val averageBillAmount: Double
)

object ExpenseAuditManager {

    private const val EXPENSES_DIR = "expenses"
    private const val EXPENSES_FILE = "expense_ledger.json"

    private val cachedReceipts = mutableListOf<ExpenseReceiptItem>()
    private var isLoaded = false

    /**
     * Loads all audited receipts from on-device JSON ledger.
     */
    suspend fun getAuditedReceipts(context: Context, forceRefresh: Boolean = false): List<ExpenseReceiptItem> = withContext(Dispatchers.IO) {
        if (isLoaded && !forceRefresh) {
            return@withContext cachedReceipts.toList()
        }

        val dir = File(context.filesDir, EXPENSES_DIR)
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, EXPENSES_FILE)
        cachedReceipts.clear()

        if (file.exists()) {
            try {
                val jsonStr = file.readText()
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    cachedReceipts.add(ExpenseReceiptItem.fromJson(obj))
                }
            } catch (_: Exception) {
            }
        }

        // Sort by billEpochMs descending (latest first)
        cachedReceipts.sortByDescending { it.billEpochMs }
        isLoaded = true
        cachedReceipts.toList()
    }

    /**
     * Persists cached receipts back to disk.
     */
    private suspend fun persistToDisk(context: Context) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, EXPENSES_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, EXPENSES_FILE)

        val jsonArray = JSONArray()
        for (item in cachedReceipts) {
            jsonArray.put(item.toJson())
        }

        FileOutputStream(file).use { out ->
            out.write(jsonArray.toString(2).toByteArray())
        }
    }

    /**
     * Automatically scans all saved documents from DocumentHistoryManager,
     * extracts expense entities from receipts/invoices, and updates the ledger.
     */
    suspend fun syncWithDocumentHistory(context: Context): Int = withContext(Dispatchers.IO) {
        getAuditedReceipts(context) // Ensure loaded
        val savedDocs = DocumentHistoryManager.getSavedDocuments(context)
        var newlyAuditedCount = 0

        for (doc in savedDocs) {
            // Check if document is already audited
            val alreadyAudited = cachedReceipts.any { it.documentId == doc.id }
            if (alreadyAudited) continue

            // Determine if doc is likely an invoice, bill or receipt
            val isBillOrInvoice = isReceiptOrBill(doc)
            if (isBillOrInvoice) {
                val extracted = ReceiptEntityExtractor.extract(
                    ocrText = doc.extractedOcrText,
                    documentTitle = doc.title,
                    documentId = doc.id,
                    imagePath = doc.thumbnailPath.ifBlank { doc.filePath }
                )
                // If it extracted a reasonable receipt (or user explicitly labeled it as bill)
                if (extracted.grandTotal > 0.0 || extracted.hasValidGstin || doc.category.equals("Bills & Finance", ignoreCase = true)) {
                    cachedReceipts.add(extracted)
                    newlyAuditedCount++
                }
            }
        }

        if (newlyAuditedCount > 0) {
            cachedReceipts.sortByDescending { it.billEpochMs }
            persistToDisk(context)
        }

        newlyAuditedCount
    }

    private fun isReceiptOrBill(doc: SavedDocumentItem): Boolean {
        if (doc.category.equals("Bills & Finance", ignoreCase = true)) return true
        val lower = doc.extractedOcrText.lowercase()
        return lower.contains("tax invoice") || lower.contains("retail invoice") ||
               lower.contains("gstin") || lower.contains("cash memo") ||
               lower.contains("grand total") || lower.contains("subtotal") ||
               lower.contains("bill of supply") || lower.contains("petrol") ||
               lower.contains("restaurant") || lower.contains("pharmacy")
    }

    /**
     * Adds or updates a receipt item.
     */
    suspend fun saveReceipt(context: Context, receipt: ExpenseReceiptItem) = withContext(Dispatchers.IO) {
        getAuditedReceipts(context)
        val existingIndex = cachedReceipts.indexOfFirst { it.id == receipt.id }
        if (existingIndex >= 0) {
            cachedReceipts[existingIndex] = receipt
        } else {
            cachedReceipts.add(0, receipt)
        }
        cachedReceipts.sortByDescending { it.billEpochMs }
        persistToDisk(context)
    }

    /**
     * Deletes a receipt by ID.
     */
    suspend fun deleteReceipt(context: Context, receiptId: String) = withContext(Dispatchers.IO) {
        getAuditedReceipts(context)
        val removed = cachedReceipts.removeAll { it.id == receiptId }
        if (removed) {
            persistToDisk(context)
        }
    }

    /**
     * Retrieves all receipts belonging to a specific Year and Month (1..12).
     * If month == 0, returns all receipts for the whole year.
     */
    suspend fun getReceiptsForPeriod(context: Context, year: Int, month: Int = 0): List<ExpenseReceiptItem> {
        val all = getAuditedReceipts(context)
        val cal = Calendar.getInstance()

        return all.filter { item ->
            cal.timeInMillis = item.billEpochMs
            val itemYear = cal.get(Calendar.YEAR)
            val itemMonth = cal.get(Calendar.MONTH) + 1 // 1-based

            if (year > 0 && itemYear != year) return@filter false
            if (month in 1..12 && itemMonth != month) return@filter false
            true
        }
    }

    /**
     * Computes comprehensive monthly analytics for the Vyapar / Khatabook dashboard.
     */
    suspend fun getMonthlyAnalytics(context: Context, year: Int, month: Int): MonthlyExpenseSummary = withContext(Dispatchers.Default) {
        val receipts = getReceiptsForPeriod(context, year, month)

        var totalSpend = 0.0
        var totalGst = 0.0
        val categoryMap = mutableMapOf<ExpenseCategory, Double>()
        val categoryCountMap = mutableMapOf<ExpenseCategory, Int>()
        val paymentModes = mutableMapOf<String, Double>()
        val merchantFrequency = mutableMapOf<String, Int>()

        for (r in receipts) {
            totalSpend += r.grandTotal
            totalGst += r.totalGst

            categoryMap[r.category] = (categoryMap[r.category] ?: 0.0) + r.grandTotal
            categoryCountMap[r.category] = (categoryCountMap[r.category] ?: 0) + 1

            paymentModes[r.paymentMode] = (paymentModes[r.paymentMode] ?: 0.0) + r.grandTotal

            val cleanMerchant = r.merchantName.trim()
            if (cleanMerchant.isNotBlank()) {
                merchantFrequency[cleanMerchant] = (merchantFrequency[cleanMerchant] ?: 0) + 1
            }
        }

        val categoryStatsList = ExpenseCategory.entries.mapNotNull { cat ->
            val amt = categoryMap[cat] ?: 0.0
            val count = categoryCountMap[cat] ?: 0
            if (count > 0 || amt > 0.0) {
                val pct = if (totalSpend > 0.0) (amt / totalSpend) * 100.0 else 0.0
                CategoryStats(cat, amt, count, pct)
            } else null
        }.sortedByDescending { it.totalAmount }

        val topMerchant = merchantFrequency.maxByOrNull { it.value }?.key ?: "N/A"
        val avgAmt = if (receipts.isNotEmpty()) totalSpend / receipts.size else 0.0

        MonthlyExpenseSummary(
            year = year,
            month = month,
            totalSpend = totalSpend,
            totalGstCredit = totalGst,
            totalBillsCount = receipts.size,
            categoryBreakdown = categoryStatsList,
            paymentModeBreakdown = paymentModes,
            topMerchant = topMerchant,
            averageBillAmount = avgAmt
        )
    }

    /**
     * Returns list of distinct Year/Month pairs present in receipts for UI dropdown selector.
     */
    suspend fun getAvailableMonths(context: Context): List<Pair<Int, Int>> {
        val receipts = getAuditedReceipts(context)
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1

        val set = mutableSetOf<Pair<Int, Int>>()
        set.add(Pair(currentYear, currentMonth)) // always include current month

        for (r in receipts) {
            cal.timeInMillis = r.billEpochMs
            set.add(Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1))
        }

        return set.sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
    }
}
