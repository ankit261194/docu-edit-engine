package com.docu.editor.core.util

import android.graphics.Bitmap

object BitmapOptimizer {

    private const val MAX_DIMENSION_PX = 2800

    fun optimizeForProcessing(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        if (width <= MAX_DIMENSION_PX && height <= MAX_DIMENSION_PX) {
            return source
        }

        val aspectRatio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (width > height) {
            targetWidth = MAX_DIMENSION_PX
            targetHeight = (MAX_DIMENSION_PX / aspectRatio).toInt()
        } else {
            targetHeight = MAX_DIMENSION_PX
            targetWidth = (MAX_DIMENSION_PX * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
