package com.docu.editor.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ApkDownloadInstaller(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1L
    private var receiverRegistered = false

    suspend fun downloadInAppStream(
        apkUrl: String,
        fileName: String,
        onProgress: (progress: Float, downloadedMb: Float, totalMb: Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val destFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
            fileName
        )
        if (destFile.exists()) destFile.delete()

        val url = URL(apkUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
        }

        val totalBytes = conn.contentLength.toFloat()
        val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f

        var downloadedBytes = 0L
        val buffer = ByteArray(16384)

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    val downloadedMb = downloadedBytes / (1024f * 1024f)
                    val progress = if (totalBytes > 0) (downloadedBytes / totalBytes).coerceIn(0f, 1f) else 0f
                    withContext(Dispatchers.Main) {
                        onProgress(progress, downloadedMb, totalMb)
                    }
                }
                output.flush()
            }
        }
        destFile
    }

    fun startDownload(apkUrl: String, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please grant permission to install updates", Toast.LENGTH_LONG).show()
                return
            }
        }

        val destinationFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("Downloading DocuEdit Update")
            setDescription("Fetching latest release: $fileName")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        registerDownloadCompletionReceiver(fileName)
        downloadId = downloadManager.enqueue(request)

        Toast.makeText(context, "Downloading update in background...", Toast.LENGTH_SHORT).show()
    }

    private fun registerDownloadCompletionReceiver(expectedFileName: String) {
        if (receiverRegistered) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                if (id == downloadId) {
                    val downloadedFile = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        expectedFileName
                    )
                    installApk(downloadedFile)

                    try {
                        context.unregisterReceiver(this)
                        receiverRegistered = false
                    } catch (_: Exception) {}
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        receiverRegistered = true
    }

    fun installApk(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "Downloaded update file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to launch installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
