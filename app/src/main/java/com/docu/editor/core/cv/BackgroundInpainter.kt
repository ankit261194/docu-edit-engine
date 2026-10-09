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
        val sampledColors = samplePerimeterPaperColors(sourceBitmap, safeTarget, sampleMargin)
        val paperLuma = getLuminance(sampledColors.topColor)

        // Detect any crossing table grid lines or notebook ruled lines
        val horizontalLines = detectHorizontalCrossingLines(sourceBitmap, safeTarget, paperLuma)
        val verticalLines = detectVerticalCrossingLines(sourceBitmap, safeTarget, paperLuma)

        // Create clean output bitmap
        val outputBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)

        // Soft-feathered ambient paper patch (zero hard rectangular boundaries!)
        val extraPadX = (safeTarget.height() * 0.12f).toInt().coerceIn(3, 8)
        val extraPadY = (safeTarget.height() * 0.10f).toInt().coerceIn(3, 8)
        val fillRect = Rect(
            max(0, safeTarget.left - extraPadX),
            max(0, safeTarget.top - extraPadY),
            min(width, safeTarget.right + extraPadX),
            min(height, safeTarget.bottom + extraPadY)
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

            // Feather outer edges (smooth 4-6px alpha falloff into genuine paper)
            val featherDist = minOf(6, patchW / 4, patchH / 4)
            if (featherDist > 1) {
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                }
                val alphaMask = Bitmap.createBitmap(patchW, patchH, Bitmap.Config.ARGB_8888)
                val maskCanvas = Canvas(alphaMask)
                maskCanvas.drawColor(Color.WHITE)
                for (f in 0 until featherDist) {
                    val alphaPercent = (f.toFloat() / featherDist)
                    val borderPaint = Paint().apply {
                        color = Color.argb(((1f - alphaPercent) * 255).toInt(), 0, 0, 0)
                        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    maskCanvas.drawRect(f.toFloat(), f.toFloat(), (patchW - 1 - f).toFloat(), (patchH - 1 - f).toFloat(), borderPaint)
                }
                patchCanvas.drawBitmap(alphaMask, 0f, 0f, clearPaint)
                alphaMask.recycle()
            }

            canvas.drawBitmap(patchBmp, fillRect.left.toFloat(), fillRect.top.toFloat(), null)
            patchBmp.recycle()
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
        margin: Int
    ): PaperSampleResult {
        val width = source.width
        val height = source.height

        // Determine if local region is light or dark document
        val localMargin = (target.height() * 1.5f).toInt().coerceIn(16, 60)
        val lLeft = max(0, target.left - localMargin)
        val lTop = max(0, target.top - localMargin)
        val lRight = min(width, target.right + localMargin)
        val lBottom = min(height, target.bottom + localMargin)
        val localW = lRight - lLeft
        val localH = lBottom - lTop

        var sumLocalLuma = 0.0
        var localPixelCount = 0
        val step = max(1, min(localW, localH) / 10)
        for (y in lTop until lBottom step step) {
            for (x in lLeft until lRight step step) {
                val p = source.getPixel(x, y)
                sumLocalLuma += getLuminance(p)
                localPixelCount++
            }
        }
        val isDarkDoc = if (localPixelCount > 0) (sumLocalLuma / localPixelCount) < 128 else false

        // 1. Sample clean paper strip ABOVE (between target.top - 16 and target.top - 2)
        val topY1 = max(0, target.top - 16)
        val topY2 = max(0, target.top - 2)
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

        // 2. Sample clean paper strip BELOW (between target.bottom + 2 and target.bottom + 16)
        val botY1 = min(height - 1, target.bottom + 2)
        val botY2 = min(height - 1, target.bottom + 16)
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

        // 3. Sample left and right margins (only safe 2-8px margin)
        val sideSamples = mutableListOf<Int>()
        val leftX1 = max(0, target.left - 8)
        val leftX2 = max(0, target.left - 2)
        val rightX1 = min(width - 1, target.right + 2)
        val rightX2 = min(width - 1, target.right + 8)
        val stepY = max(1, target.height() / 10)
        for (y in target.top until target.bottom step stepY) {
            if (y in 0 until height) {
                for (x in leftX1 until leftX2) sideSamples.add(source.getPixel(x, y))
                for (x in rightX1 until rightX2) sideSamples.add(source.getPixel(x, y))
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

        // For standard digital documents with bright paper (Luma >= 235), snap to pure clean white
        val finalTop = if (!isDarkDoc && topLuma >= 235) Color.WHITE else topColor
        val finalBot = if (!isDarkDoc && botLuma >= 235) Color.WHITE else bottomColor

        return PaperSampleResult(
            topColor = finalTop,
            bottomColor = finalBot,
            hasNoise = !isDarkDoc && (topLuma in 130..234 || botLuma in 130..234)
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
