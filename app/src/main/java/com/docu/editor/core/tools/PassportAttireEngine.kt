package com.docu.editor.core.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Enterprise Formal Attire & Suit Replacement Engine for Passport & ID Photos.
 * 
 * Allows users to 1-click replace casual clothes (T-shirts, hoodies, vests)
 * with sharp, tailored formal suits, executive blazers, or official white collared shirts.
 * 
 * Features:
 * - Men's and Women's tailored business attire presets.
 * - Dynamic collar elevation and shoulder width adjustment.
 * - Realistic fabric shading, collar shadows, buttons, and silk tie highlights.
 */
object PassportAttireEngine {

    enum class AttireGender {
        ALL, MEN, WOMEN
    }

    enum class AttireType(
        val displayName: String,
        val subtitle: String,
        val gender: AttireGender,
        val iconEmoji: String
    ) {
        NONE("Original Clothes", "Keep current clothing", AttireGender.ALL, "👕"),
        MEN_BLACK_SUIT("Black Suit & Blue Tie", "Classic corporate formal", AttireGender.MEN, "👔"),
        MEN_NAVY_BLAZER("Navy Blazer & Red Tie", "Executive diplomat style", AttireGender.MEN, "👔"),
        MEN_WHITE_SHIRT("White Collared Shirt", "Govt Exam / Armed Forces", AttireGender.MEN, "👔"),
        MEN_CHARCOAL_SUIT("Charcoal Grey Suit", "Modern business attire", AttireGender.MEN, "👔"),
        WOMEN_BLACK_BLAZER("Black Executive Blazer", "Formal interview attire", AttireGender.WOMEN, "🧥"),
        WOMEN_NAVY_BLAZER("Navy Blue Blazer", "Corporate & Bank official", AttireGender.WOMEN, "🧥"),
        WOMEN_WHITE_SHIRT("White Collared Shirt", "Official standard passport", AttireGender.WOMEN, "👚")
    }

