package com.docu.editor.ui.canvas

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.docu.editor.domain.model.DocumentCanvasLayer

/**
 * Canva-Style Contextual Layer Bottom Dock.
 * Displays horizontal real-time controls when any shape, text, or photo layer is selected on canvas.
 * Changes reflect live on the document canvas in real time.
 */
@Composable
fun ContextualLayerBottomBar(
    layer: DocumentCanvasLayer,
    onUpdateShapeStyle: (fillColor: Int?, strokeColor: Int, strokeWidth: Float, cornerRadius: Float, alpha: Float) -> Unit,
    onUpdateTextStyle: (text: String, textColor: Int, bgColor: Int?, fontSize: Float, isBold: Boolean, isItalic: Boolean, fontFamily: String, alpha: Float) -> Unit,
    onUpdateLayerAlpha: (Float) -> Unit,
    onDuplicateLayer: () -> Unit,
    onDeleteLayer: () -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onReplaceImageClicked: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickColors = listOf(
        Pair("Transparent", null),
        Pair("Red", android.graphics.Color.rgb(239, 68, 68)),
        Pair("Blue", android.graphics.Color.rgb(37, 99, 235)),
        Pair("Green", android.graphics.Color.rgb(16, 185, 129)),
        Pair("Amber", android.graphics.Color.rgb(245, 158, 11)),
        Pair("Purple", android.graphics.Color.rgb(139, 92, 246)),
        Pair("Pink", android.graphics.Color.rgb(236, 72, 153)),
        Pair("Cyan", android.graphics.Color.rgb(6, 182, 212)),
        Pair("Black", android.graphics.Color.BLACK),
        Pair("White", android.graphics.Color.WHITE)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = Color(0xFA0F172A),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header Row: Layer Title, Quick Actions & Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                layer.isShapeLayer -> Icons.Default.Palette
                                layer.isTextLayer -> Icons.Default.TextFields
                                else -> Icons.Default.Image
                            },
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = layer.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDuplicateLayer, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onBringToFront, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Layers, contentDescription = "Front", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onFlipH, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Flip, contentDescription = "Flip", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteLayer, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDeselect() },
                        color = Color(0xFF2563EB)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tool Options Strip
            if (layer.isShapeLayer) {
                // Shape Live Controls
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Fill Color Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Fill:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        quickColors.forEach { (name, colorVal) ->
                            val isSelected = (layer.shapeFillColor == colorVal)
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (colorVal != null) Color(colorVal) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onUpdateShapeStyle(
                                            colorVal,
                                            layer.shapeStrokeColor,
                                            layer.shapeStrokeWidth,
                                            layer.cornerRadius,
                                            layer.alpha
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (colorVal == null) {
                                    Text("∅", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Border / Stroke Width & Corner Radius
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Border: ${layer.shapeStrokeWidth.toInt()}dp", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(72.dp))
                        Slider(
                            value = layer.shapeStrokeWidth,
                            onValueChange = { newW ->
                                onUpdateShapeStyle(
                                    layer.shapeFillColor,
                                    layer.shapeStrokeColor,
                                    newW,
                                    layer.cornerRadius,
                                    layer.alpha
                                )
                            },
                            valueRange = 0f..28f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Corner: ${layer.cornerRadius.toInt()}", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(64.dp))
                        Slider(
                            value = layer.cornerRadius,
                            onValueChange = { newR ->
                                onUpdateShapeStyle(
                                    layer.shapeFillColor,
                                    layer.shapeStrokeColor,
                                    layer.shapeStrokeWidth,
                                    newR,
                                    layer.alpha
                                )
                            },
                            valueRange = 0f..50f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF818CF8), activeTrackColor = Color(0xFF818CF8))
                        )
                    }
                }
            } else if (layer.isTextLayer) {
                // Text Live Controls
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Text Color:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        quickColors.filter { it.second != null }.forEach { (name, colorVal) ->
                            val isSelected = (layer.textColor == colorVal)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal!!))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onUpdateTextStyle(
                                            layer.text,
                                            colorVal,
                                            layer.backgroundColor,
                                            layer.fontSize,
                                            layer.isBold,
                                            layer.isItalic,
                                            layer.fontFamily,
                                            layer.alpha
                                        )
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Size: ${layer.fontSize.toInt()}sp", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(68.dp))
                        Slider(
                            value = layer.fontSize,
                            onValueChange = { newSz ->
                                onUpdateTextStyle(
                                    layer.text,
                                    layer.textColor,
                                    layer.backgroundColor,
                                    newSz,
                                    layer.isBold,
                                    layer.isItalic,
                                    layer.fontFamily,
                                    layer.alpha
                                )
                            },
                            valueRange = 14f..96f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Bold & Italic Toggles
                        FilterChip(
                            selected = layer.isBold,
                            onClick = {
                                onUpdateTextStyle(
                                    layer.text,
                                    layer.textColor,
                                    layer.backgroundColor,
                                    layer.fontSize,
                                    !layer.isBold,
                                    layer.isItalic,
                                    layer.fontFamily,
                                    layer.alpha
                                )
                            },
                            label = { Text("B", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF2563EB), selectedLabelColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        FilterChip(
                            selected = layer.isItalic,
                            onClick = {
                                onUpdateTextStyle(
                                    layer.text,
                                    layer.textColor,
                                    layer.backgroundColor,
                                    layer.fontSize,
                                    layer.isBold,
                                    !layer.isItalic,
                                    layer.fontFamily,
                                    layer.alpha
                                )
                            },
                            label = { Text("I", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF2563EB), selectedLabelColor = Color.White)
                        )
                    }
                }
            } else {
                // Photo / Image Layer Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onReplaceImageClicked() },
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Replace Photo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.weight(1f).padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Opacity: ${(layer.alpha * 100).toInt()}%", color = Color(0xFF94A3B8), fontSize = 11.sp, modifier = Modifier.width(76.dp))
                        Slider(
                            value = layer.alpha,
                            onValueChange = { onUpdateLayerAlpha(it) },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                        )
                    }
                }
            }
        }
    }
}
