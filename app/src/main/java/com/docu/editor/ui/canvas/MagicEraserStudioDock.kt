package com.docu.editor.ui.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.domain.model.MagicEraserTargetMode

/**
 * Enterprise Floating Studio Dock for CamScanner Magic Eraser 2.0.
 * Features:
 * - Multi-Stroke Mask Accumulation & Batch Inpainting
 * - Target Presets: All Objects, Rubber Stamps & Ink (Strict Text Protection), Fold Creases
 * - Instant vs Batch Selection Toggle
 * - Live Brush Radius Slider (10px - 80px)
 * - Gemini Vision Generative Fill Toggle
 */
@Composable
fun MagicEraserStudioDock(
    brushRadius: Float,
    onBrushRadiusChanged: (Float) -> Unit,
    targetMode: MagicEraserTargetMode,
    onTargetModeChanged: (MagicEraserTargetMode) -> Unit,
    isBatchMode: Boolean,
    onBatchModeChanged: (Boolean) -> Unit,
    strokeCount: Int,
    onUndoLastStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onEraseBatch: () -> Unit,
    isCloudAiEnabled: Boolean,
    onToggleCloudAi: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF20F172A),
        border = BorderStroke(1.5.dp, Color(0xFFD946EF).copy(alpha = 0.55f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFD946EF), Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Magic Eraser",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Magic Eraser 2.0",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFD946EF).copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO AI",
                            color = Color(0xFFF472B6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Cloud AI Generative Fill Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isCloudAiEnabled) Color(0xFF8B5CF6).copy(alpha = 0.25f)
                                else Color(0xFF1E293B)
                            )
                            .clickable { onToggleCloudAi() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Cloud Fill",
                            tint = if (isCloudAiEnabled) Color(0xFFA78BFA) else Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCloudAiEnabled) "Cloud AI On" else "Cloud Off",
                            color = if (isCloudAiEnabled) Color(0xFFA78BFA) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Target Preset Mode Selector Chips
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MagicEraserTargetPresetChip(
                    title = "🎯 All Objects",
                    subtitle = "Stains & Doodles",
                    isSelected = targetMode == MagicEraserTargetMode.ALL_OBJECTS,
                    onClick = { onTargetModeChanged(MagicEraserTargetMode.ALL_OBJECTS) }
                )
                MagicEraserTargetPresetChip(
                    title = "🔴 Stamps & Ink",
                    subtitle = "Text-Safe Protection",
                    isSelected = targetMode == MagicEraserTargetMode.STAMPS_AND_INK,
                    onClick = { onTargetModeChanged(MagicEraserTargetMode.STAMPS_AND_INK) }
                )
                MagicEraserTargetPresetChip(
                    title = "📄 Fold & Crease",
                    subtitle = "Shadow Removal",
                    isSelected = targetMode == MagicEraserTargetMode.CREASE_SHADOWS,
                    onClick = { onTargetModeChanged(MagicEraserTargetMode.CREASE_SHADOWS) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Brush Size Slider & Mode Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${brushRadius.toInt()}px",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(36.dp)
                )

                Slider(
                    value = brushRadius,
                    onValueChange = onBrushRadiusChanged,
                    valueRange = 10f..80f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD946EF),
                        activeTrackColor = Color(0xFFD946EF),
                        inactiveTrackColor = Color(0xFF334155)
                    )
                )

                // Instant vs Batch Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBatchMode) Color(0xFFD946EF).copy(alpha = 0.22f) else Color(0xFF1E293B))
                        .border(
                            1.dp,
                            if (isBatchMode) Color(0xFFD946EF).copy(alpha = 0.6f) else Color(0xFF334155),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onBatchModeChanged(!isBatchMode) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isBatchMode) "Multi-Mark" else "Instant",
                        color = if (isBatchMode) Color(0xFFF472B6) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. Batch Actions Row (When in Multi-Mark mode)
            if (isBatchMode) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (strokeCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Undo Last Stroke
                            IconButton(
                                onClick = onUndoLastStroke,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo Last Stroke",
                                    tint = Color(0xFFE2E8F0),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Clear All
                            IconButton(
                                onClick = onClearStrokes,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear All",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = "$strokeCount marked",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        // Prominent Inpaint Gradient Button
                        Button(
                            onClick = onEraseBatch,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .height(36.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFD946EF), Color(0xFF8B5CF6))
                                    ),
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✨ Erase Marked ($strokeCount)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (targetMode) {
                                    MagicEraserTargetMode.ALL_OBJECTS -> "👆 Brush over any unwanted object or blemish to mark"
                                    MagicEraserTargetMode.STAMPS_AND_INK -> "👆 Brush over colored stamps (Printed text stays safe!)"
                                    MagicEraserTargetMode.CREASE_SHADOWS -> "👆 Trace along book spine fold or paper crease lines"
                                },
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

@Composable
private fun MagicEraserTargetPresetChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) Color(0xFFD946EF).copy(alpha = 0.22f)
                else Color(0xFF1E293B)
            )
            .border(
                1.2.dp,
                if (isSelected) Color(0xFFD946EF) else Color(0xFF334155),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = if (isSelected) Color(0xFFF472B6) else Color(0xFF64748B),
                fontSize = 9.5.sp
            )
        }
    }
}
