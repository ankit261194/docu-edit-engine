package com.docu.editor.ui.dialogs

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.domain.model.DocumentCanvasLayer

@Composable
fun EditTextLayerDialog(
    initialLayer: DocumentCanvasLayer? = null,
    onConfirm: (text: String, textColor: Int, bgColor: Int?, fontSize: Float, isBold: Boolean, isItalic: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialLayer?.text?.ifBlank { "Sample Heading" } ?: "Sample Heading") }
    var selectedTextColor by remember { mutableIntStateOf(initialLayer?.textColor ?: AndroidColor.BLACK) }
    var selectedBgColor by remember { mutableStateOf<Int?>(initialLayer?.backgroundColor) }
    var fontSize by remember { mutableFloatStateOf(initialLayer?.fontSize ?: 36f) }
    var isBold by remember { mutableStateOf(initialLayer?.isBold ?: true) }
    var isItalic by remember { mutableStateOf(initialLayer?.isItalic ?: false) }

    val colorPalette = listOf(
        AndroidColor.BLACK to "Black",
        AndroidColor.WHITE to "White",
        AndroidColor.rgb(220, 38, 38) to "Red",
        AndroidColor.rgb(37, 99, 235) to "Blue",
        AndroidColor.rgb(16, 185, 129) to "Green",
        AndroidColor.rgb(234, 179, 8) to "Yellow",
        AndroidColor.rgb(147, 51, 234) to "Purple"
    )

    val backgroundStyles = listOf(
        null to "None",
        AndroidColor.rgb(254, 240, 138) to "Highlight",
        AndroidColor.rgb(15, 23, 42) to "Dark Pill",
        AndroidColor.WHITE to "White Card",
        AndroidColor.rgb(224, 231, 255) to "Indigo Pill",
        AndroidColor.rgb(254, 226, 226) to "Red Pill"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialLayer == null) "✨ Canva Text Layer" else "✏️ Edit Text Layer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Preview Box
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = selectedBgColor?.let { Color(it) } ?: Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            border = if (selectedBgColor == AndroidColor.WHITE) BorderStroke(1.dp, Color(0xFFCBD5E1)) else null
                        ) {
                            Text(
                                text = text.ifBlank { "Type Here..." },
                                color = Color(selectedTextColor),
                                fontSize = (fontSize * 0.55f).coerceIn(14f, 26f).sp,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(
                                    horizontal = if (selectedBgColor != null) 16.dp else 4.dp,
                                    vertical = if (selectedBgColor != null) 8.dp else 2.dp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Text Input Field
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Layer Text") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Text Color Palette
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Text Color", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        colorPalette.forEach { (col, _) ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(
                                        width = if (selectedTextColor == col) 2.5.dp else 1.dp,
                                        color = if (selectedTextColor == col) Color(0xFF6366F1) else Color(0xFFCBD5E1),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedTextColor = col }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Background Pill Style Selector
                Text(
                    text = "Background Badge / Pill",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    backgroundStyles.take(4).forEach { (bg, label) ->
                        val isSelected = selectedBgColor == bg
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedBgColor = bg }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF4338CA) else Color(0xFF64748B),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Font Styling: Bold, Italic & Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isBold,
                            onClick = { isBold = !isBold },
                            label = { Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = isItalic,
                            onClick = { isItalic = !isItalic },
                            label = { Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(16.dp)) }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size: ${fontSize.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        Slider(
                            value = fontSize,
                            onValueChange = { fontSize = it },
                            valueRange = 18f..64f,
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm Button
                Button(
                    onClick = {
                        onConfirm(text, selectedTextColor, selectedBgColor, fontSize, isBold, isItalic)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (initialLayer == null) "Add Text to Canvas" else "Update Text Layer",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
