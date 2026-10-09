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
        val alignment: Paint.Align = Paint.Align.LEFT,
        val availableWidth: Float? = null,
        // Pro Camera Photo & Realism Tuning:
        val cameraBlurSigma: Float = 0.0f,
        val paperBlendStrength: Float = 0.0f,
        val baselineNudgePx: Float = 0f,
        val inkToneDarkness: Float = 1.0f,
        val lockedBaselineY: Float? = null,
        val lineReferenceHeightPx: Float? = null
    )

    data class TextRenderResult(
        val outputBitmap: Bitmap,
        val isolatedTextLayer: Bitmap,
        val fittedFontSize: Float,
        val appliedLetterSpacing: Float,
        val appliedScaleX: Float,
        val renderedWidth: Float = 0f,
        val renderedHeight: Float = 0f,
        val renderedBounds: Rect = Rect()
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

        val numericWeight = params.typographyMetrics.numericFontWeight
        val baseTypeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                android.graphics.Typeface.create(matchedFont.typeface, numericWeight, false)
            } catch (_: Exception) {
                matchedFont.typeface
            }
        } else {
            matchedFont.typeface
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = solidInk
            typeface = baseTypeface
            isFakeBoldText = matchedFont.isBold && !baseTypeface.isBold
            if (numericWeight >= 650 && !isFakeBoldText) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = ((numericWeight - 500) / 1000f) * 0.8f
            } else {
                style = Paint.Style.FILL
            }
        }

        val fitResult = AutoFitFontCondenser.condenseToFit(
            text = params.newText,
            targetBounds = params.targetBounds,
            paint = paint,
            originalText = params.originalText,
            sizeMultiplier = params.sizeMultiplier,
            availableWidth = params.availableWidth,
            baselineNudgePx = params.baselineNudgePx,
            lockedBaselineY = params.lockedBaselineY,
            lineReferenceHeightPx = params.lineReferenceHeightPx
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

        // 1.5 Authentic Dot-Matrix 9-Pin / 24-Pin Cash Bill Synthesis
        val finalRenderLayer = if (matchedFont.classification == FontClassification.DOT_MATRIX) {
            val pinDiameter = (fitResult.fontSize * 0.11f).coerceIn(1.5f, 5.0f)
            val dotLayer = applyDotMatrixEffect(textLayer, params.targetBounds, pinDiameter, solidInk)
            textLayer.recycle()
            dotLayer
        } else {
            textLayer
        }

        val maxMeasuredWidth = lines.maxOfOrNull { paint.measureText(it) } ?: paint.measureText(params.newText)
        val renderedTotalHeight = (lines.size * lineHeight).coerceAtLeast(params.targetBounds.height().toFloat())
        val renderedLeft = when (params.alignment) {
            Paint.Align.RIGHT -> (params.targetBounds.right.toFloat() - maxMeasuredWidth).toInt().coerceAtLeast(0)
            Paint.Align.CENTER -> (params.targetBounds.left.toFloat() + (params.targetBounds.width() - maxMeasuredWidth) / 2f).toInt()
            else -> params.targetBounds.left
        }
        val renderedRight = (renderedLeft + maxMeasuredWidth.toInt()).coerceAtMost(cleanedBackground.width)
        val renderedTop = minOf(params.targetBounds.top, (fitResult.baselineY + fontMetrics.ascent).toInt().coerceAtLeast(0))
        val renderedBottom = maxOf(
            params.targetBounds.bottom,
            (fitResult.baselineY + (lines.size - 1) * lineHeight + fontMetrics.descent).toInt().coerceAtMost(cleanedBackground.height)
        )
        val unrotatedBounds = Rect(renderedLeft, renderedTop, renderedRight, renderedBottom)

        // Compute enclosing bounding box if text is rotated
        val fullRenderBounds = if (abs(params.rotationAngle) > 0.5f) {
            val rad = Math.toRadians(params.rotationAngle.toDouble())
            val cosA = kotlin.math.cos(rad)
            val sinA = kotlin.math.sin(rad)
            val corners = listOf(
                Pair(unrotatedBounds.left.toFloat(), unrotatedBounds.top.toFloat()),
                Pair(unrotatedBounds.right.toFloat(), unrotatedBounds.top.toFloat()),
                Pair(unrotatedBounds.right.toFloat(), unrotatedBounds.bottom.toFloat()),
                Pair(unrotatedBounds.left.toFloat(), unrotatedBounds.bottom.toFloat())
            )
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for ((cx, cy) in corners) {
                val dx = cx - pivotX
                val dy = cy - pivotY
                val rx = pivotX + (dx * cosA - dy * sinA).toFloat()
                val ry = pivotY + (dx * sinA + dy * cosA).toFloat()
                minX = minOf(minX, rx)
                minY = minOf(minY, ry)
                maxX = maxOf(maxX, rx)
                maxY = maxOf(maxY, ry)
            }
            Rect(
                minX.toInt().coerceIn(0, cleanedBackground.width),
                minY.toInt().coerceIn(0, cleanedBackground.height),
                maxX.toInt().coerceIn(0, cleanedBackground.width),
                maxY.toInt().coerceIn(0, cleanedBackground.height)
            )
        } else {
            unrotatedBounds
        }

        // 2. Analyze paper background texture & apply authentic optical camera blur, toner micro-grain & edge bleed
        val paperStats = PaperTextureBlender.analyzeLocalPaperBackground(cleanedBackground, params.targetBounds)
        val effectiveBlur = if (params.cameraBlurSigma >= 0f) params.cameraBlurSigma else paperStats.estimatedBlurSigma
        PaperTextureBlender.blendTextWithPaperTexture(
            masterCanvas = masterCanvas,
            textLayerBitmap = finalRenderLayer,
            targetBounds = params.targetBounds,
            stats = paperStats,
            cameraBlurSigma = effectiveBlur,
            paperBlendStrength = params.paperBlendStrength,
            inkToneDarkness = params.inkToneDarkness,
            renderBounds = fullRenderBounds
        )
        finalRenderLayer.recycle()

        return TextRenderResult(
            outputBitmap = masterOutput,
            isolatedTextLayer = masterOutput,
            fittedFontSize = fitResult.fontSize,
            appliedLetterSpacing = fitResult.letterSpacingEm,
            appliedScaleX = fitResult.scaleX,
            renderedWidth = maxMeasuredWidth,
            renderedHeight = renderedTotalHeight,
            renderedBounds = fullRenderBounds
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

        // Grayscale / black ink: preserve sampled tone directly!
        // On computer prints and laser documents, pure black (#000000) is razor authentic and must never be lightened to gray!
        return sampledRgb
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

    private fun applyDotMatrixEffect(
        sourceLayer: Bitmap,
        targetBounds: Rect,
        pinDiameterPx: Float,
        inkColor: Int
    ): Bitmap {
        val w = sourceLayer.width
        val h = sourceLayer.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            style = Paint.Style.FILL
        }

        val pitch = (pinDiameterPx * 1.35f).coerceAtLeast(2.0f)
        val radius = pinDiameterPx * 0.5f

        val pad = 12
        val left = (targetBounds.left - pad).coerceIn(0, w - 1)
        val top = (targetBounds.top - pad).coerceIn(0, h - 1)
        val right = (targetBounds.right + pad).coerceIn(left + 1, w)
        val bottom = (targetBounds.bottom + pad).coerceIn(top + 1, h)

        var y = top.toFloat() + radius
        while (y < bottom) {
            var x = left.toFloat() + radius
            while (x < right) {
                val px = x.toInt()
                val py = y.toInt()
                if (px in 0 until w && py in 0 until h) {
                    val pixel = sourceLayer.getPixel(px, py)
                    val alpha = (pixel ushr 24)
                    if (alpha > 60) {
                        pinPaint.alpha = (alpha * 0.95f).toInt().coerceIn(100, 255)
                        canvas.drawCircle(x, y, radius, pinPaint)
                    }
                }
                x += pitch
            }
            y += pitch
        }
        return output
    }
}
