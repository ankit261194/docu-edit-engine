package com.docu.editor.core.scanner

import android.graphics.Bitmap
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
        GRAYSCALE         // Smooth leveled gray tone
    }

    suspend fun applyFilter(bitmap: Bitmap, filter: FilterType): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            FilterType.ORIGINAL -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
            FilterType.MAGIC_COLOR -> applyMagicColor(bitmap)
            FilterType.REMOVE_SHADOWS -> applyShadowRemoval(bitmap)
            FilterType.REMOVE_WATERMARK -> applyWatermarkRemoval(bitmap)
            FilterType.REMOVE_FINGERS -> FingerRemovalEngine.removeFingers(bitmap)
            FilterType.DEWARP_CURVED_PAGE -> BookDewarpEngine.dewarpPage(bitmap)
            FilterType.CLEAN_BW -> applyCleanBw(bitmap)
            FilterType.GRAYSCALE -> applyEnhancedGrayscale(bitmap)
        }
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
}
