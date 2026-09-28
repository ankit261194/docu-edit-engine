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
        val overrideClassification: FontClassification? = null
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

        val solidInk = ensureSolidInkColor(params.inkColorRgb)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = solidInk
            typeface = matchedFont.typeface
            style = Paint.Style.FILL
        }

        val fitResult = AutoFitFontCondenser.condenseToFit(
            text = params.newText,
            targetBounds = params.targetBounds,
            paint = paint
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

        return if (luma < 95) {
            // Document ink: make it rich and crisp so it doesn't look washed out
            val factor = 0.70f
            Color.rgb((r * factor).toInt(), (g * factor).toInt(), (b * factor).toInt())
        } else {
            sampledRgb
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
