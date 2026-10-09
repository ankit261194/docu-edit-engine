package com.docu.editor.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
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

    private val tag = "AutoUpdateManager"
    private val prefs by lazy { context.getSharedPreferences("docu_update_cache", Context.MODE_PRIVATE) }

    suspend fun checkForUpdates(forceCheck: Boolean = false): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = getInstalledVersionName()
        val currentVersionCode = getInstalledVersionCode()

        val lastCheckTime = prefs.getLong("last_check_timestamp", 0L)
        val now = System.currentTimeMillis()
        val sixHoursMs = 6 * 3600 * 1000L

        // 0. 6-Hour Cache Validation to protect GitHub API rate-limits
        if (!forceCheck && (now - lastCheckTime) < sixHoursMs) {
            val cachedVer = prefs.getString("cached_latest_version", null)
            if (cachedVer != null) {
                val hasActualUpdate = prefs.getBoolean("cached_has_update", false) && isSemanticVersionNewer(currentVersion, cachedVer)
                if (!hasActualUpdate) {
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
                val cachedUrl = prefs.getString("cached_apk_url", null)
                return@withContext UpdateInfo(
                    hasUpdate = true,
                    currentVersion = currentVersion,
                    latestVersion = cachedVer,
                    releaseTitle = prefs.getString("cached_title", "") ?: "",
                    changelog = prefs.getString("cached_changelog", "") ?: "",
                    apkDownloadUrl = cachedUrl,
                    apkFileName = prefs.getString("cached_filename", "DocuEdit-v$cachedVer-arm64.apk")
                )
            }
        }

        // 1. Instant Local Check: Scan Downloads directory for newer APK transferred or downloaded
        val localUpdate = checkLocalDownloadsForUpdate(currentVersion, currentVersionCode)
        if (localUpdate != null && localUpdate.hasUpdate) {
            Log.i(tag, "Update found locally in Downloads: ${localUpdate.latestVersion}")
            saveToCache(localUpdate)
            return@withContext localUpdate
        }

        // 2. High-Availability CDN Check: version.json (Zero rate limit, fast global CDN)
        val cdnUpdate = checkViaVersionJson(currentVersion)
        if (cdnUpdate != null && cdnUpdate.hasUpdate) {
            Log.i(tag, "Update found via CDN version.json: ${cdnUpdate.latestVersion}")
            saveToCache(cdnUpdate)
            return@withContext cdnUpdate
        }

        // 3. GitHub Web Redirect Check: /releases/latest -> /releases/tag/v... (No API rate limits)
        val webUpdate = checkViaWebRedirect(currentVersion)
        if (webUpdate != null && webUpdate.hasUpdate) {
            Log.i(tag, "Update found via Web Redirect: ${webUpdate.latestVersion}")
            saveToCache(webUpdate)
            return@withContext webUpdate
        }

        // 4. Remote GitHub REST API Check (Standard API fallback)
        val apiUpdate = checkViaGithubApi(currentVersion)
        if (apiUpdate != null && apiUpdate.hasUpdate) {
            Log.i(tag, "Update found via GitHub REST API: ${apiUpdate.latestVersion}")
            saveToCache(apiUpdate)
            return@withContext apiUpdate
        }

        val fallback = UpdateInfo(
            hasUpdate = false,
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            releaseTitle = "",
            changelog = "",
            apkDownloadUrl = null,
            apkFileName = null
        )
        saveToCache(fallback)
        fallback
    }

    private fun saveToCache(info: UpdateInfo) {
        prefs.edit()
            .putLong("last_check_timestamp", System.currentTimeMillis())
            .putBoolean("cached_has_update", info.hasUpdate)
            .putString("cached_latest_version", info.latestVersion)
            .putString("cached_title", info.releaseTitle)
            .putString("cached_changelog", info.changelog)
            .putString("cached_apk_url", info.apkDownloadUrl)
            .putString("cached_filename", info.apkFileName)
            .apply()
    }

    /**
     * Tier 2: Fetches version.json directly via raw GitHub content / jsdelivr CDN.
     * Bypasses GitHub API rate limits completely.
     */
    private fun checkViaVersionJson(currentVersion: String): UpdateInfo? {
        val cdnUrls = listOf(
            "https://raw.githubusercontent.com/$githubOwner/$githubRepo/main/version.json?t=${System.currentTimeMillis()}",
            "https://cdn.jsdelivr.net/gh/$githubOwner/$githubRepo@main/version.json"
        )

        for (endpoint in cdnUrls) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "DocuEditEngine-AndroidApp")
                    connectTimeout = 7000
                    readTimeout = 7000
                    useCaches = false
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val json = JSONObject(response)
                    val tagName = json.getString("versionName").removePrefix("v").trim()
                    val title = json.optString("releaseTitle", "New Update Available (v$tagName)")
                    val changelog = json.optString("changelog", "Bug fixes and performance improvements.")
                    val apkUrl = json.optString("apkArm64Url", json.optString("apkUrl", ""))
                    val fileName = json.optString("apkFileName", "DocuEdit-v$tagName-arm64.apk")
                    val sizeMb = json.optDouble("apkSizeMb", 60.5).toFloat()

                    if (isSemanticVersionNewer(currentVersion, tagName) && apkUrl.isNotBlank()) {
                        return UpdateInfo(
                            hasUpdate = true,
                            currentVersion = currentVersion,
                            latestVersion = tagName,
                            releaseTitle = title,
                            changelog = changelog,
                            apkDownloadUrl = apkUrl,
                            apkFileName = fileName,
                            apkSizeMb = sizeMb
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "CDN version.json check failed on $endpoint: ${e.message}")
            }
        }
        return null
    }

    /**
     * Tier 3: Follows standard HTTP 302 redirect on the releases/latest web page.
     * Web requests are NOT subject to GitHub API 60 req/hr rate limits.
     */
    private fun checkViaWebRedirect(currentVersion: String): UpdateInfo? {
        try {
            val url = URL("https://github.com/$githubOwner/$githubRepo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "DocuEditEngine-AndroidApp")
                connectTimeout = 7000
                readTimeout = 7000
            }

            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrBlank() && location.contains("/tag/")) {
                    val tagName = location.substringAfterLast("/tag/").removePrefix("v").trim()
                    if (isSemanticVersionNewer(currentVersion, tagName)) {
                        val apkUrl = "https://github.com/$githubOwner/$githubRepo/releases/download/v$tagName/DocuEdit-v$tagName-arm64.apk"
                        return UpdateInfo(
                            hasUpdate = true,
                            currentVersion = currentVersion,
                            latestVersion = tagName,
                            releaseTitle = "DocuEdit Engine v$tagName Pro Update",
                            changelog = "A new official version (v$tagName) is available with major performance and feature upgrades.",
                            apkDownloadUrl = apkUrl,
                            apkFileName = "DocuEdit-v$tagName-arm64.apk",
                            apkSizeMb = 60.5f
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Web redirect check failed: ${e.message}")
        }
        return null
    }

    /**
     * Tier 4: GitHub REST API endpoint check.
     */
    private fun checkViaGithubApi(currentVersion: String): UpdateInfo? {
        val apiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
        try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "DocuEditEngine-AndroidApp")
                connectTimeout = 7000
                readTimeout = 7000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)

                val tagName = json.getString("tag_name").removePrefix("v").trim()
                val releaseTitle = json.optString("name", "New Update Available (v$tagName)")
                val changelog = json.optString("body", "Bug fixes and performance enhancements.")

                val assetsArray = json.getJSONArray("assets")
                var apkUrl: String? = null
                var apkName: String? = null
                var apkSizeMb = 60.5f

                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.getString("browser_download_url")
                        apkName = name
                        apkSizeMb = (asset.optLong("size", 0L) / (1024f * 1024f))
                        // Prefer arm64 if available
                        if (name.contains("arm64", ignoreCase = true)) {
                            break
                        }
                    }
                }

                val isNewer = isSemanticVersionNewer(currentVersion, tagName)
                if (isNewer && apkUrl != null) {
                    return UpdateInfo(
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
        } catch (e: Exception) {
            Log.w(tag, "GitHub REST API check failed: ${e.message}")
        }
        return null
    }

    /**
     * Instantly inspects local Downloads folder for DocuEdit APKs transferred or downloaded.
     */
    fun checkLocalDownloadsForUpdate(currentVersion: String, currentVersionCode: Long): UpdateInfo? {
        try {
            val candidateDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                File(context.cacheDir, "updates")
            ).filter { it.exists() && it.isDirectory }

            var newestApk: File? = null
            var newestCode = currentVersionCode
            var newestName = currentVersion

            for (dir in candidateDirs) {
                val apkFiles = dir.listFiles { file ->
                    file.isFile && file.name.endsWith(".apk", ignoreCase = true) &&
                            (file.name.contains("DocuEdit", ignoreCase = true) || file.name.contains("app-", ignoreCase = true))
                } ?: continue

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

                            // Must be strictly newer than installed version
                            val isNewer = apkCode > currentVersionCode || isSemanticVersionNewer(currentVersion, apkVersionName)
                            if (isNewer && (apkCode > newestCode || isSemanticVersionNewer(newestName, apkVersionName))) {
                                newestCode = apkCode
                                newestName = apkVersionName
                                newestApk = apk
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (newestApk != null && (newestCode > currentVersionCode || isSemanticVersionNewer(currentVersion, newestName))) {
                val sizeMb = newestApk.length() / (1024f * 1024f)
                return UpdateInfo(
                    hasUpdate = true,
                    currentVersion = currentVersion,
                    latestVersion = newestName,
                    releaseTitle = "🎉 New Update Ready (v$newestName)",
                    changelog = "A new update file (${newestApk.name}) was detected on your device. Tap below to install it immediately!",
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

    fun isSemanticVersionNewer(current: String, candidate: String): Boolean {
        val currClean = current.trim().removePrefix("v").removePrefix("V")
        val candClean = candidate.trim().removePrefix("v").removePrefix("V")
        val currParts = currClean.split(".").map { it.toIntOrNull() ?: 0 }
        val candParts = candClean.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(currParts.size, candParts.size)

        for (i in 0 until maxLen) {
            val currVal = currParts.getOrElse(i) { 0 }
            val candVal = candParts.getOrElse(i) { 0 }
            if (candVal > currVal) return true
            if (candVal < currVal) return false
        }
        return false
    }

    /**
     * Downloads APK update silently in the background and posts a 1-click install notification.
     */
    suspend fun downloadUpdateSilently(info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        val downloadUrl = info.apkDownloadUrl ?: return@withContext null
        try {
            val fileName = info.apkFileName ?: "DocuEdit-v${info.latestVersion}-arm64.apk"
            val targetDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(targetDir, fileName)

            if (targetFile.exists() && targetFile.length() > 5 * 1024 * 1024L) {
                com.docu.editor.core.util.DocuNotificationHelper.showUpdateReadyNotification(
                    context, targetFile, info.latestVersion
                )
                return@withContext targetFile
            }

            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val tempFile = File(targetDir, "$fileName.tmp")
                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.renameTo(targetFile)

                com.docu.editor.core.util.DocuNotificationHelper.showUpdateReadyNotification(
                    context, targetFile, info.latestVersion
                )
                return@withContext targetFile
            }
        } catch (_: Exception) {}
        null
    }
}
