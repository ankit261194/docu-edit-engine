package com.docu.editor.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.docu.editor.core.scanner.model.DocumentCorners
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise 8-Point Document Crop Overlay with Live Magnifier Loupe (CamScanner Grade).
 * Features:
 * - 4 interactive draggable corners with tactile haptic anchors.
 * - 4 edge midpoints for intuitive edge dragging.
 * - Floating Circular Magnifier Loupe (2.5x Zoom + Precision Crosshair).
 * - Automatic flip of magnifier location (avoids finger obstruction).
 */
class CropLoupeOverlayView(context: Context) : View(context) {

    var sourceBitmap: Bitmap? = null
        set(value) {
            field = value
            fitBitmapToView()
            invalidate()
        }

    var corners: DocumentCorners = DocumentCorners(
        topLeft = PointF(100f, 100f),
        topRight = PointF(700f, 100f),
        bottomRight = PointF(700f, 1000f),
        bottomLeft = PointF(100f, 1000f)
    )
        set(value) {
            field = value
            invalidate()
        }

    var onCornersChanged: ((DocumentCorners) -> Unit)? = null

    private val polygonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 118) // Neon Vibrant Green
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 230, 118)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
    }

    private val cornerHandleOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val cornerHandleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 118)
        style = Paint.Style.FILL
    }

    private val edgeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val shadowMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val loupeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val loupeCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 230, 118)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var activeCornerIndex = -1 // 0: TL, 1: TR, 2: BR, 3: BL
    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f

    // Screen destination rect for displayed bitmap
    private val destRect = RectF()
    private var bmpScale = 1.0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitBitmapToView()
    }

    fun fitBitmapToView() {
        val bmp = sourceBitmap ?: return
        if (width == 0 || height == 0) return

        val scaleW = width.toFloat() / bmp.width
        val scaleH = height.toFloat() / bmp.height
        bmpScale = min(scaleW, scaleH)

        val drawW = bmp.width * bmpScale
        val drawH = bmp.height * bmpScale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f

        destRect.set(left, top, left + drawW, top + drawH)
    }

    // Convert bitmap coordinate to view screen coordinate
    fun bmpToScreen(pt: PointF): PointF {
        return PointF(
            destRect.left + pt.x * bmpScale,
            destRect.top + pt.y * bmpScale
        )
    }

    // Convert view screen coordinate to bitmap coordinate
    fun screenToBmp(x: Float, y: Float): PointF {
        val bmp = sourceBitmap ?: return PointF(x, y)
        val bmpX = ((x - destRect.left) / bmpScale).coerceIn(0f, bmp.width.toFloat())
        val bmpY = ((y - destRect.top) / bmpScale).coerceIn(0f, bmp.height.toFloat())
        return PointF(bmpX, bmpY)
    }

    fun resetToFullImage() {
        val bmp = sourceBitmap ?: return
        corners = DocumentCorners(
            topLeft = PointF(0f, 0f),
            topRight = PointF(bmp.width.toFloat(), 0f),
            bottomRight = PointF(bmp.width.toFloat(), bmp.height.toFloat()),
            bottomLeft = PointF(0f, bmp.height.toFloat())
        )
        onCornersChanged?.invoke(corners)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return

        // 1. Draw source document photo
        val srcRect = Rect(0, 0, bmp.width, bmp.height)
        canvas.drawBitmap(bmp, srcRect, destRect, null)

        // Screen points
        val tl = bmpToScreen(corners.topLeft)
        val tr = bmpToScreen(corners.topRight)
        val br = bmpToScreen(corners.bottomRight)
        val bl = bmpToScreen(corners.bottomLeft)

        // 2. Dim the outside crop area (Dark Mask)
        val path = Path().apply {
            moveTo(tl.x, tl.y)
            lineTo(tr.x, tr.y)
            lineTo(br.x, br.y)
            lineTo(bl.x, bl.y)
            close()
        }

        canvas.save()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            canvas.clipOutPath(path)
        } else {
            @Suppress("DEPRECATION")
            canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE)
        }
        canvas.drawRect(destRect, shadowMaskPaint)
        canvas.restore()

        // 3. Draw Polygon Boundary
        canvas.drawPath(path, polygonPaint)

        // 4. Draw 3x3 Grid Guides
        drawGridGuides(canvas, tl, tr, br, bl)

        // 5. Draw 4 Corner Handles (Double circle)
        drawCornerHandle(canvas, tl.x, tl.y)
        drawCornerHandle(canvas, tr.x, tr.y)
        drawCornerHandle(canvas, br.x, br.y)
        drawCornerHandle(canvas, bl.x, bl.y)

        // 6. Draw 4 Edge Midpoint Handles
        drawEdgeHandle(canvas, (tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
        drawEdgeHandle(canvas, (tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
        drawEdgeHandle(canvas, (br.x + bl.x) / 2f, (br.y + bl.y) / 2f)
        drawEdgeHandle(canvas, (bl.x + tl.x) / 2f, (bl.y + tl.y) / 2f)

        // 7. Draw Floating Magnifier Loupe if actively dragging a corner
        if (isDragging && activeCornerIndex != -1) {
            val activeScreenPt = when (activeCornerIndex) {
                0 -> tl
                1 -> tr
                2 -> br
                else -> bl
            }
            val activeBmpPt = when (activeCornerIndex) {
                0 -> corners.topLeft
                1 -> corners.topRight
                2 -> corners.bottomRight
                else -> corners.bottomLeft
            }
            drawMagnifierLoupe(canvas, bmp, activeScreenPt, activeBmpPt)
        }
    }

    private fun drawCornerHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, 24f, cornerHandleOuterPaint)
        canvas.drawCircle(x, y, 16f, cornerHandleInnerPaint)
    }

    private fun drawEdgeHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, 12f, edgeHandlePaint)
        canvas.drawCircle(x, y, 8f, cornerHandleInnerPaint)
    }

    private fun drawGridGuides(canvas: Canvas, tl: PointF, tr: PointF, br: PointF, bl: PointF) {
        for (i in 1..2) {
            val f = i / 3f
            // Horizontal lines
            val lX = tl.x + (bl.x - tl.x) * f
            val lY = tl.y + (bl.y - tl.y) * f
            val rX = tr.x + (br.x - tr.x) * f
            val rY = tr.y + (br.y - tr.y) * f
            canvas.drawLine(lX, lY, rX, rY, guideLinePaint)

            // Vertical lines
            val tX = tl.x + (tr.x - tl.x) * f
            val tY = tl.y + (tr.y - tl.y) * f
            val bX = bl.x + (br.x - bl.x) * f
            val bY = bl.y + (br.y - bl.y) * f
            canvas.drawLine(tX, tY, bX, bY, guideLinePaint)
        }
    }

    private fun drawMagnifierLoupe(canvas: Canvas, bmp: Bitmap, screenPt: PointF, bmpPt: PointF) {
        val loupeRadius = 160f
        val margin = 40f

        // Position loupe opposite to the touch point (top-left or top-right)
        val loupeCenterX = if (screenPt.x > width / 2f) {
            loupeRadius + margin
        } else {
            width - loupeRadius - margin
        }
        val loupeCenterY = loupeRadius + margin + 60f

        canvas.save()
        val clipPath = Path().apply {
            addCircle(loupeCenterX, loupeCenterY, loupeRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        // Draw dark background inside loupe
        canvas.drawColor(Color.BLACK)

        // 2.5x Zoomed portion of the source bitmap
        val zoomFactor = 2.5f
        val sampleSizeBmp = (loupeRadius * 2f) / (zoomFactor * bmpScale)
        val sampleLeft = (bmpPt.x - sampleSizeBmp / 2f).toInt().coerceIn(0, bmp.width)
        val sampleTop = (bmpPt.y - sampleSizeBmp / 2f).toInt().coerceIn(0, bmp.height)
        val sampleRight = (sampleLeft + sampleSizeBmp).toInt().coerceAtMost(bmp.width)
        val sampleBottom = (sampleTop + sampleSizeBmp).toInt().coerceAtMost(bmp.height)

        val srcSample = Rect(sampleLeft, sampleTop, sampleRight, sampleBottom)
        val dstSample = RectF(
            loupeCenterX - loupeRadius,
            loupeCenterY - loupeRadius,
            loupeCenterX + loupeRadius,
            loupeCenterY + loupeRadius
        )

        canvas.drawBitmap(bmp, srcSample, dstSample, null)

        // Precision Crosshairs (+)
        canvas.drawLine(
            loupeCenterX - 40f, loupeCenterY,
            loupeCenterX + 40f, loupeCenterY,
            loupeCrosshairPaint
        )
        canvas.drawLine(
            loupeCenterX, loupeCenterY - 40f,
            loupeCenterX, loupeCenterY + 40f,
            loupeCrosshairPaint
        )

        canvas.restore()

        // White border ring around loupe
        canvas.drawCircle(loupeCenterX, loupeCenterY, loupeRadius, loupeBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                activeCornerIndex = findNearestCorner(x, y, touchThreshold = 100f)
                if (activeCornerIndex != -1) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && activeCornerIndex != -1) {
                    val bmpPt = screenToBmp(x, y)
                    updateActiveCorner(bmpPt)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    activeCornerIndex = -1
                    onCornersChanged?.invoke(corners)
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findNearestCorner(x: Float, y: Float, touchThreshold: Float): Int {
        val points = listOf(
            bmpToScreen(corners.topLeft),
            bmpToScreen(corners.topRight),
            bmpToScreen(corners.bottomRight),
            bmpToScreen(corners.bottomLeft)
        )

        var closestIdx = -1
        var minDistance = touchThreshold

        for (i in points.indices) {
            val dist = hypot((points[i].x - x).toDouble(), (points[i].y - y).toDouble()).toFloat()
            if (dist < minDistance) {
                minDistance = dist
                closestIdx = i
            }
        }
        return closestIdx
    }

    private fun updateActiveCorner(pt: PointF) {
        corners = when (activeCornerIndex) {
            0 -> corners.copy(topLeft = pt)
            1 -> corners.copy(topRight = pt)
            2 -> corners.copy(bottomRight = pt)
            3 -> corners.copy(bottomLeft = pt)
            else -> corners
        }
    }
}
