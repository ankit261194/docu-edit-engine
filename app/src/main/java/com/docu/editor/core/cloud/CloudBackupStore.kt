package com.docu.editor.core.cloud

import android.content.Context
import android.content.SharedPreferences
import com.docu.editor.ui.dialogs.CloudSyncResult
import org.json.JSONArray
import org.json.JSONObject

data class CloudBackupItem(
    val docId: String,
    val title: String,
    val shareUrl: String,
    val downloadUrl: String,
    val qrUrl: String,
    val fileSizeFormatted: String,
    val pagesCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages persistent local history of documents backed up to
 * the user's hosting server (shribalajikripadham.online).
 */
object CloudBackupStore {
    private const val PREFS_NAME = "docu_cloud_backups"
    private const val KEY_BACKUPS = "backup_list"
    private const val KEY_DEVICE_ID = "docu_device_uuid"
    private const val KEY_CUSTOM_SYNC_KEY = "docu_custom_sync_key"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDeviceId(context: Context): String {
        val prefs = getPrefs(context)
        var devId = prefs.getString(KEY_DEVICE_ID, null)
        if (devId.isNullOrBlank()) {
            devId = "dev_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_DEVICE_ID, devId).apply()
        }
        return devId
    }

    fun getSyncKey(context: Context): String {
        val prefs = getPrefs(context)
        val customKey = prefs.getString(KEY_CUSTOM_SYNC_KEY, null)
        if (!customKey.isNullOrBlank()) {
            return customKey
        }
        return getDeviceId(context)
    }

    fun setSyncKey(context: Context, key: String): Boolean {
        val cleanKey = key.trim().replace(Regex("[^a-zA-Z0-9_-]"), "")
        if (cleanKey.length < 4) return false
        getPrefs(context).edit().putString(KEY_CUSTOM_SYNC_KEY, cleanKey).apply()
        return true
    }

    fun clearCustomSyncKey(context: Context) {
        getPrefs(context).edit().remove(KEY_CUSTOM_SYNC_KEY).apply()
    }

    fun saveBackup(context: Context, result: CloudSyncResult) {
        val existing = getBackups(context).toMutableList()
        existing.removeAll { it.docId == result.docId }
        val newItem = CloudBackupItem(
            docId = result.docId,
            title = result.title,
            shareUrl = result.shareUrl,
            downloadUrl = result.downloadUrl,
            qrUrl = result.qrUrl,
            fileSizeFormatted = result.fileSizeFormatted,
            pagesCount = result.pagesCount,
            timestamp = System.currentTimeMillis()
        )
        existing.add(0, newItem)

        val array = JSONArray()
        existing.take(100).forEach { item ->
            val obj = JSONObject().apply {
                put("docId", item.docId)
                put("title", item.title)
                put("shareUrl", item.shareUrl)
                put("downloadUrl", item.downloadUrl)
                put("qrUrl", item.qrUrl)
                put("fileSizeFormatted", item.fileSizeFormatted)
                put("pagesCount", item.pagesCount)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_BACKUPS, array.toString()).apply()
    }

    fun saveBackupItem(context: Context, item: CloudBackupItem) {
        val existing = getBackups(context).toMutableList()
        existing.removeAll { it.docId == item.docId }
        existing.add(0, item)

        val array = JSONArray()
        existing.take(100).forEach { itm ->
            val obj = JSONObject().apply {
                put("docId", itm.docId)
                put("title", itm.title)
                put("shareUrl", itm.shareUrl)
                put("downloadUrl", itm.downloadUrl)
                put("qrUrl", itm.qrUrl)
                put("fileSizeFormatted", itm.fileSizeFormatted)
                put("pagesCount", itm.pagesCount)
                put("timestamp", itm.timestamp)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_BACKUPS, array.toString()).apply()
    }

    fun getBackups(context: Context): List<CloudBackupItem> {
        val jsonStr = getPrefs(context).getString(KEY_BACKUPS, null) ?: return emptyList()
        val list = mutableListOf<CloudBackupItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CloudBackupItem(
                        docId = obj.optString("docId"),
                        title = obj.optString("title", "Document"),
                        shareUrl = obj.optString("shareUrl"),
                        downloadUrl = obj.optString("downloadUrl"),
                        qrUrl = obj.optString("qrUrl"),
                        fileSizeFormatted = obj.optString("fileSizeFormatted", "100 KB"),
                        pagesCount = obj.optInt("pagesCount", 1),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteBackup(context: Context, docId: String) {
        val existing = getBackups(context).filterNot { it.docId == docId }
        val array = JSONArray()
        existing.forEach { item ->
            val obj = JSONObject().apply {
                put("docId", item.docId)
                put("title", item.title)
                put("shareUrl", item.shareUrl)
                put("downloadUrl", item.downloadUrl)
                put("qrUrl", item.qrUrl)
                put("fileSizeFormatted", item.fileSizeFormatted)
                put("pagesCount", item.pagesCount)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_BACKUPS, array.toString()).apply()
    }
}
