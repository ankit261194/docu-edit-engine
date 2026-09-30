package com.docu.editor.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import com.docu.editor.ui.scanner.LiveCameraScannerActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.TextStyle
import com.docu.editor.core.print.PrintDocumentHelper
import com.docu.editor.ui.dialogs.ExitConfirmationDialog
import com.docu.editor.ui.dialogs.PagesOverviewDialog
import com.docu.editor.ui.dialogs.PdfPasswordPromptDialog
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
import com.docu.editor.ui.dialogs.ExportDialog
import com.docu.editor.ui.dialogs.IdCardDialog
import com.docu.editor.ui.dialogs.InteractiveCropDialog
import com.docu.editor.ui.dialogs.OcrTextExtractDialog
import com.docu.editor.ui.dialogs.CloudAiSettingsDialog
import com.docu.editor.ui.dialogs.PdfToolboxDialog
import com.docu.editor.ui.dialogs.SignatureDialog
import com.docu.editor.ui.dialogs.WatermarkDialog
import com.docu.editor.ui.dialogs.BookDewarpDialog
import com.docu.editor.ui.home.HomeScreenDashboard
import android.widget.Toast
import android.content.Context
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
            Log.i("MainActivity", "OpenCV loaded successfully via initLocal.")
        } else if (OpenCVLoader.initDebug()) {
            Log.i("MainActivity", "OpenCV loaded successfully via initDebug.")
        } else {
            Log.e("MainActivity", "OpenCV failed to load.")
        }

        setContent {
            DocuEditTheme {
                val uiState by viewModel.uiState.collectAsState()
                val recentDocs by viewModel.recentDocuments.collectAsState()
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

                // Enterprise Real-Time Live Scanner Launcher
                val liveScannerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val batchPaths = result.data?.getStringArrayListExtra(LiveCameraScannerActivity.EXTRA_BATCH_PATHS)
                        if (!batchPaths.isNullOrEmpty()) {
                            viewModel.loadBatchScannedPages(batchPaths)
                        } else {
                            val path = result.data?.getStringExtra(LiveCameraScannerActivity.EXTRA_SCANNED_PATH)
                            val autoMagic = result.data?.getBooleanExtra(LiveCameraScannerActivity.EXTRA_AUTO_MAGIC_COLOR, true) ?: true
                            if (!path.isNullOrBlank()) {
                                viewModel.loadScannedDocument(path, autoApplyMagicColor = autoMagic)
                            }
                        }
                    }
                }

                // Fallback Camera Photo Capture Launcher
                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success) {
                        tempCameraUri?.let { viewModel.loadDocumentUri(it, autoApplyMagicColor = true) }
                    }
                }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        val intent = Intent(this@MainActivity, LiveCameraScannerActivity::class.java)
                        liveScannerLauncher.launch(intent)
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

                BackHandler(enabled = uiState.currentBitmap != null) {
                    viewModel.closeActiveDocument()
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (uiState.currentBitmap != null) {
                            TopAppBar(
                                title = {
                                    if (uiState.isSearchActive) {
                                        BasicTextField(
                                            value = uiState.searchQuery,
                                            onValueChange = { viewModel.setSearchQuery(it) },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                                ) {
                                                    if (uiState.searchQuery.isEmpty()) {
                                                        Text(
                                                            "Search words in page...",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    } else {
                                        Column {
                                            Text(
                                                "DocuEdit Studio",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val subtext = if (uiState.pdfPageCount > 1) {
                                                "Page ${uiState.currentPdfPageIndex + 1}/${uiState.pdfPageCount} • ${uiState.detectedItems.size} blocks"
                                            } else {
                                                "${uiState.detectedItems.size} editable text blocks detected"
                                            }
                                            Text(
                                                subtext,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { viewModel.closeActiveDocument() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to Home",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                actions = {
                                    if (uiState.pdfPageCount > 1) {
                                        IconButton(onClick = { viewModel.showPagesOverview(true) }) {
                                            Icon(
                                                Icons.Default.Layers,
                                                contentDescription = "Pages Overview",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.toggleSearch(!uiState.isSearchActive) }) {
                                        Icon(
                                            if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = "Search Words",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.undo() },
                                        enabled = uiState.canUndo
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "Undo",
                                            tint = if (uiState.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.redo() },
                                        enabled = uiState.canRedo
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = "Redo",
                                            tint = if (uiState.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.showExportDialog(true) }
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
                                onBrightnessContrastChanged = { b, c ->
                                    viewModel.applyBrightnessContrast(b, c)
                                },
                                onRotateClicked = {
                                    viewModel.rotateDocumentClockwise()
                                },
                                onInteractiveCropClicked = {
                                    viewModel.showInteractiveCropDialog(true)
                                },
                                onExtractTextClicked = {
                                    viewModel.showOcrTextExtractDialog(true)
                                },
                                onSignatureClicked = {
                                    viewModel.showSignatureDialog(true)
                                },
                                onPagesOverviewClicked = {
                                    viewModel.showPagesOverview(true)
                                },
                                onCompressClicked = {
                                    viewModel.showPdfToolboxDialog(true)
                                },
                                onExportClicked = {
                                    viewModel.showExportDialog(true)
                                },
                                onCloudSyncClicked = {
                                    viewModel.syncDocumentToCloud()
                                },
                                selectedLassoCount = uiState.selectedItems.size,
                                onMergeEditLasso = { viewModel.mergeAndEditLassoSelection() },
                                onWhiteoutLasso = { viewModel.whiteoutLassoSelection() },
                                onClearLasso = { viewModel.clearLassoSelection() },
                                onWatermarkClicked = { viewModel.showWatermarkDialog(true) },
                                onBookDewarpClicked = { viewModel.showBookDewarpDialog(true) },
                                whiteoutBrushRadius = uiState.whiteoutBrushRadius,
                                onWhiteoutBrushRadiusChanged = { r -> viewModel.setWhiteoutBrushRadius(r) }
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
                                selectedItems = uiState.selectedItems,
                                activeMode = uiState.activeToolMode,
                                canvasRevision = uiState.canvasRevision,
                                onTextItemTapped = { viewModel.selectTextItem(it) },
                                onLassoSelectionChanged = { viewModel.setLassoSelection(it) },
                                onWhiteoutTouch = { x, y -> viewModel.applyWhiteoutCircle(x, y) },
                                onInsertTextTouch = { x, y -> viewModel.insertNewTextItem(x, y) },
                                activeOverlayBitmap = uiState.activeOverlayBitmap,
                                originalBitmap = uiState.originalBitmap,
                                overlayPositionX = uiState.overlayPositionX,
                                overlayPositionY = uiState.overlayPositionY,
                                overlayScale = uiState.overlayScale,
                                overlayRotation = uiState.overlayRotation,
                                onOverlayDragged = { dx, dy -> viewModel.updateOverlayPosition(dx, dy) },
                                onOverlayScaleChanged = { sm -> viewModel.updateOverlayScale(sm) },
                                onOverlayRotateChanged = { newRot -> viewModel.updateOverlayRotation(newRot) },
                                onCommitOverlay = { viewModel.commitOverlayToDocument() },
                                onCancelOverlay = { viewModel.cancelOverlay() },
                                searchMatchingIndices = uiState.searchMatchingIndices,
                                pdfPageCount = uiState.pdfPageCount,
                                currentPageIndex = uiState.currentPdfPageIndex,
                                onPreviousPage = { viewModel.previousPdfPage() },
                                onNextPage = { viewModel.nextPdfPage() },
                                onOpenPagesOverview = { viewModel.showPagesOverview(true) }
                            )

                            // CamScanner-Style Floating "Auto-Fetched Text" Badge
                            if (uiState.activeToolMode == EditorToolMode.TEXT_EDIT && uiState.detectedItems.isNotEmpty() && uiState.selectedItem == null) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xEE0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                    shadowElevation = 8.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoFixHigh,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "✨ ${uiState.detectedItems.size} lines auto-fetched • Tap any line to edit",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Premium CamScanner Home Dashboard
                            HomeScreenDashboard(
                                recentDocuments = recentDocs,
                                onOpenSavedDocument = { path -> viewModel.loadScannedDocument(path) },
                                onDeleteRecentDocument = { id -> viewModel.deleteRecentDocument(id) },
                                onClearAllRecentDocuments = { viewModel.clearRecentDocuments() },
                                onCameraScanClicked = {
                                    if (ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        val intent = Intent(this@MainActivity, LiveCameraScannerActivity::class.java)
                                        liveScannerLauncher.launch(intent)
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
                                },
                                onCloudAiSettingsClicked = {
                                    viewModel.showCloudAiSettingsDialog(true)
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
                                onApplyEdit = { newText, fontClassification, isBold, sizeMultiplier, colorRgb, alignment, useCloudAi ->
                                    viewModel.applyTextReplacement(
                                        targetItem = targetItem,
                                        newText = newText,
                                        fontClassification = fontClassification,
                                        isBold = isBold,
                                        sizeMultiplier = sizeMultiplier,
                                        colorOverrideRgb = colorRgb,
                                        alignment = alignment,
                                        useCloudAi = useCloudAi
                                    )
                                }
                            )
                        }

                        // Exit Confirmation Dialog
                        if (uiState.showExitConfirmationDialog) {
                            ExitConfirmationDialog(
                                onKeepEditing = { viewModel.showExitConfirmationDialog(false) },
                                onDiscard = {
                                    viewModel.showExitConfirmationDialog(false)
                                    viewModel.closeActiveDocumentImmediately()
                                },
                                onSaveAndExport = {
                                    viewModel.showExitConfirmationDialog(false)
                                    viewModel.showExportDialog(true)
                                }
                            )
                        }

                        // PDF Password Prompt Dialog
                        if (uiState.showPasswordPromptDialog) {
                            PdfPasswordPromptDialog(
                                onUnlock = { pass ->
                                    viewModel.unlockAndLoadPdf(pass)
                                },
                                onDismiss = { viewModel.dismissPasswordPrompt() }
                            )
                        }

                        // Pages Overview Dialog
                        if (uiState.showPagesOverviewDialog) {
                            PagesOverviewDialog(
                                pageCount = uiState.pdfPageCount,
                                currentPageIndex = uiState.currentPdfPageIndex,
                                pageThumbnails = viewModel.editedPagesMap,
                                onSelectPage = { index ->
                                    viewModel.showPagesOverview(false)
                                    viewModel.jumpToPage(index)
                                },
                                onDeletePage = { index ->
                                    viewModel.deletePage(index)
                                },
                                onMovePage = { from, to ->
                                    viewModel.movePage(from, to)
                                },
                                onDismiss = { viewModel.showPagesOverview(false) }
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
                                onApplyToDocument = { bmp ->
                                    viewModel.startPlacingOverlay(bmp)
                                },
                                onDismiss = { viewModel.showSignatureDialog(false) }
                            )
                        }

                        // Export Format Dialog (Real PDF / JPG / PNG / Print)
                        if (uiState.showExportDialog) {
                            ExportDialog(
                                onExportConfirmed = { format, fitToA4, customFileName ->
                                    viewModel.exportCurrentDocument(format, fitToA4, customFileName)
                                },
                                onPrintClicked = {
                                    val bmp = uiState.currentBitmap
                                    if (bmp != null) {
                                        PrintDocumentHelper.printBitmap(this@MainActivity, bmp)
                                    }
                                },
                                onDismiss = { viewModel.showExportDialog(false) }
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
                                    viewModel.passwordProtectAndExport(pass)
                                },
                                onDismiss = { viewModel.showPdfToolboxDialog(false) }
                            )
                        }

                        // Anti-Counterfeiting Security Watermark Dialog
                        if (uiState.showWatermarkDialog) {
                            WatermarkDialog(
                                onApplyWatermark = { config ->
                                    viewModel.showWatermarkDialog(false)
                                    viewModel.applyWatermark(config)
                                },
                                onDismiss = { viewModel.showWatermarkDialog(false) }
                            )
                        }

                        // AI Book Curve Dewarping Dialog
                        if (uiState.showBookDewarpDialog) {
                            BookDewarpDialog(
                                onApplyDewarp = { spine, intensity ->
                                    viewModel.showBookDewarpDialog(false)
                                    viewModel.applyBookDewarp(spine, intensity)
                                },
                                onDismiss = { viewModel.showBookDewarpDialog(false) }
                            )
                        }

                        // Interactive 4-Corner Perspective Crop with Loupe Magnifier (CamScanner)
                        val currentBmp = uiState.currentBitmap
                        if (uiState.showInteractiveCropDialog && currentBmp != null) {
                            InteractiveCropDialog(
                                sourceBitmap = currentBmp,
                                onApplyCrop = { corners ->
                                    viewModel.applyInteractiveCrop(corners)
                                },
                                onRotateClockwise = {
                                    viewModel.rotateDocumentClockwise()
                                },
                                onDismiss = { viewModel.showInteractiveCropDialog(false) }
                            )
                        }

                        // CamScanner OCR Text Extraction Dialog
                        if (uiState.showOcrTextExtractDialog) {
                            OcrTextExtractDialog(
                                detectedItems = uiState.detectedItems,
                                onCopyAll = { text ->
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("DocuEdit OCR", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Copied all text to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                onShareTxt = { text ->
                                    val path = viewModel.exportTextToFile(text)
                                    if (path != null) {
                                        val file = File(path)
                                        val shareUri = FileProvider.getUriForFile(
                                            this@MainActivity,
                                            "${applicationContext.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            putExtra(Intent.EXTRA_TEXT, text)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(Intent.createChooser(shareIntent, "Share Extracted Text"))
                                    }
                                },
                                onShareCsv = { csvContent ->
                                    val csvFile = File(cacheDir, "table_export_${System.currentTimeMillis()}.csv")
                                    try {
                                        csvFile.writeText(csvContent)
                                        val shareUri = FileProvider.getUriForFile(
                                            this@MainActivity,
                                            "${applicationContext.packageName}.fileprovider",
                                            csvFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Document Table Export (.csv)")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(Intent.createChooser(shareIntent, "Open in Excel / Sheets"))
                                    } catch (e: Exception) {
                                        Toast.makeText(this@MainActivity, "Export CSV error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShareDocx = { docxText ->
                                    val path = viewModel.exportDocxFile(docxText)
                                    if (path != null) {
                                        val file = File(path)
                                        val shareUri = FileProvider.getUriForFile(
                                            this@MainActivity,
                                            "${applicationContext.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Word Document (.docx)")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(Intent.createChooser(shareIntent, "Open in Word / Docs"))
                                    } else {
                                        Toast.makeText(this@MainActivity, "Failed to create .docx", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onHandwritingAiRequest = { onComplete ->
                                    viewModel.transcribeHandwritingWithAi(onComplete)
                                },
                                onDismiss = { viewModel.showOcrTextExtractDialog(false) }
                            )
                        }

                        // CamScanner Multi-Device Cloud Web Sync Dialog
                        if (uiState.showCloudSyncDialog && uiState.cloudSyncResult != null) {
                            com.docu.editor.ui.dialogs.CloudSyncDialog(
                                syncResult = uiState.cloudSyncResult!!,
                                onCopyLink = { url ->
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("DocuEdit Web Viewer", url)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(this@MainActivity, "Web Viewer link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                onOpenWebViewer = { url ->
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {
                                        Toast.makeText(this@MainActivity, "Could not open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShareLink = { url ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "View my scanned document online: $url")
                                        putExtra(Intent.EXTRA_SUBJECT, "DocuEdit Cloud Web Document")
                                    }
                                    startActivity(Intent.createChooser(shareIntent, "Share Web Document Link"))
                                },
                                onDismiss = { viewModel.showCloudSyncDialog(false) }
                            )
                        }

                        // Cloud AI & Hosting Settings Dialog
                        if (uiState.showCloudAiSettingsDialog) {
                            CloudAiSettingsDialog(
                                currentApiKey = viewModel.getGeminiApiKey(),
                                onSaveApiKey = { key ->
                                    viewModel.setGeminiApiKey(key)
                                    viewModel.showCloudAiSettingsDialog(false)
                                    Toast.makeText(this@MainActivity, "Cloud AI settings updated!", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = { viewModel.showCloudAiSettingsDialog(false) }
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
            com.docu.editor.core.util.ExifBitmapUtil.decodeUriWithExif(this, uri, 2880)
        } catch (e: Exception) {
            null
        }
    }
}
