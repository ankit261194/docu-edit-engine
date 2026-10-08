package com.docu.editor.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
import com.docu.editor.core.util.DocuStorageUtil
import com.docu.editor.core.update.ApkDownloadInstaller
import com.docu.editor.core.update.AutoUpdateManager
import com.docu.editor.core.update.UpdateInfo
import com.docu.editor.domain.model.EditorToolMode
import com.docu.editor.ui.canvas.DocumentBottomBar
import com.docu.editor.ui.canvas.DocumentInteractiveCanvas
import com.docu.editor.ui.canvas.ContextualLayerBottomBar
import com.docu.editor.ui.canvas.PageThumbnailStrip
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
import com.docu.editor.ui.dialogs.CloudBackupsListDialog
import com.docu.editor.ui.dialogs.TargetSizeAdjusterDialog
import com.docu.editor.ui.dialogs.DocumentFiltersSheet
import com.docu.editor.ui.dialogs.SizeAdjustMode
import com.docu.editor.ui.dialogs.PageSizeDialog
import com.docu.editor.ui.dialogs.DirectCloudUploadDialog
import com.docu.editor.ui.home.HomeScreenDashboard
import com.docu.editor.ui.dialogs.BatchResizeStudioDialog
import com.docu.editor.ui.dialogs.CountCamDialog
import com.docu.editor.ui.dialogs.IdPhotoMakerDialog
import com.docu.editor.ui.dialogs.ScanCodeDialog
import com.docu.editor.ui.dialogs.SolverAiDialog
import com.docu.editor.core.tools.CamScannerToolsEngine
import com.docu.editor.core.cloud.GeminiCloudAiClient
import com.docu.editor.core.pdf.PdfToolbox
import com.docu.editor.domain.model.DocumentFilterMode
import android.os.Environment
import android.widget.Toast
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
    private val updateCheckTrigger = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        updateCheckTrigger.intValue++
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)

        if (OpenCVLoader.initLocal()) {
            Log.i("MainActivity", "OpenCV loaded successfully via initLocal.")
        } else {
            @Suppress("DEPRECATION")
            if (OpenCVLoader.initDebug()) {
                Log.i("MainActivity", "OpenCV loaded successfully via initDebug.")
            } else {
                Log.e("MainActivity", "OpenCV failed to load.")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        try {
            if (Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) {
                intent.data?.let { uri ->
                    viewModel.loadDocumentUri(uri)
                }
            } else if (Intent.ACTION_SEND == action) {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    viewModel.loadDocumentUri(uri)
                } else {
                    intent.data?.let { viewModel.loadDocumentUri(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to handle incoming intent: ${e.message}")
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
                var showCountCamDialog by remember { mutableStateOf(false) }
                var countCamBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var showIdPhotoMakerDialog by remember { mutableStateOf(false) }
                var idPhotoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var showScanCodeDialog by remember { mutableStateOf(false) }
                var scanCodeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var showBatchResizeStudioDialog by remember { mutableStateOf(false) }
                var showSolverAiDialog by remember { mutableStateOf(false) }
                var solverAiQuestion by remember { mutableStateOf("") }

                LaunchedEffect(updateCheckTrigger.intValue) {
                    val info = updateManager.checkForUpdates()
                    if (info.hasUpdate) {
                        pendingUpdate = info
                    }
                }

                var pendingInitialToolMode by remember { mutableStateOf<com.docu.editor.domain.model.EditorToolMode?>(null) }
                var pendingInitialFilter by remember { mutableStateOf<com.docu.editor.domain.model.DocumentFilterMode?>(null) }
                var pendingOpenOcrExtract by remember { mutableStateOf(false) }

                // File Open Launcher
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        viewModel.loadDocumentUri(
                            it,
                            autoApplyMagicColor = (pendingInitialFilter == com.docu.editor.domain.model.DocumentFilterMode.MAGIC_COLOR),
                            initialToolMode = pendingInitialToolMode ?: com.docu.editor.domain.model.EditorToolMode.TEXT_EDIT,
                            initialFilter = pendingInitialFilter,
                            onLoaded = {
                                if (pendingOpenOcrExtract) {
                                    viewModel.showOcrTextExtractDialog(true)
                                    pendingOpenOcrExtract = false
                                }
                                if (pendingInitialToolMode == com.docu.editor.domain.model.EditorToolMode.FILTERS) {
                                    viewModel.showFiltersSheet(true)
                                }
                            }
                        )
                        pendingInitialToolMode = null
                        pendingInitialFilter = null
                    }
                }

                val countCamPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        if (bmp != null) {
                            countCamBitmap = bmp
                            showCountCamDialog = true
                        }
                    }
                }

                val idPhotoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        if (bmp != null) {
                            idPhotoBitmap = bmp
                            showIdPhotoMakerDialog = true
                        }
                    }
                }

                val scanCodePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        if (bmp != null) {
                            scanCodeBitmap = bmp
                            showScanCodeDialog = true
                        }
                    }
                }

                val mergeFilesPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.size >= 2) {
                        lifecycleScope.launch {
                            try {
                                Toast.makeText(this@MainActivity, "Merging ${uris.size} files...", Toast.LENGTH_SHORT).show()
                                val fileName = "Merged_Doc_${System.currentTimeMillis()}.pdf"
                                val outFile = File(cacheDir, fileName)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    PdfToolbox(applicationContext).mergeFiles(uris, outFile)
                                }
                                DocuStorageUtil.saveFileToPublicDownloads(
                                    this@MainActivity,
                                    outFile,
                                    fileName,
                                    "application/pdf"
                                )
                                Toast.makeText(this@MainActivity, "Merged successfully: $fileName (Saved to Downloads)", Toast.LENGTH_LONG).show()
                                viewModel.loadScannedDocument(outFile.absolutePath)
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Merge failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Select at least 2 files (PDF or images) to merge", Toast.LENGTH_SHORT).show()
                    }
                }

                val pdfToImagesPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        lifecycleScope.launch {
                            try {
                                Toast.makeText(this@MainActivity, "Extracting pages to images...", Toast.LENGTH_SHORT).show()
                                val outDir = File(cacheDir, "extracted_${System.currentTimeMillis()}").apply { mkdirs() }
                                val images = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    PdfToolbox(applicationContext).extractPagesAsImages(it, outDir)
                                }
                                var savedCount = 0
                                for (imgFile in images) {
                                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                                    if (bmp != null) {
                                        DocuStorageUtil.saveBitmapToGallery(this@MainActivity, bmp, imgFile.nameWithoutExtension)
                                        bmp.recycle()
                                        savedCount++
                                    }
                                }
                                Toast.makeText(this@MainActivity, "Extracted $savedCount pages saved to Gallery!", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Extraction failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val pdfToLongImagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        lifecycleScope.launch {
                            try {
                                Toast.makeText(this@MainActivity, "Stitching pages to long image...", Toast.LENGTH_SHORT).show()
                                val fileName = "Long_Image_${System.currentTimeMillis()}.jpg"
                                val outFile = File(cacheDir, fileName)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    CamScannerToolsEngine.stitchPdfToLongImage(applicationContext, it, outFile)
                                }
                                val bmp = BitmapFactory.decodeFile(outFile.absolutePath)
                                if (bmp != null) {
                                    DocuStorageUtil.saveBitmapToGallery(this@MainActivity, bmp, "Long_Image_${System.currentTimeMillis()}")
                                    bmp.recycle()
                                }
                                DocuStorageUtil.saveFileToPublicDownloads(this@MainActivity, outFile, fileName, "image/jpeg")
                                Toast.makeText(this@MainActivity, "Saved to Gallery & Downloads: $fileName", Toast.LENGTH_LONG).show()
                                viewModel.loadScannedDocument(outFile.absolutePath)
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Stitching failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val pptPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        lifecycleScope.launch {
                            try {
                                Toast.makeText(this@MainActivity, "Exporting slides to PPT...", Toast.LENGTH_SHORT).show()
                                val fileName = "Presentation_${System.currentTimeMillis()}.pptx"
                                val outFile = File(cacheDir, fileName)
                                val bmp = loadBitmapDirect(it)
                                if (bmp != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        CamScannerToolsEngine.exportPagesToPptx(listOf(bmp), outFile)
                                    }
                                    val pubUri = DocuStorageUtil.saveFileToPublicDownloads(
                                        this@MainActivity,
                                        outFile,
                                        fileName,
                                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                    )
                                    Toast.makeText(this@MainActivity, "PowerPoint deck saved: $fileName", Toast.LENGTH_LONG).show()
                                    if (pubUri != null) {
                                        DocuStorageUtil.openFileWithExternalApp(
                                            this@MainActivity,
                                            pubUri,
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "Open PowerPoint Presentation"
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "PPT export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Direct 1-Click Document to Word (.docx) Converter Launcher
                val directWordPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        lifecycleScope.launch {
                            try {
                                Toast.makeText(this@MainActivity, "Converting document to Word (.docx)...", Toast.LENGTH_SHORT).show()
                                val bmp = loadBitmapDirect(it)
                                if (bmp != null) {
                                    val ocrAnalyzer = com.docu.editor.core.ocr.OcrAnalyzer()
                                    val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE)
                                    }
                                    val fileName = "DocuEdit_Word_${System.currentTimeMillis()}.docx"
                                    val tempOut = File(cacheDir, fileName)
                                    val docText = com.docu.editor.core.export.DocxExportEngine.formatItemsToStructuredDocument(items).ifBlank { "Scanned Document Text" }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.docu.editor.core.export.DocxExportEngine.generateDocx("Converted Document", docText, tempOut)
                                    }
                                    val publicUri = DocuStorageUtil.saveFileToPublicDownloads(
                                        this@MainActivity,
                                        tempOut,
                                        fileName,
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                                    Toast.makeText(this@MainActivity, "Word document saved: $fileName", Toast.LENGTH_LONG).show()
                                    if (publicUri != null) {
                                        DocuStorageUtil.openFileWithExternalApp(
                                            this@MainActivity,
                                            publicUri,
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "Open Word Document"
                                        )
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "Unable to read document file", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Word conversion failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Direct 1-Click Document to Excel (.xlsx) Converter Launcher
                val directExcelPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        lifecycleScope.launch {
                            try {
                                Toast.makeText(this@MainActivity, "Converting document to Excel (.xlsx)...", Toast.LENGTH_SHORT).show()
                                val bmp = loadBitmapDirect(it)
                                if (bmp != null) {
                                    val ocrAnalyzer = com.docu.editor.core.ocr.OcrAnalyzer()
                                    val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        ocrAnalyzer.detectTextBlocks(bmp, com.docu.editor.core.ocr.model.TextHierarchyLevel.LINE)
                                    }
                                    val fileName = "DocuEdit_Excel_${System.currentTimeMillis()}.xlsx"
                                    val tempOut = File(cacheDir, fileName)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.docu.editor.core.export.SpreadsheetExportEngine.exportToXlsx(mapOf(0 to items), tempOut)
                                    }
                                    val publicUri = DocuStorageUtil.saveFileToPublicDownloads(
                                        this@MainActivity,
                                        tempOut,
                                        fileName,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    )
                                    Toast.makeText(this@MainActivity, "Excel spreadsheet saved: $fileName", Toast.LENGTH_LONG).show()
                                    if (publicUri != null) {
                                        DocuStorageUtil.openFileWithExternalApp(
                                            this@MainActivity,
                                            publicUri,
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "Open Excel Spreadsheet"
                                        )
                                    }
                                } else {
                                    Toast.makeText(this@MainActivity, "Unable to read document file", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Excel conversion failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // Dedicated PDF Tools Picker Launcher for Home Screen
                val pdfToolsPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        viewModel.loadDocumentUri(it)
                        viewModel.showPdfToolboxDialog(true)
                    }
                }

                // Dedicated 1-Click Book Dewarp Image Picker
                val directBookDewarpPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        viewModel.loadDocumentUri(it)
                        viewModel.showBookDewarpDialog(true)
                    }
                }

                // Bulk Batch OCR Multi-File Launcher
                val bulkBatchOcrPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris: List<Uri> ->
                    if (uris.isNotEmpty()) {
                        viewModel.processBatchOcrDocuments(uris)
                    }
                }

                // Append Page from Gallery Launcher
                val appendPageGalleryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { viewModel.appendPageFromUri(it) }
                }

                // Append Page from Camera Launcher
                val appendPageCameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val path = result.data?.getStringExtra(LiveCameraScannerActivity.EXTRA_SCANNED_PATH)
                        if (!path.isNullOrBlank()) {
                            val bmp = BitmapFactory.decodeFile(path)
                            if (bmp != null) {
                                viewModel.appendPageToDocument(bmp)
                            }
                        }
                    }
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

                var pendingScannerMode by remember { mutableStateOf<LiveCameraScannerActivity.ScannerMode?>(null) }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        val mode = pendingScannerMode ?: LiveCameraScannerActivity.ScannerMode.SINGLE
                        val intent = Intent(this@MainActivity, LiveCameraScannerActivity::class.java).apply {
                            putExtra(LiveCameraScannerActivity.EXTRA_INITIAL_MODE, mode.name)
                        }
                        liveScannerLauncher.launch(intent)
                        pendingScannerMode = null
                    }
                }

                val launchScannerWithMode: (LiveCameraScannerActivity.ScannerMode) -> Unit = { mode ->
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val intent = Intent(this@MainActivity, LiveCameraScannerActivity::class.java).apply {
                            putExtra(LiveCameraScannerActivity.EXTRA_INITIAL_MODE, mode.name)
                        }
                        liveScannerLauncher.launch(intent)
                    } else {
                        pendingScannerMode = mode
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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

                // Custom Font (.ttf / .otf) file picker
                val fontPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { viewModel.importCustomFont(it) }
                }

                // Canva Insert Image / Photo Layer Launcher
                val insertImageLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        bmp?.let { b ->
                            viewModel.setCustomOverlayImage(b)
                        }
                    }
                }

                // Canva Replace Image for Selected Layer Launcher
                val replaceImagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        val bmp = loadBitmapDirect(it)
                        bmp?.let { b ->
                            viewModel.replaceSelectedLayerImage(b)
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
                            val mime = when {
                                file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                                file.name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                file.name.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                file.name.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                file.name.endsWith(".csv", ignoreCase = true) -> "text/csv"
                                file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                                file.name.endsWith(".txt", ignoreCase = true) -> "text/plain"
                                else -> "image/jpeg"
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mime
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
                                        Text(
                                            "Find & Replace All",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier.clickable { viewModel.showRenameDialog(true) }
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = uiState.documentTitle.ifBlank { "DocuEdit Studio" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Rename Document",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
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
                                    if (uiState.activePdfUri != null) {
                                        IconButton(onClick = { viewModel.checkAcroFormsForCurrentPdf() }) {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = "Fill PDF Form",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    IconButton(onClick = { fontPickerLauncher.launch(arrayOf("*/*")) }) {
                                        Icon(
                                            Icons.Default.TextFields,
                                            contentDescription = "Import Font",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { viewModel.showPageSizeDialog(true) }) {
                                        Icon(
                                            Icons.Default.AspectRatio,
                                            contentDescription = "Page Dimensions",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { viewModel.showDirectCloudUploadDialog(true) }) {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            contentDescription = "1-Tap Cloud Hub",
                                            tint = Color(0xFF0284C7)
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
                            val activeLayer = uiState.selectedLayer
                            if (activeLayer != null) {
                                ContextualLayerBottomBar(
                                    layer = activeLayer,
                                    onUpdateShapeStyle = { fill, stroke, strokeW, cornerR, alpha ->
                                        viewModel.updateShapeLayerStyle(fill, stroke, strokeW, cornerR, alpha)
                                    },
                                    onUpdateTextStyle = { text, textColor, bgColor, fontSize, isBold, isItalic, fontFamily, alpha ->
                                        viewModel.updateTextLayerStyle(text, textColor, bgColor, fontSize, isBold, isItalic, fontFamily, alpha)
                                    },
                                    onUpdateLayerAlpha = { alpha ->
                                        viewModel.updateSelectedLayerAlpha(alpha)
                                    },
                                    onDuplicateLayer = {
                                        viewModel.duplicateSelectedLayer()
                                    },
                                    onDeleteLayer = {
                                        viewModel.deleteSelectedLayer()
                                    },
                                    onBringToFront = {
                                        viewModel.bringSelectedLayerToFront()
                                    },
                                    onSendToBack = {
                                        viewModel.sendSelectedLayerToBack()
                                    },
                                    onFlipH = {
                                        viewModel.toggleLayerFlipH(activeLayer.id)
                                    },
                                    onFlipV = {
                                        viewModel.toggleLayerFlipV(activeLayer.id)
                                    },
                                    onReplaceImageClicked = {
                                        replaceImagePickerLauncher.launch(arrayOf("image/*"))
                                    },
                                    onDeselect = {
                                        viewModel.selectCanvasLayer(null)
                                    }
                                )
                            } else {
                                DocumentBottomBar(
                                    activeMode = uiState.activeToolMode,
                                    activeFilter = uiState.activeFilter,
                                    showFiltersRow = showFiltersRow,
                                    onModeSelected = { mode ->
                                        if (mode == EditorToolMode.FILTERS) {
                                            viewModel.showFiltersSheet(true)
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
                                    onAutoOrientClicked = {
                                        viewModel.autoOrientCurrentDocument()
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
                                    onTargetSizeClicked = {
                                        viewModel.showTargetSizeAdjusterDialog(true)
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
                                    onRubberStampClicked = { viewModel.showRubberStampDialog(true) },
                                    onIdRedactionClicked = { viewModel.showIdRedactionDialog(true) },
                                    onBookSplitClicked = { viewModel.splitCurrentBookSpread() },
                                    onEraseMarksClicked = { viewModel.eraseMarks() },
                                    whiteoutBrushRadius = uiState.whiteoutBrushRadius,
                                    onWhiteoutBrushRadiusChanged = { r -> viewModel.setWhiteoutBrushRadius(r) },
                                    markupColorRgb = uiState.markupColorRgb,
                                    markupStrokeWidth = uiState.markupStrokeWidth,
                                    onMarkupColorChanged = { viewModel.setMarkupColor(it) },
                                    onMarkupStrokeWidthChanged = { viewModel.setMarkupStrokeWidth(it) },
                                    penColorRgb = uiState.penColorRgb,
                                    penStrokeWidth = uiState.penStrokeWidth,
                                    onPenColorChanged = { viewModel.setPenColor(it) },
                                    onPenStrokeWidthChanged = { viewModel.setPenStrokeWidth(it) },
                                    selectedShapeType = uiState.selectedShapeType,
                                    onShapeTypeSelected = { viewModel.setSelectedShapeType(it) },
                                    shapeStrokeWidth = uiState.shapeStrokeWidth,
                                    onShapeStrokeWidthChanged = { viewModel.setShapeStrokeWidth(it) },
                                    shapeStrokeColorRgb = uiState.shapeStrokeColorRgb,
                                    onShapeStrokeColorChanged = { viewModel.setShapeStrokeColor(it) },
                                    shapeFillColor = uiState.shapeFillColor,
                                    onShapeFillColorChanged = { viewModel.setShapeFillColor(it) },
                                    onAddShapeLayerClicked = { viewModel.addShapeLayer(it) },
                                    onBackgroundRemovalClicked = { viewModel.showBackgroundRemovalDialog(true) },
                                    onInsertImageClicked = { insertImageLauncher.launch(arrayOf("image/*")) },
                                    onCanvaStickersClicked = { viewModel.showCanvaStickersDialog(true) },
                                    magicEraserBrushRadius = uiState.magicEraserBrushRadius,
                                    onMagicEraserBrushRadiusChanged = { viewModel.setMagicEraserBrushRadius(it) },
                                    isCloudAiEraserEnabled = uiState.isCloudAiEraserEnabled,
                                    hasGeminiApiKey = viewModel.getGeminiApiKey().isNotBlank(),
                                    onToggleCloudAiEraser = { viewModel.toggleCloudAiEraser() },
                                    onOpenCloudAiSettings = { viewModel.showCloudAiSettingsDialog(true) },
                                    onApplyShadowRemover = { viewModel.applyFilter(com.docu.editor.domain.model.DocumentFilterMode.SHADOW_REMOVER) },
                                    onApplyFingerRemover = { viewModel.applyFilter(com.docu.editor.domain.model.DocumentFilterMode.FINGER_REMOVER) },
                                    onCanvaMockupsClicked = { viewModel.showCanvaMockupsDialog(true) },
                                    onCanvaTextStudioClicked = { viewModel.showCanvaTextStudioDialog(true) },
                                    onCanvaBrandKitClicked = { viewModel.showCanvaBrandKitDialog(true) },
                                    onCanvaMagicStudioClicked = { viewModel.showCanvaMagicStudioDialog(true) },
                                    onCanvaAdjustClicked = { viewModel.showCanvaAdjustDialog(true) },
                                    onCanvaAnimateClicked = { viewModel.showCanvaAnimateDialog(true) },
                                    onCanvaLayersClicked = { viewModel.showCanvaLayersDialog(true) },
                                    onPageDimensionsClicked = { viewModel.showPageSizeDialog(true) },
                                    onAcroFormClicked = { viewModel.checkAcroFormsForCurrentPdf() },
                                    onWordExportClicked = { viewModel.exportCurrentDocument("DOCX") },
                                    onPptxExportClicked = { viewModel.exportCurrentDocument("PPTX") },
                                    onExcelExportClicked = { viewModel.exportCurrentDocument("XLSX") },
                                    onPkiDigitalSignClicked = { viewModel.prepareAndLaunchPkiSign() },
                                    onCheckUpdateClicked = {
                                        lifecycleScope.launch {
                                            Toast.makeText(this@MainActivity, "Checking for latest updates...", Toast.LENGTH_SHORT).show()
                                            val info = updateManager.checkForUpdates()
                                            if (info.hasUpdate) {
                                                pendingUpdate = info
                                            } else {
                                                Toast.makeText(this@MainActivity, "You are on the latest version (v${info.currentVersion})", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Floating Search & Replace All Bar
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.isSearchActive,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 10.dp,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                var replaceText by remember { mutableStateOf("") }
                                var replaceAllPagesScope by remember { mutableStateOf(false) }
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = uiState.searchQuery,
                                            onValueChange = { viewModel.setSearchQuery(it) },
                                            placeholder = { Text("Find text...", fontSize = 13.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2563EB)
                                            )
                                        )
                                        OutlinedTextField(
                                            value = replaceText,
                                            onValueChange = { replaceText = it },
                                            placeholder = { Text("Replace with...", fontSize = 13.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF16A34A)
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (uiState.searchQuery.isNotEmpty()) "${uiState.searchMatchingIndices.size} match(es)" else "Type word to find",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (uiState.searchMatchingIndices.isNotEmpty()) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (uiState.pdfPageCount > 1) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable { replaceAllPagesScope = !replaceAllPagesScope }
                                                ) {
                                                    Checkbox(
                                                        checked = replaceAllPagesScope,
                                                        onCheckedChange = { replaceAllPagesScope = it },
                                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2563EB)),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "All ${uiState.pdfPageCount} pgs",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (replaceAllPagesScope) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (replaceAllPagesScope) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { viewModel.toggleSearch(false) }) {
                                                Text("Close", fontSize = 12.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.replaceAllOccurrences(uiState.searchQuery, replaceText, allPages = replaceAllPagesScope)
                                                    replaceText = ""
                                                },
                                                enabled = uiState.searchQuery.isNotBlank() && uiState.searchMatchingIndices.isNotEmpty(),
                                                colors = ButtonDefaults.buttonColors(containerColor = if (replaceAllPagesScope) Color(0xFF1D4ED8) else Color(0xFF2563EB)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text(
                                                    text = if (replaceAllPagesScope && uiState.pdfPageCount > 1) "Replace All Pages" else "Replace Page",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

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
                                onTextItemBoundsChanged = { item, newBounds -> viewModel.updateSelectedItemBounds(item, newBounds) },
                                onLassoSelectionChanged = { viewModel.setLassoSelection(it) },
                                onWhiteoutTouch = { x, y -> viewModel.applyWhiteoutCircle(x, y) },
                                onInsertTextTouch = { x, y -> viewModel.insertNewTextItem(x, y) },
                                activeOverlayBitmap = uiState.activeOverlayBitmap,
                                canvasLayers = uiState.canvasLayers,
                                selectedLayerId = uiState.selectedLayerId,
                                onSelectLayer = { viewModel.selectCanvasLayer(it) },
                                onDuplicateLayer = { viewModel.duplicateSelectedLayer() },
                                onDeleteLayer = { viewModel.deleteSelectedLayer() },
                                onBringLayerToFront = { viewModel.bringSelectedLayerToFront() },
                                onSendLayerToBack = { viewModel.sendSelectedLayerToBack() },
                                onLayerAlphaChanged = { viewModel.updateSelectedLayerAlpha(it) },
                                onEditTextLayer = { viewModel.openTextLayerDialog(it) },
                                onAddTextLayerClicked = { viewModel.openTextLayerDialog(null) },
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
                                onOpenPagesOverview = { viewModel.showPagesOverview(true) },
                                markupColorRgb = uiState.markupColorRgb,
                                markupStrokeWidth = uiState.markupStrokeWidth,
                                penColorRgb = uiState.penColorRgb,
                                penStrokeWidth = uiState.penStrokeWidth,
                                onCommitMarkupStroke = { pts, isHl ->
                                    val col = if (isHl) uiState.markupColorRgb else uiState.penColorRgb
                                    val w = if (isHl) uiState.markupStrokeWidth else uiState.penStrokeWidth
                                    viewModel.commitMarkupStroke(pts, col, w, isHl)
                                },
                                selectedShapeType = uiState.selectedShapeType,
                                shapeStrokeWidth = uiState.shapeStrokeWidth,
                                shapeStrokeColorRgb = uiState.shapeStrokeColorRgb,
                                onCommitShape = { type, start, end, col, w ->
                                    viewModel.commitShape(type, start, end, col, w)
                                },
                                onCommitBlackoutRect = { rectF ->
                                    viewModel.applyBlackoutRect(
                                        android.graphics.Rect(
                                            rectF.left.toInt(),
                                            rectF.top.toInt(),
                                            rectF.right.toInt(),
                                            rectF.bottom.toInt()
                                        )
                                    )
                                },
                                magicEraserBrushRadius = uiState.magicEraserBrushRadius,
                                onCommitMagicEraserStroke = { pts, radius ->
                                    viewModel.applyMagicObjectEraser(pts, radius)
                                }
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
                                            text = "Live Text: ${uiState.detectedItems.size} lines detected • Tap to edit",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Live Horizontal Multi-Page Thumbnail Strip & Range Selector (Phase 1)
                            if (uiState.pdfPageCount > 1) {
                                PageThumbnailStrip(
                                    pageCount = uiState.pdfPageCount,
                                    currentPageIndex = uiState.currentPdfPageIndex,
                                    pageThumbnails = viewModel.editedPagesMap,
                                    onSelectPage = { pageIdx ->
                                        viewModel.jumpToPage(pageIdx)
                                    },
                                    onAddPageClicked = {
                                        appendPageGalleryLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    },
                                    onDeleteSelectedPages = { pages ->
                                        pages.sortedDescending().forEach { viewModel.deletePage(it) }
                                    },
                                    onExportSelectedPages = { pages ->
                                        viewModel.exportCurrentDocument("PDF", pageIndices = pages.toList())
                                    },
                                    onRotateSelectedPages = { pages ->
                                        pages.forEach { viewModel.rotatePageAt(it) }
                                    },
                                    onOpenPagesOverview = {
                                        viewModel.showPagesOverview(true)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp)
                                )
                            }
                        } else {
                            // Premium CamScanner Home Dashboard
                            val cloudBackupsList = remember(uiState.canvasRevision) { viewModel.getCloudBackups() }
                            HomeScreenDashboard(
                                recentDocuments = recentDocs,
                                cloudBackups = cloudBackupsList,
                                onOpenSavedDocument = { path -> viewModel.loadScannedDocument(path) },
                                onDeleteRecentDocument = { id -> viewModel.deleteRecentDocument(id) },
                                onClearAllRecentDocuments = { viewModel.clearRecentDocuments() },
                                onUpdateCategory = { id, cat -> viewModel.updateDocumentCategory(id, cat) },
                                onRenameDocument = { id, title -> viewModel.renameDocument(id, title) },
                                onSaveToGoogleDrive = { doc -> viewModel.saveSavedDocumentToGoogleDrive(this@MainActivity, doc) },
                                onBackupToCloud = { doc -> viewModel.backupSavedDocumentToCloud(doc) },
                                onViewCloudBackupsClicked = { viewModel.showCloudBackupsListDialog(true) },
                                onMergeSelectedDocuments = { list -> viewModel.mergeMultipleDocumentsToPdf(list) },
                                onShareSelectedDocuments = { list -> shareMultipleSavedDocuments(list) },
                                onBackupSelectedDocuments = { list -> list.forEach { doc -> viewModel.backupSavedDocumentToCloud(doc) } },
                                onDeleteSelectedDocuments = { ids -> viewModel.deleteMultipleDocuments(ids) },
                                onBatchScanClicked = { launchScannerWithMode(LiveCameraScannerActivity.ScannerMode.BATCH) },
                                onBookScanClicked = { launchScannerWithMode(LiveCameraScannerActivity.ScannerMode.BOOK) },
                                onCameraScanClicked = {
                                    launchScannerWithMode(LiveCameraScannerActivity.ScannerMode.SINGLE)
                                },
                                onOpenFileClicked = {
                                    pendingInitialToolMode = com.docu.editor.domain.model.EditorToolMode.TEXT_EDIT
                                    pendingInitialFilter = null
                                    filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                },
                                onIdCardClicked = {
                                    viewModel.showIdCardDialog(true)
                                },
                                onSignatureClicked = {
                                    viewModel.showSignatureDialog(true)
                                },
                                onPdfToolsClicked = {
                                    if (uiState.currentBitmap == null) {
                                        pdfToolsPickerLauncher.launch(arrayOf("application/pdf"))
                                    } else {
                                        viewModel.showPdfToolboxDialog(true)
                                    }
                                },
                                onBulkBatchOcrClicked = {
                                    bulkBatchOcrPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                onBatchResizeClicked = {
                                    showBatchResizeStudioDialog = true
                                },
                                onTryDemoClicked = {
                                    viewModel.loadSampleDocument()
                                },
                                onCloudAiSettingsClicked = {
                                    viewModel.showCloudAiSettingsDialog(true)
                                },
                                onCheckUpdateClicked = {
                                    lifecycleScope.launch {
                                        Toast.makeText(this@MainActivity, "Checking for latest updates...", Toast.LENGTH_SHORT).show()
                                        val info = updateManager.checkForUpdates()
                                        if (info.hasUpdate) {
                                            pendingUpdate = info
                                        } else {
                                            Toast.makeText(this@MainActivity, "You are on the latest version (v${info.currentVersion})", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                hasGeminiApiKey = viewModel.getGeminiApiKey().isNotBlank(),
                                onConvertToWord = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.exportCurrentDocument("DOCX")
                                    } else {
                                        directWordPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    }
                                },
                                onConvertToExcel = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.exportCurrentDocument("XLSX")
                                    } else {
                                        directExcelPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    }
                                },
                                onConvertToPpt = {
                                    pptPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                },
                                onCountCam = {
                                    if (uiState.currentBitmap != null) {
                                        countCamBitmap = uiState.currentBitmap
                                        showCountCamDialog = true
                                    } else {
                                        countCamPickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                onPdfToImages = {
                                    pdfToImagesPickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                onPdfToLongImage = {
                                    pdfToLongImagePickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                onCamScannerAi = {
                                    solverAiQuestion = "Summarize document and extract key action items"
                                    showSolverAiDialog = true
                                },
                                onImportImages = {
                                    bulkBatchOcrPickerLauncher.launch(arrayOf("image/*"))
                                },
                                onImportFiles = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                onSign = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.showSignatureDialog(true)
                                    } else {
                                        signPicker.launch(arrayOf("image/*"))
                                    }
                                },
                                onAddWatermark = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.showWatermarkDialog(true)
                                    } else {
                                        pdfToolsPickerLauncher.launch(arrayOf("application/pdf"))
                                    }
                                },
                                onMergeFiles = {
                                    mergeFilesPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                onExtractPdfPages = {
                                    pdfToolsPickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                onReorderPages = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.showPagesOverview(true)
                                    } else {
                                        pdfToolsPickerLauncher.launch(arrayOf("application/pdf"))
                                    }
                                },
                                onLockPdf = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.showPdfToolboxDialog(true)
                                    } else {
                                        pdfToolsPickerLauncher.launch(arrayOf("application/pdf"))
                                    }
                                },
                                onEraseMarks = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.applyFilter(DocumentFilterMode.SHADOW_REMOVER)
                                    } else {
                                        pendingInitialToolMode = com.docu.editor.domain.model.EditorToolMode.MAGIC_ERASER
                                        pendingInitialFilter = DocumentFilterMode.SHADOW_REMOVER
                                        filePickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                onSmartErase = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.setActiveToolMode(EditorToolMode.MAGIC_ERASER)
                                    } else {
                                        pendingInitialToolMode = com.docu.editor.domain.model.EditorToolMode.MAGIC_ERASER
                                        pendingInitialFilter = null
                                        filePickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                onSolverAi = {
                                    solverAiQuestion = ""
                                    showSolverAiDialog = true
                                },
                                onEnhanceDocuments = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.showFiltersSheet(true)
                                    } else {
                                        pendingInitialToolMode = com.docu.editor.domain.model.EditorToolMode.FILTERS
                                        pendingInitialFilter = DocumentFilterMode.MAGIC_COLOR
                                        filePickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                onRestorePhoto = {
                                    if (uiState.currentBitmap != null) {
                                        val restored = CamScannerToolsEngine.restorePhoto(uiState.currentBitmap!!)
                                        viewModel.setEditedBitmap(restored)
                                        Toast.makeText(this@MainActivity, "Photo restored & enhanced!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pendingInitialToolMode = com.docu.editor.domain.model.EditorToolMode.FILTERS
                                        pendingInitialFilter = DocumentFilterMode.PHOTO_RESTORE
                                        filePickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                onScanIdCards = {
                                    viewModel.showIdCardDialog(true)
                                },
                                onExtractText = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.showOcrTextExtractDialog(true)
                                    } else {
                                        pendingOpenOcrExtract = true
                                        filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    }
                                },
                                onIdPhotoMaker = {
                                    if (uiState.currentBitmap != null) {
                                        idPhotoBitmap = uiState.currentBitmap
                                        showIdPhotoMakerDialog = true
                                    } else {
                                        idPhotoPickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                onScanToExcel = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.exportCurrentDocument("XLSX")
                                    } else {
                                        directExcelPickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    }
                                },
                                onFormulaOcr = {
                                    val detected = uiState.detectedItems.joinToString("\n") { it.text }
                                    solverAiQuestion = if (detected.isNotBlank()) "Format formula to LaTeX and solve: $detected" else "E = mc^2"
                                    showSolverAiDialog = true
                                },
                                onBookDewarp = {
                                    if (uiState.currentBitmap != null) {
                                        viewModel.showBookDewarpDialog(true)
                                    } else {
                                        directBookDewarpPickerLauncher.launch(arrayOf("image/*"))
                                    }
                                },
                                onSlidesScan = {
                                    launchScannerWithMode(LiveCameraScannerActivity.ScannerMode.SINGLE)
                                },
                                onWhiteboardScan = {
                                    launchScannerWithMode(LiveCameraScannerActivity.ScannerMode.WHITEBOARD)
                                },
                                onTimestampScan = {
                                    launchScannerWithMode(LiveCameraScannerActivity.ScannerMode.SINGLE)
                                },
                                onScanCode = {
                                    if (uiState.currentBitmap != null) {
                                        scanCodeBitmap = uiState.currentBitmap
                                        showScanCodeDialog = true
                                    } else {
                                        scanCodePickerLauncher.launch(arrayOf("image/*"))
                                    }
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
                                allDetectedItems = uiState.detectedItems,
                                onCopyText = { _ ->
                                    viewModel.quickCopyItemText(targetItem)
                                },
                                onQuickErase = {
                                    viewModel.quickEraseItem(targetItem)
                                },
                                onQuickHighlight = {
                                    viewModel.quickHighlightItem(targetItem)
                                },
                                onQuickBlackout = {
                                    viewModel.quickBlackoutItem(targetItem)
                                },
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
                                onDuplicatePage = { index ->
                                    viewModel.duplicatePage(index)
                                },
                                onDeleteMultiplePages = { indices ->
                                    viewModel.deleteMultiplePages(indices)
                                },
                                onMovePage = { from, to ->
                                    viewModel.movePage(from, to)
                                },
                                onRotatePage = { index ->
                                    viewModel.rotatePageAt(index, 90f)
                                },
                                onAddPageFromCamera = {
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        val intent = Intent(this@MainActivity, LiveCameraScannerActivity::class.java)
                                        appendPageCameraLauncher.launch(intent)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onAddPageFromGallery = {
                                    appendPageGalleryLauncher.launch(arrayOf("image/*", "application/pdf"))
                                },
                                onAddBlankPage = {
                                    viewModel.addBlankPage()
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
                                onStitchClicked = { layoutMode, purposeText ->
                                    viewModel.stitchIdCardToA4(layoutMode, purposeText)
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
                                onExtractStampTargetClicked = { target ->
                                    currentSignSourceBitmap?.let { viewModel.extractStampFromBitmap(it, target) }
                                },
                                onApplyToDocument = { bmp ->
                                    viewModel.startPlacingOverlay(bmp)
                                },
                                onDismiss = { viewModel.showSignatureDialog(false) }
                            )
                        }

                        // Export Format Dialog (Real PDF / JPG / PNG / Print / Google Drive / Hosting Cloud)
                        if (uiState.showExportDialog) {
                            ExportDialog(
                                totalPages = uiState.pdfPageCount,
                                currentPageIndex = uiState.currentPdfPageIndex,
                                onExportConfirmed = { format, fitToA4, customFileName, password, pageIndices ->
                                    viewModel.exportCurrentDocument(format, fitToA4, customFileName, password, pageIndices)
                                },
                                onSaveToGoogleDriveClicked = { format, fitToA4, customFileName ->
                                    viewModel.exportAndSaveToGoogleDrive(this@MainActivity, format, fitToA4, customFileName)
                                },
                                onSaveToHostingCloudClicked = { customFileName ->
                                    viewModel.syncDocumentToCloud(customFileName)
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

                        // Standard International Page Size Selector Dialog (Phase 1)
                        if (uiState.showPageSizeDialog) {
                            PageSizeDialog(
                                onApplyPageSize = { size, fitMode ->
                                    viewModel.applyStandardPageSize(size, fitMode)
                                },
                                onDismiss = { viewModel.showPageSizeDialog(false) }
                            )
                        }

                        // 1-Tap Direct Cloud Upload & Share Hub Dialog (Phase 1)
                        if (uiState.showDirectCloudUploadDialog) {
                            DirectCloudUploadDialog(
                                onUploadGoogleDrive = {
                                    viewModel.showDirectCloudUploadDialog(false)
                                    viewModel.exportAndSaveToGoogleDrive(this@MainActivity, "PDF")
                                },
                                onWebCloudSync = {
                                    viewModel.showDirectCloudUploadDialog(false)
                                    viewModel.syncDocumentToCloud()
                                },
                                onShareSocial = {
                                    viewModel.showDirectCloudUploadDialog(false)
                                    viewModel.exportCurrentDocument("PDF")
                                },
                                onDirectPrint = {
                                    viewModel.showDirectCloudUploadDialog(false)
                                    uiState.currentBitmap?.let { bmp ->
                                        PrintDocumentHelper.printBitmap(this@MainActivity, bmp, uiState.documentTitle)
                                    }
                                },
                                onDismiss = { viewModel.showDirectCloudUploadDialog(false) }
                            )
                        }

                        // PDF Toolbox Dialog
                        if (uiState.showPdfToolboxDialog) {
                            PdfToolboxDialog(
                                onCompressSelected = { dpi, quality ->
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.compressCurrentDocument(dpi, quality)
                                },
                                onPasswordProtectSelected = { userPass, ownerPass, canPrint, canExtract, canModify, canFillIn, keyLength ->
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.passwordProtectAndExport(userPass, ownerPass, canPrint, canExtract, canModify, canFillIn, keyLength)
                                },
                                onUnlockPdfSelected = { pass ->
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.unlockPasswordProtectedDocument(pass)
                                },
                                onPkiSignSelected = {
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.prepareAndLaunchPkiSign()
                                },
                                onSplitAllSelected = {
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.splitCurrentDocument()
                                },
                                onSplitByRangeSelected = { range ->
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.splitCurrentDocumentByRange(range)
                                },
                                onSplitIntoChunksSelected = { chunkSize ->
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.splitCurrentDocumentIntoChunks(chunkSize)
                                },
                                onExtractImagesSelected = { quality ->
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.extractPagesAsImages(quality)
                                },
                                onMergeFilesSelected = {
                                    viewModel.showPdfToolboxDialog(false)
                                    mergeFilesPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                onOpenPageStudioSelected = {
                                    viewModel.showPdfToolboxDialog(false)
                                    viewModel.showPagesOverview(true)
                                },
                                onDismiss = { viewModel.showPdfToolboxDialog(false) }
                            )
                        }

                        // Target Size Adjuster Dialog (Pi7 Algorithm)
                        if (uiState.showTargetSizeAdjusterDialog) {
                            TargetSizeAdjusterDialog(
                                initialFormat = if (uiState.activePdfUri != null || uiState.pdfPageCount > 1) "PDF" else "JPG",
                                onConfirmAdjust = { mode, targetKb, format ->
                                    viewModel.adjustDocumentToTargetSize(mode, targetKb, format)
                                },
                                onDismiss = { viewModel.showTargetSizeAdjusterDialog(false) }
                            )
                        }

                        // Legal PKI Cryptographic Digital Signature Dialog (Adobe Green Checkmark)
                        if (uiState.showPkiDigitalSignDialog && uiState.pendingSignedPdfFile != null) {
                            com.docu.editor.ui.dialogs.PkiDigitalSignDialog(
                                inputPdfFile = uiState.pendingSignedPdfFile!!,
                                pageCount = uiState.pdfPageCount,
                                onSignCompleted = { signedFile ->
                                    Toast.makeText(this@MainActivity, "Signed PDF saved: ${signedFile.name}", Toast.LENGTH_LONG).show()
                                },
                                onShareFile = { signedFile ->
                                    val shareUri = FileProvider.getUriForFile(
                                        this@MainActivity,
                                        "${applicationContext.packageName}.fileprovider",
                                        signedFile
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Digitally Signed PDF (Verified)")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(Intent.createChooser(shareIntent, "Share Signed PDF"))
                                },
                                onDismiss = { viewModel.showPkiDigitalSignDialog(false) }
                            )
                        }

                        // Anti-Counterfeiting Security Watermark & Bates Studio Dialog
                        if (uiState.showWatermarkDialog) {
                            WatermarkDialog(
                                pageCount = if (uiState.activePdfUri != null) uiState.pdfPageCount else uiState.batchScannedPaths.size.coerceAtLeast(1),
                                onApplyWatermark = { config, applyToAll ->
                                    viewModel.showWatermarkDialog(false)
                                    if (applyToAll) {
                                        viewModel.applyWatermarkToAllPages(config)
                                    } else {
                                        viewModel.applyWatermark(config)
                                    }
                                },
                                onApplyImageWatermark = { config, applyToAll ->
                                    viewModel.showWatermarkDialog(false)
                                    if (applyToAll) {
                                        viewModel.applyImageWatermarkToAllPages(config)
                                    } else {
                                        viewModel.applyImageWatermark(config)
                                    }
                                },
                                onApplyBatesNumbering = { config, applyToAll ->
                                    viewModel.showWatermarkDialog(false)
                                    if (applyToAll) {
                                        viewModel.applyBatesNumberingToAllPages(config)
                                    } else {
                                        viewModel.applyBatesNumbering(config)
                                    }
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

                        // Digital Rubber Stamp Maker Dialog
                        if (uiState.showRubberStampDialog) {
                            com.docu.editor.ui.dialogs.RubberStampDialog(
                                onDismiss = { viewModel.showRubberStampDialog(false) },
                                onApplyStamp = { config ->
                                    viewModel.addRubberStampLayer(config)
                                }
                            )
                        }

                        // Sensitive Government ID Auto-Redaction Dialog
                        if (uiState.showIdRedactionDialog) {
                            com.docu.editor.ui.dialogs.IdRedactionDialog(
                                onDismiss = { viewModel.showIdRedactionDialog(false) },
                                onApplyRedaction = { options ->
                                    viewModel.autoRedactSensitiveData(options)
                                }
                            )
                        }

                        // Enterprise Document Filters & Enhancement Studio Dialog
                        if (uiState.showFiltersSheet) {
                            DocumentFiltersSheet(
                                currentBitmap = uiState.currentBitmap,
                                originalBitmap = uiState.originalBitmap,
                                activeFilter = uiState.activeFilter,
                                pageCount = if (uiState.activePdfUri != null) uiState.pdfPageCount else uiState.batchScannedPaths.size.coerceAtLeast(1),
                                onFilterSelected = { filter, intensity ->
                                    viewModel.applyFilter(filter, intensity)
                                },
                                onApplyToAllPages = { filter, intensity ->
                                    viewModel.applyFilterToAllPages(filter, intensity)
                                },
                                onDismiss = { viewModel.showFiltersSheet(false) }
                            )
                        }

                        // Interactive AcroForm PDF Fillable Fields Dialog
                        if (uiState.showAcroFormDialog && uiState.acroFormFields.isNotEmpty()) {
                            com.docu.editor.ui.dialogs.AcroFormEditDialog(
                                formFields = uiState.acroFormFields,
                                onDismiss = { viewModel.dismissAcroFormDialog() },
                                onSaveFields = { fieldValues ->
                                    viewModel.saveAcroFormFields(fieldValues)
                                }
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
                                onOfflineHandwritingRequest = { onComplete ->
                                    viewModel.transcribeHandwritingOffline(onComplete)
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

                        // Hosting Cloud Backups List Dialog
                        if (uiState.showCloudBackupsListDialog) {
                            val backups = remember(uiState.canvasRevision) { viewModel.getCloudBackups() }
                            val syncKey = remember(uiState.canvasRevision) { viewModel.getCurrentSyncKey() }
                            val isAutoBackup = viewModel.isAutoCloudBackupEnabled()
                            val unsyncedCount = viewModel.getUnsyncedDocumentCount()
                            CloudBackupsListDialog(
                                backups = backups,
                                syncKey = syncKey,
                                isAutoBackupEnabled = isAutoBackup,
                                unsyncedCount = unsyncedCount,
                                onToggleAutoBackup = { viewModel.toggleAutoCloudBackup(it) },
                                onSyncAllUnsynced = { viewModel.syncAllUnsyncedDocumentsToCloud() },
                                onOpenUrl = { url ->
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {
                                        Toast.makeText(this@MainActivity, "Could not open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCopyUrl = { url ->
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", url))
                                    Toast.makeText(this@MainActivity, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteBackup = { docId ->
                                    viewModel.deleteCloudBackup(docId)
                                    Toast.makeText(this@MainActivity, "Backup deleted from cloud and list", Toast.LENGTH_SHORT).show()
                                },
                                onRestoreBackup = { item ->
                                    viewModel.restoreCloudDocumentToLibrary(this@MainActivity, item) { success, err ->
                                        if (success) {
                                            Toast.makeText(this@MainActivity, "'${item.title}' restored to Library", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "Restore failed: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onSetSyncKey = { key ->
                                    viewModel.setCustomSyncKey(key)
                                },
                                onRefreshCloud = {
                                    viewModel.fetchCloudBackupsFromServer { count ->
                                        Toast.makeText(this@MainActivity, "Cloud synchronized ($count documents)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDismiss = { viewModel.showCloudBackupsListDialog(false) }
                            )
                        }

                        // Auto-Update Dialog (100% In-App Download & Immediate PackageInstaller Prompt)
                        pendingUpdate?.let { updateInfo ->
                            UpdateDialog(
                                updateInfo = updateInfo,
                                onDismiss = { pendingUpdate = null },
                                onInstallLocalApk = { file ->
                                    apkInstaller.installApk(file)
                                },
                                onDownloadInApp = { url, name, onProg ->
                                    try {
                                        apkInstaller.downloadInAppStream(url, name, onProg)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            )
                        }

                        // Rename Document Dialog
                        if (uiState.showRenameDialog) {
                            var renameText by remember(uiState.documentTitle) { mutableStateOf(uiState.documentTitle) }
                            AlertDialog(
                                onDismissRequest = { viewModel.showRenameDialog(false) },
                                title = {
                                    Text(text = "Rename Document", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                },
                                text = {
                                    Column {
                                        Text(
                                            text = "Enter a new name for this document:",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = renameText,
                                            onValueChange = { renameText = it },
                                            singleLine = true,
                                            label = { Text("Document Name") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { viewModel.setDocumentTitle(renameText) }
                                    ) {
                                        Text("Rename")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { viewModel.showRenameDialog(false) }
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Canva Stickers / Badges Dialog
                        if (uiState.showCanvaStickersDialog) {
                            com.docu.editor.ui.dialogs.CanvaStickersDialog(
                                onBadgeSelected = { badgeBmp ->
                                    viewModel.setCustomOverlayImage(badgeBmp)
                                },
                                onDismiss = {
                                    viewModel.showCanvaStickersDialog(false)
                                }
                            )
                        }

                        // Canva Live Editable Typography Dialog
                        if (uiState.showEditTextLayerDialog) {
                            com.docu.editor.ui.dialogs.EditTextLayerDialog(
                                initialLayer = uiState.editingTextLayer,
                                onConfirm = { text, textColor, bgColor, fontSize, isBold, isItalic, fontFamily ->
                                    viewModel.addOrUpdateTextLayer(text, textColor, bgColor, fontSize, isBold, isItalic, fontFamily)
                                },
                                onDismiss = {
                                    viewModel.closeTextLayerDialog()
                                }
                            )
                        }

                        // Canva Pro 1-Click Background Removal Dialog
                        if (uiState.showBackgroundRemovalDialog) {
                            com.docu.editor.ui.dialogs.BackgroundRemovalDialog(
                                onApplyRemovalMode = { mode ->
                                    viewModel.applyOneClickBackgroundRemoval(mode)
                                },
                                onDismiss = {
                                    viewModel.showBackgroundRemovalDialog(false)
                                }
                            )
                        }

                        // Canva Pro Frames & 3D Mockups Dialog
                        if (uiState.showCanvaMockupsDialog) {
                            com.docu.editor.ui.dialogs.CanvaMockupsFramesDialog(
                                onFrameSelected = { frameType ->
                                    viewModel.applyCanvaFrame(frameType)
                                },
                                onDismiss = {
                                    viewModel.showCanvaMockupsDialog(false)
                                }
                            )
                        }

                        // Canva Pro Text Studio & Magic Write Dialog
                        if (uiState.showCanvaTextStudioDialog) {
                            com.docu.editor.ui.dialogs.CanvaTextStudioDialog(
                                onAddTextLayer = { text, colorRgb, fontSize, isBold, isItalic, fontFamily, effect ->
                                    viewModel.addStyledTextLayer(text, colorRgb, fontSize, isBold, isItalic, fontFamily, effect)
                                },
                                onDismiss = {
                                    viewModel.showCanvaTextStudioDialog(false)
                                }
                            )
                        }

                        // Canva Pro Brand Kit Dialog
                        if (uiState.showCanvaBrandKitDialog) {
                            com.docu.editor.ui.dialogs.CanvaBrandKitDialog(
                                currentPaletteId = uiState.activeBrandPaletteId,
                                onApplyPalette = { palette ->
                                    viewModel.applyBrandPalette(palette)
                                },
                                onDismiss = {
                                    viewModel.showCanvaBrandKitDialog(false)
                                }
                            )
                        }

                        // Canva Pro Magic Studio Dialog
                        if (uiState.showCanvaMagicStudioDialog) {
                            com.docu.editor.ui.dialogs.CanvaMagicStudioDialog(
                                onTriggerMagicEraser = { viewModel.setActiveToolMode(com.docu.editor.domain.model.EditorToolMode.MAGIC_ERASER) },
                                onTriggerBgRemover = { viewModel.showBackgroundRemovalDialog(true) },
                                onTriggerMagicGrab = {
                                    val bmp = uiState.currentBitmap
                                    if (bmp != null) {
                                        val box = android.graphics.RectF(bmp.width * 0.25f, bmp.height * 0.25f, bmp.width * 0.75f, bmp.height * 0.75f)
                                        viewModel.executeMagicGrab(box)
                                    }
                                },
                                onTriggerGrabText = { viewModel.executeGrabText() },
                                onTriggerFaceRetouch = { viewModel.executeFaceRetouch() },
                                onTriggerAutofocus = { viewModel.executeAutofocusBokeh() },
                                onTriggerUpscale = { viewModel.executeUpscaleSharpen() },
                                onTriggerMagicExpand = { viewModel.executeMagicExpand() },
                                onDismiss = { viewModel.showCanvaMagicStudioDialog(false) }
                            )
                        }

                        // Canva Pro Adjust Dialog
                        if (uiState.showCanvaAdjustDialog) {
                            com.docu.editor.ui.dialogs.CanvaAdjustDialog(
                                initialPreset = uiState.activeStyleMatchPreset,
                                onApplyAdjustments = { b, c, s, w, t, cl, v, bl, preset ->
                                    viewModel.applyCanvaAdjustments(b, c, s, w, t, cl, v, bl, preset)
                                },
                                onDismiss = {
                                    viewModel.showCanvaAdjustDialog(false)
                                }
                            )
                        }

                        // Canva Pro Animate Dialog
                        if (uiState.showCanvaAnimateDialog) {
                            com.docu.editor.ui.dialogs.CanvaAnimateDialog(
                                currentAnimation = uiState.activeAnimationType,
                                onApplyAnimation = { anim ->
                                    viewModel.applyCanvaAnimation(anim)
                                },
                                onDismiss = {
                                    viewModel.showCanvaAnimateDialog(false)
                                }
                            )
                        }

                        // Canva Pro Layers Dialog
                        if (uiState.showCanvaLayersDialog) {
                            com.docu.editor.ui.dialogs.CanvaLayersDialog(
                                layers = uiState.canvasLayers,
                                selectedLayerId = uiState.selectedLayerId,
                                onSelectLayer = { id -> viewModel.selectCanvasLayer(id) },
                                onMoveLayerUp = { id -> viewModel.moveLayerUp(id) },
                                onMoveLayerDown = { id -> viewModel.moveLayerDown(id) },
                                onBringToFront = { id -> viewModel.bringLayerToFront(id) },
                                onSendToBack = { id -> viewModel.sendLayerToBack(id) },
                                onToggleLock = { id -> viewModel.toggleLayerLock(id) },
                                onToggleFlipH = { id -> viewModel.toggleLayerFlipH(id) },
                                onToggleFlipV = { id -> viewModel.toggleLayerFlipV(id) },
                                onUpdateOpacity = { id, alpha -> viewModel.updateLayerOpacity(id, alpha) },
                                onDuplicateLayer = { id -> viewModel.duplicateLayer(id) },
                                onDeleteLayer = { id -> viewModel.deleteLayer(id) },
                                onDismiss = { viewModel.showCanvaLayersDialog(false) }
                            )
                        }

                        // CamScanner CountCam AI Dialog
                        if (showCountCamDialog) {
                            com.docu.editor.ui.dialogs.CountCamDialog(
                                initialBitmap = countCamBitmap ?: uiState.currentBitmap,
                                onPickImageFromGallery = {
                                    countCamPickerLauncher.launch(arrayOf("image/*"))
                                },
                                onSaveCountResult = { annotatedBmp, count ->
                                    viewModel.setEditedBitmap(annotatedBmp)
                                    showCountCamDialog = false
                                    Toast.makeText(this@MainActivity, "Count Cam: $count objects identified and loaded into editor", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = {
                                    showCountCamDialog = false
                                }
                            )
                        }

                        // Passport & ID Photo Maker Dialog
                        if (showIdPhotoMakerDialog) {
                            com.docu.editor.ui.dialogs.IdPhotoMakerDialog(
                                initialBitmap = idPhotoBitmap ?: uiState.currentBitmap,
                                onPickPhotoClicked = {
                                    idPhotoPickerLauncher.launch(arrayOf("image/*"))
                                },
                                onSaveToGallery = { bmp, prefix, targetKb ->
                                    lifecycleScope.launch {
                                        try {
                                            val time = System.currentTimeMillis()
                                            val tempFile = File(cacheDir, "${prefix}_$time.jpg")
                                            val finalBytes: Long = if (targetKb != null) {
                                                val res = com.docu.editor.core.export.TargetFileSizeEngine.compressBitmapToTargetKb(
                                                    bitmap = bmp,
                                                    targetKb = targetKb,
                                                    outputFile = tempFile
                                                )
                                                res.finalBytes
                                            } else {
                                                tempFile.outputStream().use {
                                                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 98, it)
                                                }
                                                tempFile.length()
                                            }
                                            val uri = DocuStorageUtil.saveImageFileToGallery(this@MainActivity, tempFile, "${prefix}_$time.jpg")
                                            if (uri != null) {
                                                val sizeStr = if (targetKb != null) " (${finalBytes / 1024} KB)" else ""
                                                Toast.makeText(this@MainActivity, "Saved to Gallery$sizeStr (DocuEdit album)!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "Failed to save photo to Gallery", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onExportPdf = { bmp, prefix ->
                                    lifecycleScope.launch {
                                        try {
                                            val time = System.currentTimeMillis()
                                            val tempPdf = File(cacheDir, "${prefix}_$time.pdf")
                                            com.docu.editor.core.pdf.PdfExportEngine.exportBitmapToPdf(
                                                bitmap = bmp,
                                                outputFile = tempPdf,
                                                fitToA4 = true
                                            )
                                            val uri = DocuStorageUtil.saveFileToPublicDownloads(
                                                this@MainActivity,
                                                tempPdf,
                                                "${prefix}_$time.pdf",
                                                "application/pdf"
                                            )
                                            if (uri != null) {
                                                Toast.makeText(this@MainActivity, "Printable PDF saved to Downloads!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "Failed to export PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "PDF Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onSharePrint = { bmp ->
                                    lifecycleScope.launch {
                                        try {
                                            val tempFile = File(cacheDir, "passport_share_${System.currentTimeMillis()}.jpg")
                                            tempFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 98, it) }
                                            val shareUri = DocuStorageUtil.getShareableUriForFile(this@MainActivity, tempFile)
                                            DocuStorageUtil.shareFile(this@MainActivity, shareUri, "image/jpeg", "Print / Share Passport Photo")
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Share failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onEditInCanvas = { bmp ->
                                    viewModel.setEditedBitmap(bmp)
                                    Toast.makeText(this@MainActivity, "Loaded into editor canvas", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = {
                                    showIdPhotoMakerDialog = false
                                }
                            )
                        }

                        // CamScanner Scan Code (QR / Barcode) Dialog
                        if (showScanCodeDialog) {
                            com.docu.editor.ui.dialogs.ScanCodeDialog(
                                initialBitmap = scanCodeBitmap ?: uiState.currentBitmap,
                                onPickImageClicked = {
                                    scanCodePickerLauncher.launch(arrayOf("image/*"))
                                },
                                onDismiss = {
                                    showScanCodeDialog = false
                                }
                            )
                        }

                        // CamScanner Solver AI Dialog
                        if (showSolverAiDialog) {
                            com.docu.editor.ui.dialogs.SolverAiDialog(
                                initialQuestion = solverAiQuestion,
                                onSolveRequested = { q ->
                                    try {
                                        com.docu.editor.core.cloud.GeminiCloudAiClient.solveQuestion(q, viewModel.getGeminiApiKey())
                                    } catch (e: Exception) {
                                        "Error solving problem: ${e.localizedMessage}"
                                    }
                                },
                                onDismiss = {
                                    showSolverAiDialog = false
                                }
                            )
                        }

                        // Batch Target Size Resizer & Converter Studio Dialog (Govt Exam Special)
                        if (showBatchResizeStudioDialog) {
                            BatchResizeStudioDialog(
                                onDismiss = {
                                    showBatchResizeStudioDialog = false
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


    private fun shareMultipleSavedDocuments(docs: List<com.docu.editor.core.history.SavedDocumentItem>) {
        if (docs.isEmpty()) return
        val uris = ArrayList<Uri>()
        for (doc in docs) {
            val file = File(doc.filePath)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
                uris.add(uri)
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "No valid files found to share", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = if (docs.first().filePath.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(Intent.createChooser(intent, "Share ${docs.size} Document(s)"))
    }

    private fun loadBitmapDirect(uri: Uri): android.graphics.Bitmap? {
        return try {
            com.docu.editor.core.util.ExifBitmapUtil.decodeUriWithExif(this, uri, 2880)
        } catch (e: Exception) {
            null
        }
    }
}
