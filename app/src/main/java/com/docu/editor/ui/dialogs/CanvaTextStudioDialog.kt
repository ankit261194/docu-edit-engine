package com.docu.editor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.core.scanner.CanvaTextStudioEngine
import com.docu.editor.domain.model.MagicWriteMode
import com.docu.editor.domain.model.TextEffectType
import kotlinx.coroutines.launch

@Composable
fun CanvaTextStudioDialog(
    initialText: String = "Headline Text",
    initialColorRgb: Int = android.graphics.Color.WHITE,
    initialFontSize: Float = 48f,
    initialLetterSpacingEm: Float = 0.05f,
    initialLineHeightMultiplier: Float = 1.2f,
    onAddTextLayer: (
        text: String,
        colorRgb: Int,
        fontSize: Float,
        isBold: Boolean,
        isItalic: Boolean,
        fontFamily: String,
        effect: TextEffectType,
        letterSpacingEm: Float,
        lineHeightMultiplier: Float
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColorRgb by remember { mutableIntStateOf(initialColorRgb) }
    var fontSize by remember { mutableFloatStateOf(initialFontSize) }
    var letterSpacingEm by remember { mutableFloatStateOf(initialLetterSpacingEm) }
    var lineHeightMultiplier by remember { mutableFloatStateOf(initialLineHeightMultiplier) }
    var isBold by remember { mutableStateOf(true) }
    var isItalic by remember { mutableStateOf(false) }
    var selectedFontFamily by remember { mutableStateOf("Sans-Serif") }
    var selectedEffect by remember { mutableStateOf(TextEffectType.NONE) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isRewriting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Typography & Effects", "Magic Write AI")

    val colorPalette = listOf(
        android.graphics.Color.WHITE,
        android.graphics.Color.BLACK,
        android.graphics.Color.rgb(139, 92, 246), // Purple
        android.graphics.Color.rgb(6, 182, 212),  // Cyan
        android.graphics.Color.rgb(244, 63, 94),  // Rose
        android.graphics.Color.rgb(16, 185, 129), // Emerald
        android.graphics.Color.rgb(245, 158, 11), // Gold
        android.graphics.Color.rgb(59, 130, 246)  // Blue
    )

    val fontFamilies = listOf("Sans-Serif", "Serif", "Monospace", "Cursive")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF13151F),
            border = BorderStroke(1.dp, Color(0xFF2A2D3D)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF6366F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Canva Pro Text Studio",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Effects, Typography & Magic Write",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Field
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text Content", color = Color(0xFF94A3B8)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color(0xFF2A2D3D),
                        focusedContainerColor = Color(0xFF1E2130),
                        unfocusedContainerColor = Color(0xFF1E2130)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E2130),
                    contentColor = Color(0xFF8B5CF6),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF8B5CF6)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == index) Color(0xFF8B5CF6) else Color(0xFF94A3B8)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // Typography & Effects Tab
                    Text(
                        text = "Text Effects",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextEffectType.entries.forEach { effect ->
                            FilterChip(
                                selected = selectedEffect == effect,
                                onClick = { selectedEffect = effect },
                                label = { Text(effect.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1E2130),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Font Family
                    Text(
                        text = "Font Family",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fontFamilies.forEach { font ->
                            FilterChip(
                                selected = selectedFontFamily == font,
                                onClick = { selectedFontFamily = font },
                                label = { Text(font, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF6366F1),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1E2130),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        Text("${fontSize.toInt()} px", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 20f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8B5CF6),
                            activeTrackColor = Color(0xFF8B5CF6),
                            inactiveTrackColor = Color(0xFF2A2D3D)
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Letter Spacing (Tracking) Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Letter Spacing (Tracking)", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        Text("${String.format("%.2f", letterSpacingEm)} em", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = letterSpacingEm,
                        onValueChange = { letterSpacingEm = it },
                        valueRange = -0.05f..0.30f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8B5CF6),
                            activeTrackColor = Color(0xFF8B5CF6),
                            inactiveTrackColor = Color(0xFF2A2D3D)
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Line Height (Leading) Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Line Height (Leading)", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        Text("${String.format("%.2f", lineHeightMultiplier)}x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Slider(
                        value = lineHeightMultiplier,
                        onValueChange = { lineHeightMultiplier = it },
                        valueRange = 0.8f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8B5CF6),
                            activeTrackColor = Color(0xFF8B5CF6),
                            inactiveTrackColor = Color(0xFF2A2D3D)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Colors & Formatting (Bold, Italic)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            colorPalette.forEach { cRgb ->
                                val isSelected = (cRgb == selectedColorRgb)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(cRgb))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF475569),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorRgb = cRgb }
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { isBold = !isBold }) {
                                Icon(
                                    imageVector = Icons.Default.FormatBold,
                                    contentDescription = "Bold",
                                    tint = if (isBold) Color(0xFF8B5CF6) else Color(0xFF64748B)
                                )
                            }
                            IconButton(onClick = { isItalic = !isItalic }) {
                                Icon(
                                    imageVector = Icons.Default.FormatItalic,
                                    contentDescription = "Italic",
                                    tint = if (isItalic) Color(0xFF8B5CF6) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                } else {
                    // Magic Write Tab
                    if (isRewriting) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MagicWriteMode.entries.forEach { mode ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                                    border = BorderStroke(1.dp, Color(0xFF2A2D3D)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                isRewriting = true
                                                val res = CanvaTextStudioEngine.magicWrite(text, mode)
                                                text = res
                                                isRewriting = false
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = mode.description,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Apply button
                Button(
                    onClick = {
                        onAddTextLayer(
                            text,
                            selectedColorRgb,
                            fontSize,
                            isBold,
                            isItalic,
                            selectedFontFamily,
                            selectedEffect,
                            letterSpacingEm,
                            lineHeightMultiplier
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Text(
                        text = "Add / Update Text Layer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
