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
        CLEAN_BW,         // Crisp high-contrast black & white fax mode
        GRAYSCALE         // Smooth leveled gray tone
    }

    suspend fun applyFilter(bitmap: Bitmap, filter: FilterType): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            FilterType.ORIGINAL -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
            FilterType.MAGIC_COLOR -> applyMagicColor(bitmap)
            FilterType.REMOVE_SHADOWS -> applyShadowRemoval(bitmap)
            FilterType.CLEAN_BW -> applyCleanBw(bitmap)
            FilterType.GRAYSCALE -> applyEnhancedGrayscale(bitmap)
        }
    }

    /**
     * CamScanner "Magic Color": CLAHE on Luma + subtle unsharp mask sharpening + saturation boost.
     */
    private fun applyMagicColor(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val labMat = Mat()
        val channels = mutableListOf<Mat>()
        val lumaClahe = Mat()
        val sharpenedRgb = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(srcRgb, labMat, Imgproc.COLOR_RGB2Lab)

            Core.split(labMat, channels)
            val lChannel = channels[0]

            // Contrast Limited Adaptive Histogram Equalization on L channel
            val clahe = Imgproc.createCLAHE(2.4, Size(8.0, 8.0))
            clahe.apply(lChannel, lumaClahe)
            clahe.collectGarbage()

            lumaClahe.copyTo(channels[0])
            Core.merge(channels, labMat)
            Imgproc.cvtColor(labMat, srcRgb, Imgproc.COLOR_Lab2RGB)

            // Unsharp Masking: Text = 1.3 * Original - 0.3 * GaussianBlur
            val blurred = Mat()
            Imgproc.GaussianBlur(srcRgb, blurred, Size(3.0, 3.0), 0.0)
            Core.addWeighted(srcRgb, 1.35, blurred, -0.35, 0.0, sharpenedRgb)
            blurred.release()

            Imgproc.cvtColor(sharpenedRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            labMat.release()
            channels.forEach { it.release() }
            lumaClahe.release()
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

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(21.0, 21.0))

            for (ch in channels) {
                val backgroundIllumination = Mat()
                val diffFloat = Mat()
                val chFloat = Mat()
                val bgFloat = Mat()
                val normMat = Mat()

                // Morphological closing approximates background light distribution
                Imgproc.morphologyEx(ch, backgroundIllumination, Imgproc.MORPH_CLOSE, kernel)

                ch.convertTo(chFloat, CvType.CV_32F)
                backgroundIllumination.convertTo(bgFloat, CvType.CV_32F)

                // Divide channel by background illumination
                Core.divide(chFloat, bgFloat, diffFloat)
                Core.multiply(diffFloat, Scalar(255.0), diffFloat)

                diffFloat.convertTo(normMat, CvType.CV_8U)
                resultChannels.add(normMat)

                backgroundIllumination.release()
                diffFloat.release()
                chFloat.release()
                bgFloat.release()
            }
            kernel.release()

            val mergedRgb = Mat()
            Core.merge(resultChannels, mergedRgb)
            Imgproc.cvtColor(mergedRgb, resultRgba, Imgproc.COLOR_RGB2RGBA)

            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            mergedRgb.release()
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
     */
    private fun applyCleanBw(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val gray = Mat()
        val bwMat = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)

            // Adaptive Gaussian Thresholding produces clean documents without dark patches
            Imgproc.adaptiveThreshold(
                gray,
                bwMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                15,
                11.0
            )

            Imgproc.cvtColor(bwMat, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            gray.release()
            bwMat.release()
            resultRgba.release()
        }
    }

    private fun applyEnhancedGrayscale(source: Bitmap): Bitmap {
        val srcRgba = Mat()
        val gray = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.equalizeHist(gray, gray)
            Imgproc.cvtColor(gray, resultRgba, Imgproc.COLOR_GRAY2RGBA)

            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            gray.release()
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
