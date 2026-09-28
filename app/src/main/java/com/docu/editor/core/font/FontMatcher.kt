package com.docu.editor.core.font

import android.content.Context
import android.graphics.Typeface
import com.docu.editor.core.ocr.model.FontWeightEstimate
import com.docu.editor.core.ocr.model.TypographyMetrics
import java.util.concurrent.ConcurrentHashMap

enum class FontClassification {
    SANS_SERIF,
    SERIF,
    MONOSPACE
}

class FontMatcher(private val context: Context) {

    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    data class MatchedTypeface(
        val typeface: Typeface,
        val classification: FontClassification,
        val isBold: Boolean,
        val fontIdentifier: String
    )

    fun matchFont(
        text: String,
        metrics: TypographyMetrics,
        preferredClassification: FontClassification? = null
    ): MatchedTypeface {
        val classification = preferredClassification ?: classifyFromMetrics(text, metrics)
        val isBold = metrics.estimatedFontWeight == FontWeightEstimate.BOLD ||
                     metrics.estimatedFontWeight == FontWeightEstimate.EXTRA_BOLD

        val typeface = getSystemFallbackTypeface(classification, isBold)

        return MatchedTypeface(
            typeface = typeface,
            classification = classification,
            isBold = isBold,
            fontIdentifier = "System:${classification.name}"
        )
    }

    private fun classifyFromMetrics(text: String, metrics: TypographyMetrics): FontClassification {
        return when {
            metrics.strokeWidthRatio < 0.10f && metrics.letterSpacingEm > 0.15f -> FontClassification.MONOSPACE
            metrics.glyphDensity > 0.32f -> FontClassification.SERIF
            else -> FontClassification.SANS_SERIF
        }
    }

    private fun getSystemFallbackTypeface(classification: FontClassification, isBold: Boolean): Typeface {
        val baseFamily = when (classification) {
            FontClassification.SERIF -> Typeface.SERIF
            FontClassification.SANS_SERIF -> Typeface.SANS_SERIF
            FontClassification.MONOSPACE -> Typeface.MONOSPACE
        }
        val style = if (isBold) Typeface.BOLD else Typeface.NORMAL
        return Typeface.create(baseFamily, style)
    }
}
