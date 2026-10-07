package com.docu.editor.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.cloud.CloudBackupItem
import com.docu.editor.core.history.SavedDocumentItem
import com.docu.editor.core.util.ThumbnailCache

enum class HomeNavTab(val label: String) {
    HOME("Home"),
    FILES("Files"),
    TOOLS("Tools"),
    ME("Me")
}

/**
 * Premium CamScanner-Grade Home Screen Dashboard.
 * 4-Tab Architecture (Home, Files, Tools, Me) matching authentic CamScanner design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenDashboard(
    recentDocuments: List<SavedDocumentItem> = emptyList(),
    cloudBackups: List<CloudBackupItem> = emptyList(),
    hasGeminiApiKey: Boolean = false,
    onOpenSavedDocument: (filePath: String) -> Unit = {},
    onDeleteRecentDocument: (String) -> Unit = {},
    onClearAllRecentDocuments: () -> Unit = {},
    onUpdateCategory: (String, String) -> Unit = { _, _ -> },
    onRenameDocument: (String, String) -> Unit = { _, _ -> },
    onSaveToGoogleDrive: (SavedDocumentItem) -> Unit = {},
    onBackupToCloud: (SavedDocumentItem) -> Unit = {},
    onViewCloudBackupsClicked: () -> Unit = {},
    onCameraScanClicked: () -> Unit,
    onOpenFileClicked: () -> Unit,
    onIdCardClicked: () -> Unit,
    onSignatureClicked: () -> Unit,
    onPdfToolsClicked: () -> Unit,
    onBulkBatchOcrClicked: () -> Unit = {},
    onTryDemoClicked: () -> Unit,
    onCloudAiSettingsClicked: () -> Unit = {},
    onCheckUpdateClicked: () -> Unit = {},
    // CamScanner Tools Hub Callbacks
    onConvertToWord: () -> Unit = {},
    onConvertToExcel: () -> Unit = {},
    onConvertToPpt: () -> Unit = {},
    onCountCam: () -> Unit = {},
    onPdfToImages: () -> Unit = {},
    onPdfToLongImage: () -> Unit = {},
    onCamScannerAi: () -> Unit = {},
    onImportImages: () -> Unit = {},
    onImportFiles: () -> Unit = {},
    onSign: () -> Unit = {},
    onAddWatermark: () -> Unit = {},
    onMergeFiles: () -> Unit = {},
    onExtractPdfPages: () -> Unit = {},
    onReorderPages: () -> Unit = {},
    onLockPdf: () -> Unit = {},
    onEraseMarks: () -> Unit = {},
    onSmartErase: () -> Unit = {},
    onSolverAi: () -> Unit = {},
    onEnhanceDocuments: () -> Unit = {},
    onRestorePhoto: () -> Unit = {},
    onScanIdCards: () -> Unit = {},
    onExtractText: () -> Unit = {},
    onIdPhotoMaker: () -> Unit = {},
    onScanToExcel: () -> Unit = {},
    onFormulaOcr: () -> Unit = {},
    onBookDewarp: () -> Unit = {},
    onSlidesScan: () -> Unit = {},
    onWhiteboardScan: () -> Unit = {},
    onTimestampScan: () -> Unit = {},
    onScanCode: () -> Unit = {}
) {
    var activeNavTab by remember { mutableStateOf(HomeNavTab.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }
    var docToRename by remember { mutableStateOf<SavedDocumentItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val categories = listOf("All", "ID Cards", "Invoices", "Office", "Personal")

    val filteredDocs = remember(recentDocuments, searchQuery, selectedCategory) {
        recentDocuments.filter { doc ->
            val matchesCat = if (selectedCategory == "All") true else doc.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = if (searchQuery.isBlank()) true else {
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.extractedOcrText.contains(searchQuery, ignoreCase = true)
            }
            matchesCat && matchesQuery
        }
    }

    // Bottom Sheet for Document Rename
    val activeDocToRename = docToRename
    if (activeDocToRename != null) {
        ModalBottomSheet(
            onDismissRequest = { docToRename = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD1FAE5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Rename Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { docToRename = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("Document Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF059669),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { docToRename = null },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            if (renameInput.isNotBlank()) {
                                onRenameDocument(activeDocToRename.id, renameInput.trim())
                            }
                            docToRename = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (activeNavTab) {
            HomeNavTab.HOME -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. CamScanner Top Header Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF047857), Color(0xFF10B981))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "DocuEdit",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD1FAE5)
                                    ) {
                                        Text(
                                            text = "PRO",
                                            color = Color(0xFF047857),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onCheckUpdateClicked() }
                                    ) {
                                        Text(
                                            text = "v${com.docu.editor.BuildConfig.VERSION_NAME} 🔄",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                                        Icon(
                                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEFF6FF),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onViewCloudBackupsClicked() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CloudDone,
                                                contentDescription = null,
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${cloudBackups.size}",
                                                color = Color(0xFF1D4ED8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = isSearchExpanded,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search title or OCR text...", fontSize = 13.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF059669),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Category Navigation Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (cat in categories) {
                                    val isSelected = selectedCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSelected) Color(0xFF059669) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { selectedCategory = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Main Document Section & Quick Action Cards
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StudioActionCard(
                                    title = "Camera Scan",
                                    subtitle = "Manual Shutter",
                                    icon = Icons.Default.CameraAlt,
                                    gradient = listOf(Color(0xFF059669), Color(0xFF10B981)),
                                    onClick = onCameraScanClicked
                                )
                                StudioActionCard(
                                    title = "Import Files",
                                    subtitle = "PDF & Gallery",
                                    icon = Icons.Default.PhotoLibrary,
                                    gradient = listOf(Color(0xFF2563EB), Color(0xFF3B82F6)),
                                    onClick = onOpenFileClicked
                                )
                                StudioActionCard(
                                    title = "ID Card Mode",
                                    subtitle = "Front & Back Page",
                                    icon = Icons.Default.Badge,
                                    gradient = listOf(Color(0xFF0D9488), Color(0xFF14B8A6)),
                                    onClick = onIdCardClicked
                                )
                                StudioActionCard(
                                    title = "Batch OCR",
                                    subtitle = "Multi-Doc Extract",
                                    icon = Icons.Default.AutoAwesome,
                                    gradient = listOf(Color(0xFF7C3AED), Color(0xFF8B5CF6)),
                                    onClick = onBulkBatchOcrClicked
                                )
                                StudioActionCard(
                                    title = "Sign & Stamp",
                                    subtitle = "Vector Signatures",
                                    icon = Icons.Default.Draw,
                                    gradient = listOf(Color(0xFFE11D48), Color(0xFFF43F5E)),
                                    onClick = onSignatureClicked
                                )
                                StudioActionCard(
                                    title = "PDF Tools",
                                    subtitle = "Merge & Split",
                                    icon = Icons.Default.PictureAsPdf,
                                    gradient = listOf(Color(0xFFD97706), Color(0xFFF59E0B)),
                                    onClick = onPdfToolsClicked
                                )
                            }
                        }

                        if (filteredDocs.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFD1FAE5)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = Color(0xFF059669),
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = if (searchQuery.isNotBlank()) "No documents match '$searchQuery'" else "No documents yet",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tap the Camera button below to scan your first document",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = onCameraScanClicked,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Scanning", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = onTryDemoClicked,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Load Sample Invoice", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } else {
                            items(filteredDocs, key = { it.id }) { doc ->
                                CamScannerDocCard(
                                    document = doc,
                                    onClick = { onOpenSavedDocument(doc.filePath) },
                                    onDelete = { onDeleteRecentDocument(doc.id) },
                                    onUpdateCategory = { newCat -> onUpdateCategory(doc.id, newCat) },
                                    onRename = {
                                        renameInput = doc.title
                                        docToRename = doc
                                    },
                                    onSaveToGoogleDrive = { onSaveToGoogleDrive(doc) },
                                    onBackupToCloud = { onBackupToCloud(doc) }
                                )
                            }
                        }
                    }
                }

                // Floating Action Buttons (Camera + Gallery)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 78.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable { onOpenFileClicked() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Import Gallery",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        FloatingActionButton(
                            onClick = onCameraScanClicked,
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White,
                            shape = CircleShape,
                            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(8.dp),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Scan Document",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }

            HomeNavTab.FILES -> {
                FilesManagerTab(
                    documents = recentDocuments,
                    cloudBackups = cloudBackups,
                    onOpenDocument = onOpenSavedDocument,
                    onCategoryFilterSelected = { cat ->
                        selectedCategory = cat
                        activeNavTab = HomeNavTab.HOME
                    },
                    onViewCloudBackupsClicked = onViewCloudBackupsClicked
                )
            }

            HomeNavTab.TOOLS -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Tools",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    CamScannerToolsTab(
                        onConvertToWord = onConvertToWord,
                        onConvertToExcel = onConvertToExcel,
                        onConvertToPpt = onConvertToPpt,
                        onCountCam = onCountCam,
                        onPdfToImages = onPdfToImages,
                        onPdfToLongImage = onPdfToLongImage,
                        onCamScannerAi = onCamScannerAi,
                        onImportImages = onImportImages,
                        onImportFiles = onImportFiles,
                        onSign = onSign,
                        onAddWatermark = onAddWatermark,
                        onMergeFiles = onMergeFiles,
                        onExtractPdfPages = onExtractPdfPages,
                        onReorderPages = onReorderPages,
                        onLockPdf = onLockPdf,
                        onEraseMarks = onEraseMarks,
                        onSmartErase = onSmartErase,
                        onSolverAi = onSolverAi,
                        onEnhanceDocuments = onEnhanceDocuments,
                        onRestorePhoto = onRestorePhoto,
                        onScanIdCards = onScanIdCards,
                        onExtractText = onExtractText,
                        onIdPhotoMaker = onIdPhotoMaker,
                        onScanToExcel = onScanToExcel,
                        onFormulaOcr = onFormulaOcr,
                        onBookDewarp = onBookDewarp,
                        onSlidesScan = onSlidesScan,
                        onWhiteboardScan = onWhiteboardScan,
                        onTimestampScan = onTimestampScan,
                        onScanCode = onScanCode
                    )
                }
            }

            HomeNavTab.ME -> {
                ProfileMeTab(
                    geminiKeyConfigured = hasGeminiApiKey,
                    onConfigureCloudAiKey = onCloudAiSettingsClicked,
                    onViewCloudBackups = onViewCloudBackupsClicked,
                    onCheckUpdate = onCheckUpdateClicked
                )
            }
        }

        // Signature CamScanner Bottom Navigation Bar (Home, Files, Tools, Me)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(58.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    label = "Home",
                    icon = Icons.Default.Home,
                    isSelected = activeNavTab == HomeNavTab.HOME,
                    onClick = { activeNavTab = HomeNavTab.HOME }
                )
                BottomNavItem(
                    label = "Files",
                    icon = Icons.Default.Description,
                    isSelected = activeNavTab == HomeNavTab.FILES,
                    onClick = { activeNavTab = HomeNavTab.FILES }
                )
                BottomNavItem(
                    label = "Tools",
                    icon = Icons.Default.GridView,
                    isSelected = activeNavTab == HomeNavTab.TOOLS,
                    onClick = { activeNavTab = HomeNavTab.TOOLS }
                )
                BottomNavItem(
                    label = "Me",
                    icon = Icons.Default.Person,
                    isSelected = activeNavTab == HomeNavTab.ME,
                    hasEduBadge = true,
                    onClick = { activeNavTab = HomeNavTab.ME }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    hasEduBadge: Boolean = false,
    onClick: () -> Unit
) {
    val activeColor = Color(0xFF059669) // CamScanner Emerald
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val color = if (isSelected) activeColor else inactiveColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            if (hasEduBadge) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFFEF4444),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 14.dp, bottom = 8.dp)
                ) {
                    Text(
                        "EDU",
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Modern High-Contrast Studio Action Card with Gradient Accent.
 */
