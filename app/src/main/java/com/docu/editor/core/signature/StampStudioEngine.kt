package com.docu.editor.core.signature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Enterprise Digital Rubber Stamp Generator Engine.
 * Creates photorealistic official rubber stamps:
 * - Circular Double-Ring Seals with Arc Text (Top & Bottom).
 * - Rectangular Box Business Stamps with Double Borders.
 * - Distressed Rubber Texture (Vintage Ink bleed & Paper grain perforations).
 * - Authentic Tilting & Rotated Canvas Stamping.
 */
object StampStudioEngine {

    enum class StampShape {
        CIRCULAR_SEAL,
        RECTANGULAR_BOX,
        OVAL_BADGE
    }

    data class StampConfig(
        val shape: StampShape = StampShape.CIRCULAR_SEAL,
        val centerText: String = "APPROVED",
        val topText: String = "OFFICIAL VERIFICATION",
        val bottomText: String = "AUTHORIZED SIGNATORY",
        val inkColor: Int = Color.rgb(220, 38, 38), // Red
        val distressLevel: Float = 0.22f, // 0.0 to 1.0
        val sizePx: Int = 600
    )

    fun createRubberStamp(config: StampConfig): Bitmap {
        val size = config.sizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (config.shape) {
            StampShape.CIRCULAR_SEAL -> drawCircularSeal(canvas, size, config)
            StampShape.RECTANGULAR_BOX -> drawRectangularBox(canvas, size, config)
            StampShape.OVAL_BADGE -> drawOvalBadge(canvas, size, config)
        }

        // Apply vintage rubber ink distress & speckle noise
        if (config.distressLevel > 0f) {
            applyDistressNoise(bitmap, config.distressLevel, config.inkColor)
        }

        return bitmap
    }

    private fun drawCircularSeal(canvas: Canvas, size: Int, config: StampConfig) {
        val cx = size / 2f
        val cy = size / 2f
        val outerRadius = size * 0.45f
        val innerRadius = size * 0.38f
        val coreRadius = size * 0.28f

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = config.inkColor
        }

        // 1. Outer Ring
        canvas.drawCircle(cx, cy, outerRadius, ringPaint)

        // 2. Decorative Mid Ring
        val midRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = config.inkColor
        }
        canvas.drawCircle(cx, cy, innerRadius, midRingPaint)

        // 3. Inner Center Ring
        canvas.drawCircle(cx, cy, coreRadius, ringPaint)

        // 4. Arc Text - Top
        if (config.topText.isNotBlank()) {
            val topPath = Path()
            val textRadius = (outerRadius + innerRadius) / 2f - 6f
            val oval = RectF(cx - textRadius, cy - textRadius, cx + textRadius, cy + textRadius)
            topPath.addArc(oval, 180f, 180f)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.inkColor
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.15f
            }
            canvas.drawTextOnPath(config.topText.uppercase(), topPath, 0f, 0f, textPaint)
        }

        // 5. Arc Text - Bottom
        if (config.bottomText.isNotBlank()) {
            val bottomPath = Path()
            val textRadius = (outerRadius + innerRadius) / 2f + 16f
            val oval = RectF(cx - textRadius, cy - textRadius, cx + textRadius, cy + textRadius)
            bottomPath.addArc(oval, 180f, -180f)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.inkColor
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
            }
            canvas.drawTextOnPath(config.bottomText.uppercase(), bottomPath, 0f, 0f, textPaint)
        }

        // 6. Decorative Stars on Sides
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.inkColor
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        val starDist = (outerRadius + innerRadius) / 2f
        canvas.drawText("★", cx - starDist, cy + 8f, starPaint)
        canvas.drawText("★", cx + starDist, cy + 8f, starPaint)

        // 7. Center Bold Text
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.inkColor
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText(config.centerText.uppercase(), cx, cy + 16f, centerPaint)
    }

    private fun drawRectangularBox(canvas: Canvas, size: Int, config: StampConfig) {
        val pad = size * 0.10f
        val outerRect = RectF(pad, pad * 1.5f, size - pad, size - pad * 1.5f)
        val innerRect = RectF(outerRect.left + 12f, outerRect.top + 12f, outerRect.right - 12f, outerRect.bottom - 12f)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            color = config.inkColor
        }
        canvas.drawRoundRect(outerRect, 18f, 18f, borderPaint)

        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = config.inkColor
        }
        canvas.drawRoundRect(innerRect, 12f, 12f, innerBorderPaint)

        val cx = size / 2f
        val cy = size / 2f

        // Top Subtext
        if (config.topText.isNotBlank()) {
            val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.inkColor
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
            }
            canvas.drawText(config.topText.uppercase(), cx, cy - 60f, topPaint)
        }

        // Divider Line
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = config.inkColor
        }
        canvas.drawLine(cx - 180f, cy - 40f, cx + 180f, cy - 40f, divPaint)

        // Center Big Text
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.inkColor
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        canvas.drawText(config.centerText.uppercase(), cx, cy + 22f, centerPaint)

        // Divider Line Bottom
        canvas.drawLine(cx - 180f, cy + 50f, cx + 180f, cy + 50f, divPaint)

        // Bottom Subtext
        if (config.bottomText.isNotBlank()) {
            val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.inkColor
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.10f
            }
            canvas.drawText(config.bottomText.uppercase(), cx, cy + 95f, bottomPaint)
        }
    }

    private fun drawOvalBadge(canvas: Canvas, size: Int, config: StampConfig) {
        val cx = size / 2f
        val cy = size / 2f
        val ovalW = size * 0.44f
        val ovalH = size * 0.28f

        val outerOval = RectF(cx - ovalW, cy - ovalH, cx + ovalW, cy + ovalH)
        val innerOval = RectF(cx - ovalW + 14f, cy - ovalH + 14f, cx + ovalW - 14f, cy + ovalH - 14f)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = config.inkColor
        }
        canvas.drawOval(outerOval, borderPaint)

        val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = config.inkColor
        }
        canvas.drawOval(innerOval, innerBorderPaint)

        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.inkColor
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        canvas.drawText(config.centerText.uppercase(), cx, cy + 18f, centerPaint)
    }

    private fun applyDistressNoise(bitmap: Bitmap, distress: Float, inkColor: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rng = Random(42)
        val speckleThreshold = distress.coerceIn(0.05f, 0.60f)

        for (i in pixels.indices) {
            val alpha = (pixels[i] ushr 24) and 0xFF
            if (alpha > 40) {
                // Random micro-perforations simulating paper texture grain
                if (rng.nextFloat() < speckleThreshold * 0.28f) {
                    val fadeFactor = rng.nextFloat() * 0.60f
                    val newAlpha = (alpha * fadeFactor).toInt()
                    pixels[i] = (newAlpha shl 24) or (pixels[i] and 0x00FFFFFF)
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
