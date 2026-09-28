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

    /**
     * Stitches Front and Back scans of an ID card onto a single A4 page (2480 x 3508 at 300 DPI).
     */
    suspend fun stitchIdCardToA4(
        frontCard: Bitmap,
        backCard: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val a4Width = 2480
        val a4Height = 3508

        val a4Bitmap = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(a4Bitmap)
        canvas.drawColor(Color.WHITE)

        val cardAspect = 85.6f / 53.98f // Standard ISO/IEC 7810 ID-1 card (credit card / Aadhaar / PAN)
        val cardWidthPx = 1800f
        val cardHeightPx = cardWidthPx / cardAspect

        val marginX = (a4Width - cardWidthPx) / 2f
        val topCardY = a4Height * 0.16f
        val bottomCardY = a4Height * 0.54f

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        // Draw Front Card
        val frontDst = RectF(marginX, topCardY, marginX + cardWidthPx, topCardY + cardHeightPx)
        canvas.drawBitmap(frontCard, null, frontDst, paint)
        canvas.drawRoundRect(frontDst, 24f, 24f, borderPaint)

        // Draw Back Card
        val backDst = RectF(marginX, bottomCardY, marginX + cardWidthPx, bottomCardY + cardHeightPx)
        canvas.drawBitmap(backCard, null, backDst, paint)
        canvas.drawRoundRect(backDst, 24f, 24f, borderPaint)

        // Draw guideline labels
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ID CARD FRONT", a4Width / 2f, topCardY - 24f, textPaint)
        canvas.drawText("ID CARD BACK", a4Width / 2f, bottomCardY - 24f, textPaint)

        a4Bitmap
    }
}
