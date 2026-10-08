package com.docu.editor.core.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise CamScanner Tools Engine.
 * Powering: CountCam, ID Photo Maker, QR/Barcode Scanner, PDF to Long Image,
 * PPT Deck Export, Photo Restoration, and Math/Formula AI.
 */
object CamScannerToolsEngine {

    // =========================================================================
    // 1. CountCam Engine (AI / CV Automated Object Counter)
    // =========================================================================

    data class CountResult(
        val count: Int,
        val annotatedBitmap: Bitmap,
        val detectedCenters: List<android.graphics.Point>
    )

    /**
     * Counts repetitive objects (steel rods, pipes, dots, pills, coins, boxes)
     * using Distance Transform Euclidean peak segmentation and Connected Components.
     * Accurately segments touching objects (pipes, rebar, pills) that would otherwise merge.
     */
    fun countObjects(sourceBitmap: Bitmap, minSize: Int = 12, maxSize: Int = 400): CountResult {
        val mat = Mat()
        Utils.bitmapToMat(sourceBitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // 1. Adaptive Otsu Binarization
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

        // Morphological open to eliminate tiny noise specks
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel)

        // 2. Euclidean Distance Transform: Touching pipes/rods produce local distance peaks
        val dist = Mat()
        Imgproc.distanceTransform(binary, dist, Imgproc.DIST_L2, 5)

        val minMax = org.opencv.core.Core.minMaxLoc(dist)
        val maxDist = minMax.maxVal

        val peaks = Mat()
        val peaks8 = Mat()
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()

        val detectedCenters = mutableListOf<android.graphics.Point>()
        val detectedRadii = mutableListOf<Float>()

        if (maxDist > 3.0) {
            // Distance threshold isolates centroids of individual touching objects
            val peakThreshold = (maxDist * 0.38).coerceIn(3.0, 60.0)
            Imgproc.threshold(dist, peaks, peakThreshold, 255.0, Imgproc.THRESH_BINARY)
            peaks.convertTo(peaks8, CvType.CV_8U)

            val numComponents = Imgproc.connectedComponentsWithStats(peaks8, labels, stats, centroids)

            for (i in 1 until numComponents) {
                val cx = centroids.get(i, 0)[0].toInt()
                val cy = centroids.get(i, 1)[0].toInt()
                val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
                val w = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
                val h = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()

                if (cx in 0 until sourceBitmap.width && cy in 0 until sourceBitmap.height) {
                    val r = dist.get(cy, cx)[0].toFloat().coerceAtLeast(minSize / 2f).coerceAtMost(maxSize / 2f)
                    if (area >= 4 && r >= (minSize / 2f)) {
                        detectedCenters.add(android.graphics.Point(cx, cy))
                        detectedRadii.add(r + 3f)
                    }
                }
            }
        }

        // Fallback to contour detection if distance transform had too few objects
        if (detectedCenters.isEmpty()) {
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                val w = rect.width
                val h = rect.height
                val area = Imgproc.contourArea(contour)
                if (w in minSize..maxSize && h in minSize..maxSize && area > 50) {
                    val cx = rect.x + (w / 2)
                    val cy = rect.y + (h / 2)
                    val radius = (max(w, h) / 2f) + 4f
                    detectedCenters.add(android.graphics.Point(cx, cy))
                    detectedRadii.add(radius)
                }
            }
            hierarchy.release()
        }

        val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(3f, sourceBitmap.width / 400f)
            color = Color.parseColor("#10B981") // CamScanner Emerald
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#34D399")
            alpha = 110
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = max(20f, sourceBitmap.width / 55f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EF4444")
            style = Paint.Style.FILL
        }

