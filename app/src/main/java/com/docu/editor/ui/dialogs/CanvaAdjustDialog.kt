package com.docu.editor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.window.Dialog
import com.docu.editor.domain.model.CanvaStyleMatchPreset

@Composable
fun CanvaAdjustDialog(
    initialBrightness: Float = 0f,
    initialContrast: Float = 1.0f,
    initialSaturation: Float = 1.0f,
    initialWarmth: Float = 0f,
    initialTint: Float = 0f,
    initialClarity: Float = 0f,
    initialVignette: Float = 0f,
    initialBlur: Float = 0f,
    initialPreset: CanvaStyleMatchPreset = CanvaStyleMatchPreset.NONE,
    onApplyAdjustments: (
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float,
        tint: Float,
        clarity: Float,
        vignette: Float,
        blur: Float,
        preset: CanvaStyleMatchPreset
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var brightness by remember { mutableFloatStateOf(initialBrightness) }
    var contrast by remember { mutableFloatStateOf(initialContrast) }
    var saturation by remember { mutableFloatStateOf(initialSaturation) }
    var warmth by remember { mutableFloatStateOf(initialWarmth) }
    var tint by remember { mutableFloatStateOf(initialTint) }
    var clarity by remember { mutableFloatStateOf(initialClarity) }
    var vignette by remember { mutableFloatStateOf(initialVignette) }
    var blur by remember { mutableFloatStateOf(initialBlur) }
    var selectedPreset by remember { mutableStateOf(initialPreset) }

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
                                .background(Color(0xFF06B6D4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Adjust & Style Match",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pro Color Grading & Fine Tuning",
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

                // Style Match Presets Row
                Text(
                    text = "Style Match Presets",
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
                    CanvaStyleMatchPreset.entries.forEach { p ->
                        FilterChip(
                            selected = selectedPreset == p,
                            onClick = { selectedPreset = p },
                            label = { Text(p.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF06B6D4),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E2130),
                                labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Adjustment Sliders
                AdjustSliderItem("Brightness", brightness, -60f..60f) { brightness = it }
                AdjustSliderItem("Contrast", contrast, 0.6f..1.8f) { contrast = it }
                AdjustSliderItem("Saturation", saturation, 0f..2.2f) { saturation = it }
                AdjustSliderItem("Warmth", warmth, -40f..40f) { warmth = it }
                AdjustSliderItem("Tint", tint, -40f..40f) { tint = it }
                AdjustSliderItem("Clarity / Sharpen", clarity, 0f..100f) { clarity = it }
                AdjustSliderItem("Vignette", vignette, 0f..100f) { vignette = it }
                AdjustSliderItem("Gaussian Blur", blur, 0f..40f) { blur = it }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        onApplyAdjustments(
                            brightness, contrast, saturation,
                            warmth, tint, clarity, vignette, blur,
                            selectedPreset
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                ) {
                    Text(
                        text = "Apply Adjustments",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustSliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFF94A3B8), fontSize = 12.sp)
            Text(String.format("%.1f", value), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF06B6D4),
                activeTrackColor = Color(0xFF06B6D4),
                inactiveTrackColor = Color(0xFF2A2D3D)
            )
        )
    }
}
