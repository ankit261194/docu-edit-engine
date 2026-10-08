package com.docu.editor.ui.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docu.editor.core.util.DocuStorageUtil
import com.docu.editor.core.vault.VaultRepository
import com.docu.editor.core.vault.VaultSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise Private Vault Management & Viewer Screen.
 * Hardware AES-256-GCM encrypted document manager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateVaultScreen(
    vaultMode: VaultSecurityManager.VaultMode,
    onLockAndClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityManager = remember { VaultSecurityManager(context) }

    var documentsList by remember { mutableStateOf<List<VaultRepository.VaultDocument>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedDocToView by remember { mutableStateOf<VaultRepository.VaultDocument?>(null) }
    var decryptedViewerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDecryptingViewer by remember { mutableStateOf(false) }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var docToDelete by remember { mutableStateOf<VaultRepository.VaultDocument?>(null) }

    fun refreshDocuments() {
        coroutineScope.launch {
            isLoading = true
            val docs = withContext(Dispatchers.IO) {
                VaultRepository.getDocuments(context, vaultMode)
            }
            documentsList = docs
            isLoading = false
        }
    }

    LaunchedEffect(vaultMode) {
        refreshDocuments()
    }

    // Document Picker Launcher for Importing Files into Vault
    val importPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    isLoading = true
                    Toast.makeText(context, "Encrypting with AES-256-GCM...", Toast.LENGTH_SHORT).show()
                    val tempIn = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(it)?.use { input ->
                        FileOutputStream(tempIn).use { out -> input.copyTo(out) }
                    }

                    VaultRepository.importDocument(
                        context = context,
                        sourceFile = tempIn,
                        mode = vaultMode,
                        customTitle = null,
                        deleteOriginal = true
                    )
                    Toast.makeText(context, "✅ Document securely stored in vault", Toast.LENGTH_SHORT).show()
                    refreshDocuments()
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Private Vault",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Security badge (only in Real mode)
                            if (vaultMode == VaultSecurityManager.VaultMode.REAL) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF064E3B), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AES-256",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34D399)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${documentsList.size} protected files",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onLockAndClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Lock & Exit",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (vaultMode == VaultSecurityManager.VaultMode.REAL) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Vault Settings",
                                tint = Color.LightGray
                            )
                        }
                    }
                    IconButton(onClick = onLockAndClose) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Lock Now",
                            tint = Color(0xFFF87171)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    importPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                },
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add to Vault", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = Color(0xFF0B1120)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                }
            } else if (documentsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Vault is Empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button below to import confidential photos, documents, or PDFs into hardware encrypted storage.",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(documentsList, key = { it.id }) { doc ->
                        VaultDocumentCard(
                            doc = doc,
                            onOpen = {
                                selectedDocToView = doc
                                isDecryptingViewer = true
                                coroutineScope.launch {
                                    val bytes = VaultRepository.decryptDocumentBytes(context, doc.id, vaultMode)
                                    if (bytes != null) {
                                        if (doc.mimeType.startsWith("image/")) {
                                            decryptedViewerBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        } else if (doc.mimeType == "application/pdf") {
                                            // Render first page of PDF
                                            decryptedViewerBitmap = renderPdfFirstPage(context, bytes)
                                        }
                                    }
                                    isDecryptingViewer = false
                                }
                            },
                            onExport = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Decrypting and exporting...", Toast.LENGTH_SHORT).show()
                                    val targetDir = File(context.cacheDir, "exported_${System.currentTimeMillis()}_${doc.originalFileName}")
                                    val success = VaultRepository.exportDocument(context, doc.id, vaultMode, targetDir)
                                    if (success) {
                                        val pubUri = DocuStorageUtil.saveFileToPublicDownloads(context, targetDir, doc.originalFileName, doc.mimeType)
                                        Toast.makeText(context, "Exported to Downloads!", Toast.LENGTH_SHORT).show()
                                        if (pubUri != null) {
                                            DocuStorageUtil.openFileWithExternalApp(context, pubUri, doc.mimeType, "Open Document")
                                        }
                                    } else {
                                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = {
                                docToDelete = doc
                            }
                        )
                    }
                }
            }
        }
    }

    // In-Memory Document Viewer Dialog
    val activeViewDoc = selectedDocToView
    if (activeViewDoc != null) {
        Dialog(
            onDismissRequest = {
                selectedDocToView = null
                decryptedViewerBitmap?.recycle()
                decryptedViewerBitmap = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                color = Color.Black
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                selectedDocToView = null
                                decryptedViewerBitmap?.recycle()
                                decryptedViewerBitmap = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeViewDoc.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    val tempFile = File(context.cacheDir, "share_${System.currentTimeMillis()}_${activeViewDoc.originalFileName}")
                                    if (VaultRepository.exportDocument(context, activeViewDoc.id, vaultMode, tempFile)) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            tempFile
                                        )
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = activeViewDoc.mimeType
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Document"))
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                        }
                    }

                    // Content Viewer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDecryptingViewer) {
                            CircularProgressIndicator(color = Color(0xFF10B981))
                        } else {
                            val bmp = decryptedViewerBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = activeViewDoc.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            } else {
                                Text("Unable to preview document", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    val activeDocToDelete = docToDelete
    if (activeDocToDelete != null) {
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Permanently Delete?") },
            text = { Text("This will shred and permanently delete '${activeDocToDelete.title}' from the hardware vault. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        VaultRepository.deleteDocument(context, activeDocToDelete.id, vaultMode)
                        docToDelete = null
                        refreshDocuments()
                        Toast.makeText(context, "Document shredded and deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { docToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Settings & Decoy PIN Management Sheet
    if (showSettingsSheet) {
        VaultSettingsSheet(
            securityManager = securityManager,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun VaultDocumentCard(
    doc: VaultRepository.VaultDocument,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(doc.addedTimestamp) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(doc.addedTimestamp))
    }
    val sizeStr = remember(doc.fileSize) {
        val kb = doc.fileSize / 1024.0
        if (kb > 1024) String.format(Locale.ROOT, "%.1f MB", kb / 1024.0) else String.format(Locale.ROOT, "%.0f KB", kb)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (doc.mimeType == "application/pdf") Color(0xFF7F1D1D) else Color(0xFF1E3A8A)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (doc.mimeType == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Description,
                    contentDescription = null,
                    tint = if (doc.mimeType == "application/pdf") Color(0xFFF87171) else Color(0xFF60A5FA),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sizeStr,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = " • $dateStr",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.LightGray)
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color(0xFF0F172A))
                ) {
                    DropdownMenuItem(
                        text = { Text("View", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export to Downloads", color = Color.White) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF10B981)) },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete / Shred", color = Color(0xFFF87171)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSettingsSheet(
    securityManager: VaultSecurityManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isBiometricActive by remember { mutableStateOf(securityManager.isBiometricEnabled()) }
    var isDecoySet by remember { mutableStateOf(securityManager.isDecoyConfigured()) }

    var showChangeRealPinDialog by remember { mutableStateOf(false) }
    var showSetDecoyPinDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Vault Security Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Biometric Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Biometric Quick Unlock",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Use fingerprint or face recognition instead of PIN",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Switch(
                    checked = isBiometricActive,
                    onCheckedChange = {
                        isBiometricActive = it
                        securityManager.setBiometricEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Decoy Duress PIN
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF312E81)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Duress / Decoy Fake PIN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "If someone forces you to unlock your vault, entering this fake PIN will open a convincing decoy locker containing dummy grocery & utility bills, keeping your true files completely hidden.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDecoySet) "Status: ✅ Active" else "Status: ⚠️ Not Set",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDecoySet) Color(0xFF34D399) else Color(0xFFFBBF24)
                        )

                        Button(
                            onClick = { showSetDecoyPinDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isDecoySet) "Change Decoy PIN" else "Set Decoy PIN", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Change Master PIN Button
            OutlinedButton(
                onClick = { showChangeRealPinDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Change Master PIN")
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Dialog for Setting / Changing Decoy PIN
    if (showSetDecoyPinDialog) {
        var newDecoyPin by remember { mutableStateOf("") }
        var decoyError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSetDecoyPinDialog = false },
            title = { Text("Configure Decoy PIN") },
            text = {
                Column {
                    Text("Enter a 4-digit Decoy PIN different from your master PIN:")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newDecoyPin,
                        onValueChange = { if (it.length <= 4) newDecoyPin = it },
                        singleLine = true,
                        label = { Text("4-Digit Decoy PIN") }
                    )
                    decoyError?.let {
                        Text(it, color = Color(0xFFF87171), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDecoyPin.length == 4) {
                            val success = securityManager.updateDecoyPin(newDecoyPin)
                            if (success) {
                                isDecoySet = true
                                showSetDecoyPinDialog = false
                                Toast.makeText(context, "Decoy PIN updated!", Toast.LENGTH_SHORT).show()
                            } else {
                                decoyError = "Decoy PIN cannot match your Master PIN!"
                            }
                        } else {
                            decoyError = "Must be exactly 4 digits"
                        }
                    }
                ) {
                    Text("Save Decoy PIN")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSetDecoyPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for Changing Master PIN
    if (showChangeRealPinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangeRealPinDialog = false },
            title = { Text("Change Master PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 4) currentPin = it },
                        singleLine = true,
                        label = { Text("Current Master PIN") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4) newPin = it },
                        singleLine = true,
                        label = { Text("New 4-Digit PIN") }
                    )
                    pinError?.let {
                        Text(it, color = Color(0xFFF87171), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length == 4) {
                            val success = securityManager.changeRealPin(currentPin, newPin)
                            if (success) {
                                showChangeRealPinDialog = false
                                Toast.makeText(context, "Master PIN changed successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                pinError = "Incorrect current PIN or matches Decoy PIN!"
                            }
                        } else {
                            pinError = "New PIN must be 4 digits"
                        }
                    }
                ) {
                    Text("Update PIN")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showChangeRealPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * In-memory PDF first page renderer.
 */
private fun renderPdfFirstPage(context: Context, pdfBytes: ByteArray): Bitmap? {
    return try {
        val tempPdf = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")
        FileOutputStream(tempPdf).use { it.write(pdfBytes) }
        val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val page = renderer.openPage(0)
        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()
        tempPdf.delete()
        bmp
    } catch (_: Exception) {
        null
    }
}
