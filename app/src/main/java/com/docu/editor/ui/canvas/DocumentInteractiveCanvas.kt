package com.docu.editor.ui.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale as drawScopeScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
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
    var activeHandle by remember { mutableStateOf(CanvasHandleType.NONE) }
    val snapGuides = remember { mutableStateListOf<SnapGuideLine>() }
    val hapticFeedback = LocalHapticFeedback.current
    var hudDimensions by remember { mutableStateOf<String?>(null) }
    var hudPosition by remember { mutableStateOf<Offset?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    val currentCanvasLayers by rememberUpdatedState(canvasLayers)
    val currentSelectedLayerId by rememberUpdatedState(selectedLayerId)
    val currentActiveOverlayBitmap by rememberUpdatedState(activeOverlayBitmap)
    val currentOverlayPositionX by rememberUpdatedState(overlayPositionX)
    val currentOverlayPositionY by rememberUpdatedState(overlayPositionY)
    val currentOverlayScale by rememberUpdatedState(overlayScale)
    val currentOverlayRotation by rememberUpdatedState(overlayRotation)
    val currentDetectedItems by rememberUpdatedState(detectedItems)
    val currentSelectedItem by rememberUpdatedState(selectedItem)
    val currentSelectedItems by rememberUpdatedState(selectedItems)
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentContainerSize by rememberUpdatedState(containerSize)

    val currentOnOverlayDragged by rememberUpdatedState(onOverlayDragged)
    val currentOnOverlayScaleChanged by rememberUpdatedState(onOverlayScaleChanged)
    val currentOnOverlayRotateChanged by rememberUpdatedState(onOverlayRotateChanged)
    val currentOnSelectLayer by rememberUpdatedState(onSelectLayer)
    val currentOnTextItemTapped by rememberUpdatedState(onTextItemTapped)
    val currentOnInsertTextTouch by rememberUpdatedState(onInsertTextTouch)
    val currentOnWhiteoutTouch by rememberUpdatedState(onWhiteoutTouch)
    val currentOnCommitMarkupStroke by rememberUpdatedState(onCommitMarkupStroke)
    val currentOnCommitShape by rememberUpdatedState(onCommitShape)
    val currentOnCommitBlackoutRect by rememberUpdatedState(onCommitBlackoutRect)
    val currentOnCommitMagicEraserStroke by rememberUpdatedState(onCommitMagicEraserStroke)
    val currentOnLassoSelectionChanged by rememberUpdatedState(onLassoSelectionChanged)

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 6.0f)
        offset += panChange
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0)) // High-contrast neutral document canvas
            .onSizeChanged { containerSize = it }
            .transformable(
                state = transformState,
                enabled = activeMode !in listOf(
                    EditorToolMode.WHITEOUT,
                    EditorToolMode.MAGIC_ERASER,
                    EditorToolMode.HIGHLIGHTER,
                    EditorToolMode.MARKUP_PEN,
                    EditorToolMode.SHAPES,
                    EditorToolMode.REDACTION,
                    EditorToolMode.LASSO_SELECT
                )
            )
            .pointerInput(bitmap, activeMode) {
                if (activeMode == EditorToolMode.WHITEOUT) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val fitScale = min(
                                currentContainerSize.width.toFloat() / bitmap.width,
                                currentContainerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * currentScale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                            val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                            val bitmapX = (startOffset.x - baseLeft) / effectiveScale
                            val bitmapY = (startOffset.y - baseTop) / effectiveScale
                            if (bitmapX in 0f..bitmap.width.toFloat() && bitmapY in 0f..bitmap.height.toFloat()) {
                                currentOnWhiteoutTouch(bitmapX, bitmapY)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fitScale = min(
                                currentContainerSize.width.toFloat() / bitmap.width,
                                currentContainerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * currentScale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                            val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                            val bitmapX = (change.position.x - baseLeft) / effectiveScale
                            val bitmapY = (change.position.y - baseTop) / effectiveScale
                            if (bitmapX in 0f..bitmap.width.toFloat() && bitmapY in 0f..bitmap.height.toFloat()) {
                                currentOnWhiteoutTouch(bitmapX, bitmapY)
                            }
                        }
                    )
                } else if (activeMode == EditorToolMode.HIGHLIGHTER || activeMode == EditorToolMode.MARKUP_PEN) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val fitScale = min(
                                currentContainerSize.width.toFloat() / bitmap.width,
                                currentContainerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * currentScale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                            val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

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
                                currentContainerSize.width.toFloat() / bitmap.width,
                                currentContainerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * currentScale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                            val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                            val bx = (change.position.x - baseLeft) / effectiveScale
                            val by = (change.position.y - baseTop) / effectiveScale
                            if (bx in 0f..bitmap.width.toFloat() && by in 0f..bitmap.height.toFloat()) {
                                liveMarkupPoints.add(android.graphics.PointF(bx, by))
                            }
                        },
                        onDragEnd = {
                            if (liveMarkupPoints.size >= 2) {
                                currentOnCommitMarkupStroke(
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
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val fitScale = min(
                                currentContainerSize.width.toFloat() / bitmap.width,
                                currentContainerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * currentScale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                            val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

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
                                currentContainerSize.width.toFloat() / bitmap.width,
                                currentContainerSize.height.toFloat() / bitmap.height
                            )
                            val effectiveScale = fitScale * currentScale
                            val drawWidth = bitmap.width * effectiveScale
                            val drawHeight = bitmap.height * effectiveScale
                            val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                            val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                            val bx = (change.position.x - baseLeft) / effectiveScale
                            val by = (change.position.y - baseTop) / effectiveScale
                            if (bx in 0f..bitmap.width.toFloat() && by in 0f..bitmap.height.toFloat()) {
                                liveMarkupPoints.add(android.graphics.PointF(bx, by))
                            }
                        },
                        onDragEnd = {
                            if (liveMarkupPoints.size >= 2) {
                                currentOnCommitMagicEraserStroke(
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
                                    currentContainerSize.width.toFloat() / bitmap.width,
                                    currentContainerSize.height.toFloat() / bitmap.height
                                )
                                val effectiveScale = fitScale * currentScale
                                val drawWidth = bitmap.width * effectiveScale
                                val drawHeight = bitmap.height * effectiveScale
                                val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                                val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                                val sx = (start.x - baseLeft) / effectiveScale
                                val sy = (start.y - baseTop) / effectiveScale
                                val ex = (curr.x - baseLeft) / effectiveScale
                                val ey = (curr.y - baseTop) / effectiveScale

                                val left = kotlin.math.min(sx, ex).coerceIn(0f, bitmap.width.toFloat())
                                val top = kotlin.math.min(sy, ey).coerceIn(0f, bitmap.height.toFloat())
                                val right = kotlin.math.max(sx, ex).coerceIn(0f, bitmap.width.toFloat())
                                val bottom = kotlin.math.max(sy, ey).coerceIn(0f, bitmap.height.toFloat())

                                if (right - left > 6f && bottom - top > 6f) {
                                    currentOnCommitBlackoutRect(android.graphics.RectF(left, top, right, bottom))
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
                                    currentContainerSize.width.toFloat() / bitmap.width,
                                    currentContainerSize.height.toFloat() / bitmap.height
                                )
                                val effectiveScale = fitScale * currentScale
                                val drawWidth = bitmap.width * effectiveScale
                                val drawHeight = bitmap.height * effectiveScale
                                val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                                val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                                val sx = (start.x - baseLeft) / effectiveScale
                                val sy = (start.y - baseTop) / effectiveScale
                                val ex = (curr.x - baseLeft) / effectiveScale
                                val ey = (curr.y - baseTop) / effectiveScale

                                if (kotlin.math.hypot((ex - sx).toDouble(), (ey - sy).toDouble()) > 10.0) {
                                    currentOnCommitShape(
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
                    detectTapGestures { tapScreenOffset ->
                        if (currentContainerSize.width == 0 || currentContainerSize.height == 0) return@detectTapGestures
                        val fitScale = min(
                            currentContainerSize.width.toFloat() / bitmap.width,
                            currentContainerSize.height.toFloat() / bitmap.height
                        )
                        val effectiveScale = fitScale * currentScale
                        val drawWidth = bitmap.width * effectiveScale
                        val drawHeight = bitmap.height * effectiveScale
                        val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                        val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                        val bitmapX = (tapScreenOffset.x - baseLeft) / effectiveScale
                        val bitmapY = (tapScreenOffset.y - baseTop) / effectiveScale
                        if (bitmapX in 0f..bitmap.width.toFloat() && bitmapY in 0f..bitmap.height.toFloat()) {
                            currentOnInsertTextTouch(bitmapX, bitmapY)
                        }
                    }
                } else if (activeMode == EditorToolMode.LASSO_SELECT) {
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
                                    currentContainerSize.width.toFloat() / bitmap.width,
                                    currentContainerSize.height.toFloat() / bitmap.height
                                )
                                val effectiveScale = fitScale * currentScale
                                val drawWidth = bitmap.width * effectiveScale
                                val drawHeight = bitmap.height * effectiveScale
                                val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                                val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                                val minX = (minOf(start.x, end.x) - baseLeft) / effectiveScale
                                val maxX = (maxOf(start.x, end.x) - baseLeft) / effectiveScale
                                val minY = (minOf(start.y, end.y) - baseTop) / effectiveScale
                                val maxY = (maxOf(start.y, end.y) - baseTop) / effectiveScale

                                val selected = currentDetectedItems.filter { item ->
                                    val b = item.boundingBox
                                    b.left < maxX && b.right > minX && b.top < maxY && b.bottom > minY
                                }
                                currentOnLassoSelectionChanged(selected)
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
                    // Unified High-Precision Multi-Touch Engine for Canva Layers & Document Interaction
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startOffset = down.position
                        val fitScale = min(
                            currentContainerSize.width.toFloat() / bitmap.width,
                            currentContainerSize.height.toFloat() / bitmap.height
                        )
                        val effectiveScale = fitScale * currentScale
                        if (effectiveScale <= 0f) return@awaitEachGesture

                        val drawWidth = bitmap.width * effectiveScale
                        val drawHeight = bitmap.height * effectiveScale
                        val baseLeft = (currentContainerSize.width - drawWidth) / 2f + currentOffset.x
                        val baseTop = (currentContainerSize.height - drawHeight) / 2f + currentOffset.y

                        val docX = (startOffset.x - baseLeft) / effectiveScale
                        val docY = (startOffset.y - baseTop) / effectiveScale

                        var chosenHandle = CanvasHandleType.NONE
                        val currentSelected = currentCanvasLayers.firstOrNull { it.id == currentSelectedLayerId }

                        if (currentSelected != null) {
                            val lDrawW = currentSelected.bitmap.width * currentSelected.scale * effectiveScale
                            val lDrawH = currentSelected.bitmap.height * currentSelected.scale * effectiveScale
                            val lScreenX = baseLeft + (currentSelected.x * effectiveScale)
                            val lScreenY = baseTop + (currentSelected.y * effectiveScale)
                            val pivot = Offset(lScreenX + lDrawW / 2f, lScreenY + lDrawH / 2f)

                            val rad = -Math.toRadians(currentSelected.rotation.toDouble())
                            val cos = Math.cos(rad)
                            val sin = Math.sin(rad)
                            val dx = startOffset.x - pivot.x
                            val dy = startOffset.y - pivot.y
                            val unrotX = (pivot.x + (dx * cos - dy * sin)).toFloat()
                            val unrotY = (pivot.y + (dx * sin + dy * cos)).toFloat()

                            val handleRadius = 26.dp.toPx()
                            val rotPin = Offset(lScreenX + lDrawW / 2f, lScreenY - 26.dp.toPx())

                            when {
                                kotlin.math.hypot((unrotX - rotPin.x).toDouble(), (unrotY - rotPin.y).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.ROTATE
                                }
                                kotlin.math.hypot((unrotX - lScreenX).toDouble(), (unrotY - lScreenY).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_TOP_LEFT
                                }
                                kotlin.math.hypot((unrotX - (lScreenX + lDrawW)).toDouble(), (unrotY - lScreenY).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_TOP_RIGHT
                                }
                                kotlin.math.hypot((unrotX - lScreenX).toDouble(), (unrotY - (lScreenY + lDrawH)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_BOTTOM_LEFT
                                }
                                kotlin.math.hypot((unrotX - (lScreenX + lDrawW)).toDouble(), (unrotY - (lScreenY + lDrawH)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_BOTTOM_RIGHT
                                }
                                kotlin.math.hypot((unrotX - (lScreenX + lDrawW / 2f)).toDouble(), (unrotY - lScreenY).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_TOP
                                }
                                kotlin.math.hypot((unrotX - (lScreenX + lDrawW / 2f)).toDouble(), (unrotY - (lScreenY + lDrawH)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_BOTTOM
                                }
                                kotlin.math.hypot((unrotX - lScreenX).toDouble(), (unrotY - (lScreenY + lDrawH / 2f)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_LEFT
                                }
                                kotlin.math.hypot((unrotX - (lScreenX + lDrawW)).toDouble(), (unrotY - (lScreenY + lDrawH / 2f)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_RIGHT
                                }
                                unrotX in (lScreenX - 8f)..(lScreenX + lDrawW + 8f) && unrotY in (lScreenY - 8f)..(lScreenY + lDrawH + 8f) -> {
                                    chosenHandle = CanvasHandleType.BODY
                                }
                            }
                        } else if (currentActiveOverlayBitmap != null) {
                            val oDrawW = currentActiveOverlayBitmap!!.width * currentOverlayScale * effectiveScale
                            val oDrawH = currentActiveOverlayBitmap!!.height * currentOverlayScale * effectiveScale
                            val oScreenX = baseLeft + (currentOverlayPositionX * effectiveScale)
                            val oScreenY = baseTop + (currentOverlayPositionY * effectiveScale)
                            val pivot = Offset(oScreenX + oDrawW / 2f, oScreenY + oDrawH / 2f)

                            val rad = -Math.toRadians(currentOverlayRotation.toDouble())
                            val cos = Math.cos(rad)
                            val sin = Math.sin(rad)
                            val dx = startOffset.x - pivot.x
                            val dy = startOffset.y - pivot.y
                            val unrotX = (pivot.x + (dx * cos - dy * sin)).toFloat()
                            val unrotY = (pivot.y + (dx * sin + dy * cos)).toFloat()

                            val handleRadius = 26.dp.toPx()
                            val rotPin = Offset(oScreenX + oDrawW / 2f, oScreenY - 26.dp.toPx())

                            when {
                                kotlin.math.hypot((unrotX - rotPin.x).toDouble(), (unrotY - rotPin.y).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.ROTATE
                                }
                                kotlin.math.hypot((unrotX - oScreenX).toDouble(), (unrotY - oScreenY).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_TOP_LEFT
                                }
                                kotlin.math.hypot((unrotX - (oScreenX + oDrawW)).toDouble(), (unrotY - oScreenY).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_TOP_RIGHT
                                }
                                kotlin.math.hypot((unrotX - oScreenX).toDouble(), (unrotY - (oScreenY + oDrawH)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_BOTTOM_LEFT
                                }
                                kotlin.math.hypot((unrotX - (oScreenX + oDrawW)).toDouble(), (unrotY - (oScreenY + oDrawH)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.CORNER_BOTTOM_RIGHT
                                }
                                kotlin.math.hypot((unrotX - (oScreenX + oDrawW / 2f)).toDouble(), (unrotY - oScreenY).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_TOP
                                }
                                kotlin.math.hypot((unrotX - (oScreenX + oDrawW / 2f)).toDouble(), (unrotY - (oScreenY + oDrawH)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_BOTTOM
                                }
                                kotlin.math.hypot((unrotX - oScreenX).toDouble(), (unrotY - (oScreenY + oDrawH / 2f)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_LEFT
                                }
                                kotlin.math.hypot((unrotX - (oScreenX + oDrawW)).toDouble(), (unrotY - (oScreenY + oDrawH / 2f)).toDouble()) <= handleRadius -> {
                                    chosenHandle = CanvasHandleType.EDGE_RIGHT
                                }
                                unrotX in (oScreenX - 8f)..(oScreenX + oDrawW + 8f) && unrotY in (oScreenY - 8f)..(oScreenY + oDrawH + 8f) -> {
                                    chosenHandle = CanvasHandleType.BODY
                                }
                            }
                        }

                        if (chosenHandle == CanvasHandleType.NONE) {
                            val hitOther = currentCanvasLayers.asReversed().firstOrNull { it.hitTest(docX, docY) }
                            if (hitOther != null) {
                                currentOnSelectLayer(hitOther.id)
                                chosenHandle = CanvasHandleType.BODY
                            }
                        }

                        activeHandle = chosenHandle

                        var isDrag = false
                        var totalPan = Offset.Zero
                        var lastPos = startOffset
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                // Multi-touch detected (e.g. 2 fingers). Break out so transformable can pan/zoom canvas viewport
                                break
                            }
                            val change = event.changes.firstOrNull() ?: break
                            if (change.changedToUp()) {
                                // Finger lifted!
                                if (!isDrag) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTime < 320L && kotlin.math.hypot((startOffset.x - lastTapPosition.x).toDouble(), (startOffset.y - lastTapPosition.y).toDouble()) < 48.0) {
                                        // Double Tap Zoom Toggle
                                        if (scale > 1.1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.2f
                                        }
                                        lastTapTime = 0L
                                    } else {
                                        lastTapTime = now
                                        lastTapPosition = startOffset
                                        if (chosenHandle == CanvasHandleType.NONE) {
                                            if (currentSelectedLayerId != null) {
                                                currentOnSelectLayer(null)
                                            } else {
                                                // Check text hit
                                                var hitItem = currentDetectedItems.firstOrNull { item ->
                                                    item.boundingBox.contains(docX.toInt(), docY.toInt())
                                                }
                                                if (hitItem == null && currentDetectedItems.isNotEmpty()) {
                                                    val snapRadiusPx = 32.dp.toPx() / effectiveScale
                                                    var closestDistance = Float.MAX_VALUE
                                                    for (item in currentDetectedItems) {
                                                        val b = item.boundingBox
                                                        val ddx = when {
                                                            docX < b.left -> b.left - docX
                                                            docX > b.right -> docX - b.right
                                                            else -> 0f
                                                        }
                                                        val ddy = when {
                                                            docY < b.top -> b.top - docY
                                                            docY > b.bottom -> docY - b.bottom
                                                            else -> 0f
                                                        }
                                                        val dist = kotlin.math.hypot(ddx, ddy)
                                                        if (dist <= snapRadiusPx && dist < closestDistance) {
                                                            closestDistance = dist
                                                            hitItem = item
                                                        }
                                                    }
                                                }
                                                if (hitItem != null) {
                                                    currentOnTextItemTapped(hitItem)
                                                } else {
                                                    if (docX in 0f..bitmap.width.toFloat() && docY in 0f..bitmap.height.toFloat()) {
                                                        currentOnInsertTextTouch(docX, docY)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                break
                            }

                            val dragDelta = change.positionChange()
                            totalPan += dragDelta
                            val currPos = change.position

                            if (!isDrag && totalPan.getDistance() > touchSlop) {
                                isDrag = true
                            }

                            if (isDrag && chosenHandle != CanvasHandleType.NONE) {
                                change.consume()
                                val curActive = currentCanvasLayers.firstOrNull { it.id == currentSelectedLayerId }
                                val lDrawW = (curActive?.bitmap?.width ?: currentActiveOverlayBitmap?.width ?: 100) * (curActive?.scale ?: currentOverlayScale) * effectiveScale
                                val lDrawH = (curActive?.bitmap?.height ?: currentActiveOverlayBitmap?.height ?: 100) * (curActive?.scale ?: currentOverlayScale) * effectiveScale
                                val lScreenX = baseLeft + ((curActive?.x ?: currentOverlayPositionX) * effectiveScale)
                                val lScreenY = baseTop + ((curActive?.y ?: currentOverlayPositionY) * effectiveScale)
                                val pivot = Offset(lScreenX + lDrawW / 2f, lScreenY + lDrawH / 2f)

                                when (chosenHandle) {
                                    CanvasHandleType.BODY -> {
                                        val deltaDocX = dragDelta.x / effectiveScale
                                        val deltaDocY = dragDelta.y / effectiveScale
                                        if (curActive != null) {
                                            var candX = curActive.x + deltaDocX
                                            var candY = curActive.y + deltaDocY
                                            val lW = curActive.bitmap.width * curActive.scale
                                            val lH = curActive.bitmap.height * curActive.scale
                                            val lCenterX = candX + lW / 2f
                                            val lCenterY = candY + lH / 2f

                                            val snapDist = 14f / effectiveScale.coerceAtLeast(0.2f)
                                            val docCenterX = bitmap.width / 2f
                                            val docCenterY = bitmap.height / 2f
                                            val docMarginL = bitmap.width * 0.05f
                                            val docMarginR = bitmap.width * 0.95f
                                            val docMarginT = bitmap.height * 0.05f
                                            val docMarginB = bitmap.height * 0.95f

                                            val guides = mutableListOf<SnapGuideLine>()
                                            var snappedX = false
                                            var snappedY = false

                                            // Snap Horizontal Center & Margins
                                            if (kotlin.math.abs(lCenterX - docCenterX) <= snapDist) {
                                                candX = docCenterX - lW / 2f
                                                guides.add(SnapGuideLine(isVertical = true, position = docCenterX, label = "Center"))
                                                snappedX = true
                                            } else if (kotlin.math.abs(candX - docMarginL) <= snapDist) {
                                                candX = docMarginL
                                                guides.add(SnapGuideLine(isVertical = true, position = docMarginL, label = "Margin"))
                                                snappedX = true
                                            } else if (kotlin.math.abs(candX + lW - docMarginR) <= snapDist) {
                                                candX = docMarginR - lW
                                                guides.add(SnapGuideLine(isVertical = true, position = docMarginR, label = "Margin"))
                                                snappedX = true
                                            }

                                            // Snap Vertical Center & Margins
                                            if (kotlin.math.abs(lCenterY - docCenterY) <= snapDist) {
                                                candY = docCenterY - lH / 2f
                                                guides.add(SnapGuideLine(isVertical = false, position = docCenterY, label = "Center"))
                                                snappedY = true
                                            } else if (kotlin.math.abs(candY - docMarginT) <= snapDist) {
                                                candY = docMarginT
                                                guides.add(SnapGuideLine(isVertical = false, position = docMarginT, label = "Margin"))
                                                snappedY = true
                                            } else if (kotlin.math.abs(candY + lH - docMarginB) <= snapDist) {
                                                candY = docMarginB - lH
                                                guides.add(SnapGuideLine(isVertical = false, position = docMarginB, label = "Margin"))
                                                snappedY = true
                                            }

                                            if (snappedX || snappedY) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }

                                            snapGuides.clear()
                                            snapGuides.addAll(guides)

                                            val finalDeltaX = candX - curActive.x
                                            val finalDeltaY = candY - curActive.y
                                            currentOnOverlayDragged(finalDeltaX, finalDeltaY)
                                        } else {
                                            currentOnOverlayDragged(deltaDocX, deltaDocY)
                                        }
                                    }
                                    CanvasHandleType.CORNER_TOP_LEFT,
                                    CanvasHandleType.CORNER_TOP_RIGHT,
                                    CanvasHandleType.CORNER_BOTTOM_LEFT,
                                    CanvasHandleType.CORNER_BOTTOM_RIGHT -> {
                                        val prevDist = kotlin.math.hypot(
                                            (lastPos.x - pivot.x).toDouble(),
                                            (lastPos.y - pivot.y).toDouble()
                                        ).toFloat().coerceAtLeast(10f)
                                        val currDist = kotlin.math.hypot(
                                            (currPos.x - pivot.x).toDouble(),
                                            (currPos.y - pivot.y).toDouble()
                                        ).toFloat().coerceAtLeast(10f)
                                        val multiplier = (currDist / prevDist).coerceIn(0.85f, 1.18f)
                                        currentOnOverlayScaleChanged(multiplier)
                                    }
                                    CanvasHandleType.EDGE_TOP,
                                    CanvasHandleType.EDGE_BOTTOM -> {
                                        val prevDy = kotlin.math.abs(lastPos.y - pivot.y).coerceAtLeast(10f)
                                        val currDy = kotlin.math.abs(currPos.y - pivot.y).coerceAtLeast(10f)
                                        val multiplier = (currDy / prevDy).coerceIn(0.88f, 1.15f)
                                        currentOnOverlayScaleChanged(multiplier)
                                    }
                                    CanvasHandleType.EDGE_LEFT,
                                    CanvasHandleType.EDGE_RIGHT -> {
                                        val prevDx = kotlin.math.abs(lastPos.x - pivot.x).coerceAtLeast(10f)
                                        val currDx = kotlin.math.abs(currPos.x - pivot.x).coerceAtLeast(10f)
                                        val multiplier = (currDx / prevDx).coerceIn(0.88f, 1.15f)
                                        currentOnOverlayScaleChanged(multiplier)
                                    }
                                    CanvasHandleType.ROTATE -> {
                                        val prevAngle = Math.toDegrees(
                                            kotlin.math.atan2(
                                                (lastPos.y - pivot.y).toDouble(),
                                                (lastPos.x - pivot.x).toDouble()
                                            )
                                        ).toFloat()
                                        val currAngle = Math.toDegrees(
                                            kotlin.math.atan2(
                                                (currPos.y - pivot.y).toDouble(),
                                                (currPos.x - pivot.x).toDouble()
                                            )
                                        ).toFloat()
                                        val deltaAngle = currAngle - prevAngle
                                        val baseRot = curActive?.rotation ?: currentOverlayRotation
                                        var targetRot = (baseRot + deltaAngle) % 360f
                                        if (targetRot < 0f) targetRot += 360f

                                        // Magnetic Cardinal Angles: 0, 45, 90, 135, 180, 225, 270, 315, 360
                                        val snapAngles = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)
                                        var angleSnapped = false
                                        for (snapA in snapAngles) {
                                            if (kotlin.math.abs(targetRot - snapA) < 2.5f || (snapA == 0f && kotlin.math.abs(targetRot - 360f) < 2.5f)) {
                                                targetRot = if (snapA == 360f) 0f else snapA
                                                angleSnapped = true
                                                break
                                            }
                                        }
                                        if (angleSnapped) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        currentOnOverlayRotateChanged(targetRot)
                                    }
                                    CanvasHandleType.NONE -> {}
                                }

                                // Update Live HUD dimensions & angle pill
                                if (curActive != null) {
                                    val curW = (curActive.bitmap.width * curActive.scale).toInt()
                                    val curH = (curActive.bitmap.height * curActive.scale).toInt()
                                    val curRot = curActive.rotation.toInt()
                                    hudDimensions = "📐 W: $curW • H: $curH • $curRot°"
                                    val hudCenterX = (lScreenX + lDrawW / 2f).coerceIn(80f, currentContainerSize.width.toFloat() - 80f)
                                    val hudCenterY = (lScreenY - 50.dp.toPx()).coerceAtLeast(20.dp.toPx())
                                    hudPosition = Offset(hudCenterX - 65.dp.toPx(), hudCenterY)
                                } else if (currentActiveOverlayBitmap != null) {
                                    val curW = (currentActiveOverlayBitmap!!.width * currentOverlayScale).toInt()
                                    val curH = (currentActiveOverlayBitmap!!.height * currentOverlayScale).toInt()
                                    val curRot = currentOverlayRotation.toInt()
                                    hudDimensions = "📐 W: $curW • H: $curH • $curRot°"
                                    val hudCenterX = (lScreenX + lDrawW / 2f).coerceIn(80f, currentContainerSize.width.toFloat() - 80f)
                                    val hudCenterY = (lScreenY - 50.dp.toPx()).coerceAtLeast(20.dp.toPx())
                                    hudPosition = Offset(hudCenterX - 65.dp.toPx(), hudCenterY)
                                }
                            }

                            lastPos = currPos
                        }

                        // Drag finished or cancelled
                        snapGuides.clear()
                        activeHandle = CanvasHandleType.NONE
                        hudDimensions = null
                        hudPosition = null
                    }
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
                    else -> {
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
                        val scaleX = if (layer.flipH) -1f else 1f
                        val scaleY = if (layer.flipV) -1f else 1f
                        drawScopeScale(scaleX = scaleX, scaleY = scaleY, pivot = pivot) {
                            drawImage(
                                image = layer.bitmap.asImageBitmap(),
                                dstOffset = IntOffset(lScreenX.toInt(), lScreenY.toInt()),
                                dstSize = IntSize(lDrawW, lDrawH),
                                alpha = layer.alpha
                            )
                        }

                        if (isSelected) {
                            val strokeW = 2.dp.toPx()
                            val canvaPurple = Color(0xFF6366F1)
                            val glowColor = Color(0xFF818CF8).copy(alpha = 0.35f)

                            // Outer Glow + Inner Stroke
                            drawRect(
                                color = glowColor,
                                topLeft = Offset(lScreenX - 1.5.dp.toPx(), lScreenY - 1.5.dp.toPx()),
                                size = Size(lDrawW.toFloat() + 3.dp.toPx(), lDrawH.toFloat() + 3.dp.toPx()),
                                style = Stroke(width = 4.dp.toPx())
                            )
                            drawRect(
                                color = canvaPurple,
                                topLeft = Offset(lScreenX, lScreenY),
                                size = Size(lDrawW.toFloat(), lDrawH.toFloat()),
                                style = Stroke(width = strokeW)
                            )

                            // 4 Corner Proportional Scaler Anchors
                            val cornerRadius = 7.dp.toPx()
                            val corners = listOf(
                                Offset(lScreenX, lScreenY),
                                Offset(lScreenX + lDrawW, lScreenY),
                                Offset(lScreenX, lScreenY + lDrawH),
                                Offset(lScreenX + lDrawW, lScreenY + lDrawH)
                            )
                            corners.forEach { cornerPt ->
                                drawCircle(color = Color.White, radius = cornerRadius, center = cornerPt)
                                drawCircle(color = canvaPurple, radius = cornerRadius, center = cornerPt, style = Stroke(width = 2.5.dp.toPx()))
                            }

                            // 4 Directional Edge Stretch Pills
                            val pillLen = 16.dp.toPx()
                            val pillThick = 5.dp.toPx()

                            // Top Edge
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(lScreenX + lDrawW / 2f - pillLen / 2f, lScreenY - pillThick / 2f),
                                size = Size(pillLen, pillThick),
                                cornerRadius = CornerRadius(pillThick / 2f)
                            )
                            drawRoundRect(
                                color = canvaPurple,
                                topLeft = Offset(lScreenX + lDrawW / 2f - pillLen / 2f, lScreenY - pillThick / 2f),
                                size = Size(pillLen, pillThick),
                                cornerRadius = CornerRadius(pillThick / 2f),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            // Bottom Edge
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(lScreenX + lDrawW / 2f - pillLen / 2f, lScreenY + lDrawH - pillThick / 2f),
                                size = Size(pillLen, pillThick),
                                cornerRadius = CornerRadius(pillThick / 2f)
                            )
                            drawRoundRect(
                                color = canvaPurple,
                                topLeft = Offset(lScreenX + lDrawW / 2f - pillLen / 2f, lScreenY + lDrawH - pillThick / 2f),
                                size = Size(pillLen, pillThick),
                                cornerRadius = CornerRadius(pillThick / 2f),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            // Left Edge
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(lScreenX - pillThick / 2f, lScreenY + lDrawH / 2f - pillLen / 2f),
                                size = Size(pillThick, pillLen),
                                cornerRadius = CornerRadius(pillThick / 2f)
                            )
                            drawRoundRect(
                                color = canvaPurple,
                                topLeft = Offset(lScreenX - pillThick / 2f, lScreenY + lDrawH / 2f - pillLen / 2f),
                                size = Size(pillThick, pillLen),
                                cornerRadius = CornerRadius(pillThick / 2f),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            // Right Edge
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(lScreenX + lDrawW - pillThick / 2f, lScreenY + lDrawH / 2f - pillLen / 2f),
                                size = Size(pillThick, pillLen),
                                cornerRadius = CornerRadius(pillThick / 2f)
                            )
                            drawRoundRect(
                                color = canvaPurple,
                                topLeft = Offset(lScreenX + lDrawW - pillThick / 2f, lScreenY + lDrawH / 2f - pillLen / 2f),
                                size = Size(pillThick, pillLen),
                                cornerRadius = CornerRadius(pillThick / 2f),
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            // Top Rotate Stem & Magnetic Knob
                            val rotStem = 26.dp.toPx()
                            val rotTop = Offset(lScreenX + lDrawW / 2f, lScreenY - rotStem)
                            val rotBottom = Offset(lScreenX + lDrawW / 2f, lScreenY)
                            drawLine(color = canvaPurple, start = rotBottom, end = rotTop, strokeWidth = strokeW)
                            drawCircle(color = Color.White, radius = cornerRadius, center = rotTop)
                            drawCircle(color = canvaPurple, radius = cornerRadius, center = rotTop, style = Stroke(width = 2.5.dp.toPx()))
                            drawCircle(color = canvaPurple, radius = 3.dp.toPx(), center = rotTop)
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

                    val strokeW = 2.dp.toPx()
                    val stampBlue = Color(0xFF2563EB)
                    val glowColor = Color(0xFF60A5FA).copy(alpha = 0.35f)

                    drawRect(
                        color = glowColor,
                        topLeft = Offset(overlayScreenX - 1.5.dp.toPx(), overlayScreenY - 1.5.dp.toPx()),
                        size = Size(overlayDrawW.toFloat() + 3.dp.toPx(), overlayDrawH.toFloat() + 3.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawRect(
                        color = stampBlue,
                        topLeft = Offset(overlayScreenX, overlayScreenY),
                        size = Size(overlayDrawW.toFloat(), overlayDrawH.toFloat()),
                        style = Stroke(width = strokeW)
                    )

                    val cornerRadius = 6.dp.toPx()
                    val corners = listOf(
                        Offset(overlayScreenX, overlayScreenY),
                        Offset(overlayScreenX + overlayDrawW, overlayScreenY),
                        Offset(overlayScreenX, overlayScreenY + overlayDrawH),
                        Offset(overlayScreenX + overlayDrawW, overlayScreenY + overlayDrawH)
                    )
                    corners.forEach { cornerPt ->
                        drawCircle(color = Color.White, radius = cornerRadius, center = cornerPt)
                        drawCircle(color = stampBlue, radius = cornerRadius, center = cornerPt, style = Stroke(width = 2.dp.toPx()))
                    }

                    // Top Rotate Stem
                    val rotStem = 24.dp.toPx()
                    val rotTop = Offset(overlayScreenX + overlayDrawW / 2f, overlayScreenY - rotStem)
                    val rotBottom = Offset(overlayScreenX + overlayDrawW / 2f, overlayScreenY)
                    drawLine(color = stampBlue, start = rotBottom, end = rotTop, strokeWidth = strokeW)
                    drawCircle(color = Color.White, radius = cornerRadius, center = rotTop)
                    drawCircle(color = stampBlue, radius = cornerRadius, center = rotTop, style = Stroke(width = 2.dp.toPx()))
                }
            }

            // Draw Magnetic Snapping Alignment Guidelines (CamScanner & Canva Pro)
            snapGuides.forEach { guide ->
                if (guide.isVertical) {
                    val screenX = baseLeft + (guide.position * effectiveScale)
                    drawLine(
                        color = Color(0xFFA855F7), // Neon Purple snap line
                        start = Offset(screenX, baseTop),
                        end = Offset(screenX, baseTop + drawHeight),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                } else {
                    val screenY = baseTop + (guide.position * effectiveScale)
                    drawLine(
                        color = Color(0xFFA855F7),
                        start = Offset(baseLeft, screenY),
                        end = Offset(baseLeft + drawWidth, screenY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                }
            }
        }

        // Live Canva Floating Dimension & Angle HUD Pill
        hudDimensions?.let { hudText ->
            hudPosition?.let { pos ->
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.6f)),
                    modifier = Modifier.offset { IntOffset(pos.x.toInt(), pos.y.toInt()) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = hudText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
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
                    val dispScale = curLayer?.scale ?: overlayScale
                    val dispRot = curLayer?.rotation ?: overlayRotation
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

                        Text("Scale: ${(dispScale * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))

                        // Size Stepper: Increase
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = { onOverlayScaleChanged(1.15f) }) {
                                Icon(Icons.Default.Add, contentDescription = "Larger", tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
                            }
                        }

                        // Rotation: Rotate Left
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = { onOverlayRotateChanged((dispRot - 5f) % 360f) }) {
                                Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Tilt Left", tint = Color(0xFF0F172A), modifier = Modifier.size(15.dp))
                            }
                        }

                        Text("${dispRot.toInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))

                        // Rotation: Rotate Right
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(32.dp)) {
                            IconButton(onClick = { onOverlayRotateChanged((dispRot + 5f) % 360f) }) {
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

enum class CanvasHandleType {
    NONE,
    BODY,
    CORNER_TOP_LEFT,
    CORNER_TOP_RIGHT,
    CORNER_BOTTOM_LEFT,
    CORNER_BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    ROTATE
}

data class SnapGuideLine(
    val isVertical: Boolean,
    val position: Float,
    val label: String = ""
)
