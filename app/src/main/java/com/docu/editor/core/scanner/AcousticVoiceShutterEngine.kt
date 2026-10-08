package com.docu.editor.core.scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Enterprise Hands-Free Acoustic Voice Shutter Engine.
 * Listens continuously for voice commands ("Scan", "Next", "Click", "Page", "Capture", "Photo")
 * to trigger camera shutter without touching the phone screen.
 */
class AcousticVoiceShutterEngine(
    private val context: Context,
    private val onVoiceShutterTriggered: (command: String) -> Unit,
    private val onListeningStateChanged: (isListening: Boolean) -> Unit = {}
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastTriggerTime = 0L

    companion object {
        private const val TAG = "VoiceShutterEngine"
        private const val DEBOUNCE_MS = 1600L

        private val TRIGGER_KEYWORDS = listOf(
            "scan", "next", "click", "page", "shoot", "photo", "capture",
            "kheecho", "agla", "panna", "snap", "take"
        )
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        mainHandler.post {
            initAndStartSpeechRecognizer()
        }
    }

    fun stop() {
        isRunning = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer: ${e.message}")
            }
            onListeningStateChanged(false)
        }
    }

    private fun initAndStartSpeechRecognizer() {
        if (!isRunning) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition unavailable on this device")
            onListeningStateChanged(false)
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}")
            restartListeningDelayed(1000)
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onListeningStateChanged(true)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // Restart recognizer on silence / timeouts if still running
            if (isRunning) {
                restartListeningDelayed(600)
            } else {
                onListeningStateChanged(false)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            checkMatchesForTrigger(matches)
            if (isRunning) {
                restartListeningDelayed(400)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            checkMatchesForTrigger(matches)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkMatchesForTrigger(matches: List<String>?) {
        if (matches == null) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < DEBOUNCE_MS) return

        for (match in matches) {
            val lower = match.lowercase(Locale.ROOT)
            for (keyword in TRIGGER_KEYWORDS) {
                if (lower.contains(keyword)) {
                    lastTriggerTime = now
                    onVoiceShutterTriggered(keyword)
                    return
                }
            }
        }
    }

    private fun restartListeningDelayed(delayMs: Long) {
        if (!isRunning) return
        mainHandler.postDelayed({
            if (isRunning) {
                initAndStartSpeechRecognizer()
            }
        }, delayMs)
    }
}
