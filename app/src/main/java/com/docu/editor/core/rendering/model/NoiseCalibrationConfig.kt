package com.docu.editor.core.rendering.model

data class NoiseCalibrationConfig(
    val blurRadius: Float = 0.65f,
    val noiseMultiplier: Float = 1.0f,
    val autoScaleWithDpi: Boolean = true
) {
    fun calculateEffectiveBlur(bitmapWidth: Int, bitmapHeight: Int): Float {
        if (!autoScaleWithDpi) return blurRadius
        val referenceDiag = 2202f
        val currentDiag = kotlin.math.sqrt((bitmapWidth * bitmapWidth + bitmapHeight * bitmapHeight).toDouble()).toFloat()
        val scale = (currentDiag / referenceDiag).coerceIn(0.7f, 3.5f)
        return (blurRadius * scale).coerceIn(0.3f, 3.0f)
    }
}
