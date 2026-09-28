package com.docu.editor.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class AutoUpdateManager(
    private val context: Context,
    private val githubOwner: String = "ankit261194",
    private val githubRepo: String = "docu-edit-engine"
) {

    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = getInstalledVersionName()
        val apiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"

        try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "DocuEditEngine-AndroidApp")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateInfo(
                    hasUpdate = false,
                    currentVersion = currentVersion,
                    latestVersion = currentVersion,
                    releaseTitle = "",
                    changelog = "",
                    apkDownloadUrl = null,
                    apkFileName = null
                )
            }

            val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(response)

            val tagName = json.getString("tag_name").removePrefix("v").trim()
            val releaseTitle = json.optString("name", "New Update Available")
            val changelog = json.optString("body", "Bug fixes and performance enhancements.")

            val assetsArray = json.getJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            var apkSizeMb = 0f

            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    apkName = name
                    apkSizeMb = (asset.optLong("size", 0L) / (1024f * 1024f))
                    break
                }
            }

            val isNewer = isSemanticVersionNewer(currentVersion, tagName)

            UpdateInfo(
                hasUpdate = isNewer && apkUrl != null,
                currentVersion = currentVersion,
                latestVersion = tagName,
                releaseTitle = releaseTitle,
                changelog = changelog,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName ?: "DocuEdit-update-v$tagName.apk",
                apkSizeMb = apkSizeMb
            )
        } catch (_: Exception) {
            UpdateInfo(
                hasUpdate = false,
                currentVersion = currentVersion,
                latestVersion = currentVersion,
                releaseTitle = "",
                changelog = "",
                apkDownloadUrl = null,
                apkFileName = null
            )
        }
    }

    private fun getInstalledVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun isSemanticVersionNewer(current: String, candidate: String): Boolean {
        val currParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val candParts = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(currParts.size, candParts.size)

        for (i in 0 until maxLen) {
            val currVal = currParts.getOrElse(i) { 0 }
            val candVal = candParts.getOrElse(i) { 0 }
            if (candVal > currVal) return true
            if (candVal < currVal) return false
        }
        return false
    }
}
