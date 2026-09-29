package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docu.editor.core.scanner.DocumentEdgeDetector
import com.docu.editor.core.scanner.model.DocumentCorners
import com.docu.editor.ui.scanner.CropLoupeOverlayView
import kotlinx.coroutines.launch

/**
 * Enterprise Interactive 4-Corner Document Perspective Crop Dialog (CamScanner Grade).
 * Allows users to manually drag 4 corners + 4 edge midpoints with a real-time 2.5x circular magnifier loupe.
 */
@Composable
fun InteractiveCropDialog(
    sourceBitmap: Bitmap,
    onApplyCrop: (DocumentCorners) -> Unit,
    onRotateClockwise: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var overlayViewRef by remember { mutableStateOf<CropLoupeOverlayView?>(null) }
    val initialCorners = remember(sourceBitmap) {
        DocumentCorners(
            topLeft = PointF(sourceBitmap.width * 0.04f, sourceBitmap.height * 0.04f),
            topRight = PointF(sourceBitmap.width * 0.96f, sourceBitmap.height * 0.04f),
            bottomRight = PointF(sourceBitmap.width * 0.96f, sourceBitmap.height * 0.96f),
            bottomLeft = PointF(sourceBitmap.width * 0.04f, sourceBitmap.height * 0.96f)
        )
    }
    var currentCorners by remember { mutableStateOf(initialCorners) }

    // Auto-detect document edges in background on launch
    LaunchedEffect(sourceBitmap) {
        try {
            val detected = DocumentEdgeDetector.detectCorners(sourceBitmap)
            currentCorners = detected
            overlayViewRef?.corners = detected
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxSize(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Crop & Deskew",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = "CAMSCANNER LOUPE",
                                    color = Color(0xFF15803D),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Drag corner handles • Floating 2.5x magnifier shows precise edge",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Crop Loupe View Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            CropLoupeOverlayView(ctx).also { view ->
                                view.sourceBitmap = sourceBitmap
                                view.corners = currentCorners
                                view.onCornersChanged = { updatedCorners ->
                                    currentCorners = updatedCorners
                                }
                                overlayViewRef = view
                            }
                        },
                        update = { view ->
                            view.sourceBitmap = sourceBitmap
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            overlayViewRef?.resetToFullImage()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Full Page", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val detected = DocumentEdgeDetector.detectCorners(sourceBitmap)
                                    overlayViewRef?.corners = detected
                                    currentCorners = detected
                                } catch (_: Exception) {}
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto Detect", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onRotateClockwise,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate 90°", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Confirm / Flatten Button
                Button(
                    onClick = {
                        val finalCorners = overlayViewRef?.corners ?: currentCorners
                        onApplyCrop(finalCorners)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Apply Crop & Flatten Perspective",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
