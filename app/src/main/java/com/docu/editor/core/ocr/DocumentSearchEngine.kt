package com.docu.editor.core.ocr

import android.graphics.Rect
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.util.DevanagariPostProcessor
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Offline Secure OCR Search & Highlighting Engine 2.0.
 *
 * Capabilities:
 * - Multi-lingual search supporting Devanagari (Hindi, Marathi, Sanskrit) & Latin (English).
 * - Devanagari Unicode conjunct, matra, and nukta normalization.
 * - Exact sub-word spatial interpolation: When OCR groups text into line-level bounding boxes,
 *   this engine computes the precise horizontal slice corresponding to the matching word.
 * - Whole word boundary verification and case sensitivity toggles.
 * - Multi-page match distribution indexer.
 */
data class SearchMatchOccurrence(
    val matchId: String = java.util.UUID.randomUUID().toString(),
    val itemIndex: Int,
    val pageIndex: Int,
    val matchedWord: String,
    val surroundingSnippet: String,
    val startChar: Int,
    val endChar: Int,
    val highlightBounds: Rect,
    val fullLineItem: DetectedTextItem
)

data class SearchEngineOptions(
    val isCaseSensitive: Boolean = false,
    val isWholeWord: Boolean = false,
    val isHindiScriptTolerant: Boolean = true,
    val searchAllPages: Boolean = true
)

object DocumentSearchEngine {

    /**
     * Normalizes text for search, resolving Devanagari OCR quirks and Unicode variations.
     */
    fun normalize(text: String, isCaseSensitive: Boolean = false, isHindiTolerant: Boolean = true): String {
        var str = text
        if (isHindiTolerant) {
            str = DevanagariPostProcessor.postProcess(str)
            // Strip non-semantic zero-width formatting characters
            str = str.replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\u200B", "")
                .replace("\uFEFF", "")
        }
        str = Normalizer.normalize(str, Normalizer.Form.NFC)
        return if (isCaseSensitive) str else str.lowercase()
    }

    /**
     * Searches a single page's DetectedTextItems and extracts exact bounding box matches.
     */
    fun findMatchesInPage(
        items: List<DetectedTextItem>,
        pageIndex: Int,
        query: String,
        options: SearchEngineOptions = SearchEngineOptions()
    ): List<SearchMatchOccurrence> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty() || items.isEmpty()) return emptyList()

        val normalizedQuery = normalize(trimmedQuery, options.isCaseSensitive, options.isHindiScriptTolerant)
        val queryLen = normalizedQuery.length
        val results = mutableListOf<SearchMatchOccurrence>()

        items.forEachIndexed { itemIdx, item ->
            val rawText = item.text
            if (rawText.isBlank()) return@forEachIndexed

            val normalizedText = normalize(rawText, options.isCaseSensitive, options.isHindiScriptTolerant)

            var searchStart = 0
            while (searchStart < normalizedText.length) {
                val matchPos = normalizedText.indexOf(normalizedQuery, searchStart)
                if (matchPos < 0) break

                val matchEnd = matchPos + queryLen
                var isValidMatch = true

                if (options.isWholeWord) {
                    val isStartWordBoundary = (matchPos == 0 || !normalizedText[matchPos - 1].isLetterOrDigit())
                    val isEndWordBoundary = (matchEnd >= normalizedText.length || !normalizedText[matchEnd].isLetterOrDigit())
                    if (!isStartWordBoundary || !isEndWordBoundary) {
                        isValidMatch = false
                    }
                }

                if (isValidMatch) {
                    // Extract exact matched substring from original text
                    val safeStart = matchPos.coerceIn(0, rawText.length)
                    val safeEnd = matchEnd.coerceIn(safeStart, rawText.length)
                    val matchedSub = if (safeStart < safeEnd) rawText.substring(safeStart, safeEnd) else trimmedQuery

                    // Calculate sub-word bounding box interpolation
                    val bounds = calculateSubWordBoundingBox(item.boundingBox, rawText.length, safeStart, safeEnd)

                    // Generate a readable snippet with ellipsis
                    val snippetStart = max(0, safeStart - 12)
                    val snippetEnd = min(rawText.length, safeEnd + 12)
                    val snippet = (if (snippetStart > 0) "..." else "") +
                            rawText.substring(snippetStart, snippetEnd).trim() +
                            (if (snippetEnd < rawText.length) "..." else "")

                    results.add(
                        SearchMatchOccurrence(
                            itemIndex = itemIdx,
                            pageIndex = pageIndex,
                            matchedWord = matchedSub,
                            surroundingSnippet = snippet,
                            startChar = safeStart,
                            endChar = safeEnd,
                            highlightBounds = bounds,
                            fullLineItem = item
                        )
                    )
                }

                searchStart = matchPos + max(1, queryLen)
            }
        }

        return results
    }

    /**
     * High precision horizontal interpolation:
     * When OCR detects an entire line (e.g. "Government of India / भारत सरकार"),
     * searching for "India" highlights only the exact sub-slice corresponding to "India"!
     */
    fun calculateSubWordBoundingBox(
        lineBox: Rect,
        totalChars: Int,
        startChar: Int,
        endChar: Int
    ): Rect {
        if (totalChars <= 1 || startChar >= endChar) return Rect(lineBox)

        val totalWidth = lineBox.width().toFloat()
        val charFractionStart = (startChar.toFloat() / totalChars).coerceIn(0f, 1f)
        val charFractionEnd = (endChar.toFloat() / totalChars).coerceIn(charFractionStart, 1f)

        // Add 2px horizontal padding for nice visual margins
        val subLeft = (lineBox.left + totalWidth * charFractionStart - 2).toInt().coerceAtLeast(lineBox.left)
        val subRight = (lineBox.left + totalWidth * charFractionEnd + 2).toInt().coerceAtMost(lineBox.right)
        val subTop = (lineBox.top - 1).coerceAtLeast(0)
        val subBottom = lineBox.bottom + 1

        val finalWidth = max(subRight - subLeft, 14)
        return Rect(subLeft, subTop, subLeft + finalWidth, subBottom)
    }

    /**
     * Cross-page match aggregator for multi-page PDF documents.
     */
    fun findMatchesAcrossAllPages(
        pagesMap: Map<Int, List<DetectedTextItem>>,
        query: String,
        options: SearchEngineOptions = SearchEngineOptions()
    ): Map<Int, List<SearchMatchOccurrence>> {
        if (query.trim().isEmpty()) return emptyMap()
        val resultMap = mutableMapOf<Int, List<SearchMatchOccurrence>>()
        pagesMap.forEach { (pageIdx, items) ->
            val pageMatches = findMatchesInPage(items, pageIdx, query, options)
            if (pageMatches.isNotEmpty()) {
                resultMap[pageIdx] = pageMatches
            }
        }
        return resultMap
    }
}
