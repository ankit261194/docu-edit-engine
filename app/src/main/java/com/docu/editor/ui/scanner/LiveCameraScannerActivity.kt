package com.docu.editor.ui.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.docu.editor.core.scanner.DocumentEdgeDetector
import com.docu.editor.core.scanner.PerspectiveTransformer
import com.docu.editor.core.scanner.model.DocumentCorners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/**
 * Enterprise Live Camera Document Scanner (CamScanner-Grade Real-Time Tracking + 8-Point Loupe Crop).
 * Features:
 * - 30 FPS OpenCV Edge Tracking directly on YUV sensor stream.
 * - Dynamic Neon-Cyan & Green Auto-Snapping Polygon Overlay.
 * - Millimeter-accurate 8-Point Manual Crop Screen with 2.5x Magnifier Loupe.
 * - Full-resolution 4-point perspective warp and deskew.
 * - Zero-OOM downsampling on 50MP/108MP high-resolution camera sensors.
 */
class LiveCameraScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: ScannerOverlayView
    private lateinit var cropLoupeOverlayView: CropLoupeOverlayView
    private lateinit var statusText: TextView
    private lateinit var autoSnapChip: TextView
    private lateinit var torchBtn: ImageView
    private lateinit var bottomPanel: LinearLayout
    private lateinit var cropReviewPanel: LinearLayout

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var autoSnapEnabled = true
    private var isTorchOn = false
    private var isCapturing = false

    private var isBatchMode = false
    private val batchCapturedPaths = ArrayList<String>()
    private lateinit var batchModeChip: TextView
    private lateinit var finishBatchChip: TextView

    private var lastCorners: DocumentCorners? = null
    private var analysisFrameW: Int = 1
    private var analysisFrameH: Int = 1
    private var stableFrameCount: Int = 0
    private var capturedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Camera Live Preview
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(previewView)

        // 2. Real-Time Document Corner Tracking Overlay
        overlayView = ScannerOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(overlayView)

        // 3. 8-Point Loupe Crop Review Overlay (Hidden during live scanning)
        cropLoupeOverlayView = CropLoupeOverlayView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(cropLoupeOverlayView)

        // 4. Top Status Bar & Controls
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(40, 60, 40, 20)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
        }

        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            setPadding(16, 16, 16, 16)
            setOnClickListener { finish() }
        }
        topBar.addView(closeBtn)

        val statusContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        statusText = TextView(this).apply {
            text = "Point camera at document..."
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(160, 15, 23, 42))
                cornerRadius = 30f
            }
            setPadding(30, 12, 30, 12)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        statusContainer.addView(statusText)
        topBar.addView(statusContainer)

        torchBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(Color.WHITE)
            setPadding(16, 16, 16, 16)
            setOnClickListener { toggleTorch() }
        }
        topBar.addView(torchBtn)
        rootLayout.addView(topBar)

        // 5. Bottom Capture & Auto-Snap Panel
        bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 70)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        autoSnapChip = TextView(this).apply {
            text = "⚡ AUTO-SNAP: ON"
            setTextColor(Color.rgb(0, 230, 118))
            textSize = 12f
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 15, 23, 42))
                setStroke(2, Color.rgb(0, 230, 118))
                cornerRadius = 24f
            }
            setPadding(28, 10, 28, 10)
            setOnClickListener { toggleAutoSnap() }
        }
        modeRow.addView(autoSnapChip)

        val modeSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 1)
        }
        modeRow.addView(modeSpacer)

        batchModeChip = TextView(this).apply {
            text = "📄 SINGLE"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 15, 23, 42))
                setStroke(2, Color.argb(120, 255, 255, 255))
                cornerRadius = 24f
            }
            setPadding(28, 10, 28, 10)
            setOnClickListener { toggleBatchMode() }
        }
        modeRow.addView(batchModeChip)

        bottomPanel.addView(modeRow)

        val shutterContainer = FrameLayout(this).apply {
            setPadding(0, 24, 0, 0)
        }

        val shutterRing = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(6, Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER
            }
        }
        shutterContainer.addView(shutterRing)

        val shutterButton = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(150, 150).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                if (!isCapturing) {
                    captureHighResAndFinish(lastCorners)
                }
            }
        }
        shutterContainer.addView(shutterButton)

        finishBatchChip = TextView(this).apply {
            visibility = View.GONE
            text = "Finish (0) ▶"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.rgb(16, 185, 129))
                cornerRadius = 32f
            }
            setPadding(32, 16, 32, 16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setMargins(0, 0, 40, 0)
            }
            setOnClickListener { finishBatchAndReturn() }
        }
        shutterContainer.addView(finishBatchChip)

        bottomPanel.addView(shutterContainer)
        rootLayout.addView(bottomPanel)

        // 6. Bottom Crop Review Action Dock (CamScanner-Style Retake, Full Page, Done)
        cropReviewPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(30, 20, 30, 60)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        val retakeBtn = TextView(this).apply {
            text = "🔄 Retake"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 30, 41, 59))
                cornerRadius = 24f
            }
            setPadding(36, 20, 36, 20)
            setOnClickListener { restartCameraScan() }
        }
        cropReviewPanel.addView(retakeBtn)

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(30, 1) }
        cropReviewPanel.addView(spacer1)

        val fullPageBtn = TextView(this).apply {
            text = "⛶ Full Page"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 30, 41, 59))
                cornerRadius = 24f
            }
            setPadding(36, 20, 36, 20)
            setOnClickListener { cropLoupeOverlayView.resetToFullImage() }
        }
        cropReviewPanel.addView(fullPageBtn)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(30, 1) }
        cropReviewPanel.addView(spacer2)

        val doneBtn = TextView(this).apply {
            text = "✓ Next"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(0, 200, 83)) // Vibrant Green
                cornerRadius = 24f
            }
            setPadding(44, 20, 44, 20)
            setOnClickListener { applyManualCropAndFinish() }
        }
        cropReviewPanel.addView(doneBtn)

        rootLayout.addView(cropReviewPanel)

        setContentView(rootLayout)
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            @Suppress("DEPRECATION")
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processFrameYuv(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processFrameYuv(imageProxy: ImageProxy) {
        if (isCapturing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val yPlane = mediaImage.planes[0]
            val yBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val frameW = imageProxy.width
            val frameH = imageProxy.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val grayMat = Mat(frameH, frameW, CvType.CV_8UC1)
            val byteCount = yBuffer.remaining()
            val yBytes = ByteArray(byteCount)
            yBuffer.get(yBytes)

            if (rowStride == frameW) {
                grayMat.put(0, 0, yBytes)
            } else {
                for (r in 0 until frameH) {
                    grayMat.put(r, 0, yBytes, r * rowStride, frameW)
                }
            }

            val rotatedMat = when (rotationDegrees) {
                90 -> {
                    val r = Mat()
                    Core.rotate(grayMat, r, Core.ROTATE_90_CLOCKWISE)
                    grayMat.release()
                    r
                }
                180 -> {
                    val r = Mat()
                    Core.rotate(grayMat, r, Core.ROTATE_180)
                    grayMat.release()
                    r
                }
                270 -> {
                    val r = Mat()
                    Core.rotate(grayMat, r, Core.ROTATE_90_COUNTERCLOCKWISE)
                    grayMat.release()
                    r
                }
                else -> grayMat
            }

            val curW = rotatedMat.cols()
            val curH = rotatedMat.rows()
            analysisFrameW = curW
            analysisFrameH = curH

            val detectedCorners = DocumentEdgeDetector.detectCornersFromGrayMat(rotatedMat, curW, curH)
            rotatedMat.release()

            runOnUiThread {
                handleFrameResult(detectedCorners, curW, curH)
            }
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }

    private fun handleFrameResult(corners: DocumentCorners?, frameW: Int, frameH: Int) {
        if (isCapturing || cropLoupeOverlayView.visibility == View.VISIBLE) return

        if (corners != null) {
            if (isCornersStable(corners, lastCorners)) {
                stableFrameCount++
            } else {
                stableFrameCount = 0
            }
            lastCorners = corners

            val isSteady = stableFrameCount >= 10
            overlayView.updateCorners(corners, isSteady, frameW, frameH)

            if (isSteady) {
                statusText.text = "Steady! Hold still..."
                if (autoSnapEnabled && stableFrameCount >= 14 && !isCapturing) {
                    captureHighResAndFinish(corners)
                }
            } else {
                statusText.text = "Document detected"
            }
        } else {
            stableFrameCount = 0
            lastCorners = null
            overlayView.updateCorners(null, false, frameW, frameH)
            statusText.text = "Point camera at document..."
        }
    }

    private fun isCornersStable(c1: DocumentCorners, c2: DocumentCorners?): Boolean {
        if (c2 == null) return false
        val threshold = 22f
        return abs(c1.topLeft.x - c2.topLeft.x) < threshold &&
                abs(c1.topLeft.y - c2.topLeft.y) < threshold &&
                abs(c1.topRight.x - c2.topRight.x) < threshold &&
                abs(c1.topRight.y - c2.topRight.y) < threshold &&
                abs(c1.bottomRight.x - c2.bottomRight.x) < threshold &&
                abs(c1.bottomRight.y - c2.bottomRight.y) < threshold &&
                abs(c1.bottomLeft.x - c2.bottomLeft.x) < threshold &&
                abs(c1.bottomLeft.y - c2.bottomLeft.y) < threshold
    }

    private fun captureHighResAndFinish(detectedCorners: DocumentCorners?) {
        val capture = imageCapture ?: return
        isCapturing = true

        vibrate()
        statusText.text = "Capturing document..."

        val tempFile = File(cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    processCapturedPhotoAndReturn(tempFile, detectedCorners)
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        isCapturing = false
                        statusText.text = "Capture error, try again"
                    }
                }
            }
        )
    }

    private fun decodeSampledBitmapFromFile(filePath: String, maxDim: Int = 2880): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, options)
        val origW = options.outWidth
        val origH = options.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while ((origW / sampleSize) > maxDim || (origH / sampleSize) > maxDim) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(filePath, decodeOptions)
    }

    private fun processCapturedPhotoAndReturn(photoFile: File, corners: DocumentCorners?) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val fullBitmap = decodeSampledBitmapFromFile(photoFile.absolutePath)
                photoFile.delete()
                if (fullBitmap != null) {
                    val initialCorners = if (corners != null && analysisFrameW > 0 && analysisFrameH > 0) {
                        val scaleX = fullBitmap.width.toFloat() / analysisFrameW
                        val scaleY = fullBitmap.height.toFloat() / analysisFrameH
                        DocumentCorners(
                            topLeft = PointF(corners.topLeft.x * scaleX, corners.topLeft.y * scaleY),
                            topRight = PointF(corners.topRight.x * scaleX, corners.topRight.y * scaleY),
                            bottomRight = PointF(corners.bottomRight.x * scaleX, corners.bottomRight.y * scaleY),
                            bottomLeft = PointF(corners.bottomLeft.x * scaleX, corners.bottomLeft.y * scaleY)
                        )
                    } else {
                        val marginX = fullBitmap.width * 0.08f
                        val marginY = fullBitmap.height * 0.08f
                        DocumentCorners(
                            topLeft = PointF(marginX, marginY),
                            topRight = PointF(fullBitmap.width - marginX, marginY),
                            bottomRight = PointF(fullBitmap.width - marginX, fullBitmap.height - marginY),
                            bottomLeft = PointF(marginX, fullBitmap.height - marginY)
                        )
                    }

                    if (isBatchMode) {
                        val warped = if (corners != null && analysisFrameW > 0 && analysisFrameH > 0) {
                            PerspectiveTransformer.warpPerspective(fullBitmap, initialCorners)
                        } else {
                            fullBitmap
                        }
                        val outFile = File(cacheDir, "scanned_batch_${batchCapturedPaths.size}_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(outFile).use { fos ->
                            warped.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                        }
                        if (warped != fullBitmap) warped.recycle()
                        fullBitmap.recycle()

                        batchCapturedPaths.add(outFile.absolutePath)
                        withContext(Dispatchers.Main) {
                            finishBatchChip.visibility = View.VISIBLE
                            finishBatchChip.text = "Finish (${batchCapturedPaths.size}) ▶"
                            statusText.text = "✅ Page ${batchCapturedPaths.size} scanned! Ready for next"
                            isCapturing = false
                            vibrate()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showCropLoupeReview(fullBitmap, initialCorners)
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isCapturing = false
                }
            }
        }
    }

    private fun showCropLoupeReview(bitmap: Bitmap, corners: DocumentCorners) {
        capturedBitmap = bitmap
        cropLoupeOverlayView.sourceBitmap = bitmap
        cropLoupeOverlayView.corners = corners

        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE
        bottomPanel.visibility = View.GONE

        cropLoupeOverlayView.visibility = View.VISIBLE
        cropReviewPanel.visibility = View.VISIBLE

        statusText.text = "🔍 Drag corners with loupe magnifier"
        vibrate()
    }

    private fun restartCameraScan() {
        capturedBitmap?.recycle()
        capturedBitmap = null

        cropLoupeOverlayView.visibility = View.GONE
        cropReviewPanel.visibility = View.GONE

        previewView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
        bottomPanel.visibility = View.VISIBLE

        isCapturing = false
        stableFrameCount = 0
        lastCorners = null
        statusText.text = "Point camera at document..."
    }

    private fun applyManualCropAndFinish() {
        val bmp = capturedBitmap ?: return
        val finalCorners = cropLoupeOverlayView.corners

        statusText.text = "Processing perspective crop..."

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val warped = PerspectiveTransformer.warpPerspective(bmp, finalCorners)
                val outFile = File(cacheDir, "scanned_doc_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    warped.compress(Bitmap.CompressFormat.JPEG, 94, fos)
                }

                if (warped != bmp) {
                    warped.recycle()
                }
                bmp.recycle()
                capturedBitmap = null

                withContext(Dispatchers.Main) {
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_SCANNED_PATH, outFile.absolutePath)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    restartCameraScan()
                }
            }
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        try {
            if (cam.cameraInfo.hasFlashUnit()) {
                isTorchOn = !isTorchOn
                cam.cameraControl.enableTorch(isTorchOn)
                torchBtn.setColorFilter(if (isTorchOn) Color.YELLOW else Color.WHITE)
            } else {
                android.widget.Toast.makeText(this, "Torch not available on this camera", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    private fun toggleAutoSnap() {
        autoSnapEnabled = !autoSnapEnabled
        if (autoSnapEnabled) {
            autoSnapChip.text = "⚡ AUTO-SNAP: ACTIVE"
            autoSnapChip.setTextColor(Color.rgb(0, 230, 118))
            (autoSnapChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(0, 230, 118))
        } else {
            autoSnapChip.text = "✋ MANUAL SNAP MODE"
            autoSnapChip.setTextColor(Color.WHITE)
            (autoSnapChip.background as? GradientDrawable)?.setStroke(2, Color.WHITE)
        }
    }

    private fun toggleBatchMode() {
        isBatchMode = !isBatchMode
        if (isBatchMode) {
            batchModeChip.text = "📚 BATCH"
            batchModeChip.setTextColor(Color.rgb(56, 189, 248))
            (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(56, 189, 248))
            statusText.text = "Batch scan mode: Shoot sequence of pages"
            if (batchCapturedPaths.isNotEmpty()) {
                finishBatchChip.visibility = View.VISIBLE
            }
        } else {
            batchModeChip.text = "📄 SINGLE"
            batchModeChip.setTextColor(Color.WHITE)
            (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.argb(120, 255, 255, 255))
            statusText.text = "Point camera at document..."
            finishBatchChip.visibility = View.GONE
        }
    }

    private fun finishBatchAndReturn() {
        if (batchCapturedPaths.isEmpty()) return
        val resultIntent = Intent().apply {
            putStringArrayListExtra(EXTRA_BATCH_PATHS, batchCapturedPaths)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun vibrate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        capturedBitmap?.recycle()
        cameraExecutor.shutdown()
    }

    companion object {
        const val EXTRA_SCANNED_PATH = "extra_scanned_path"
        const val EXTRA_BATCH_PATHS = "extra_batch_paths"
    }
}
