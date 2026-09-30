package com.docu.editor.ui.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.domain.model.EditorToolMode
import kotlin.math.min

@Composable
fun DocumentInteractiveCanvas(
    bitmap: Bitmap,
    detectedItems: List<DetectedTextItem>,
    selectedItem: DetectedTextItem?,
    selectedItems: List<DetectedTextItem> = emptyList(),
    activeMode: EditorToolMode = EditorToolMode.TEXT_EDIT,
    canvasRevision: Long = 0L,
    onTextItemTapped: (DetectedTextItem) -> Unit,
    onLassoSelectionChanged: (List<DetectedTextItem>) -> Unit = {},
    onWhiteoutTouch: (bitmapX: Float, bitmapY: Float) -> Unit = { _, _ -> },
    onInsertTextTouch: (bitmapX: Float, bitmapY: Float) -> Unit = { _, _ -> },
    activeOverlayBitmap: Bitmap? = null,
    originalBitmap: Bitmap? = null,
    overlayPositionX: Float = 100f,
    overlayPositionY: Float = 100f,
    overlayScale: Float = 1.0f,
    overlayRotation: Float = 0f,
    onOverlayDragged: (deltaX: Float, deltaY: Float) -> Unit = { _, _ -> },
    onOverlayScaleChanged: (scaleMultiplier: Float) -> Unit = {},
    onOverlayRotateChanged: (Float) -> Unit = {},
    onCommitOverlay: () -> Unit = {},
    onCancelOverlay: () -> Unit = {},
    searchMatchingIndices: List<Int> = emptyList(),
    pdfPageCount: Int = 1,
    currentPageIndex: Int = 0,
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onOpenPagesOverview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isHoldingCompare by remember { mutableStateOf(false) }
    var lassoBoxStart by remember { mutableStateOf<Offset?>(null) }
    var lassoBoxCurrent by remember { mutableStateOf<Offset?>(null) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (activeOverlayBitmap == null) {
            scale = (scale * zoomChange).coerceIn(0.5f, 6.0f)
            offset += panChange
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0)) // High-contrast neutral document canvas
            .onSizeChanged { containerSize = it }
            .transformable(state = transformState)
            .pointerInput(
                bitmap,
                detectedItems,
                selectedItems,
                containerSize,
                scale,
                offset,
                activeMode,
                canvasRevision,
                activeOverlayBitmap,
                overlayPositionX,
                overlayPositionY,
                overlayScale,
                overlayRotation
            ) {
                if (activeOverlayBitmap != null) {
                    // Signature / Stamp Placement Mode: Drag anywhere on canvas to move overlay
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val fitScale = min(
                            containerSize.width.toFloat() / bitmap.width,
                            containerSize.height.toFloat() / bitmap.height
                        )
                        val effectiveScale = fitScale * scale
                        if (effectiveScale > 0) {
                            onOverlayDragged(dragAmount.x / effectiveScale, dragAmount.y / effectiveScale)
                        }
                    }
                } else if (activeMode == EditorToolMode.WHITEOUT) {
                    // Whiteout mode: drag or tap to erase unwanted ink/dots with pure paper white
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val fitScale = min(
                                containerSize.width.toFloat() / bitmap.width,
                                containerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * scale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (containerSize.width - drawWidth) / 2f + offset.x
                            val baseTop = (containerSize.height - drawHeight) / 2f + offset.y

                            val bitmapX = (startOffset.x - baseLeft) / effectiveScale
                            val bitmapY = (startOffset.y - baseTop) / effectiveScale
                            if (bitmapX in 0f..bitmap.width.toFloat() && bitmapY in 0f..bitmap.height.toFloat()) {
                                onWhiteoutTouch(bitmapX, bitmapY)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fitScale = min(
                                containerSize.width.toFloat() / bitmap.width,
                                containerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * scale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (containerSize.width - drawWidth) / 2f + offset.x
                            val baseTop = (containerSize.height - drawHeight) / 2f + offset.y

                            val bitmapX = (change.position.x - baseLeft) / effectiveScale
                            val bitmapY = (change.position.y - baseTop) / effectiveScale
                            if (bitmapX in 0f..bitmap.width.toFloat() && bitmapY in 0f..bitmap.height.toFloat()) {
                                onWhiteoutTouch(bitmapX, bitmapY)
                            }
                        }
                    )
                } else if (activeMode == EditorToolMode.ADD_TEXT) {
                    // Add Text mode: tap on blank space to place new text
                    detectTapGestures { tapScreenOffset ->
                        if (containerSize.width == 0 || containerSize.height == 0) return@detectTapGestures
                        val fitScale = min(
                            containerSize.width.toFloat() / bitmap.width,
                            containerSize.height.toFloat() / bitmap.height
                        )
                        val effectiveScale = fitScale * scale
                        val drawWidth = bitmap.width * effectiveScale
                        val drawHeight = bitmap.height * effectiveScale
                        val baseLeft = (containerSize.width - drawWidth) / 2f + offset.x
                        val baseTop = (containerSize.height - drawHeight) / 2f + offset.y

                        val bitmapX = (tapScreenOffset.x - baseLeft) / effectiveScale
                        val bitmapY = (tapScreenOffset.y - baseTop) / effectiveScale
                        if (bitmapX in 0f..bitmap.width.toFloat() && bitmapY in 0f..bitmap.height.toFloat()) {
                            onInsertTextTouch(bitmapX, bitmapY)
                        }
                    }
                } else if (activeMode == EditorToolMode.LASSO_SELECT) {
                    // Multi-Word / Paragraph Lasso Box Selection
                    detectDragGestures(
                        onDragStart = { start ->
                            lassoBoxStart = start
                            lassoBoxCurrent = start
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            lassoBoxCurrent = change.position
                        },
                        onDragEnd = {
                            val start = lassoBoxStart
                            val end = lassoBoxCurrent
                            if (start != null && end != null) {
                                val fitScale = min(
                                    containerSize.width.toFloat() / bitmap.width,
                                    containerSize.height.toFloat() / bitmap.height
                                )
                                val effectiveScale = fitScale * scale
                                val drawWidth = bitmap.width * effectiveScale
                                val drawHeight = bitmap.height * effectiveScale
                                val baseLeft = (containerSize.width - drawWidth) / 2f + offset.x
                                val baseTop = (containerSize.height - drawHeight) / 2f + offset.y

                                val minX = (minOf(start.x, end.x) - baseLeft) / effectiveScale
                                val maxX = (maxOf(start.x, end.x) - baseLeft) / effectiveScale
                                val minY = (minOf(start.y, end.y) - baseTop) / effectiveScale
                                val maxY = (maxOf(start.y, end.y) - baseTop) / effectiveScale

                                val selected = detectedItems.filter { item ->
                                    val b = item.boundingBox
                                    b.left < maxX && b.right > minX && b.top < maxY && b.bottom > minY
                                }
                                onLassoSelectionChanged(selected)
                            }
                            lassoBoxStart = null
                            lassoBoxCurrent = null
                        },
                        onDragCancel = {
                            lassoBoxStart = null
                            lassoBoxCurrent = null
                        }
                    )
                } else {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.2f
                            }
                        },
                        onTap = { tapScreenOffset ->
                            if (containerSize.width == 0 || containerSize.height == 0) return@detectTapGestures

                            val fitScale = min(
                                containerSize.width.toFloat() / bitmap.width,
                                containerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * scale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (containerSize.width - drawWidth) / 2f + offset.x
                            val baseTop = (containerSize.height - drawHeight) / 2f + offset.y

                            val relX = tapScreenOffset.x - baseLeft
                            val relY = tapScreenOffset.y - baseTop

                            val bitmapX = relX / effectiveScale
                            val bitmapY = relY / effectiveScale

                            // 1. Direct hit check
                            var hitItem = detectedItems.firstOrNull { item ->
                                item.boundingBox.contains(bitmapX.toInt(), bitmapY.toInt())
                            }

                            // 2. Magnetic Snap: If finger missed by a few pixels, find the closest text item within 32dp
                            if (hitItem == null && detectedItems.isNotEmpty()) {
                                val snapRadiusPx = 32.dp.toPx() / effectiveScale
                                var closestDistance = Float.MAX_VALUE
                                for (item in detectedItems) {
                                    val b = item.boundingBox
                                    val dx = when {
                                        bitmapX < b.left -> b.left - bitmapX
                                        bitmapX > b.right -> bitmapX - b.right
                                        else -> 0f
                                    }
                                    val dy = when {
                                        bitmapY < b.top -> b.top - bitmapY
                                        bitmapY > b.bottom -> bitmapY - b.bottom
                                        else -> 0f
                                    }
                                    val dist = kotlin.math.hypot(dx, dy)
                                    if (dist <= snapRadiusPx && dist < closestDistance) {
                                        closestDistance = dist
                                        hitItem = item
                                    }
                                }
                            }

                            if (hitItem != null) {
                                onTextItemTapped(hitItem)
                            }
                        }
                    )
                }
            }
    ) {
        val displayBitmap = if (isHoldingCompare && originalBitmap != null) originalBitmap else bitmap
        val cachedImageBitmap = remember(displayBitmap, canvasRevision) {
            displayBitmap.asImageBitmap()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val _rev = canvasRevision
            if (size.width == 0f || size.height == 0f) return@Canvas

            val fitScale = min(size.width / displayBitmap.width, size.height / displayBitmap.height)
            val effectiveScale = fitScale * scale

            val drawWidth = displayBitmap.width * effectiveScale
            val drawHeight = displayBitmap.height * effectiveScale

            val baseLeft = (size.width - drawWidth) / 2f + offset.x
            val baseTop = (size.height - drawHeight) / 2f + offset.y

            // 1. Draw clean document bitmap (Cached for 60fps silky smooth pan/zoom)
            drawImage(
                image = cachedImageBitmap,
                dstOffset = IntOffset(baseLeft.toInt(), baseTop.toInt()),
                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
            )

            // 2. Draw bounding indicators in TEXT_EDIT and LASSO_SELECT modes
            if ((activeMode == EditorToolMode.TEXT_EDIT || activeMode == EditorToolMode.LASSO_SELECT) && activeOverlayBitmap == null) {
                val selectedIds = selectedItems.map { it.id }.toSet()
                for ((idx, item) in detectedItems.withIndex()) {
                    val isSingleSelected = item.id == selectedItem?.id
                    val isLassoSelected = selectedIds.contains(item.id)
                    val isSearchMatch = searchMatchingIndices.contains(idx)
                    val box = item.boundingBox

                    val boxLeft = baseLeft + (box.left * effectiveScale)
                    val boxTop = baseTop + (box.top * effectiveScale)
                    val boxWidth = box.width() * effectiveScale
                    val boxHeight = box.height() * effectiveScale

                    if (isSearchMatch) {
                        drawRect(
                            color = Color(0xFFFFEB3B).copy(alpha = 0.55f),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight)
                        )
                        drawRect(
                            color = Color(0xFFF57F17),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    } else if (isSingleSelected) {
                        // High-contrast Emerald/Cyan Selection with Corner Knobs
                        drawRect(
                            color = Color(0xFF06B6D4).copy(alpha = 0.28f),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight)
                        )
                        drawRect(
                            color = Color(0xFF0284C7),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF0284C7),
                            radius = 4.dp.toPx(),
                            center = Offset(boxLeft, boxTop)
                        )
                        drawCircle(
                            color = Color(0xFF0284C7),
                            radius = 4.dp.toPx(),
                            center = Offset(boxLeft + boxWidth, boxTop + boxHeight)
                        )
                    } else if (isLassoSelected) {
                        drawRect(
                            color = Color(0xFFF59E0B).copy(alpha = 0.35f),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight)
                        )
                        drawRect(
                            color = Color(0xFFD97706),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    } else {
                        // CamScanner-Style Soft Blue/Cyan Glowing Highlight Pill
                        drawRect(
                            color = Color(0xFF0284C7).copy(alpha = 0.14f),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight)
                        )
                        drawRect(
                            color = Color(0xFF0284C7).copy(alpha = 0.55f),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                }
            }

            // 2.1 Draw active dragging lasso rectangle
            val lStart = lassoBoxStart
            val lCurr = lassoBoxCurrent
            if (lStart != null && lCurr != null) {
                val left = minOf(lStart.x, lCurr.x)
                val top = minOf(lStart.y, lCurr.y)
                val w = kotlin.math.abs(lCurr.x - lStart.x)
                val h = kotlin.math.abs(lCurr.y - lStart.y)
                drawRect(
                    color = Color(0xFF3B82F6).copy(alpha = 0.22f),
                    topLeft = Offset(left, top),
                    size = Size(w, h)
                )
                drawRect(
                    color = Color(0xFF2563EB),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 3. Draw Signature / Stamp Overlay if active
            if (activeOverlayBitmap != null) {
                val overlayDrawW = (activeOverlayBitmap.width * overlayScale * effectiveScale).toInt()
                val overlayDrawH = (activeOverlayBitmap.height * overlayScale * effectiveScale).toInt()
                val overlayScreenX = baseLeft + (overlayPositionX * effectiveScale)
                val overlayScreenY = baseTop + (overlayPositionY * effectiveScale)
                val pivot = Offset(overlayScreenX + overlayDrawW / 2f, overlayScreenY + overlayDrawH / 2f)

                rotate(degrees = overlayRotation, pivot = pivot) {
                    drawImage(
                        image = activeOverlayBitmap.asImageBitmap(),
                        dstOffset = IntOffset(overlayScreenX.toInt(), overlayScreenY.toInt()),
                        dstSize = IntSize(overlayDrawW, overlayDrawH)
                    )

                    // Neon Stamp Boundary indicator
                    drawRect(
                        color = Color(0xFF10B981),
                        topLeft = Offset(overlayScreenX, overlayScreenY),
                        size = Size(overlayDrawW.toFloat(), overlayDrawH.toFloat()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Add Text Mode Banner
        if (activeMode == EditorToolMode.ADD_TEXT) {
            Surface(
                color = Color(0xFF2563EB).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "✍️ Insert Text Mode: Tap any blank line or space to type",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Whiteout Mode Banner
        if (activeMode == EditorToolMode.WHITEOUT) {
            Surface(
                color = Color(0xFF2563EB).copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "Whiteout Eraser Active: Drag to wipe out marks or dots",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Hold to Compare Floating Pill (CamScanner Feature)
        if (originalBitmap != null) {
            Surface(
                color = if (isHoldingCompare) Color(0xFFEF4444) else Color(0xFF0F172A).copy(alpha = 0.78f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHoldingCompare = true
                                tryAwaitRelease()
                                isHoldingCompare = false
                            }
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHoldingCompare) "👁️ Showing Original" else "👁️ Hold to Compare",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Multi-Page PDF & Batch Scan Navigation Pill
        if (pdfPageCount > 1) {
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (activeOverlayBitmap != null) 90.dp else 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousPage,
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "◀",
                            color = if (currentPageIndex > 0) Color.White else Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clickable { onOpenPagesOverview() }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Pages Overview",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Page ${currentPageIndex + 1} of $pdfPageCount",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onNextPage,
                        enabled = currentPageIndex < pdfPageCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "▶",
                            color = if (currentPageIndex < pdfPageCount - 1) Color.White else Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Overlay Control Dock (Stamp / Signature placement)
        if (activeOverlayBitmap != null) {
            Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Size Stepper: Decrease
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(onClick = { onOverlayScaleChanged(0.85f) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Smaller", tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        }
                    }

                    Text("Scale: ${(overlayScale * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))

                    // Size Stepper: Increase
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(onClick = { onOverlayScaleChanged(1.15f) }) {
                            Icon(Icons.Default.Add, contentDescription = "Larger", tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Rotation: Rotate Left
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(onClick = { onOverlayRotateChanged(overlayRotation - 5f) }) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Tilt Left", tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        }
                    }

                    Text("${overlayRotation.toInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))

                    // Rotation: Rotate Right
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(onClick = { onOverlayRotateChanged(overlayRotation + 5f) }) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Tilt Right", tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Cancel Button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(onClick = onCancelOverlay) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        }
                    }

                    // Stamp Button
                    Button(
                        onClick = onCommitOverlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stamp Here", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
