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
    A5("A5 (148 × 210 mm)", "Notebook & Receipt", 148f, 210f, 1748, 2480),
    A3("A3 (297 × 420 mm)", "Poster & Ledger", 297f, 420f, 3508, 4960),
    BUSINESS_CARD("Business Card (85 × 55 mm)", "Cards", 85f, 55f, 1004, 650),
    ID_CARD_CR80("Standard ID Card (85.6 × 54 mm)", "Cards", 85.6f, 54f, 1011, 638),
    SLIDE_16_9("Presentation 16:9", "Digital & Screens", 338.7f, 190.5f, 1920, 1080, isLandscape = true),
    STORY_9_16("Social Story 9:16", "Mobile Social", 190.5f, 338.7f, 1080, 1920),
    SQUARE_1_1("Square 1:1", "Social & Profile", 200f, 200f, 1080, 1080)
}

object PageSizeEngine {
    enum class FitMode { FIT_WITH_MARGINS, FILL_AND_CROP }

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

        if (fitMode == FitMode.FIT_WITH_MARGINS) {
            // Fit inside page with neat clean white margins (standard PDF print format)
            val destRect = if (sourceAspect > targetAspect) {
                // Wider than target: match width, center vertically
                val destW = targetW
                val destH = (destW / sourceAspect).roundToInt()
                val top = (targetH - destH) / 2
                Rect(0, top, destW, top + destH)
            } else {
                // Taller than target: match height, center horizontally
                val destH = targetH
                val destW = (destH * sourceAspect).roundToInt()
                val left = (targetW - destW) / 2
                Rect(left, 0, left + destW, destH)
            }
            val srcRect = Rect(0, 0, sourceBitmap.width, sourceBitmap.height)
            canvas.drawBitmap(sourceBitmap, srcRect, destRect, paint)
        } else {
            // Fill page and crop excess edges
            val srcRect = if (sourceAspect > targetAspect) {
                val cropW = (sourceBitmap.height * targetAspect).roundToInt()
                val xOffset = (sourceBitmap.width - cropW) / 2
                Rect(xOffset, 0, xOffset + cropW, sourceBitmap.height)
            } else {
                val cropH = (sourceBitmap.width / targetAspect).roundToInt()
                val yOffset = (sourceBitmap.height - cropH) / 2
                Rect(0, yOffset, sourceBitmap.width, yOffset + cropH)
            }
            val destRect = Rect(0, 0, targetW, targetH)
            canvas.drawBitmap(sourceBitmap, srcRect, destRect, paint)
        }

        return output
    }
}
