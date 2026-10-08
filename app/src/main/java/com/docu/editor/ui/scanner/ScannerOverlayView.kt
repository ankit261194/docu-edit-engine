package com.docu.editor.ui.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.docu.editor.core.scanner.model.DocumentCorners

/**
 * Enterprise hardware-accelerated camera scanner overlay.
 * CamScanner-Grade Features:
 * - 60fps Butter-Smooth EMA Corner Interpolation (Zero Jitter).
 * - Animated Laser Scan Beam across detected document.
 * - Circular Auto-Snap Countdown / Stability Indicator.
 * - Dedicated ID Card Front/Back Guide with Scrim.
 * - Dedicated Book 2-Page Spine Guide with Left/Right Page Badges.
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rawCorners: DocumentCorners? = null
    private var isStable: Boolean = false
    private var frameWidth: Int = 1
    private var frameHeight: Int = 1

    var isIdCardMode: Boolean = false
    var idCardGuideText: String = "ALIGN ID CARD FRONT"

    var isBookMode: Boolean = false
    var bookGuideText: String = "ALIGN OPEN BOOK • AUTO 2-PAGE SPLIT"

    var autoSnapProgress: Float = 0f // 0.0f to 1.0f
    var autoSnapEnabled: Boolean = false

    // Smooth interpolated corner coordinates
    private var smoothP1: PointF? = null
    private var smoothP2: PointF? = null
    private var smoothP3: PointF? = null
    private var smoothP4: PointF? = null

    // Laser scan animation
    private var scanAnimProgress: Float = 0f
    private var scanAnimDirection: Float = 0.035f

    private val polygonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = CornerPathEffect(18f)
    }

    private val laserBeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val idCardFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.rgb(56, 189, 248) // Sky Blue
    }

    private val bookSpinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.rgb(250, 204, 21) // Amber / Gold
        pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f)
    }

    private val bookBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 15, 23, 42)
    }

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(135, 0, 0, 0)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(203, 213, 225)
        textSize = 28f
        isFakeBoldText = false
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

    private val progressRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(0, 230, 118) // Vibrant Green
    }

    private val magneticLockRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.rgb(0, 255, 136)
    }

    private val magneticLockGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 0, 230, 118)
    }

    private val path = Path()
    private var isMagneticLocked: Boolean = false

    fun updateCorners(detected: DocumentCorners?, stable: Boolean, srcW: Int, srcH: Int, progress: Float = 0f, locked: Boolean = false) {
        rawCorners = detected
        isStable = stable
        isMagneticLocked = locked
        autoSnapProgress = progress
        frameWidth = if (srcW > 0) srcW else 1
        frameHeight = if (srcH > 0) srcH else 1
        postInvalidateOnAnimation()
    }

    fun getIdCardRect(): RectF {
        val cardAspect = 85.6f / 53.98f
        val cardW = width * 0.88f
        val cardH = cardW / cardAspect
        val l = (width - cardW) / 2f
        val t = (height - cardH) / 2f - 40f
        return RectF(l, t, l + cardW, t + cardH)
    }

    fun getBookRect(): RectF {
        val bookW = width * 0.92f
        val bookH = height * 0.65f
        val l = (width - bookW) / 2f
        val t = (height - bookH) / 2f - 30f
        return RectF(l, t, l + bookW, t + bookH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. ID CARD SCANNER OVERLAY
        if (isIdCardMode) {
            val cardRect = getIdCardRect()
            canvas.drawRect(0f, 0f, width.toFloat(), cardRect.top, scrimPaint)
            canvas.drawRect(0f, cardRect.bottom, width.toFloat(), height.toFloat(), scrimPaint)
            canvas.drawRect(0f, cardRect.top, cardRect.left, cardRect.bottom, scrimPaint)
            canvas.drawRect(cardRect.right, cardRect.top, width.toFloat(), cardRect.bottom, scrimPaint)

            canvas.drawRoundRect(cardRect, 28f, 28f, idCardFramePaint)
            canvas.drawText(idCardGuideText, width / 2f, cardRect.top - 24f, textPaint)

            // Draw Auto-Snap countdown ring around center if holding still
            if (autoSnapEnabled && autoSnapProgress > 0f) {
                drawCenterSnapRing(canvas, cardRect.centerX(), cardRect.centerY(), 50f, autoSnapProgress)
            }
            return
        }

        // 2. BOOK 2-PAGE SPLIT OVERLAY
        if (isBookMode) {
            val bookRect = getBookRect()
            canvas.drawRect(0f, 0f, width.toFloat(), bookRect.top, scrimPaint)
            canvas.drawRect(0f, bookRect.bottom, width.toFloat(), height.toFloat(), scrimPaint)
            canvas.drawRect(0f, bookRect.top, bookRect.left, bookRect.bottom, scrimPaint)
            canvas.drawRect(bookRect.right, bookRect.top, width.toFloat(), bookRect.bottom, scrimPaint)

            // Outer border
            val outerBookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.rgb(250, 204, 21) // Amber
            }
            canvas.drawRoundRect(bookRect, 20f, 20f, outerBookPaint)

            // Center vertical book spine dashed line
            val centerX = bookRect.centerX()
            canvas.drawLine(centerX, bookRect.top, centerX, bookRect.bottom, bookSpinePaint)

            // Page 1 & Page 2 Badges
            val badgeW = 140f
            val badgeH = 50f
            val leftBadgeRect = RectF(centerX - badgeW - 40f, bookRect.top + 30f, centerX - 40f, bookRect.top + 30f + badgeH)
            val rightBadgeRect = RectF(centerX + 40f, bookRect.top + 30f, centerX + 40f + badgeW, bookRect.top + 30f + badgeH)

            canvas.drawRoundRect(leftBadgeRect, 14f, 14f, bookBadgePaint)
            canvas.drawRoundRect(rightBadgeRect, 14f, 14f, bookBadgePaint)

            canvas.drawText("📄 PAGE 1", leftBadgeRect.centerX(), leftBadgeRect.centerY() + 10f, subTextPaint)
            canvas.drawText("📄 PAGE 2", rightBadgeRect.centerX(), rightBadgeRect.centerY() + 10f, subTextPaint)

            canvas.drawText(bookGuideText, width / 2f, bookRect.top - 30f, textPaint)
            canvas.drawText("Center spine between pages will auto-cut into 2 sheets", width / 2f, bookRect.top - 65f, subTextPaint)

            if (autoSnapEnabled && autoSnapProgress > 0f) {
                drawCenterSnapRing(canvas, centerX, bookRect.centerY(), 60f, autoSnapProgress)
            }
            return
        }

        // 3. LIVE DOCUMENT AUTO-EDGE TRACKING (CamScanner Flagship Green Line & Tint)
        val strokeColor = if (isStable) Color.rgb(0, 230, 118) else Color.rgb(52, 211, 153) // Vivid Emerald Green
        polygonPaint.apply {
            color = strokeColor
            strokeWidth = if (isStable) 7f else 5.5f
            pathEffect = CornerPathEffect(20f)
        }
        fillPaint.apply {
            color = if (isStable) Color.argb(45, 0, 230, 118) else Color.argb(25, 0, 230, 118)
        }

        val pts = rawCorners
        if (pts == null) {
            // Draw default center viewfinder alignment brackets when searching for document
            val viewW = width * 0.82f
            val viewH = viewW * 1.414f // A4 proportion
            val vl = (width - viewW) / 2f
            val vt = (height - viewH) / 2f - 30f
            val vr = vl + viewW
            val vb = vt + viewH

            val defaultBracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4.5f
                strokeCap = Paint.Cap.ROUND
                color = Color.argb(130, 255, 255, 255)
            }
            val bLen = 42f
            drawCornerBracket(canvas, vl, vt, bLen, 1f, 1f, defaultBracketPaint)
            drawCornerBracket(canvas, vr, vt, bLen, -1f, 1f, defaultBracketPaint)
            drawCornerBracket(canvas, vr, vb, bLen, -1f, -1f, defaultBracketPaint)
            drawCornerBracket(canvas, vl, vb, bLen, 1f, -1f, defaultBracketPaint)

            smoothP1 = null
            smoothP2 = null
            smoothP3 = null
            smoothP4 = null
            postInvalidateOnAnimation()
            return
        }

        val scaleX = width.toFloat() / frameWidth
        val scaleY = height.toFloat() / frameHeight

        val targetP1 = PointF(pts.topLeft.x * scaleX, pts.topLeft.y * scaleY)
        val targetP2 = PointF(pts.topRight.x * scaleX, pts.topRight.y * scaleY)
        val targetP3 = PointF(pts.bottomRight.x * scaleX, pts.bottomRight.y * scaleY)
        val targetP4 = PointF(pts.bottomLeft.x * scaleX, pts.bottomLeft.y * scaleY)

        // Heavy low-pass EMA filter (alpha = 0.22) eliminates hand tremor & camera sensor noise
        val alpha = 0.22f
        smoothP1 = interpolate(smoothP1, targetP1, alpha)
        smoothP2 = interpolate(smoothP2, targetP2, alpha)
        smoothP3 = interpolate(smoothP3, targetP3, alpha)
        smoothP4 = interpolate(smoothP4, targetP4, alpha)

        val p1 = smoothP1!!
        val p2 = smoothP2!!
        val p3 = smoothP3!!
        val p4 = smoothP4!!

        path.reset()
        path.moveTo(p1.x, p1.y)
        path.lineTo(p2.x, p2.y)
        path.lineTo(p3.x, p3.y)
        path.lineTo(p4.x, p4.y)
        path.close()

        // 1. Translucent Green Document Highlighting Tint
        canvas.drawPath(path, fillPaint)

        // 2. Crisp Solid Green Perimeter Line (The iconic CamScanner border)
        canvas.drawPath(path, polygonPaint)

        // 3. Precision Corner Reticles (White ring with green center)
        val outerCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
        }
        val innerCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = strokeColor
        }
        for (corner in listOf(p1, p2, p3, p4)) {
            if (isMagneticLocked || isStable) {
                canvas.drawCircle(corner.x, corner.y, 20f, magneticLockGlowPaint)
                canvas.drawCircle(corner.x, corner.y, 18f, magneticLockRingPaint)
            }
            canvas.drawCircle(corner.x, corner.y, 10f, innerCornerPaint)
            canvas.drawCircle(corner.x, corner.y, 10f, outerCornerPaint)
        }

        // 4. Dynamic Laser Scan Beam animation across the document
        scanAnimProgress += scanAnimDirection
        if (scanAnimProgress >= 1f) {
            scanAnimProgress = 1f
            scanAnimDirection = -0.03f
        } else if (scanAnimProgress <= 0f) {
            scanAnimProgress = 0f
            scanAnimDirection = 0.03f
        }

        val lx1 = p1.x * (1f - scanAnimProgress) + p4.x * scanAnimProgress
        val ly1 = p1.y * (1f - scanAnimProgress) + p4.y * scanAnimProgress
        val lx2 = p2.x * (1f - scanAnimProgress) + p3.x * scanAnimProgress
        val ly2 = p2.y * (1f - scanAnimProgress) + p3.y * scanAnimProgress

        laserBeamPaint.shader = LinearGradient(
            lx1, ly1, lx2, ly2,
            intArrayOf(Color.TRANSPARENT, Color.rgb(0, 230, 118), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(lx1, ly1, lx2, ly2, laserBeamPaint)

        // 5. Auto-Snap countdown progress ring ONLY when explicitly in Auto-Snap mode and stable
        if (autoSnapEnabled && isStable && autoSnapProgress > 0f) {
            val docCenterX = (p1.x + p2.x + p3.x + p4.x) / 4f
            val docCenterY = (p1.y + p2.y + p3.y + p4.y) / 4f
            drawCenterSnapRing(canvas, docCenterX, docCenterY, 55f, autoSnapProgress)
        }

        postInvalidateOnAnimation()
    }

    private fun drawCornerBracket(canvas: Canvas, x: Float, y: Float, len: Float, dx: Float, dy: Float, paint: Paint) {
        canvas.drawLine(x, y, x + dx * len, y, paint)
        canvas.drawLine(x, y, x, y + dy * len, paint)
    }

    private fun drawCenterSnapRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, progress: Float) {
        val bgRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.argb(120, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, radius, bgRingPaint)

        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, -90f, progress * 360f, false, progressRingPaint)

        val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val pct = (progress * 100).toInt()
        canvas.drawText("$pct%", cx, cy + 10f, centerTextPaint)
    }

    private fun interpolate(current: PointF?, target: PointF, alpha: Float): PointF {
        if (current == null) return PointF(target.x, target.y)
        return PointF(
            current.x * (1f - alpha) + target.x * alpha,
            current.y * (1f - alpha) + target.y * alpha
        )
    }
}
