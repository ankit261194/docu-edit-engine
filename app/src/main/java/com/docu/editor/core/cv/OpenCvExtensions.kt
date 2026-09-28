package com.docu.editor.core.cv

import org.opencv.core.Mat

inline fun <T : Mat, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this.release()
    }
}
