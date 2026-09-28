package com.docu.editor.core.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object RotatedTextProcessor {

    data class DeskewedPatch(
        val horizontalBitmap: Bitmap,
        val transformMatrix: Matrix,
        val inverseMatrix: Matrix,
        val localHorizontalBounds: Rect,
        val patchOriginX: Float,
        val patchOriginY: Float
    )

    suspend fun extractCanonicalHorizontalPatch(
        source: Bitmap,
        targetBounds: Rect,
        angleDegrees: Float,
        paddingPx: Int = 30
    ): DeskewedPatch = withContext(Dispatchers.Default) {
        val centerX = targetBounds.exactCenterX()
        val centerY = targetBounds.exactCenterY()
        val rad = Math.toRadians(abs(angleDegrees).toDouble())

        val rawW = targetBounds.width() + paddingPx * 2
        val rawH = targetBounds.height() + paddingPx * 2
        val rotW = (rawW * cos(rad) + rawH * sin(rad)).toInt()
        val rotH = (rawW * sin(rad) + rawH * cos(rad)).toInt()

        val left = (centerX - rotW / 2f).toInt().coerceIn(0, source.width - 1)
        val top = (centerY - rotH / 2f).toInt().coerceIn(0, source.height - 1)
        val right = (centerX + rotW / 2f).toInt().coerceIn(left + 1, source.width)
        val bottom = (centerY + rotH / 2f).toInt().coerceIn(top + 1, source.height)

        val cropW = right - left
        val cropH = bottom - top

        val patchBitmap = Bitmap.createBitmap(source, left, top, cropW, cropH)

        val srcMat = Mat()
        val dstMat = Mat()
        val rotMat = Mat()

        try {
            Utils.bitmapToMat(patchBitmap, srcMat)

            val localCenter = Point(cropW / 2.0, cropH / 2.0)
            val rotationMatrix2D = Imgproc.getRotationMatrix2D(localCenter, angleDegrees.toDouble(), 1.0)
            rotationMatrix2D.copyTo(rotMat)

            Imgproc.warpAffine(
                srcMat,
                dstMat,
                rotMat,
                Size(cropW.toDouble(), cropH.toDouble()),
                Imgproc.INTER_CUBIC,
                org.opencv.core.Core.BORDER_REPLICATE
            )

            val horizontalBmp = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dstMat, horizontalBmp)

            val forwardMatrix = Matrix().apply {
                postRotate(-angleDegrees, localCenter.x.toFloat(), localCenter.y.toFloat())
            }
            val inverseMatrix = Matrix().apply {
                forwardMatrix.invert(this)
            }

            val halfTargetW = targetBounds.width() / 2
            val halfTargetH = targetBounds.height() / 2
            val localBounds = Rect(
                (localCenter.x - halfTargetW).toInt(),
                (localCenter.y - halfTargetH).toInt(),
                (localCenter.x + halfTargetW).toInt(),
                (localCenter.y + halfTargetH).toInt()
            )

            DeskewedPatch(
                horizontalBitmap = horizontalBmp,
                transformMatrix = forwardMatrix,
                inverseMatrix = inverseMatrix,
                localHorizontalBounds = localBounds,
                patchOriginX = left.toFloat(),
                patchOriginY = top.toFloat()
            )
        } finally {
            srcMat.release()
            dstMat.release()
            rotMat.release()
            patchBitmap.recycle()
        }
    }

    suspend fun reprojectPatchToCanvas(
        masterBitmap: Bitmap,
        editedHorizontalPatch: Bitmap,
        deskewedInfo: DeskewedPatch,
        angleDegrees: Float
    ) = withContext(Dispatchers.Default) {
        val srcMat = Mat()
        val dstMat = Mat()

        try {
            Utils.bitmapToMat(editedHorizontalPatch, srcMat)

            val center = Point(editedHorizontalPatch.width / 2.0, editedHorizontalPatch.height / 2.0)
            val reprojectMatrix = Imgproc.getRotationMatrix2D(center, -angleDegrees.toDouble(), 1.0)

            Imgproc.warpAffine(
                srcMat,
                dstMat,
                reprojectMatrix,
                Size(editedHorizontalPatch.width.toDouble(), editedHorizontalPatch.height.toDouble()),
                Imgproc.INTER_CUBIC,
                org.opencv.core.Core.BORDER_TRANSPARENT
            )

            val reprojectedBmp = Bitmap.createBitmap(
                editedHorizontalPatch.width,
                editedHorizontalPatch.height,
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(dstMat, reprojectedBmp)

            val canvas = Canvas(masterBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(reprojectedBmp, deskewedInfo.patchOriginX, deskewedInfo.patchOriginY, paint)

            reprojectedBmp.recycle()
            reprojectMatrix.release()
        } finally {
            srcMat.release()
            dstMat.release()
        }
    }
}
