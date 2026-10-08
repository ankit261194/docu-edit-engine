package com.docu.editor.core.ocr.util

import java.text.Normalizer

/**
 * Intelligent Devanagari / Hindi OCR Post-Processing Engine.
 * 
 * Solves complex conjunct (Sanyuktakshar) fragmentation, matra dissociation,
 * nukta positioning, and common OCR optical misrecognitions in government forms.
 */
object DevanagariPostProcessor {

    // Common Hindi Govt Form Keyword Corrections
    private val COMMON_GOVT_CORRECTIONS = mapOf(
        "प्रमाणपन्र" to "प्रमाणपत्र",
        "प्रमाण पत्र" to "प्रमाणपत्र",
        "हस्ताक्षर" to "हस्ताक्षर",
        "हस्ताक्षर" to "हस्ताक्षर",
        "दिनाक" to "दिनांक",
        "कमांक" to "क्रमांक",
        "पजीकरण" to "पंजीकरण",
        "जन्मतिथी" to "जन्मतिथि",
        "जन्मतिथी:" to "जन्मतिथि:",
        "शपथपन्र" to "शपथपत्र",
        "शपथ पत्र" to "शपथपत्र",
        "मूलनिवास" to "मूल निवास",
        "पहचानपन्र" to "पहचानपत्र",
        "पहचान पत्र" to "पहचानपत्र",
        "आधार काड" to "आधार कार्ड",
        "आधारकाड" to "आधार कार्ड",
        "निवाशी" to "निवासी",
        "अनुभाग" to "अनुभाग",
        "तहसील" to "तहसील",
        "कार्यालय" to "कार्यालय"
    )

    fun postProcess(rawText: String): String {
        if (rawText.isBlank()) return rawText

        // Check if string contains any Devanagari Unicode codepoints (U+0900 to U+097F)
        val hasDevanagari = rawText.any { it.code in 0x0900..0x097F }
        if (!hasDevanagari) return rawText

        // 1. Unicode Canonical Decomposition & Normalization (NFC)
        var text = Normalizer.normalize(rawText, Normalizer.Form.NFC)

        // 2. Fix Latin pipe '|' misrecognized as Purna Viram '।' (U+0964)
        text = text.replace(Regex("(?<=[\\u0900-\\u097F])\\s*\\|"), " ।")
        text = text.replace(Regex("\\|\\s*(?=[\\u0900-\\u097F])"), "। ")

        // 3. Fix Disconnected Matras (Space between consonant and dependent vowel sign)
        // e.g. "क ो" -> "को", "भ ारत" -> "भारत"
        text = text.replace(Regex("([\\u0915-\\u0939])\\s+([\\u093E-\\u094F\\u0962\\u0963])"), "$1$2")

        // 4. Fix Pre-base Chhoti 'i' matra (\u093F) placed before consonant
        // e.g. "ि क" -> "कि"
        text = text.replace(Regex("(\\u093F)\\s*([\\u0915-\\u0939])"), "$2$1")

        // 5. Reconnect Halant Conjuncts (Sanyuktakshar)
        // e.g. "क् य" -> "क्य", "न् य" -> "न्य", "त् व" -> "त्व"
        text = text.replace(Regex("([\\u0915-\\u0939]\\u094D)\\s+([\\u0915-\\u0939])"), "$1$2")

        // 6. Fix Anusvara / Chandrabindu dissociation
        // e.g. "क ं" -> "कं"
        text = text.replace(Regex("([\\u0915-\\u0939\\u093E-\\u094F])\\s+([\\u0901\\u0902\\u0903])"), "$1$2")

        // 7. Fix Nukta dissociation
        // e.g. "क ़" -> "क़"
        text = text.replace(Regex("([\\u0915-\\u0939])\\s+(\\u093C)"), "$1$2")

        // 8. Dictionary Heuristic Post-Processing for Government / Official Documents
        val tokens = text.split(" ")
        val correctedTokens = tokens.map { token ->
            COMMON_GOVT_CORRECTIONS[token] ?: token
        }
        text = correctedTokens.joinToString(" ")

        // 9. Remove invisible Zero-Width joiners that are malformed
        text = text.replace("\u200B", "") // zero-width space
        text = text.replace("\uFEFF", "") // byte order mark

        return text
    }
}
