package com.docu.editor.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ultra-fast, zero-lag thumbnail cache for CamScanner document library.
 * Decodes thumbnails in RGB_565 (50% less RAM) with optimal inSampleSize
 * and maintains an LRU memory cache to guarantee 60 FPS list scrolling without UI freezes.
 */
object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(4096, 32768) // 4MB - 32MB cache limit

    private val lruCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun get(path: String): Bitmap? {
        val cached = lruCache.get(path)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    fun put(path: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            lruCache.put(path, bitmap)
        }
    }

    fun evict(path: String) {
        lruCache.remove(path)
    }

    fun clear() {
        lruCache.evictAll()
    }

    suspend fun loadThumbnail(path: String, targetSize: Int = 300): Bitmap? = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext null
        val cached = get(path)
        if (cached != null) return@withContext cached

        val file = File(path)
        if (!file.exists() || file.length() == 0L) return@withContext null

        try {
            // First decode bounds only
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, boundsOptions)

            val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= targetSize) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            }

            val bmp = BitmapFactory.decodeFile(path, decodeOptions)
            if (bmp != null) {
                put(path, bmp)
            }
            bmp
        } catch (_: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Exception) {
            null
        }
    }
}
