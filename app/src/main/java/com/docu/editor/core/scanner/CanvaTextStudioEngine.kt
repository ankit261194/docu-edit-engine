package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import com.docu.editor.domain.model.MagicWriteMode
import com.docu.editor.domain.model.TextEffectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Canva Pro Flagship Text Studio & Magic Write Engine.
 * Supports vector typography with Neon Glow, 3D Shadow, Retro Glitch,
 * Arched curved paths, Outlines, Gradient Fills, and on-device NLP Magic Write.
 */
object CanvaTextStudioEngine {

    fun createStyledTypographyBitmap(
        text: String,
        textColor: Int = Color.BLACK,
        backgroundColor: Int? = null,
        fontSize: Float = 44f,
        isBold: Boolean = true,
        isItalic: Boolean = false,
        fontFamily: String = "Sans-Serif",
        effect: TextEffectType = TextEffectType.NONE
    ): Bitmap {
        val safeText = if (text.isBlank()) "Type Here" else text

        val basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = textColor
            textSize = fontSize
            val style = when {
                isBold && isItalic -> Typeface.BOLD_ITALIC
                isBold -> Typeface.BOLD
                isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val baseTypeface = when (fontFamily) {
                "Serif" -> Typeface.SERIF
                "Monospace" -> Typeface.MONOSPACE
                "Cursive" -> Typeface.create("cursive", Typeface.NORMAL)
                else -> Typeface.SANS_SERIF
            }
            typeface = Typeface.create(baseTypeface, style)
            letterSpacing = 0.05f
        }

        val fontMetrics = basePaint.fontMetrics
        val textWidth = basePaint.measureText(safeText)
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val padX = if (backgroundColor != null || effect == TextEffectType.NEON) 48f else 18f
        val padY = if (backgroundColor != null || effect == TextEffectType.NEON) 32f else 14f

        val totalWidth = (textWidth + padX * 2).toInt().coerceAtLeast(60)
        val totalHeight = (textHeight + padY * 2).toInt().coerceAtLeast(40)

        val bmp = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Optional Background Pill
        if (backgroundColor != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor
                this.style = Paint.Style.FILL
            }
            val rect = RectF(0f, 0f, totalWidth.toFloat(), totalHeight.toFloat())
            val cornerRadius = 16f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        }

        val baseline = padY - fontMetrics.ascent

