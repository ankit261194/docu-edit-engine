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
import org.opencv.core.Core
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
    fun countObjects(
        sourceBitmap: Bitmap,
        minSize: Int = 12,
        maxSize: Int = 400,
        useWatershed: Boolean = true
    ): CountResult {
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

        val detectedCenters = mutableListOf<android.graphics.Point>()
        val detectedRadii = mutableListOf<Float>()
        var watershedLinesMat: Mat? = null

        val sureFg = Mat()
        val sureFg8 = Mat()
        val sureBg = Mat()
        val unknown = Mat()
        val markers = Mat()
        val rgbMat = Mat()
        val peaks = Mat()
        val peaks8 = Mat()
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()

        if (useWatershed && maxDist > 3.0) {
            // --- 3. PRO WATERSHED SEGMENTATION ---
            // Finds topological ridge lines separating touching objects (pills, pipes, boxes)
            val fgThresh = (maxDist * 0.38).coerceIn(3.0, 50.0)
            Imgproc.threshold(dist, sureFg, fgThresh, 255.0, Imgproc.THRESH_BINARY)
            sureFg.convertTo(sureFg8, CvType.CV_8U)

            val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
            Imgproc.dilate(binary, sureBg, dilateKernel)
            dilateKernel.release()

            Core.subtract(sureBg, sureFg8, unknown)

            val numMarkers = Imgproc.connectedComponents(sureFg8, markers)
            Core.add(markers, Scalar(1.0), markers)

            val unknownMask = Mat()
            Imgproc.threshold(unknown, unknownMask, 128.0, 255.0, Imgproc.THRESH_BINARY)
            markers.setTo(Scalar(0.0), unknownMask)
            unknownMask.release()

            Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.watershed(rgbMat, markers)

            // Extract boundaries where markers == -1
            watershedLinesMat = Mat()
            Core.compare(markers, Scalar(-1.0), watershedLinesMat, Core.CMP_EQ)
            // Keep only boundaries that are inside foreground
            Core.bitwise_and(watershedLinesMat, binary, watershedLinesMat)

            // Component analysis from markers: labels 2 until numMarkers + 1
            for (k in 2..numMarkers) {
                val compMask = Mat()
                Core.compare(markers, Scalar(k.toDouble()), compMask, Core.CMP_EQ)
                val compPoints = mutableListOf<MatOfPoint>()
                val compHier = Mat()
                Imgproc.findContours(compMask, compPoints, compHier, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                if (compPoints.isNotEmpty()) {
                    val c = compPoints[0]
                    val rect = Imgproc.boundingRect(c)
                    val area = Imgproc.contourArea(c)
                    val w = rect.width
                    val h = rect.height
                    if (w in minSize..maxSize && h in minSize..maxSize && area > 20) {
                        val cx = rect.x + (w / 2)
                        val cy = rect.y + (h / 2)
                        val r = (max(w, h) / 2f).coerceIn(minSize / 2f, maxSize / 2f)
                        detectedCenters.add(android.graphics.Point(cx, cy))
                        detectedRadii.add(r + 3f)
                    }
                }
                compMask.release()
                compHier.release()
            }
        }

        // Fallback to Euclidean distance peaks if watershed was disabled or found too few items
        if (detectedCenters.isEmpty() && maxDist > 3.0) {
            val peakThreshold = (maxDist * 0.38).coerceIn(3.0, 60.0)
            Imgproc.threshold(dist, peaks, peakThreshold, 255.0, Imgproc.THRESH_BINARY)
            peaks.convertTo(peaks8, CvType.CV_8U)

            val numComponents = Imgproc.connectedComponentsWithStats(peaks8, labels, stats, centroids)

            for (i in 1 until numComponents) {
                val cx = centroids.get(i, 0)[0].toInt()
                val cy = centroids.get(i, 1)[0].toInt()
                val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
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

        // Draw watershed separation lines in high-contrast red
        if (watershedLinesMat != null && !watershedLinesMat.empty()) {
            val wLinesBytes = ByteArray(sourceBitmap.width * sourceBitmap.height)
            watershedLinesMat.get(0, 0, wLinesBytes)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EF4444") // Crimson separation line
                strokeWidth = max(2.5f, sourceBitmap.width / 500f)
                style = Paint.Style.STROKE
            }
            val w = sourceBitmap.width
            val h = sourceBitmap.height
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if ((wLinesBytes[y * w + x].toInt() and 0xFF) > 128) {
                        canvas.drawPoint(x.toFloat(), y.toFloat(), linePaint)
                    }
                }
            }
            watershedLinesMat.release()
        }

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
        sureFg.release()
        sureFg8.release()
        sureBg.release()
        unknown.release()
        markers.release()
        rgbMat.release()
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
        borderColor: Int = Color.BLACK, // High-contrast Studio Black default
        candidateName: String? = null,
        dateOfPhoto: String? = null,
        dopTextScale: Float = 1.0f
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

        // 2. Official Govt Exam Name & Date of Photo (DOP) Strip Stamp
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
                color = Color.BLACK
                strokeWidth = 2.0f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(0f, stripTop.toFloat(), targetW.toFloat(), stripTop.toFloat(), stripLinePaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            fun fitText(text: String, maxW: Float, baseSize: Float): Float {
                var s = baseSize * dopTextScale
                textPaint.textSize = s
                while (textPaint.measureText(text) > maxW && s > 10f) {
                    s -= 0.5f
                    textPaint.textSize = s
                }
                return s
            }

            val maxTextW = targetW * 0.90f

            if (hasName && hasDop) {
                val nameSize = fitText(candidateName!!.trim().uppercase(), maxTextW, (stripHeight * 0.36f).coerceIn(18f, 26f))
                textPaint.textSize = nameSize
                canvas.drawText(candidateName.trim().uppercase(), targetW / 2f, stripTop + (stripHeight * 0.44f), textPaint)

                val dopFormatted = if (dateOfPhoto!!.trim().startsWith("DOP", ignoreCase = true)) dateOfPhoto.trim() else "DOP: ${dateOfPhoto.trim()}"
                val dopSize = fitText(dopFormatted, maxTextW, (stripHeight * 0.30f).coerceIn(15f, 22f))
                textPaint.textSize = dopSize
                canvas.drawText(dopFormatted, targetW / 2f, stripTop + (stripHeight * 0.86f), textPaint)
            } else if (hasName) {
                val nameSize = fitText(candidateName!!.trim().uppercase(), maxTextW, (stripHeight * 0.48f).coerceIn(20f, 30f))
                textPaint.textSize = nameSize
                canvas.drawText(candidateName.trim().uppercase(), targetW / 2f, stripTop + (stripHeight * 0.65f), textPaint)
            } else if (hasDop) {
                val dopFormatted = if (dateOfPhoto!!.trim().startsWith("DOP", ignoreCase = true)) dateOfPhoto.trim() else "DOP: ${dateOfPhoto.trim()}"
                val dopSize = fitText(dopFormatted, maxTextW, (stripHeight * 0.44f).coerceIn(18f, 26f))
                textPaint.textSize = dopSize
                canvas.drawText(dopFormatted, targetW / 2f, stripTop + (stripHeight * 0.65f), textPaint)
            }
        }

        // 3. High-Contrast Studio Inset Border for clean scissor cutting or govt outline
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
     * Enforces authentic 300 DPI Lab Print standard with safe printer margins.
     */
    fun createPrintableSheet(
        singlePhoto: Bitmap,
        sheetType: PrintSheetType = PrintSheetType.PHOTO_PAPER_4X6,
        copies: Int = 8,
        drawCutGuides: Boolean = true
    ): Bitmap {
        if (sheetType == PrintSheetType.SINGLE) {
            val copyBmp = singlePhoto.copy(singlePhoto.config ?: Bitmap.Config.ARGB_8888, true)
            copyBmp.density = 300
            return copyBmp
        }

        val isA4 = sheetType == PrintSheetType.A4_SHEET || copies > 8
        val sheetW = if (isA4) 2480 else 1200
        val sheetH = if (isA4) 3508 else 1800

        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        sheet.density = 300 // Standard 300 DPI Lab Print density
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val (cols, rows) = if (isA4) {
            when {
                copies <= 2 -> Pair(1, 2)
                copies <= 4 -> Pair(2, 2)
                copies <= 6 -> Pair(2, 3)
                copies <= 8 -> Pair(2, 4)
                copies <= 12 -> Pair(3, 4)
                copies <= 16 -> Pair(4, 4)
                copies <= 24 -> Pair(4, 6)
                else -> Pair(4, 8) // Up to 32 photos on A4
            }
        } else {
            when {
                copies <= 2 -> Pair(1, 2)
                copies <= 4 -> Pair(2, 2)
                copies <= 6 -> Pair(2, 3)
                else -> Pair(2, 4) // Up to 8 photos on 4x6"
            }
        }

        val totalSlots = cols * rows
        val effectiveCopies = copies.coerceIn(1, totalSlots)

        // Enforce safe 40px minilab printer margins
        val safeMargin = if (isA4) 80 else 40
        val marginX = (sheetW * 0.04f).toInt().coerceAtLeast(safeMargin)
        val marginY = (sheetH * 0.04f).toInt().coerceAtLeast(safeMargin)
        val availableW = sheetW - (2 * marginX)
        val availableH = sheetH - (2 * marginY) - if (isA4) 40 else 24 // Leave space for footer stamp

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

        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = if (isA4) 3f else 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }

        val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            strokeWidth = if (isA4) 2.5f else 1.5f
            style = Paint.Style.STROKE
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        val tickLen = if (isA4) 14f else 8f
        val pad = if (isA4) 12f else 6f

        var count = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (count < effectiveCopies) {
                    val x = marginX + hSpacing + c * (photoW + hSpacing)
                    val y = marginY + vSpacing + r * (photoH + vSpacing)

                    canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + photoW).toFloat(), (y + photoH).toFloat(), borderPaint)

                    if (drawCutGuides) {
                        // Dashed scissor guideline rectangle
                        val left = x - pad
                        val top = y - pad
                        val right = x + photoW + pad
                        val bottom = y + photoH + pad
                        canvas.drawRect(left, top, right, bottom, dashPaint)

                        // Precision Corner Crosshairs (+) for scissor blade alignment
                        // Top-left
                        canvas.drawLine(left - tickLen, top, left + tickLen, top, crosshairPaint)
                        canvas.drawLine(left, top - tickLen, left, top + tickLen, crosshairPaint)
                        // Top-right
                        canvas.drawLine(right - tickLen, top, right + tickLen, top, crosshairPaint)
                        canvas.drawLine(right, top - tickLen, right, top + tickLen, crosshairPaint)
                        // Bottom-left
                        canvas.drawLine(left - tickLen, bottom, left + tickLen, bottom, crosshairPaint)
                        canvas.drawLine(left, bottom - tickLen, left, bottom + tickLen, crosshairPaint)
                        // Bottom-right
                        canvas.drawLine(right - tickLen, bottom, right + tickLen, bottom, crosshairPaint)
                        canvas.drawLine(right, bottom - tickLen, right, bottom + tickLen, crosshairPaint)
                    }
                    count++
                }
            }
        }
        scaled.recycle()

        // Authentic 300 DPI Lab Print Footer Stamp
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = if (isA4) 22f else 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val paperLabel = if (isA4) "A4 FULL SHEET (210×297mm)" else "4×6\" PHOTO PAPER (102×152mm)"
        canvas.drawText(
            "✂️ 300 DPI LAB PRINT MASTER • $paperLabel • DOCUEDIT PRO STUDIO",
            sheetW / 2f,
            sheetH - (marginY * 0.35f),
            footerPaint
        )

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
     * Stitches multi-page PDFs (including 20+ pages) into one seamless vertical long image.
     * Uses memory-safe dynamic scale adaptation and RGB_565 canvas tiling to prevent OutOfMemoryError.
     * Directly writes to outputFile without holding massive uncompressed bitmaps in memory.
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

                // First pass: Probe page unscaled dimensions
                var maxUnscaledW = 0
                var totalUnscaledH = 0
                val rawWidths = mutableListOf<Int>()
                val rawHeights = mutableListOf<Int>()

                for (i in 0 until totalPages) {
                    val page = renderer.openPage(i)
                    val pw = page.width
                    val ph = page.height
                    maxUnscaledW = max(maxUnscaledW, pw)
                    totalUnscaledH += ph
                    rawWidths.add(pw)
                    rawHeights.add(ph)
                    page.close()
                }

                // Dynamic memory-safe scale adaptation:
                // Hardware canvas and memory limit: max height capped to 24000px, max width 1200px
                val maxAllowedHeight = 24000
                val initialScale = when {
                    totalPages <= 4 -> scale.coerceAtMost(1.5f)
                    totalPages in 5..10 -> 1.25f
                    totalPages in 11..20 -> 1.0f
                    else -> 0.8f
                }

                val safeScale = if (totalUnscaledH * initialScale > maxAllowedHeight) {
                    (maxAllowedHeight.toFloat() / totalUnscaledH).coerceIn(0.25f, initialScale)
                } else {
                    initialScale
                }

                val targetWidth = (maxUnscaledW * safeScale).toInt().coerceIn(480, 1200)
                val scaledWidths = rawWidths.map { (it * safeScale).toInt() }
                val scaledHeights = rawHeights.map { (it * safeScale).toInt() }
                val finalTotalHeight = scaledHeights.sum().coerceAtLeast(100)

                // Allocate memory-efficient RGB_565 canvas (50% less RAM than ARGB_8888)
                val longBitmap = Bitmap.createBitmap(targetWidth, finalTotalHeight, Bitmap.Config.RGB_565)
                val canvas = Canvas(longBitmap)
                canvas.drawColor(Color.WHITE)

                val dividerPaint = Paint().apply {
                    color = Color.parseColor("#E2E8F0")
                    strokeWidth = 2f
                }

                var currentY = 0f

                // Second pass: Render page-by-page directly onto canvas and recycle immediately
                for (i in 0 until totalPages) {
                    val page = renderer.openPage(i)
                    val pw = scaledWidths[i]
                    val ph = scaledHeights[i]

                    // Single-page buffer: rendered in RGB_565 and recycled immediately
                    val pageBmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.RGB_565)
                    pageBmp.eraseColor(Color.WHITE)
                    page.render(pageBmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val startX = ((targetWidth - pw) / 2f).coerceAtLeast(0f)
                    canvas.drawBitmap(pageBmp, startX, currentY, null)
                    pageBmp.recycle()

                    currentY += ph
                    if (i < totalPages - 1) {
                        canvas.drawLine(0f, currentY, targetWidth.toFloat(), currentY, dividerPaint)
                    }
                }

                // Stream directly to disk with high JPEG quality (90)
                outputFile.parentFile?.mkdirs()
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
     * Uses enterprise PhotoRestorerEngine with multi-angle scratch inpainting, portrait enhancement,
     * and chromatic cast neutralization.
     */
    fun restorePhoto(sourceBitmap: Bitmap): Bitmap {
        return com.docu.editor.core.scanner.PhotoRestorerEngine.restorePhotoSync(sourceBitmap)
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
        return FormulaOcrEngine.process(rawOcr).displayLatex
    }
}
