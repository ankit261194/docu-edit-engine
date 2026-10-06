package com.docu.editor.core.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.docu.editor.domain.model.CanvaAnimationType
import com.docu.editor.domain.model.DocumentCanvasLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

data class AnimationTransform(
    val alpha: Float = 1.0f,
    val scale: Float = 1.0f,
    val transX: Float = 0f,
    val transY: Float = 0f
)

/**
 * Canva Pro Animation & Video Presentation Engine.
 * Computes fluid easing curves for kinetic document and layer animations:
 * Fade, Rise, Zoom, Pan, Breathe Pulse, Pop, and Stomp.
 */
object CanvaAnimationEngine {

    /**
     * Computes the transform state of an animation at normalized progress t [0.0 .. 1.0].
     */
    fun computeTransform(type: CanvaAnimationType, t: Float): AnimationTransform {
        val clampedT = t.coerceIn(0f, 1f)
        return when (type) {
            CanvaAnimationType.NONE -> AnimationTransform()

            CanvaAnimationType.FADE -> {
                // Smooth cubic ease in
                val alpha = (clampedT * clampedT * (3f - 2f * clampedT))
                AnimationTransform(alpha = alpha)
            }

            CanvaAnimationType.RISE -> {
                // Rises 80px upwards with decelerate ease
                val ease = 1f - (1f - clampedT) * (1f - clampedT)
                val transY = (1f - ease) * 80f
                AnimationTransform(alpha = ease, transY = transY)
            }

            CanvaAnimationType.ZOOM_IN -> {
                // Smooth Ken Burns scale 0.85 -> 1.0
                val ease = clampedT * (2f - clampedT)
                val scale = 0.85f + 0.15f * ease
                AnimationTransform(alpha = ease, scale = scale)
            }

            CanvaAnimationType.PAN_LEFT -> {
                val ease = 1f - (1f - clampedT) * (1f - clampedT)
                val transX = (1f - ease) * 120f
                AnimationTransform(alpha = ease, transX = transX)
            }

            CanvaAnimationType.BREATHE -> {
                // Sinusoidal organic breathing loop
                val angle = clampedT * Math.PI.toFloat() * 2f
                val scale = 1.0f + 0.05f * sin(angle)
                AnimationTransform(alpha = 1.0f, scale = scale)
            }

            CanvaAnimationType.POP -> {
                // Overshoot elastic bounce
                val c4 = (2f * Math.PI.toFloat()) / 3f
                val ease = if (clampedT == 0f) 0f
                else if (clampedT == 1f) 1f
                else Math.pow(2.0, -10.0 * clampedT).toFloat() * sin((clampedT * 10f - 0.75f) * c4) + 1f
                AnimationTransform(alpha = clampedT.coerceAtMost(1f), scale = ease.coerceIn(0f, 1.25f))
            }

            CanvaAnimationType.STOMP -> {
                // Fast impact slam: starts big at 1.4x scale and slams down to 1.0x
                val ease = clampedT * clampedT
                val scale = 1.4f - 0.4f * ease
                AnimationTransform(alpha = clampedT.coerceAtLeast(0.4f), scale = scale)
            }
        }
    }

    /**
     * Generates a multi-frame animation sequence for video preview or export.
     */
    suspend fun renderAnimationFrames(
        baseBitmap: Bitmap,
        layers: List<DocumentCanvasLayer>,
        animationType: CanvaAnimationType,
        frameCount: Int = 12
    ): List<Bitmap> = withContext(Dispatchers.Default) {
        val frames = mutableListOf<Bitmap>()
        val w = baseBitmap.width
        val h = baseBitmap.height

        for (i in 0 until frameCount) {
            val t = i.toFloat() / (frameCount - 1).coerceAtLeast(1)
            val transform = computeTransform(animationType, t)

            val frameBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBmp)

            // Draw base
            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (transform.alpha * 255).toInt().coerceIn(0, 255)
            }
            canvas.save()
            canvas.translate(transform.transX, transform.transY)
            canvas.scale(transform.scale, transform.scale, w / 2f, h / 2f)
            canvas.drawBitmap(baseBitmap, 0f, 0f, basePaint)

            // Draw layers
            for (layer in layers) {
                val lPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (layer.alpha * transform.alpha * 255).toInt().coerceIn(0, 255)
                }
                canvas.save()
                val cx = layer.x + (layer.bitmap.width * layer.scale) / 2f
                val cy = layer.y + (layer.bitmap.height * layer.scale) / 2f
                canvas.rotate(layer.rotation, cx, cy)
                canvas.scale(layer.scale, layer.scale, layer.x, layer.y)
                canvas.drawBitmap(layer.bitmap, layer.x, layer.y, lPaint)
                canvas.restore()
            }
            canvas.restore()

            frames.add(frameBmp)
        }

        return@withContext frames
    }
}
