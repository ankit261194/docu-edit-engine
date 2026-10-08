package com.docu.editor.core.history

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SavedDocumentItem(
    val id: String,
    val title: String,
    val filePath: String,
    val thumbnailPath: String,
    val pageCount: Int,
    val timestamp: Long,
    val fileSizeBytes: Long,
    val category: String = "All",
    val extractedOcrText: String = "",
    val isCloudSynced: Boolean = false,
    val cloudDocId: String = "",
    val cloudShareUrl: String = "",
    val lastSyncedTime: Long = 0L,
    val documentSubtype: String = "",
    val expiryDateString: String = "",
    val expiryEpochMs: Long = 0L,
    val hasCalendarReminder: Boolean = false
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))

    val formattedSize: String
        get() = when {
            fileSizeBytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", fileSizeBytes / (1024f * 1024f))
            fileSizeBytes >= 1024 -> "${fileSizeBytes / 1024} KB"
            else -> "$fileSizeBytes B"
        }

    val daysUntilExpiry: Int?
        get() = if (expiryEpochMs > 0L) {
            ((expiryEpochMs - System.currentTimeMillis()) / (1000L * 60L * 60L * 24L)).toInt()
        } else null
}

object DocumentHistoryManager {

    private const val INDEX_FILE_NAME = "saved_documents_index.json"
    private const val DOCS_DIR_NAME = "saved_documents"
    private const val THUMBS_DIR_NAME = "document_thumbnails"

