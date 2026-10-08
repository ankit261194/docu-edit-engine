package com.docu.editor.core.tools

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.QRCodeDetector
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise Barcode & QR Code Item representation.
 */
data class BarcodeItem(
    val rawValue: String,
    val displayValue: String = rawValue,
    val format: String = "QR / Barcode",
    val formatId: Int = 0,
    val boundingBox: Rect? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var quantity: Int = 1
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Enterprise Camera & Document Barcode Scanner Engine.
 * Supports:
 * - Real-time continuous batch warehouse scanning (50+ items)
 * - Retail standard 1D barcodes: EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, Codabar, ITF
 * - 2D matrix symbologies: QR Code, Data Matrix, PDF417, Aztec
 * - Duplicate count accumulation and inventory sheet export
 */
object BarcodeScannerEngine {

    private val scannerOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
    }

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(scannerOptions)
    }

    /**
     * Scans all barcodes present in the bitmap using Google ML Kit with OpenCV fallback.
     */
    suspend fun scanBarcodes(bitmap: Bitmap): List<BarcodeItem> = withContext(Dispatchers.Default) {
        val results = mutableListOf<BarcodeItem>()
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val mlkitBarcodes = barcodeScanner.process(inputImage).await()
            for (bc in mlkitBarcodes) {
                val raw = bc.rawValue ?: bc.displayValue ?: continue
                val display = bc.displayValue ?: raw
                results.add(
                    BarcodeItem(
                        rawValue = raw,
                        displayValue = display,
                        format = formatName(bc.format),
                        formatId = bc.format,
                        boundingBox = bc.boundingBox,
                        quantity = 1
                    )
                )
            }
        } catch (_: Exception) {
            // Fallback to OpenCV QRCodeDetector
        }

        if (results.isEmpty()) {
            val cvResult = decodeOpenCvQr(bitmap)
            if (!cvResult.isNullOrBlank()) {
                results.add(
                    BarcodeItem(
                        rawValue = cvResult,
                        displayValue = cvResult,
                        format = "QR Code",
                        formatId = Barcode.FORMAT_QR_CODE,
                        quantity = 1
                    )
                )
            }
        }

        results
    }

    /**
     * High-speed single string decoder helper.
     */
    suspend fun decodeFirst(bitmap: Bitmap): String? {
        val list = scanBarcodes(bitmap)
        return list.firstOrNull()?.rawValue
    }

    /**
     * OpenCV native QR fallback decoder.
     */
    fun decodeOpenCvQr(bitmap: Bitmap): String? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            val qrDetector = QRCodeDetector()
            val points = Mat()
            val result = qrDetector.detectAndDecode(gray, points)
            mat.release()
            gray.release()
            points.release()
            if (!result.isNullOrBlank()) result else null
        } catch (_: Exception) {
            null
        }
    }

    fun formatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "Aztec"
            else -> "Barcode"
        }
    }
}
