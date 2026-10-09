package com.docu.editor.core.cv

import android.graphics.Rect
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Forensic-Grade Stroke Mask Extraction Engine.
 *
 * Resolves the "Halo / Bright Rectangle" defect in smartphone camera photos:
 * 1. Background Normalization: Estimates local paper illumination gradient via
 *    morphological closing to normalize ambient lighting, shadows, and color cast to 0.
 * 2. True Ink Stroke Isolation: Extracts only the genuine glyph ink strokes (top-hat contrast)
 *    without capturing paper background or shadow gradients.
 * 3. Micro-Stroke Dilation: Dilates by only 1-2px (sub-pixel anti-aliasing margin), preserving
 *    the untouched camera paper between letters and inside glyph loops ('e', 'o', 'a', 'd').
 * 4. Line Preservation: Protects table gridlines, ruled notebook lines, and underscores.
 */
object PrecisionMaskBuilder {

    fun buildShadowFreeMask(
        srcRgb: Mat,
        targetRect: Rect,
        outputMask: Mat
    ) {
        Mat.zeros(srcRgb.size(), CvType.CV_8UC1).copyTo(outputMask)

        val gray = Mat()
        val textCrop = Mat()
        val bgEstimate = Mat()
        val inkContrast = Mat()
        val coreMask = Mat()
        val dilatedText = Mat()
        val hLineMask = Mat()
        val vLineMask = Mat()
        val protectedLines = Mat()

        val boxHeight = max(10, targetRect.height())
        val boxWidth = max(10, targetRect.width())

        // Background estimate kernel size must be odd and larger than typical stroke thickness (7 to 25px)
        val rawBgKernelSize = (boxHeight * 0.40).toInt().coerceIn(9, 29)
        val bgKernelSize = (rawBgKernelSize / 2) * 2 + 1
        val bgKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(bgKernelSize.toDouble(), bgKernelSize.toDouble())
        )

        // Stroke-level dilation: only 1 to 2 pixels to cover anti-aliasing edge falloff without blooming into paper
        val dilateRadius = (boxHeight * 0.045f).toInt().coerceIn(1, 2)
        val ellipseKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size((2 * dilateRadius + 1).toDouble(), (2 * dilateRadius + 1).toDouble())
        )

        val hKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(max(20.0, boxWidth * 0.4), 1.0))
        val vKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, max(16.0, boxHeight * 0.6)))

        try {
            Imgproc.cvtColor(srcRgb, gray, Imgproc.COLOR_RGB2GRAY)

            // Safe sub-pixel margin around OCR bounding box to capture tall ascenders ('h', 't', 'k') and descenders ('p', 'g', 'y')
            val marginY = (boxHeight * 0.08f).toInt().coerceIn(2, 5)
            val marginX = (boxHeight * 0.05f).toInt().coerceIn(2, 4)
            val cropTop = (targetRect.top - marginY).coerceIn(0, gray.rows())
            val cropBottom = (targetRect.bottom + marginY).coerceIn(0, gray.rows())
            val cropLeft = (targetRect.left - marginX).coerceIn(0, gray.cols())
            val cropRight = (targetRect.right + marginX).coerceIn(0, gray.cols())

            if (cropRight <= cropLeft || cropBottom <= cropTop) return

            val subGray = gray.submat(cropTop, cropBottom, cropLeft, cropRight)
            subGray.copyTo(textCrop)
            subGray.release()

            // 1. Local Ambient Illumination Estimation (Morphological Closing)
            // Removes ink strokes narrower than bgKernelSize, leaving the smooth ambient paper illumination
            Imgproc.morphologyEx(textCrop, bgEstimate, Imgproc.MORPH_CLOSE, bgKernel)

            // 2. Pure Ink Stroke Contrast Extraction (Background - Crop)
            // Paper pixels evaluate to ~0 regardless of room lighting or shadows; ink pixels evaluate to >0
            Core.subtract(bgEstimate, textCrop, inkContrast)

            // 3. Robust Otsu Segmentation on Normalized Ink Contrast
            val otsuThreshold = Imgproc.threshold(
                inkContrast,
                coreMask,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
            )

            // Capture faint anti-aliased edges and faded pencil/ballpoint ink without noise floor
            val effectiveThresh = (otsuThreshold * 0.45).coerceIn(14.0, 50.0)
            Imgproc.threshold(inkContrast, coreMask, effectiveThresh, 255.0, Imgproc.THRESH_BINARY)

            // Ensure we didn't accidentally invert (ink mask should occupy < 65% of crop)
            val nonZero = Core.countNonZero(coreMask).toDouble()
            val totalPixels = coreMask.rows() * coreMask.cols()
            if (totalPixels > 0 && (nonZero / totalPixels) > 0.65) {
                Core.bitwise_not(coreMask, coreMask)
            }

            // 4. Line Protection (Preserve crossing underlines or table rules)
            Imgproc.morphologyEx(coreMask, hLineMask, Imgproc.MORPH_OPEN, hKernel)
            Imgproc.morphologyEx(coreMask, vLineMask, Imgproc.MORPH_OPEN, vKernel)
            Core.bitwise_or(hLineMask, vLineMask, protectedLines)

            // 5. Minimal Sub-Pixel Stroke Dilation
            Imgproc.dilate(coreMask, dilatedText, ellipseKernel)
            Core.subtract(dilatedText, protectedLines, dilatedText)

            val maskRoi = outputMask.submat(cropTop, cropBottom, cropLeft, cropRight)
            dilatedText.copyTo(maskRoi)
            maskRoi.release()
        } finally {
            gray.release()
            textCrop.release()
            bgEstimate.release()
            inkContrast.release()
            coreMask.release()
            dilatedText.release()
            hLineMask.release()
            vLineMask.release()
            protectedLines.release()
            bgKernel.release()
            ellipseKernel.release()
            hKernel.release()
            vKernel.release()
        }
    }
}
