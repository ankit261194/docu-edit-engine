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
        val originalText: String = "",
        val targetBounds: Rect,
        val inkColorRgb: Int,
        val rotationAngle: Float = 0f,
        val typographyMetrics: TypographyMetrics,
        val overrideClassification: FontClassification? = null,
        val isBold: Boolean? = null,
        val sizeMultiplier: Float = 1.0f,
        val alignment: Paint.Align = Paint.Align.LEFT
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
            text = if (params.originalText.isNotBlank()) params.originalText else params.newText,
            metrics = params.typographyMetrics,
            bounds = params.targetBounds,
            preferredClassification = params.overrideClassification,
            forceBold = params.isBold
        )

        val solidInk = ensureSolidInkColor(params.inkColorRgb)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = solidInk
            typeface = matchedFont.typeface
            isFakeBoldText = false // Never double-bold; matchedFont already has genuine typographer's bold TTF
            style = Paint.Style.FILL
        }

        val fitResult = AutoFitFontCondenser.condenseToFit(
            text = params.newText,
            targetBounds = params.targetBounds,
            paint = paint,
            originalText = params.originalText,
            sizeMultiplier = params.sizeMultiplier
        )

        val masterOutput = cleanedBackground.copy(Bitmap.Config.ARGB_8888, true)
        val masterCanvas = Canvas(masterOutput)

        val isAmount = isNumericOrCurrency(params.newText) || isNumericOrCurrency(params.originalText)
        val renderedWidth = paint.measureText(params.newText)
        val startX = when (params.alignment) {
            Paint.Align.RIGHT -> (params.targetBounds.right.toFloat() - renderedWidth).coerceAtLeast(params.targetBounds.left.toFloat())
            Paint.Align.CENTER -> params.targetBounds.left.toFloat() + (params.targetBounds.width() - renderedWidth) / 2f
            else -> {
                if (isAmount && renderedWidth < params.targetBounds.width()) {
                    params.targetBounds.right.toFloat() - renderedWidth
                } else {
                    params.targetBounds.left.toFloat()
                }
            }
        }
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

    private fun isNumericOrCurrency(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val numericOrSymbols = clean.count { it.isDigit() || it in "₹$€£.,%/-+:#" }
        return numericOrSymbols >= (clean.length * 0.70f) && clean.any { it.isDigit() }
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
