package com.docu.editor.core.expense

/**
 * Standard Tax & Business Expense Categories recognized by Vyapar, Khatabook,
 * and Indian GST / Income Tax accounting standards.
 */
enum class ExpenseCategory(
    val displayName: String,
    val iconEmoji: String,
    val colorHex: Long,
    val isGstEligible: Boolean
) {
    FUEL("Fuel & Transport", "⛽", 0xFFE11D48, true),
    FOOD("Food & Dining", "🍔", 0xFFD97706, false),
    MEDICAL("Medical & Health", "💊", 0xFF0D9488, true),
    OFFICE("Office & Stationery", "💼", 0xFF2563EB, true),
    GROCERIES("Groceries & Provisions", "🛒", 0xFF16A34A, false),
    SHOPPING("Retail & Shopping", "🛍️", 0xFF9333EA, true),
    TRAVEL("Travel & Lodging", "✈️", 0xFF0284C7, true),
    UTILITIES("Utilities & Bills", "⚡", 0xFFEA580C, true),
    MAINTENANCE("Repairs & Maintenance", "🔧", 0xFF4B5563, true),
    OTHER("General Expenses", "🧾", 0xFF64748B, true);

    companion object {
        fun fromString(value: String?): ExpenseCategory {
            if (value.isNullOrBlank()) return OTHER
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.displayName.equals(value, ignoreCase = true) 
            } ?: OTHER
        }
    }
}
