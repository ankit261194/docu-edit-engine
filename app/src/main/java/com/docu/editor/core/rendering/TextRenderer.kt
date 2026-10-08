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
            isFakeBoldText = matchedFont.isBold && !matchedFont.typeface.isBold
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
        val pivotX = params.targetBounds.exactCenterX()
        val pivotY = params.targetBounds.exactCenterY()

        val lines = fitResult.wrappedText.split("\n")
        val fontMetrics = paint.fontMetrics
        val lineHeight = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading

        val isHandwritten = matchedFont.classification.category == "Handwriting" ||
            matchedFont.classification.category == "Signature" ||
            matchedFont.classification == FontClassification.KALAM ||
            matchedFont.classification == FontClassification.CAVEAT ||
            matchedFont.classification == FontClassification.DANCING_SCRIPT

        if (isHandwritten && paint.textSkewX == 0f) {
            // Natural human pen slant
            paint.textSkewX = -0.10f
        }

        // 1. Render pristine text onto transparent overlay
        val textLayer = Bitmap.createBitmap(cleanedBackground.width, cleanedBackground.height, Bitmap.Config.ARGB_8888)
        val textCanvas = Canvas(textLayer)

        if (abs(params.rotationAngle) > 0.5f) {
            textCanvas.save()
            textCanvas.rotate(params.rotationAngle, pivotX, pivotY)
            for (i in lines.indices) {
                val line = lines[i]
                val lineWidth = paint.measureText(line)
                val lineStartX = when (params.alignment) {
                    Paint.Align.RIGHT -> (params.targetBounds.right.toFloat() - lineWidth).coerceAtLeast(params.targetBounds.left.toFloat())
                    Paint.Align.CENTER -> params.targetBounds.left.toFloat() + (params.targetBounds.width() - lineWidth) / 2f
                    else -> params.targetBounds.left.toFloat()
                }
                val lineY = fitResult.baselineY + (i * lineHeight)
                if (isHandwritten && line.length > 1) {
                    drawHandwrittenLineWithJitter(textCanvas, line, lineStartX, lineY, paint, fitResult.fontSize)
                } else {
                    textCanvas.drawText(line, lineStartX, lineY, paint)
                }
            }
            textCanvas.restore()
        } else {
            for (i in lines.indices) {
                val line = lines[i]
                val lineWidth = paint.measureText(line)
                val lineStartX = when (params.alignment) {
                    Paint.Align.RIGHT -> (params.targetBounds.right.toFloat() - lineWidth).coerceAtLeast(params.targetBounds.left.toFloat())
                    Paint.Align.CENTER -> params.targetBounds.left.toFloat() + (params.targetBounds.width() - lineWidth) / 2f
                    else -> params.targetBounds.left.toFloat()
                }
                val lineY = fitResult.baselineY + (i * lineHeight)
                if (isHandwritten && line.length > 1) {
                    drawHandwrittenLineWithJitter(textCanvas, line, lineStartX, lineY, paint, fitResult.fontSize)
                } else {
                    textCanvas.drawText(line, lineStartX, lineY, paint)
                }
            }
        }

        // 2. Analyze paper background texture & apply authentic toner micro-grain & edge bleed
        val paperStats = PaperTextureBlender.analyzeLocalPaperBackground(cleanedBackground, params.targetBounds)
        PaperTextureBlender.blendTextWithPaperTexture(masterCanvas, textLayer, params.targetBounds, paperStats)
        textLayer.recycle()

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
        val chroma = maxOf(r, g, b) - minOf(r, g, b)

        // 1. If ink has chromatic color (blue ballpoint pen, red stamp, green ink, violet seal, etc.),
        // PRESERVE the exact color 100%! Never force colored ink to black or charcoal!
        if (chroma > 15) {
            return sampledRgb
        }

        val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        return when {
            // Already authentic dark document ink (luminance 15 to 90): preserve authentic tone!
            luma in 15..90 -> sampledRgb
            // Artificial 0,0,0 pitch black: soften to natural laser printer dark charcoal #222428
            luma < 15 -> Color.rgb(34, 36, 40)
            // Faded or light gray document toner: keep sampled ink faithfully
            else -> sampledRgb
        }
    }

    private fun isNumericOrCurrency(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val numericOrSymbols = clean.count { it.isDigit() || it in "₹$€£.,%/-+:#" }
        return numericOrSymbols >= (clean.length * 0.70f) && clean.any { it.isDigit() }
    }

    private fun drawHandwrittenLineWithJitter(
        canvas: Canvas,
        text: String,
        startX: Float,
        baselineY: Float,
        paint: Paint,
        fontSize: Float
    ) {
        var currX = startX
        val maxJitter = (fontSize * 0.045f).coerceIn(0.5f, 2.2f)

        for (i in text.indices) {
            val charStr = text[i].toString()
            val wave = kotlin.math.sin(i * 1.6 + (text.hashCode() % 10)) * maxJitter
            val jitterY = baselineY + wave.toFloat()
            canvas.drawText(charStr, currX, jitterY, paint)
            currX += paint.measureText(charStr)
        }
    }
}
