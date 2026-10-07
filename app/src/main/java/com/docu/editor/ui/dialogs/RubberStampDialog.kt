package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.core.signature.StampStudioEngine

@Composable
fun RubberStampDialog(
    onDismiss: () -> Unit,
    onApplyStamp: (StampStudioEngine.StampConfig) -> Unit
) {
    val presets = listOf("APPROVED", "VERIFIED", "CONFIDENTIAL", "OFFICIAL SEAL", "PAID", "REJECTED", "ORIGINAL")
    var centerText by remember { mutableStateOf("APPROVED") }
    var topText by remember { mutableStateOf("OFFICIAL VERIFICATION") }
    var bottomText by remember { mutableStateOf("AUTHORIZED SIGNATORY") }
    var selectedShape by remember { mutableStateOf(StampStudioEngine.StampShape.CIRCULAR_SEAL) }
    var selectedColor by remember { mutableStateOf(android.graphics.Color.rgb(220, 38, 38)) } // Red

    val inkColors = listOf(
        Pair("Red", android.graphics.Color.rgb(220, 38, 38)),
        Pair("Blue", android.graphics.Color.rgb(29, 78, 216)),
        Pair("Green", android.graphics.Color.rgb(5, 150, 105)),
        Pair("Violet", android.graphics.Color.rgb(124, 58, 237)),
        Pair("Black", android.graphics.Color.rgb(30, 41, 59))
    )

    val currentConfig = remember(centerText, topText, bottomText, selectedShape, selectedColor) {
        StampStudioEngine.StampConfig(
            shape = selectedShape,
            centerText = centerText,
            topText = topText,
            bottomText = bottomText,
            inkColor = selectedColor,
            distressLevel = 0.22f,
            sizePx = 420
        )
    }

    val previewBitmap = remember(currentConfig) {
        StampStudioEngine.createRubberStamp(currentConfig)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
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
                            imageVector = Icons.Default.LocalPolice,
                            contentDescription = null,
                            tint = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Digital Rubber Stamp Studio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Preview Canvas
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Stamp Preview",
                        modifier = Modifier.size(170.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stamp Presets Row
                Text(
                    text = "Quick Presets",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = centerText == preset,
                            onClick = { centerText = preset },
                            label = { Text(preset, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Shape Selection (Circular vs Rectangular)
                Text(
                    text = "Stamp Shape",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = selectedShape == StampStudioEngine.StampShape.CIRCULAR_SEAL,
                        onClick = { selectedShape = StampStudioEngine.StampShape.CIRCULAR_SEAL },
                        label = { Text("⭕ Circular Seal", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedShape == StampStudioEngine.StampShape.RECTANGULAR_BOX,
                        onClick = { selectedShape = StampStudioEngine.StampShape.RECTANGULAR_BOX },
                        label = { Text("▭ Box Stamp", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedShape == StampStudioEngine.StampShape.OVAL_BADGE,
                        onClick = { selectedShape = StampStudioEngine.StampShape.OVAL_BADGE },
                        label = { Text("⬭ Oval", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Ink Color Row
                Text(
                    text = "Official Ink Color",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    inkColors.forEach { (name, colorInt) ->
                        val isSelected = selectedColor == colorInt
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(colorInt), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorInt },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Texts
                OutlinedTextField(
                    value = centerText,
                    onValueChange = { centerText = it },
                    label = { Text("Center Text", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = topText,
                    onValueChange = { topText = it },
                    label = { Text("Top / Department Name", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bottomText,
                    onValueChange = { bottomText = it },
                    label = { Text("Bottom / Date / Subtext", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Button
                Button(
                    onClick = {
                        onApplyStamp(currentConfig)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("✓ Stamp Document Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
