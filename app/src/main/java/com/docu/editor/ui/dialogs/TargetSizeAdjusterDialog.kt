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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

enum class SizeAdjustMode {
    DECREASE,
    INCREASE
}

@Composable
fun TargetSizeAdjusterDialog(
    initialFormat: String = "JPG",
    onConfirmAdjust: (mode: SizeAdjustMode, targetKb: Int, format: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(SizeAdjustMode.DECREASE) }
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var targetKbText by remember { mutableStateOf("50") }

    val presets = listOf(
        Triple(20, "20 KB", "Signature / Thumb"),
        Triple(50, "50 KB", "SSC / UPSC Photo"),
        Triple(100, "100 KB", "Govt ID / Marksheet"),
        Triple(200, "200 KB", "Portal Standard")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEEF2FF),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Target Size Adjuster",
                                color = Color(0xFF0F172A),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pi7 Perceptual Compression & Padding",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector: Decrease vs Increase
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Decrease Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (mode == SizeAdjustMode.DECREASE) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (mode == SizeAdjustMode.DECREASE) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mode = SizeAdjustMode.DECREASE }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.South,
                                contentDescription = null,
                                tint = if (mode == SizeAdjustMode.DECREASE) Color(0xFF2563EB) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Decrease Size",
                                fontWeight = if (mode == SizeAdjustMode.DECREASE) FontWeight.Bold else FontWeight.Medium,
                                color = if (mode == SizeAdjustMode.DECREASE) Color(0xFF1E40AF) else Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Increase Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (mode == SizeAdjustMode.INCREASE) Color(0xFFFAF5FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (mode == SizeAdjustMode.INCREASE) Color(0xFF9333EA) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mode = SizeAdjustMode.INCREASE }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.North,
                                contentDescription = null,
                                tint = if (mode == SizeAdjustMode.INCREASE) Color(0xFF9333EA) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Increase Size",
                                fontWeight = if (mode == SizeAdjustMode.INCREASE) FontWeight.Bold else FontWeight.Medium,
                                color = if (mode == SizeAdjustMode.INCREASE) Color(0xFF6B21A8) else Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Explanation Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (mode == SizeAdjustMode.DECREASE) Color(0xFFF0FDF4) else Color(0xFFFDF4FF),
                    border = BorderStroke(1.dp, if (mode == SizeAdjustMode.DECREASE) Color(0xFF86EFAC) else Color(0xFFF0ABFC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (mode == SizeAdjustMode.DECREASE)
                            "✨ Strips junk EXIF metadata and optimizes quality loop. Visual sharpness preserved 100%."
                        else
                            "🏛️ Injects safe standard metadata padding bytes. Meets SSC/UPSC min-size rules with zero quality loss.",
                        fontSize = 11.sp,
                        color = if (mode == SizeAdjustMode.DECREASE) Color(0xFF166534) else Color(0xFF701A75),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presets
                Text(
                    text = "Quick Govt Exam Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (kb, label, _) ->
                        val isSelected = targetKbText == kb.toString()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF4F46E5) else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { targetKbText = kb.toString() }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF4338CA) else Color(0xFF475569),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom KB Input
                OutlinedTextField(
                    value = targetKbText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        if (filtered.length <= 5) targetKbText = filtered
                    },
                    label = { Text("Exact Target File Size (KB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4F46E5),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Format Picker: JPG vs PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Output Format", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("JPG", "PDF").forEach { fmt ->
                            val isSelected = selectedFormat == fmt
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0)),
                                modifier = Modifier.clickable { selectedFormat = fmt }
                            ) {
                                Text(
                                    text = fmt,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF4338CA) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val targetKbInt = targetKbText.toIntOrNull() ?: 0
                val isValid = targetKbInt in 5..10000

                Button(
                    onClick = {
                        if (isValid) {
                            onConfirmAdjust(mode, targetKbInt, selectedFormat)
                        }
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == SizeAdjustMode.DECREASE) Color(0xFF2563EB) else Color(0xFF9333EA)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mode == SizeAdjustMode.DECREASE)
                            "🎯 Compress to $targetKbInt KB ($selectedFormat)"
                        else
                            "🎯 Increase to $targetKbInt KB ($selectedFormat)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