        for (idx in detectedCenters.indices) {
            val pt = detectedCenters[idx]
            val radius = if (idx < detectedRadii.size) detectedRadii[idx] else (minSize * 1.5f)

            // Draw bounding circle
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius, fillPaint)
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius, circlePaint)

            // Draw number tag badge
            val badgeRadius = max(14f, textPaint.textSize * 0.75f)
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), badgeRadius, badgePaint)
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText("${idx + 1}", pt.x.toFloat(), pt.y.toFloat() - textOffset, textPaint)
        }

        mat.release()
        gray.release()
        binary.release()
        dist.release()
        peaks.release()
        peaks8.release()
        labels.release()
        stats.release()
        centroids.release()
        kernel.release()

        return CountResult(
            count = detectedCenters.size,
            annotatedBitmap = outputBitmap,
            detectedCenters = detectedCenters
        )
    }

    // =========================================================================
    // 2. ID Photo Maker Engine (Passport, Visa & Stamp Photos)
    // =========================================================================

    enum class IdPhotoSize(val displayName: String, val widthMm: Int, val heightMm: Int) {
        GOVT_EXAM_INDIA("Govt Exam - SSC/UPSC/IBPS (35 x 45 mm)", 35, 45),
        PASSPORT_INDIA_US("Standard Passport (2 x 2 inch / 51 x 51 mm)", 51, 51),
        VISA_SCHENGEN("Schengen / European Visa (35 x 45 mm)", 35, 45),
        GOVT_EXAM_SIGNATURE("Govt Exam Signature (35 x 15 mm)", 35, 15),
        STAMP_SIZE("Stamp Size (25 x 30 mm)", 25, 30),
        ID_CARD_STANDARD("Standard ID Card (30 x 40 mm)", 30, 40)
    }

    /**
     * Creates professional ID / Passport photo with customized background color,
     * optional Indian Govt exam Name & Date of Photo (DOP) strip,
     * border, and printable multi-photo sheet (6 or 8 copies on 4x6 / A4).
     */
    fun createIdPhoto(
        sourceBitmap: Bitmap,
        size: IdPhotoSize,
        backgroundColor: Int = Color.WHITE,
        addBorder: Boolean = true,
        borderWidthPx: Float = 2f,
        borderColor: Int = Color.parseColor("#CBD5E1"),
        candidateName: String? = null,
        dateOfPhoto: String? = null
    ): Bitmap {
        // Target aspect ratio
        val targetAspect = size.widthMm.toFloat() / size.heightMm.toFloat()
        val currentAspect = sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat()

        // Center crop to desired passport aspect ratio
        val cropRect = if (currentAspect > targetAspect) {
            val cropW = (sourceBitmap.height * targetAspect).toInt()
            val xOffset = (sourceBitmap.width - cropW) / 2
            Rect(xOffset, 0, xOffset + cropW, sourceBitmap.height)
        } else {
            val cropH = (sourceBitmap.width / targetAspect).toInt()
            val yOffset = (sourceBitmap.height - cropH) / 4 // Bias upward for head/face
            Rect(0, yOffset, sourceBitmap.width, yOffset + cropH)
        }

        val cropped = Bitmap.createBitmap(
            sourceBitmap,
            cropRect.left.coerceAtLeast(0),
            cropRect.top.coerceAtLeast(0),
            cropRect.width().coerceAtMost(sourceBitmap.width),
            cropRect.height().coerceAtMost(sourceBitmap.height)
        )

        val targetW = 600
        val targetH = (600 / targetAspect).toInt()
        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        if (cropped != sourceBitmap) cropped.recycle()

        // 1. Studio Background Replacement
        val segmented = if (backgroundColor != Color.TRANSPARENT && size != IdPhotoSize.GOVT_EXAM_SIGNATURE) {
            replacePortraitBackground(scaled, backgroundColor)
        } else {
            scaled
        }

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        canvas.drawBitmap(segmented, 0f, 0f, null)
        if (segmented != scaled) segmented.recycle()
        scaled.recycle()

        // 2. Govt Exam Name & Date of Photo (DOP) Strip Stamp
        val hasName = !candidateName.isNullOrBlank()
        val hasDop = !dateOfPhoto.isNullOrBlank()
        if (hasName || hasDop) {
            val stripHeight = (targetH * 0.16f).toInt()
            val stripTop = targetH - stripHeight

            val stripBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, stripTop.toFloat(), targetW.toFloat(), targetH.toFloat(), stripBgPaint)

            val stripLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(0f, stripTop.toFloat(), targetW.toFloat(), stripTop.toFloat(), stripLinePaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            if (hasName && hasDop) {
                textPaint.textSize = (stripHeight * 0.36f).coerceIn(18f, 26f)
                canvas.drawText(candidateName!!.trim().uppercase(), targetW / 2f, stripTop + (stripHeight * 0.44f), textPaint)
                textPaint.textSize = (stripHeight * 0.30f).coerceIn(15f, 22f)
                val dopFormatted = if (dateOfPhoto!!.trim().startsWith("DOP", ignoreCase = true)) dateOfPhoto.trim() else "DOP: ${dateOfPhoto.trim()}"
                canvas.drawText(dopFormatted, targetW / 2f, stripTop + (stripHeight * 0.86f), textPaint)
            } else if (hasName) {
                textPaint.textSize = (stripHeight * 0.48f).coerceIn(20f, 30f)
                canvas.drawText(candidateName!!.trim().uppercase(), targetW / 2f, stripTop + (stripHeight * 0.65f), textPaint)
            } else if (hasDop) {
                textPaint.textSize = (stripHeight * 0.44f).coerceIn(18f, 26f)
                val dopFormatted = if (dateOfPhoto!!.trim().startsWith("DOP", ignoreCase = true)) dateOfPhoto.trim() else "DOP: ${dateOfPhoto.trim()}"
                canvas.drawText(dopFormatted, targetW / 2f, stripTop + (stripHeight * 0.65f), textPaint)
            }
        }

        // 3. Fine outer border for clean scissor cutting or govt outline
        if (addBorder && borderWidthPx > 0f) {
            val halfBorder = borderWidthPx / 2f
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderWidthPx
                color = borderColor
            }
            canvas.drawRect(halfBorder, halfBorder, (targetW - halfBorder), (targetH - halfBorder), borderPaint)
        }

        return output
    }

    /**
     * Replaces ambient background of headshot with studio background color (White, Blue, Grey)
     * using corner backdrop sampling and color distance flood fill.
     */
    private fun replacePortraitBackground(source: Bitmap, targetBgColor: Int): Bitmap {
        val w = source.width
        val h = source.height
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        output.getPixels(pixels, 0, w, 0, 0, w, h)

        // Sample ambient backdrop color from top corners
        var sampleR = 0
        var sampleG = 0
        var sampleB = 0
        var sampleCount = 0
        val sampleSize = min(30, min(w, h))

        for (y in 0 until sampleSize) {
            for (x in 0 until sampleSize) {
                val p = pixels[y * w + x]
                sampleR += (p shr 16) and 0xFF
                sampleG += (p shr 8) and 0xFF
                sampleB += p and 0xFF
                sampleCount++
            }
            for (x in (w - sampleSize) until w) {
                val p = pixels[y * w + x]
                sampleR += (p shr 16) and 0xFF
                sampleG += (p shr 8) and 0xFF
                sampleB += p and 0xFF
                sampleCount++
            }
        }

        if (sampleCount == 0) return output
        val bgR = sampleR / sampleCount
        val bgG = sampleG / sampleCount
        val bgB = sampleB / sampleCount

        val targetR = (targetBgColor shr 16) and 0xFF
        val targetG = (targetBgColor shr 8) and 0xFF
        val targetB = targetBgColor and 0xFF

        val maxColorDist = 58.0
        val maxY = (h * 0.78).toInt() // Focus on upper body backdrop

        for (y in 0 until maxY) {
            for (x in 0 until w) {
                val idx = y * w + x
                val p = pixels[idx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val dist = kotlin.math.sqrt(
                    ((r - bgR) * (r - bgR) + (g - bgG) * (g - bgG) + (b - bgB) * (b - bgB)).toDouble()
                )

                if (dist < maxColorDist) {
                    val factor = (dist / maxColorDist).coerceIn(0.0, 1.0)
                    if (factor < 0.45) {
                        pixels[idx] = (0xFF shl 24) or (targetR shl 16) or (targetG shl 8) or targetB
                    } else {
                        // Soft edge anti-aliasing blend
                        val blendR = (targetR * (1.0 - factor) + r * factor).toInt()
                        val blendG = (targetG * (1.0 - factor) + g * factor).toInt()
                        val blendB = (targetB * (1.0 - factor) + b * factor).toInt()
                        pixels[idx] = (0xFF shl 24) or (blendR shl 16) or (blendG shl 8) or blendB
                    }
                }
            }
        }

        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }

    enum class PrintSheetType(
        val displayName: String,
        val paperWidthPx: Int,
        val paperHeightPx: Int,
        val maxCopies: Int
    ) {
        SINGLE("Single Photo", 600, 750, 1),
        PHOTO_PAPER_4X6("4×6\" Photo Paper (8 Copies)", 1200, 1800, 8),
        A4_SHEET("A4 Full Sheet (32 Copies)", 2480, 3508, 32),
        CUSTOM("Custom Copies", 1200, 1800, 32)
    }

    /**
     * Generates a 4x6 inch or A4 printable sheet with custom copy count
     * and precision dashed cutting guidelines for clean scissor cuts.
     */
    fun createPrintableSheet(
        singlePhoto: Bitmap,
        sheetType: PrintSheetType = PrintSheetType.PHOTO_PAPER_4X6,
        copies: Int = 8,
        drawCutGuides: Boolean = true
    ): Bitmap {
        if (sheetType == PrintSheetType.SINGLE) {
            return singlePhoto.copy(singlePhoto.config ?: Bitmap.Config.ARGB_8888, true)
        }

        val isA4 = sheetType == PrintSheetType.A4_SHEET || copies > 8
        val sheetW = if (isA4) 2480 else 1200
        val sheetH = if (isA4) 3508 else 1800

        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val (cols, rows) = if (isA4) {
            Pair(4, 8) // Up to 32 photos on A4
        } else {
            if (copies <= 6) Pair(2, 3) else Pair(2, 4) // Up to 8 photos on 4x6"
        }

        val totalSlots = cols * rows
        val effectiveCopies = copies.coerceIn(1, totalSlots)

        val marginX = (sheetW * 0.05f).toInt()
        val marginY = (sheetH * 0.04f).toInt()
        val availableW = sheetW - (2 * marginX)
        val availableH = sheetH - (2 * marginY)

        val maxCellW = availableW / cols
        val maxCellH = availableH / rows

        val aspect = singlePhoto.width.toFloat() / singlePhoto.height.toFloat()
        var photoW = (maxCellW * 0.88f).toInt()
        var photoH = (photoW / aspect).toInt()

        if (photoH > maxCellH * 0.90f) {
            photoH = (maxCellH * 0.90f).toInt()
            photoW = (photoH * aspect).toInt()
        }

        val scaled = Bitmap.createScaledBitmap(singlePhoto, photoW, photoH, true)

        val hSpacing = (availableW - (cols * photoW)) / (cols + 1)
        val vSpacing = (availableH - (rows * photoH)) / (rows + 1)

        val dashPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = if (isA4) 3f else 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }

        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        var count = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (count < effectiveCopies) {
                    val x = marginX + hSpacing + c * (photoW + hSpacing)
                    val y = marginY + vSpacing + r * (photoH + vSpacing)

                    canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + photoW).toFloat(), (y + photoH).toFloat(), borderPaint)

                    if (drawCutGuides) {
                        val pad = if (isA4) 10f else 6f
                        canvas.drawRect(
                            x - pad,
                            y - pad,
                            x + photoW + pad,
                            y + photoH + pad,
                            dashPaint
                        )
                    }
                    count++
                }
            }
        }
        scaled.recycle()
        return sheet
    }

    // =========================================================================
    // 3. Scan Code Engine (QR Code & Barcode Decoder)
    // =========================================================================

    /**
     * Decodes QR code and barcode data from document or camera bitmap.
     */
    fun decodeQrCode(sourceBitmap: Bitmap): String? {
        val mat = Mat()
        Utils.bitmapToMat(sourceBitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

        val qrDetector = QRCodeDetector()
        val points = Mat()
        val result = qrDetector.detectAndDecode(gray, points)

        mat.release()
        gray.release()
        points.release()

        return if (!result.isNullOrBlank()) result else null
    }

    // =========================================================================
    // 4. PDF to Long Image Engine (Vertical Page Stitching)
    // =========================================================================

    /**
     * Stitches all pages of a PDF into one seamless vertical long image.
     */
    suspend fun stitchPdfToLongImage(
        context: Context,
        pdfUri: Uri,
        outputFile: File,
        scale: Float = 1.5f
    ): File = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF file")

        pfd.use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                val totalPages = renderer.pageCount
                if (totalPages == 0) throw IllegalStateException("Empty PDF")

                // First pass: Calculate maximum width and total height
                var maxWidth = 0
                var totalHeight = 0
                val pageHeights = mutableListOf<Int>()
                val pageWidths = mutableListOf<Int>()

                for (i in 0 until totalPages) {
                    val page = renderer.openPage(i)
                    val pw = (page.width * scale).toInt()
                    val ph = (page.height * scale).toInt()
                    maxWidth = max(maxWidth, pw)
                    totalHeight += ph
                    pageWidths.add(pw)
                    pageHeights.add(ph)
                    page.close()
                }

                // Create the long canvas bitmap
                val longBitmap = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(longBitmap)
                canvas.drawColor(Color.WHITE)

                var currentY = 0f
                val dividerPaint = Paint().apply {
                    color = Color.parseColor("#E2E8F0")
                    strokeWidth = 2f
                }

                for (i in 0 until totalPages) {
                    val page = renderer.openPage(i)
                    val pw = pageWidths[i]
                    val ph = pageHeights[i]
                    val pageBmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
                    pageBmp.eraseColor(Color.WHITE)
                    page.render(pageBmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // Center page if width varies
                    val startX = (maxWidth - pw) / 2f
                    canvas.drawBitmap(pageBmp, startX, currentY, null)
                    pageBmp.recycle()

                    currentY += ph
                    if (i < totalPages - 1) {
                        canvas.drawLine(0f, currentY, maxWidth.toFloat(), currentY, dividerPaint)
                    }
                }

                FileOutputStream(outputFile).use { fos ->
                    longBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                longBitmap.recycle()
            }
        }
        outputFile
    }

    // =========================================================================
    // 5. Vintage Photo Restore & Enhance Engine
    // =========================================================================

    /**
     * Restores faded colors, removes film scratches, and sharpens details in vintage documents or photos.
     */
    fun restorePhoto(sourceBitmap: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(sourceBitmap, mat)

        val rgb = Mat()
        Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_RGBA2RGB)

        // 1. Bilateral filter for edge-preserving denoising / scratch softening
        val smoothed = Mat()
        Imgproc.bilateralFilter(rgb, smoothed, 9, 75.0, 75.0)

        // 2. Color Balance & Contrast CLAHE in LAB space
        val lab = Mat()
        Imgproc.cvtColor(smoothed, lab, Imgproc.COLOR_RGB2Lab)
        val channels = mutableListOf<Mat>()
        org.opencv.core.Core.split(lab, channels)

        // Apply CLAHE on L (Lightness) channel
        val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
        val lEnhanced = Mat()
        clahe.apply(channels[0], lEnhanced)
        channels[0] = lEnhanced

        org.opencv.core.Core.merge(channels, lab)
        val restoredRgb = Mat()
        Imgproc.cvtColor(lab, restoredRgb, Imgproc.COLOR_Lab2RGB)

        // 3. High-frequency unsharp mask for crisp text/facial restoration
        val blurred = Mat()
        Imgproc.GaussianBlur(restoredRgb, blurred, Size(0.0, 0.0), 3.0)
        val sharpened = Mat()
        org.opencv.core.Core.addWeighted(restoredRgb, 1.45, blurred, -0.45, 0.0, sharpened)

        val output = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
        val outRgba = Mat()
        Imgproc.cvtColor(sharpened, outRgba, Imgproc.COLOR_RGB2RGBA)
        Utils.matToBitmap(outRgba, output)

        mat.release()
        rgb.release()
        smoothed.release()
        lab.release()
        restoredRgb.release()
        blurred.release()
        sharpened.release()
        outRgba.release()

        return output
    }

    // =========================================================================
    // 6. Presentation / PPT Slide Deck Engine
    // =========================================================================

    /**
     * Generates a 100% authentic Microsoft PowerPoint (.pptx) presentation deck from document pages.
     */
    suspend fun exportPagesToPptx(
        pages: List<Bitmap>,
        outputFile: File,
        presentationTitle: String = "Presentation"
    ): File = withContext(Dispatchers.IO) {
        val success = com.docu.editor.core.export.PptxExportEngine.generatePptx(pages, outputFile, presentationTitle)
        if (!success) {
            throw java.io.IOException("Failed to generate OpenXML PowerPoint presentation")
        }
        outputFile
    }

    // =========================================================================
    // 7. Formula / Math LaTeX Parser
    // =========================================================================

    /**
     * Cleans OCR raw text of formulas and translates to clean LaTeX equation.
     */
    fun formatToLatexFormula(rawOcr: String): String {
        var latex = rawOcr.trim()
        // Greek letters
        latex = latex.replace(Regex("""\balpha\b""", RegexOption.IGNORE_CASE), "\\alpha")
        latex = latex.replace(Regex("""\bbeta\b""", RegexOption.IGNORE_CASE), "\\beta")
        latex = latex.replace(Regex("""\bgamma\b""", RegexOption.IGNORE_CASE), "\\gamma")
        latex = latex.replace(Regex("""\bdelta\b""", RegexOption.IGNORE_CASE), "\\delta")
        latex = latex.replace(Regex("""\btheta\b""", RegexOption.IGNORE_CASE), "\\theta")
        latex = latex.replace(Regex("""\blambda\b""", RegexOption.IGNORE_CASE), "\\lambda")
        latex = latex.replace(Regex("""\bsigma\b""", RegexOption.IGNORE_CASE), "\\sigma")
        latex = latex.replace(Regex("""\bomega\b""", RegexOption.IGNORE_CASE), "\\omega")
        latex = latex.replace(Regex("""\bmu\b""", RegexOption.IGNORE_CASE), "\\mu")
        latex = latex.replace(Regex("""\bpi\b""", RegexOption.IGNORE_CASE), "\\pi")
        latex = latex.replace(Regex("""\bDelta\b"""), "\\Delta")

        // Operators & Relations
        latex = latex.replace("<=", "\\le")
        latex = latex.replace(">=", "\\ge")
        latex = latex.replace("!=", "\\neq")
        latex = latex.replace("==", "\\equiv")
        latex = latex.replace("+/-", "\\pm")
        latex = latex.replace("+-", "\\pm")
        latex = latex.replace("*", "\\times")
        latex = latex.replace("->", "\\rightarrow")
        latex = latex.replace("<->", "\\leftrightarrow")

        // Calculus, Roots & Integrals
        latex = latex.replace(Regex("""\bsum\b""", RegexOption.IGNORE_CASE), "\\sum")
        latex = latex.replace(Regex("""\bintegral\b""", RegexOption.IGNORE_CASE), "\\int")
        latex = latex.replace(Regex("""\binf(inity)?\b""", RegexOption.IGNORE_CASE), "\\infty")
        latex = latex.replace(Regex("""sqrt\(([^)]+)\)"""), "\\sqrt{$1}")

        // Exponents & Subscripts
        latex = latex.replace("x^2", "x^{2}")
        latex = latex.replace("x^3", "x^{3}")
        latex = latex.replace("y^2", "y^{2}")

        return "$$ $latex $$"
    }
}
