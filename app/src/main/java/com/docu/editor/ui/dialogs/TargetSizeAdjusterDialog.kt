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
    EXACT,
    INCREASE
}

@Composable
fun TargetSizeAdjusterDialog(
    initialFormat: String = "JPG",
    onConfirmAdjust: (mode: SizeAdjustMode, targetKb: Int, format: String, targetDpi: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(SizeAdjustMode.DECREASE) }
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var selectedDpi by remember { mutableIntStateOf(300) }
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
                                text = "Govt Portal Verified · Strict Upper Bound",
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

                // Mode Selector: Decrease vs Exact vs Increase
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Decrease Button (<=)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (mode == SizeAdjustMode.DECREASE) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (mode == SizeAdjustMode.DECREASE) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mode = SizeAdjustMode.DECREASE }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Max (≤)",
                                fontWeight = if (mode == SizeAdjustMode.DECREASE) FontWeight.Bold else FontWeight.Medium,
                                color = if (mode == SizeAdjustMode.DECREASE) Color(0xFF1E40AF) else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Portal Safe",
                                color = if (mode == SizeAdjustMode.DECREASE) Color(0xFF2563EB) else Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Exact Button (==)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (mode == SizeAdjustMode.EXACT) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (mode == SizeAdjustMode.EXACT) Color(0xFF059669) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mode = SizeAdjustMode.EXACT }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Exact (==)",
                                fontWeight = if (mode == SizeAdjustMode.EXACT) FontWeight.Bold else FontWeight.Medium,
                                color = if (mode == SizeAdjustMode.EXACT) Color(0xFF065F46) else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Exact Byte",
                                color = if (mode == SizeAdjustMode.EXACT) Color(0xFF059669) else Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Increase Button (>=)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (mode == SizeAdjustMode.INCREASE) Color(0xFFFAF5FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (mode == SizeAdjustMode.INCREASE) Color(0xFF9333EA) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mode = SizeAdjustMode.INCREASE }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Min (≥)",
                                fontWeight = if (mode == SizeAdjustMode.INCREASE) FontWeight.Bold else FontWeight.Medium,
                                color = if (mode == SizeAdjustMode.INCREASE) Color(0xFF6B21A8) else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Inflate",
                                color = if (mode == SizeAdjustMode.INCREASE) Color(0xFF9333EA) else Color(0xFF94A3B8),
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Explanation Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (mode) {
                        SizeAdjustMode.DECREASE -> Color(0xFFEFF6FF)
                        SizeAdjustMode.EXACT -> Color(0xFFECFDF5)
                        SizeAdjustMode.INCREASE -> Color(0xFFFAF5FF)
                    },
                    border = BorderStroke(
                        1.dp,
                        when (mode) {
                            SizeAdjustMode.DECREASE -> Color(0xFFBFDBFE)
                            SizeAdjustMode.EXACT -> Color(0xFFA7F3D0)
                            SizeAdjustMode.INCREASE -> Color(0xFFE9D5FF)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (mode) {
                            SizeAdjustMode.DECREASE -> "✅ Strict Max Limit: Guaranteed file size <= ${targetKbText} KB. Never exceeds target for government portal uploads."
                            SizeAdjustMode.EXACT -> "🎯 Exact Target KB: Iterative compression + standard JPEG padding to reach exactly ${targetKbText}.0 KB."
                            SizeAdjustMode.INCREASE -> "🏛️ Strict Min Limit: Inflates small images to meet government minimum KB requirements without quality loss."
                        },
                        fontSize = 11.sp,
                        color = when (mode) {
                            SizeAdjustMode.DECREASE -> Color(0xFF1E40AF)
                            SizeAdjustMode.EXACT -> Color(0xFF065F46)
                            SizeAdjustMode.INCREASE -> Color(0xFF6B21A8)
                        },
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

                // DPI Resolution Selector for Govt Portals (UPSC/SSC 200 DPI, 300 DPI)
                if (selectedFormat == "JPG") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("DPI Resolution Tag", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            Text("UPSC/SSC Portal Strict Check", fontSize = 9.sp, color = Color(0xFF64748B))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Pair(200, "200 DPI (Govt)"), Pair(300, "300 DPI (HD)")).forEach { (dpi, label) ->
                                val isSelected = selectedDpi == dpi
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.clickable { selectedDpi = dpi }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF1E40AF) else Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
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
                            onConfirmAdjust(mode, targetKbInt, selectedFormat, selectedDpi)
                        }
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (mode) {
                            SizeAdjustMode.DECREASE -> Color(0xFF2563EB)
                            SizeAdjustMode.EXACT -> Color(0xFF059669)
                            SizeAdjustMode.INCREASE -> Color(0xFF9333EA)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    val dpiLabel = if (selectedFormat == "JPG") " • ${selectedDpi}DPI" else ""
                    Text(
                        text = when (mode) {
                            SizeAdjustMode.DECREASE -> "🎯 Compress to ≤ $targetKbInt KB ($selectedFormat$dpiLabel)"
                            SizeAdjustMode.EXACT -> "🎯 Make Exact $targetKbInt.0 KB ($selectedFormat$dpiLabel)"
                            SizeAdjustMode.INCREASE -> "🎯 Increase to ≥ $targetKbInt KB ($selectedFormat$dpiLabel)"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
