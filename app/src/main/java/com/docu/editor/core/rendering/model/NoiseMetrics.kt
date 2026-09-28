package com.docu.editor.core.rendering.model

data class NoiseMetrics(
    val noiseStdDev: Float,
    val meanLuminance: Float,
    val recommendedBlurSigma: Float,
    val isMonochromeNoise: Boolean
)
