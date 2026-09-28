package com.docu.editor.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.font.FontClassification
import com.docu.editor.core.font.FontMatcher
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.FontWeightEstimate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditBottomSheet(
    item: DetectedTextItem,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onApplyEdit: (
        newText: String,
        fontClassification: FontClassification,
        isBold: Boolean,
        sizeMultiplier: Float,
        colorRgb: Int,
        useCloudAi: Boolean
    ) -> Unit
) {
    // 100% Automatic Detection (Like CamScanner):
    val autoDetectedClassification = remember(item.id) {
        FontMatcher.classifyFromMetrics(item.text, item.typography, item.boundingBox)
    }
    val autoDetectedBold = remember(item.id) {
        item.typography.estimatedFontWeight == FontWeightEstimate.BOLD ||
        item.typography.estimatedFontWeight == FontWeightEstimate.EXTRA_BOLD ||
        item.typography.strokeWidthRatio >= 0.14f
    }

    var editedText by remember(item.id) { mutableStateOf(item.text) }
    var selectedFontType by remember(item.id) { mutableStateOf(autoDetectedClassification) }
    var isBold by remember(item.id) { mutableStateOf(autoDetectedBold) }
    var sizeMultiplier by remember(item.id) { mutableFloatStateOf(1.0f) }
    var selectedColorRgb by remember(item.id) { mutableIntStateOf(item.inkColorRgb) } // Default to document original ink

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Document Text",
                        color = Color(0xFF0F172A),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Auto-matched to original document typography",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF0F172A))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-Detection Badge (CamScanner style indicator)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-Matched Font: ${selectedFontType.displayName} • ${if (isBold) "Bold" else "Regular"}",
                        color = Color(0xFF1D4ED8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Text Input Field
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                label = { Text("Replacement Text", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold) },
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF0F172A),
                    unfocusedTextColor = Color(0xFF0F172A),
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Comparison Preview Box
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "LIVE VISUAL COMPARISON:",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Original Document:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Text(
                                text = item.text,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color(0xFFCBD5E1))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Preview:",
                                color = Color(0xFF2563EB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = editedText.ifEmpty { "Sample" },
                                color = Color(selectedColorRgb),
                                fontSize = (14 * sizeMultiplier).sp,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = when (selectedFontType.category) {
                                    "Classic Serif" -> FontFamily.Serif
                                    "Handwriting", "Signature" -> FontFamily.Cursive
                                    else -> if (selectedFontType == FontClassification.MONOSPACE || selectedFontType == FontClassification.INCONSOLATA) FontFamily.Monospace else if (selectedFontType == FontClassification.SERIF) FontFamily.Serif else FontFamily.SansSerif
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Typography Controls Card
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Family:", color = Color(0xFF475569), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${selectedFontType.category} • ${selectedFontType.displayName}",
                            color = Color(0xFF2563EB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FontClassification.entries.forEach { font ->
                            FilterChip(
                                selected = selectedFontType == font,
                                onClick = { selectedFontType = font },
                                label = { Text(font.displayName.substringBefore(" /"), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2563EB),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: Bold Toggle + Color Palette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bold Toggle Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isBold) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                            modifier = Modifier.clickable { isBold = !isBold }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatBold,
                                    contentDescription = "Bold",
                                    tint = if (isBold) Color.White else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBold) "BOLD (On)" else "Normal",
                                    color = if (isBold) Color.White else Color(0xFF475569),
                                    fontSize = 12.sp,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Ink Color Palette
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Ink:", color = Color(0xFF475569), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            // Document Original Ink
                            ColorChipLight(
                                color = item.inkColor,
                                label = "Orig",
                                isSelected = selectedColorRgb == item.inkColorRgb,
                                onClick = { selectedColorRgb = item.inkColorRgb }
                            )

                            // Natural Laser Charcoal
                            ColorChipLight(
                                color = Color(0xFF222428),
                                label = "Charcoal",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(34, 36, 40),
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(34, 36, 40) }
                            )

                            // Navy Blue (Pen/Stamp)
                            ColorChipLight(
                                color = Color(0xFF0F2B5C),
                                label = "Navy",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(15, 43, 92),
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(15, 43, 92) }
                            )

                            // Legal Blue
                            ColorChipLight(
                                color = Color(0xFF1E3A8A),
                                label = "Blue",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(30, 58, 138),
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(30, 58, 138) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 3: Fine-Tune Font Size Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatSize, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Size: ${(sizeMultiplier * 100).toInt()}%",
                                color = Color(0xFF0F172A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { sizeMultiplier = (sizeMultiplier - 0.05f).coerceAtLeast(0.60f) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color(0xFF1E293B), modifier = Modifier.size(16.dp))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .clickable { sizeMultiplier = 1.0f }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { sizeMultiplier = (sizeMultiplier + 0.05f).coerceAtMost(1.80f) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color(0xFF1E293B), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Slider(
                        value = sizeMultiplier,
                        onValueChange = { sizeMultiplier = it },
                        valueRange = 0.60f..1.80f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2563EB),
                            activeTrackColor = Color(0xFF2563EB),
                            inactiveTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Instant Local Apply (Default CamScanner Auto Mode)
                Button(
                    onClick = {
                        onApplyEdit(editedText, selectedFontType, isBold, sizeMultiplier, selectedColorRgb, false)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto Apply", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Gemini Pro Cloud AI Apply
                Button(
                    onClick = {
                        onApplyEdit(editedText, selectedFontType, isBold, sizeMultiplier, selectedColorRgb, true)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gemini Pro AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ColorChipLight(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF94A3B8),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
