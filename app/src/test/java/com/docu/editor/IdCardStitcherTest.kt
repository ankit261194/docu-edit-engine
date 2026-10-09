package com.docu.editor

import com.docu.editor.core.scanner.IdCardStitcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class IdCardStitcherTest {

    @Test
    fun testIso7810Id1StandardAspectRatio() {
        // ISO/IEC 7810 ID-1 standard dimensions: 85.60mm x 53.98mm
        val standardWidthMm = 85.60f
        val standardHeightMm = 53.98f
        val standardAspect = standardWidthMm / standardHeightMm

        assertEquals(1.5857725f, standardAspect, 0.0001f)
    }

    @Test
    fun testPhysical1To1DimensionsAt300Dpi() {
        val standardWidthMm = 85.60f
        val cardAspect = 85.60f / 53.98f
        val dpi = 300f
        val mmPerInch = 25.4f

        val calculatedWidthPx = (standardWidthMm * (dpi / mmPerInch)).roundToInt().toFloat()
        val calculatedHeightPx = calculatedWidthPx / cardAspect

        assertEquals(1011f, calculatedWidthPx, 1.0f)
        assertEquals(638f, calculatedHeightPx, 1.5f)
    }

    @Test
    fun testEnlargedKycDimensions() {
        val cardAspect = 85.60f / 53.98f
        val verticalWidth = 1680f
        val verticalHeight = verticalWidth / cardAspect

        assertEquals(1059f, verticalHeight, 1.5f)

        val sideBySideWidth = 1140f
        val sideBySideHeight = sideBySideWidth / cardAspect
        assertEquals(719f, sideBySideHeight, 1.5f)
    }

    @Test
    fun testA4PageAndLetterPageDimensions() {
        val a4 = IdCardStitcher.PaperSize.A4
        assertEquals(2480, a4.widthPx)
        assertEquals(3508, a4.heightPx)

        val letter = IdCardStitcher.PaperSize.US_LETTER
        assertEquals(2550, letter.widthPx)
        assertEquals(3300, letter.heightPx)
    }

    @Test
    fun testVerticalStackLayoutCalculations() {
        val pageWidth = IdCardStitcher.PaperSize.A4.widthPx
        val pageHeight = IdCardStitcher.PaperSize.A4.heightPx
        val cardWidth = 1011f
        val cardHeight = 638f

        val marginX = (pageWidth - cardWidth) / 2f
        val topCardY = pageHeight * 0.20f
        val bottomCardY = pageHeight * 0.54f

        // Centered horizontally
        assertEquals(734.5f, marginX, 0.1f)
        assertTrue(marginX > 0f)

        // Cards do not overlap vertically
        val topCardBottom = topCardY + cardHeight
        assertTrue("Top card bottom ($topCardBottom) must be well above bottom card top ($bottomCardY)", topCardBottom < bottomCardY)

        val separation = bottomCardY - topCardBottom
        assertTrue("Sufficient breathing room between cards", separation > 400f)

        // Both cards fit within page height
        val bottomCardBottom = bottomCardY + cardHeight
        assertTrue("Bottom card bottom ($bottomCardBottom) must fit within page ($pageHeight)", bottomCardBottom < pageHeight)
    }

    @Test
    fun testSideBySideLayoutCalculations() {
        val pageWidth = IdCardStitcher.PaperSize.A4.widthPx
        val cardWidth = 1011f
        val spacing = 90f
        val totalW = (cardWidth * 2) + spacing

        val startX = (pageWidth - totalW) / 2f
        assertTrue("Side by side fits comfortably on A4 width", startX > 0f)
        assertEquals(184f, startX, 1.0f)
    }
}
