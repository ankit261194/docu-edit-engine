package com.docu.editor.core.print

import android.content.Context
import android.graphics.Bitmap
import androidx.print.PrintHelper

object PrintDocumentHelper {

    fun printBitmap(
        context: Context,
        bitmap: Bitmap,
        jobName: String = "DocuEdit_Print_${System.currentTimeMillis()}"
    ) {
        val printHelper = PrintHelper(context).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
            colorMode = PrintHelper.COLOR_MODE_COLOR
        }
        printHelper.printBitmap(jobName, bitmap)
    }
}
