package com.docu.editor.ui.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.docu.editor.core.scanner.model.DocumentCorners

/**
 * High-performance hardware-accelerated overlay that draws live CamScanner-style
 * animated edge tracking lines, corner reticles, and stability glow.
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var corners: DocumentCorners? = null
    private var isStable: Boolean = false
    private var frameWidth: Int = 1
    private var frameHeight: Int = 1
    var isIdCardMode: Boolean = false
    var idCardGuideText: String = "ALIGN ID CARD FRONT"

    private val polygonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = CornerPathEffect(16f)
    }

    private val idCardFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.rgb(56, 189, 248) // Vibrant Sky Blue / Cyan
    }

    private val idCardScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(130, 0, 0, 0)
    }

    private val idCardTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cornerReticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cornerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val path = Path()

    fun updateCorners(detected: DocumentCorners?, stable: Boolean, srcW: Int, srcH: Int) {
        corners = detected
        isStable = stable
        frameWidth = if (srcW > 0) srcW else 1
        frameHeight = if (srcH > 0) srcH else 1
        postInvalidateOnAnimation()
    }

    fun getIdCardRect(): android.graphics.RectF {
        val cardAspect = 85.6f / 53.98f
        val cardW = width * 0.88f
        val cardH = cardW / cardAspect
        val l = (width - cardW) / 2f
        val t = (height - cardH) / 2f - 40f
        return android.graphics.RectF(l, t, l + cardW, t + cardH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isIdCardMode) {
            val cardRect = getIdCardRect()
            // Draw darkened scrim areas around the card rectangle
            canvas.drawRect(0f, 0f, width.toFloat(), cardRect.top, idCardScrimPaint)
            canvas.drawRect(0f, cardRect.bottom, width.toFloat(), height.toFloat(), idCardScrimPaint)
            canvas.drawRect(0f, cardRect.top, cardRect.left, cardRect.bottom, idCardScrimPaint)
            canvas.drawRect(cardRect.right, cardRect.top, width.toFloat(), cardRect.bottom, idCardScrimPaint)

            // Draw glowing rounded border
            canvas.drawRoundRect(cardRect, 28f, 28f, idCardFramePaint)

            // Draw hint text above the frame
            canvas.drawText(idCardGuideText, width / 2f, cardRect.top - 24f, idCardTextPaint)
            return
        }

        val pts = corners ?: return

        val scaleX = width.toFloat() / frameWidth
        val scaleY = height.toFloat() / frameHeight

        val p1 = PointF(pts.topLeft.x * scaleX, pts.topLeft.y * scaleY)
        val p2 = PointF(pts.topRight.x * scaleX, pts.topRight.y * scaleY)
        val p3 = PointF(pts.bottomRight.x * scaleX, pts.bottomRight.y * scaleY)
        val p4 = PointF(pts.bottomLeft.x * scaleX, pts.bottomLeft.y * scaleY)

        val strokeColor = if (isStable) Color.rgb(0, 230, 118) else Color.rgb(0, 229, 255)
        val fillColor = if (isStable) Color.argb(45, 0, 230, 118) else Color.argb(25, 0, 229, 255)

        polygonPaint.color = strokeColor
        fillPaint.color = fillColor
        cornerReticlePaint.color = strokeColor

        // Draw bounded polygon
        path.reset()
        path.moveTo(p1.x, p1.y)
        path.lineTo(p2.x, p2.y)
        path.lineTo(p3.x, p3.y)
        path.lineTo(p4.x, p4.y)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, polygonPaint)

        // Draw circular corner reticles
        val cornerRadius = 20f
        val points = arrayOf(p1, p2, p3, p4)
        for (pt in points) {
            canvas.drawCircle(pt.x, pt.y, cornerRadius, cornerReticlePaint)
            canvas.drawCircle(pt.x, pt.y, cornerRadius, cornerBorderPaint)
            canvas.drawCircle(pt.x, pt.y, 6f, cornerBorderPaint)
        }
    }
}
