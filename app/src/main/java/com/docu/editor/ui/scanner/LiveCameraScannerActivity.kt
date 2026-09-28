package com.docu.editor.ui.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
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
 * Enterprise Live Camera Document Scanner (CamScanner-Grade Real-Time Tracking).
 * Features:
 * - 30 FPS OpenCV Edge Tracking directly on YUV sensor stream.
 * - Dynamic Neon-Cyan & Green Auto-Snapping Polygon Overlay.
 * - Hands-Free Instant Auto-Capture on Document Stabilization (~400ms).
 * - Automatic 4-Corner Deskew and Flattening.
 */
class LiveCameraScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: ScannerOverlayView
    private lateinit var statusText: TextView
    private lateinit var autoSnapChip: TextView
    private lateinit var torchBtn: ImageView

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var autoSnapEnabled: Boolean = true
    private var isTorchOn: Boolean = false
    private var isCapturing: Boolean = false

    private var lastCorners: DocumentCorners? = null
    private var analysisFrameW: Int = 1
    private var analysisFrameH: Int = 1
    private var stableFrameCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

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

        // 3. Top Status Bar & Controls
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

        // 4. Bottom Capture & Auto-Snap Panel
        val bottomPanel = LinearLayout(this).apply {
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

        autoSnapChip = TextView(this).apply {
            text = "⚡ AUTO-SNAP: ACTIVE"
            setTextColor(Color.rgb(0, 230, 118))
            textSize = 12f
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 15, 23, 42))
                setStroke(2, Color.rgb(0, 230, 118))
                cornerRadius = 24f
            }
            setPadding(32, 10, 32, 10)
            setOnClickListener { toggleAutoSnap() }
        }
        bottomPanel.addView(autoSnapChip)

        val shutterContainer = FrameLayout(this).apply {
            setPadding(0, 30, 0, 0)
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
        bottomPanel.addView(shutterContainer)

        rootLayout.addView(bottomPanel)
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

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null || isCapturing) {
            imageProxy.close()
            return
        }

        try {
            val yBuffer = image.planes[0].buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)

            val yMat = Mat(image.height, image.width, CvType.CV_8UC1)
            yMat.put(0, 0, yBytes)

            val rotation = imageProxy.imageInfo.rotationDegrees
            val procMat = when (rotation) {
                90 -> {
                    val r = Mat()
                    Core.rotate(yMat, r, Core.ROTATE_90_CLOCKWISE)
                    yMat.release()
                    r
                }
                270 -> {
                    val r = Mat()
                    Core.rotate(yMat, r, Core.ROTATE_90_COUNTERCLOCKWISE)
                    yMat.release()
                    r
                }
                180 -> {
                    val r = Mat()
                    Core.rotate(yMat, r, Core.ROTATE_180)
                    yMat.release()
                    r
                }
                else -> yMat
            }

            val w = procMat.cols()
            val h = procMat.rows()
            val corners = DocumentEdgeDetector.detectCornersFromGrayMat(procMat, w, h)
            procMat.release()

            runOnUiThread {
                handleDetectedFrameCorners(corners, w, h)
            }
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }

    private fun handleDetectedFrameCorners(corners: DocumentCorners?, w: Int, h: Int) {
        if (isCapturing) return

        analysisFrameW = w
        analysisFrameH = h

        if (corners != null) {
            val prev = lastCorners
            val isStable = if (prev != null) {
                val d1 = abs(corners.topLeft.x - prev.topLeft.x) + abs(corners.topLeft.y - prev.topLeft.y)
                val d2 = abs(corners.topRight.x - prev.topRight.x) + abs(corners.topRight.y - prev.topRight.y)
                val d3 = abs(corners.bottomRight.x - prev.bottomRight.x) + abs(corners.bottomRight.y - prev.bottomRight.y)
                val d4 = abs(corners.bottomLeft.x - prev.bottomLeft.x) + abs(corners.bottomLeft.y - prev.bottomLeft.y)
                val maxDelta = max(max(d1, d2), max(d3, d4))
                maxDelta < 22f
            } else {
                false
            }

            if (isStable) {
                stableFrameCount++
            } else {
                stableFrameCount = 0
            }

            lastCorners = corners

            val steady = stableFrameCount >= 6
            overlayView.updateCorners(corners, steady, w, h)

            if (steady) {
                statusText.text = "Steady! Auto-Capturing..."
                statusText.setTextColor(Color.rgb(0, 230, 118))
            } else {
                statusText.text = "Document detected - hold still"
                statusText.setTextColor(Color.WHITE)
            }

            // Auto-Snap trigger when document is steady for ~400ms
            if (autoSnapEnabled && stableFrameCount >= 12 && !isCapturing) {
                captureHighResAndFinish(corners)
            }
        } else {
            stableFrameCount = 0
            overlayView.updateCorners(null, false, w, h)
            statusText.text = "Align document in camera frame..."
            statusText.setTextColor(Color.WHITE)
        }
    }

    private fun captureHighResAndFinish(detectedCorners: DocumentCorners?) {
        val capture = imageCapture ?: return
        isCapturing = true

        vibrate()

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
                    isCapturing = false
                }
            }
        )
    }

    private fun processCapturedPhotoAndReturn(photoFile: File, corners: DocumentCorners?) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val fullBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                if (fullBitmap != null) {
                    val finalBitmap = if (corners != null && analysisFrameW > 0 && analysisFrameH > 0) {
                        // Scale detected corners from analysis resolution to full photo resolution
                        val scaleX = fullBitmap.width.toFloat() / analysisFrameW
                        val scaleY = fullBitmap.height.toFloat() / analysisFrameH

                        val scaledCorners = DocumentCorners(
                            topLeft = PointF(corners.topLeft.x * scaleX, corners.topLeft.y * scaleY),
                            topRight = PointF(corners.topRight.x * scaleX, corners.topRight.y * scaleY),
                            bottomRight = PointF(corners.bottomRight.x * scaleX, corners.bottomRight.y * scaleY),
                            bottomLeft = PointF(corners.bottomLeft.x * scaleX, corners.bottomLeft.y * scaleY)
                        )

                        // 4-point perspective warp and deskew
                        PerspectiveTransformer.warpPerspective(fullBitmap, scaledCorners)
                    } else {
                        fullBitmap
                    }

                    // Save processed document
                    val outFile = File(cacheDir, "scanned_doc_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(outFile).use { fos ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos)
                    }
                    photoFile.delete()

                    withContext(Dispatchers.Main) {
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_SCANNED_PATH, outFile.absolutePath)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isCapturing = false
                }
            }
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        isTorchOn = !isTorchOn
        cam.cameraControl.enableTorch(isTorchOn)
        torchBtn.setColorFilter(if (isTorchOn) Color.YELLOW else Color.WHITE)
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

    private fun vibrate() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        const val EXTRA_SCANNED_PATH = "extra_scanned_path"
    }
}
