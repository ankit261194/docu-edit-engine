package com.docu.editor.core.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Random
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AdaptiveNoiseEngine {

    private val random = Random()

    data class ResolutionProfile(
        val scaleFactor: Float,
        val effectiveBlurSigma: Float,
        val effectiveNoiseStdDev: Float
    )

    fun computeProfile(
        bitmapWidth: Int,
        bitmapHeight: Int,
        measuredBackgroundNoise: Float,
        baseBlur: Float = 0.55f
    ): ResolutionProfile {
        val referenceDiag = sqrt((1280.0 * 1280.0) + (720.0 * 720.0)).toFloat()
        val currentDiag = sqrt((bitmapWidth.toDouble() * bitmapWidth) + (bitmapHeight.toDouble() * bitmapHeight)).toFloat()

        val scaleFactor = (currentDiag / referenceDiag).coerceIn(0.7f, 4.0f)
        val effectiveBlur = (baseBlur * scaleFactor).coerceIn(0.4f, 2.8f)
        val noiseMultiplier = 1.0f + 0.35f * log2(scaleFactor).coerceAtLeast(0f)
        val effectiveNoise = (measuredBackgroundNoise * noiseMultiplier).coerceIn(2.5f, 20.0f)

        return ResolutionProfile(scaleFactor, effectiveBlur, effectiveNoise)
    }
}