        // 2. Render Text based on Effect
        when (effect) {
            TextEffectType.NONE -> {
                canvas.drawText(safeText, padX, baseline, basePaint)
            }
            TextEffectType.NEON -> {
                // Intense Multi-Layered Neon Glow
                val glowPaint = Paint(basePaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 14f
                    maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
                    color = textColor
                    alpha = 180
                }
                canvas.drawText(safeText, padX, baseline, glowPaint)

                glowPaint.strokeWidth = 6f
                glowPaint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
                glowPaint.alpha = 230
                canvas.drawText(safeText, padX, baseline, glowPaint)

                // White-hot core
                val corePaint = Paint(basePaint).apply {
                    color = Color.WHITE
                }
                canvas.drawText(safeText, padX, baseline, corePaint)
            }
            TextEffectType.GLITCH -> {
                // Retro VHS Chromatic Aberration
                val cyanPaint = Paint(basePaint).apply {
                    color = Color.rgb(0, 240, 255)
                    alpha = 200
                }
                canvas.drawText(safeText, padX - 5f, baseline, cyanPaint)

                val magentaPaint = Paint(basePaint).apply {
                    color = Color.rgb(255, 0, 110)
                    alpha = 200
                }
                canvas.drawText(safeText, padX + 5f, baseline, magentaPaint)

                canvas.drawText(safeText, padX, baseline, basePaint)
            }
            TextEffectType.SHADOW_3D -> {
                // Deep Isometric Extrusion Shadow
                val shadowSteps = 6
                val shadowPaint = Paint(basePaint).apply {
                    color = Color.argb(120, 20, 20, 25)
                }
                for (step in shadowSteps downTo 1) {
                    canvas.drawText(safeText, padX + step * 2f, baseline + step * 2.5f, shadowPaint)
                }
                canvas.drawText(safeText, padX, baseline, basePaint)
            }
            TextEffectType.HOLLOW_OUTLINE -> {
                val outlinePaint = Paint(basePaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    color = textColor
                }
                canvas.drawText(safeText, padX, baseline, outlinePaint)
            }
            TextEffectType.DUAL_GRADIENT -> {
                val gradPaint = Paint(basePaint).apply {
                    shader = LinearGradient(
                        padX, baseline - textHeight,
                        padX + textWidth, baseline,
                        intArrayOf(Color.rgb(139, 92, 246), Color.rgb(6, 182, 212), Color.rgb(244, 63, 94)),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawText(safeText, padX, baseline, gradPaint)
            }
            TextEffectType.CURVED_ARC -> {
                // Renders text along an arched curve
                val arcPath = Path().apply {
                    val r = totalWidth * 0.9f
                    val arcRect = RectF(padX, baseline - textHeight * 0.4f, padX + textWidth, baseline + textHeight * 1.5f)
                    arcTo(arcRect, 190f, 160f, true)
                }
                canvas.drawTextOnPath(safeText, arcPath, 0f, 0f, basePaint)
            }
        }

        return bmp
    }

    /**
     * Canva Magic Write: Instant on-device NLP transformation engine.
     */
    suspend fun magicWrite(inputText: String, mode: MagicWriteMode): String = withContext(Dispatchers.Default) {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) return@withContext "Please type some text first."

        when (mode) {
            MagicWriteMode.POLISH -> {
                // Fix capitalization, spaces, punctuation
                val sentences = trimmed.split(Regex("(?<=[.!?])\\s+"))
                sentences.joinToString(" ") { sentence ->
                    val s = sentence.trim()
                    if (s.isEmpty()) ""
                    else s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }.let { if (!it.endsWith(".") && !it.endsWith("!") && !it.endsWith("?")) "$it." else it }
            }

            MagicWriteMode.PROFESSIONAL -> {
                // Executive business tone transformer
                var result = trimmed
                val replacements = mapOf(
                    "i want" to "We intend to",
                    "can you" to "Please be advised to",
                    "thanks" to "We sincerely appreciate your prompt cooperation",
                    "need to" to "It is imperative that we",
                    "asap" to "at your earliest convenience",
                    "good" to "exemplary",
                    "bad" to "suboptimal",
                    "problem" to "challenge",
                    "talk about" to "deliberate upon",
                    "fix" to "rectify"
                )
                replacements.forEach { (casual, formal) ->
                    result = result.replace(Regex("(?i)\\b$casual\\b"), formal)
                }
                result = result.replaceFirstChar { it.uppercase() }
                if (!result.endsWith(".")) result += "."
                result
            }

            MagicWriteMode.HEADLINE -> {
                // Transform into punchy hook title
                val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
                val lead = words.take(6).joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
                "⚡ Elevate Your Impact: $lead"
            }

            MagicWriteMode.SUMMARIZE -> {
                val sentences = trimmed.split(Regex("(?<=[.!?])\\s+"))
                if (sentences.size <= 2) {
                    "📌 Key Takeaway: $trimmed"
                } else {
                    val summary = sentences.take(2).joinToString(" ")
                    "📌 Executive Summary:\n• $summary"
                }
            }

            MagicWriteMode.BULLET_POINTS -> {
                val items = trimmed.split(Regex("[,;\\n]|(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
                val bullets = items.take(6).joinToString("\n") { "✔ $it" }
                "📋 Action Items:\n$bullets"
            }

            MagicWriteMode.CATCHY_CAPTION -> {
                val hashtags = "#Design #CanvaPro #Excellence #DocumentEdit #Workflow"
                "✨ $trimmed\n\n$hashtags"
            }
        }
    }
}
