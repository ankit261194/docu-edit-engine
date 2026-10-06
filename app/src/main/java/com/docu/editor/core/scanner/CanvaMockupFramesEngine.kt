package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.docu.editor.domain.model.CanvaFrameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Canva Pro High-Fidelity 3D Device Mockup & Photo Frames Engine.
 * Supports photorealistic 3D smartphone, laptop, polaroid, 35mm film,
 * and geometric clipping frames with realistic drop shadows and screen gloss.
 */
object CanvaMockupFramesEngine {

    suspend fun applyFrameOrMockup(source: Bitmap, frameType: CanvaFrameType): Bitmap = withContext(Dispatchers.Default) {
        if (frameType == CanvaFrameType.NONE) return@withContext source.copy(Bitmap.Config.ARGB_8888, true)

        when (frameType) {
            CanvaFrameType.CIRCLE -> renderCircleFrame(source)
            CanvaFrameType.ROUNDED_SQUARE -> renderRoundedCardFrame(source)
            CanvaFrameType.POLAROID -> renderPolaroidFrame(source)
            CanvaFrameType.VINTAGE_FILM -> renderFilmStripFrame(source)
            CanvaFrameType.ARCH -> renderArchFrame(source)
            CanvaFrameType.HEART -> renderHeartFrame(source)
            CanvaFrameType.HEXAGON -> renderHexagonFrame(source)
            CanvaFrameType.STAMP_BADGE -> renderStampFrame(source)
            CanvaFrameType.SMARTPHONE_MOCKUP -> renderSmartphoneMockup(source)
            CanvaFrameType.LAPTOP_MOCKUP -> renderLaptopMockup(source)
            CanvaFrameType.CANVAS_TILT_3D -> render3DTiltMockup(source)
            CanvaFrameType.NONE -> source
        }
    }

    private fun renderCircleFrame(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val padding = 40
        val outSize = size + padding * 2
        val output = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Drop Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            setShadowLayer(24f, 0f, 12f, Color.argb(90, 0, 0, 0))
        }
        val center = outSize / 2f
        val radius = size / 2f
        canvas.drawCircle(center, center + 4f, radius, shadowPaint)

        // Circular Mask Bitmap
        val maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        maskCanvas.drawCircle(size / 2f, size / 2f, radius, maskPaint)

        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val srcCropL = (source.width - size) / 2
        val srcCropT = (source.height - size) / 2
        val cropped = Bitmap.createBitmap(source, srcCropL, srcCropT, size, size)
        maskCanvas.drawBitmap(cropped, 0f, 0f, maskPaint)
        cropped.recycle()

        canvas.drawBitmap(maskBitmap, padding.toFloat(), padding.toFloat(), null)
        maskBitmap.recycle()

