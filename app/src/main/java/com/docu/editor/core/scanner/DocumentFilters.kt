package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object DocumentFilters {

    enum class FilterType {
        ORIGINAL,
        MAGIC_COLOR,      // High contrast text pop + vivid ink colors (CamScanner flagship)
        REMOVE_SHADOWS,   // Bilateral Illumination Division (erases phone flash & crease shadows)
        REMOVE_WATERMARK, // Sigmoid background subtraction: erases diagonal translucent watermarks
        REMOVE_FINGERS,   // Automated YCrCb skin segmentation & border intrusion inpainting
        DEWARP_CURVED_PAGE, // Non-linear cylindrical remap flattening curved book spine pages
        CLEAN_BW,         // Crisp high-contrast black & white fax mode
        GRAYSCALE,        // Smooth leveled gray tone
        VIVID_DOC,        // CLAHE LAB Chroma Boost (Vivid colors for certificates & brochures)
        STUDIO_WHITE,     // Studio-grade paper illumination whitening with crisp text
        BLUEPRINT,        // Engineering drawing & blueprint cyanotype inversion
        SEPIA,            // Archival warm sepia tone mapping
        INK_SHARPENER     // Laplacian Anti-Smudge stroke de-bleeding & edge sharpening
    }

    suspend fun applyFilter(bitmap: Bitmap, filter: FilterType, intensity: Float = 1.0f): Bitmap = withContext(Dispatchers.Default) {
        val filtered = when (filter) {
            FilterType.ORIGINAL -> return@withContext bitmap.copy(Bitmap.Config.ARGB_8888, true)
            FilterType.MAGIC_COLOR -> applyMagicColor(bitmap)
            FilterType.REMOVE_SHADOWS -> applyShadowRemoval(bitmap)
            FilterType.REMOVE_WATERMARK -> applyWatermarkRemoval(bitmap)
            FilterType.REMOVE_FINGERS -> FingerRemovalEngine.removeFingers(bitmap)
            FilterType.DEWARP_CURVED_PAGE -> BookDewarpEngine.dewarpPage(bitmap)
            FilterType.CLEAN_BW -> applyCleanBw(bitmap)
            FilterType.GRAYSCALE -> applyEnhancedGrayscale(bitmap)
            FilterType.VIVID_DOC -> applyVividDoc(bitmap)
            FilterType.STUDIO_WHITE -> applyStudioWhite(bitmap)
            FilterType.BLUEPRINT -> applyBlueprint(bitmap)
            FilterType.SEPIA -> applySepia(bitmap)
            FilterType.INK_SHARPENER -> applyInkSharpener(bitmap)
        }

        if (intensity >= 0.99f || filter == FilterType.DEWARP_CURVED_PAGE || filter == FilterType.REMOVE_FINGERS) {
            filtered
        } else {
            val blended = blendBitmaps(bitmap, filtered, intensity)
            if (blended != filtered && !filtered.isRecycled) {
                filtered.recycle()
            }
            blended
        }
    }

    /**
     * Studio-grade bitmap blending: Blends filtered result with original base according to intensity (0.0 to 1.0).
     */
    fun blendBitmaps(base: Bitmap, overlay: Bitmap, intensity: Float): Bitmap {
        val safeIntensity = intensity.coerceIn(0f, 1f)
        if (safeIntensity >= 0.99f) return overlay
        if (safeIntensity <= 0.01f) return base.copy(Bitmap.Config.ARGB_8888, true)

        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (safeIntensity * 255).toInt()
        }
        canvas.drawBitmap(overlay, 0f, 0f, paint)
        return result
    }

    /**
     * CamScanner Flagship "Magic Color":
     * 1. Per-channel Morphological Background Illumination Division:
     *    Erases all paper shadows, yellow room lighting, and phone flash gradients,
     *    turning the paper into 100% studio-clean white (255, 255, 255).
     * 2. Adaptive Ink S-Curve Contrast: Deepens black text characters and ink strokes.
     * 3. HSV Saturation Boost: Makes colored inks (blue pens, red seals, green signatures) pop with vivid color.
     * 4. Unsharp Masking: Razor-sharp character stroke edges without noise.
     */
    fun applyMagicColor(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val channels = mutableListOf<Mat>()
        val dividedChannels = mutableListOf<Mat>()
        val normalizedRgb = Mat()
        val contrastRgb = Mat()
        val hsvMat = Mat()
        val hsvChannels = mutableListOf<Mat>()
        val blurred = Mat()
        val sharpenedRgb = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)

            // Step 1: Per-channel illumination division
            Core.split(srcRgb, channels)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(41.0, 41.0))

            for (ch in channels) {
                val bg = Mat()
                val chFloat = Mat()
                val bgFloat = Mat()
                val divFloat = Mat()
                val divU8 = Mat()

                // Morphological close fills ink with paper color to estimate background illumination
                Imgproc.morphologyEx(ch, bg, Imgproc.MORPH_CLOSE, kernel)

                ch.convertTo(chFloat, CvType.CV_32F)
                bg.convertTo(bgFloat, CvType.CV_32F)
                bg.release()

                // Normalize: (ch / bg) * 255.0
                Core.divide(chFloat, bgFloat, divFloat, 255.0)
                chFloat.release()
                bgFloat.release()

                divFloat.convertTo(divU8, CvType.CV_8U)
                divFloat.release()

                dividedChannels.add(divU8)
            }
            kernel.release()

            Core.merge(dividedChannels, normalizedRgb)

            // Step 2: Adaptive Ink S-Curve & Studio Paper Whitening
            val lut = Mat(1, 256, CvType.CV_8U)
            val lutData = ByteArray(256)
            for (i in 0..255) {
                val v = when {
                    i >= 220 -> 255 // Pure studio white paper
                    i <= 35 -> 0    // Deep rich black text
                    else -> {
                        val norm = (i - 35).toDouble() / (220 - 35)
                        val gamma = 1.35
                        val mapped = Math.pow(norm, gamma) * 255.0
                        mapped.coerceIn(0.0, 255.0).toInt()
                    }
                }
                lutData[i] = v.toByte()
            }
            lut.put(0, 0, lutData)
            Core.LUT(normalizedRgb, lut, contrastRgb)
            lut.release()

            // Step 3: Saturation Boost in HSV for blue ballpoints & red official stamps
            Imgproc.cvtColor(contrastRgb, hsvMat, Imgproc.COLOR_RGB2HSV)
            Core.split(hsvMat, hsvChannels)
            val sChannel = hsvChannels[1]
            sChannel.convertTo(sChannel, -1, 1.30, 0.0)
            Core.merge(hsvChannels, hsvMat)
            Imgproc.cvtColor(hsvMat, contrastRgb, Imgproc.COLOR_HSV2RGB)

            // Step 4: Unsharp Masking for razor-sharp text strokes (1.30 * Img - 0.30 * Blur)
            Imgproc.GaussianBlur(contrastRgb, blurred, Size(3.0, 3.0), 0.0)
            Core.addWeighted(contrastRgb, 1.30, blurred, -0.30, 0.0, sharpenedRgb)

            Imgproc.cvtColor(sharpenedRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            channels.forEach { it.release() }
            dividedChannels.forEach { it.release() }
            normalizedRgb.release()
            contrastRgb.release()
            hsvMat.release()
            hsvChannels.forEach { it.release() }
            blurred.release()
            sharpenedRgb.release()
            resultRgba.release()
        }
    }

    /**
     * Bilateral Illumination Division: Erases crease shadows and flash gradients without fading ink.
     * Formula: Result = (Image / MorphologicalClose(Image)) * 255
     */
    private fun applyShadowRemoval(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val channels = mutableListOf<Mat>()
        val resultChannels = mutableListOf<Mat>()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Core.split(srcRgb, channels)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(35.0, 35.0))

            for (ch in channels) {
                val backgroundIllumination = Mat()
                val diffFloat = Mat()
                val chFloat = Mat()
                val bgFloat = Mat()
                val normMat = Mat()

                Imgproc.morphologyEx(ch, backgroundIllumination, Imgproc.MORPH_CLOSE, kernel)

                ch.convertTo(chFloat, CvType.CV_32F)
                backgroundIllumination.convertTo(bgFloat, CvType.CV_32F)
                backgroundIllumination.release()

                Core.divide(chFloat, bgFloat, diffFloat, 255.0)
                chFloat.release()
                bgFloat.release()

                diffFloat.convertTo(normMat, CvType.CV_8U)
                diffFloat.release()
                resultChannels.add(normMat)
            }
            kernel.release()

            val mergedRgb = Mat()
            Core.merge(resultChannels, mergedRgb)
            Imgproc.cvtColor(mergedRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            mergedRgb.release()

            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            channels.forEach { it.release() }
            resultChannels.forEach { it.release() }
            resultRgba.release()
        }
    }

    /**
     * Clean B&W / Fax Mode: Crisp text with pure white paper background.
     * Uses Illumination Division first to eradicate all desk/paper shadows,
     * followed by Otsu thresholding for 100% noise-free, crisp text.
     */
    private fun applyCleanBw(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val gray = Mat()
        val bg = Mat()
        val grayFloat = Mat()
        val bgFloat = Mat()
        val divFloat = Mat()
        val norm = Mat()
        val bwMat = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(41.0, 41.0))
            Imgproc.morphologyEx(gray, bg, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            gray.convertTo(grayFloat, CvType.CV_32F)
            bg.convertTo(bgFloat, CvType.CV_32F)
            bg.release()

            Core.divide(grayFloat, bgFloat, divFloat, 255.0)
            grayFloat.release()
            bgFloat.release()

            divFloat.convertTo(norm, CvType.CV_8U)
            divFloat.release()

            // Otsu threshold on shadow-normalized surface gives pure black text on pure white paper
            Imgproc.threshold(norm, bwMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

            Imgproc.cvtColor(bwMat, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            gray.release()
            bg.release()
            grayFloat.release()
            bgFloat.release()
            divFloat.release()
            norm.release()
            bwMat.release()
            resultRgba.release()
        }
    }

    private fun applyEnhancedGrayscale(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val gray = Mat()
        val bg = Mat()
        val grayFloat = Mat()
        val bgFloat = Mat()
        val divFloat = Mat()
        val norm = Mat()
        val cleanGray = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(41.0, 41.0))
            Imgproc.morphologyEx(gray, bg, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            gray.convertTo(grayFloat, CvType.CV_32F)
            bg.convertTo(bgFloat, CvType.CV_32F)
            bg.release()

            Core.divide(grayFloat, bgFloat, divFloat, 255.0)
            grayFloat.release()
            bgFloat.release()

            divFloat.convertTo(norm, CvType.CV_8U)
            divFloat.release()

            val lut = Mat(1, 256, CvType.CV_8U)
            val lutData = ByteArray(256)
            for (i in 0..255) {
                val v = when {
                    i >= 220 -> 255
                    i <= 30 -> 0
                    else -> {
                        val t = (i - 30).toDouble() / (220 - 30)
                        (Math.pow(t, 1.25) * 255.0).coerceIn(0.0, 255.0).toInt()
                    }
                }
                lutData[i] = v.toByte()
            }
            lut.put(0, 0, lutData)
            Core.LUT(norm, lut, cleanGray)
            lut.release()

            Imgproc.cvtColor(cleanGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            gray.release()
            bg.release()
            grayFloat.release()
            bgFloat.release()
            divFloat.release()
            norm.release()
            cleanGray.release()
            resultRgba.release()
        }
    }

    /**
     * Translucent Watermark Removal:
     * Erases light grey/colored diagonal watermarks (e.g. "CONFIDENTIAL", "SAMPLE", draft stamps)
     * while preserving high-contrast text strokes.
     */
    private fun applyWatermarkRemoval(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val gray = Mat()
        val background = Mat()
        val normalized = Mat()
        val resultRgb = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcRgb, gray, Imgproc.COLOR_RGB2GRAY)

            // 1. Estimate background paper luminance via large morphological close
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(35.0, 35.0))
            Imgproc.morphologyEx(gray, background, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            // 2. Normalize: (gray / background) * 255
            val gray32 = Mat()
            val bg32 = Mat()
            val norm32 = Mat()
            gray.convertTo(gray32, CvType.CV_32F)
            background.convertTo(bg32, CvType.CV_32F)
            Core.divide(gray32, bg32, norm32)
            Core.multiply(norm32, Scalar(255.0), norm32)
            norm32.convertTo(normalized, CvType.CV_8U)
            gray32.release()
            bg32.release()
            norm32.release()

            // 3. Sigmoid tone curve:
            // Text ink has normalized luma <= 140
            // Watermarks sit in range 150..225
            // Paper sits in range 230..255
            val lut = Mat(1, 256, CvType.CV_8U)
            val lutData = ByteArray(256)
            for (i in 0..255) {
                lutData[i] = when {
                    i <= 130 -> i.toByte() // preserve dark text ink
                    i >= 210 -> 255.toByte() // push paper & faint watermark to pure white
                    else -> {
                        val t = (i - 130).toFloat() / (210 - 130)
                        val v = (130 + t * t * (255 - 130)).coerceIn(0f, 255f).toInt()
                        v.toByte()
                    }
                }
            }
            lut.put(0, 0, lutData)
            val cleanGray = Mat()
            Core.LUT(normalized, lut, cleanGray)
            lut.release()

            // 4. Color reconstruction: Where cleanGray is near 255, make it white paper
            val mask = Mat()
            Imgproc.threshold(cleanGray, mask, 240.0, 255.0, Imgproc.THRESH_BINARY)
            val textMask = Mat()
            Core.bitwise_not(mask, textMask)

            resultRgb.create(source.height, source.width, CvType.CV_8UC3)
            resultRgb.setTo(Scalar(255.0, 255.0, 255.0))
            srcRgb.copyTo(resultRgb, textMask)

            mask.release()
            textMask.release()
            cleanGray.release()

            Imgproc.cvtColor(resultRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            gray.release()
            background.release()
            normalized.release()
            resultRgb.release()
            resultRgba.release()
        }
    }

    /**
     * Vivid Document: CLAHE luminance contrast + LAB chroma boost + unsharp mask.
     * Ideal for color certificates, brochures, and glossy forms.
     */
    fun applyVividDoc(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val labMat = Mat()
        val labChannels = mutableListOf<Mat>()
        val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
        val equalizedL = Mat()
        val boostedRgb = Mat()
        val blurred = Mat()
        val sharpenedRgb = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcRgb, labMat, Imgproc.COLOR_RGB2Lab)
            Core.split(labMat, labChannels)

            // Equalize luminance with CLAHE for crisp local text contrast
            clahe.apply(labChannels[0], equalizedL)
            equalizedL.copyTo(labChannels[0])

            // Boost chroma A & B for vivid color pop
            val aCh = labChannels[1]
            val bCh = labChannels[2]
            aCh.convertTo(aCh, -1, 1.35, -44.55)
            bCh.convertTo(bCh, -1, 1.35, -44.55)

            Core.merge(labChannels, labMat)
            Imgproc.cvtColor(labMat, boostedRgb, Imgproc.COLOR_Lab2RGB)

            // Crisp unsharp mask
            Imgproc.GaussianBlur(boostedRgb, blurred, Size(3.0, 3.0), 0.0)
            Core.addWeighted(boostedRgb, 1.25, blurred, -0.25, 0.0, sharpenedRgb)

            Imgproc.cvtColor(sharpenedRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            labMat.release()
            labChannels.forEach { it.release() }
            equalizedL.release()
            boostedRgb.release()
            blurred.release()
            sharpenedRgb.release()
            resultRgba.release()
        }
    }

    /**
     * Studio White: Per-channel background illumination division with high-key paper whitening LUT.
     * Yields pristine 255 paper background while preserving colored logos and text.
     */
    fun applyStudioWhite(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val channels = mutableListOf<Mat>()
        val dividedChannels = mutableListOf<Mat>()
        val normalizedRgb = Mat()
        val contrastRgb = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Core.split(srcRgb, channels)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(51.0, 51.0))

            for (ch in channels) {
                val bg = Mat()
                val chFloat = Mat()
                val bgFloat = Mat()
                val divFloat = Mat()
                val divU8 = Mat()

                Imgproc.morphologyEx(ch, bg, Imgproc.MORPH_CLOSE, kernel)
                ch.convertTo(chFloat, CvType.CV_32F)
                bg.convertTo(bgFloat, CvType.CV_32F)
                bg.release()

                Core.divide(chFloat, bgFloat, divFloat, 255.0)
                chFloat.release()
                bgFloat.release()

                divFloat.convertTo(divU8, CvType.CV_8U)
                divFloat.release()
                dividedChannels.add(divU8)
            }
            kernel.release()
            Core.merge(dividedChannels, normalizedRgb)

            // Ultra-clean studio white LUT: threshold background > 195 to absolute 255
            val lut = Mat(1, 256, CvType.CV_8U)
            val lutData = ByteArray(256)
            for (i in 0..255) {
                val v = when {
                    i >= 195 -> 255
                    i <= 30 -> 0
                    else -> {
                        val norm = (i - 30).toDouble() / (195 - 30)
                        (Math.pow(norm, 1.25) * 255.0).coerceIn(0.0, 255.0).toInt()
                    }
                }
                lutData[i] = v.toByte()
            }
            lut.put(0, 0, lutData)
            Core.LUT(normalizedRgb, lut, contrastRgb)
            lut.release()

            Imgproc.cvtColor(contrastRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            channels.forEach { it.release() }
            dividedChannels.forEach { it.release() }
            normalizedRgb.release()
            contrastRgb.release()
            resultRgba.release()
        }
    }

    /**
     * Blueprint: Inverted cyanotype color LUT mapping for architectural and engineering plans.
     */
    fun applyBlueprint(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val gray = Mat()
        val inverted = Mat()
        val bpRgb = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.equalizeHist(gray, gray)
            Core.bitwise_not(gray, inverted)

            // Blueprint LUT: 0 -> deep prussian navy (18, 38, 76), 255 -> crisp cyan-white (225, 245, 255)
            val lut = Mat(1, 256, CvType.CV_8UC3)
            val lutData = ByteArray(256 * 3)
            for (i in 0..255) {
                val t = i.toFloat() / 255f
                val r = (18f * (1f - t) + 225f * t).toInt().coerceIn(0, 255)
                val g = (38f * (1f - t) + 245f * t).toInt().coerceIn(0, 255)
                val b = (76f * (1f - t) + 255f * t).toInt().coerceIn(0, 255)
                lutData[i * 3 + 0] = r.toByte()
                lutData[i * 3 + 1] = g.toByte()
                lutData[i * 3 + 2] = b.toByte()
            }
            lut.put(0, 0, lutData)
            val inv3ch = Mat()
            Imgproc.cvtColor(inverted, inv3ch, Imgproc.COLOR_GRAY2RGB)
            Core.LUT(inv3ch, lut, bpRgb)
            inv3ch.release()
            lut.release()

            Imgproc.cvtColor(bpRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            gray.release()
            inverted.release()
            bpRgb.release()
            resultRgba.release()
        }
    }

    /**
     * Vintage Sepia: Mathematical 3x3 color transform for warm archival document tone.
     */
    fun applySepia(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val sepiaMat = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)

            val kernel = Mat(3, 3, CvType.CV_32F)
            kernel.put(0, 0, floatArrayOf(
                0.393f, 0.769f, 0.189f,
                0.349f, 0.686f, 0.168f,
                0.272f, 0.534f, 0.131f
            ))
            Core.transform(srcRgb, sepiaMat, kernel)
            kernel.release()

            Imgproc.cvtColor(sepiaMat, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            sepiaMat.release()
            resultRgba.release()
        }
    }

    /**
     * Ink Sharpener & Anti-Smudge: Bilateral de-bleeding + Laplacian edge high-pass filter.
     * Sharpens bleeding ink, smudged handwriting, and faint dot-matrix printouts.
     */
    fun applyInkSharpener(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val filtered = Mat()
        val laplacian = Mat()
        val sharpRgb = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)

            Imgproc.bilateralFilter(srcRgb, filtered, 7, 50.0, 50.0)

            Imgproc.Laplacian(filtered, laplacian, CvType.CV_16S, 3)
            val laplacianU8 = Mat()
            Core.convertScaleAbs(laplacian, laplacianU8)
            laplacian.release()

            Core.addWeighted(filtered, 1.0, laplacianU8, 0.35, 0.0, sharpRgb)
            laplacianU8.release()

            val lut = Mat(1, 256, CvType.CV_8U)
            val lutData = ByteArray(256)
            for (i in 0..255) {
                val v = if (i < 120) {
                    (i * 0.88).toInt().coerceIn(0, 255)
                } else if (i > 210) {
                    255
                } else {
                    i
                }
                lutData[i] = v.toByte()
            }
            lut.put(0, 0, lutData)
            val finalRgb = Mat()
            Core.LUT(sharpRgb, lut, finalRgb)
            lut.release()

            Imgproc.cvtColor(finalRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            finalRgb.release()

            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            filtered.release()
            sharpRgb.release()
            resultRgba.release()
        }
    }

    fun adjustBrightnessContrast(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        val cm = android.graphics.ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Enterprise Document Line Straightening & Deskewing.
     * Uses Canny edge detection + Probabilistic Hough Lines transform
     * to detect text baseline angles and rotates document with sub-degree precision.
     */
    fun detectAndStraightenDocument(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val grayMat = Mat()
        val edges = Mat()
        val lines = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val maxDim = 1200
            val scale = if (source.width > maxDim || source.height > maxDim) {
                maxDim.toDouble() / maxOf(source.width, source.height)
            } else 1.0

            val processGray = if (scale < 1.0) {
                val scaled = Mat()
                Imgproc.resize(grayMat, scaled, Size(source.width * scale, source.height * scale))
                scaled
            } else {
                grayMat
            }

            Imgproc.Canny(processGray, edges, 50.0, 150.0, 3, false)
            if (processGray != grayMat) {
                processGray.release()
            }

            val minLineLength = (minOf(source.width, source.height) * scale * 0.18)
            val maxLineGap = 20.0
            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180.0, 70, minLineLength, maxLineGap)

            val detectedAngles = mutableListOf<Double>()
            for (i in 0 until lines.rows()) {
                val line = lines.get(i, 0)
                val x1 = line[0]
                val y1 = line[1]
                val x2 = line[2]
                val y2 = line[3]
                val dx = x2 - x1
                val dy = y2 - y1
                val angleDeg = Math.toDegrees(Math.atan2(dy, dx))
                if (Math.abs(angleDeg) in 0.5..25.0) {
                    detectedAngles.add(angleDeg)
                } else if (Math.abs(angleDeg) in 155.0..179.5) {
                    val norm = if (angleDeg > 0) angleDeg - 180.0 else angleDeg + 180.0
                    detectedAngles.add(norm)
                }
            }

            if (detectedAngles.size < 3) {
                return source
            }

            detectedAngles.sort()
            val medianAngle = detectedAngles[detectedAngles.size / 2]

            if (Math.abs(medianAngle) < 0.4 || Math.abs(medianAngle) > 25.0) {
                return source
            }

            val center = org.opencv.core.Point(source.width / 2.0, source.height / 2.0)
            val rotMat = Imgproc.getRotationMatrix2D(center, medianAngle, 1.0)
            val rotatedMat = Mat()
            Imgproc.warpAffine(
                srcRgba,
                rotatedMat,
                rotMat,
                Size(source.width.toDouble(), source.height.toDouble()),
                Imgproc.INTER_CUBIC,
                Core.BORDER_REPLICATE
            )
            rotMat.release()

            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rotatedMat, output)
            rotatedMat.release()
            output
        } catch (_: Throwable) {
            source
        } finally {
            srcRgba.release()
            grayMat.release()
            edges.release()
            lines.release()
        }
    }
}
