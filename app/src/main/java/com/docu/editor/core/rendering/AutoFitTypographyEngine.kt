package com.docu.editor.core.rendering

import android.graphics.Paint
import android.graphics.Rect

object AutoFitTypographyEngine {
    fun calculateFit(text: String, targetBounds: Rect, paint: Paint) =
        AutoFitFontCondenser.condenseToFit(text, targetBounds, paint)
}
