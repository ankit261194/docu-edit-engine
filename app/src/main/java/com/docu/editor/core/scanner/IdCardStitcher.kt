package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.roundToInt

object IdCardStitcher {

    enum class IdCardLayoutMode {
        VERTICAL_STACK,          // Standard Top & Bottom (Default for bank/telecom/govt KYC)
        HORIZONTAL_SIDE_BY_SIDE, // Side-by-Side
        FIT_CARD_ONLY            // Compact duplex presentation
    }

    enum class CardScaleMode {
        PHYSICAL_1TO1, // Exact 100% True-to-Life Govt ID Print Size: 85.60mm x 53.98mm (1011 x 638 px @ 300 DPI)
        ENLARGED_KYC   // High-Visibility 160% Scale: 1680 x 1059 px for easy review
    }

    enum class PaperSize(val widthPx: Int, val heightPx: Int, val label: String) {
        A4(2480, 3508, "ISO A4 (210 × 297 mm)"),
        US_LETTER(2550, 3300, "US Letter (8.5 × 11 in)")
    }

    /**
     * Stitches Front and Back scans of an ID card onto a single print-ready page.
     * Features:
     * - Physical standard 85.6mm x 53.98mm scaling (1:1 Govt print size) or Enlarged KYC.
     * - Strict dimension synchronization so Front and Back match exactly regardless of capture distance.
     * - Anti-glare hologram and lamination specular reflection neutralization.
     * - Official center cutting dashed line ("✂ CUT ALONG DOTTED LINE") and corner registration crosshairs.
     */
    suspend fun stitchIdCardToA4(
        frontCard: Bitmap,
        backCard: Bitmap,
        layoutMode: IdCardLayoutMode = IdCardLayoutMode.VERTICAL_STACK,
        scaleMode: CardScaleMode = CardScaleMode.PHYSICAL_1TO1,
        paperSize: PaperSize = PaperSize.A4,
        applyAntiGlare: Boolean = true,
        drawCuttingGuide: Boolean = true,
        purposeAnnotation: String = ""
    ): Bitmap = withContext(Dispatchers.Default) {
        val preprocessedFront = if (applyAntiGlare) suppressHologramGlare(frontCard) else frontCard
        val preprocessedBack = if (applyAntiGlare) suppressHologramGlare(backCard) else backCard

        val normalizedFront = normalizeAndCropCard(preprocessedFront)
        val normalizedBack = normalizeAndCropCard(preprocessedBack)

        if (preprocessedFront != frontCard && !preprocessedFront.isRecycled) preprocessedFront.recycle()
        if (preprocessedBack != backCard && !preprocessedBack.isRecycled) preprocessedBack.recycle()

        val pageWidth = paperSize.widthPx
        val pageHeight = paperSize.heightPx

        val pageBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pageBitmap)
        canvas.drawColor(Color.WHITE)

