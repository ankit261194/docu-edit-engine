package com.docu.editor.core.scanner

import android.os.SystemClock
import kotlin.math.abs

/**
 * Enterprise Page-Turn Motion AI Detector for Hands-Free Continuous Book Scanning.
 * Monitors real-time camera video stream using frame-to-frame pixel luminance delta.
 * 
 * Flow:
 * 1. User puts hand in to turn book page -> Motion Spikes (HAND_MOVING).
 * 2. User removes hand -> Motion Drops (PAGE_SETTLING).
 * 3. Book remains still for 1.5 seconds -> Countdown finishes -> AUTO-SHUTTER FIRES.
 * 4. Cycle repeats indefinitely for 100% hands-free multi-page scanning.
 */
class PageTurnMotionDetector(
    private val onAutoCaptureTriggered: () -> Unit,
    private val onMotionStateChanged: (state: MotionState, progressFraction: Float) -> Unit = { _, _ -> }
) {

    enum class MotionState {
        WAITING_FOR_HAND,   // Book is stable, waiting for user to turn page
        HAND_MOVING,        // User's hand or page turning is actively moving
        PAGE_SETTLING,      // Hand just exited, page is stabilizing
        COUNTDOWN_ACTIVE,   // 1.5s countdown running to take photo
        DEBOUNCED           // Shutter just fired, waiting for next page turn
    }

    private var previousFrameBuffer: ByteArray? = null
    private var prevWidth = 0
    private var prevHeight = 0

    private var currentState = MotionState.WAITING_FOR_HAND
    private var stillStartTime = 0L
    private var lastCaptureTime = 0L

    var isEnabled = true

    companion object {
        private const val MOTION_HIGH_THRESHOLD = 0.10f    // 10% pixel delta = hand moving
        private const val MOTION_LOW_THRESHOLD = 0.035f    // 3.5% pixel delta = still page
        private const val COUNTDOWN_DURATION_MS = 1500L    // 1.5 second stabilization countdown
        private const val POST_CAPTURE_DEBOUNCE_MS = 1800L // 1.8s cooldown after shutter
    }

    /**
     * Resets detector state.
     */
    fun reset() {
        previousFrameBuffer = null
        currentState = MotionState.WAITING_FOR_HAND
        stillStartTime = 0L
        onMotionStateChanged(currentState, 0f)
    }

    /**
     * Processes downsampled Y-luminance plane from camera ImageAnalysis.
     * @param yBuffer raw grayscale bytes of current frame
     * @param width width of frame (e.g. 160 or 320)
     * @param height height of frame (e.g. 120 or 240)
     */
    fun processFrame(yBuffer: ByteArray, width: Int, height: Int) {
        if (!isEnabled) return

        val now = SystemClock.elapsedRealtime()

        // Check post-capture debounce
        if (now - lastCaptureTime < POST_CAPTURE_DEBOUNCE_MS) {
            currentState = MotionState.DEBOUNCED
            onMotionStateChanged(currentState, 0f)
            return
        }

        val prev = previousFrameBuffer
        if (prev == null || prevWidth != width || prevHeight != height) {
            previousFrameBuffer = yBuffer.copyOf()
            prevWidth = width
            prevHeight = height
            stillStartTime = now
            return
        }

        // Calculate motion intensity (subsampled for 60+ FPS zero-latency performance)
        val step = 4
        var changedPixels = 0
        var totalSamples = 0
        val thresholdDelta = 22 // luminance delta out of 255

        for (i in 0 until (width * height) step step) {
            val currVal = yBuffer[i].toInt() and 0xFF
            val prevVal = prev[i].toInt() and 0xFF
            if (abs(currVal - prevVal) > thresholdDelta) {
                changedPixels++
            }
            totalSamples++
        }

        val motionIntensity = if (totalSamples > 0) changedPixels.toFloat() / totalSamples else 0f

        // Copy current frame for next comparison
        System.arraycopy(yBuffer, 0, prev, 0, minOf(yBuffer.size, prev.size))

        // State Machine Transition
        when (currentState) {
            MotionState.WAITING_FOR_HAND, MotionState.DEBOUNCED -> {
                if (motionIntensity > MOTION_HIGH_THRESHOLD) {
                    currentState = MotionState.HAND_MOVING
                    onMotionStateChanged(currentState, 0f)
                }
            }

            MotionState.HAND_MOVING -> {
                if (motionIntensity < MOTION_LOW_THRESHOLD) {
                    // Hand just left! Begin settling countdown
                    currentState = MotionState.PAGE_SETTLING
                    stillStartTime = now
                    onMotionStateChanged(currentState, 0.1f)
                }
            }

            MotionState.PAGE_SETTLING, MotionState.COUNTDOWN_ACTIVE -> {
                if (motionIntensity > MOTION_HIGH_THRESHOLD) {
                    // Hand came back in, abort countdown
                    currentState = MotionState.HAND_MOVING
                    stillStartTime = 0L
                    onMotionStateChanged(currentState, 0f)
                } else {
                    currentState = MotionState.COUNTDOWN_ACTIVE
                    val elapsed = now - stillStartTime
                    val progress = (elapsed.toFloat() / COUNTDOWN_DURATION_MS).coerceIn(0f, 1f)
                    onMotionStateChanged(currentState, progress)

                    if (elapsed >= COUNTDOWN_DURATION_MS) {
                        // Page has settled and countdown reached! Fire shutter!
                        lastCaptureTime = now
                        currentState = MotionState.DEBOUNCED
                        onMotionStateChanged(currentState, 1.0f)
                        onAutoCaptureTriggered()
                    }
                }
            }
        }
    }
}
