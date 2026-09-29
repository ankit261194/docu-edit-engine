package com.docu.editor.core.font

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import com.docu.editor.core.ocr.model.FontWeightEstimate
import com.docu.editor.core.ocr.model.TypographyMetrics
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

enum class FontClassification(
    val id: String,
    val displayName: String,
    val category: String = "Basic"
) {
    // Bundled Offline Assets
    SANS_SERIF("arial", "Arial / Standard", "Basic"),
    CALIBRI("calibri", "Calibri / Office", "Basic"),
    SERIF("times", "Times New Roman / Formal", "Basic"),
    MONOSPACE("cour", "Courier / Receipt", "Basic"),
    DEVANAGARI("mangal", "Mangal / Hindi", "Hindi"),

    // Cloud Hosted on shribalajikripadham.online (On-demand cached)
    ROBOTO("roboto", "Roboto / Android", "Clean Sans"),
    POPPINS("poppins", "Poppins / Modern", "Clean Sans"),
    MONTSERRAT("montserrat", "Montserrat / Header", "Clean Sans"),
    LATO("lato", "Lato / Clear", "Clean Sans"),
    OSWALD("oswald", "Oswald / Condensed", "Clean Sans"),
    MERRIWEATHER("merriweather", "Merriweather / Editorial", "Classic Serif"),
    PLAYFAIR("playfair", "Playfair / Certificate", "Classic Serif"),
    LORA("lora", "Lora / Elegant", "Classic Serif"),
    KALAM("kalam", "Kalam / Hindi Pen", "Handwriting"),
    CAVEAT("caveat", "Caveat / Casual Script", "Handwriting"),
    DANCING_SCRIPT("dancingscript", "Dancing Script / Signature", "Handwriting"),
    INCONSOLATA("inconsolata", "Inconsolata / Code", "Basic"),
    HIND("hind", "Hind / Hindi Official", "Hindi")
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
            metrics.estimatedFontWeight == FontWeightEstimate.MEDIUM ||
            metrics.strokeWidthRatio >= 0.11f ||
            metrics.glyphDensity >= 0.22f
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
                else -> {
                    // Check local disk cache on phone first
                    val cached = RemoteFontManager.getCachedTypeface(context, classification.id, isBold)
                    if (cached != null) {
                        cached
                    } else {
                        // Trigger background download so next time it is ready
                        RemoteFontManager.fetchFontAsync(context, classification.id, isBold) { newTf ->
                            typefaceCache[cacheKey] = newTf
                        }
                        // Immediate fallback while downloading: nearest bundled font
                        when (classification.category) {
                            "Classic Serif" -> getDocumentTypeface(FontClassification.SERIF, isBold)
                            "Hindi" -> getDocumentTypeface(FontClassification.DEVANAGARI, isBold)
                            "Basic" -> getDocumentTypeface(FontClassification.MONOSPACE, isBold)
                            else -> getDocumentTypeface(FontClassification.SANS_SERIF, isBold)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to Android system fonts if asset is missing
            val sysFamily = when (classification) {
                FontClassification.SERIF, FontClassification.MERRIWEATHER, FontClassification.PLAYFAIR, FontClassification.LORA -> Typeface.SERIF
                FontClassification.MONOSPACE, FontClassification.INCONSOLATA -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
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
                // 2. Calibri: compact modern office font (narrower proportions)
                charAspectRatio < 0.44f -> {
                    FontClassification.CALIBRI
                }
                // 3. Sans-serif: standard Arial (default for 95%+ of business invoices, forms, and documents)
                else -> {
                    FontClassification.SANS_SERIF
                }
            }
        }
    }
}
