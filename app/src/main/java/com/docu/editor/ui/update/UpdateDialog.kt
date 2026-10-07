package com.docu.editor.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.update.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onInstallLocalApk: (File) -> Unit,
    onDownloadInApp: suspend (apkUrl: String, fileName: String, onProgress: (Float, Float, Float) -> Unit) -> File?
) {
    val isLocal = updateInfo.localApkPath != null
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableFloatStateOf(0f) }
    var totalMb by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = Color(0xFF2563EB)
            )
        },
        title = {
            Text(
                text = if (isDownloading) "Downloading Update..." else updateInfo.releaseTitle.ifBlank { "Update Available (v${updateInfo.latestVersion})" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isDownloading) {
                    Text(
                        text = "Downloading update directly in-app. Please keep app open...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = Color(0xFF2563EB),
                        trackColor = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${(downloadProgress * 100).toInt()}% • ${String.format("%.1f", downloadedMb)} MB / ${String.format("%.1f", totalMb)} MB",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B)
                    )
                } else {
                    Text(
                        text = "Current: v${updateInfo.currentVersion} • New Version: v${updateInfo.latestVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isLocal) "Ready to install immediately:" else "What's New:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateInfo.changelog,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (updateInfo.apkSizeMb > 0f) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "File Size: ${String.format("%.1f", updateInfo.apkSizeMb)} MB",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "❌ $errorMessage",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = {
                        if (isLocal) {
                            onInstallLocalApk(File(updateInfo.localApkPath!!))
                            onDismiss()
                        } else {
                            val url = updateInfo.apkDownloadUrl
                            val name = updateInfo.apkFileName ?: "DocuEdit-v${updateInfo.latestVersion}.apk"
                            if (url != null) {
                                isDownloading = true
                                errorMessage = null
                                scope.launch {
                                    val downloadedFile = onDownloadInApp(url, name) { prog, dMb, tMb ->
                                        downloadProgress = prog
                                        downloadedMb = dMb
                                        totalMb = tMb
                                    }
                                    isDownloading = false
                                    if (downloadedFile != null && downloadedFile.exists()) {
                                        onInstallLocalApk(downloadedFile)
                                        onDismiss()
                                    } else {
                                        errorMessage = "Download failed. Please check connection and try again."
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isLocal) Icons.Default.InstallMobile else Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isLocal) "Install Update Now" else "In-App Update Now", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Later")
                }
            }
        }
    )
}
