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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.update.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onConfirmUpdate: (apkUrl: String?, localApkPath: String?, fileName: String) -> Unit
) {
    val isLocal = updateInfo.localApkPath != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = Color(0xFF2563EB)
            )
        },
        title = {
            Text(
                text = updateInfo.releaseTitle.ifBlank { "Update Available (v${updateInfo.latestVersion})" },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmUpdate(
                        updateInfo.apkDownloadUrl,
                        updateInfo.localApkPath,
                        updateInfo.apkFileName ?: "update.apk"
                    )
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
                Text(if (isLocal) "Install Update Now" else "Download & Install", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Later")
            }
        }
    )
}
