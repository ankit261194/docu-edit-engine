package com.docu.editor.core.font

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import com.docu.editor.core.ocr.model.FontWeightEstimate
import com.docu.editor.core.ocr.model.TypographyMetrics
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

enum class FontClassification(val displayName: String) {
    SANS_SERIF("Arial / Standard"),
    CALIBRI("Calibri / Office"),
    SERIF("Times New Roman / Formal"),
    MONOSPACE("Courier / Receipt"),
    DEVANAGARI("Mangal / Hindi")
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
        bounds: Rect? = null,
        preferredClassification: FontClassification? = null,
        forceBold: Boolean? = null
    ): MatchedTypeface {
        val classification = preferredClassification ?: classifyFromMetrics(text, metrics, bounds)
        val isBold = forceBold ?: (
            metrics.estimatedFontWeight == FontWeightEstimate.BOLD ||
            metrics.estimatedFontWeight == FontWeightEstimate.EXTRA_BOLD ||
            metrics.strokeWidthRatio >= 0.14f
        )

        val typeface = getDocumentTypeface(classification, isBold)

        return MatchedTypeface(
            typeface = typeface,
            classification = classification,
            isBold = isBold,
            fontIdentifier = "${classification.name}:${if (isBold) "Bold" else "Regular"}"
        )
    }

    fun getDocumentTypeface(classification: FontClassification, isBold: Boolean): Typeface {
        val cacheKey = "${classification.name}_$isBold"
        typefaceCache[cacheKey]?.let { return it }

        val loaded = try {
            when (classification) {
                FontClassification.SANS_SERIF -> {
                    val assetName = if (isBold) "fonts/arialbd.ttf" else "fonts/arial.ttf"
                    Typeface.createFromAsset(context.assets, assetName)
                }
                FontClassification.CALIBRI -> {
                    val base = Typeface.createFromAsset(context.assets, "fonts/calibri.ttf")
                    if (isBold) Typeface.create(base, Typeface.BOLD) else base
                }
                FontClassification.SERIF -> {
                    val assetName = if (isBold) "fonts/timesbd.ttf" else "fonts/times.ttf"
                    Typeface.createFromAsset(context.assets, assetName)
                }
                FontClassification.MONOSPACE -> {
                    val assetName = if (isBold) "fonts/courbd.ttf" else "fonts/cour.ttf"
                    Typeface.createFromAsset(context.assets, assetName)
                }
                FontClassification.DEVANAGARI -> {
                    val assetName = if (isBold) "fonts/mangalb.ttf" else "fonts/mangal.ttf"
                    try {
                        Typeface.createFromAsset(context.assets, assetName)
                    } catch (_: Exception) {
                        val nirmalaAsset = if (isBold) "fonts/nirmalab.ttf" else "fonts/nirmala.ttf"
                        Typeface.createFromAsset(context.assets, nirmalaAsset)
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to Android system fonts if asset is missing
            val sysFamily = when (classification) {
                FontClassification.SERIF -> Typeface.SERIF
                FontClassification.SANS_SERIF, FontClassification.CALIBRI, FontClassification.DEVANAGARI -> Typeface.SANS_SERIF
                FontClassification.MONOSPACE -> Typeface.MONOSPACE
            }
            Typeface.create(sysFamily, if (isBold) Typeface.BOLD else Typeface.NORMAL)
        }

        typefaceCache[cacheKey] = loaded
        return loaded
    }

    companion object {
        fun classifyFromMetrics(
            text: String,
            metrics: TypographyMetrics,
            bounds: Rect? = null
        ): FontClassification {
            // 0. Devanagari Hindi Script Detection (Unicode \u0900..\u097F)
            if (text.any { it in '\u0900'..'\u097F' }) {
                return FontClassification.DEVANAGARI
            }

            val avgCharWidth = if (bounds != null && bounds.width() > 0) {
                bounds.width().toFloat() / max(1, text.length)
            } else {
                metrics.estimatedFontSizePx * 0.52f
            }
            val height = if (bounds != null && bounds.height() > 0) bounds.height().toFloat() else metrics.estimatedFontSizePx
            val charAspectRatio = avgCharWidth / max(1f, height)

            return when {
                // 1. Monospace: fixed pitch typewriter numbers/code
                (metrics.strokeWidthRatio < 0.10f && metrics.letterSpacingEm > 0.14f) ||
                (text.all { it.isDigit() || it == '-' || it == '/' || it == '.' } && charAspectRatio > 0.58f) -> {
                    FontClassification.MONOSPACE
                }
                // 2. Serif: certificates, formal letters, high density / stroke modulation
                metrics.glyphDensity > 0.32f -> {
                    FontClassification.SERIF
                }
                // 3. Calibri: compact modern office font (narrower proportions)
                charAspectRatio < 0.48f -> {
                    FontClassification.CALIBRI
                }
                // 4. Arial: standard 80% business invoices and forms
                else -> {
                    FontClassification.SANS_SERIF
                }
            }
        }
    }
}
