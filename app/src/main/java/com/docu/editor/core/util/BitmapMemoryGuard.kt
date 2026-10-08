package com.docu.editor.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Memory Guard & OOM Shield.
 * Protects budget devices (1GB-3GB RAM) against OutOfMemoryError crashes:
 * - Dynamic Heap Memory Monitoring & Threshold Guard
 * - Safe Bitmap Downsampling Calculator
 * - Tiled Bitmap Region Decoder for 50MP–100MP High-Resolution Scans
 * - Automatic OOM Recovery & Emergency Memory Trim
 */
object BitmapMemoryGuard {

    private const val LOW_MEMORY_THRESHOLD_BYTES = 24 * 1024 * 1024L // 24 MB safe margin

    fun getMaxMemoryBytes(): Long = Runtime.getRuntime().maxMemory()
    fun getTotalMemoryBytes(): Long = Runtime.getRuntime().totalMemory()
    fun getFreeMemoryBytes(): Long = Runtime.getRuntime().freeMemory()

    fun getAvailableMemoryBytes(): Long {
        return getMaxMemoryBytes() - (getTotalMemoryBytes() - getFreeMemoryBytes())
    }

    fun isLowMemory(): Boolean {
        return getAvailableMemoryBytes() < LOW_MEMORY_THRESHOLD_BYTES
    }

    /**
     * Recycles a list of bitmaps cleanly without throwing exceptions.
     */
    fun recycleAll(vararg bitmaps: Bitmap?) {
        for (b in bitmaps) {
            if (b != null && !b.isRecycled) {
                try {
                    b.recycle()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Computes the optimal inSampleSize power-of-two downsampling factor.
     */
    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    /**
     * Safely decodes a large bitmap file with automatic OOM shielding.
     * If native allocation fails or memory is tight, falls back to RGB_565 and 2x downsampling.
     */
    fun decodeFileSafely(
        file: File,
        maxDimension: Int = 2880
    ): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        val sampleSize = calculateInSampleSize(rawW, rawH, maxDimension, maxDimension)

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = if (isLowMemory()) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }

        return try {
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: OutOfMemoryError) {
            System.gc()
            try {
                decodeOpts.inSampleSize = sampleSize * 2
                decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    // =========================================================================
    // TILED BITMAP REGION DECODER (100MP VIEWPORT STREAMER)
    // =========================================================================

    /**
     * Decodes ONLY the requested screen viewport tile from a massive image file (up to 100MP).
     * Bypasses full uncompressed bitmap allocation so 1GB RAM devices experience zero lag.
     */
    fun decodeRegionTiled(
        file: File,
        viewportRect: Rect,
        inSampleSize: Int = 1,
        preferRgb565: Boolean = true
    ): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        val decoder: BitmapRegionDecoder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(file.absolutePath)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(file.absolutePath, false)
            }
        } catch (_: Exception) {
            return null
        } ?: return null

        return try {
            val imageW = decoder.width
            val imageH = decoder.height

            // Clamp viewport coordinates strictly inside image bounds
            val bounded = Rect(
                max(0, viewportRect.left),
                max(0, viewportRect.top),
                min(imageW, viewportRect.right),
                min(imageH, viewportRect.bottom)
            )

            if (bounded.width() <= 0 || bounded.height() <= 0) {
                decoder.recycle()
                return null
            }

            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = max(1, inSampleSize)
                this.inPreferredConfig = if (preferRgb565 || isLowMemory()) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }

            decoder.decodeRegion(bounded, opts)
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } finally {
            decoder.recycle()
        }
    }

    /**
     * Decodes a specific tile from an InputStream.
     */
    fun decodeRegionTiled(
        inputStream: InputStream,
        viewportRect: Rect,
        inSampleSize: Int = 1,
        preferRgb565: Boolean = true
    ): Bitmap? {
        val decoder: BitmapRegionDecoder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(inputStream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(inputStream, false)
            }
        } catch (_: Exception) {
            return null
        } ?: return null

        return try {
            val imageW = decoder.width
            val imageH = decoder.height

            val bounded = Rect(
                max(0, viewportRect.left),
                max(0, viewportRect.top),
                min(imageW, viewportRect.right),
                min(imageH, viewportRect.bottom)
            )

            if (bounded.width() <= 0 || bounded.height() <= 0) {
                decoder.recycle()
                return null
            }

            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = max(1, inSampleSize)
                this.inPreferredConfig = if (preferRgb565 || isLowMemory()) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }

            decoder.decodeRegion(bounded, opts)
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } finally {
            decoder.recycle()
        }
    }
}