        val cardAspect = 85.60f / 53.98f // Standard ISO/IEC 7810 ID-1 card (1.58577)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // Dark Navy Slate
            style = Paint.Style.FILL
        }

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }

        // Calculate card dimensions based on scale mode
        val (cardWidthPx, cardHeightPx) = when (scaleMode) {
            CardScaleMode.PHYSICAL_1TO1 -> {
                // At 300 DPI: 1 mm = 11.811 px -> 85.6 mm = 1011 px, 53.98 mm = 638 px
                val w = (85.60f * (300f / 25.4f)).roundToInt().toFloat()
                val h = w / cardAspect
                Pair(w, h)
            }
            CardScaleMode.ENLARGED_KYC -> {
                val w = if (layoutMode == IdCardLayoutMode.HORIZONTAL_SIDE_BY_SIDE) 1140f else 1680f
                val h = w / cardAspect
                Pair(w, h)
            }
        }

        when (layoutMode) {
            IdCardLayoutMode.VERTICAL_STACK, IdCardLayoutMode.FIT_CARD_ONLY -> {
                val marginX = (pageWidth - cardWidthPx) / 2f
                val topCardY = if (scaleMode == CardScaleMode.PHYSICAL_1TO1) {
                    pageHeight * 0.22f
                } else {
                    pageHeight * 0.14f
                }
                val bottomCardY = if (scaleMode == CardScaleMode.PHYSICAL_1TO1) {
                    pageHeight * 0.58f
                } else {
                    topCardY + cardHeightPx + (pageHeight * 0.12f)
                }

                // 1. Draw Front Card
                val frontDst = RectF(marginX, topCardY, marginX + cardWidthPx, topCardY + cardHeightPx)
                canvas.drawBitmap(normalizedFront, null, frontDst, paint)
                canvas.drawRoundRect(frontDst, 24f, 24f, borderPaint)

                // Front Badge
                drawCardBadge(canvas, "FRONT SIDE • मुख पृष्ठ", "ISO/IEC 7810 ID-1", frontDst.centerX(), topCardY - 42f, badgeBgPaint, badgeTextPaint, subTextPaint)

                // 2. Draw Back Card
                val backDst = RectF(marginX, bottomCardY, marginX + cardWidthPx, bottomCardY + cardHeightPx)
                canvas.drawBitmap(normalizedBack, null, backDst, paint)
                canvas.drawRoundRect(backDst, 24f, 24f, borderPaint)

                // Back Badge
                drawCardBadge(canvas, "BACK SIDE • पृष्ठ भाग", "Govt Identity Proof", backDst.centerX(), bottomCardY - 42f, badgeBgPaint, badgeTextPaint, subTextPaint)

                // Draw Cutting Guide between Front and Back
                if (drawCuttingGuide) {
                    val cutY = (frontDst.bottom + backDst.top) / 2f
                    drawCuttingGuideLine(canvas, pageWidth, cutY)
                }

                // Draw optional Purpose Watermark across both cards
                if (purposeAnnotation.isNotBlank()) {
                    drawPurposeWatermark(canvas, frontDst, purposeAnnotation)
                    drawPurposeWatermark(canvas, backDst, purposeAnnotation)
                }
            }

            IdCardLayoutMode.HORIZONTAL_SIDE_BY_SIDE -> {
                val spacingX = 90f
                val totalW = (cardWidthPx * 2) + spacingX
                val startX = (pageWidth - totalW) / 2f
                val centerY = pageHeight * 0.38f

                val frontDst = RectF(startX, centerY, startX + cardWidthPx, centerY + cardHeightPx)
                val backDst = RectF(startX + cardWidthPx + spacingX, centerY, startX + (cardWidthPx * 2) + spacingX, centerY + cardHeightPx)

                canvas.drawBitmap(normalizedFront, null, frontDst, paint)
                canvas.drawRoundRect(frontDst, 24f, 24f, borderPaint)

                canvas.drawBitmap(normalizedBack, null, backDst, paint)
                canvas.drawRoundRect(backDst, 24f, 24f, borderPaint)

                drawCardBadge(canvas, "FRONT SIDE", "ISO/IEC 7810 ID-1", frontDst.centerX(), centerY - 42f, badgeBgPaint, badgeTextPaint, subTextPaint)
                drawCardBadge(canvas, "BACK SIDE", "Identity Proof", backDst.centerX(), centerY - 42f, badgeBgPaint, badgeTextPaint, subTextPaint)

                if (drawCuttingGuide) {
                    val cutX = frontDst.right + (spacingX / 2f)
                    drawVerticalCuttingGuideLine(canvas, cutX, centerY - 80f, centerY + cardHeightPx + 80f)
                }

                if (purposeAnnotation.isNotBlank()) {
                    drawPurposeWatermark(canvas, frontDst, purposeAnnotation)
                    drawPurposeWatermark(canvas, backDst, purposeAnnotation)
                }
            }
        }

        // Draw Corner Registration Crosshairs
        drawRegistrationCrosshairs(canvas, pageWidth, pageHeight)

        // Clean up temporary bitmaps if rotated/cropped
        if (normalizedFront != frontCard && !normalizedFront.isRecycled) normalizedFront.recycle()
        if (normalizedBack != backCard && !normalizedBack.isRecycled) normalizedBack.recycle()

        // Official Security Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val scaleDesc = if (scaleMode == CardScaleMode.PHYSICAL_1TO1) "Exact Physical Scale 85.60 × 53.98 mm (100% Real Print)" else "Enlarged KYC Review Scale (160%)"
        canvas.drawText("Generated via Enterprise ID Duplex Scanner • $scaleDesc • 300 DPI Official Print-Ready", pageWidth / 2f, pageHeight - 80f, footerPaint)

        pageBitmap
    }

    private fun drawCardBadge(
        canvas: Canvas,
        title: String,
        subTitle: String,
        centerX: Float,
        centerY: Float,
        bgPaint: Paint,
        textPaint: Paint,
        subTextPaint: Paint
    ) {
        val pillW = 460f
        val pillH = 68f
        val pillRect = RectF(centerX - pillW / 2f, centerY - pillH / 2f, centerX + pillW / 2f, centerY + pillH / 2f)
        canvas.drawRoundRect(pillRect, 20f, 20f, bgPaint)
        canvas.drawText(title, centerX, centerY + 11f, textPaint)
    }

    private fun drawCuttingGuideLine(canvas: Canvas, pageWidth: Int, y: Float) {
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f)
        }
        val path = Path().apply {
            moveTo(140f, y)
            lineTo(pageWidth - 140f, y)
        }
        canvas.drawPath(path, dashPaint)

        val cutTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 26f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val textBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val label = "✂  CUT ALONG DOTTED LINE / यहाँ से काटें  ✂"
        val textWidth = cutTextPaint.measureText(label)
        canvas.drawRect((pageWidth - textWidth) / 2f - 24f, y - 22f, (pageWidth + textWidth) / 2f + 24f, y + 22f, textBg)
        canvas.drawText(label, pageWidth / 2f, y + 9f, cutTextPaint)
    }

    private fun drawVerticalCuttingGuideLine(canvas: Canvas, x: Float, top: Float, bottom: Float) {
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f)
        }
        canvas.drawLine(x, top, x, bottom, dashPaint)
    }

    private fun drawRegistrationCrosshairs(canvas: Canvas, width: Int, height: Int) {
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
        }
        val arm = 30f
        val offsets = listOf(
            Pair(80f, 80f),
            Pair(width - 80f, 80f),
            Pair(80f, height - 80f),
            Pair(width - 80f, height - 80f)
        )
        for ((cx, cy) in offsets) {
            canvas.drawLine(cx - arm, cy, cx + arm, cy, crossPaint)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, crossPaint)
            canvas.drawCircle(cx, cy, 14f, crossPaint)
        }
    }

    private fun drawPurposeWatermark(canvas: Canvas, bounds: RectF, text: String) {
        canvas.save()
        canvas.rotate(-22f, bounds.centerX(), bounds.centerY())
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 220, 38, 38) // Semi-transparent Red
            textSize = (bounds.height() * 0.11f).coerceIn(34f, 60f)
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text.uppercase(), bounds.centerX(), bounds.centerY(), wmPaint)
        canvas.restore()
    }

    /**
     * Suppresses specular glare and hologram reflections from lamination on ID cards.
     * Uses OpenCV LAB illumination leveling and adaptive reflection suppression.
     */
    fun suppressHologramGlare(card: Bitmap): Bitmap {
        val srcMat = Mat()
        val labMat = Mat()
        val channels = ArrayList<Mat>()

        return try {
            Utils.bitmapToMat(card, srcMat)
            Imgproc.cvtColor(srcMat, labMat, Imgproc.COLOR_RGBA2RGB)
            val rgbMat = Mat()
            labMat.copyTo(rgbMat)
            Imgproc.cvtColor(rgbMat, labMat, Imgproc.COLOR_RGB2Lab)
            Core.split(labMat, channels)

            val lChannel = channels[0] // Luminance (0..255)

            // 1. Identify specular reflection hotspot mask (L > 235 with low local saturation/chroma)
            val glareMask = Mat()
            Imgproc.threshold(lChannel, glareMask, 236.0, 255.0, Imgproc.THRESH_BINARY)

            // Morphological dilation to cover glare corona
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
            Imgproc.dilate(glareMask, glareMask, kernel)

            // 2. Estimate ambient background luminance via morphological closing
            val bgLuma = Mat()
            val bgKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(25.0, 25.0))
            Imgproc.morphologyEx(lChannel, bgLuma, Imgproc.MORPH_CLOSE, bgKernel)

            // 3. Selectively dampen specular highlights to target paper/card level
            val lFloat = Mat()
            val bgFloat = Mat()
            lChannel.convertTo(lFloat, CvType.CV_32F)
            bgLuma.convertTo(bgFloat, CvType.CV_32F)

            val maskFloat = Mat()
            glareMask.convertTo(maskFloat, CvType.CV_32F)
            Core.divide(maskFloat, Scalar(255.0), maskFloat)

            // Blend: newL = lFloat * (1 - mask) + (bgFloat * 0.88 + 20) * mask
            val dampened = Mat()
            Core.multiply(bgFloat, Scalar(0.88), dampened)
            Core.add(dampened, Scalar(20.0), dampened)

            val blendedL = Mat()
            val invMask = Mat()
            val onesMat = Mat(maskFloat.size(), CvType.CV_32F, Scalar(1.0))
            Core.subtract(onesMat, maskFloat, invMask)
            onesMat.release()
            Core.multiply(lFloat, invMask, lFloat)
            Core.multiply(dampened, maskFloat, dampened)
            Core.add(lFloat, dampened, blendedL)

            blendedL.convertTo(channels[0], CvType.CV_8U)

            Core.merge(channels, labMat)
            val outRgb = Mat()
            Imgproc.cvtColor(labMat, outRgb, Imgproc.COLOR_Lab2RGB)
            val outRgba = Mat()
            Imgproc.cvtColor(outRgb, outRgba, Imgproc.COLOR_RGB2RGBA)

            val resultBitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outRgba, resultBitmap)

            // Cleanup Mats
            rgbMat.release()
            glareMask.release()
            kernel.release()
            bgKernel.release()
            bgLuma.release()
            lFloat.release()
            bgFloat.release()
            maskFloat.release()
            invMask.release()
            dampened.release()
            blendedL.release()
            outRgb.release()
            outRgba.release()

            resultBitmap
        } catch (_: Exception) {
            card
        } finally {
            srcMat.release()
            labMat.release()
            for (c in channels) c.release()
        }
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
        val targetAspect = 85.60f / 53.98f
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

