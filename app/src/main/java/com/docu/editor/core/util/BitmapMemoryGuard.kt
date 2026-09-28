package com.docu.editor.core.util

import android.graphics.Bitmap

object BitmapMemoryGuard {
    fun recycleAll(vararg bitmaps: Bitmap?) {
        for (b in bitmaps) {
            if (b != null && !b.isRecycled) {
                try {
                    b.recycle()
                } catch (_: Exception) {}
            }
        }
    }
}
