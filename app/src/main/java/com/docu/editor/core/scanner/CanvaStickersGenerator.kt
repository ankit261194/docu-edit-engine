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

    /**
     * Dynamic Official Authority & Notary Stamp Generator.
     * Dual-ring circular seal with curved arc organization text,
     * center framed current date, custom designation, and high-fidelity ink color.
     */
    fun createDynamicAuthorityStamp(
        organization: String = "DEPARTMENT OF REVENUE",
        designation: String = "AUTHORIZED SIGNATORY",
        dateText: String = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
        inkColor: Int = Color.rgb(30, 58, 138),
        sealSize: Int = 420
    ): Bitmap {
        val size = sealSize.coerceIn(300, 800)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            style = Paint.Style.STROKE
            strokeWidth = 9f
        }

        // 1. Dual concentric outer rings
        canvas.drawCircle(center, center, center - 16f, strokePaint)

        strokePaint.strokeWidth = 3f
        canvas.drawCircle(center, center, center - 30f, strokePaint)

        strokePaint.strokeWidth = 4f
        val innerRadius = center - 85f
        canvas.drawCircle(center, center, innerRadius, strokePaint)

        // 2. Curved Arc Text along the Top Ring for Organization
        val topArcPath = android.graphics.Path().apply {
            val r = center - 52f
            val arcRect = RectF(center - r, center - r, center + r, center + r)
            arcTo(arcRect, 195f, 150f, true)
        }

        val arcTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            style = Paint.Style.FILL
            textSize = 24f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        val safeOrg = if (organization.isBlank()) "OFFICIAL VERIFICATION" else organization.uppercase()
        canvas.drawTextOnPath(safeOrg, topArcPath, 0f, 0f, arcTextPaint)

        // Curved Arc Text along Bottom Ring
        val bottomArcPath = android.graphics.Path().apply {
            val r = center - 52f
            val arcRect = RectF(center - r, center - r, center + r, center + r)
            arcTo(arcRect, 15f, 150f, true)
        }
        canvas.drawTextOnPath("★ OFFICIAL DOCUMENT SEAL ★", bottomArcPath, 0f, 0f, arcTextPaint)

        // 3. Center Date Pill Box
        val dateBoxW = innerRadius * 1.45f
        val dateBoxH = 46f
        val dateBoxRect = RectF(
            center - dateBoxW / 2f,
            center - dateBoxH / 2f - 10f,
            center + dateBoxW / 2f,
            center + dateBoxH / 2f - 10f
        )
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(dateBoxRect, 8f, 8f, strokePaint)

        // Date Text
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            style = Paint.Style.FILL
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        val dateBaseline = dateBoxRect.centerY() - ((datePaint.descent() + datePaint.ascent()) / 2f)
        canvas.drawText(dateText, center, dateBaseline, datePaint)

        // 4. Designation Text below Date
        val desigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            style = Paint.Style.FILL
            textSize = 21f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        val safeDesig = if (designation.isBlank()) "AUTHORIZED SIGNATORY" else designation.uppercase()
        canvas.drawText(safeDesig, center, center + 44f, desigPaint)

        // Decorative stars above date
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            style = Paint.Style.FILL
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("★  ★  ★", center, center - 48f, starPaint)

        return bitmap
    }
}
