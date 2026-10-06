package com.docu.editor.core.scanner

import android.graphics.Color
import com.docu.editor.domain.model.BrandPalette
import com.docu.editor.domain.model.DocumentCanvasLayer

/**
 * Canva Pro Brand Kit Engine.
 * Manages curated and custom brand color schemes, typography presets,
 * and 1-tap palette synchronization across active canvas layers.
 */
object CanvaBrandKitEngine {

    val curatedPalettes: List<BrandPalette> = listOf(
        BrandPalette(
            id = "canva_signature",
            name = "🌟 Canva Signature",
            colorsRgb = listOf(
                Color.rgb(139, 92, 246),  // Electric Violet
                Color.rgb(6, 182, 212),   // Neon Cyan
                Color.rgb(16, 185, 129),  // Emerald Green
                Color.rgb(245, 158, 11),  // Amber Gold
                Color.rgb(15, 23, 42)     // Slate Dark
            )
        ),
        BrandPalette(
            id = "corporate_blue",
            name = "🏢 Executive Royal",
            colorsRgb = listOf(
                Color.rgb(30, 58, 138),   // Dark Blue
                Color.rgb(59, 130, 246),  // Blue Accent
                Color.rgb(96, 165, 250),  // Light Sky
                Color.rgb(147, 197, 253), // Frost
                Color.rgb(15, 23, 42)     // Navy Midnight
            )
        ),
        BrandPalette(
            id = "emerald_elegance",
            name = "🌿 Emerald Luxe",
            colorsRgb = listOf(
                Color.rgb(6, 78, 59),     // Deep Forest
                Color.rgb(5, 150, 105),   // Vivid Emerald
                Color.rgb(16, 185, 129),  // Jade
                Color.rgb(167, 243, 208), // Mint Glow
                Color.rgb(20, 30, 25)     // Onyx Green
            )
        ),
        BrandPalette(
            id = "sunset_vibes",
            name = "🌇 Sunset Radiance",
            colorsRgb = listOf(
                Color.rgb(124, 45, 18),   // Burnt Sienna
                Color.rgb(234, 88, 12),   // Coral Orange
                Color.rgb(249, 115, 22),  // Tangerine
                Color.rgb(253, 186, 116), // Peach Cream
                Color.rgb(36, 18, 14)     // Dark Umber
            )
        ),
        BrandPalette(
            id = "cyberpunk_neon",
            name = "🔮 Cyberpunk Neon",
            colorsRgb = listOf(
                Color.rgb(76, 29, 149),   // Deep Violet
                Color.rgb(217, 70, 239),  // Magenta Hot
                Color.rgb(6, 182, 212),   // Electric Cyan
                Color.rgb(244, 63, 94),   // Rose Neon
                Color.rgb(9, 9, 11)       // Obsidian Jet
            )
        ),
        BrandPalette(
            id = "warm_minimal",
            name = "☕ Warm Minimalist",
            colorsRgb = listOf(
                Color.rgb(69, 26, 3),     // Espresso
                Color.rgb(120, 53, 15),   // Caramel
                Color.rgb(217, 119, 6),   // Warm Amber
                Color.rgb(253, 230, 138), // Vanilla Cream
                Color.rgb(28, 25, 23)     // Warm Charcoal
            )
        ),
        BrandPalette(
            id = "pastel_dream",
            name = "🌸 Pastel Dream",
            colorsRgb = listOf(
                Color.rgb(244, 114, 182), // Soft Pink
                Color.rgb(192, 132, 252), // Lilac
                Color.rgb(129, 140, 248), // Periwinkle
                Color.rgb(103, 232, 249), // Sky Pastel
                Color.rgb(253, 242, 248)  // Milk White
            )
        )
    )

    /**
     * Synchronizes a Brand Palette across all existing Canvas Layers:
     * Shapes receive primary stroke and secondary fill;
     * Text layers receive primary color or accent background.
     */
    fun applyPaletteToLayers(layers: List<DocumentCanvasLayer>, palette: BrandPalette): List<DocumentCanvasLayer> {
        val primary = palette.primaryColorRgb
        val accent = palette.accentColorRgb

        return layers.mapIndexed { idx, layer ->
            val colorForLayer = palette.colorsRgb[idx % palette.colorsRgb.size]
            when {
                layer.isShapeLayer -> {
                    val updatedBmp = VectorShapeGenerator.createShapeBitmap(
                        type = layer.shapeType,
                        width = layer.shapeWidth,
                        height = layer.shapeHeight,
                        strokeColor = colorForLayer,
                        strokeWidth = layer.shapeStrokeWidth,
                        fillColor = layer.shapeFillColor?.let { Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent)) }
                    )
                    layer.copy(
                        bitmap = updatedBmp,
                        shapeStrokeColor = colorForLayer,
                        shapeFillColor = layer.shapeFillColor?.let { Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent)) }
                    )
                }
                layer.isTextLayer -> {
                    val updatedBmp = CanvaTextStudioEngine.createStyledTypographyBitmap(
                        text = layer.text,
                        textColor = colorForLayer,
                        backgroundColor = layer.backgroundColor?.let { primary },
                        fontSize = layer.fontSize,
                        isBold = layer.isBold,
                        isItalic = layer.isItalic,
                        fontFamily = layer.fontFamily
                    )
                    layer.copy(
                        bitmap = updatedBmp,
                        textColor = colorForLayer,
                        backgroundColor = layer.backgroundColor?.let { primary }
                    )
                }
                else -> layer
            }
        }
    }
}
