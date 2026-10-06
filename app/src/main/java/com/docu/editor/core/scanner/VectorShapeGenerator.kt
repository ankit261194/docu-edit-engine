package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.docu.editor.domain.model.ShapeType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Enterprise Canva Pro Vector Shape Generator.
 * Renders mathematical vector geometries into ultra-crisp ARGB_8888 Bitmaps:
 * - Rectangles, Rounded Rectangles, Circles, Ovals, Lines, Arrows, Double Arrows
 * - Triangles, 5-Point Stars, Hearts, Callout Speech Bubbles, Hexagons, Shields, Checkmark Badges
 * - Configurable Fill Color, Border/Stroke Color, Stroke Width, and Opacity.
 */
object VectorShapeGenerator {

    fun createShapeBitmap(
        type: ShapeType,
        width: Int = 360,
        height: Int = 360,
        strokeColor: Int = Color.rgb(220, 38, 38),
        strokeWidth: Float = 8f,
        fillColor: Int? = null,
        cornerRadius: Float = 24f
    ): Bitmap {
        val w = width.coerceAtLeast(60)
        val h = height.coerceAtLeast(60)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (fillColor != null) {
                color = fillColor
            }
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val halfStroke = strokeWidth / 2f
        val rect = RectF(halfStroke + 4f, halfStroke + 4f, w - halfStroke - 4f, h - halfStroke - 4f)

        when (type) {
            ShapeType.RECTANGLE -> {
                if (fillColor != null) canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, strokePaint)
            }

            ShapeType.ROUNDED_RECTANGLE -> {
                if (fillColor != null) canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
            }

            ShapeType.CIRCLE, ShapeType.OVAL -> {
                if (fillColor != null) canvas.drawOval(rect, fillPaint)
                canvas.drawOval(rect, strokePaint)
            }

            ShapeType.LINE -> {
                val startY = h / 2f
                canvas.drawLine(halfStroke + 8f, startY, w - halfStroke - 8f, startY, strokePaint)
            }

            ShapeType.ARROW -> {
                val midY = h / 2f
                val headSize = (h * 0.35f).coerceAtLeast(32f)
                val lineEnd = w - halfStroke - headSize
                canvas.drawLine(halfStroke + 8f, midY, lineEnd, midY, strokePaint)

                val arrowHead = Path().apply {
                    moveTo(w - halfStroke - 6f, midY)
                    lineTo(w - halfStroke - headSize, midY - headSize * 0.6f)
                    lineTo(w - halfStroke - headSize, midY + headSize * 0.6f)
                    close()
                }
                val headFillPaint = Paint(strokePaint).apply { style = Paint.Style.FILL }
                canvas.drawPath(arrowHead, headFillPaint)
            }

            ShapeType.DOUBLE_ARROW -> {
                val midY = h / 2f
                val headSize = (h * 0.3f).coerceAtLeast(28f)
                canvas.drawLine(halfStroke + headSize, midY, w - halfStroke - headSize, midY, strokePaint)

                val headFillPaint = Paint(strokePaint).apply { style = Paint.Style.FILL }
                // Right arrow head
                val rightHead = Path().apply {
                    moveTo(w - halfStroke - 6f, midY)
                    lineTo(w - halfStroke - headSize, midY - headSize * 0.6f)
                    lineTo(w - halfStroke - headSize, midY + headSize * 0.6f)
                    close()
                }
                canvas.drawPath(rightHead, headFillPaint)

                // Left arrow head
                val leftHead = Path().apply {
                    moveTo(halfStroke + 6f, midY)
                    lineTo(halfStroke + headSize, midY - headSize * 0.6f)
                    lineTo(halfStroke + headSize, midY + headSize * 0.6f)
                    close()
                }
                canvas.drawPath(leftHead, headFillPaint)
            }

            ShapeType.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(w / 2f, rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                if (fillColor != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            ShapeType.STAR -> {
                val path = Path()
                val centerX = w / 2f
                val centerY = h / 2f
                val outerRadius = (minOf(w, h) / 2f) - halfStroke - 6f
                val innerRadius = outerRadius * 0.42f
                val points = 5
                var angle = -PI / 2.0

                for (i in 0 until points * 2) {
                    val r = if (i % 2 == 0) outerRadius else innerRadius
                    val px = (centerX + r * cos(angle)).toFloat()
                    val py = (centerY + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    angle += PI / points
                }
                path.close()

                if (fillColor != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            ShapeType.HEART -> {
                val path = Path()
                val cx = w / 2f
                val cy = h * 0.4f
                val size = (minOf(w, h) * 0.42f).coerceAtLeast(30f)

                path.moveTo(cx, cy + size * 0.9f)
                // Left curve
                path.cubicTo(
                    cx - size * 1.5f, cy - size * 0.8f,
                    cx - size * 0.7f, cy - size * 1.6f,
                    cx, cy - size * 0.5f
                )
                // Right curve
                path.cubicTo(
                    cx + size * 0.7f, cy - size * 1.6f,
                    cx + size * 1.5f, cy - size * 0.8f,
                    cx, cy + size * 0.9f
                )
                path.close()

                if (fillColor != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            ShapeType.CALLOUT_BUBBLE -> {
                val path = Path()
                val bubbleBottom = rect.bottom - 45f
                val r = cornerRadius

                path.moveTo(rect.left + r, rect.top)
                path.lineTo(rect.right - r, rect.top)
                path.quadTo(rect.right, rect.top, rect.right, rect.top + r)
                path.lineTo(rect.right, bubbleBottom - r)
                path.quadTo(rect.right, bubbleBottom, rect.right - r, bubbleBottom)
                // Speech pointer tail
                path.lineTo(rect.left + 90f, bubbleBottom)
                path.lineTo(rect.left + 45f, rect.bottom)
                path.lineTo(rect.left + 55f, bubbleBottom)
                path.lineTo(rect.left + r, bubbleBottom)
                path.quadTo(rect.left, bubbleBottom, rect.left, bubbleBottom - r)
                path.lineTo(rect.left, rect.top + r)
                path.quadTo(rect.left, rect.top, rect.left + r, rect.top)
                path.close()

                if (fillColor != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            ShapeType.HEXAGON -> {
                val path = Path()
                val cx = w / 2f
                val cy = h / 2f
                val radius = (minOf(w, h) / 2f) - halfStroke - 6f
                for (i in 0 until 6) {
                    val angle = PI / 3.0 * i - PI / 6.0
                    val px = (cx + radius * cos(angle)).toFloat()
                    val py = (cy + radius * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()

                if (fillColor != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            ShapeType.SHIELD -> {
                val path = Path()
                val cx = w / 2f
                path.moveTo(cx, rect.top)
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right, rect.top + h * 0.45f)
                path.quadTo(rect.right, rect.bottom, cx, rect.bottom)
                path.quadTo(rect.left, rect.bottom, rect.left, rect.top + h * 0.45f)
                path.lineTo(rect.left, rect.top)
                path.close()

                if (fillColor != null) canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }

            ShapeType.CHECKMARK_BADGE -> {
                // Circle badge with tick
                if (fillColor != null) canvas.drawOval(rect, fillPaint)
                canvas.drawOval(rect, strokePaint)

                val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = strokeColor
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth * 1.3f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val checkPath = Path().apply {
                    moveTo(rect.left + w * 0.28f, rect.top + h * 0.52f)
                    lineTo(rect.left + w * 0.44f, rect.top + h * 0.68f)
                    lineTo(rect.left + w * 0.74f, rect.top + h * 0.34f)
                }
                canvas.drawPath(checkPath, checkPaint)
            }
        }

        return bitmap
    }
}
