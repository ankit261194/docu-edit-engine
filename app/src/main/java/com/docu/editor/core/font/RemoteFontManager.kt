package com.docu.editor.core.font

import android.content.Context
import android.graphics.Typeface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Enterprise Remote Font Manager for DocuEdit.
 * Fetches, caches, and serves dynamic typography directly from the high-speed
 * central server at https://shribalajikripadham.online/api/docu_ai.php
 *
 * Keeps the APK ultra-lightweight while providing 100% full CamScanner-grade font depth!
 */
object RemoteFontManager {

    private const val FONT_SERVER_BASE = "https://shribalajikripadham.online/api/docu_ai.php"
    private val memoryCache = ConcurrentHashMap<String, Typeface>()

    fun isFontCached(context: Context, fontId: String, isBold: Boolean): Boolean {
        val file = getCacheFile(context, fontId, isBold)
        return file.exists() && file.length() > 1024
    }

    fun getCachedTypeface(context: Context, fontId: String, isBold: Boolean): Typeface? {
        val key = "${fontId}_$isBold"
        memoryCache[key]?.let { return it }

        if (fontId == "custom") {
            val customFile = File(File(context.filesDir, "custom_fonts"), "user_custom_font.ttf")
            if (customFile.exists() && customFile.length() > 500) {
                try {
                    val tf = Typeface.createFromFile(customFile)
                    memoryCache[key] = tf
                    return tf
                } catch (_: Exception) {}
            }
        }

        val file = getCacheFile(context, fontId, isBold)
        if (file.exists() && file.length() > 1024) {
            try {
                val tf = Typeface.createFromFile(file)
                memoryCache[key] = tf
                return tf
            } catch (_: Exception) {}
        }
        return null
    }

    fun importCustomFont(context: Context, uri: android.net.Uri): Typeface? {
        return try {
            val fontsDir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
            val customFontFile = File(fontsDir, "user_custom_font.ttf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(customFontFile).use { output ->
                    input.copyTo(output)
                }
            }
            val tf = Typeface.createFromFile(customFontFile)
            memoryCache["custom_false"] = tf
            memoryCache["custom_true"] = tf
            tf
        } catch (_: Exception) {
            null
        }
    }

    fun hasCustomFont(context: Context): Boolean {
        val customFontFile = File(File(context.filesDir, "custom_fonts"), "user_custom_font.ttf")
        return customFontFile.exists() && customFontFile.length() > 500
    }

    suspend fun fetchFont(context: Context, fontId: String, isBold: Boolean): Typeface? = withContext(Dispatchers.IO) {
        val key = "${fontId}_$isBold"
        memoryCache[key]?.let { return@withContext it }

        val cached = getCachedTypeface(context, fontId, isBold)
        if (cached != null) return@withContext cached

        val cacheFile = getCacheFile(context, fontId, isBold)
        val boldParam = if (isBold) "1" else "0"
        val fontUrl = "$FONT_SERVER_BASE?action=get_font&id=$fontId&bold=$boldParam"

        try {
            val url = URL(fontUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = true

            if (conn.responseCode == 200) {
                cacheFile.parentFile?.mkdirs()
                val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 1024) {
                    tempFile.renameTo(cacheFile)
                    val tf = Typeface.createFromFile(cacheFile)
                    memoryCache[key] = tf
                    return@withContext tf
                }
            }
        } catch (_: Exception) {}

        null
    }

    fun fetchFontAsync(
        context: Context,
        fontId: String,
        isBold: Boolean,
        onLoaded: (Typeface) -> Unit
    ) {
        val cached = getCachedTypeface(context, fontId, isBold)
        if (cached != null) {
            onLoaded(cached)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val tf = fetchFont(context, fontId, isBold)
            if (tf != null) {
                withContext(Dispatchers.Main) {
                    onLoaded(tf)
                }
            }
        }
    }

    private fun getCacheFile(context: Context, fontId: String, isBold: Boolean): File {
        val dir = File(context.cacheDir, "fonts")
        return File(dir, "${fontId}_${if (isBold) "bold" else "reg"}.ttf")
    }
}
