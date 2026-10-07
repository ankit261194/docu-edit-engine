package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

object IdCardStitcher {

    enum class IdCardLayoutMode {
        VERTICAL_STACK,         // Standard A4 Top & Bottom (Default for bank/telecom/govt KYC)
        HORIZONTAL_SIDE_BY_SIDE, // A4 Side-by-Side
        FIT_CARD_ONLY           // Compact duplex card presentation
    }

    /**
     * Stitches Front and Back scans of an ID card onto a single A4 page (2480 x 3508 at 300 DPI).
     */
    suspend fun stitchIdCardToA4(
        frontCard: Bitmap,
        backCard: Bitmap,
        layoutMode: IdCardLayoutMode = IdCardLayoutMode.VERTICAL_STACK,
        purposeAnnotation: String = ""
    ): Bitmap = withContext(Dispatchers.Default) {
        val normalizedFront = normalizeAndCropCard(frontCard)
        val normalizedBack = normalizeAndCropCard(backCard)

        val a4Width = 2480
        val a4Height = 3508

        val a4Bitmap = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(a4Bitmap)
        canvas.drawColor(Color.WHITE)

        val cardAspect = 85.6f / 53.98f // Standard ISO/IEC 7810 ID-1 card (1.5858)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 44f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        when (layoutMode) {
            IdCardLayoutMode.VERTICAL_STACK, IdCardLayoutMode.FIT_CARD_ONLY -> {
                val cardWidthPx = 1800f
                val cardHeightPx = cardWidthPx / cardAspect
                val marginX = (a4Width - cardWidthPx) / 2f
                val topCardY = a4Height * 0.16f
                val bottomCardY = a4Height * 0.54f

                // Draw Front Card
                val frontDst = RectF(marginX, topCardY, marginX + cardWidthPx, topCardY + cardHeightPx)
                canvas.drawBitmap(normalizedFront, null, frontDst, paint)
                canvas.drawRoundRect(frontDst, 32f, 32f, borderPaint)

                // Draw Back Card
                val backDst = RectF(marginX, bottomCardY, marginX + cardWidthPx, bottomCardY + cardHeightPx)
                canvas.drawBitmap(normalizedBack, null, backDst, paint)
                canvas.drawRoundRect(backDst, 32f, 32f, borderPaint)

                canvas.drawText("FRONT SIDE", a4Width / 2f, topCardY - 32f, textPaint)
                canvas.drawText("BACK SIDE", a4Width / 2f, bottomCardY - 32f, textPaint)

                // Draw optional Purpose Watermark across both cards
                if (purposeAnnotation.isNotBlank()) {
                    drawPurposeWatermark(canvas, frontDst, purposeAnnotation)
                    drawPurposeWatermark(canvas, backDst, purposeAnnotation)
                }
            }

            IdCardLayoutMode.HORIZONTAL_SIDE_BY_SIDE -> {
                val cardWidthPx = 1120f
                val cardHeightPx = cardWidthPx / cardAspect
                val centerY = a4Height * 0.38f
                val spacingX = 80f
                val totalW = (cardWidthPx * 2) + spacingX
                val startX = (a4Width - totalW) / 2f

                val frontDst = RectF(startX, centerY, startX + cardWidthPx, centerY + cardHeightPx)
                val backDst = RectF(startX + cardWidthPx + spacingX, centerY, startX + (cardWidthPx * 2) + spacingX, centerY + cardHeightPx)

                canvas.drawBitmap(normalizedFront, null, frontDst, paint)
                canvas.drawRoundRect(frontDst, 28f, 28f, borderPaint)

                canvas.drawBitmap(normalizedBack, null, backDst, paint)
                canvas.drawRoundRect(backDst, 28f, 28f, borderPaint)

                canvas.drawText("FRONT SIDE", frontDst.centerX(), centerY - 32f, textPaint)
                canvas.drawText("BACK SIDE", backDst.centerX(), centerY - 32f, textPaint)

                if (purposeAnnotation.isNotBlank()) {
                    drawPurposeWatermark(canvas, frontDst, purposeAnnotation)
                    drawPurposeWatermark(canvas, backDst, purposeAnnotation)
                }
            }
        }

        // Clean up temporary bitmaps if rotated/cropped
        if (normalizedFront != frontCard) normalizedFront.recycle()
        if (normalizedBack != backCard) normalizedBack.recycle()

        // Footer legal verification notice
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated via Enterprise ID Duplex Scanner • ISO/IEC 7810 ID-1 Standard", a4Width / 2f, a4Height - 80f, footerPaint)

        a4Bitmap
    }

    private fun drawPurposeWatermark(canvas: Canvas, bounds: RectF, text: String) {
        canvas.save()
        canvas.rotate(-24f, bounds.centerX(), bounds.centerY())
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 220, 38, 38) // Semi-transparent Red
            textSize = (bounds.height() * 0.11f).coerceIn(36f, 62f)
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text.uppercase(), bounds.centerX(), bounds.centerY(), wmPaint)
        canvas.restore()
    }

    /**
     * Auto-rotates vertical/portrait cards to landscape and crops to standard ID-1 aspect ratio.
     */
    fun normalizeAndCropCard(card: Bitmap): Bitmap {
        var bmp = card
        if (bmp.height > bmp.width) {
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        val targetAspect = 85.6f / 53.98f
        val currentAspect = bmp.width.toFloat() / bmp.height.toFloat()
        return if (currentAspect > targetAspect * 1.05f) {
            val newWidth = (bmp.height * targetAspect).toInt()
            val startX = ((bmp.width - newWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bmp, startX, 0, newWidth.coerceAtMost(bmp.width), bmp.height)
        } else if (currentAspect < targetAspect * 0.95f) {
            val newHeight = (bmp.width / targetAspect).toInt()
            val startY = ((bmp.height - newHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bmp, 0, startY, bmp.width, newHeight.coerceAtMost(bmp.height))
        } else {
            bmp
        }
    }
}
