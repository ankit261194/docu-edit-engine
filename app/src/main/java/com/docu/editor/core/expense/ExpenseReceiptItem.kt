package com.docu.editor.core.expense

import org.json.JSONObject
import java.util.UUID

/**
 * Structured Data Model representing an Audited Expense Receipt or Tax Invoice.
 * Compatible with Indian GST (CGST/SGST/IGST), Vyapar, and CA Tax Filing workflows.
 */
data class ExpenseReceiptItem(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String? = null,
    val documentTitle: String = "Scanned Receipt",
    val imagePath: String? = null,
    val merchantName: String = "Unknown Vendor",
    val invoiceNumber: String = "",
    val billDate: String = "",
    val billEpochMs: Long = System.currentTimeMillis(),
    val gstin: String = "",
    val subtotal: Double = 0.0,
    val cgst: Double = 0.0,
    val sgst: Double = 0.0,
    val igst: Double = 0.0,
    val totalGst: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val paymentMode: String = "Cash",
    val rawOcrSnippet: String = "",
    val verifiedByUser: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Formats total amount in Indian Rupee format (e.g. ₹1,450.00).
     */
    fun formattedGrandTotal(): String {
        return "₹%,.2f".format(grandTotal)
    }

    /**
     * Formats total GST amount in Indian Rupee format.
     */
    fun formattedTotalGst(): String {
        return "₹%,.2f".format(totalGst)
    }

    /**
     * Returns true if document has valid Indian GSTIN number.
     */
    val hasValidGstin: Boolean
        get() = gstin.isNotBlank() && gstin.matches(Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$"))

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("documentId", documentId ?: "")
        json.put("documentTitle", documentTitle)
        json.put("imagePath", imagePath ?: "")
        json.put("merchantName", merchantName)
        json.put("invoiceNumber", invoiceNumber)
        json.put("billDate", billDate)
        json.put("billEpochMs", billEpochMs)
        json.put("gstin", gstin)
        json.put("subtotal", subtotal)
        json.put("cgst", cgst)
        json.put("sgst", sgst)
        json.put("igst", igst)
        json.put("totalGst", totalGst)
        json.put("discount", discount)
        json.put("grandTotal", grandTotal)
        json.put("category", category.name)
        json.put("paymentMode", paymentMode)
        json.put("rawOcrSnippet", rawOcrSnippet)
        json.put("verifiedByUser", verifiedByUser)
        json.put("createdAt", createdAt)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ExpenseReceiptItem {
            val totalGstVal = if (json.has("totalGst")) {
                json.optDouble("totalGst", 0.0)
            } else {
                json.optDouble("cgst", 0.0) + json.optDouble("sgst", 0.0) + json.optDouble("igst", 0.0)
            }

            return ExpenseReceiptItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                documentId = json.optString("documentId").takeIf { it.isNotBlank() },
                documentTitle = json.optString("documentTitle", "Scanned Receipt"),
                imagePath = json.optString("imagePath").takeIf { it.isNotBlank() },
                merchantName = json.optString("merchantName", "Unknown Vendor"),
                invoiceNumber = json.optString("invoiceNumber", ""),
                billDate = json.optString("billDate", ""),
                billEpochMs = json.optLong("billEpochMs", System.currentTimeMillis()),
                gstin = json.optString("gstin", ""),
                subtotal = json.optDouble("subtotal", 0.0),
                cgst = json.optDouble("cgst", 0.0),
                sgst = json.optDouble("sgst", 0.0),
                igst = json.optDouble("igst", 0.0),
                totalGst = totalGstVal,
                discount = json.optDouble("discount", 0.0),
                grandTotal = json.optDouble("grandTotal", 0.0),
                category = ExpenseCategory.fromString(json.optString("category")),
                paymentMode = json.optString("paymentMode", "Cash"),
                rawOcrSnippet = json.optString("rawOcrSnippet", ""),
                verifiedByUser = json.optBoolean("verifiedByUser", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
