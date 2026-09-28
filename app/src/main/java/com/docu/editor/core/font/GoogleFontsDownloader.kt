package com.docu.editor.core.font

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class GoogleFontsDownloader(private val context: Context) {

    private val fontCache = ConcurrentHashMap<String, Typeface>()
    private val handler = Handler(Looper.getMainLooper())

    suspend fun fetchFontAsync(
        fontFamilyName: String,
        weight: Int = 400,
        isItalic: Boolean = false
    ): Typeface? {
        val cacheKey = "${fontFamilyName}_${weight}_$isItalic"
        fontCache[cacheKey]?.let { return it }

        val query = "name=$fontFamilyName&weight=$weight&italic=${if (isItalic) 1 else 0}&besteffort=true"

        val fontRequest = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            query,
            com.docu.editor.R.array.com_google_android_gms_fonts_certs
        )

        return suspendCancellableCoroutine { continuation ->
            val callback = object : FontsContractCompat.FontRequestCallback() {
                override fun onTypefaceRetrieved(typeface: Typeface) {
                    fontCache[cacheKey] = typeface
                    if (continuation.isActive) {
                        continuation.resume(typeface)
                    }
                }

                override fun onTypefaceRequestFailed(reason: Int) {
                    if (continuation.isActive) {
                        val fallback = Typeface.create(Typeface.DEFAULT, if (weight >= 700) Typeface.BOLD else Typeface.NORMAL)
                        continuation.resume(fallback)
                    }
                }
            }

            FontsContractCompat.requestFont(
                context,
                fontRequest,
                callback,
                handler
            )
        }
    }
}
