package com.docu.editor.domain.model

import androidx.compose.ui.graphics.Color

enum class CanvaFrameType(val displayName: String, val category: String) {
    NONE("Normal", "Standard"),
    CIRCLE("Circle Mask", "Basic"),
    ROUNDED_SQUARE("Rounded Card", "Basic"),
    POLAROID("Vintage Polaroid", "Photo"),
    VINTAGE_FILM("35mm Film Strip", "Photo"),
    ARCH("Modern Arch", "Editorial"),
    HEART("Heart Cutout", "Decorative"),
    HEXAGON("Hexagon Gem", "Geometric"),
    STAMP_BADGE("Serrated Postage", "Decorative"),
    SMARTPHONE_MOCKUP("Phone Mockup 3D", "Device 3D"),
    LAPTOP_MOCKUP("Laptop Mockup 3D", "Device 3D"),
    CANVAS_TILT_3D("Perspective 3D Tilt", "Device 3D")
}

enum class TextEffectType(val displayName: String) {
    NONE("Classic Clean"),
    NEON("Cyberpunk Neon"),
    GLITCH("Retro VHS Glitch"),
    SHADOW_3D("3D Extrusion Shadow"),
    CURVED_ARC("Curved Arc Path"),
    HOLLOW_OUTLINE("Hollow Outline"),
    DUAL_GRADIENT("Gradient Vibrant")
}

enum class MagicWriteMode(val title: String, val description: String) {
    POLISH("Polish & Fix Grammar", "Clean grammar, typos, capitalization, and punctuation"),
    PROFESSIONAL("Executive Professional", "Elevate into executive business and formal prose"),
    HEADLINE("Punchy Hook Headline", "Transform into high-converting marketing hook"),
    SUMMARIZE("Executive Summary", "Condense into essential key takeaways"),
    BULLET_POINTS("Structured Action Items", "Convert paragraph into neat actionable bullet points"),
    CATCHY_CAPTION("Social Media Caption", "Engaging social post with relevant hashtags")
}

data class BrandPalette(
    val id: String,
    val name: String,
    val colorsRgb: List<Int>,
    val isCustom: Boolean = false
) {
    val primaryColorRgb: Int get() = colorsRgb.firstOrNull() ?: android.graphics.Color.BLACK
    val accentColorRgb: Int get() = colorsRgb.getOrNull(1) ?: android.graphics.Color.BLUE
}

enum class CanvaAnimationType(val displayName: String, val description: String) {
    NONE("None", "Static display"),
    FADE("Fade In", "Smooth cinematic opacity dissolve"),
    RISE("Rise Up", "Ascends vertically with momentum"),
    ZOOM_IN("Cinematic Zoom", "Ken Burns perspective scaling"),
    PAN_LEFT("Slide In", "Smooth horizontal motion glide"),
    BREATHE("Breathe Pulse", "Gentle continuous organic scale pulse"),
    POP("Energetic Pop", "Bouncy overshoot elastic entrance"),
    STOMP("Stomp Impact", "Bold heavy impact entrance")
}

enum class CanvaStyleMatchPreset(val displayName: String, val description: String) {
    NONE("Original", "Unfiltered natural tones"),
    CINEMATIC_TEAL_ORANGE("Teal & Orange", "Hollywood cinematic color grading"),
    VINTAGE_1970S("Vintage 1970s", "Warm nostalgic analog film tones"),
    NOIR_BW("High Contrast Noir", "Deep velvety blacks and dramatic highlights"),
    GOLDEN_HOUR("Golden Hour", "Rich warm amber sunlight ambiance"),
    NORDIC_COOL("Nordic Cool", "Crisp minimalist desaturated cool tones"),
    VIVID_POP("Vivid Pop Art", "High-saturation vibrant modern tones")
}
