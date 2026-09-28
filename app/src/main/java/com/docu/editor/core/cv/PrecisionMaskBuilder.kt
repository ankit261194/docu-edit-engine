package com.docu.editor.core.cv

import android.graphics.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

object PrecisionMaskBuilder {

    fun buildShadowFreeMask(
        srcRgb: Mat,
        targetRect: Rect,
        outputMask: Mat
    ) {
        Mat.zeros(srcRgb.size(), CvType.CV_8UC1).copyTo(outputMask)

        val gray = Mat()
        val textCrop = Mat()
        val coreMask = Mat()
        val shadowMask = Mat()
        val combinedText = Mat()
        val dilatedText = Mat()
        val hLineMask = Mat()
        val vLineMask = Mat()
        val protectedLines = Mat()

        val boxHeight = max(10, targetRect.height())
        val dilationSize = (boxHeight * 0.12).toInt().coerceIn(3, 9)

        val ellipseKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(dilationSize.toDouble(), dilationSize.toDouble())
        )
        val hKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(28.0, 1.0))
        val vKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, 28.0))

        try {
            Imgproc.cvtColor(srcRgb, gray, Imgproc.COLOR_RGB2GRAY)

            val cropTop = targetRect.top.coerceIn(0, gray.rows())
            val cropBottom = targetRect.bottom.coerceIn(0, gray.rows())
            val cropLeft = targetRect.left.coerceIn(0, gray.cols())
            val cropRight = targetRect.right.coerceIn(0, gray.cols())

            if (cropRight <= cropLeft || cropBottom <= cropTop) return

            val subGray = gray.submat(cropTop, cropBottom, cropLeft, cropRight)
            subGray.copyTo(textCrop)
            subGray.release()

            val otsuThreshold = Imgproc.threshold(
                textCrop,
                coreMask,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU
            )

            if (Core.countNonZero(coreMask).toDouble() / (coreMask.rows() * coreMask.cols()) > 0.65) {
                Core.bitwise_not(coreMask, coreMask)
            }

            val shadowCutoff = (otsuThreshold + 28.0).coerceAtMost(250.0)
            Imgproc.threshold(textCrop, shadowMask, shadowCutoff, 255.0, Imgproc.THRESH_BINARY_INV)

            Core.bitwise_or(coreMask, shadowMask, combinedText)

            Imgproc.morphologyEx(combinedText, hLineMask, Imgproc.MORPH_OPEN, hKernel)
            Imgproc.morphologyEx(combinedText, vLineMask, Imgproc.MORPH_OPEN, vKernel)
            Core.bitwise_or(hLineMask, vLineMask, protectedLines)

            Imgproc.dilate(combinedText, dilatedText, ellipseKernel)
            Core.subtract(dilatedText, protectedLines, dilatedText)

            val maskRoi = outputMask.submat(cropTop, cropBottom, cropLeft, cropRight)
            dilatedText.copyTo(maskRoi)
            maskRoi.release()
        } finally {
            gray.release()
            textCrop.release()
            coreMask.release()
            shadowMask.release()
            combinedText.release()
            dilatedText.release()
            hLineMask.release()
            vLineMask.release()
            protectedLines.release()
            ellipseKernel.release()
            hKernel.release()
            vKernel.release()
        }
    }
}
