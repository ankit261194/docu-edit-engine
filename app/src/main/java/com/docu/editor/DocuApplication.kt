package com.docu.editor

import android.app.Application
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.opencv.android.OpenCVLoader

class DocuApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV native library loaded successfully.")
        } else {
            Log.e(TAG, "OpenCV native initialization failed.")
        }

        PDFBoxResourceLoader.init(applicationContext)
    }

    companion object {
        private const val TAG = "DocuApplication"
    }
}
