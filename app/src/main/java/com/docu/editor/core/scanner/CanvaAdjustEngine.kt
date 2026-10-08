package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.docu.editor.domain.model.CanvaStyleMatchPreset
import com.docu.editor.domain.model.SelectiveColorTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Canva Pro Adjust, Color Grading & Style Match Engine.
 * Granular pro image tuning (Brightness, Contrast, Saturation, Warmth,
 * Tint, Clarity, Vignette, Blur), Interactive Tone Curves (Shadows, Midtones, Highlights),
 * Selective Color Tuning (Blue ink, Red stamps, Green seals, Paper tint), + Cinematic Look Presets.
 */
object CanvaAdjustEngine {

    suspend fun applyAdjustments(
        source: Bitmap,
        brightness: Float = 0f,      // -100 .. 100
        contrast: Float = 1.0f,      // 0.5 .. 2.0
        saturation: Float = 1.0f,    // 0.0 .. 2.5
        warmth: Float = 0f,          // -50 .. 50
        tint: Float = 0f,            // -50 .. 50
        clarity: Float = 0f,         // 0 .. 100
        vignette: Float = 0f,        // 0 .. 100
        blur: Float = 0f,            // 0 .. 50
        preset: CanvaStyleMatchPreset = CanvaStyleMatchPreset.NONE,
        shadows: Float = 0f,         // -50 .. 50 (Tone Curve: Shadows)
        midtones: Float = 0f,        // -50 .. 50 (Tone Curve: Midtones)
        highlights: Float = 0f,      // -50 .. 50 (Tone Curve: Highlights)
        colorTarget: SelectiveColorTarget = SelectiveColorTarget.ALL_MASTER,
        targetSaturation: Float = 1.0f, // 0.0 .. 2.5
        targetLuminance: Float = 1.0f   // 0.5 .. 1.8
    ): Bitmap = withContext(Dispatchers.Default) {
        var bmp = source.copy(Bitmap.Config.ARGB_8888, true)

        // 1. Color Matrix: Brightness, Contrast, Saturation, Warmth, Tint
        val cm = ColorMatrix()

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(saturation)
        cm.postConcat(satMatrix)

        // Contrast & Brightness
        val scale = contrast
        val translate = brightness + (1f - scale) * 128f * 0.5f
        val cbMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(cbMatrix)

        // Warmth (Red up, Blue down) & Tint (Green vs Magenta)
        if (warmth != 0f || tint != 0f) {
            val rShift = warmth * 0.8f
            val bShift = -warmth * 0.8f
            val gShift = -tint * 0.8f
            val rTint = tint * 0.4f
            val bTint = tint * 0.4f

            val tempMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, rShift + rTint,
                    0f, 1f, 0f, 0f, gShift,
                    0f, 0f, 1f, 0f, bShift + bTint,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(tempMatrix)
        }

        // Apply Color Matrix pass
        val cmBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val cmCanvas = Canvas(cmBmp)
        val cmPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        cmCanvas.drawBitmap(bmp, 0f, 0f, cmPaint)
        bmp.recycle()
        bmp = cmBmp

        // 2. Preset Color Grading Look
        if (preset != CanvaStyleMatchPreset.NONE) {
            val presetBmp = applyPresetLut(bmp, preset)
            bmp.recycle()
            bmp = presetBmp
        }

        // 3. Clarity / Unsharp Sharpen
        if (clarity > 5f) {
            val sharpBmp = applyClarity(bmp, clarity)
            bmp.recycle()
            bmp = sharpBmp
        }

        // 4. Gaussian Blur
        if (blur > 1f) {
            val blurBmp = applyGaussianBlur(bmp, blur)
            bmp.recycle()
            bmp = blurBmp
        }

        // 5. Vignette Darkening
        if (vignette > 5f) {
            applyVignetteInPlace(bmp, vignette)
        }

        // 6. Interactive Tone Curves (Shadows, Midtones, Highlights)
        if (shadows != 0f || midtones != 0f || highlights != 0f) {
            val lut = IntArray(256)
            for (i in 0..255) {
                val x = i / 255.0
                val wShadow = (1.0 - x) * (1.0 - x)
                val wMid = 4.0 * x * (1.0 - x)
                val wHigh = x * x
                val delta = shadows * wShadow + midtones * wMid + highlights * wHigh
                lut[i] = (i + delta).roundToInt().coerceIn(0, 255)
            }
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            for (idx in pixels.indices) {
                val a = (pixels[idx] ushr 24) and 0xFF
                val r = lut[(pixels[idx] ushr 16) and 0xFF]
                val g = lut[(pixels[idx] ushr 8) and 0xFF]
                val b = lut[pixels[idx] and 0xFF]
                pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        }

        // 7. Selective Color Tuning (target Blue ink, Red stamps, Green seals, Paper tint)
        if (colorTarget != SelectiveColorTarget.ALL_MASTER || targetSaturation != 1.0f || targetLuminance != 1.0f) {
            val pixels = IntArray(bmp.width * bmp.height)
            bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val hsv = FloatArray(3)
            for (idx in pixels.indices) {
                val col = pixels[idx]
                val a = (col ushr 24) and 0xFF
                Color.colorToHSV(col, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                var matches = false
                when (colorTarget) {
                    SelectiveColorTarget.ALL_MASTER -> matches = true
                    SelectiveColorTarget.BLUE_INK -> {
                        // Blue / Cyan ballpoint & fountain pen ink
                        if (hue in 175f..265f && sat >= 0.12f) matches = true
                    }
                    SelectiveColorTarget.RED_STAMPS -> {
                        // Red notary seals / stamps
                        if ((hue >= 330f || hue <= 30f) && sat >= 0.15f) matches = true
                    }
                    SelectiveColorTarget.GREEN_SEALS -> {
                        // Green official verification seals
                        if (hue in 75f..165f && sat >= 0.15f) matches = true
                    }
                    SelectiveColorTarget.PAPER_TINT -> {
                        // Paper background tint: low sat, high value
                        if (sat < 0.28f && value > 0.60f) matches = true
                    }
                }

                if (matches) {
                    hsv[1] = (sat * targetSaturation).coerceIn(0f, 1f)
                    hsv[2] = (value * targetLuminance).coerceIn(0f, 1f)
                    pixels[idx] = Color.HSVToColor(a, hsv)
                }
            }
            bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        }

        return@withContext bmp
    }

    private fun applyPresetLut(source: Bitmap, preset: CanvaStyleMatchPreset): Bitmap {
        val cm = ColorMatrix()
        when (preset) {
            CanvaStyleMatchPreset.CINEMATIC_TEAL_ORANGE -> {
                // Boost Orange in highlights, Teal in shadows
                cm.set(
                    floatArrayOf(
                        1.15f, 0.05f, 0.00f, 0f, 10f,
                        0.00f, 1.05f, 0.05f, 0f, 5f,
                        -0.10f, 0.05f, 1.25f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            CanvaStyleMatchPreset.VINTAGE_1970S -> {
                // Warm fade, lifted blacks, slight yellow tint
                cm.set(
                    floatArrayOf(
                        1.08f, 0.04f, 0.00f, 0f, 18f,
                        0.02f, 1.02f, 0.00f, 0f, 12f,
                        -0.05f, 0.00f, 0.85f, 0f, 24f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            CanvaStyleMatchPreset.NOIR_BW -> {
                // High contrast dramatic monochrome
                cm.setSaturation(0.0f)
                val highContrast = ColorMatrix(
                    floatArrayOf(
                        1.45f, 0f, 0f, 0f, -40f,
                        0f, 1.45f, 0f, 0f, -40f,
                        0f, 0f, 1.45f, 0f, -40f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(highContrast)
            }
            CanvaStyleMatchPreset.GOLDEN_HOUR -> {
                cm.set(
                    floatArrayOf(
                        1.20f, 0.05f, 0.00f, 0f, 22f,
                        0.05f, 1.10f, 0.00f, 0f, 14f,
                        0.00f, 0.00f, 0.82f, 0f, -18f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            CanvaStyleMatchPreset.NORDIC_COOL -> {
                cm.setSaturation(0.75f)
                val cool = ColorMatrix(
                    floatArrayOf(
                        0.92f, 0f, 0f, 0f, -5f,
                        0f, 0.98f, 0f, 0f, 0f,
                        0f, 0f, 1.18f, 0f, 15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(cool)
            }
            CanvaStyleMatchPreset.VIVID_POP -> {
                cm.setSaturation(1.65f)
                val pop = ColorMatrix(
                    floatArrayOf(
                        1.12f, 0f, 0f, 0f, 0f,
                        0f, 1.12f, 0f, 0f, 0f,
                        0f, 0f, 1.12f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(pop)
            }
            CanvaStyleMatchPreset.NONE -> {}
        }

        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(source, 0f, 0f, p)
        return out
    }

    private fun applyClarity(source: Bitmap, clarity: Float): Bitmap {
        val srcRgba = Mat()
        val srcRgb = Mat()
        val blurred = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        return try {
            Utils.bitmapToMat(source, srcRgba)
            Imgproc.cvtColor(srcRgba, srcRgb, Imgproc.COLOR_RGBA2RGB)

            val amount = (clarity / 100f) * 1.5
            Imgproc.GaussianBlur(srcRgb, blurred, Size(0.0, 0.0), 3.0)
            Core.addWeighted(srcRgb, 1.0 + amount, blurred, -amount, 0.0, sharpened)

            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_RGB2RGBA)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resultRgba, output)
            output
        } finally {
            srcRgba.release()
            srcRgb.release()
            blurred.release()
            sharpened.release()
            resultRgba.release()
        }
    }

    private fun applyGaussianBlur(source: Bitmap, blurRadius: Float): Bitmap {
        val srcRgba = Mat()
        val blurred = Mat()
        return try {
            Utils.bitmapToMat(source, srcRgba)
            val k = (blurRadius.toInt() * 2 + 1).coerceIn(3, 51).toDouble()
            Imgproc.GaussianBlur(srcRgba, blurred, Size(k, k), 0.0)
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(blurred, output)
            output
        } finally {
            srcRgba.release()
            blurred.release()
        }
    }

    private fun applyVignetteInPlace(source: Bitmap, intensity: Float) {
        val canvas = Canvas(source)
        val cx = source.width / 2f
        val cy = source.height / 2f
        val radius = kotlin.math.hypot(cx.toDouble(), cy.toDouble()).toFloat()

        val maxAlpha = ((intensity / 100f) * 200).toInt().coerceIn(0, 220)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(maxAlpha, 0, 0, 0)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), paint)
    }
}
