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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
    onPickFrontClicked: () -> Unit = {},
    onPickBackClicked: () -> Unit = {},
    onCaptureFrontCamera: () -> Unit = {},
    onPickFrontGallery: () -> Unit = onPickFrontClicked,
    onClearFront: () -> Unit = {},
    onCaptureBackCamera: () -> Unit = {},
    onPickBackGallery: () -> Unit = onPickBackClicked,
    onClearBack: () -> Unit = {},
    onStitchClicked: (
        layoutMode: IdCardStitcher.IdCardLayoutMode,
        scaleMode: IdCardStitcher.CardScaleMode,
        paperSize: IdCardStitcher.PaperSize,
        applyAntiGlare: Boolean,
        drawCuttingGuide: Boolean,
        purposeAnnotation: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLayout by remember { mutableStateOf(IdCardStitcher.IdCardLayoutMode.VERTICAL_STACK) }
    var selectedScale by remember { mutableStateOf(IdCardStitcher.CardScaleMode.PHYSICAL_1TO1) }
    var selectedPaper by remember { mutableStateOf(IdCardStitcher.PaperSize.A4) }
    var autoMagicColor by remember { mutableStateOf(true) }
    var drawCuttingGuide by remember { mutableStateOf(true) }
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
                            text = "ID Card Front & Back Scanner",
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
                    text = "Automatically straightens, crops backgrounds, enhances ink contrast, and stitches both sides onto a standard A4 KYC sheet.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Front Slot
                EnhancedIdCardSlot(
                    label = "1. FRONT SIDE",
                    bitmap = frontBitmap,
                    onCameraCapture = onCaptureFrontCamera,
                    onGalleryPick = onPickFrontGallery,
                    onClear = onClearFront
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Back Slot
                EnhancedIdCardSlot(
                    label = "2. BACK SIDE",
                    bitmap = backBitmap,
                    onCameraCapture = onCaptureBackCamera,
                    onGalleryPick = onPickBackGallery,
                    onClear = onClearBack
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Card Print Scale Selector
                Text(
                    text = "Print Scale Standard",
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
                        selected = selectedScale == IdCardStitcher.CardScaleMode.PHYSICAL_1TO1,
                        onClick = { selectedScale = IdCardStitcher.CardScaleMode.PHYSICAL_1TO1 },
                        label = { Text("Exact 1:1 (85.6mm)", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedScale == IdCardStitcher.CardScaleMode.ENLARGED_KYC,
                        onClick = { selectedScale = IdCardStitcher.CardScaleMode.ENLARGED_KYC },
                        label = { Text("Enlarged KYC (160%)", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Layout Selector
                Text(
                    text = "Page Arrangement",
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
                        label = { Text("Top & Bottom", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedLayout == IdCardStitcher.IdCardLayoutMode.HORIZONTAL_SIDE_BY_SIDE,
                        onClick = { selectedLayout = IdCardStitcher.IdCardLayoutMode.HORIZONTAL_SIDE_BY_SIDE },
                        label = { Text("Side-by-Side", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pro Toggles: Magic Color Enhancement & Cutting Guide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = autoMagicColor,
                        onClick = { autoMagicColor = !autoMagicColor },
                        label = { Text(if (autoMagicColor) "✨ Magic Color" else "Original Color", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = drawCuttingGuide,
                        onClick = { drawCuttingGuide = !drawCuttingGuide },
                        label = { Text(if (drawCuttingGuide) "✂ Cutting Line On" else "No Cutting Line", fontSize = 11.sp) },
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
                        onStitchClicked(
                            selectedLayout,
                            selectedScale,
                            selectedPaper,
                            autoMagicColor,
                            drawCuttingGuide,
                            purposeText.trim()
                        )
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
                        text = if (canStitch) "Generate Official A4 KYC Sheet" else "Capture Both Sides First",
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
private fun EnhancedIdCardSlot(
    label: String,
    bitmap: Bitmap?,
    onCameraCapture: () -> Unit,
    onGalleryPick: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFF1E293B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (bitmap != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Captured",
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ready",
                        color = Color(0xFF059669),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (bitmap != null) {
            // Captured Preview Box with Controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.5.dp, Color(0xFF059669), RoundedCornerShape(12.dp))
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )

                // Action pills at bottom of thumbnail
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC0F172A))
                            .clickable { onCameraCapture() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retake", tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retake", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC0F172A))
                            .clickable { onGalleryPick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = "Change", tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xDDEF4444))
                            .clickable { onClear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        } else {
            // Unselected State: Clear dual-choice buttons (Camera & Gallery)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Camera Button
                    Button(
                        onClick = onCameraCapture,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Camera Scan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    // Gallery Button
                    OutlinedButton(
                        onClick = onGalleryPick,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("From Gallery", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                        }
                    }
                }
            }
        }
    }
}