    suspend fun getSavedDocuments(context: Context): List<SavedDocumentItem> = withContext(Dispatchers.IO) {
        val indexFile = File(context.filesDir, INDEX_FILE_NAME)
        if (!indexFile.exists()) return@withContext emptyList()

        try {
            val jsonStr = indexFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SavedDocumentItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val filePath = obj.getString("filePath")
                if (File(filePath).exists()) {
                    list.add(
                        SavedDocumentItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            filePath = filePath,
                            thumbnailPath = obj.getString("thumbnailPath"),
                            pageCount = obj.optInt("pageCount", 1),
                            timestamp = obj.getLong("timestamp"),
                            fileSizeBytes = obj.optLong("fileSizeBytes", File(filePath).length()),
                            category = obj.optString("category", "All"),
                            extractedOcrText = obj.optString("extractedOcrText", ""),
                            isCloudSynced = obj.optBoolean("isCloudSynced", false),
                            cloudDocId = obj.optString("cloudDocId", ""),
                            cloudShareUrl = obj.optString("cloudShareUrl", ""),
                            lastSyncedTime = obj.optLong("lastSyncedTime", 0L),
                            documentSubtype = obj.optString("documentSubtype", ""),
                            expiryDateString = obj.optString("expiryDateString", ""),
                            expiryEpochMs = obj.optLong("expiryEpochMs", 0L),
                            hasCalendarReminder = obj.optBoolean("hasCalendarReminder", false)
                        )
                    )
                }
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveDocument(
        context: Context,
        bitmap: Bitmap,
        title: String = "Doc_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}",
        pageCount: Int = 1,
        category: String = "All",
        extractedOcrText: String = ""
    ): SavedDocumentItem = withContext(Dispatchers.IO) {
        val docsDir = File(context.filesDir, DOCS_DIR_NAME).apply { mkdirs() }
        val thumbsDir = File(context.filesDir, THUMBS_DIR_NAME).apply { mkdirs() }

        val id = UUID.randomUUID().toString()
        val docFile = File(docsDir, "doc_${id}.jpg")
        FileOutputStream(docFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }

        // Save miniature thumbnail for instant loading without memory pressure
        val thumbFile = File(thumbsDir, "thumb_${id}.jpg")
        val thumbScale = 240f / bitmap.width.coerceAtLeast(1)
        val thumbH = (bitmap.height * thumbScale).toInt().coerceAtLeast(1)
        val thumbBmp = Bitmap.createScaledBitmap(bitmap, 240, thumbH, true)
        FileOutputStream(thumbFile).use { fos ->
            thumbBmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        }
        if (thumbBmp != bitmap) thumbBmp.recycle()

        // Automatic AI Classification & Expiry Extraction
        var effectiveCategory = category
        var effectiveSubtype = ""
        var effectiveExpiryDate = ""
        var effectiveExpiryEpoch = 0L

        if (extractedOcrText.isNotBlank()) {
            val classResult = com.docu.editor.core.classification.DocumentClassificationEngine.classify(extractedOcrText, title)
            if (effectiveCategory == "All" || effectiveCategory.isBlank()) {
                effectiveCategory = classResult.category.displayName
            }
            effectiveSubtype = classResult.subtype

            val expiryResult = com.docu.editor.core.classification.ExpiryWatchdogEngine.extractExpiry(extractedOcrText)
            if (expiryResult.hasExpiry) {
                effectiveExpiryDate = expiryResult.formattedDate
                effectiveExpiryEpoch = expiryResult.expiryEpochMs
            }

            if (effectiveCategory.equals("Bills & Finance", ignoreCase = true) || extractedOcrText.contains("Tax Invoice", ignoreCase = true) || extractedOcrText.contains("GSTIN", ignoreCase = true)) {
                try {
                    val expenseItem = com.docu.editor.core.expense.ReceiptEntityExtractor.extract(
                        ocrText = extractedOcrText,
                        documentTitle = title,
                        documentId = id,
                        imagePath = thumbFile.absolutePath
                    )
                    com.docu.editor.core.expense.ExpenseAuditManager.saveReceipt(context, expenseItem)
                } catch (_: Exception) {
                }
            }
        }

        val item = SavedDocumentItem(
            id = id,
            title = title,
            filePath = docFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            pageCount = pageCount,
            timestamp = System.currentTimeMillis(),
            fileSizeBytes = docFile.length(),
            category = effectiveCategory,
            extractedOcrText = extractedOcrText,
            documentSubtype = effectiveSubtype,
            expiryDateString = effectiveExpiryDate,
            expiryEpochMs = effectiveExpiryEpoch
        )

        val currentList = getSavedDocuments(context).toMutableList()
        currentList.removeAll { it.id == id }
        currentList.add(0, item)
        saveIndex(context, currentList)

        item
    }

    suspend fun saveExistingDocumentFile(
        context: Context,
        title: String,
        filePath: String,
        thumbnailBitmap: Bitmap?,
        pageCount: Int = 1,
        category: String = "All"
    ): SavedDocumentItem = withContext(Dispatchers.IO) {
        val thumbsDir = File(context.filesDir, THUMBS_DIR_NAME).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val thumbFile = File(thumbsDir, "thumb_${id}.jpg")

        if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
            val thumbScale = 240f / thumbnailBitmap.width.coerceAtLeast(1)
            val thumbH = (thumbnailBitmap.height * thumbScale).toInt().coerceAtLeast(1)
            val thumbBmp = Bitmap.createScaledBitmap(thumbnailBitmap, 240, thumbH, true)
            FileOutputStream(thumbFile).use { fos ->
                thumbBmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            if (thumbBmp != thumbnailBitmap) thumbBmp.recycle()
        }

        val item = SavedDocumentItem(
            id = id,
            title = title,
            filePath = filePath,
            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else "",
            pageCount = pageCount,
            timestamp = System.currentTimeMillis(),
            fileSizeBytes = File(filePath).length(),
            category = category,
            extractedOcrText = ""
        )

        val currentList = getSavedDocuments(context).toMutableList()
        currentList.removeAll { it.id == id }
        currentList.add(0, item)
        saveIndex(context, currentList)

        item
    }

    suspend fun updateDocument(
        context: Context,
        id: String,
        bitmap: Bitmap,
        pageCount: Int = 1,
        category: String? = null,
        extractedOcrText: String? = null
    ): SavedDocumentItem? = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context).toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == id }
        if (existingIndex == -1) return@withContext null

        val existing = currentList[existingIndex]
        val docFile = File(existing.filePath)
        FileOutputStream(docFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
        }

        val thumbFile = File(existing.thumbnailPath)
        val thumbScale = 240f / bitmap.width.coerceAtLeast(1)
        val thumbH = (bitmap.height * thumbScale).toInt().coerceAtLeast(1)
        val thumbBmp = Bitmap.createScaledBitmap(bitmap, 240, thumbH, true)
        FileOutputStream(thumbFile).use { fos ->
            thumbBmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        }
        if (thumbBmp != bitmap) thumbBmp.recycle()

        val updated = existing.copy(
            timestamp = System.currentTimeMillis(),
            fileSizeBytes = docFile.length(),
            pageCount = pageCount,
            category = category ?: existing.category,
            extractedOcrText = extractedOcrText ?: existing.extractedOcrText
        )
        currentList.removeAt(existingIndex)
        currentList.add(0, updated)
        saveIndex(context, currentList)
        updated
    }

    suspend fun updateDocumentCategory(context: Context, id: String, newCategory: String, newSubtype: String? = null) = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context).toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(
                category = newCategory,
                documentSubtype = newSubtype ?: existing.documentSubtype
            )
            saveIndex(context, currentList)
        }
    }

    suspend fun updateDocumentExpiry(
        context: Context,
        id: String,
        expiryDateString: String,
        expiryEpochMs: Long,
        hasCalendarReminder: Boolean
    ) = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context).toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(
                expiryDateString = expiryDateString,
                expiryEpochMs = expiryEpochMs,
                hasCalendarReminder = hasCalendarReminder
            )
            saveIndex(context, currentList)
        }
    }

    suspend fun renameDocument(context: Context, id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context).toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(title = newTitle)
            saveIndex(context, currentList)
        }
    }

    suspend fun markDocumentCloudSynced(
        context: Context,
        id: String,
        cloudDocId: String,
        cloudShareUrl: String
    ) = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context).toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(
                isCloudSynced = true,
                cloudDocId = cloudDocId,
                cloudShareUrl = cloudShareUrl,
                lastSyncedTime = System.currentTimeMillis()
            )
            saveIndex(context, currentList)
        }
    }

    suspend fun deleteDocument(context: Context, id: String) = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context).toMutableList()
        val toRemove = currentList.find { it.id == id }
        if (toRemove != null) {
            File(toRemove.filePath).delete()
            File(toRemove.thumbnailPath).delete()
            currentList.remove(toRemove)
            saveIndex(context, currentList)
        }
    }

    suspend fun deleteMultipleDocuments(context: Context, ids: Set<String>) = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context).toMutableList()
        val toRemove = currentList.filter { it.id in ids }
        for (item in toRemove) {
            File(item.filePath).delete()
            File(item.thumbnailPath).delete()
        }
        currentList.removeAll(toRemove)
        saveIndex(context, currentList)
    }

    suspend fun clearAllDocuments(context: Context) = withContext(Dispatchers.IO) {
        val currentList = getSavedDocuments(context)
        for (item in currentList) {
            File(item.filePath).delete()
            File(item.thumbnailPath).delete()
        }
        File(context.filesDir, INDEX_FILE_NAME).delete()
    }

    private fun saveIndex(context: Context, list: List<SavedDocumentItem>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("filePath", item.filePath)
                put("thumbnailPath", item.thumbnailPath)
                put("pageCount", item.pageCount)
                put("timestamp", item.timestamp)
                put("fileSizeBytes", item.fileSizeBytes)
                put("category", item.category)
                put("extractedOcrText", item.extractedOcrText)
                put("isCloudSynced", item.isCloudSynced)
                put("cloudDocId", item.cloudDocId)
                put("cloudShareUrl", item.cloudShareUrl)
                put("lastSyncedTime", item.lastSyncedTime)
                put("documentSubtype", item.documentSubtype)
                put("expiryDateString", item.expiryDateString)
                put("expiryEpochMs", item.expiryEpochMs)
                put("hasCalendarReminder", item.hasCalendarReminder)
            }
            array.put(obj)
        }
        File(context.filesDir, INDEX_FILE_NAME).writeText(array.toString())
    }
}