@Composable
private fun StudioActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(135.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Clean Document Card with Responsive Thumbnails & Options Menu.
 */
@Composable
private fun CamScannerDocCard(
    document: SavedDocumentItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onUpdateCategory: (String) -> Unit = {},
    onRename: () -> Unit = {},
    onSaveToGoogleDrive: () -> Unit = {},
    onBackupToCloud: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var thumbBmp by remember { mutableStateOf<Bitmap?>(null) }
    val categoryList = listOf("ID Cards", "Invoices", "Office", "Personal")

    LaunchedEffect(document.thumbnailPath) {
        thumbBmp = ThumbnailCache.loadThumbnail(document.thumbnailPath, targetSize = 280)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val currentThumb = thumbBmp
                if (currentThumb != null && !currentThumb.isRecycled) {
                    Image(
                        bitmap = currentThumb.asImageBitmap(),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(42.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = if (document.pageCount > 1) "${document.pageCount} Pages" else "1 Page",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = document.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${document.formattedDate} • ${document.formattedSize}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = document.category,
                            color = Color(0xFF475569),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save to Google Drive", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onSaveToGoogleDrive()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Backup to Cloud", fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onBackupToCloud()
                            }
                        )
                        for (cat in categoryList) {
                            if (cat != document.category) {
                                DropdownMenuItem(
                                    text = { Text("Move to $cat", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    onClick = {
                                        showMenu = false
                                        onUpdateCategory(cat)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
