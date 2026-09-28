package com.docu.editor.core.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
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

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = params.inkColorRgb
            typeface = matchedFont.typeface
            style = Paint.Style.FILL
        }

        val fitResult = AutoFitFontCondenser.condenseToFit(
            text = params.newText,
            targetBounds = params.targetBounds,
            paint = paint
        )

        paint.textSize = fitResult.fontSize
        paint.letterSpacing = fitResult.letterSpacingEm
        paint.textScaleX = fitResult.scaleX

        val startX = params.targetBounds.left.toFloat()

        val isolatedLayer = Bitmap.createBitmap(
            cleanedBackground.width,
            cleanedBackground.height,
            Bitmap.Config.ARGB_8888
        )
        val isolatedCanvas = Canvas(isolatedLayer)

        val masterOutput = cleanedBackground.copy(Bitmap.Config.ARGB_8888, true)
        val masterCanvas = Canvas(masterOutput)

        val pivotX = params.targetBounds.exactCenterX()
        val pivotY = params.targetBounds.exactCenterY()

        drawRotatedText(isolatedCanvas, params.newText, startX, fitResult.baselineY, params.rotationAngle, pivotX, pivotY, paint)
        drawRotatedText(masterCanvas, params.newText, startX, fitResult.baselineY, params.rotationAngle, pivotX, pivotY, paint)

        return TextRenderResult(
            outputBitmap = masterOutput,
            isolatedTextLayer = isolatedLayer,
            fittedFontSize = fitResult.fontSize,
            appliedLetterSpacing = fitResult.letterSpacingEm,
            appliedScaleX = fitResult.scaleX
        )
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
        if (abs(angle) > 0.05f) {
            canvas.save()
            canvas.rotate(angle, pivotX, pivotY)
            canvas.drawText(text, x, y, paint)
            canvas.restore()
        } else {
            canvas.drawText(text, x, y, paint)
        }
    }
}
