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

        // 1. Watermark & complex background preservation (guilloche, security patterns)
        if (WatermarkPreservingInpainter.hasComplexBackground(sourceBitmap, safeTarget)) {
            try {
                return@withContext WatermarkPreservingInpainter.inpaintWatermarkBackground(sourceBitmap, safeTarget)
            } catch (_: Throwable) {}
        }

        // 2. Pure ambient document paper sampling & micro-grain gradient synthesis
        val prelimColors = samplePerimeterPaperColors(sourceBitmap, safeTarget, sampleMargin)
        val paperLuma = getLuminance(prelimColors.topColor)

        // Detect any crossing table grid lines or notebook ruled lines, and surrounding cell borders
        val gridResult = detectSurroundingAndCrossingLines(sourceBitmap, safeTarget, paperLuma)
        val horizontalLines = gridResult.horizontalLines
        val verticalLines = gridResult.verticalLines

        // Sample perimeter paper colors clamped strictly within detected cell boundaries
        val topLimitY = if (gridResult.topBorderY != null) gridResult.topBorderY + 2 else 0
        val botLimitY = if (gridResult.botBorderY != null) gridResult.botBorderY - 2 else height - 1
        val leftLimitX = if (gridResult.leftBorderX != null) gridResult.leftBorderX + 2 else 0
        val rightLimitX = if (gridResult.rightBorderX != null) gridResult.rightBorderX - 2 else width - 1

        val sampledColors = samplePerimeterPaperColors(
            sourceBitmap, safeTarget, sampleMargin,
            topLimitY = topLimitY,
            botLimitY = botLimitY,
            leftLimitX = leftLimitX,
            rightLimitX = rightLimitX
        )

        // Create clean output bitmap
        val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        // Soft-feathered ambient paper patch (zero hard rectangular boundaries!)
        val extraPadX = (safeTarget.height() * 0.12f).toInt().coerceIn(3, 8)
        val extraPadY = (safeTarget.height() * 0.10f).toInt().coerceIn(3, 8)

        // Clamp target and fill area strictly within surrounding cell boundaries so we never cut into table borders
        val effectiveTarget = Rect(
            if (gridResult.leftBorderX != null && safeTarget.left <= gridResult.leftBorderX) gridResult.leftBorderX + 1 else safeTarget.left,
            if (gridResult.topBorderY != null && safeTarget.top <= gridResult.topBorderY) gridResult.topBorderY + 1 else safeTarget.top,
            if (gridResult.rightBorderX != null && safeTarget.right >= gridResult.rightBorderX) gridResult.rightBorderX - 1 else safeTarget.right,
            if (gridResult.botBorderY != null && safeTarget.bottom >= gridResult.botBorderY) gridResult.botBorderY - 1 else safeTarget.bottom
        )
        if (effectiveTarget.width() <= 0 || effectiveTarget.height() <= 0) {
            effectiveTarget.set(safeTarget)
        }

        val rawLeft = max(0, effectiveTarget.left - extraPadX)
        val rawTop = max(0, effectiveTarget.top - extraPadY)
        val rawRight = min(width, effectiveTarget.right + extraPadX)
        val rawBottom = min(height, effectiveTarget.bottom + extraPadY)

        val fillLeft = if (gridResult.leftBorderX != null) max(rawLeft, gridResult.leftBorderX + 1) else rawLeft
        val fillRight = if (gridResult.rightBorderX != null) min(rawRight, gridResult.rightBorderX - 1) else rawRight
        val fillTop = if (gridResult.topBorderY != null) max(rawTop, gridResult.topBorderY + 1) else rawTop
        val fillBottom = if (gridResult.botBorderY != null) min(rawBottom, gridResult.botBorderY - 1) else rawBottom

        val fillRect = Rect(
            min(fillLeft, effectiveTarget.left),
            min(fillTop, effectiveTarget.top),
            max(fillRight, effectiveTarget.right),
            max(fillBottom, effectiveTarget.bottom)
        )
        val patchW = fillRect.width()
        val patchH = fillRect.height()
        if (patchW > 0 && patchH > 0) {
            val patchBmp = Bitmap.createBitmap(patchW, patchH, Bitmap.Config.ARGB_8888)
            val patchCanvas = Canvas(patchBmp)

            val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, patchH.toFloat(),
                    sampledColors.topColor,
                    sampledColors.bottomColor,
                    Shader.TileMode.CLAMP
                )
                style = Paint.Style.FILL
            }
            patchCanvas.drawRect(0f, 0f, patchW.toFloat(), patchH.toFloat(), gradPaint)

            // Inject subtle micro paper texture matching document noise
            if (sampledColors.hasNoise) {
                val random = Random(42)
                val noisePaint = Paint().apply { style = Paint.Style.FILL }
                val noiseCount = (patchW * patchH * 0.04f).toInt().coerceIn(10, 800)
                val baseLuma = (Color.red(sampledColors.topColor) + Color.green(sampledColors.topColor) + Color.blue(sampledColors.topColor)) / 3
                val isDarkPaper = baseLuma < 120

                for (i in 0 until noiseCount) {
                    val nx = random.nextFloat() * patchW
                    val ny = random.nextFloat() * patchH
                    val alpha = random.nextInt(4, 14)
                    val grainVal = if (isDarkPaper) 220 else 80
                    noisePaint.color = Color.argb(alpha, grainVal, grainVal, grainVal)
                    patchCanvas.drawPoint(nx, ny, noisePaint)
                }
            }

            // Alpha mask feathering:
            // 1. relTarget (effectiveTarget) is 100% SOLID OPAQUE (alpha = 255) to eliminate old text completely (zero ghosting).
            // 2. Feathering strictly applies in the outer padding margins towards outer boundaries of fillRect.
            val relTarget = Rect(
                (effectiveTarget.left - fillRect.left).coerceIn(0, patchW),
                (effectiveTarget.top - fillRect.top).coerceIn(0, patchH),
                (effectiveTarget.right - fillRect.left).coerceIn(0, patchW),
                (effectiveTarget.bottom - fillRect.top).coerceIn(0, patchH)
            )

            val padL = relTarget.left
            val padT = relTarget.top
            val padR = patchW - relTarget.right
            val padB = patchH - relTarget.bottom

            if (padL > 0 || padT > 0 || padR > 0 || padB > 0) {
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                }
                val alphaMask = Bitmap.createBitmap(patchW, patchH, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(patchW * patchH)

                for (y in 0 until patchH) {
                    val rowOffset = y * patchW
                    val dy = when {
                        y < relTarget.top -> relTarget.top - y
                        y >= relTarget.bottom -> y - (relTarget.bottom - 1)
                        else -> 0
                    }
                    val maxDy = if (y < relTarget.top) padT else padB
                    val fracY = if (maxDy > 0) (1f - (dy.toFloat() / maxDy)).coerceIn(0f, 1f) else 1f

                    for (x in 0 until patchW) {
                        val dx = when {
                            x < relTarget.left -> relTarget.left - x
                            x >= relTarget.right -> x - (relTarget.right - 1)
                            else -> 0
                        }
                        val maxDx = if (x < relTarget.left) padL else padR
                        val fracX = if (maxDx > 0) (1f - (dx.toFloat() / maxDx)).coerceIn(0f, 1f) else 1f

                        val alpha = if (dx == 0 && dy == 0) {
                            255
                        } else {
                            (min(fracX, fracY) * 255).toInt().coerceIn(0, 255)
                        }
                        pixels[rowOffset + x] = Color.argb(alpha, 255, 255, 255)
                    }
                }
                alphaMask.setPixels(pixels, 0, patchW, 0, 0, patchW, patchH)
                patchCanvas.drawBitmap(alphaMask, 0f, 0f, clearPaint)
                alphaMask.recycle()
            }

            canvas.drawBitmap(patchBmp, fillRect.left.toFloat(), fillRect.top.toFloat(), null)
            patchBmp.recycle()
        }

        // Reconstruct crossing grid/notebook lines across the entire fillRect span
        for (line in horizontalLines) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = line.color
                strokeWidth = line.thickness.toFloat().coerceAtLeast(1f)
                style = Paint.Style.STROKE
            }
            canvas.drawLine(
                fillRect.left.toFloat() - 1f, line.coord.toFloat(),
                fillRect.right.toFloat() + 1f, line.coord.toFloat(),
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
                line.coord.toFloat(), fillRect.top.toFloat() - 2f,
                line.coord.toFloat(), fillRect.bottom.toFloat() + 2f,
                linePaint
            )
        }

        outputBitmap
    }

    private fun inpaintWithOpenCv(
        source: Bitmap,
        target: Rect,
        padding: Int = 18
    ): Bitmap? {
        val width = source.width
        val height = source.height
        val dynamicPadding = (target.height() * 0.45f).toInt().coerceIn(16, 44)
        val pad = max(padding, dynamicPadding)
        val cropLeft = max(0, target.left - pad)
        val cropTop = max(0, target.top - pad)
        val cropRight = min(width, target.right + pad)
        val cropBottom = min(height, target.bottom + pad)
        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop
        if (cropW <= 4 || cropH <= 4) return null

        val cropBitmap = Bitmap.createBitmap(source, cropLeft, cropTop, cropW, cropH)
        val srcMat = Mat()
        val rgbMat = Mat()
        val grayMat = Mat()
        val inpaintMask = Mat()
        val teleaMat = Mat()
        val nsMat = Mat()
        val inpaintMat = Mat()
        val restoredCrop = Mat()
        val ringMat = Mat()
        val blendAlpha = Mat()

        return try {
            Utils.bitmapToMat(cropBitmap, srcMat)
            Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val paperLuma = estimateLocalPaperLuma(grayMat)

            // 1. Calculate relative target bounds inside crop
            val relTarget = Rect(
                max(0, target.left - cropLeft),
                max(0, target.top - cropTop),
                min(cropW, target.right - cropLeft),
                min(cropH, target.bottom - cropTop)
            )

            // Define complete erase rectangle with safety margin (3-7px)
            // This guarantees 100% complete eradication of old letters, ascenders, descenders, and antialiased fringes.
            val safetyPadX = (target.height() * 0.08f).toInt().coerceIn(3, 7)
            val safetyPadY = (target.height() * 0.06f).toInt().coerceIn(2, 6)
            val eraseLeft = max(1, relTarget.left - safetyPadX)
            val eraseTop = max(1, relTarget.top - safetyPadY)
            val eraseRight = min(cropW - 2, relTarget.right + safetyPadX)
            val eraseBottom = min(cropH - 2, relTarget.bottom + safetyPadY)

            // Build solid inpaint mask over the entire erase area
            Mat.zeros(cropH, cropW, CvType.CV_8UC1).copyTo(inpaintMask)
            Imgproc.rectangle(
                inpaintMask,
                org.opencv.core.Point(eraseLeft.toDouble(), eraseTop.toDouble()),
                org.opencv.core.Point(eraseRight.toDouble(), eraseBottom.toDouble()),
                Scalar(255.0),
                -1 // FILLED
            )

            // 2. Dual-Engine Navier-Stokes + Fast Marching Telea Inpainting:
            // Reconstructs clean paper background across the entire erase rectangle from perimeter paper
            val inpaintRad = (target.height() * 0.12).coerceIn(3.0, 9.0)
            Photo.inpaint(rgbMat, inpaintMask, nsMat, inpaintRad + 1.0, Photo.INPAINT_NS)
            Photo.inpaint(rgbMat, inpaintMask, teleaMat, inpaintRad, Photo.INPAINT_TELEA)
            org.opencv.core.Core.addWeighted(nsMat, 0.60, teleaMat, 0.40, 0.0, inpaintMat)

            // 3. Inject authentic matching local micro paper noise
            val ringKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(13.0, 13.0))
            Imgproc.dilate(inpaintMask, ringMat, ringKernel)
            ringKernel.release()
            org.opencv.core.Core.subtract(ringMat, inpaintMask, ringMat)

            val meanMat = org.opencv.core.MatOfDouble()
            val stddevMat = org.opencv.core.MatOfDouble()
            org.opencv.core.Core.meanStdDev(rgbMat, meanMat, stddevMat, ringMat)
            val stdArr = stddevMat.toArray()
            val grainSigma = if (stdArr.isNotEmpty()) stdArr[0].coerceIn(0.5, 6.0) else 1.5
            meanMat.release()
            stddevMat.release()

            if (grainSigma > 1.0) {
                val floatDst = Mat()
                inpaintMat.convertTo(floatDst, CvType.CV_32FC3)
                val noiseMat = Mat(inpaintMat.size(), CvType.CV_32FC3)
                org.opencv.core.Core.randn(noiseMat, 0.0, grainSigma * 0.50)
                val floatMask = Mat()
                inpaintMask.convertTo(floatMask, CvType.CV_32FC1, 1.0 / 255.0)
                val chs = mutableListOf<Mat>()
                org.opencv.core.Core.split(noiseMat, chs)
                for (ch in chs) org.opencv.core.Core.multiply(ch, floatMask, ch)
                org.opencv.core.Core.merge(chs, noiseMat)
                chs.forEach { it.release() }
                floatMask.release()
                org.opencv.core.Core.add(floatDst, noiseMat, floatDst)
                noiseMat.release()
                floatDst.convertTo(inpaintMat, CvType.CV_8UC3)
                floatDst.release()
            }

            // 4. Seamless Boundary Feathering:
            // Inside relTarget: 100% Opaque (Alpha = 255) so the old text is completely obliterated!
            // Outside relTarget up to eraseRect margin: Smooth 3-5px Gaussian falloff into genuine paper.
            Mat.zeros(cropH, cropW, CvType.CV_8UC1).copyTo(blendAlpha)
            Imgproc.rectangle(
                blendAlpha,
                org.opencv.core.Point(eraseLeft.toDouble(), eraseTop.toDouble()),
                org.opencv.core.Point(eraseRight.toDouble(), eraseBottom.toDouble()),
                Scalar(255.0),
                -1
            )
            Imgproc.GaussianBlur(blendAlpha, blendAlpha, Size(7.0, 7.0), 1.8)

            // Ensure inner target box is 100% solid 255 (zero bleed-through of underlying text)
            if (relTarget.bottom > relTarget.top && relTarget.right > relTarget.left) {
                val innerSub = blendAlpha.submat(relTarget.top, relTarget.bottom, relTarget.left, relTarget.right)
                innerSub.setTo(Scalar(255.0))
                innerSub.release()
            }

            // Compose RGBA with inpaintMat (RGB) and blendAlpha (Alpha)
            val rgbChs = mutableListOf<Mat>()
            org.opencv.core.Core.split(inpaintMat, rgbChs)
            rgbChs.add(blendAlpha)
            org.opencv.core.Core.merge(rgbChs, restoredCrop)
            rgbChs.forEach { it.release() }

            val outCropBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(restoredCrop, outCropBitmap)

            val output = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(output)
            canvas.drawBitmap(outCropBitmap, cropLeft.toFloat(), cropTop.toFloat(), null)

            // Reconstruct crossing grid/notebook lines
            val gridResult = detectSurroundingAndCrossingLines(source, target, paperLuma.toInt())
            for (line in gridResult.horizontalLines) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = line.color
                    strokeWidth = line.thickness.toFloat().coerceAtLeast(1f)
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(
                    cropLeft.toFloat() - 1f, line.coord.toFloat(),
                    cropRight.toFloat() + 1f, line.coord.toFloat(),
                    p
                )
            }
            for (line in gridResult.verticalLines) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = line.color
                    strokeWidth = line.thickness.toFloat().coerceAtLeast(1f)
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(
                    line.coord.toFloat(), cropTop.toFloat() - 1f,
                    line.coord.toFloat(), cropBottom.toFloat() + 1f,
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
            inpaintMask.release()
            teleaMat.release()
            nsMat.release()
            inpaintMat.release()
            restoredCrop.release()
            ringMat.release()
            blendAlpha.release()
        }
    }

    /**
     * Pro Inpainting for Whiteout Eraser Brush / Circular Drag.
     * Replaces circular patch with surrounding paper texture, scanner lighting gradient,
     * and watermark continuity via Navier-Stokes inpainting.
     */
    suspend fun inpaintCircle(
        sourceBitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        radius: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val padding = (radius * 1.5f + 16f).toInt()
        val cropL = (centerX - radius - padding).toInt().coerceIn(0, width - 1)
        val cropT = (centerY - radius - padding).toInt().coerceIn(0, height - 1)
        val cropR = (centerX + radius + padding).toInt().coerceIn(cropL + 1, width)
        val cropB = (centerY + radius + padding).toInt().coerceIn(cropT + 1, height)
        val cropW = max(2, cropR - cropL)
        val cropH = max(2, cropB - cropT)

        val cropBitmap = Bitmap.createBitmap(sourceBitmap, cropL, cropT, cropW, cropH)
        val srcMat = Mat()
        val rgbMat = Mat()
        val maskMat = Mat.zeros(cropH, cropW, CvType.CV_8UC1)
        val teleaMat = Mat()
        val nsMat = Mat()
        val inpaintMat = Mat()
        val restoredCrop = Mat()

        try {
            Utils.bitmapToMat(cropBitmap, srcMat)
            Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

            val localCenter = org.opencv.core.Point((centerX - cropL).toDouble(), (centerY - cropT).toDouble())
            Imgproc.circle(maskMat, localCenter, radius.toInt(), Scalar(255.0), -1)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.dilate(maskMat, maskMat, kernel)
            kernel.release()

            val inpaintRad = (radius * 0.35).toDouble().coerceIn(3.0, 14.0)
            Photo.inpaint(rgbMat, maskMat, nsMat, inpaintRad + 2.0, Photo.INPAINT_NS)
            Photo.inpaint(rgbMat, maskMat, teleaMat, inpaintRad, Photo.INPAINT_TELEA)
            org.opencv.core.Core.addWeighted(nsMat, 0.70, teleaMat, 0.30, 0.0, inpaintMat)

            val ringMat = Mat()
            val ringKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
            Imgproc.dilate(maskMat, ringMat, ringKernel)
            ringKernel.release()
            org.opencv.core.Core.subtract(ringMat, maskMat, ringMat)

            val meanMat = org.opencv.core.MatOfDouble()
            val stddevMat = org.opencv.core.MatOfDouble()
            org.opencv.core.Core.meanStdDev(rgbMat, meanMat, stddevMat, ringMat)
            ringMat.release()
            val stdArr = stddevMat.toArray()
            val grainSigma = if (stdArr.isNotEmpty()) stdArr[0].coerceIn(0.5, 6.0) else 1.5
            meanMat.release()
            stddevMat.release()

            if (grainSigma > 1.2) {
                val floatDst = Mat()
                inpaintMat.convertTo(floatDst, CvType.CV_32FC3)
                val noiseMat = Mat(inpaintMat.size(), CvType.CV_32FC3)
                org.opencv.core.Core.randn(noiseMat, 0.0, grainSigma * 0.55)
                val floatMask = Mat()
                maskMat.convertTo(floatMask, CvType.CV_32FC1, 1.0 / 255.0)
                val chs = mutableListOf<Mat>()
                org.opencv.core.Core.split(noiseMat, chs)
                for (ch in chs) org.opencv.core.Core.multiply(ch, floatMask, ch)
                org.opencv.core.Core.merge(chs, noiseMat)
                chs.forEach { it.release() }
                floatMask.release()
                org.opencv.core.Core.add(floatDst, noiseMat, floatDst)
                noiseMat.release()
                floatDst.convertTo(inpaintMat, CvType.CV_8UC3)
                floatDst.release()
            }

            val featheredMask = Mat()
            Imgproc.GaussianBlur(maskMat, featheredMask, Size(5.0, 5.0), 1.2)

            val floatInpaint = Mat()
            val floatRgb = Mat()
            val floatAlpha = Mat()
            val invAlpha = Mat()
            val alpha3 = Mat()
            val ones = Mat(maskMat.size(), CvType.CV_32FC3, Scalar(1.0, 1.0, 1.0))

            inpaintMat.convertTo(floatInpaint, CvType.CV_32FC3)
            rgbMat.convertTo(floatRgb, CvType.CV_32FC3)
            featheredMask.convertTo(floatAlpha, CvType.CV_32FC1, 1.0 / 255.0)

            val chList = listOf(floatAlpha, floatAlpha, floatAlpha)
            org.opencv.core.Core.merge(chList, alpha3)
            org.opencv.core.Core.subtract(ones, alpha3, invAlpha)

            val part1 = Mat()
            val part2 = Mat()
            org.opencv.core.Core.multiply(floatInpaint, alpha3, part1)
            org.opencv.core.Core.multiply(floatRgb, invAlpha, part2)
            org.opencv.core.Core.add(part1, part2, floatInpaint)

            floatInpaint.convertTo(inpaintMat, CvType.CV_8UC3)

            floatInpaint.release()
            floatRgb.release()
            floatAlpha.release()
            invAlpha.release()
            alpha3.release()
            ones.release()
            part1.release()
            part2.release()
            featheredMask.release()

            Imgproc.cvtColor(inpaintMat, restoredCrop, Imgproc.COLOR_RGB2RGBA)
            val outCropBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(restoredCrop, outCropBitmap)

            val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(outputBitmap)
            canvas.drawBitmap(outCropBitmap, cropL.toFloat(), cropT.toFloat(), null)
            outCropBitmap.recycle()
            outputBitmap
        } catch (_: Throwable) {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        } finally {
            cropBitmap.recycle()
            srcMat.release()
            rgbMat.release()
            maskMat.release()
            teleaMat.release()
            nsMat.release()
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
        margin: Int = 10,
        topLimitY: Int = 0,
        botLimitY: Int = source.height - 1,
        leftLimitX: Int = 0,
        rightLimitX: Int = source.width - 1
    ): PaperSampleResult {
        val width = source.width
        val height = source.height

        // Determine if local region is light or dark document
        val localMargin = (target.height() * 1.5f).toInt().coerceIn(16, 60)
        val lLeft = max(leftLimitX, target.left - localMargin)
        val lTop = max(topLimitY, target.top - localMargin)
        val lRight = min(rightLimitX, target.right + localMargin)
        val lBottom = min(botLimitY, target.bottom + localMargin)
        val localW = max(1, lRight - lLeft)
        val localH = max(1, lBottom - lTop)

        var sumLocalLuma = 0.0
        var localPixelCount = 0
        val step = max(1, min(localW, localH) / 10)
        for (y in lTop until lBottom step step) {
            for (x in lLeft until lRight step step) {
                if (x in 0 until width && y in 0 until height) {
                    val p = source.getPixel(x, y)
                    sumLocalLuma += getLuminance(p)
                    localPixelCount++
                }
            }
        }
        val isDarkDoc = if (localPixelCount > 0) (sumLocalLuma / localPixelCount) < 128 else false

        // 1. Sample clean paper strip ABOVE (within cell boundary topLimitY)
        val topY1 = max(topLimitY, target.top - 16)
        val topY2 = max(topLimitY, target.top - 2)
        val topSamples = mutableListOf<Int>()
        if (topY1 < topY2) {
            val stepX = max(1, target.width() / 25)
            for (y in topY1 until topY2) {
                for (x in target.left until target.right step stepX) {
                    if (x in 0 until width && y in 0 until height) {
                        topSamples.add(source.getPixel(x, y))
                    }
                }
            }
        }

        // 2. Sample clean paper strip BELOW (within cell boundary botLimitY)
        val botY1 = min(botLimitY, target.bottom + 2)
        val botY2 = min(botLimitY, target.bottom + 16)
        val botSamples = mutableListOf<Int>()
        if (botY1 < botY2) {
            val stepX = max(1, target.width() / 25)
            for (y in botY1 until botY2) {
                for (x in target.left until target.right step stepX) {
                    if (x in 0 until width && y in 0 until height) {
                        botSamples.add(source.getPixel(x, y))
                    }
                }
            }
        }

        // 3. Sample left and right margins (within cell boundaries)
        val sideSamples = mutableListOf<Int>()
        val leftX1 = max(leftLimitX, target.left - 8)
        val leftX2 = max(leftLimitX, target.left - 2)
        val rightX1 = min(rightLimitX, target.right + 2)
        val rightX2 = min(rightLimitX, target.right + 8)
        val stepY = max(1, target.height() / 10)
        for (y in target.top until target.bottom step stepY) {
            if (y in 0 until height) {
                if (leftX1 < leftX2) {
                    for (x in leftX1 until leftX2) {
                        if (x in 0 until width) sideSamples.add(source.getPixel(x, y))
                    }
                }
                if (rightX1 < rightX2) {
                    for (x in rightX1 until rightX2) {
                        if (x in 0 until width) sideSamples.add(source.getPixel(x, y))
                    }
                }
            }
        }

        val cleanTop = filterPaperPixels(topSamples, isDarkDoc)
        val cleanBot = filterPaperPixels(botSamples, isDarkDoc)
        val cleanSides = filterPaperPixels(sideSamples, isDarkDoc)

        val baseColor = when {
            cleanTop != null && cleanBot != null -> blendColors(cleanTop, cleanBot, 0.5f)
            cleanTop != null -> cleanTop
            cleanBot != null -> cleanBot
            cleanSides != null -> cleanSides
            else -> if (isDarkDoc) Color.BLACK else Color.WHITE
        }

        val topColor = cleanTop ?: baseColor
        val bottomColor = cleanBot ?: baseColor
        val topLuma = getLuminance(topColor)
        val botLuma = getLuminance(bottomColor)

        // For standard digital documents with pure white paper (Luma >= 248), snap to clean white.
        // Never snap to white if paper has authentic gray shading (Luma < 248).
        val finalTop = if (!isDarkDoc && topLuma >= 248) Color.WHITE else topColor
        val finalBot = if (!isDarkDoc && botLuma >= 248) Color.WHITE else bottomColor

        return PaperSampleResult(
            topColor = finalTop,
            bottomColor = finalBot,
            hasNoise = !isDarkDoc && (topLuma in 130..247 || botLuma in 130..247)
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

    private fun filterPaperPixels(samples: List<Int>, isDarkDoc: Boolean = false): Int? {
        if (samples.isEmpty()) return null

        val sorted = samples.map { c ->
            Pair(c, getLuminance(c))
        }.sortedBy { it.second }

        // On normal light documents, paper is the brightest 40% pixels (filtering out dark text & borders).
        // On dark mode documents, paper is the darkest 40% pixels.
        val validSamples = if (!isDarkDoc) {
            val cutoff = (sorted.size * 0.60f).toInt().coerceIn(0, sorted.size - 1)
            sorted.subList(cutoff, sorted.size).map { it.first }
        } else {
            val cutoff = (sorted.size * 0.40f).toInt().coerceIn(1, sorted.size)
            sorted.subList(0, cutoff).map { it.first }
        }

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

    private data class GridLineDetectionResult(
        val horizontalLines: List<CrossingLine>,
        val verticalLines: List<CrossingLine>,
        val topBorderY: Int?,
        val botBorderY: Int?,
        val leftBorderX: Int?,
        val rightBorderX: Int?
    )

    private fun detectSurroundingAndCrossingLines(
        source: Bitmap,
        target: Rect,
        paperLuma: Int
    ): GridLineDetectionResult {
        val width = source.width
        val height = source.height

        val scanMargin = 20
        val scanLeft = max(0, target.left - scanMargin)
        val scanRight = min(width - 1, target.right + scanMargin)
        val scanTop = max(0, target.top - scanMargin)
        val scanBottom = min(height - 1, target.bottom + scanMargin)

        val vLines = mutableListOf<CrossingLine>()
        var inVLine = false
        var vLineStartX = 0
        val vLineColors = mutableListOf<Int>()

        // Check vertical lines across scanLeft..scanRight
        val checkTopY1 = max(0, target.top - 14)
        val checkTopY2 = max(0, target.top - 2)
        val checkBotY1 = min(height - 1, target.bottom + 2)
        val checkBotY2 = min(height - 1, target.bottom + 14)

        for (x in scanLeft..scanRight) {
            var topDark = false
            var botDark = false
            var colTopLuma = 255
            var colBotLuma = 255
            var topPix = 0
            var botPix = 0

            if (checkTopY1 < checkTopY2) {
                var sum = 0
                var cnt = 0
                for (y in checkTopY1 until checkTopY2) {
                    val p = source.getPixel(x, y)
                    sum += getLuminance(p)
                    cnt++
                    topPix = p
                }
                if (cnt > 0) {
                    colTopLuma = sum / cnt
                    topDark = (paperLuma - colTopLuma) >= 24
                }
            }

            if (checkBotY1 < checkBotY2) {
                var sum = 0
                var cnt = 0
                for (y in checkBotY1 until checkBotY2) {
                    val p = source.getPixel(x, y)
                    sum += getLuminance(p)
                    cnt++
                    botPix = p
                }
                if (cnt > 0) {
                    colBotLuma = sum / cnt
                    botDark = (paperLuma - colBotLuma) >= 24
                }
            }

            // A vertical grid line must be dark above AND below the target text box
            val isGridCol = topDark && botDark && kotlin.math.abs(colTopLuma - colBotLuma) < 45

            if (isGridCol) {
                if (!inVLine) {
                    inVLine = true
                    vLineStartX = x
                    vLineColors.clear()
                }
                vLineColors.add(topPix)
                vLineColors.add(botPix)
            } else {
                if (inVLine) {
                    val thickness = x - vLineStartX
                    if (thickness in 1..8) {
                        val avgColor = averageColor(vLineColors)
                        vLines.add(CrossingLine(vLineStartX + thickness / 2, thickness, avgColor))
                    }
                    inVLine = false
                }
            }
        }
        if (inVLine) {
            val thickness = scanRight + 1 - vLineStartX
            if (thickness in 1..8) {
                val avgColor = averageColor(vLineColors)
                vLines.add(CrossingLine(vLineStartX + thickness / 2, thickness, avgColor))
            }
        }

        // Horizontal lines across scanTop..scanBottom
        val hLines = mutableListOf<CrossingLine>()
        var inHLine = false
        var hLineStartY = 0
        val hLineColors = mutableListOf<Int>()

        val checkLeftX1 = max(0, target.left - 24)
        val checkLeftX2 = max(0, target.left - 4)
        val checkRightX1 = min(width - 1, target.right + 4)
        val checkRightX2 = min(width - 1, target.right + 24)

        for (y in scanTop..scanBottom) {
            var leftDark = false
            var rightDark = false
            var rowLeftLuma = 255
            var rowRightLuma = 255
            var leftPix = 0
            var rightPix = 0

            if (checkLeftX1 < checkLeftX2) {
                var sum = 0
                var cnt = 0
                for (x in checkLeftX1 until checkLeftX2) {
                    val p = source.getPixel(x, y)
                    sum += getLuminance(p)
                    cnt++
                    leftPix = p
                }
                if (cnt > 0) {
                    rowLeftLuma = sum / cnt
                    leftDark = (paperLuma - rowLeftLuma) >= 24
                }
            }

            if (checkRightX1 < checkRightX2) {
                var sum = 0
                var cnt = 0
                for (x in checkRightX1 until checkRightX2) {
                    val p = source.getPixel(x, y)
                    sum += getLuminance(p)
                    cnt++
                    rightPix = p
                }
                if (cnt > 0) {
                    rowRightLuma = sum / cnt
                    rightDark = (paperLuma - rowRightLuma) >= 24
                }
            }

            val isGridRow = leftDark && rightDark && kotlin.math.abs(rowLeftLuma - rowRightLuma) < 45

            if (isGridRow) {
                if (!inHLine) {
                    inHLine = true
                    hLineStartY = y
                    hLineColors.clear()
                }
                hLineColors.add(leftPix)
                hLineColors.add(rightPix)
            } else {
                if (inHLine) {
                    val thickness = y - hLineStartY
                    if (thickness in 1..8) {
                        val avgColor = averageColor(hLineColors)
                        hLines.add(CrossingLine(hLineStartY + thickness / 2, thickness, avgColor))
                    }
                    inHLine = false
                }
            }
        }
        if (inHLine) {
            val thickness = scanBottom + 1 - hLineStartY
            if (thickness in 1..8) {
                val avgColor = averageColor(hLineColors)
                hLines.add(CrossingLine(hLineStartY + thickness / 2, thickness, avgColor))
            }
        }

        // Identify closest cell borders
        val leftBorderX = vLines.filter { it.coord <= target.left + 2 }.maxByOrNull { it.coord }?.coord
        val rightBorderX = vLines.filter { it.coord >= target.right - 2 }.minByOrNull { it.coord }?.coord
        val topBorderY = hLines.filter { it.coord <= target.top + 2 }.maxByOrNull { it.coord }?.coord
        val botBorderY = hLines.filter { it.coord >= target.bottom - 2 }.minByOrNull { it.coord }?.coord

        return GridLineDetectionResult(
            horizontalLines = hLines,
            verticalLines = vLines,
            topBorderY = topBorderY,
            botBorderY = botBorderY,
            leftBorderX = leftBorderX,
            rightBorderX = rightBorderX
        )
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
