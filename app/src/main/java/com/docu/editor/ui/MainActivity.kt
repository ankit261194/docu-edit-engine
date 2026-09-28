package com.docu.editor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.docu.editor.core.update.ApkDownloadInstaller
import com.docu.editor.core.update.AutoUpdateManager
import com.docu.editor.core.update.UpdateInfo
import com.docu.editor.domain.model.EditorToolMode
import com.docu.editor.ui.canvas.DocumentBottomBar
import com.docu.editor.ui.canvas.DocumentInteractiveCanvas
import com.docu.editor.ui.canvas.TextEditBottomSheet
import com.docu.editor.ui.dialogs.IdCardDialog
import com.docu.editor.ui.dialogs.PdfToolboxDialog
import com.docu.editor.ui.dialogs.SignatureDialog
import com.docu.editor.ui.home.HomeScreenDashboard
import com.docu.editor.ui.theme.DocuEditTheme
import com.docu.editor.ui.update.UpdateDialog
import com.docu.editor.ui.viewmodel.DocumentEditorViewModel
import org.opencv.android.OpenCVLoader
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: DocumentEditorViewModel by viewModels()
    private val updateManager by lazy {
        AutoUpdateManager(
            context = applicationContext,
            githubOwner = "ankit261194",
            githubRepo = "docu-edit-engine"
        )
    }
    private val apkInstaller by lazy { ApkDownloadInstaller(this) }

    private var tempCameraUri: Uri? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initLocal()) {
            Log.i("MainActivity", "OpenCV loaded successfully.")
        } else {
            Log.e("MainActivity", "OpenCV failed to load.")
        }

        setContent {
            DocuEditTheme {
                val uiState by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
                var showFiltersRow by remember { mutableStateOf(false) }
                var currentSignSourceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

                LaunchedEffect(Unit) {
                    val info = updateManager.checkForUpdates()
                    if (info.hasUpdate) {
                        pendingUpdate = info
                    }
                }

                // File Open Launcher
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { viewModel.loadDocumentUri(it) }
                }

                // Camera Photo Capture Launcher
                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success) {
                        tempCameraUri?.let { viewModel.loadDocumentUri(it) }
                    }
                }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        launchCameraCapture { uri ->
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    }
                }

                // ID Card image pickers
                val idCardFrontPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        bmp?.let { b -> viewModel.setIdCardFront(b) }
                    }
                }

                val idCardBackPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        bmp?.let { b -> viewModel.setIdCardBack(b) }
                    }
                }

                // Sign & Stamp picker
                val signPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        bmp?.let { b ->
                            currentSignSourceBitmap = b
                            viewModel.extractSignatureFromBitmap(b)
                        }
                    }
                }

                // Snackbar notifications
                LaunchedEffect(uiState.errorMessage) {
                    uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
                }
                LaunchedEffect(uiState.successMessage) {
                    uiState.successMessage?.let { snackbarHostState.showSnackbar(it) }
                }

                // Trigger Share Sheet on export
                LaunchedEffect(uiState.exportUri) {
                    uiState.exportUri?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val shareUri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "${applicationContext.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share Document"))
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (uiState.currentBitmap != null) {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            "DocuEdit Studio",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            "${uiState.detectedItems.size} editable text blocks detected",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { viewModel.closeActiveDocument() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to Home",
                                            tint = Color(0xFF0F172A)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.White
                                ),
                                actions = {
                                    IconButton(
                                        onClick = { viewModel.undo() },
                                        enabled = uiState.canUndo
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "Undo",
                                            tint = if (uiState.canUndo) Color(0xFF0F172A) else Color(0xFFCBD5E1)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.redo() },
                                        enabled = uiState.canRedo
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = "Redo",
                                            tint = if (uiState.canRedo) Color(0xFF0F172A) else Color(0xFFCBD5E1)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.exportCurrentDocument("JPG") }
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Save & Share",
                                            tint = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (uiState.currentBitmap != null) {
                            DocumentBottomBar(
                                activeMode = uiState.activeToolMode,
                                activeFilter = uiState.activeFilter,
                                showFiltersRow = showFiltersRow,
                                onModeSelected = { mode ->
                                    if (mode == EditorToolMode.FILTERS) {
                                        showFiltersRow = !showFiltersRow
                                    } else {
                                        showFiltersRow = false
                                        viewModel.setActiveToolMode(mode)
                                    }
                                },
                                onFilterSelected = { filter ->
                                    viewModel.applyFilter(filter)
                                },
                                onAutoCropClicked = {
                                    viewModel.applyAutoPerspectiveCrop()
                                },
                                onCompressClicked = {
                                    viewModel.showPdfToolboxDialog(true)
                                },
                                onExportClicked = {
                                    viewModel.exportCurrentDocument("JPG")
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val bitmap = uiState.currentBitmap
                        if (bitmap != null) {
                            // Active Interactive Document Canvas
                            DocumentInteractiveCanvas(
                                bitmap = bitmap,
                                detectedItems = uiState.detectedItems,
                                selectedItem = uiState.selectedItem,
                                activeMode = uiState.activeToolMode,
                                onTextItemTapped = { viewModel.selectTextItem(it) },
                                onWhiteoutTouch = { x, y -> viewModel.applyWhiteoutCircle(x, y) }
                            )
                        } else {
                            // Premium CamScanner Home Dashboard
                            HomeScreenDashboard(
                                onCameraScanClicked = {
                                    if (ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        launchCameraCapture { uri ->
                                            tempCameraUri = uri
                                            cameraLauncher.launch(uri)
                                        }
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onOpenFileClicked = {
                                    filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                },
                                onIdCardClicked = {
                                    viewModel.showIdCardDialog(true)
                                },
                                onSignatureClicked = {
                                    viewModel.showSignatureDialog(true)
                                },
                                onPdfToolsClicked = {
                                    viewModel.showPdfToolboxDialog(true)
                                },
                                onTryDemoClicked = {
                                    viewModel.loadSampleDocument()
                                }
                            )
                        }

                        // Ultra-Fast Non-Blocking Progress HUD
                        AnimatedVisibility(visible = uiState.isScanning || uiState.isApplyingEdit) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Black.copy(alpha = 0.55f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .background(Color(0xFF1E293B), RoundedCornerShape(18.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(38.dp),
                                            color = Color(0xFF38BDF8),
                                            strokeWidth = 3.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = uiState.processingMessage ?: "Processing...",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Bottom Inpainting Sheet
                        uiState.selectedItem?.let { targetItem ->
                            TextEditBottomSheet(
                                item = targetItem,
                                sheetState = sheetState,
                                onDismiss = { viewModel.selectTextItem(null) },
                                onApplyEdit = { newText, fontClassification, isBold, sizeMultiplier, colorRgb, useCloudAi ->
                                    viewModel.applyTextReplacement(
                                        targetItem = targetItem,
                                        newText = newText,
                                        fontClassification = fontClassification,
                                        isBold = isBold,
                                        sizeMultiplier = sizeMultiplier,
                                        colorOverrideRgb = colorRgb,
                                        useCloudAi = useCloudAi
                                    )
                                }
                            )
                        }

                        // ID Card Dialog
                        if (uiState.showIdCardDialog) {
                            IdCardDialog(
                                frontBitmap = uiState.idCardFrontBitmap,
                                backBitmap = uiState.idCardBackBitmap,
                                onPickFrontClicked = {
                                    idCardFrontPicker.launch(arrayOf("image/*"))
                                },
                                onPickBackClicked = {
                                    idCardBackPicker.launch(arrayOf("image/*"))
                                },
                                onStitchClicked = {
                                    viewModel.stitchIdCardToA4()
                                },
                                onDismiss = { viewModel.showIdCardDialog(false) }
                            )
                        }

                        // Signature & Stamp Dialog
                        if (uiState.showSignatureDialog) {
                            SignatureDialog(
                                extractedBitmap = uiState.extractedSignature,
                                onPickSourceImage = {
                                    signPicker.launch(arrayOf("image/*"))
                                },
                                onExtractSignatureClicked = { inkColor ->
                                    currentSignSourceBitmap?.let { viewModel.extractSignatureFromBitmap(it, inkColor) }
                                },
                                onExtractStampClicked = { isRed ->
                                    currentSignSourceBitmap?.let { viewModel.extractStampFromBitmap(it, isRed) }
                                },
                                onDismiss = { viewModel.showSignatureDialog(false) }
                            )
                        }

                        // PDF Toolbox Dialog
                        if (uiState.showPdfToolboxDialog) {
                            PdfToolboxDialog(
                                onCompressSelected = { dpi ->
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.compressCurrentDocument(dpi)
                                },
                                onPasswordProtectSelected = { pass ->
                                    viewModel.showPdfToolboxDialog(false)
                                },
                                onDismiss = { viewModel.showPdfToolboxDialog(false) }
                            )
                        }

                        // Auto-Update Dialog
                        pendingUpdate?.let { updateInfo ->
                            UpdateDialog(
                                updateInfo = updateInfo,
                                onDismiss = { pendingUpdate = null },
                                onConfirmUpdate = { downloadUrl, fileName ->
                                    pendingUpdate = null
                                    apkInstaller.startDownload(downloadUrl, fileName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun launchCameraCapture(onUriReady: (Uri) -> Unit) {
        val file = File(cacheDir, "camera_capture.jpg")
        if (file.exists()) file.delete()
        file.createNewFile()
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        onUriReady(uri)
    }

    private fun loadBitmapDirect(uri: Uri): android.graphics.Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }
}
