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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import com.docu.editor.domain.model.DocumentCanvasLayer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.docu.editor.domain.model.ShapeType
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
    canvasLayers: List<DocumentCanvasLayer> = emptyList(),
    selectedLayerId: String? = null,
    onSelectLayer: (String?) -> Unit = {},
    onDuplicateLayer: () -> Unit = {},
    onDeleteLayer: () -> Unit = {},
    onBringLayerToFront: () -> Unit = {},
    onSendLayerToBack: () -> Unit = {},
    onLayerAlphaChanged: (Float) -> Unit = {},
    onEditTextLayer: (DocumentCanvasLayer) -> Unit = {},
    onAddTextLayerClicked: () -> Unit = {},
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
    markupColorRgb: Int = android.graphics.Color.rgb(255, 235, 59),
    markupStrokeWidth: Float = 28f,
    penColorRgb: Int = android.graphics.Color.rgb(220, 38, 38),
    penStrokeWidth: Float = 6f,
    onCommitMarkupStroke: (points: List<android.graphics.PointF>, isHighlighter: Boolean) -> Unit = { _, _ -> },
    selectedShapeType: ShapeType = ShapeType.RECTANGLE,
    shapeStrokeWidth: Float = 6f,
    shapeStrokeColorRgb: Int = android.graphics.Color.rgb(220, 38, 38),
    onCommitShape: (shapeType: ShapeType, start: android.graphics.PointF, end: android.graphics.PointF, colorRgb: Int, strokeWidth: Float) -> Unit = { _, _, _, _, _ -> },
    onCommitBlackoutRect: (android.graphics.RectF) -> Unit = {},
    magicEraserBrushRadius: Float = 28f,
    onCommitMagicEraserStroke: (points: List<android.graphics.PointF>, brushRadius: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isHoldingCompare by remember { mutableStateOf(false) }
    var lassoBoxStart by remember { mutableStateOf<Offset?>(null) }
    var lassoBoxCurrent by remember { mutableStateOf<Offset?>(null) }
    var shapeDragStart by remember { mutableStateOf<Offset?>(null) }
    var shapeDragCurrent by remember { mutableStateOf<Offset?>(null) }
    var redactionBoxStart by remember { mutableStateOf<Offset?>(null) }
    var redactionBoxCurrent by remember { mutableStateOf<Offset?>(null) }
    val liveMarkupPoints = remember { mutableStateListOf<android.graphics.PointF>() }

    val isMultiLayerActive = canvasLayers.isNotEmpty() || activeOverlayBitmap != null

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (!isMultiLayerActive) {
            scale = (scale * zoomChange).coerceIn(0.5f, 6.0f)
            offset += panChange
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0)) // High-contrast neutral document canvas
            .onSizeChanged { containerSize = it }
            .transformable(
                state = transformState,
                enabled = (!isMultiLayerActive && activeMode !in listOf(
                    EditorToolMode.WHITEOUT,
                    EditorToolMode.MAGIC_ERASER,
                    EditorToolMode.HIGHLIGHTER,
                    EditorToolMode.MARKUP_PEN,
                    EditorToolMode.SHAPES,
                    EditorToolMode.REDACTION,
                    EditorToolMode.LASSO_SELECT
                ))
            )
            .pointerInput(
                bitmap,
                detectedItems,
                selectedItems,
                containerSize,
                scale,
                offset,
                activeMode,
                canvasRevision,
                canvasLayers,
                selectedLayerId,
                activeOverlayBitmap,
                overlayPositionX,
                overlayPositionY,
                overlayScale,
                overlayRotation
            ) {
                if (canvasLayers.isNotEmpty()) {
                    // Canva Multi-Layer Touch & Drag Interaction
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

                            val docX = (startOffset.x - baseLeft) / effectiveScale
                            val docY = (startOffset.y - baseTop) / effectiveScale

                            // Hit test from top-most layer downwards
                            val hit = canvasLayers.asReversed().firstOrNull { it.hitTest(docX, docY) }
                            if (hit != null && hit.id != selectedLayerId) {
                                onSelectLayer(hit.id)
                            }
                        },
                        onDrag = { change, dragAmount ->
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
                    )
                } else if (activeOverlayBitmap != null) {
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
                } else if (activeMode == EditorToolMode.HIGHLIGHTER || activeMode == EditorToolMode.MARKUP_PEN) {
                    // Continuous smooth drag for Highlighter & Markup Pen
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

                            val bx = (startOffset.x - baseLeft) / effectiveScale
                            val by = (startOffset.y - baseTop) / effectiveScale
                            liveMarkupPoints.clear()
                            if (bx in 0f..bitmap.width.toFloat() && by in 0f..bitmap.height.toFloat()) {
                                liveMarkupPoints.add(android.graphics.PointF(bx, by))
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

                            val bx = (change.position.x - baseLeft) / effectiveScale
                            val by = (change.position.y - baseTop) / effectiveScale
                            if (bx in 0f..bitmap.width.toFloat() && by in 0f..bitmap.height.toFloat()) {
                                liveMarkupPoints.add(android.graphics.PointF(bx, by))
                            }
                        },
                        onDragEnd = {
                            if (liveMarkupPoints.size >= 2) {
                                onCommitMarkupStroke(
                                    liveMarkupPoints.toList(),
                                    activeMode == EditorToolMode.HIGHLIGHTER
                                )
                            }
                            liveMarkupPoints.clear()
                        },
                        onDragCancel = {
                            liveMarkupPoints.clear()
                        }
                    )
                } else if (activeMode == EditorToolMode.MAGIC_ERASER) {
                    // Canva Pro Magic Object Eraser drag gesture
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

                            val bx = (startOffset.x - baseLeft) / effectiveScale
                            val by = (startOffset.y - baseTop) / effectiveScale
                            liveMarkupPoints.clear()
                            if (bx in 0f..bitmap.width.toFloat() && by in 0f..bitmap.height.toFloat()) {
                                liveMarkupPoints.add(android.graphics.PointF(bx, by))
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

                            val bx = (change.position.x - baseLeft) / effectiveScale
                            val by = (change.position.y - baseTop) / effectiveScale
                            if (bx in 0f..bitmap.width.toFloat() && by in 0f..bitmap.height.toFloat()) {
                                liveMarkupPoints.add(android.graphics.PointF(bx, by))
                            }
                        },
                        onDragEnd = {
                            if (liveMarkupPoints.size >= 2) {
                                onCommitMagicEraserStroke(
                                    liveMarkupPoints.toList(),
                                    magicEraserBrushRadius
                                )
                            }
                            liveMarkupPoints.clear()
                        },
                        onDragCancel = {
                            liveMarkupPoints.clear()
                        }
                    )
                } else if (activeMode == EditorToolMode.REDACTION) {
                    // Redaction / Blackout drag gesture to censor area
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            redactionBoxStart = startOffset
                            redactionBoxCurrent = startOffset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            redactionBoxCurrent = change.position
                        },
                        onDragEnd = {
                            val start = redactionBoxStart
                            val curr = redactionBoxCurrent
                            if (start != null && curr != null) {
                                val fitScale = min(
                                    containerSize.width.toFloat() / bitmap.width,
                                    containerSize.height.toFloat() / bitmap.height
                                )
                                val effectiveScale = fitScale * scale
                                val drawWidth = bitmap.width * effectiveScale
                                val drawHeight = bitmap.height * effectiveScale
                                val baseLeft = (containerSize.width - drawWidth) / 2f + offset.x
                                val baseTop = (containerSize.height - drawHeight) / 2f + offset.y

                                val sx = (start.x - baseLeft) / effectiveScale
                                val sy = (start.y - baseTop) / effectiveScale
                                val ex = (curr.x - baseLeft) / effectiveScale
                                val ey = (curr.y - baseTop) / effectiveScale

                                val left = kotlin.math.min(sx, ex).coerceIn(0f, bitmap.width.toFloat())
                                val top = kotlin.math.min(sy, ey).coerceIn(0f, bitmap.height.toFloat())
                                val right = kotlin.math.max(sx, ex).coerceIn(0f, bitmap.width.toFloat())
                                val bottom = kotlin.math.max(sy, ey).coerceIn(0f, bitmap.height.toFloat())

                                if (right - left > 6f && bottom - top > 6f) {
                                    onCommitBlackoutRect(android.graphics.RectF(left, top, right, bottom))
                                }
                            }
                            redactionBoxStart = null
                            redactionBoxCurrent = null
                        },
                        onDragCancel = {
                            redactionBoxStart = null
                            redactionBoxCurrent = null
                        }
                    )
                } else if (activeMode == EditorToolMode.SHAPES) {
                    // Geometric Shapes drag gesture
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            shapeDragStart = startOffset
                            shapeDragCurrent = startOffset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            shapeDragCurrent = change.position
                        },
                        onDragEnd = {
                            val start = shapeDragStart
                            val curr = shapeDragCurrent
                            if (start != null && curr != null) {
                                val fitScale = min(
                                    containerSize.width.toFloat() / bitmap.width,
                                    containerSize.height.toFloat() / bitmap.height
                                )
                                val effectiveScale = fitScale * scale
                                val drawWidth = bitmap.width * effectiveScale
                                val drawHeight = bitmap.height * effectiveScale
                                val baseLeft = (containerSize.width - drawWidth) / 2f + offset.x
                                val baseTop = (containerSize.height - drawHeight) / 2f + offset.y

                                val sx = (start.x - baseLeft) / effectiveScale
                                val sy = (start.y - baseTop) / effectiveScale
                                val ex = (curr.x - baseLeft) / effectiveScale
                                val ey = (curr.y - baseTop) / effectiveScale

                                if (kotlin.math.hypot((ex - sx).toDouble(), (ey - sy).toDouble()) > 10.0) {
                                    onCommitShape(
                                        selectedShapeType,
                                        android.graphics.PointF(sx, sy),
                                        android.graphics.PointF(ex, ey),
                                        shapeStrokeColorRgb,
                                        shapeStrokeWidth
                                    )
                                }
                            }
                            shapeDragStart = null
                            shapeDragCurrent = null
                        },
                        onDragCancel = {
                            shapeDragStart = null
                            shapeDragCurrent = null
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
                            } else {
                                if (bitmapX in 0f..bitmap.width.toFloat() && bitmapY in 0f..bitmap.height.toFloat()) {
                                    onInsertTextTouch(bitmapX, bitmapY)
                                }
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

            // 2.2 Draw active live highlighter / markup pen / magic eraser stroke
            if (liveMarkupPoints.size > 1) {
                val isMagicEraser = activeMode == EditorToolMode.MAGIC_ERASER
                val isHl = activeMode == EditorToolMode.HIGHLIGHTER
                val strokeColor = when {
                    isMagicEraser -> Color(0xFFD946EF).copy(alpha = 0.65f)
                    isHl -> Color(markupColorRgb).copy(alpha = 0.55f)
                    else -> Color(penColorRgb)
                }
                val strokeW = when {
                    isMagicEraser -> (magicEraserBrushRadius * 2f) * effectiveScale
                    isHl -> markupStrokeWidth * effectiveScale
                    else -> penStrokeWidth * effectiveScale
                }
                for (i in 0 until liveMarkupPoints.size - 1) {
                    val p1 = liveMarkupPoints[i]
                    val p2 = liveMarkupPoints[i + 1]
                    val s1 = Offset(baseLeft + p1.x * effectiveScale, baseTop + p1.y * effectiveScale)
                    val s2 = Offset(baseLeft + p2.x * effectiveScale, baseTop + p2.y * effectiveScale)
                    drawLine(
                        color = strokeColor,
                        start = s1,
                        end = s2,
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2.3 Draw live Redaction box preview
            if (redactionBoxStart != null && redactionBoxCurrent != null) {
                val s = redactionBoxStart!!
                val c = redactionBoxCurrent!!
                val left = kotlin.math.min(s.x, c.x)
                val top = kotlin.math.min(s.y, c.y)
                val w = kotlin.math.abs(s.x - c.x)
                val h = kotlin.math.abs(s.y - c.y)
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(left, top),
                    size = Size(w, h)
                )
                drawRect(
                    color = Color(0xFFEF4444),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 2.4 Draw live Geometric Shape preview
            if (shapeDragStart != null && shapeDragCurrent != null) {
                val s = shapeDragStart!!
                val c = shapeDragCurrent!!
                val strokeColor = Color(shapeStrokeColorRgb)
                val strokeW = shapeStrokeWidth * effectiveScale
                when (selectedShapeType) {
                    ShapeType.RECTANGLE -> {
                        val left = kotlin.math.min(s.x, c.x)
                        val top = kotlin.math.min(s.y, c.y)
                        val w = kotlin.math.abs(s.x - c.x)
                        val h = kotlin.math.abs(s.y - c.y)
                        drawRect(
                            color = strokeColor,
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = strokeW)
                        )
                    }
                    ShapeType.LINE -> {
                        drawLine(
                            color = strokeColor,
                            start = s,
                            end = c,
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                    ShapeType.CIRCLE -> {
                        val left = kotlin.math.min(s.x, c.x)
                        val top = kotlin.math.min(s.y, c.y)
                        val w = kotlin.math.abs(s.x - c.x)
                        val h = kotlin.math.abs(s.y - c.y)
                        drawOval(
                            color = strokeColor,
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = strokeW)
                        )
                    }
                    ShapeType.ARROW -> {
                        drawLine(
                            color = strokeColor,
                            start = s,
                            end = c,
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        val deltaX = c.x - s.x
                        val deltaY = c.y - s.y
                        val angle = kotlin.math.atan2(deltaY.toDouble(), deltaX.toDouble())
                        val headLen = (strokeW * 3.5f).coerceAtLeast(20f)
                        val headAngle = Math.PI / 6
                        val x1 = c.x - headLen * kotlin.math.cos(angle - headAngle).toFloat()
                        val y1 = c.y - headLen * kotlin.math.sin(angle - headAngle).toFloat()
                        val x2 = c.x - headLen * kotlin.math.cos(angle + headAngle).toFloat()
                        val y2 = c.y - headLen * kotlin.math.sin(angle + headAngle).toFloat()
                        drawLine(color = strokeColor, start = c, end = Offset(x1, y1), strokeWidth = strokeW, cap = StrokeCap.Round)
                        drawLine(color = strokeColor, start = c, end = Offset(x2, y2), strokeWidth = strokeW, cap = StrokeCap.Round)
                    }
                }
            }

            // 3. Draw Canva Pro Multi-Layers in z-order or single overlay fallback
            if (canvasLayers.isNotEmpty()) {
                canvasLayers.forEach { layer ->
                    val isSelected = (layer.id == selectedLayerId)
                    val lDrawW = (layer.bitmap.width * layer.scale * effectiveScale).toInt().coerceAtLeast(1)
                    val lDrawH = (layer.bitmap.height * layer.scale * effectiveScale).toInt().coerceAtLeast(1)
                    val lScreenX = baseLeft + (layer.x * effectiveScale)
                    val lScreenY = baseTop + (layer.y * effectiveScale)
                    val pivot = Offset(lScreenX + lDrawW / 2f, lScreenY + lDrawH / 2f)

                    rotate(degrees = layer.rotation, pivot = pivot) {
                        drawImage(
                            image = layer.bitmap.asImageBitmap(),
                            dstOffset = IntOffset(lScreenX.toInt(), lScreenY.toInt()),
                            dstSize = IntSize(lDrawW, lDrawH),
                            alpha = layer.alpha
                        )

                        if (isSelected) {
                            val strokeW = 2.dp.toPx()
                            // Canva Purple selection frame
                            drawRect(
                                color = Color(0xFF6366F1),
                                topLeft = Offset(lScreenX, lScreenY),
                                size = Size(lDrawW.toFloat(), lDrawH.toFloat()),
                                style = Stroke(width = strokeW)
                            )

                            val cornerRadius = 6.dp.toPx()
                            val corners = listOf(
                                Offset(lScreenX, lScreenY),
                                Offset(lScreenX + lDrawW, lScreenY),
                                Offset(lScreenX, lScreenY + lDrawH),
                                Offset(lScreenX + lDrawW, lScreenY + lDrawH)
                            )
                            corners.forEach { cornerPt ->
                                drawCircle(color = Color.White, radius = cornerRadius, center = cornerPt)
                                drawCircle(color = Color(0xFF6366F1), radius = cornerRadius, center = cornerPt, style = Stroke(width = strokeW))
                            }

                            // Top Rotate Stem
                            val rotStem = 22.dp.toPx()
                            val rotTop = Offset(lScreenX + lDrawW / 2f, lScreenY - rotStem)
                            val rotBottom = Offset(lScreenX + lDrawW / 2f, lScreenY)
                            drawLine(color = Color(0xFF6366F1), start = rotBottom, end = rotTop, strokeWidth = strokeW)
                            drawCircle(color = Color.White, radius = cornerRadius, center = rotTop)
                            drawCircle(color = Color(0xFF6366F1), radius = cornerRadius, center = rotTop, style = Stroke(width = strokeW))
                        }
                    }
                }
            } else if (activeOverlayBitmap != null) {
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

                    // Canva-Grade Stamp Boundary indicator & 4 Corner Handles
                    drawRect(
                        color = Color(0xFF2563EB),
                        topLeft = Offset(overlayScreenX, overlayScreenY),
                        size = Size(overlayDrawW.toFloat(), overlayDrawH.toFloat()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val cornerRadius = 5.dp.toPx()
                    val corners = listOf(
                        Offset(overlayScreenX, overlayScreenY),
                        Offset(overlayScreenX + overlayDrawW, overlayScreenY),
                        Offset(overlayScreenX, overlayScreenY + overlayDrawH),
                        Offset(overlayScreenX + overlayDrawW, overlayScreenY + overlayDrawH)
                    )
                    corners.forEach { cornerPt ->
                        drawCircle(color = Color.White, radius = cornerRadius, center = cornerPt)
                        drawCircle(color = Color(0xFF2563EB), radius = cornerRadius, center = cornerPt, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }

        // Highlighter Mode Banner
        if (activeMode == EditorToolMode.HIGHLIGHTER) {
            Surface(
                color = Color(0xFFD97706).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "🖍️ Highlighter Active: Drag across text to highlight",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Canva Magic Object Eraser Banner
        if (activeMode == EditorToolMode.MAGIC_ERASER) {
            Surface(
                color = Color(0xFFA855F7).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "🪄 Canva Magic Eraser: Brush over unwanted object to erase",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Markup Pen Mode Banner
        if (activeMode == EditorToolMode.MARKUP_PEN) {
            Surface(
                color = Color(0xFFDC2626).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "🖊️ Pen Markup Active: Drag to draw notes or markings",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Redaction / Blackout Mode Banner
        if (activeMode == EditorToolMode.REDACTION) {
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "⬛ Redaction Active: Drag black censor box or tap text",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Shapes Mode Banner
        if (activeMode == EditorToolMode.SHAPES) {
            Surface(
                color = Color(0xFF2563EB).copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "📐 Shapes Active: Drag to draw ${selectedShapeType.displayName}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
                    .padding(bottom = if (canvasLayers.isNotEmpty() || activeOverlayBitmap != null) 90.dp else 16.dp)
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

        // Active Canva Multi-Layer Control Bar
        if (canvasLayers.isNotEmpty()) {
            val curLayer = canvasLayers.firstOrNull { it.id == selectedLayerId } ?: canvasLayers.lastOrNull()
            val curIdx = canvasLayers.indexOfFirst { it.id == selectedLayerId }.let { if (it >= 0) it else canvasLayers.size - 1 }
            val curAlpha = curLayer?.alpha ?: 1.0f

            Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top row: Layer Info + Quick Canva Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color(0xFFEEF2FF),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "🎨 Layer ${curIdx + 1}/${canvasLayers.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Edit Text Layer (if text layer)
                        if (curLayer?.isTextLayer == true) {
                            Surface(shape = CircleShape, color = Color(0xFFE0E7FF), modifier = Modifier.size(32.dp)) {
                                IconButton(onClick = { curLayer.let { onEditTextLayer(it) } }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Text", tint = Color(0xFF4338CA), modifier = Modifier.size(15.dp))
                                }
                            }
                        }

                        // Add Text Layer Quick Action
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = onAddTextLayerClicked) {
                                Icon(Icons.Default.TextFields, contentDescription = "Add Text Layer", tint = Color(0xFF334155), modifier = Modifier.size(15.dp))
                            }
                        }

                        // Duplicate
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = onDuplicateLayer) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Layer", tint = Color(0xFF334155), modifier = Modifier.size(15.dp))
                            }
                        }

                        // Bring Forward
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = onBringLayerToFront) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Bring Forward", tint = Color(0xFF334155), modifier = Modifier.size(15.dp))
                            }
                        }

                        // Send Backward
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = onSendLayerToBack) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Send Backward", tint = Color(0xFF334155), modifier = Modifier.size(15.dp))
                            }
                        }

                        // Opacity Toggle
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable {
                                val nextAlpha = when {
                                    curAlpha > 0.85f -> 0.75f
                                    curAlpha > 0.60f -> 0.50f
                                    curAlpha > 0.35f -> 0.25f
                                    else -> 1.0f
                                }
                                onLayerAlphaChanged(nextAlpha)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Opacity, contentDescription = "Opacity", tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${(curAlpha * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            }
                        }

                        // Delete Layer
                        Surface(shape = CircleShape, color = Color(0xFFFEE2E2), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = onDeleteLayer) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Layer", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.size(6.dp))

                    // Bottom row: Scale, Rotate & Flatten Done Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Size Stepper: Decrease
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = { onOverlayScaleChanged(0.85f) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Smaller", tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
                            }
                        }

                        Text("Scale: ${(overlayScale * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))

                        // Size Stepper: Increase
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = { onOverlayScaleChanged(1.15f) }) {
                                Icon(Icons.Default.Add, contentDescription = "Larger", tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
                            }
                        }

                        // Rotation: Rotate Left
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = { onOverlayRotateChanged(overlayRotation - 5f) }) {
                                Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Tilt Left", tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
                            }
                        }

                        Text("${overlayRotation.toInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))

                        // Rotation: Rotate Right
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = { onOverlayRotateChanged(overlayRotation + 5f) }) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Tilt Right", tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Flatten / Done Button
                        Button(
                            onClick = onCommitOverlay,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Flatten All", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (activeOverlayBitmap != null) {
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
