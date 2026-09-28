package com.docu.editor.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.unit.dp
import com.docu.editor.core.update.ApkDownloadInstaller
import com.docu.editor.core.update.AutoUpdateManager
import com.docu.editor.core.update.UpdateInfo
import com.docu.editor.ui.canvas.DocumentInteractiveCanvas
import com.docu.editor.ui.canvas.TextEditBottomSheet
import com.docu.editor.ui.theme.DocuEditTheme
import com.docu.editor.ui.update.UpdateDialog
import com.docu.editor.ui.viewmodel.DocumentEditorViewModel
import org.opencv.android.OpenCVLoader

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

                LaunchedEffect(Unit) {
                    val info = updateManager.checkForUpdates()
                    if (info.hasUpdate) {
                        pendingUpdate = info
                    }
                }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { viewModel.loadDocumentUri(it) }
                }

                LaunchedEffect(uiState.errorMessage) {
                    uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text("Seamless Document Editor") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            actions = {
                                IconButton(
                                    onClick = { viewModel.undo() },
                                    enabled = uiState.canUndo
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = "Undo")
                                }
                                IconButton(onClick = {
                                    filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                }) {
                                    Icon(Icons.Default.FileOpen, contentDescription = "Open Document")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val bitmap = uiState.currentBitmap
                        if (bitmap != null) {
                            DocumentInteractiveCanvas(
                                bitmap = bitmap,
                                detectedItems = uiState.detectedItems,
                                selectedItem = uiState.selectedItem,
                                onTextItemTapped = { viewModel.selectTextItem(it) }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Tap folder icon to open PDF or Image")
                            }
                        }

                        if (uiState.isScanning || uiState.isApplyingEdit) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(52.dp), color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = uiState.processingMessage ?: "Processing...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        uiState.selectedItem?.let { targetItem ->
                            TextEditBottomSheet(
                                item = targetItem,
                                sheetState = sheetState,
                                onDismiss = { viewModel.selectTextItem(null) },
                                onApplyEdit = { newText ->
                                    viewModel.applyTextReplacement(targetItem, newText)
                                }
                            )
                        }

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
}
