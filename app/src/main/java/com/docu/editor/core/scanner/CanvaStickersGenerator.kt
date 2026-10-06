package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

enum class CanvaBadgeType(val title: String, val colorRgb: Int, val isCircular: Boolean = false) {
    APPROVED("APPROVED", Color.rgb(5, 150, 105)),
    CONFIDENTIAL("CONFIDENTIAL", Color.rgb(220, 38, 38)),
    PAID("PAID", Color.rgb(37, 99, 235)),
    VERIFIED("VERIFIED", Color.rgb(13, 148, 136)),
    URGENT("URGENT", Color.rgb(234, 88, 12)),
    DRAFT("DRAFT", Color.rgb(100, 116, 139)),
    REJECTED("REJECTED", Color.rgb(185, 28, 28)),
    OFFICIAL_SEAL("OFFICIAL SEAL", Color.rgb(180, 83, 9), true)
}

object CanvaStickersGenerator {
    fun createBadgeBitmap(type: CanvaBadgeType): Bitmap {
        if (type.isCircular) {
            val size = 320
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = type.colorRgb
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }

            // Outer thick circle
            canvas.drawCircle(center, center, center - 14f, strokePaint)

            // Inner circle
            strokePaint.strokeWidth = 3f
            canvas.drawCircle(center, center, center - 26f, strokePaint)

            // Innermost circle
            strokePaint.strokeWidth = 2f
            canvas.drawCircle(center, center, center - 64f, strokePaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = type.colorRgb
                style = Paint.Style.FILL
                textSize = 36f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.12f
            }

            val yPos = center - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText("OFFICIAL", center, yPos - 16f, textPaint)
            textPaint.textSize = 24f
            canvas.drawText("★ SEAL ★", center, yPos + 22f, textPaint)

            return bitmap
        }

        val width = 480
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = type.colorRgb
            style = Paint.Style.STROKE
            strokeWidth = 9f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = type.colorRgb
            style = Paint.Style.FILL
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }

        val rect = RectF(14f, 14f, width - 14f, height - 14f)
        canvas.drawRoundRect(rect, 22f, 22f, strokePaint)

        val innerRect = RectF(26f, 26f, width - 26f, height - 26f)
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(innerRect, 16f, 16f, strokePaint)

        val yPos = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(type.title, width / 2f, yPos, textPaint)

        return bitmap
    }
}
