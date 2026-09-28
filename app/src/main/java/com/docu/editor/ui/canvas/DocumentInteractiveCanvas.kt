package com.docu.editor.ui.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
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
    activeMode: EditorToolMode = EditorToolMode.TEXT_EDIT,
    onTextItemTapped: (DetectedTextItem) -> Unit,
    onWhiteoutTouch: (bitmapX: Float, bitmapY: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 6.0f)
        offset += panChange
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .onSizeChanged { containerSize = it }
            .transformable(state = transformState)
            .pointerInput(bitmap, detectedItems, containerSize, scale, offset, activeMode) {
                if (activeMode == EditorToolMode.WHITEOUT) {
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
                } else {
                    // Normal text editing mode: tap to select text item
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

                        val relX = tapScreenOffset.x - baseLeft
                        val relY = tapScreenOffset.y - baseTop

                        val bitmapX = relX / effectiveScale
                        val bitmapY = relY / effectiveScale

                        val hitItem = detectedItems.firstOrNull { item ->
                            item.boundingBox.contains(bitmapX.toInt(), bitmapY.toInt())
                        }

                        if (hitItem != null) {
                            onTextItemTapped(hitItem)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width == 0f || size.height == 0f) return@Canvas

            val fitScale = min(size.width / bitmap.width, size.height / bitmap.height)
            val effectiveScale = fitScale * scale

            val drawWidth = bitmap.width * effectiveScale
            val drawHeight = bitmap.height * effectiveScale

            val baseLeft = (size.width - drawWidth) / 2f + offset.x
            val baseTop = (size.height - drawHeight) / 2f + offset.y

            // 1. Draw clean document bitmap
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(baseLeft.toInt(), baseTop.toInt()),
                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
            )

            // 2. Draw bounding indicators ONLY in TEXT_EDIT mode (hidden in Whiteout mode so user sees pure document)
            if (activeMode == EditorToolMode.TEXT_EDIT) {
                for (item in detectedItems) {
                    val isSelected = item.id == selectedItem?.id
                    val box = item.boundingBox

                    val boxLeft = baseLeft + (box.left * effectiveScale)
                    val boxTop = baseTop + (box.top * effectiveScale)
                    val boxWidth = box.width() * effectiveScale
                    val boxHeight = box.height() * effectiveScale

                    if (isSelected) {
                        // Selected: vibrant glowing highlight
                        drawRect(
                            color = Color(0xFF00E5FF).copy(alpha = 0.22f),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight)
                        )
                        drawRect(
                            color = Color(0xFF00E5FF),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight),
                            style = Stroke(width = 3.5f)
                        )
                    } else {
                        // Non-selected: ultra-subtle, non-intrusive dotted boundary (NO opaque fill!)
                        drawRect(
                            color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxWidth, boxHeight),
                            style = Stroke(width = 1.2f)
                        )
                    }
                }
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
    }
}
