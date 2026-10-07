package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.core.scanner.IdCardStitcher

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdCardDialog(
    frontBitmap: Bitmap?,
    backBitmap: Bitmap?,
    onPickFrontClicked: () -> Unit,
    onPickBackClicked: () -> Unit,
    onStitchClicked: (IdCardStitcher.IdCardLayoutMode, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLayout by remember { mutableStateOf(IdCardStitcher.IdCardLayoutMode.VERTICAL_STACK) }
    var purposeText by remember { mutableStateOf("") }

    val presetWatermarks = listOf(
        "FOR BANK KYC ONLY",
        "FOR SIM VERIFICATION",
        "FOR LOAN APPLICATION ONLY",
        "CONFIDENTIAL"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enterprise ID Duplex Scanner",
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Text(
                    text = "Capture Front & Back ID cards. Auto-crops to ISO/IEC 7810 ID-1 standard and aligns onto 300 DPI A4 sheet.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Front Slot
                IdCardSlot(
                    label = "1. Front Side",
                    bitmap = frontBitmap,
                    onClick = onPickFrontClicked
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Back Slot
                IdCardSlot(
                    label = "2. Back Side",
                    bitmap = backBitmap,
                    onClick = onPickBackClicked
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Layout Selector
                Text(
                    text = "A4 Page Layout",
                    color = Color(0xFF1E293B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedLayout == IdCardStitcher.IdCardLayoutMode.VERTICAL_STACK,
                        onClick = { selectedLayout = IdCardStitcher.IdCardLayoutMode.VERTICAL_STACK },
                        label = { Text("Top & Bottom (KYC)", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedLayout == IdCardStitcher.IdCardLayoutMode.HORIZONTAL_SIDE_BY_SIDE,
                        onClick = { selectedLayout = IdCardStitcher.IdCardLayoutMode.HORIZONTAL_SIDE_BY_SIDE },
                        label = { Text("Side-by-Side", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Purpose Watermark
                Text(
                    text = "Purpose Security Watermark (Optional)",
                    color = Color(0xFF1E293B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = purposeText,
                    onValueChange = { purposeText = it },
                    placeholder = { Text("e.g. FOR BANK KYC ONLY", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetWatermarks.forEach { preset ->
                        SuggestionChip(
                            onClick = { purposeText = preset },
                            label = { Text(preset, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val canStitch = frontBitmap != null && backBitmap != null
                Button(
                    onClick = {
                        onStitchClicked(selectedLayout, purposeText.trim())
                    },
                    enabled = canStitch,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        disabledContainerColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (canStitch) "Stitch to A4 Document" else "Capture Both Sides First",
                        color = if (canStitch) Color.White else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun IdCardSlot(
    label: String,
    bitmap: Bitmap?,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Color(0xFF1E293B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, if (bitmap != null) Color(0xFF059669) else Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Uploaded",
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to pick photo / scan",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
