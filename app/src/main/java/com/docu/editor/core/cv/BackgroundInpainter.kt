package com.docu.editor.core.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BackgroundInpainter {

    /**
     * Seamlessly erases original text by sampling the surrounding paper background
     * and synthesizing an ambient lighting gradient with matching paper micro-grain
     * or applying OpenCV Fast Marching Telea inpainting across ink strokes.
     */
    suspend fun inpaint(
        sourceBitmap: Bitmap,
        targetBounds: Rect,
        sampleMargin: Int = 10
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val safeTarget = Rect(
            targetBounds.left.coerceIn(0, width - 1),
            targetBounds.top.coerceIn(0, height - 1),
            targetBounds.right.coerceIn(1, width),
            targetBounds.bottom.coerceIn(1, height)
        )

        if (safeTarget.width() <= 0 || safeTarget.height() <= 0) {
            return@withContext sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        // 1. Watermark & complex background preservation
        if (WatermarkPreservingInpainter.hasComplexBackground(sourceBitmap, safeTarget)) {
            try {
                return@withContext WatermarkPreservingInpainter.inpaintWatermarkBackground(sourceBitmap, safeTarget)
            } catch (_: Throwable) {}
        }

        // 2. High-precision OpenCV Fast Marching Telea inpainting
        val cvResult = inpaintWithOpenCv(sourceBitmap, safeTarget)
        if (cvResult != null) {
            return@withContext cvResult
        }

        // 3. Fallback: Ambient paper color sampling & micro-grain gradient synthesis
        val sampledColors = samplePerimeterPaperColors(sourceBitmap, safeTarget, sampleMargin)
        val paperLuma = getLuminance(sampledColors.topColor)

        // Detect any crossing table grid lines or notebook ruled lines
        val horizontalLines = detectHorizontalCrossingLines(sourceBitmap, safeTarget, paperLuma)
        val verticalLines = detectVerticalCrossingLines(sourceBitmap, safeTarget, paperLuma)

        // Create clean output bitmap
        val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        // Fill text rectangle with smooth ambient paper gradient
        val patchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                safeTarget.left.toFloat(), safeTarget.top.toFloat(),
                safeTarget.left.toFloat(), safeTarget.bottom.toFloat(),
                sampledColors.topColor,
                sampledColors.bottomColor,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }

        // Proportional expansion to eliminate any residual anti-aliased text edge or outer serifs (e.g. ghost 'U')
        val extraPadX = (safeTarget.height() * 0.12f).toInt().coerceIn(3, 8)
        val extraPadY = (safeTarget.height() * 0.08f).toInt().coerceIn(2, 6)
        val fillRect = Rect(
            max(0, safeTarget.left - extraPadX),
            max(0, safeTarget.top - extraPadY),
            min(width, safeTarget.right + extraPadX),
            min(height, safeTarget.bottom + extraPadY)
        )
        canvas.drawRect(fillRect, patchPaint)

        // Inject subtle micro paper texture matching document noise
        if (sampledColors.hasNoise) {
            val random = Random(42)
            val noisePaint = Paint().apply { style = Paint.Style.FILL }
            val noiseCount = (fillRect.width() * fillRect.height() * 0.04f).toInt().coerceIn(10, 800)

            val baseLuma = (Color.red(sampledColors.topColor) + Color.green(sampledColors.topColor) + Color.blue(sampledColors.topColor)) / 3
            val isDarkPaper = baseLuma < 120

            for (i in 0 until noiseCount) {
                val nx = fillRect.left + random.nextFloat() * fillRect.width()
                val ny = fillRect.top + random.nextFloat() * fillRect.height()
                val alpha = random.nextInt(4, 14)
                val grainVal = if (isDarkPaper) 220 else 80
                noisePaint.color = Color.argb(alpha, grainVal, grainVal, grainVal)
                canvas.drawPoint(nx, ny, noisePaint)
            }
        }

        // Reconstruct crossing grid/notebook lines
        for (line in horizontalLines) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = line.color
                strokeWidth = line.thickness.toFloat().coerceAtLeast(1f)
                style = Paint.Style.STROKE
            }
            canvas.drawLine(
                safeTarget.left.toFloat() - 1f, line.coord.toFloat(),
                safeTarget.right.toFloat() + 1f, line.coord.toFloat(),
                linePaint
            )
        }

        for (line in verticalLines) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = line.color
                strokeWidth = line.thickness.toFloat().coerceAtLeast(1f)
                style = Paint.Style.STROKE
            }
            canvas.drawLine(
                line.coord.toFloat(), safeTarget.top.toFloat() - 1f,
                line.coord.toFloat(), safeTarget.bottom.toFloat() + 1f,
                linePaint
            )
        }

        outputBitmap
    }

    private fun inpaintWithOpenCv(
        source: Bitmap,
        target: Rect,
        padding: Int = 14
    ): Bitmap? {
        val width = source.width
        val height = source.height
        val cropLeft = max(0, target.left - padding)
        val cropTop = max(0, target.top - padding)
        val cropRight = min(width, target.right + padding)
        val cropBottom = min(height, target.bottom + padding)
        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop
        if (cropW <= 2 || cropH <= 2) return null

        val cropBitmap = Bitmap.createBitmap(source, cropLeft, cropTop, cropW, cropH)
        val srcMat = Mat()
        val rgbMat = Mat()
        val grayMat = Mat()
        val maskMat = Mat()
        val inpaintMat = Mat()
        val restoredCrop = Mat()

        return try {
            Utils.bitmapToMat(cropBitmap, srcMat)
            Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val paperLuma = estimateLocalPaperLuma(grayMat)
            val inkThreshold = (paperLuma - 20.0).coerceIn(40.0, 215.0)

            Imgproc.threshold(grayMat, maskMat, inkThreshold, 255.0, Imgproc.THRESH_BINARY_INV)

            val maskPad = (target.height() * 0.12f).toInt().coerceIn(3, 8)
            val relLeft = max(0, target.left - cropLeft - maskPad)
            val relTop = max(0, target.top - cropTop - (maskPad / 2))
            val relRight = min(maskMat.cols(), target.right - cropLeft + maskPad)
            val relBottom = min(maskMat.rows(), target.bottom - cropTop + (maskPad / 2))

            for (r in 0 until maskMat.rows()) {
                if (r < relTop || r >= relBottom) {
                    val row = maskMat.row(r)
                    row.setTo(Scalar(0.0))
                    row.release()
                }
            }
            for (c in 0 until maskMat.cols()) {
                if (c < relLeft || c >= relRight) {
                    val col = maskMat.col(c)
                    col.setTo(Scalar(0.0))
                    col.release()
                }
            }

            // 5x5 dilation eliminates all anti-aliased subpixel edge halos around previous text
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.dilate(maskMat, maskMat, kernel)
            kernel.release()

            // Navier-Stokes/Telea inpainting with 5.0 radius for zero-halo paper texture continuity
            Photo.inpaint(rgbMat, maskMat, inpaintMat, 5.0, Photo.INPAINT_TELEA)
            Imgproc.cvtColor(inpaintMat, restoredCrop, Imgproc.COLOR_RGB2RGBA)

            val outCropBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(restoredCrop, outCropBitmap)

            val output = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(output)
            canvas.drawBitmap(outCropBitmap, cropLeft.toFloat(), cropTop.toFloat(), null)

            val hLines = detectHorizontalCrossingLines(source, target, paperLuma.toInt())
            val vLines = detectVerticalCrossingLines(source, target, paperLuma.toInt())
            for (line in hLines) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = line.color
                    strokeWidth = line.thickness.toFloat().coerceAtLeast(1f)
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(
                    target.left.toFloat() - 1f, line.coord.toFloat(),
                    target.right.toFloat() + 1f, line.coord.toFloat(),
                    p
                )
            }
            for (line in vLines) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = line.color
                    strokeWidth = line.thickness.toFloat().coerceAtLeast(1f)
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(
                    line.coord.toFloat(), target.top.toFloat() - 1f,
                    line.coord.toFloat(), target.bottom.toFloat() + 1f,
                    p
                )
            }

            outCropBitmap.recycle()
            cropBitmap.recycle()
            output
        } catch (_: Throwable) {
            cropBitmap.recycle()
            null
        } finally {
            srcMat.release()
            rgbMat.release()
            grayMat.release()
            maskMat.release()
            inpaintMat.release()
            restoredCrop.release()
        }
    }

    private fun estimateLocalPaperLuma(grayMat: Mat): Double {
        val rows = grayMat.rows()
        val cols = grayMat.cols()
        var sum = 0.0
        var count = 0
        for (c in 0 until cols step 2) {
            sum += grayMat.get(0, c)[0]
            sum += grayMat.get(rows - 1, c)[0]
            count += 2
        }
        for (r in 1 until rows - 1 step 2) {
            sum += grayMat.get(r, 0)[0]
            sum += grayMat.get(r, cols - 1)[0]
            count += 2
        }
        return if (count > 0) sum / count else 240.0
    }

    private data class PaperSampleResult(
        val topColor: Int,
        val bottomColor: Int,
        val hasNoise: Boolean
    )

    private fun samplePerimeterPaperColors(
        source: Bitmap,
        target: Rect,
        margin: Int
    ): PaperSampleResult {
        val width = source.width
        val height = source.height

        val horizontalSamples = mutableListOf<Int>()
        val verticalSamples = mutableListOf<Int>()

        // 1. Primary: Sample HORIZONTALLY along the exact text baseline (Left and Right margins)
        val leftX1 = max(0, target.left - 14)
        val leftX2 = max(0, target.left - 2)
        val rightX1 = min(width - 1, target.right + 2)
        val rightX2 = min(width - 1, target.right + 14)

        val midY1 = max(0, target.top + 2)
        val midY2 = min(height - 1, target.bottom - 2)

        for (y in midY1..midY2) {
            for (x in leftX1..leftX2) {
                horizontalSamples.add(source.getPixel(x, y))
            }
            for (x in rightX1..rightX2) {
                horizontalSamples.add(source.getPixel(x, y))
            }
        }

        // 2. Secondary: Sample TOP and BOTTOM ONLY 2-3px close
        val topY = max(0, target.top - 2)
        val botY = min(height - 1, target.bottom + 2)
        val stepX = max(1, target.width() / 15)

        for (x in target.left until target.right step stepX) {
            if (topY >= 0) verticalSamples.add(source.getPixel(x, topY))
            if (botY < height) verticalSamples.add(source.getPixel(x, botY))
        }

        val cleanHorizontal = filterPaperPixels(horizontalSamples)
        val cleanVertical = filterPaperPixels(verticalSamples)

        val horizontalLuma = cleanHorizontal?.let { getLuminance(it) } ?: 250
        val verticalLuma = cleanVertical?.let { getLuminance(it) } ?: horizontalLuma

        val baseColor = if (cleanHorizontal != null) {
            if (cleanVertical != null && kotlin.math.abs(verticalLuma - horizontalLuma) < 18) {
                blendColors(cleanHorizontal, cleanVertical, 0.7f)
            } else {
                cleanHorizontal
            }
        } else {
            cleanVertical ?: Color.WHITE
        }

        val finalColor = baseColor

        return PaperSampleResult(
            topColor = finalColor,
            bottomColor = finalColor,
            hasNoise = getLuminance(finalColor) < 235
        )
    }

    private fun getLuminance(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * ratio + Color.red(c2) * inv).toInt()
        val g = (Color.green(c1) * ratio + Color.green(c2) * inv).toInt()
        val b = (Color.blue(c1) * ratio + Color.blue(c2) * inv).toInt()
        return Color.rgb(r, g, b)
    }

    private fun filterPaperPixels(samples: List<Int>): Int? {
        if (samples.isEmpty()) return null

        val sorted = samples.map { c ->
            Pair(c, getLuminance(c))
        }.sortedBy { it.second }

        val startIndex = (sorted.size * 0.50f).toInt().coerceIn(0, sorted.size - 1)
        val validSamples = sorted.subList(startIndex, sorted.size).map { it.first }

        if (validSamples.isEmpty()) return null

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (c in validSamples) {
            sumR += Color.red(c)
            sumG += Color.green(c)
            sumB += Color.blue(c)
        }
        val count = validSamples.size
        return Color.rgb((sumR / count).toInt(), (sumG / count).toInt(), (sumB / count).toInt())
    }

    private data class CrossingLine(val coord: Int, val thickness: Int, val color: Int)

    private fun detectHorizontalCrossingLines(
        source: Bitmap,
        target: Rect,
        paperLuma: Int
    ): List<CrossingLine> {
        val width = source.width
        val leftX = max(0, target.left - 4)
        val rightX = min(width - 1, target.right + 4)
        if (target.left <= 4 || target.right >= width - 5) return emptyList()

        val lines = mutableListOf<CrossingLine>()
        var inLine = false
        var lineStartY = 0
        val lineColors = mutableListOf<Int>()

        for (y in target.top..target.bottom) {
            val leftPix = source.getPixel(leftX, y)
            val rightPix = source.getPixel(rightX, y)
            val leftLuma = getLuminance(leftPix)
            val rightLuma = getLuminance(rightPix)

            val isLeftDark = (paperLuma - leftLuma) >= 28
            val isRightDark = (paperLuma - rightLuma) >= 28
            val lumaDiff = kotlin.math.abs(leftLuma - rightLuma)

            if (isLeftDark && isRightDark && lumaDiff < 35) {
                if (!inLine) {
                    inLine = true
                    lineStartY = y
                    lineColors.clear()
                }
                lineColors.add(leftPix)
                lineColors.add(rightPix)
            } else {
                if (inLine) {
                    val thickness = y - lineStartY
                    if (thickness in 1..8) {
                        val avgColor = averageColor(lineColors)
                        lines.add(CrossingLine(lineStartY + thickness / 2, thickness, avgColor))
                    }
                    inLine = false
                }
            }
        }
        if (inLine) {
            val thickness = target.bottom + 1 - lineStartY
            if (thickness in 1..8) {
                val avgColor = averageColor(lineColors)
                lines.add(CrossingLine(lineStartY + thickness / 2, thickness, avgColor))
            }
        }
        return lines
    }

    private fun detectVerticalCrossingLines(
        source: Bitmap,
        target: Rect,
        paperLuma: Int
    ): List<CrossingLine> {
        val width = source.width
        val height = source.height
        val topY = max(0, target.top - 4)
        val botY = min(height - 1, target.bottom + 4)
        if (target.top <= 4 || target.bottom >= height - 5) return emptyList()

        val lines = mutableListOf<CrossingLine>()
        var inLine = false
        var lineStartX = 0
        val lineColors = mutableListOf<Int>()

        for (x in target.left..target.right) {
            val topPix = source.getPixel(x, topY)
            val botPix = source.getPixel(x, botY)
            val topLuma = getLuminance(topPix)
            val botLuma = getLuminance(botPix)

            val isTopDark = (paperLuma - topLuma) >= 28
            val isBotDark = (paperLuma - botLuma) >= 28
            val lumaDiff = kotlin.math.abs(topLuma - botLuma)

            if (isTopDark && isBotDark && lumaDiff < 35) {
                if (!inLine) {
                    inLine = true
                    lineStartX = x
                    lineColors.clear()
                }
                lineColors.add(topPix)
                lineColors.add(botPix)
            } else {
                if (inLine) {
                    val thickness = x - lineStartX
                    if (thickness in 1..8) {
                        val avgColor = averageColor(lineColors)
                        lines.add(CrossingLine(lineStartX + thickness / 2, thickness, avgColor))
                    }
                    inLine = false
                }
            }
        }
        if (inLine) {
            val thickness = target.right + 1 - lineStartX
            if (thickness in 1..8) {
                val avgColor = averageColor(lineColors)
                lines.add(CrossingLine(lineStartX + thickness / 2, thickness, avgColor))
            }
        }
        return lines
    }

    private fun averageColor(colors: List<Int>): Int {
        if (colors.isEmpty()) return Color.BLACK
        var r = 0L
        var g = 0L
        var b = 0L
        for (c in colors) {
            r += Color.red(c)
            g += Color.green(c)
            b += Color.blue(c)
        }
        val size = colors.size
        return Color.rgb((r / size).toInt(), (g / size).toInt(), (b / size).toInt())
    }
}
