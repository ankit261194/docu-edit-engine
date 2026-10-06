package com.docu.editor.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AutoUpdateManager(
    private val context: Context,
    private val githubOwner: String = "ankit261194",
    private val githubRepo: String = "docu-edit-engine"
) {

    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = getInstalledVersionName()
        val currentVersionCode = getInstalledVersionCode()

        // 1. Instant Local Check: Scan Downloads directory for newer APK
        val localUpdate = checkLocalDownloadsForUpdate(currentVersion, currentVersionCode)
        if (localUpdate != null && localUpdate.hasUpdate) {
            return@withContext localUpdate
        }

        // 2. Remote GitHub Release Check
        val apiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
        try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "DocuEditEngine-AndroidApp")
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)

                val tagName = json.getString("tag_name").removePrefix("v").trim()
                val releaseTitle = json.optString("name", "New Update Available")
                val changelog = json.optString("body", "Bug fixes, Pi7 smart compression and performance enhancements.")

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
                if (isNewer && apkUrl != null) {
                    return@withContext UpdateInfo(
                        hasUpdate = true,
                        currentVersion = currentVersion,
                        latestVersion = tagName,
                        releaseTitle = releaseTitle,
                        changelog = changelog,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName ?: "DocuEdit-update-v$tagName.apk",
                        apkSizeMb = apkSizeMb
                    )
                }
            }
        } catch (_: Exception) {}

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

    /**
     * Instantly inspects local Downloads folder for DocuEdit APKs transferred or downloaded.
     */
    fun checkLocalDownloadsForUpdate(currentVersion: String, currentVersionCode: Long): UpdateInfo? {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists() || !downloadsDir.isDirectory) return null

            val apkFiles = downloadsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".apk", ignoreCase = true) &&
                        (file.name.contains("DocuEdit", ignoreCase = true) || file.name.contains("app-", ignoreCase = true))
            } ?: return null

            var newestApk: File? = null
            var newestCode = currentVersionCode
            var newestName = currentVersion

            for (apk in apkFiles) {
                try {
                    val archiveInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
                    if (archiveInfo != null) {
                        val apkCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            archiveInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            archiveInfo.versionCode.toLong()
                        }
                        val apkVersionName = archiveInfo.versionName ?: "1.0.0"

                        if (apkCode > newestCode || isSemanticVersionNewer(newestName, apkVersionName)) {
                            newestCode = apkCode
                            newestName = apkVersionName
                            newestApk = apk
                        }
                    }
                } catch (_: Exception) {}
            }

            if (newestApk != null) {
                val sizeMb = newestApk.length() / (1024f * 1024f)
                return UpdateInfo(
                    hasUpdate = true,
                    currentVersion = currentVersion,
                    latestVersion = newestName,
                    releaseTitle = "🎉 New Update Ready (v$newestName)",
                    changelog = "A new update file (${newestApk.name}) was detected in your Downloads folder. Tap below to install it immediately!",
                    apkDownloadUrl = null,
                    apkFileName = newestApk.name,
                    apkSizeMb = sizeMb,
                    localApkPath = newestApk.absolutePath
                )
            }
        } catch (_: Exception) {}
        return null
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

    private fun getInstalledVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
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