    /**
     * Overlays the selected formal attire on top of the portrait.
     * 
     * @param sourceBitmap The portrait bitmap (preferably after background replacement)
     * @param attire The selected attire preset
     * @param verticalShiftRatio Up/down adjustment to fit neck height (-0.15f to +0.15f)
     * @param shoulderScale Shoulder width scale factor (0.85f to 1.25f)
     */
    fun applyAttire(
        sourceBitmap: Bitmap,
        attire: AttireType,
        verticalShiftRatio: Float = 0f,
        shoulderScale: Float = 1.0f
    ): Bitmap {
        if (attire == AttireType.NONE) {
            return sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val output = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val wF = width.toFloat()
        val hF = height.toFloat()

        // Base anchor: Neck/chest opening usually begins around 62-65% down from top
        val baseNeckY = hF * 0.63f + (hF * verticalShiftRatio)
        val centerX = wF * 0.5f

        when (attire) {
            AttireType.MEN_BLACK_SUIT -> renderMenSuit(
                canvas, wF, hF, centerX, baseNeckY, shoulderScale,
                suitColor = Color.parseColor("#18181B"), // Jet black
                lapelColor = Color.parseColor("#09090B"),
                tieColor = Color.parseColor("#1D4ED8"), // Royal Blue Silk Tie
                tieHighlight = Color.parseColor("#3B82F6")
            )
            AttireType.MEN_NAVY_BLAZER -> renderMenSuit(
                canvas, wF, hF, centerX, baseNeckY, shoulderScale,
                suitColor = Color.parseColor("#1E3A8A"), // Navy Blue
                lapelColor = Color.parseColor("#172554"),
                tieColor = Color.parseColor("#991B1B"), // Deep Red Tie
                tieHighlight = Color.parseColor("#DC2626")
            )
            AttireType.MEN_CHARCOAL_SUIT -> renderMenSuit(
                canvas, wF, hF, centerX, baseNeckY, shoulderScale,
                suitColor = Color.parseColor("#334155"), // Slate Charcoal
                lapelColor = Color.parseColor("#1E293B"),
                tieColor = Color.parseColor("#0F172A"), // Dark Grey Tie
                tieHighlight = Color.parseColor("#475569")
            )
            AttireType.MEN_WHITE_SHIRT -> renderCollaredShirt(
                canvas, wF, hF, centerX, baseNeckY, shoulderScale,
                shirtColor = Color.parseColor("#F8FAFC"),
                collarColor = Color.parseColor("#FFFFFF"),
                shadowColor = Color.parseColor("#CBD5E1"),
                buttonColor = Color.parseColor("#E2E8F0")
            )
            AttireType.WOMEN_BLACK_BLAZER -> renderWomenBlazer(
                canvas, wF, hF, centerX, baseNeckY, shoulderScale,
                blazerColor = Color.parseColor("#18181B"),
                innerTopColor = Color.parseColor("#FFFFFF")
            )
            AttireType.WOMEN_NAVY_BLAZER -> renderWomenBlazer(
                canvas, wF, hF, centerX, baseNeckY, shoulderScale,
                blazerColor = Color.parseColor("#1E3A8A"),
                innerTopColor = Color.parseColor("#F1F5F9")
            )
            AttireType.WOMEN_WHITE_SHIRT -> renderCollaredShirt(
                canvas, wF, hF, centerX, baseNeckY, shoulderScale,
                shirtColor = Color.parseColor("#FAFAFA"),
                collarColor = Color.parseColor("#FFFFFF"),
                shadowColor = Color.parseColor("#D4D4D8"),
                buttonColor = Color.parseColor("#E4E4E7")
            )
            AttireType.NONE -> {}
        }

        return output
    }

    private fun renderMenSuit(
        canvas: Canvas,
        w: Float,
        h: Float,
        cx: Float,
        neckY: Float,
        scale: Float,
        suitColor: Int,
        lapelColor: Int,
        tieColor: Int,
        tieHighlight: Int
    ) {
        val halfW = (w * 0.5f) * scale
        val shoulderLeft = cx - halfW * 1.15f
        val shoulderRight = cx + halfW * 1.15f

        // 1. White Shirt Collar & V-Neck Opening
        val shirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val shirtVPath = Path().apply {
            moveTo(cx - (w * 0.16f), neckY)
            lineTo(cx + (w * 0.16f), neckY)
            lineTo(cx, neckY + (h * 0.22f))
            close()
        }
        canvas.drawPath(shirtVPath, shirtPaint)

        // Shirt Collar Wings (Left and Right crisp white flaps)
        val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val leftCollar = Path().apply {
            moveTo(cx - (w * 0.16f), neckY)
            lineTo(cx - (w * 0.05f), neckY + (h * 0.07f))
            lineTo(cx - (w * 0.02f), neckY + (h * 0.02f))
            close()
        }
        val rightCollar = Path().apply {
            moveTo(cx + (w * 0.16f), neckY)
            lineTo(cx + (w * 0.05f), neckY + (h * 0.07f))
            lineTo(cx + (w * 0.02f), neckY + (h * 0.02f))
            close()
        }
        canvas.drawPath(leftCollar, collarPaint)
        canvas.drawPath(rightCollar, collarPaint)

        // 2. Silk Necktie (Knot + Body)
        val tiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - 15f, neckY, cx + 15f, h,
                tieHighlight, tieColor, Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        // Tie Knot (Trapezoid)
        val tieKnot = Path().apply {
            moveTo(cx - (w * 0.035f), neckY + (h * 0.025f))
            lineTo(cx + (w * 0.035f), neckY + (h * 0.025f))
            lineTo(cx + (w * 0.022f), neckY + (h * 0.065f))
            lineTo(cx - (w * 0.022f), neckY + (h * 0.065f))
            close()
        }
        canvas.drawPath(tieKnot, tiePaint)

        // Tie Body
        val tieBody = Path().apply {
            moveTo(cx - (w * 0.022f), neckY + (h * 0.065f))
            lineTo(cx + (w * 0.022f), neckY + (h * 0.065f))
            lineTo(cx + (w * 0.055f), h)
            lineTo(cx - (w * 0.055f), h)
            close()
        }
        canvas.drawPath(tieBody, tiePaint)

        // 3. Suit Body & Shoulder Coat
        val suitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, neckY, cx, h,
                suitColor, Color.BLACK, Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }

        // Left Suit Shoulder & Torso
        val leftCoat = Path().apply {
            moveTo(cx - (w * 0.16f), neckY)
            quadTo(cx - (w * 0.35f), neckY + (h * 0.02f), shoulderLeft, neckY + (h * 0.14f))
            lineTo(shoulderLeft - 20f, h)
            lineTo(cx - (w * 0.04f), h)
            lineTo(cx - (w * 0.10f), neckY + (h * 0.22f))
            close()
        }
        canvas.drawPath(leftCoat, suitPaint)

        // Right Suit Shoulder & Torso
        val rightCoat = Path().apply {
            moveTo(cx + (w * 0.16f), neckY)
            quadTo(cx + (w * 0.35f), neckY + (h * 0.02f), shoulderRight, neckY + (h * 0.14f))
            lineTo(shoulderRight + 20f, h)
            lineTo(cx + (w * 0.04f), h)
            lineTo(cx + (w * 0.10f), neckY + (h * 0.22f))
            close()
        }
        canvas.drawPath(rightCoat, suitPaint)

        // 4. Suit Notch Lapels (Front folded flaps with realistic dark wool contrast)
        val lapelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lapelColor
            style = Paint.Style.FILL
        }

