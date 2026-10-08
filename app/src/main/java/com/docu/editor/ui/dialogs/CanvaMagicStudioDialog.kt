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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class MagicToolItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val onClick: () -> Unit
)

@Composable
fun CanvaMagicStudioDialog(
    onTriggerMagicEraser: () -> Unit,
    onTriggerBgRemover: () -> Unit,
    onTriggerMagicGrab: () -> Unit,
    onTriggerGrabText: () -> Unit,
    onTriggerFaceRetouch: () -> Unit,
    onTriggerAutofocus: () -> Unit,
    onTriggerUpscale: () -> Unit,
    onTriggerMagicExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    val tools = listOf(
        MagicToolItem(
            title = "Magic Eraser 2.0 (AI Inpainting)",
            subtitle = "Erase stamps, pen marks, and crease folds with strict printed text protection",
            icon = Icons.Default.AutoFixHigh,
            iconColor = Color(0xFFD946EF),
            onClick = { onTriggerMagicEraser(); onDismiss() }
        ),
        MagicToolItem(
            title = "Background Remover",
            subtitle = "1-click transparent PNG cutout, signature extractor & studio white",
            icon = Icons.Default.ContentCut,
            iconColor = Color(0xFFEC4899),
            onClick = { onTriggerBgRemover(); onDismiss() }
        ),
        MagicToolItem(
            title = "Grab Text (OCR to Layers)",
            subtitle = "Extracts all document text into movable, editable vector layers",
            icon = Icons.Default.TextFields,
            iconColor = Color(0xFF06B6D4),
            onClick = { onTriggerGrabText(); onDismiss() }
        ),
        MagicToolItem(
            title = "Magic Grab (Subject to Layer)",
            subtitle = "Select an object to lift into a separate layer and fill background",
            icon = Icons.Default.PanTool,
            iconColor = Color(0xFF8B5CF6),
            onClick = { onTriggerMagicGrab(); onDismiss() }
        ),
        MagicToolItem(
            title = "Face Retouch & Beauty",
            subtitle = "Smooths skin blemishes and enhances natural portraits",
            icon = Icons.Default.Face,
            iconColor = Color(0xFF10B981),
            onClick = { onTriggerFaceRetouch(); onDismiss() }
        ),
        MagicToolItem(
            title = "Autofocus & Bokeh Blur",
            subtitle = "Simulates DSLR wide aperture lens depth-of-field background blur",
            icon = Icons.Default.BlurCircular,
            iconColor = Color(0xFF3B82F6),
            onClick = { onTriggerAutofocus(); onDismiss() }
        ),
        MagicToolItem(
            title = "Upscale & Super-Resolution",
            subtitle = "Laplacian high-frequency detail restoration for 4K crisp clarity",
            icon = Icons.Default.HighQuality,
            iconColor = Color(0xFF6366F1),
            onClick = { onTriggerUpscale(); onDismiss() }
        ),
        MagicToolItem(
            title = "Magic Expand (Canvas Borders)",
            subtitle = "Synthesizes seamless outward borders to expand your canvas",
            icon = Icons.Default.ZoomOutMap,
            iconColor = Color(0xFF14B8A6),
            onClick = { onTriggerMagicExpand(); onDismiss() }
        )
    )

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
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Canva Pro Magic Studio",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Generative AI & Computer Vision Tools",
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

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tools.forEach { tool ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2130)),
                            border = BorderStroke(1.dp, Color(0xFF2A2D3D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tool.onClick() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tool.iconColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = tool.iconColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tool.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = tool.subtitle,
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
    }
}
