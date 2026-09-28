package com.docu.editor.ui.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.docu.editor.core.ocr.model.DetectedTextItem
import kotlin.math.min

@Composable
fun DocumentInteractiveCanvas(
    bitmap: Bitmap,
    detectedItems: List<DetectedTextItem>,
    selectedItem: DetectedTextItem?,
    onTextItemTapped: (DetectedTextItem) -> Unit,
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
            .background(Color(0xFF1E1E1E))
            .onSizeChanged { containerSize = it }
            .transformable(state = transformState)
            .pointerInput(bitmap, detectedItems, containerSize, scale, offset) {
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
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width == 0f || size.height == 0f) return@Canvas

            val fitScale = min(size.width / bitmap.width, size.height / bitmap.height)
            val effectiveScale = fitScale * scale

            val drawWidth = bitmap.width * effectiveScale
            val drawHeight = bitmap.height * effectiveScale

            val baseLeft = (size.width - drawWidth) / 2f + offset.x
            val baseTop = (size.height - drawHeight) / 2f + offset.y

            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(baseLeft.toInt(), baseTop.toInt()),
                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
            )

            for (item in detectedItems) {
                val isSelected = item.id == selectedItem?.id
                val box = item.boundingBox

                val boxLeft = baseLeft + (box.left * effectiveScale)
                val boxTop = baseTop + (box.top * effectiveScale)
                val boxWidth = box.width() * effectiveScale
                val boxHeight = box.height() * effectiveScale

                drawRect(
                    color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.35f)
                            else Color(0xFF2979FF).copy(alpha = 0.15f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight)
                )

                drawRect(
                    color = if (isSelected) Color(0xFF00E676) else Color(0xFF2979FF).copy(alpha = 0.6f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = if (isSelected) 3.5f else 1.5f)
                )
            }
        }
    }
}
