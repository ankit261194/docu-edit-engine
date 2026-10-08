package com.docu.editor.core.layout

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.roundToInt

enum class StandardPageSize(
    val displayName: String,
    val category: String,
    val widthMm: Float,
    val heightMm: Float,
    val pixelWidth300Dpi: Int,
    val pixelHeight300Dpi: Int,
    val isLandscape: Boolean = false
) {
    A4("A4 (210 × 297 mm)", "International Document", 210f, 297f, 2480, 3508),
    US_LETTER("US Letter (8.5 × 11 in)", "North America Document", 215.9f, 279.4f, 2550, 3300),
    US_LEGAL("US Legal (8.5 × 14 in)", "Legal & Affidavit", 215.9f, 355.6f, 2550, 4200),
    GOVT_STAMP_PAPER("India Stamp Paper (100mm Blank Header)", "Legal & Govt", 215.9f, 355.6f, 2550, 4200),
    GOVT_EXAM_FORM("Govt / Job Application Form (A4)", "Official & Govt", 210f, 297f, 2480, 3508),
    A5("A5 (148 × 210 mm)", "Notebook & Receipt", 148f, 210f, 1748, 2480),
    A3("A3 (297 × 420 mm)", "Poster & Ledger", 297f, 420f, 3508, 4960),
    BUSINESS_CARD("Business Card (85 × 55 mm)", "Cards", 85f, 55f, 1004, 650),
    ID_CARD_CR80("Standard ID Card (85.6 × 54 mm)", "Cards", 85.6f, 54f, 1011, 638),
    SLIDE_16_9("Presentation 16:9", "Digital & Screens", 338.7f, 190.5f, 1920, 1080, isLandscape = true),
    STORY_9_16("Social Story 9:16", "Mobile Social", 190.5f, 338.7f, 1080, 1920),
    SQUARE_1_1("Square 1:1", "Social & Profile", 200f, 200f, 1080, 1080)
}

object PageSizeEngine {
    enum class FitMode { FIT_WITH_MARGINS, FILL_AND_CROP, EXPAND_CANVAS_ONLY }

    /**
     * Rescales and reframes the document bitmap onto standard page format
     * with crisp 300 DPI print-safe rasterization.
     */
    fun applyPageSize(
        sourceBitmap: Bitmap,
        pageSize: StandardPageSize,
        fitMode: FitMode = FitMode.FIT_WITH_MARGINS,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val targetAspect = pageSize.pixelWidth300Dpi.toFloat() / pageSize.pixelHeight300Dpi.toFloat()
        val sourceAspect = sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat()

        // Max target resolution capped at 2880 to prevent OOM
        val maxDim = 2880
        val targetW: Int
        val targetH: Int
        if (pageSize.pixelWidth300Dpi >= pageSize.pixelHeight300Dpi) {
            targetW = maxDim
            targetH = (maxDim / targetAspect).roundToInt()
        } else {
            targetH = maxDim
            targetW = (maxDim * targetAspect).roundToInt()
        }

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val isStampPaper = (pageSize == StandardPageSize.GOVT_STAMP_PAPER)
        val headerOffset = if (isStampPaper) (targetH * 0.28f).roundToInt() else 0
        val availableH = targetH - headerOffset
        val availableAspect = targetW.toFloat() / availableH.toFloat()

        when (fitMode) {
            FitMode.FIT_WITH_MARGINS -> {
                // Fit inside page with neat clean white margins (standard PDF print format)
                val destRect = if (sourceAspect > availableAspect) {
                    val destW = targetW
                    val destH = (destW / sourceAspect).roundToInt().coerceAtMost(availableH)
                    val top = headerOffset + (availableH - destH) / 2
                    Rect(0, top, destW, top + destH)
                } else {
                    val destH = availableH
                    val destW = (destH * sourceAspect).roundToInt().coerceAtMost(targetW)
                    val left = (targetW - destW) / 2
                    Rect(left, headerOffset, left + destW, headerOffset + destH)
                }
                val srcRect = Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
                canvas.drawBitmap(sourceBitmap, srcRect, destRect, paint)
            }
            FitMode.EXPAND_CANVAS_ONLY -> {
                // Keep content at 100% scale (or downscale proportionally if overflowing target canvas)
                val scale = minOf(1.0f, minOf(targetW.toFloat() / sourceBitmap.width, availableH.toFloat() / sourceBitmap.height))
                val destW = (sourceBitmap.width * scale).roundToInt()
                val destH = (sourceBitmap.height * scale).roundToInt()
                val left = (targetW - destW) / 2
                val top = headerOffset + (availableH - destH) / 2
                val destRect = Rect(left, top, left + destW, top + destH)
                val srcRect = Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
                canvas.drawBitmap(sourceBitmap, srcRect, destRect, paint)
            }
            FitMode.FILL_AND_CROP -> {
                // Fill page and crop excess edges
                val srcRect = if (sourceAspect > availableAspect) {
                    val cropW = (sourceBitmap.height * availableAspect).roundToInt()
                    val xOffset = (sourceBitmap.width - cropW) / 2
                    Rect(xOffset, 0, xOffset + cropW, sourceBitmap.height)
                } else {
                    val cropH = (sourceBitmap.width / availableAspect).roundToInt()
                    val yOffset = (sourceBitmap.height - cropH) / 2
                    Rect(0, yOffset, sourceBitmap.width, yOffset + cropH)
                }
                val destRect = Rect(0, headerOffset, targetW, headerOffset + availableH)
                canvas.drawBitmap(sourceBitmap, srcRect, destRect, paint)
            }
        }

        return output
    }
}
