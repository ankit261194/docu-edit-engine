package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.docu.editor.core.scanner.BackgroundRemovalEngine.RemovalMode

/**
 * Enterprise Canva Pro 1-Click Background Removal Dialog.
 * Offers transparent cutout generation, signature ink extraction, and studio-white paper normalization.
 */
@Composable
fun BackgroundRemovalDialog(
    onApplyRemovalMode: (RemovalMode) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A), // Dark Studio Slate
            border = BorderStroke(1.dp, Color(0xFF334155)),
            shadowElevation = 24.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Canva Pro BG Remover",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "1-Click AI & OpenCV Background Cutout",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Option 1: Object / Photo Transparent Cutout
                BgRemoverCard(
                    icon = Icons.Default.Layers,
                    title = "Transparent PNG Cutout",
                    subtitle = "Cuts out subject/object and makes background 100% transparent PNG.",
                    badge = "CANVA PRO",
                    badgeColor = Color(0xFF8B5CF6),
                    onClick = {
                        onApplyRemovalMode(RemovalMode.OBJECT_CUTOUT)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Signature & Rubber Stamp Cutout
                BgRemoverCard(
                    icon = Icons.Default.Draw,
                    title = "Extract Transparent Signature",
                    subtitle = "Removes paper background from signatures or stamps for overlaying.",
                    badge = "STAMP & SIGN",
                    badgeColor = Color(0xFF059669),
                    onClick = {
                        onApplyRemovalMode(RemovalMode.SIGNATURE_OR_STAMP)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 3: Studio Pure-White Background
                BgRemoverCard(
                    icon = Icons.Default.LightMode,
                    title = "Studio Pure White (#FFFFFF)",
                    subtitle = "Normalizes yellow or shadowy document pages into studio-white paper.",
                    badge = "DOC CLEAN",
                    badgeColor = Color(0xFF0284C7),
                    onClick = {
                        onApplyRemovalMode(RemovalMode.STUDIO_WHITE_PAPER)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BgRemoverCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.25f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = badgeColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
