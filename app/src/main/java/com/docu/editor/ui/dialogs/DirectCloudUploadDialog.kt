package com.docu.editor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 1-Tap Cloud Upload & Direct Share Hub.
 * Provides immediate upload to Google Drive, Web Cloud Sync link,
 * Direct WhatsApp/Email sharing, and Wireless Printing.
 */
@Composable
fun DirectCloudUploadDialog(
    onUploadGoogleDrive: () -> Unit,
    onWebCloudSync: () -> Unit,
    onShareSocial: () -> Unit,
    onDirectPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFF0F172A),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Upload & Cloud Share", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Save to cloud storage or generate shareable link", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Options
                UploadOptionCard(
                    title = "Upload to Google Drive",
                    subtitle = "Save directly into your Google Drive folders",
                    icon = Icons.Default.CloudUpload,
                    iconBg = Color(0xFF2563EB),
                    onClick = {
                        onDismiss()
                        onUploadGoogleDrive()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                UploadOptionCard(
                    title = "Generate Web Cloud Link",
                    subtitle = "Sync to web hosting & copy instant viewable link",
                    icon = Icons.Default.AddLink,
                    iconBg = Color(0xFF10B981),
                    onClick = {
                        onDismiss()
                        onWebCloudSync()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                UploadOptionCard(
                    title = "Share via WhatsApp / Gmail",
                    subtitle = "Send original high-resolution PDF or images",
                    icon = Icons.Default.Share,
                    iconBg = Color(0xFF8B5CF6),
                    onClick = {
                        onDismiss()
                        onShareSocial()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                UploadOptionCard(
                    title = "Wireless Print (A4 / Letter)",
                    subtitle = "Send directly to nearby Wi-Fi printer",
                    icon = Icons.Default.Print,
                    iconBg = Color(0xFFF59E0B),
                    onClick = {
                        onDismiss()
                        onDirectPrint()
                    }
                )
            }
        }
    }
}

@Composable
private fun UploadOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconBg, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
        }
    }
}