        // White border ring
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        return output
    }

    private fun renderRoundedCardFrame(source: Bitmap): Bitmap {
        val pad = 36
        val outW = source.width + pad * 2
        val outH = source.height + pad * 2
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            setShadowLayer(28f, 0f, 14f, Color.argb(100, 0, 0, 0))
        }
        val cardRect = RectF(pad.toFloat(), pad.toFloat(), (pad + source.width).toFloat(), (pad + source.height).toFloat())
        canvas.drawRoundRect(cardRect, 48f, 48f, shadowPaint)

        val maskBmp = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        maskCanvas.drawRoundRect(RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), 48f, 48f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        maskCanvas.drawBitmap(source, 0f, 0f, p)

        canvas.drawBitmap(maskBmp, pad.toFloat(), pad.toFloat(), null)
        maskBmp.recycle()

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, strokePaint)

        return output
    }

    private fun renderPolaroidFrame(source: Bitmap): Bitmap {
        val borderWidth = (source.width * 0.08f).coerceAtLeast(32f)
        val bottomWidth = (source.width * 0.26f).coerceAtLeast(96f)
        val pad = 30

        val outW = (source.width + borderWidth * 2 + pad * 2).toInt()
        val outH = (source.height + borderWidth + bottomWidth + pad * 2).toInt()

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val frameRect = RectF(
            pad.toFloat(),
            pad.toFloat(),
            (outW - pad).toFloat(),
            (outH - pad).toFloat()
        )

        // Drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 0, 0, 0)
            setShadowLayer(26f, 0f, 14f, Color.argb(85, 0, 0, 0))
        }
        canvas.drawRoundRect(frameRect, 8f, 8f, shadowPaint)

        // Vintage Paper white background
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(252, 250, 246)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(frameRect, 8f, 8f, paperPaint)

        // Photo image placement
        val photoLeft = pad + borderWidth
        val photoTop = pad + borderWidth
        canvas.drawBitmap(source, photoLeft, photoTop, null)

        // Photo inner bevel/shadow
        val innerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(RectF(photoLeft, photoTop, photoLeft + source.width, photoTop + source.height), innerStroke)

        return output
    }

    private fun renderFilmStripFrame(source: Bitmap): Bitmap {
        val perfHeight = (source.height * 0.12f).coerceIn(40f, 120f)
        val outH = (source.height + perfHeight * 2).toInt()
        val outW = source.width + 40

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Film strip background (matte black)
        val filmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 18, 20)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), filmPaint)

        // Sprocket holes / perforations
        val perfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val holeW = perfHeight * 0.45f
        val holeH = perfHeight * 0.6f
        val spacing = holeW * 2.1f
        var curX = 24f
        while (curX < outW - holeW) {
            // Top sprocket hole
            canvas.drawRoundRect(RectF(curX, (perfHeight - holeH) / 2f, curX + holeW, (perfHeight + holeH) / 2f), 8f, 8f, perfPaint)
            // Bottom sprocket hole
            val botY = outH - perfHeight + (perfHeight - holeH) / 2f
            canvas.drawRoundRect(RectF(curX, botY, curX + holeW, botY + holeH), 8f, 8f, perfPaint)
            curX += spacing
        }

        // Draw photo centered
        canvas.drawBitmap(source, 20f, perfHeight, null)

        return output
    }

    private fun renderArchFrame(source: Bitmap): Bitmap {
        val outW = source.width
        val outH = source.height
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val path = Path().apply {
            val r = outW / 2f
            arcTo(RectF(0f, 0f, outW.toFloat(), outW.toFloat()), 180f, 180f, true)
            lineTo(outW.toFloat(), outH.toFloat())
            lineTo(0f, outH.toFloat())
            close()
        }

        val maskBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        maskCanvas.drawPath(path, maskPaint)

        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        maskCanvas.drawBitmap(source, 0f, 0f, maskPaint)

        canvas.drawBitmap(maskBmp, 0f, 0f, null)
        maskBmp.recycle()

        // Crisp border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawPath(path, borderPaint)

        return output
    }

    private fun renderHeartFrame(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val path = Path()
        val w = size.toFloat()
        val h = size.toFloat()
        path.moveTo(w / 2f, h * 0.85f)
        path.cubicTo(w * 0.1f, h * 0.55f, 0f, h * 0.3f, w * 0.25f, h * 0.12f)
        path.cubicTo(w * 0.42f, 0f, w / 2f, h * 0.2f, w / 2f, h * 0.25f)
        path.cubicTo(w / 2f, h * 0.2f, w * 0.58f, 0f, w * 0.75f, h * 0.12f)
        path.cubicTo(w, h * 0.3f, w * 0.9f, h * 0.55f, w / 2f, h * 0.85f)
        path.close()

        val maskBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        maskCanvas.drawPath(path, p)

        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val cropL = (source.width - size) / 2
        val cropT = (source.height - size) / 2
        val cropped = Bitmap.createBitmap(source, cropL, cropT, size, size)
        maskCanvas.drawBitmap(cropped, 0f, 0f, p)
        cropped.recycle()

        canvas.drawBitmap(maskBmp, 0f, 0f, null)
        maskBmp.recycle()

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        canvas.drawPath(path, border)

        return output
    }

    private fun renderHexagonFrame(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val path = Path()
        val c = size / 2f
        val r = size * 0.47f
        for (i in 0 until 6) {
            val angle = Math.toRadians((60 * i - 30).toDouble())
            val x = (c + r * Math.cos(angle)).toFloat()
            val y = (c + r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val maskBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        maskCanvas.drawPath(path, p)

        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val cropL = (source.width - size) / 2
        val cropT = (source.height - size) / 2
        val cropped = Bitmap.createBitmap(source, cropL, cropT, size, size)
        maskCanvas.drawBitmap(cropped, 0f, 0f, p)
        cropped.recycle()

        canvas.drawBitmap(maskBmp, 0f, 0f, null)
        maskBmp.recycle()

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawPath(path, border)

        return output
    }

    private fun renderStampFrame(source: Bitmap): Bitmap {
        val pad = 36
        val outW = source.width + pad * 2
        val outH = source.height + pad * 2
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Stamp base paper
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(250, 248, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(RectF(10f, 10f, (outW - 10).toFloat(), (outH - 10).toFloat()), paperPaint)

        // Draw serrated hole cutouts along all 4 edges
        val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val holeR = 12f
        val step = 32f

        // Top & bottom edges
        var x = 20f
        while (x < outW) {
            canvas.drawCircle(x, 10f, holeR, cutoutPaint)
            canvas.drawCircle(x, (outH - 10).toFloat(), holeR, cutoutPaint)
            x += step
        }
        // Left & right edges
        var y = 20f
        while (y < outH) {
            canvas.drawCircle(10f, y, holeR, cutoutPaint)
            canvas.drawCircle((outW - 10).toFloat(), y, holeR, cutoutPaint)
            y += step
        }

        // Draw photo in center
        canvas.drawBitmap(source, pad.toFloat(), pad.toFloat(), null)

        return output
    }

    private fun renderSmartphoneMockup(source: Bitmap): Bitmap {
        // iPhone/Modern Android style flagship 3D smartphone frame
        val phoneAspect = 19.5f / 9f
        val screenW = source.width
        val screenH = (screenW * phoneAspect).toInt()
        val scaledSource = Bitmap.createScaledBitmap(source, screenW, screenH, true)

        val bezel = (screenW * 0.055f).coerceAtLeast(24f)
        val phoneW = (screenW + bezel * 2).toInt()
        val phoneH = (screenH + bezel * 2).toInt()
        val pad = 50

        val totalW = phoneW + pad * 2
        val totalH = phoneH + pad * 2
        val output = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val phoneRect = RectF(
            pad.toFloat(),
            pad.toFloat(),
            (pad + phoneW).toFloat(),
            (pad + phoneH).toFloat()
        )

        // 3D Realistic Ambient Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
            setShadowLayer(38f, 0f, 22f, Color.argb(120, 0, 0, 0))
        }
        canvas.drawRoundRect(phoneRect, 68f, 68f, shadowPaint)

        // Outer Metallic Chassis (Space Black Titanium gradient)
        val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                phoneRect.left, phoneRect.top,
                phoneRect.right, phoneRect.bottom,
                intArrayOf(Color.rgb(38, 40, 48), Color.rgb(18, 19, 24), Color.rgb(44, 46, 56)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(phoneRect, 68f, 68f, chassisPaint)

        // Screen area
        val screenRect = RectF(
            phoneRect.left + bezel,
            phoneRect.top + bezel,
            phoneRect.right - bezel,
            phoneRect.bottom - bezel
        )

        val screenMask = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
        val screenMaskCanvas = Canvas(screenMask)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        screenMaskCanvas.drawRoundRect(RectF(0f, 0f, screenW.toFloat(), screenH.toFloat()), 44f, 44f, maskPaint)
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        screenMaskCanvas.drawBitmap(scaledSource, 0f, 0f, maskPaint)
        scaledSource.recycle()

        canvas.drawBitmap(screenMask, screenRect.left, screenRect.top, null)
        screenMask.recycle()

        // Dynamic Island / Camera Notch Pill
        val notchW = screenW * 0.28f
        val notchH = screenW * 0.07f
        val notchL = screenRect.left + (screenRect.width() - notchW) / 2f
        val notchT = screenRect.top + bezel * 0.45f
        val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(notchL, notchT, notchL + notchW, notchT + notchH), 22f, 22f, notchPaint)

        // Camera lens reflection dot
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 26, 43, 76)
        }
        canvas.drawCircle(notchL + notchW * 0.75f, notchT + notchH / 2f, 7f, lensPaint)

        // Glossy glass screen highlight overlay
        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                screenRect.left, screenRect.top,
                screenRect.right, screenRect.top + screenRect.height() * 0.4f,
                intArrayOf(Color.argb(55, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(screenRect, 44f, 44f, glossPaint)

        return output
    }

    private fun renderLaptopMockup(source: Bitmap): Bitmap {
        // Modern Aluminum Laptop (MacBook Style 16:10 aspect ratio)
        val laptopAspect = 10f / 16f
        val screenW = source.width
        val screenH = (screenW * laptopAspect).toInt()
        val scaledSource = Bitmap.createScaledBitmap(source, screenW, screenH, true)

        val bezel = (screenW * 0.04f).coerceAtLeast(18f)
        val lidW = (screenW + bezel * 2).toInt()
        val lidH = (screenH + bezel * 2).toInt()
        val baseH = (lidH * 0.12f).toInt()
        val baseW = (lidW * 1.15f).toInt()
        val pad = 40

        val totalW = baseW + pad * 2
        val totalH = lidH + baseH + pad * 2
        val output = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Drop shadow for lid and base
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            setShadowLayer(32f, 0f, 18f, Color.argb(100, 0, 0, 0))
        }

        val lidLeft = (totalW - lidW) / 2f
        val lidTop = pad.toFloat()
        val lidRect = RectF(lidLeft, lidTop, lidLeft + lidW, lidTop + lidH)
        canvas.drawRoundRect(lidRect, 28f, 28f, shadowPaint)

        // Aluminum Lid Bezel
        val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 23, 28)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(lidRect, 28f, 28f, lidPaint)

        // Screen insertion
        val screenRect = RectF(lidLeft + bezel, lidTop + bezel, lidLeft + bezel + screenW, lidTop + bezel + screenH)
        canvas.drawBitmap(scaledSource, screenRect.left, screenRect.top, null)
        scaledSource.recycle()

        // Web camera dot
        val camPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 55, 72)
        }
        canvas.drawCircle(lidLeft + lidW / 2f, lidTop + bezel / 2f, 5f, camPaint)

        // Base Keyboard Deck
        val baseLeft = pad.toFloat()
        val baseTop = lidTop + lidH
        val baseRect = RectF(baseLeft, baseTop, baseLeft + baseW, baseTop + baseH)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                baseLeft, baseTop,
                baseLeft, baseTop + baseH,
                intArrayOf(Color.rgb(195, 200, 208), Color.rgb(155, 160, 170)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(baseRect, 14f, 14f, basePaint)

        // Thumb notch in base
        val notchW = baseW * 0.16f
        val notchRect = RectF(baseLeft + (baseW - notchW) / 2f, baseTop, baseLeft + (baseW + notchW) / 2f, baseTop + 10f)
        val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 125, 135)
        }
        canvas.drawRoundRect(notchRect, 6f, 6f, notchPaint)

        return output
    }

    private fun render3DTiltMockup(source: Bitmap): Bitmap {
        // Isometric 3D Perspective Tilt with Directional Ambient Occlusion
        val pad = (source.width * 0.18f).toInt()
        val outW = source.width + pad * 2
        val outH = source.height + pad * 2
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Build 3D perspective skew matrix
        val matrix = Matrix()
        matrix.setSkew(-0.10f, 0.05f)
        matrix.postRotate(-6f, source.width / 2f, source.height / 2f)
        matrix.postTranslate(pad.toFloat() * 0.8f, pad.toFloat() * 0.8f)

        // Projected multi-layer drop shadow
        val shadowMatrix = Matrix(matrix)
        shadowMatrix.postTranslate(26f, 38f)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(Color.argb(85, 0, 0, 0), PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(source, shadowMatrix, shadowPaint)

        // Crisp 3D tilted document
        val docPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, docPaint)

        return output
    }
}
