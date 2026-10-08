package com.docu.editor.core.tools

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Enterprise Portrait & Passport Photo Studio Enhancement Engine.
 * 
 * Provides:
 * - High-precision Unsharp Masking for crisp eyes, facial features, hair, and collars.
 * - LAB-space CLAHE (Contrast Limited Adaptive Histogram Equalization) for balanced lighting without color distortion.
 * - Bilateral edge-preserving skin softening to remove room shadows and camera sensor grain.
 */
object PassportEnhanceEngine {

    enum class StudioSharpness(val displayName: String, val description: String) {
        ORIGINAL("Normal", "Original sharpness & lighting"),
        NATURAL("Natural Clean", "Balanced lighting & gentle clarity"),
        STUDIO_PRO("Studio Pro", "Studio lighting, smooth skin & crisp eyes"),
        ULTRA_CRISP("Ultra Sharp", "Maximum high-frequency detail for lab print")
    }

    suspend fun enhancePortrait(
        source: Bitmap,
        sharpness: StudioSharpness = StudioSharpness.STUDIO_PRO
    ): Bitmap = withContext(Dispatchers.Default) {
        if (sharpness == StudioSharpness.ORIGINAL) {
            return@withContext source.copy(Bitmap.Config.ARGB_8888, true)
        }

        try {
            val mat = Mat()
            Utils.bitmapToMat(source, mat)

            val rgb = Mat()
            Imgproc.cvtColor(mat, rgb, Imgproc.COLOR_RGBA2RGB)

            // 1. Bilateral filter for edge-preserving skin tone smoothing
            val smoothed = Mat()
            val (diameter, sigmaColor, sigmaSpace) = when (sharpness) {
                StudioSharpness.NATURAL -> Triple(5, 45.0, 45.0)
                StudioSharpness.STUDIO_PRO -> Triple(7, 60.0, 60.0)
                StudioSharpness.ULTRA_CRISP -> Triple(5, 35.0, 35.0)
                StudioSharpness.ORIGINAL -> Triple(0, 0.0, 0.0)
            }
            Imgproc.bilateralFilter(rgb, smoothed, diameter, sigmaColor, sigmaSpace)

            // 2. Lighting Balance in LAB Color Space (CLAHE on L-channel)
            val lab = Mat()
            Imgproc.cvtColor(smoothed, lab, Imgproc.COLOR_RGB2Lab)
            val channels = mutableListOf<Mat>()
            Core.split(lab, channels)

            val clipLimit = when (sharpness) {
                StudioSharpness.NATURAL -> 1.8
                StudioSharpness.STUDIO_PRO -> 2.4
                StudioSharpness.ULTRA_CRISP -> 2.8
                StudioSharpness.ORIGINAL -> 1.0
            }
            val clahe = Imgproc.createCLAHE(clipLimit, Size(8.0, 8.0))
            val lEnhanced = Mat()
            clahe.apply(channels[0], lEnhanced)
            channels[0] = lEnhanced

            Core.merge(channels, lab)
            val balancedRgb = Mat()
            Imgproc.cvtColor(lab, balancedRgb, Imgproc.COLOR_Lab2RGB)

            // 3. High-Pass Unsharp Mask for Crisp Eyes, Eyebrows & Hair
            val blurred = Mat()
            val blurSigma = when (sharpness) {
                StudioSharpness.NATURAL -> 1.8
                StudioSharpness.STUDIO_PRO -> 2.5
                StudioSharpness.ULTRA_CRISP -> 3.0
                StudioSharpness.ORIGINAL -> 0.0
            }
            Imgproc.GaussianBlur(balancedRgb, blurred, Size(0.0, 0.0), blurSigma)

            val (weightOriginal, weightBlurred) = when (sharpness) {
                StudioSharpness.NATURAL -> Pair(1.30, -0.30)
                StudioSharpness.STUDIO_PRO -> Pair(1.50, -0.50)
                StudioSharpness.ULTRA_CRISP -> Pair(1.75, -0.75)
                StudioSharpness.ORIGINAL -> Pair(1.0, 0.0)
            }
            val sharpened = Mat()
            Core.addWeighted(balancedRgb, weightOriginal, blurred, weightBlurred, 0.0, sharpened)

            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val outRgba = Mat()
            Imgproc.cvtColor(sharpened, outRgba, Imgproc.COLOR_RGB2RGBA)
            Utils.matToBitmap(outRgba, output)

            // Cleanup native OpenCV matrices
            mat.release()
            rgb.release()
            smoothed.release()
            lab.release()
            channels.forEach { it.release() }
            lEnhanced.release()
            balancedRgb.release()
            blurred.release()
            sharpened.release()
            outRgba.release()

            output
        } catch (_: Throwable) {
            // Safe fallback if OpenCV encounters memory or format issue
            source.copy(Bitmap.Config.ARGB_8888, true)
        }
    }
}
