package com.docu.editor.core.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Enterprise Google Gemini Cloud AI Client.
 * Connects directly to Google Generative Language APIs for:
 * 1. Generative Image Inpainting (Object & Stamp Eraser)
 * 2. High-Accuracy Multimodal Handwriting & Print OCR
 * 3. Smart Document Element Analysis & Replacement
 *
 * Provides instant on-device fallback when network or quota limits are reached.
 */
object GeminiCloudAiClient {

    private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
    private const val PROXY_SERVER_BASE = "https://shribalajikripadham.online/api/docu_ai.php"

    /**
     * Verifies whether the provided Gemini API key is valid by querying the models list.
     */
    suspend fun verifyApiKey(apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Pair(false, "No API key provided")
        }

        try {
            val url = URL("$GEMINI_API_BASE/models?key=$cleanKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
            }

            val code = conn.responseCode
            if (code == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val models = json.optJSONArray("models")
                val count = models?.length() ?: 0
                Pair(true, "Active: Gemini Vision API Verified ($count models available)")
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val msg = try {
                    JSONObject(errText).optJSONObject("error")?.optString("message") ?: "HTTP $code"
                } catch (_: Exception) {
                    "HTTP $code"
                }
                Pair(false, "Authentication Failed: $msg")
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.localizedMessage ?: "Network unreachable"}")
        }
    }

    /**
     * Generative Inpainting for Document Object Removal.
     * Reconstructs paper background where an unwanted object was masked.
     */
    suspend fun inpaintCrop(
        cropBitmap: Bitmap,
        maskBitmap: Bitmap?,
        apiKey: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()

        // 1. First attempt: Direct Google Gemini Interactions Image API
        if (cleanKey.isNotEmpty()) {
            try {
                val baos = ByteArrayOutputStream()
                cropBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                val prompt = "Seamlessly inpaint and erase the marked object in this document image crop. " +
                        "Reconstruct clean, natural, seamless paper background matching the surrounding paper texture and color. " +
                        "Maintain the original document tone without any blur or distortion."

                // Try Gemini Interactions endpoint
                val url = URL("$GEMINI_API_BASE/interactions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("x-goog-api-key", cleanKey)
                    connectTimeout = 12000
                    readTimeout = 25000
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("model", "gemini-2.5-flash-image")
                    val inputArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("type", "image")
                            put("data", base64Img)
                            put("mime_type", "image/jpeg")
                        })
                    }
                    put("input", inputArray)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

                if (conn.responseCode == 200) {
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    val respJson = JSONObject(respText)
                    val outputImg = respJson.optJSONObject("interaction")?.optJSONObject("output_image")
                    val b64Result = outputImg?.optString("data")
                    if (!b64Result.isNullOrEmpty()) {
                        val decodedBytes = Base64.decode(b64Result, Base64.DEFAULT)
                        val resultBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (resultBitmap != null) {
                            return@withContext resultBitmap
                        }
                    }
                }
            } catch (_: Exception) {
                // Silently try secondary proxy or fallback to local
            }
        }

        // 2. Secondary attempt: Optional Cloud Hosting Proxy if deployed
        try {
            val baos = ByteArrayOutputStream()
            cropBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("action", "inpaint_object")
                put("image", base64Img)
                if (cleanKey.isNotEmpty()) {
                    put("gemini_api_key", cleanKey)
                }
            }

            val url = URL(PROXY_SERVER_BASE)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (cleanKey.isNotEmpty()) {
                    setRequestProperty("X-Gemini-Key", cleanKey)
                }
                connectTimeout = 8000
                readTimeout = 15000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(respText)
                if (respJson.optBoolean("success")) {
                    val b64 = respJson.optString("inpainted_image")
                    if (b64.isNotEmpty()) {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val resultBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (resultBmp != null) {
                            return@withContext resultBmp
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Hand off to local OpenCV exemplar inpainter
        }

        null
    }

    /**
     * Direct Gemini Vision Multimodal Handwriting and Print OCR.
     */
    suspend fun transcribeHandwriting(
        bitmap: Bitmap,
        apiKey: String
    ): String? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return@withContext null

        try {
            // Scale down if massive to keep payload fast (< 1600px)
            val maxDim = 1600
            val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            } else 1.0f

            val scaled = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            if (scaled != bitmap) scaled.recycle()
            val base64Img = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            // Direct Gemini 2.0 Flash / 1.5 Flash generateContent call
            val url = URL("$GEMINI_API_BASE/models/gemini-2.0-flash:generateContent?key=$cleanKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 12000
                readTimeout = 30000
                doOutput = true
            }

            val prompt = "Accurately transcribe all handwritten and printed text in this document image. " +
                    "Preserve paragraph breaks and layout where appropriate. " +
                    "Return only the transcribed text without any conversational preamble or markdown code blocks."

            val partsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", prompt)
                })
                put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Img)
                    })
                })
            }

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            }

            val payload = JSONObject().apply {
                put("contents", contentsArray)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            if (conn.responseCode == 200) {
                val respText = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(respText)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text").trim()
                        if (text.isNotEmpty()) {
                            return@withContext text
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Return null for offline fallback
        }
        null
    }

    /**
     * Solves academic questions, math equations, or summarizes document text step-by-step.
     */
    suspend fun solveQuestion(question: String, apiKey: String = ""): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isNotEmpty()) {
            try {
                val url = URL("$GEMINI_API_BASE/models/gemini-2.0-flash:generateContent?key=$cleanKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 12000
                    readTimeout = 30000
                    doOutput = true
                }

                val prompt = "You are an expert academic tutor and mathematics solver. " +
                        "Solve the following question or problem step-by-step with clear explanations and the final answer:\n\n$question"

                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                }
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply { put("parts", partsArray) })
                }
                val payload = JSONObject().apply {
                    put("contents", contentsArray)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

                if (conn.responseCode == 200) {
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    val respJson = JSONObject(respText)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text").trim()
                            if (text.isNotEmpty()) return@withContext text
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val cleanQ = question.trim()
        val mathMatch = Regex("""([\d.]+)\s*([\+\-\*\/])\s*([\d.]+)""").find(cleanQ)
        if (mathMatch != null) {
            val (aStr, op, bStr) = mathMatch.destructured
            val a = aStr.toDoubleOrNull() ?: 0.0
            val b = bStr.toDoubleOrNull() ?: 0.0
            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else Double.NaN
                else -> 0.0
            }
            return@withContext "Step 1: Identify numbers and operator: $a $op $b\nStep 2: Calculate result: $res\n\nFinal Answer: $res"
        }

        "Question: $cleanQ\n\nAnalysis:\nTo enable deep generative step-by-step reasoning with AI, configure your Gemini API Key in Me -> Google Gemini AI API Key (Free)."
    }

    /**
     * Translates document text into target language using Google Gemini 2.0 Flash.
     */
    suspend fun translateText(
        text: String,
        targetLanguage: String,
        apiKey: String = ""
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isNotEmpty() && text.isNotBlank()) {
            try {
                val url = URL("$GEMINI_API_BASE/models/gemini-2.0-flash:generateContent?key=$cleanKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 12000
                    readTimeout = 30000
                    doOutput = true
                }

                val prompt = "You are a professional multilingual document translator. " +
                        "Translate the following document text into $targetLanguage. " +
                        "Preserve numbered lists, formatting, dates, names, and formal administrative tone accurately. " +
                        "Output ONLY the translated text without conversational intro or commentary:\n\n$text"

                val partsArray = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                }
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply { put("parts", partsArray) })
                }
                val payload = JSONObject().apply {
                    put("contents", contentsArray)
                }

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

                if (conn.responseCode == 200) {
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    val respJson = JSONObject(respText)
                    val candidates = respJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val translated = parts.getJSONObject(0).optString("text").trim()
                            if (translated.isNotEmpty()) return@withContext translated
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        text
    }
}

