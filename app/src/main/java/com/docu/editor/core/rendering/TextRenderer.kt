package com.docu.editor.core.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.docu.editor.core.font.FontClassification
import com.docu.editor.core.font.FontMatcher
import com.docu.editor.core.ocr.model.TypographyMetrics
import kotlin.math.abs

class TextRenderer(private val fontMatcher: FontMatcher) {

    data class TextRenderParams(
        val newText: String,
        val targetBounds: Rect,
        val inkColorRgb: Int,
        val rotationAngle: Float = 0f,
        val typographyMetrics: TypographyMetrics,
        val overrideClassification: FontClassification? = null,
        val isBold: Boolean = true,
        val sizeMultiplier: Float = 1.0f
    )

    data class TextRenderResult(
        val outputBitmap: Bitmap,
        val isolatedTextLayer: Bitmap,
        val fittedFontSize: Float,
        val appliedLetterSpacing: Float,
        val appliedScaleX: Float
    )

    fun render(
        cleanedBackground: Bitmap,
        params: TextRenderParams
    ): TextRenderResult {
        val matchedFont = fontMatcher.matchFont(
            text = params.newText,
            metrics = params.typographyMetrics,
            preferredClassification = params.overrideClassification
        )

        val baseTypeface = matchedFont.typeface
        val activeTypeface = if (params.isBold) {
            android.graphics.Typeface.create(baseTypeface, android.graphics.Typeface.BOLD)
        } else {
            android.graphics.Typeface.create(baseTypeface, android.graphics.Typeface.NORMAL)
        }

        val solidInk = ensureSolidInkColor(params.inkColorRgb)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = solidInk
            typeface = activeTypeface
            isFakeBoldText = false // Never double-bold; activeTypeface already has clean typographic bolding
            style = Paint.Style.FILL
        }

        val fitResult = AutoFitFontCondenser.condenseToFit(
            text = params.newText,
            targetBounds = params.targetBounds,
            paint = paint,
            sizeMultiplier = params.sizeMultiplier
        )

        val masterOutput = cleanedBackground.copy(Bitmap.Config.ARGB_8888, true)
        val masterCanvas = Canvas(masterOutput)

        val startX = params.targetBounds.left.toFloat()
        val pivotX = params.targetBounds.exactCenterX()
        val pivotY = params.targetBounds.exactCenterY()

        drawRotatedText(
            canvas = masterCanvas,
            text = params.newText,
            x = startX,
            y = fitResult.baselineY,
            angle = params.rotationAngle,
            pivotX = pivotX,
            pivotY = pivotY,
            paint = paint
        )

        return TextRenderResult(
            outputBitmap = masterOutput,
            isolatedTextLayer = masterOutput,
            fittedFontSize = fitResult.fontSize,
            appliedLetterSpacing = fitResult.letterSpacingEm,
            appliedScaleX = fitResult.scaleX
        )
    }

    private fun ensureSolidInkColor(sampledRgb: Int): Int {
        val r = Color.red(sampledRgb)
        val g = Color.green(sampledRgb)
        val b = Color.blue(sampledRgb)
        val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

        return when {
            // Already authentic dark document ink (luminance 20 to 80): keep it authentic!
            luma in 20..80 -> sampledRgb
            // Artificial 0,0,0 pitch black: soften to natural laser printer dark charcoal #222428
            luma < 20 -> Color.rgb(34, 36, 40)
            // Faded/washed out (81 to 145): enhance naturally to solid document ink #282828
            luma in 81..145 -> Color.rgb(40, 42, 48)
            else -> sampledRgb
        }
    }

    private fun drawRotatedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        angle: Float,
        pivotX: Float,
        pivotY: Float,
        paint: Paint
    ) {
        if (abs(angle) > 0.5f) {
            canvas.save()
            canvas.rotate(angle, pivotX, pivotY)
            canvas.drawText(text, x, y, paint)
            canvas.restore()
        } else {
            canvas.drawText(text, x, y, paint)
        }
    }
}