        // Left Notch Lapel
        val leftLapel = Path().apply {
            moveTo(cx - (w * 0.16f), neckY)
            lineTo(cx - (w * 0.22f), neckY + (h * 0.08f))
            lineTo(cx - (w * 0.18f), neckY + (h * 0.09f))
            lineTo(cx - (w * 0.08f), neckY + (h * 0.26f))
            lineTo(cx - (w * 0.02f), neckY + (h * 0.26f))
            lineTo(cx - (w * 0.12f), neckY + (h * 0.06f))
            close()
        }
        canvas.drawPath(leftLapel, lapelPaint)

        // Right Notch Lapel
        val rightLapel = Path().apply {
            moveTo(cx + (w * 0.16f), neckY)
            lineTo(cx + (w * 0.22f), neckY + (h * 0.08f))
            lineTo(cx + (w * 0.18f), neckY + (h * 0.09f))
            lineTo(cx + (w * 0.08f), neckY + (h * 0.26f))
            lineTo(cx + (w * 0.02f), neckY + (h * 0.26f))
            lineTo(cx + (w * 0.12f), neckY + (h * 0.06f))
            close()
        }
        canvas.drawPath(rightLapel, lapelPaint)

        // Seam / Edge Stitches
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3F3F46")
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }
        canvas.drawPath(leftLapel, seamPaint)
        canvas.drawPath(rightLapel, seamPaint)
    }

    private fun renderWomenBlazer(
        canvas: Canvas,
        w: Float,
        h: Float,
        cx: Float,
        neckY: Float,
        scale: Float,
        blazerColor: Int,
        innerTopColor: Int
    ) {
        val halfW = (w * 0.5f) * scale
        val shoulderLeft = cx - halfW * 1.10f
        val shoulderRight = cx + halfW * 1.10f

        // 1. Inner Formal Blouse / Top
        val blousePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = innerTopColor
            style = Paint.Style.FILL
        }
        val blousePath = Path().apply {
            moveTo(cx - (w * 0.14f), neckY)
            lineTo(cx + (w * 0.14f), neckY)
            lineTo(cx + (w * 0.08f), h)
            lineTo(cx - (w * 0.08f), h)
            close()
        }
        canvas.drawPath(blousePath, blousePaint)

        // 2. Blazer Coat & Shoulders
        val blazerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx, neckY, cx, h,
                blazerColor, Color.BLACK, Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }

        val leftSide = Path().apply {
            moveTo(cx - (w * 0.14f), neckY)
            quadTo(cx - (w * 0.32f), neckY + (h * 0.02f), shoulderLeft, neckY + (h * 0.13f))
            lineTo(shoulderLeft - 20f, h)
            lineTo(cx - (w * 0.03f), h)
            lineTo(cx - (w * 0.06f), neckY + (h * 0.18f))
            close()
        }
        canvas.drawPath(leftSide, blazerPaint)

        val rightSide = Path().apply {
            moveTo(cx + (w * 0.14f), neckY)
            quadTo(cx + (w * 0.32f), neckY + (h * 0.02f), shoulderRight, neckY + (h * 0.13f))
            lineTo(shoulderRight + 20f, h)
            lineTo(cx + (w * 0.03f), h)
            lineTo(cx + (w * 0.06f), neckY + (h * 0.18f))
            close()
        }
        canvas.drawPath(rightSide, blazerPaint)

        // 3. Shawl / Peaked Lapels
        val lapelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#09090B")
            style = Paint.Style.FILL
        }

        val leftLapel = Path().apply {
            moveTo(cx - (w * 0.14f), neckY)
            lineTo(cx - (w * 0.18f), neckY + (h * 0.08f))
            lineTo(cx - (w * 0.05f), neckY + (h * 0.22f))
            lineTo(cx - (w * 0.01f), neckY + (h * 0.22f))
            lineTo(cx - (w * 0.08f), neckY + (h * 0.08f))
            close()
        }
        val rightLapel = Path().apply {
            moveTo(cx + (w * 0.14f), neckY)
            lineTo(cx + (w * 0.18f), neckY + (h * 0.08f))
            lineTo(cx + (w * 0.05f), neckY + (h * 0.22f))
            lineTo(cx + (w * 0.01f), neckY + (h * 0.22f))
            lineTo(cx + (w * 0.08f), neckY + (h * 0.08f))
            close()
        }
        canvas.drawPath(leftLapel, lapelPaint)
        canvas.drawPath(rightLapel, lapelPaint)
    }

    private fun renderCollaredShirt(
        canvas: Canvas,
        w: Float,
        h: Float,
        cx: Float,
        neckY: Float,
        scale: Float,
        shirtColor: Int,
        collarColor: Int,
        shadowColor: Int,
        buttonColor: Int
    ) {
        val halfW = (w * 0.5f) * scale
        val shoulderLeft = cx - halfW * 1.15f
        val shoulderRight = cx + halfW * 1.15f

        // 1. Shirt Body
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shirtColor
            style = Paint.Style.FILL
        }
        val shirtBody = Path().apply {
            moveTo(cx - (w * 0.15f), neckY)
            quadTo(cx - (w * 0.32f), neckY + (h * 0.02f), shoulderLeft, neckY + (h * 0.13f))
            lineTo(shoulderLeft - 20f, h)
            lineTo(shoulderRight + 20f, h)
            lineTo(shoulderRight, neckY + (h * 0.13f))
            quadTo(cx + (w * 0.32f), neckY + (h * 0.02f), cx + (w * 0.15f), neckY)
            close()
        }
        canvas.drawPath(shirtBody, bodyPaint)

        // 2. Center Button Placket
        val placketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = collarColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(cx - (w * 0.025f), neckY + (h * 0.06f), cx + (w * 0.025f), h, placketPaint)

        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(cx - (w * 0.025f), neckY + (h * 0.06f), cx - (w * 0.025f), h, seamPaint)
        canvas.drawLine(cx + (w * 0.025f), neckY + (h * 0.06f), cx + (w * 0.025f), h, seamPaint)

        // Buttons
        val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = buttonColor
            style = Paint.Style.FILL
        }
        val btnBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            strokeWidth = 1.0f
            style = Paint.Style.STROKE
        }
        val btnR = w * 0.009f
        var btnY = neckY + (h * 0.12f)
        while (btnY < h - 10f) {
            canvas.drawCircle(cx, btnY, btnR, btnPaint)
            canvas.drawCircle(cx, btnY, btnR, btnBorder)
            btnY += (h * 0.08f)
        }

        // 3. Structured Collar Wings
        val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = collarColor
            style = Paint.Style.FILL
        }
        val leftCollar = Path().apply {
            moveTo(cx - (w * 0.15f), neckY)
            lineTo(cx - (w * 0.04f), neckY + (h * 0.085f))
            lineTo(cx - (w * 0.015f), neckY + (h * 0.035f))
            close()
        }
        val rightCollar = Path().apply {
            moveTo(cx + (w * 0.15f), neckY)
            lineTo(cx + (w * 0.04f), neckY + (h * 0.085f))
            lineTo(cx + (w * 0.015f), neckY + (h * 0.035f))
            close()
        }
        canvas.drawPath(leftCollar, collarPaint)
        canvas.drawPath(rightCollar, collarPaint)
        canvas.drawPath(leftCollar, seamPaint)
        canvas.drawPath(rightCollar, seamPaint)
    }
}
