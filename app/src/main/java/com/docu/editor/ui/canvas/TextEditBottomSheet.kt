package com.docu.editor.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.ocr.model.DetectedTextItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditBottomSheet(
    item: DetectedTextItem,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onApplyEdit: (
        newText: String,
        isBold: Boolean,
        sizeMultiplier: Float,
        colorRgb: Int,
        useCloudAi: Boolean
    ) -> Unit
) {
    var editedText by remember(item.id) { mutableStateOf(item.text) }
    var isBold by remember(item.id) { mutableStateOf(true) } // Default Bold for crisp document printing
    var sizeMultiplier by remember(item.id) { mutableFloatStateOf(1.0f) }
    var selectedColorRgb by remember(item.id) { mutableIntStateOf(0xFF000000.toInt()) } // Solid Black default

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color(0xFF1E293B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Document Typography Studio",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Original: \"${item.text.take(30)}\"",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Text Input Field
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                label = { Text("Replacement Text", color = Color(0xFF38BDF8)) },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Typography Controls Card
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Row 1: Bold Button + Color Palette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bold Toggle Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBold) Color(0xFF2563EB) else Color(0xFF1E293B),
                            modifier = Modifier
                                .clickable { isBold = !isBold }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatBold,
                                    contentDescription = "Bold",
                                    tint = if (isBold) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isBold) "BOLD (Active)" else "Normal",
                                    color = if (isBold) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Ink Color Choices
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Ink:", color = Color(0xFF94A3B8), fontSize = 12.sp)

                            // Pure Black
                            ColorChip(
                                color = Color.Black,
                                isSelected = selectedColorRgb == 0xFF000000.toInt(),
                                onClick = { selectedColorRgb = 0xFF000000.toInt() }
                            )

                            // Document Navy
                            ColorChip(
                                color = Color(0xFF0D47A1),
                                isSelected = selectedColorRgb == 0xFF0D47A1.toInt(),
                                onClick = { selectedColorRgb = 0xFF0D47A1.toInt() }
                            )

                            // Original Sampled
                            ColorChip(
                                color = item.inkColor,
                                isSelected = selectedColorRgb == item.inkColorRgb,
                                onClick = { selectedColorRgb = item.inkColorRgb }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Row 2: Font Size Slider & Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font Size: ${(sizeMultiplier * 100).toInt()}%",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { sizeMultiplier = (sizeMultiplier - 0.05f).coerceAtLeast(0.60f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                            }
                            IconButton(
                                onClick = { sizeMultiplier = (sizeMultiplier + 0.05f).coerceAtMost(2.0f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                            }
                        }
                    }

                    Slider(
                        value = sizeMultiplier,
                        onValueChange = { sizeMultiplier = it },
                        valueRange = 0.60f..1.80f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF2563EB),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons (Local Offline & Gemini Pro Cloud)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Instant Local Apply
                Button(
                    onClick = {
                        onApplyEdit(editedText, isBold, sizeMultiplier, selectedColorRgb, false)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Instant Apply", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                // Gemini Pro Cloud AI Apply
                Button(
                    onClick = {
                        onApplyEdit(editedText, isBold, sizeMultiplier, selectedColorRgb, true)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gemini Pro AI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorChip(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (color == Color.Black || color == Color(0xFF0D47A1)) Color.White else Color.Black,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
