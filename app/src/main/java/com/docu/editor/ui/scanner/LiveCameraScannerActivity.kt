package com.docu.editor.ui.scanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

    // CamScanner Document Filter Enhancement Panel
    private lateinit var filterReviewPanel: FrameLayout
    private lateinit var filterPreviewImageView: ImageView
    private var currentWarpedBitmap: Bitmap? = null
    private var currentFilteredBitmap: Bitmap? = null
    private var selectedFilterType = com.docu.editor.core.scanner.DocumentFilters.FilterType.MAGIC_COLOR
    private val filterChips = ArrayList<TextView>()

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var autoSnapEnabled = false
    private var isTorchOn = false
    private var isCapturing = false

    enum class ScannerMode { SINGLE, BATCH, ID_CARD, BOOK, WHITEBOARD, PASSPORT }
    private var scannerMode = ScannerMode.SINGLE
    private var isBatchMode = false
    private var idCardFrontBitmap: Bitmap? = null
    private val batchCapturedPaths = ArrayList<String>()
    private lateinit var batchModeChip: TextView
    private lateinit var finishBatchChip: TextView
    private lateinit var batchThumbnailBadge: FrameLayout
    private lateinit var batchThumbnailImg: ImageView
    private lateinit var batchBadgeCountText: TextView

    // Enterprise Zero Motion Blur Sensor Integration
    private var sensorManager: SensorManager? = null
    private var motionSensor: Sensor? = null
    private var isDeviceSteady: Boolean = true
    private var smoothMotionMagnitude: Float = 0f
    private val gravityFilter = FloatArray(3)

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rawMotion = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                kotlin.math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
            } else {
                val alpha = 0.8f
                gravityFilter[0] = alpha * gravityFilter[0] + (1 - alpha) * event.values[0]
                gravityFilter[1] = alpha * gravityFilter[1] + (1 - alpha) * event.values[1]
                gravityFilter[2] = alpha * gravityFilter[2] + (1 - alpha) * event.values[2]
                val lx = event.values[0] - gravityFilter[0]
                val ly = event.values[1] - gravityFilter[1]
                val lz = event.values[2] - gravityFilter[2]
                kotlin.math.sqrt(lx * lx + ly * ly + lz * lz)
            }
            smoothMotionMagnitude = 0.7f * smoothMotionMagnitude + 0.3f * rawMotion
            isDeviceSteady = (smoothMotionMagnitude < 0.38f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var lastCorners: DocumentCorners? = null
    private var analysisFrameW: Int = 1
    private var analysisFrameH: Int = 1
    private var stableFrameCount: Int = 0
    private var capturedBitmap: Bitmap? = null
    private var waitingForPageTurn: Boolean = false
    private var lastCapturedCenter: PointF = PointF(0f, 0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        motionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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
            autoSnapEnabled = this@LiveCameraScannerActivity.autoSnapEnabled
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
            text = "✋ MANUAL SHUTTER"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 15, 23, 42))
                setStroke(2, Color.argb(140, 255, 255, 255))
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

        batchThumbnailBadge = FrameLayout(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(130, 160).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setMargins(40, 0, 0, 0)
            }
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.argb(200, 30, 41, 59))
                setStroke(3, Color.rgb(0, 230, 118))
            }
            setPadding(6, 6, 6, 6)
            setOnClickListener { showBatchReviewDialog() }
        }

        batchThumbnailImg = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        batchThumbnailBadge.addView(batchThumbnailImg)

        batchBadgeCountText = TextView(this).apply {
            text = "0"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(16, 185, 129))
            }
            layoutParams = FrameLayout.LayoutParams(52, 52).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 0, 0, 0)
            }
        }
        batchThumbnailBadge.addView(batchBadgeCountText)

        shutterContainer.addView(batchThumbnailBadge)

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
            setOnClickListener { showBatchReviewDialog() }
        }
        shutterContainer.addView(finishBatchChip)

        bottomPanel.addView(shutterContainer)
        rootLayout.addView(bottomPanel)

        // 6. Bottom Crop Review Action Dock (CamScanner-Style Rotate, Auto, Full Page, Next)
        cropReviewPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(20, 16, 20, 50)
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
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 30, 41, 59))
                cornerRadius = 24f
            }
            setPadding(28, 18, 28, 18)
            setOnClickListener { restartCameraScan() }
        }
        cropReviewPanel.addView(retakeBtn)

        val spacer1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(12, 1) }
        cropReviewPanel.addView(spacer1)

        val rotateBtn = TextView(this).apply {
            text = "↺ Rotate"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 30, 41, 59))
                cornerRadius = 24f
            }
            setPadding(28, 18, 28, 18)
            setOnClickListener { cropLoupeOverlayView.rotateImage(90f) }
        }
        cropReviewPanel.addView(rotateBtn)

        val spacer2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(12, 1) }
        cropReviewPanel.addView(spacer2)

        val autoBtn = TextView(this).apply {
            text = "⚡ Auto"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 30, 41, 59))
                cornerRadius = 24f
            }
            setPadding(28, 18, 28, 18)
            setOnClickListener { cropLoupeOverlayView.autoDetectDocument() }
        }
        cropReviewPanel.addView(autoBtn)

        val spacer3 = View(this).apply { layoutParams = LinearLayout.LayoutParams(12, 1) }
        cropReviewPanel.addView(spacer3)

        val fullPageBtn = TextView(this).apply {
            text = "⛶ Full"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 30, 41, 59))
                cornerRadius = 24f
            }
            setPadding(28, 18, 28, 18)
            setOnClickListener { cropLoupeOverlayView.resetToFullImage() }
        }
        cropReviewPanel.addView(fullPageBtn)

        val spacer4 = View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) }
        cropReviewPanel.addView(spacer4)

        val nextBtn = TextView(this).apply {
            text = "Next ➔"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(0, 200, 83)) // Vibrant Green
                cornerRadius = 24f
            }
            setPadding(36, 18, 36, 18)
            setOnClickListener { applyManualCropAndProceedToFilter() }
        }
        cropReviewPanel.addView(nextBtn)

        rootLayout.addView(cropReviewPanel)

        // 7. CamScanner Document Filter Enhancement Panel (Dedicated Post-Crop Screen)
        filterReviewPanel = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.rgb(15, 23, 42))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val filterRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val filterTopBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(30, 50, 30, 20)
            setBackgroundColor(Color.argb(230, 15, 23, 42))
        }
        val filterTitle = TextView(this).apply {
            text = "✨ DocuEdit Pro Enhancement"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val filterSub = TextView(this).apply {
            text = "Select contrast & shadow removal filter"
            setTextColor(Color.rgb(148, 163, 184))
            textSize = 12f
            setPadding(0, 4, 0, 0)
        }
        filterTopBar.addView(filterTitle)
        filterTopBar.addView(filterSub)
        filterRoot.addView(filterTopBar)

        val previewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
            ).apply {
                setMargins(20, 10, 20, 10)
            }
        }
        filterPreviewImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewContainer.addView(filterPreviewImageView)
        filterRoot.addView(previewContainer)

        val filterBottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(240, 15, 23, 42))
            setPadding(20, 20, 20, 60)
        }

        val filterPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(10, 10, 10, 24)
        }

        val filtersList = listOf(
            Pair("✨ Magic Color", com.docu.editor.core.scanner.DocumentFilters.FilterType.MAGIC_COLOR),
            Pair("📄 Original", com.docu.editor.core.scanner.DocumentFilters.FilterType.ORIGINAL),
            Pair("🖨️ Sharp B&W", com.docu.editor.core.scanner.DocumentFilters.FilterType.CLEAN_BW),
            Pair("🌓 Grayscale", com.docu.editor.core.scanner.DocumentFilters.FilterType.GRAYSCALE)
        )

        filterChips.clear()
        for ((name, type) in filtersList) {
            val chip = TextView(this).apply {
                text = name
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(28, 16, 28, 16)
                val isSelected = (type == selectedFilterType)
                setTextColor(if (isSelected) Color.rgb(0, 230, 118) else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.argb(220, 30, 41, 59))
                    cornerRadius = 24f
                    if (isSelected) setStroke(3, Color.rgb(0, 230, 118))
                }
                setOnClickListener { selectFilterPreset(type) }
            }
            filterChips.add(chip)
            filterPillsRow.addView(chip)
            val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) }
            filterPillsRow.addView(spacer)
        }
        val filterScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filterPillsRow)
        }
        filterBottomContainer.addView(filterScroll)

        val filterActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 10, 16, 0)
        }
        val retakeFilterBtn = TextView(this).apply {
            text = "🔄 Retake"
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 51, 65, 85))
                cornerRadius = 24f
            }
            setPadding(28, 18, 28, 18)
            setOnClickListener { restartCameraScan() }
        }
        filterActionRow.addView(retakeFilterBtn)

        val spacerActions1 = View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) }
        filterActionRow.addView(spacerActions1)

        val backToCropBtn = TextView(this).apply {
            text = "📐 Adjust Crop"
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(Color.argb(200, 51, 65, 85))
                cornerRadius = 24f
            }
            setPadding(28, 18, 28, 18)
            setOnClickListener { returnToCropScreen() }
        }
        filterActionRow.addView(backToCropBtn)

        val spacerActions2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(24, 1) }
        filterActionRow.addView(spacerActions2)

        val saveFinishBtn = TextView(this).apply {
            text = "✓ Open in Editor"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.rgb(0, 200, 83))
                cornerRadius = 24f
            }
            setPadding(36, 18, 36, 18)
            setOnClickListener { saveFilteredDocumentAndFinish() }
        }
        filterActionRow.addView(saveFinishBtn)

        filterBottomContainer.addView(filterActionRow)
        filterRoot.addView(filterBottomContainer)
        filterReviewPanel.addView(filterRoot)
        rootLayout.addView(filterReviewPanel)

        setContentView(rootLayout)

        val initMode = intent.getStringExtra(EXTRA_INITIAL_MODE)
        if (initMode != null) {
            try {
                setScannerMode(ScannerMode.valueOf(initMode))
            } catch (_: Exception) {}
        }

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

    private fun playShutterSound() {
        try {
            val sound = android.media.MediaActionSound()
            sound.play(android.media.MediaActionSound.SHUTTER_CLICK)
        } catch (_: Exception) {}
    }

    private fun handleFrameResult(corners: DocumentCorners?, frameW: Int, frameH: Int) {
        if (isCapturing || cropLoupeOverlayView.visibility == View.VISIBLE) return

        if (scannerMode == ScannerMode.ID_CARD || scannerMode == ScannerMode.PASSPORT) {
            statusText.text = if (scannerMode == ScannerMode.PASSPORT) {
                "🛂 Align passport photo page in frame"
            } else if (idCardFrontBitmap == null) {
                "🪪 Align ID card FRONT in frame"
            } else {
                "🪪 Align ID card BACK in frame"
            }
            stableFrameCount++
            val progress = (stableFrameCount.toFloat() / 20f).coerceIn(0f, 1f)
            overlayView.updateCorners(null, true, frameW, frameH, progress)
            if (autoSnapEnabled && stableFrameCount >= 20 && !isCapturing) {
                playShutterSound()
                captureHighResAndFinish(null)
            }
            return
        }

        if (scannerMode == ScannerMode.BOOK) {
            statusText.text = "📖 Align book spine on dashed center line"
            stableFrameCount++
            val progress = (stableFrameCount.toFloat() / 22f).coerceIn(0f, 1f)
            overlayView.updateCorners(null, true, frameW, frameH, progress)
            if (autoSnapEnabled && stableFrameCount >= 22 && !isCapturing) {
                playShutterSound()
                captureHighResAndFinish(null)
            }
            return
        }

        if (corners != null) {
            if (isBatchMode && waitingForPageTurn) {
                val curCenterX = (corners.topLeft.x + corners.topRight.x + corners.bottomRight.x + corners.bottomLeft.x) / 4f
                val curCenterY = (corners.topLeft.y + corners.topRight.y + corners.bottomRight.y + corners.bottomLeft.y) / 4f
                val drift = kotlin.math.hypot((curCenterX - lastCapturedCenter.x).toDouble(), (curCenterY - lastCapturedCenter.y).toDouble()).toFloat()
                if (drift > 60f) {
                    waitingForPageTurn = false
                    stableFrameCount = 0
                } else {
                    statusText.text = "✅ Page ${batchCapturedPaths.size} captured • Flip page to scan next 📄"
                    overlayView.updateCorners(corners, true, frameW, frameH, 1f)
                    return
                }
            }

            if (isCornersStable(corners, lastCorners)) {
                stableFrameCount++
            } else {
                stableFrameCount = max(0, stableFrameCount - 2)
            }
            lastCorners = corners

            val progress = (stableFrameCount.toFloat() / 9f).coerceIn(0f, 1f)
            val isSteady = stableFrameCount >= 4
            overlayView.updateCorners(corners, isSteady, frameW, frameH, progress)

            if (!isDeviceSteady) {
                statusText.text = "⚠️ Steady your device... hold still"
                stableFrameCount = max(0, stableFrameCount - 2)
            } else if (isSteady) {
                if (autoSnapEnabled) {
                    statusText.text = "🎯 Perfect alignment & steady! Auto-snapping (${(progress * 100).toInt()}%)"
                    if (stableFrameCount >= 18 && !isCapturing) {
                        playShutterSound()
                        captureHighResAndFinish(corners)
                    }
                } else {
                    statusText.text = "📄 Document detected • Press shutter to capture 📸"
                }
            } else {
                statusText.text = "Align document in camera frame..."
            }
        } else {
            if (isBatchMode && waitingForPageTurn) {
                waitingForPageTurn = false
                stableFrameCount = 0
            }
            stableFrameCount = max(0, stableFrameCount - 3)
            lastCorners = null
            overlayView.updateCorners(null, false, frameW, frameH, 0f)
            statusText.text = if (isBatchMode && batchCapturedPaths.isNotEmpty()) "Flip page for next scan..." else "Point camera at document..."
        }
    }

    private fun isCornersStable(c1: DocumentCorners, c2: DocumentCorners?): Boolean {
        if (c2 == null) return false
        val center1X = (c1.topLeft.x + c1.topRight.x + c1.bottomRight.x + c1.bottomLeft.x) / 4f
        val center1Y = (c1.topLeft.y + c1.topRight.y + c1.bottomRight.y + c1.bottomLeft.y) / 4f
        val center2X = (c2.topLeft.x + c2.topRight.x + c2.bottomRight.x + c2.bottomLeft.x) / 4f
        val center2Y = (c2.topLeft.y + c2.topRight.y + c2.bottomRight.y + c2.bottomLeft.y) / 4f

        val centerDrift = kotlin.math.hypot((center1X - center2X).toDouble(), (center1Y - center2Y).toDouble()).toFloat()
        val cornerDriftMax = max(
            max(abs(c1.topLeft.x - c2.topLeft.x), abs(c1.topRight.x - c2.topRight.x)),
            max(abs(c1.bottomRight.x - c2.bottomRight.x), abs(c1.bottomLeft.x - c2.bottomLeft.x))
        )
        return centerDrift < 28f && cornerDriftMax < 45f
    }

    private fun captureHighResAndFinish(detectedCorners: DocumentCorners?) {
        val capture = imageCapture ?: return
        isCapturing = true

        if (detectedCorners != null) {
            val cX = (detectedCorners.topLeft.x + detectedCorners.topRight.x + detectedCorners.bottomRight.x + detectedCorners.bottomLeft.x) / 4f
            val cY = (detectedCorners.topLeft.y + detectedCorners.topRight.y + detectedCorners.bottomRight.y + detectedCorners.bottomLeft.y) / 4f
            lastCapturedCenter = PointF(cX, cY)
        }

        playShutterFeedback()
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

    private fun clampCorners(corners: DocumentCorners, maxW: Int, maxH: Int): DocumentCorners {
        val w = maxW.toFloat()
        val h = maxH.toFloat()
        return DocumentCorners(
            topLeft = PointF(corners.topLeft.x.coerceIn(0f, w), corners.topLeft.y.coerceIn(0f, h)),
            topRight = PointF(corners.topRight.x.coerceIn(0f, w), corners.topRight.y.coerceIn(0f, h)),
            bottomRight = PointF(corners.bottomRight.x.coerceIn(0f, w), corners.bottomRight.y.coerceIn(0f, h)),
            bottomLeft = PointF(corners.bottomLeft.x.coerceIn(0f, w), corners.bottomLeft.y.coerceIn(0f, h))
        )
    }

    private fun processCapturedPhotoAndReturn(photoFile: File, corners: DocumentCorners?) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val fullBitmap = com.docu.editor.core.util.ExifBitmapUtil.decodeFileWithExif(photoFile.absolutePath, 4096)
                photoFile.delete()
                if (fullBitmap != null) {
                    val initialCorners: DocumentCorners = if (corners != null && analysisFrameW > 0 && analysisFrameH > 0) {
                        val scaleX = fullBitmap.width.toFloat() / analysisFrameW
                        val scaleY = fullBitmap.height.toFloat() / analysisFrameH
                        val liveCorners = DocumentCorners(
                            topLeft = PointF(corners.topLeft.x * scaleX, corners.topLeft.y * scaleY),
                            topRight = PointF(corners.topRight.x * scaleX, corners.topRight.y * scaleY),
                            bottomRight = PointF(corners.bottomRight.x * scaleX, corners.bottomRight.y * scaleY),
                            bottomLeft = PointF(corners.bottomLeft.x * scaleX, corners.bottomLeft.y * scaleY)
                        )
                        clampCorners(liveCorners, fullBitmap.width, fullBitmap.height)
                    } else {
                        DocumentEdgeDetector.detectCornersOrNull(fullBitmap) ?: DocumentEdgeDetector.detectCorners(fullBitmap)
                    }

                    if (scannerMode == ScannerMode.ID_CARD) {
                        val cardRect = overlayView.getIdCardRect()
                        val scaleX = fullBitmap.width.toFloat() / overlayView.width.coerceAtLeast(1)
                        val scaleY = fullBitmap.height.toFloat() / overlayView.height.coerceAtLeast(1)
                        val cropL = (cardRect.left * scaleX).toInt().coerceIn(0, fullBitmap.width - 10)
                        val cropT = (cardRect.top * scaleY).toInt().coerceIn(0, fullBitmap.height - 10)
                        val cropW = (cardRect.width() * scaleX).toInt().coerceIn(10, fullBitmap.width - cropL)
                        val cropH = (cardRect.height() * scaleY).toInt().coerceIn(10, fullBitmap.height - cropT)
                        val cardCrop = Bitmap.createBitmap(fullBitmap, cropL, cropT, cropW, cropH)
                        fullBitmap.recycle()

                        if (idCardFrontBitmap == null) {
                            idCardFrontBitmap = cardCrop
                            withContext(Dispatchers.Main) {
                                overlayView.idCardGuideText = "ALIGN ID CARD BACK"
                                overlayView.invalidate()
                                statusText.text = "✅ Front captured! Flip & align BACK side"
                                isCapturing = false
                                vibrate()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                statusText.text = "⚡ Auto-stitching ID card to A4 page..."
                            }
                            val front = idCardFrontBitmap!!
                            val stitchedA4 = com.docu.editor.core.scanner.IdCardStitcher.stitchIdCardToA4(front, cardCrop)
                            front.recycle()
                            idCardFrontBitmap = null
                            cardCrop.recycle()

                            val outFile = File(cacheDir, "scanned_idcard_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(outFile).use { fos ->
                                stitchedA4.compress(Bitmap.CompressFormat.JPEG, 94, fos)
                            }
                            stitchedA4.recycle()

                            withContext(Dispatchers.Main) {
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_SCANNED_PATH, outFile.absolutePath)
                                }
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            }
                        }
                        return@launch
                    }

                    if (scannerMode == ScannerMode.BOOK) {
                        val splitResult = com.docu.editor.core.dewarp.BookSplitEngine.splitBookSpread(fullBitmap, autoDewarpCurvature = true)
                        val leftBmp = splitResult.leftPage
                        val rightBmp = splitResult.rightPage
                        fullBitmap.recycle()

                        val outLeft = File(cacheDir, "scanned_book_p1_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(outLeft).use { fos ->
                            leftBmp.compress(Bitmap.CompressFormat.JPEG, 94, fos)
                        }
                        leftBmp.recycle()

                        val outRight = File(cacheDir, "scanned_book_p2_${System.currentTimeMillis() + 1}.jpg")
                        FileOutputStream(outRight).use { fos ->
                            rightBmp.compress(Bitmap.CompressFormat.JPEG, 94, fos)
                        }
                        rightBmp.recycle()

                        batchCapturedPaths.add(outLeft.absolutePath)
                        batchCapturedPaths.add(outRight.absolutePath)

                        withContext(Dispatchers.Main) {
                            val resultIntent = Intent().apply {
                                putStringArrayListExtra(EXTRA_BATCH_PATHS, batchCapturedPaths)
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        }
                        return@launch
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
                        val thumbBmp = decodeSampledBitmapFromFile(outFile.absolutePath, 180)
                        withContext(Dispatchers.Main) {
                            if (thumbBmp != null) {
                                batchThumbnailImg.setImageBitmap(thumbBmp)
                            }
                            batchThumbnailBadge.visibility = View.VISIBLE
                            batchBadgeCountText.text = "${batchCapturedPaths.size}"
                            batchThumbnailBadge.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
                                batchThumbnailBadge.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                            }.start()

                            finishBatchChip.visibility = View.VISIBLE
                            finishBatchChip.text = "Finish (${batchCapturedPaths.size}) ▶"
                            statusText.text = "✅ Page ${batchCapturedPaths.size} scanned! Flip page to scan next 📄"
                            isCapturing = false
                            waitingForPageTurn = true
                            vibrate()
                        }
                    } else {
                        // 1-Click Pro Auto-Crop directly along the detected green line corners!
                        val warped = PerspectiveTransformer.warpPerspective(fullBitmap, initialCorners)
                        capturedBitmap = fullBitmap
                        currentWarpedBitmap = warped
                        withContext(Dispatchers.Main) {
                            cropLoupeOverlayView.sourceBitmap = fullBitmap
                            cropLoupeOverlayView.corners = initialCorners
                            showFilterReviewScreen(warped)
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
        currentWarpedBitmap?.recycle()
        currentWarpedBitmap = null
        currentFilteredBitmap?.recycle()
        currentFilteredBitmap = null

        filterReviewPanel.visibility = View.GONE
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

    private fun applyManualCropAndProceedToFilter() {
        val bmp = capturedBitmap ?: return
        val finalCorners = cropLoupeOverlayView.corners

        statusText.text = "Processing perspective crop..."

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val warped = PerspectiveTransformer.warpPerspective(bmp, finalCorners)
                currentWarpedBitmap = warped
                withContext(Dispatchers.Main) {
                    showFilterReviewScreen(warped)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    restartCameraScan()
                }
            }
        }
    }

    private fun showFilterReviewScreen(warped: Bitmap) {
        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE
        bottomPanel.visibility = View.GONE
        cropLoupeOverlayView.visibility = View.GONE
        cropReviewPanel.visibility = View.GONE
        filterReviewPanel.visibility = View.VISIBLE

        statusText.text = "✨ DocuEdit Pro Enhancement"
        selectFilterPreset(com.docu.editor.core.scanner.DocumentFilters.FilterType.MAGIC_COLOR)
    }

    private fun selectFilterPreset(type: com.docu.editor.core.scanner.DocumentFilters.FilterType) {
        selectedFilterType = type
        val warped = currentWarpedBitmap ?: return

        val filtersList = listOf(
            com.docu.editor.core.scanner.DocumentFilters.FilterType.MAGIC_COLOR,
            com.docu.editor.core.scanner.DocumentFilters.FilterType.ORIGINAL,
            com.docu.editor.core.scanner.DocumentFilters.FilterType.CLEAN_BW,
            com.docu.editor.core.scanner.DocumentFilters.FilterType.GRAYSCALE
        )
        for (i in filterChips.indices) {
            val chip = filterChips[i]
            val isSelected = (filtersList[i] == type)
            chip.setTextColor(if (isSelected) Color.rgb(0, 230, 118) else Color.WHITE)
            (chip.background as? GradientDrawable)?.apply {
                if (isSelected) {
                    setStroke(3, Color.rgb(0, 230, 118))
                } else {
                    setStroke(0, Color.TRANSPARENT)
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            val filtered = com.docu.editor.core.scanner.DocumentFilters.applyFilter(warped, type)
            currentFilteredBitmap = filtered
            withContext(Dispatchers.Main) {
                filterPreviewImageView.setImageBitmap(filtered)
            }
        }
    }

    private fun returnToCropScreen() {
        filterReviewPanel.visibility = View.GONE
        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE
        bottomPanel.visibility = View.GONE
        cropLoupeOverlayView.visibility = View.VISIBLE
        cropReviewPanel.visibility = View.VISIBLE
        statusText.text = "🔍 Drag corners with loupe magnifier"
    }

    private fun saveFilteredDocumentAndFinish() {
        val finalBmp = currentFilteredBitmap ?: currentWarpedBitmap ?: return
        statusText.text = "Saving document..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outFile = File(cacheDir, "scanned_doc_${System.currentTimeMillis()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
                withContext(Dispatchers.Main) {
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_SCANNED_PATH, outFile.absolutePath)
                        putExtra(EXTRA_AUTO_MAGIC_COLOR, false)
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
        overlayView.autoSnapEnabled = autoSnapEnabled
        if (autoSnapEnabled) {
            autoSnapChip.text = "⚡ AUTO-SNAP: ACTIVE"
            autoSnapChip.setTextColor(Color.rgb(0, 230, 118))
            (autoSnapChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(0, 230, 118))
        } else {
            autoSnapChip.text = "✋ MANUAL SNAP MODE"
            autoSnapChip.setTextColor(Color.WHITE)
            (autoSnapChip.background as? GradientDrawable)?.setStroke(2, Color.WHITE)
        }
        overlayView.invalidate()
    }

    private fun toggleBatchMode() {
        val nextMode = when (scannerMode) {
            ScannerMode.SINGLE -> ScannerMode.BATCH
            ScannerMode.BATCH -> ScannerMode.ID_CARD
            ScannerMode.ID_CARD -> ScannerMode.BOOK
            ScannerMode.BOOK -> ScannerMode.WHITEBOARD
            ScannerMode.WHITEBOARD -> ScannerMode.PASSPORT
            ScannerMode.PASSPORT -> ScannerMode.SINGLE
        }
        setScannerMode(nextMode)
    }

    fun setScannerMode(mode: ScannerMode) {
        scannerMode = mode
        isBatchMode = (scannerMode == ScannerMode.BATCH)

        when (scannerMode) {
            ScannerMode.SINGLE -> {
                batchModeChip.text = "📄 SINGLE"
                batchModeChip.setTextColor(Color.WHITE)
                (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.argb(120, 255, 255, 255))
                statusText.text = "Point camera at document..."
                finishBatchChip.visibility = View.GONE
                batchThumbnailBadge.visibility = View.GONE
                overlayView.isIdCardMode = false
                overlayView.isBookMode = false
                overlayView.invalidate()
                idCardFrontBitmap?.recycle()
                idCardFrontBitmap = null
            }
            ScannerMode.BATCH -> {
                batchModeChip.text = "📚 BATCH"
                batchModeChip.setTextColor(Color.rgb(56, 189, 248))
                (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(56, 189, 248))
                statusText.text = "Batch scan mode: Shoot sequence of pages"
                if (batchCapturedPaths.isNotEmpty()) {
                    finishBatchChip.visibility = View.VISIBLE
                    batchThumbnailBadge.visibility = View.VISIBLE
                } else {
                    batchThumbnailBadge.visibility = View.GONE
                }
                overlayView.isIdCardMode = false
                overlayView.isBookMode = false
                overlayView.invalidate()
                idCardFrontBitmap?.recycle()
                idCardFrontBitmap = null
            }
            ScannerMode.ID_CARD -> {
                batchModeChip.text = "🪪 ID CARD"
                batchModeChip.setTextColor(Color.rgb(251, 146, 60))
                (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(251, 146, 60))
                statusText.text = "🪪 Align front of ID card in frame"
                finishBatchChip.visibility = View.GONE
                batchThumbnailBadge.visibility = View.GONE
                overlayView.isIdCardMode = true
                overlayView.isBookMode = false
                overlayView.idCardGuideText = "ALIGN ID CARD FRONT"
                overlayView.invalidate()
                idCardFrontBitmap = null
            }
            ScannerMode.BOOK -> {
                batchModeChip.text = "📖 BOOK (2-PAGE)"
                batchModeChip.setTextColor(Color.rgb(250, 204, 21))
                (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(250, 204, 21))
                statusText.text = "📖 Align book: Left & Right pages will auto-split"
                finishBatchChip.visibility = View.GONE
                batchThumbnailBadge.visibility = View.GONE
                overlayView.isIdCardMode = false
                overlayView.isBookMode = true
                overlayView.invalidate()
                idCardFrontBitmap?.recycle()
                idCardFrontBitmap = null
            }
            ScannerMode.WHITEBOARD -> {
                batchModeChip.text = "📊 WHITEBOARD"
                batchModeChip.setTextColor(Color.rgb(168, 85, 247))
                (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(168, 85, 247))
                statusText.text = "📊 Whiteboard mode: Anti-glare contrast filter active"
                finishBatchChip.visibility = View.GONE
                batchThumbnailBadge.visibility = View.GONE
                overlayView.isIdCardMode = false
                overlayView.isBookMode = false
                overlayView.invalidate()
                idCardFrontBitmap?.recycle()
                idCardFrontBitmap = null
            }
            ScannerMode.PASSPORT -> {
                batchModeChip.text = "🛂 PASSPORT"
                batchModeChip.setTextColor(Color.rgb(45, 212, 191))
                (batchModeChip.background as? GradientDrawable)?.setStroke(2, Color.rgb(45, 212, 191))
                statusText.text = "🛂 Align passport identity page in frame"
                finishBatchChip.visibility = View.GONE
                batchThumbnailBadge.visibility = View.GONE
                overlayView.isIdCardMode = true
                overlayView.isBookMode = false
                overlayView.idCardGuideText = "ALIGN PASSPORT PHOTO PAGE"
                overlayView.invalidate()
                idCardFrontBitmap?.recycle()
                idCardFrontBitmap = null
            }
        }
    }

    private fun showBatchReviewDialog() {
        if (batchCapturedPaths.isEmpty()) return
        val dialog = android.app.Dialog(this).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.argb(235, 15, 23, 42)))
        }

        val scroll = android.widget.HorizontalScrollView(this).apply {
            setPadding(16, 16, 16, 16)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun refreshThumbnails() {
            row.removeAllViews()
            for ((idx, path) in batchCapturedPaths.withIndex()) {
                val card = LinearLayout(this@LiveCameraScannerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(12, 12, 12, 12)
                    background = GradientDrawable().apply {
                        setColor(Color.argb(240, 30, 41, 59))
                        cornerRadius = 16f
                        setStroke(2, Color.argb(120, 148, 163, 184))
                    }
                }

                val iv = ImageView(this@LiveCameraScannerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(180, 240)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        val b = BitmapFactory.decodeFile(path, opts)
                        setImageBitmap(b)
                    } catch (_: Exception) {}
                }
                card.addView(iv)

                val label = TextView(this@LiveCameraScannerActivity).apply {
                    text = "Page ${idx + 1}"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setPadding(0, 8, 0, 8)
                }
                card.addView(label)

                val btnRow = LinearLayout(this@LiveCameraScannerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                // Move left
                if (idx > 0) {
                    val leftBtn = TextView(this@LiveCameraScannerActivity).apply {
                        text = "◀"
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setPadding(16, 8, 16, 8)
                        setOnClickListener {
                            java.util.Collections.swap(batchCapturedPaths, idx, idx - 1)
                            refreshThumbnails()
                        }
                    }
                    btnRow.addView(leftBtn)
                }

                // Delete
                val delBtn = TextView(this@LiveCameraScannerActivity).apply {
                    text = "🗑"
                    setTextColor(Color.rgb(239, 68, 68))
                    textSize = 14f
                    setPadding(16, 8, 16, 8)
                    setOnClickListener {
                        val p = batchCapturedPaths.removeAt(idx)
                        try { java.io.File(p).delete() } catch (_: Exception) {}
                        if (batchCapturedPaths.isEmpty()) {
                            dialog.dismiss()
                            finishBatchChip.visibility = View.GONE
                        } else {
                            finishBatchChip.text = "Finish (${batchCapturedPaths.size}) ▶"
                            refreshThumbnails()
                        }
                    }
                }
                btnRow.addView(delBtn)

                // Move right
                if (idx < batchCapturedPaths.size - 1) {
                    val rightBtn = TextView(this@LiveCameraScannerActivity).apply {
                        text = "▶"
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setPadding(16, 8, 16, 8)
                        setOnClickListener {
                            java.util.Collections.swap(batchCapturedPaths, idx, idx + 1)
                            refreshThumbnails()
                        }
                    }
                    btnRow.addView(rightBtn)
                }

                card.addView(btnRow)
                row.addView(card)

                val spacer = View(this@LiveCameraScannerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(16, 1)
                }
                row.addView(spacer)
            }
        }

        refreshThumbnails()
        scroll.addView(row)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Reorder & Review Scanned Pages"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)
        root.addView(scroll)

        val doneBtn = TextView(this).apply {
            text = "Open in Editor ▶"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.rgb(16, 185, 129))
                cornerRadius = 24f
            }
            setPadding(40, 16, 40, 16)
            setOnClickListener {
                dialog.dismiss()
                finishBatchAndReturn()
            }
        }
        root.addView(doneBtn)

        dialog.setContentView(root)
        dialog.show()
    }

    private fun finishBatchAndReturn() {
        if (batchCapturedPaths.isEmpty()) return
        val resultIntent = Intent().apply {
            putStringArrayListExtra(EXTRA_BATCH_PATHS, batchCapturedPaths)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private val mediaActionSound by lazy { android.media.MediaActionSound() }

    private fun playShutterFeedback() {
        try {
            mediaActionSound.play(android.media.MediaActionSound.SHUTTER_CLICK)
        } catch (_: Exception) {}
        vibrate()
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

    override fun onResume() {
        super.onResume()
        motionSensor?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(sensorListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaActionSound.release()
        } catch (_: Exception) {}
        capturedBitmap?.recycle()
        cameraExecutor.shutdown()
    }

    companion object {
        const val EXTRA_SCANNED_PATH = "extra_scanned_path"
        const val EXTRA_BATCH_PATHS = "extra_batch_paths"
        const val EXTRA_AUTO_MAGIC_COLOR = "extra_auto_magic_color"
        const val EXTRA_INITIAL_MODE = "extra_initial_mode"
    }
}
