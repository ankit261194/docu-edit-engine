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
    NIRMALA("nirmala", "Nirmala / Hindi Govt", "Hindi"),
    TYPEWRITER("typewriter", "Typewriter / Stamp Paper", "Mechanical"),
    DOT_MATRIX("dotmatrix", "Dot-Matrix / Cash Bill", "Mechanical"),
    CONSOLAS("consolas", "Consolas / Numbers", "Mechanical"),
    OCR_B("ocrb", "OCR-B / Bank Cheque", "Mechanical"),
    GEORGIA("georgia", "Georgia / Certificate", "Classic Serif"),

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
    ARCHITECTS_DAUGHTER("architectsdaughter", "Architects Daughter / Pencil Notes", "Handwriting"),
    MARCK_SCRIPT("marckscript", "Marck Script / Cursive Script", "Handwriting"),
    CUSTOM("custom", "Custom Imported Font (.ttf)", "Handwriting"),
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
                FontClassification.MONOSPACE, FontClassification.TYPEWRITER -> {
                    val assetName = if (isBold) "fonts/courbd.ttf" else "fonts/cour.ttf"
                    Typeface.createFromAsset(context.assets, assetName)
                }
                FontClassification.DOT_MATRIX, FontClassification.OCR_B, FontClassification.CONSOLAS -> {
                    Typeface.create(Typeface.MONOSPACE, if (isBold) Typeface.BOLD else Typeface.NORMAL)
                }
                FontClassification.GEORGIA -> {
                    val base = Typeface.createFromAsset(context.assets, if (isBold) "fonts/timesbd.ttf" else "fonts/times.ttf")
                    Typeface.create(base, if (isBold) Typeface.BOLD else Typeface.NORMAL)
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
                FontClassification.NIRMALA -> {
                    val assetName = if (isBold) "fonts/nirmalab.ttf" else "fonts/nirmala.ttf"
                    Typeface.createFromAsset(context.assets, assetName)
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
                            "Mechanical" -> getDocumentTypeface(FontClassification.MONOSPACE, isBold)
                            "Basic" -> getDocumentTypeface(FontClassification.MONOSPACE, isBold)
                            else -> getDocumentTypeface(FontClassification.SANS_SERIF, isBold)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to Android system fonts if asset is missing
            val sysFamily = when (classification) {
                FontClassification.SERIF, FontClassification.MERRIWEATHER, FontClassification.PLAYFAIR, FontClassification.LORA, FontClassification.GEORGIA -> Typeface.SERIF
                FontClassification.MONOSPACE, FontClassification.INCONSOLATA, FontClassification.TYPEWRITER, FontClassification.DOT_MATRIX, FontClassification.OCR_B, FontClassification.CONSOLAS -> Typeface.MONOSPACE
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
            bounds: Rect? = null,
            documentDominantFont: FontClassification? = null
        ): FontClassification {
            // 0. Devanagari Hindi Script Detection (Unicode \u0900..\u097F)
            if (text.any { it in '\u0900'..'\u097F' }) {
                return FontClassification.DEVANAGARI
            }

            // 1. Bank Cheque / IFSC / MICR Code / Passport MRZ detection
            if (text.matches(Regex("^[A-Z]{4}0[A-Z0-9]{6}$")) ||
                text.matches(Regex("^[0-9]{9,16}$")) ||
                text.contains("<<<")
            ) {
                return FontClassification.OCR_B
            }

            val avgCharWidth = if (bounds != null && bounds.width() > 0) {
                bounds.width().toFloat() / max(1, text.length)
            } else {
                metrics.estimatedFontSizePx * 0.52f
            }
            val height = if (bounds != null && bounds.height() > 0) bounds.height().toFloat() else metrics.estimatedFontSizePx
            val charAspectRatio = avgCharWidth / max(1f, height)

            // 2. Dot-Matrix / Receipt / Cash Bill numbers (fixed-pitch monospace numbers)
            val isBillOrReceipt = text.contains("TOTAL", ignoreCase = true) ||
                text.contains("TAX", ignoreCase = true) ||
                text.contains("BILL", ignoreCase = true) ||
                text.contains("INV-", ignoreCase = true) ||
                text.contains("CHALLAN", ignoreCase = true) ||
                (text.all { it.isDigit() || it in "₹$.,-/#: " } && text.length >= 4 && charAspectRatio > 0.54f)

            if (isBillOrReceipt) {
                return FontClassification.DOT_MATRIX
            }

            // 2.5 Medical, ultrasound, lab, and formal legal document keywords (Universal Times New Roman)
            val isFormalOrMedicalDocument = text.contains("USG", ignoreCase = true) ||
                text.contains("ABDOMEN", ignoreCase = true) ||
                text.contains("REPORT", ignoreCase = true) ||
                text.contains("ECHO", ignoreCase = true) ||
                text.contains("DOCTOR", ignoreCase = true) ||
                text.contains("DR.", ignoreCase = true) ||
                text.contains("HOSPITAL", ignoreCase = true) ||
                text.contains("PATIENT", ignoreCase = true) ||
                text.contains("CLINIC", ignoreCase = true) ||
                text.contains("IMPRESSION", ignoreCase = true) ||
                text.contains("SONOGRAPHY", ignoreCase = true) ||
                text.contains("CERTIFICATE", ignoreCase = true) ||
                text.contains("AGREEMENT", ignoreCase = true)

            if (isFormalOrMedicalDocument && !isBillOrReceipt) {
                return FontClassification.SERIF
            }

            // If the document has an established dominant font (e.g. Serif document), honor it
            if (documentDominantFont != null && (metrics.isSerif || documentDominantFont == FontClassification.SERIF)) {
                return documentDominantFont
            }

            return when {
                // 3. Serif: Times New Roman / Formal documents, legal certificates, agreements
                metrics.isSerif -> {
                    FontClassification.SERIF
                }
                // 4. Typewriter: fixed pitch typewriter numbers/code
                (metrics.strokeWidthRatio < 0.10f && metrics.letterSpacingEm > 0.14f) ||
                (text.all { it.isDigit() || it == '-' || it == '/' || it == '.' } && charAspectRatio > 0.58f) -> {
                    FontClassification.TYPEWRITER
                }
                // 5. Calibri: compact modern office font (narrower proportions)
                charAspectRatio < 0.44f -> {
                    FontClassification.CALIBRI
                }
                // 6. Sans-serif: standard Arial (default for business invoices, forms, and documents)
                else -> {
                    documentDominantFont ?: FontClassification.SANS_SERIF
                }
            }
        }
    }
}
