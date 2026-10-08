package com.docu.editor.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docu.editor.core.export.BatchTargetResizeConverterEngine
import com.docu.editor.core.export.BatchTargetResizeConverterEngine.BatchAdjustMode
import com.docu.editor.core.export.BatchTargetResizeConverterEngine.BatchInputItem
import com.docu.editor.core.export.BatchTargetResizeConverterEngine.BatchOutputFormat
import com.docu.editor.core.export.BatchTargetResizeConverterEngine.BatchOverallResult
import com.docu.editor.core.export.BatchTargetResizeConverterEngine.BatchProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

/**
 * Enterprise Batch Target Size Resizer & Format Converter Studio Dialog.
 *
 * Designed specifically for Govt Exams (SSC, UPSC, IBPS, Railways, Police),
 * College Portals, and High-Volume Office Operations:
 * 1. Batch ingestion of 1 to 50+ mixed files (JPG, PNG, PDF, WEBP).
 * 2. Exact Target Size Enforcement (e.g. 20 KB signature, 50 KB photo, 100 KB ID, 200 KB PDF).
 * 3. Cross-Format Conversion: Convert all to JPG, all to PDF, etc., or keep original.
 * 4. 1-Click "Save All to Downloads", "Export ZIP Archive", or "Share All via WhatsApp/Email".
 */
@Composable
fun BatchResizeStudioDialog(
    initialUris: List<Uri> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputQueue by remember { mutableStateOf<List<BatchInputItem>>(emptyList()) }
    var targetKbText by remember { mutableStateOf("50") }
    var selectedAdjustMode by remember { mutableStateOf(BatchAdjustMode.DECREASE_TO_MAX) }
    var selectedOutputFormat by remember { mutableStateOf(BatchOutputFormat.KEEP_ORIGINAL) }
    var selectedTargetDpi by remember { mutableIntStateOf(300) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressState by remember { mutableStateOf<BatchProgressState?>(null) }
    var overallResult by remember { mutableStateOf<BatchOverallResult?>(null) }
    var zipFileResult by remember { mutableStateOf<File?>(null) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val resolved = uris.map { uri: Uri ->
                BatchTargetResizeConverterEngine.createInputItemFromUri(context, uri)
            }
            inputQueue = inputQueue + resolved
        }
    }

    // Auto-resolve initial URIs if passed from caller
    androidx.compose.runtime.LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty() && inputQueue.isEmpty()) {
            val resolved = initialUris.map { uri: Uri ->
                BatchTargetResizeConverterEngine.createInputItemFromUri(context, uri)
            }
            inputQueue = resolved
        }
    }

    val presets = listOf(
        Triple(20, "20 KB", "Signature / Thumb"),
        Triple(50, "50 KB", "SSC / UPSC Photo"),
        Triple(100, "100 KB", "Govt ID Card"),
        Triple(200, "200 KB", "Application PDF"),
        Triple(500, "500 KB", "Standard Web")
    )

    fun formatBytes(bytes: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isProcessing) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF2563EB), Color(0xFF3B82F6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compress,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Batch Resizer & Converter",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFDBEAFE)
                                ) {
                                    Text(
                                        text = "PRO",
                                        color = Color(0xFF1D4ED8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Exact KB Target • Multi-Format • 1-Click ZIP",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // If processing completed, show results view
                val res = overallResult
                if (res != null) {
                    BatchResultsView(
                        result = res,
                        zipFile = zipFileResult,
                        onSaveAllToGallery = {
                            scope.launch {
                                val validFiles = res.results.filter { it.isSuccess }.map { it.outputFile }
                                val savedCount = BatchTargetResizeConverterEngine.saveAllToGallery(context, validFiles)
                                Toast.makeText(context, "$savedCount files saved to Gallery (DocuEdit album)", Toast.LENGTH_LONG).show()
                            }
                        },
                        onSaveAllToDownloads = {
                            scope.launch {
                                val validFiles = res.results.filter { it.isSuccess }.map { it.outputFile }
                                val savedCount = BatchTargetResizeConverterEngine.saveAllToDownloads(context, validFiles)
                                Toast.makeText(context, "$savedCount files saved to Downloads (DocuEdit)", Toast.LENGTH_LONG).show()
                            }
                        },
                        onSaveSingleToGallery = { file ->
                            val uri = BatchTargetResizeConverterEngine.saveSingleFileToGallery(context, file)
                            if (uri != null) {
                                Toast.makeText(context, "${file.name} saved to Gallery / Downloads", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save ${file.name}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCreateZip = {
                            scope.launch {
                                val validFiles = res.results.filter { it.isSuccess }.map { it.outputFile }
                                val zipOutFile = File(context.cacheDir, "DocuEdit_Batch_${System.currentTimeMillis()}.zip")
                                val zip = BatchTargetResizeConverterEngine.createZipArchive(validFiles, zipOutFile)
                                zipFileResult = zip
                                Toast.makeText(context, "ZIP archive created: ${zip.name}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onShareAll = {
                            val validFiles = res.results.filter { it.isSuccess }.map { it.outputFile }
                            val shareUris = BatchTargetResizeConverterEngine.getShareUris(context, validFiles)
                            if (shareUris.isNotEmpty()) {
                                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share All Converted Files"))
                            }
                        },
                        onReset = {
                            overallResult = null
                            progressState = null
                            zipFileResult = null
                        },
                        onDismiss = onDismiss
                    )
                } else if (isProcessing) {
                    // Processing State View
                    BatchProcessingView(
                        progressState = progressState,
                        totalCount = inputQueue.size
                    )
                } else {
                    // Queue Setup & Configuration View
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Files Ingestion Strip
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Selected Files: ${inputQueue.size} items",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            val totalSize = inputQueue.sumOf { it.originalSizeBytes }
                                            Text(
                                                text = "Total input size: ${formatBytes(totalSize)}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (inputQueue.isNotEmpty()) {
                                                OutlinedButton(
                                                    onClick = { inputQueue = emptyList() },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Clear", fontSize = 12.sp)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    multiFilePicker.launch(arrayOf("image/*", "application/pdf"))
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (inputQueue.isEmpty()) "Select Files" else "Add More", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    if (inputQueue.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Horizontal scrollable preview list of items
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            inputQueue.forEachIndexed { idx, item ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.White,
                                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (item.originalName.endsWith(".pdf", ignoreCase = true)) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                                            contentDescription = null,
                                                            tint = if (item.originalName.endsWith(".pdf", ignoreCase = true)) Color(0xFFDC2626) else Color(0xFF2563EB),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Column {
                                                            Text(
                                                                text = item.originalName,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.width(100.dp)
                                                            )
                                                            Text(
                                                                text = formatBytes(item.originalSizeBytes),
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF64748B)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        IconButton(
                                                            onClick = {
                                                                inputQueue = inputQueue.filterIndexed { i, _ -> i != idx }
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Target File Size Section
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Target File Size",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )

                                        // Adjustment Mode Selector
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (selectedAdjustMode == BatchAdjustMode.DECREASE_TO_MAX) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                                                modifier = Modifier.clickable { selectedAdjustMode = BatchAdjustMode.DECREASE_TO_MAX }
                                            ) {
                                                Text(
                                                    text = "Max Limit (≤)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selectedAdjustMode == BatchAdjustMode.DECREASE_TO_MAX) Color.White else Color(0xFF334155),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (selectedAdjustMode == BatchAdjustMode.INCREASE_TO_MIN) Color(0xFF059669) else Color(0xFFE2E8F0),
                                                modifier = Modifier.clickable { selectedAdjustMode = BatchAdjustMode.INCREASE_TO_MIN }
                                            ) {
                                                Text(
                                                    text = "Min Limit (≥)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selectedAdjustMode == BatchAdjustMode.INCREASE_TO_MIN) Color.White else Color(0xFF334155),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Quick Presets Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        presets.forEach { (kb, label, desc) ->
                                            val isSelected = targetKbText == kb.toString()
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { targetKbText = kb.toString() },
                                                label = {
                                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                                        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text(desc, fontSize = 9.sp)
                                                    }
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF2563EB),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color.White,
                                                    labelColor = Color(0xFF334155)
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Custom Target Size Input
                                    OutlinedTextField(
                                        value = targetKbText,
                                        onValueChange = { targetKbText = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Exact Target Size in KB (Kilobytes)") },
                                        trailingIcon = { Text("KB", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), modifier = Modifier.padding(end = 12.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF2563EB),
                                            unfocusedBorderColor = Color(0xFFCBD5E1),
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // 3. Format Converter Section
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Output Format (Optional)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BatchOutputFormat.entries.forEach { fmt ->
                                            val isSelected = selectedOutputFormat == fmt
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) Color(0xFF2563EB) else Color.White,
                                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedOutputFormat = fmt }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = fmt.label,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. DPI Resolution Compliance Section (Govt Standard)
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "DPI Resolution Compliance",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDBEAFE)
                                        ) {
                                            Text(
                                                text = "UPSC/SSC Ready",
                                                color = Color(0xFF1D4ED8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val dpiOptions = listOf(
                                        Pair(300, "300 DPI (High Quality)"),
                                        Pair(200, "200 DPI (Govt Standard)"),
                                        Pair(150, "150 DPI (Compact Web)")
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        dpiOptions.forEach { (dpi, label) ->
                                            val isSelected = selectedTargetDpi == dpi
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) Color(0xFF1E40AF) else Color.White,
                                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF1E40AF) else Color(0xFFCBD5E1)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedTargetDpi = dpi }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isSelected) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = label,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Execute Button
                    val targetKbInt = targetKbText.toIntOrNull() ?: 50
                    Button(
                        onClick = {
                            if (inputQueue.isEmpty()) {
                                Toast.makeText(context, "Please select at least 1 file", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessing = true
                            progressState = BatchProgressState(
                                currentIndex = 0,
                                totalCount = inputQueue.size,
                                currentFileName = inputQueue.first().originalName,
                                progressPercent = 0
                            )

                            scope.launch(Dispatchers.Default) {
                                val result = BatchTargetResizeConverterEngine.processBatch(
                                    context = context.applicationContext,
                                    items = inputQueue,
                                    targetKb = targetKbInt,
                                    outputFormat = selectedOutputFormat,
                                    adjustMode = selectedAdjustMode,
                                    targetDpi = selectedTargetDpi,
                                    onProgress = { progress ->
                                        progressState = progress
                                    }
                                )
                                overallResult = result
                                isProcessing = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        enabled = inputQueue.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Batch Processing (${inputQueue.size} Files • ${targetKbInt} KB)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchProcessingView(
    progressState: BatchProgressState?,
    totalCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF2563EB),
            strokeWidth = 4.dp,
            modifier = Modifier.size(60.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Processing Batch Queue...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(6.dp))

        val currentIdx = progressState?.currentIndex ?: 0
        val fileName = progressState?.currentFileName ?: "Initializing..."
        val percent = progressState?.progressPercent ?: 0

        Text(
            text = "File $currentIdx of $totalCount: $fileName",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            color = Color(0xFF2563EB),
            trackColor = Color(0xFFE2E8F0),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$percent%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2563EB)
        )
    }
}

@Composable
private fun BatchResultsView(
    result: BatchOverallResult,
    zipFile: File?,
    onSaveAllToGallery: () -> Unit,
    onSaveAllToDownloads: () -> Unit,
    onSaveSingleToGallery: (File) -> Unit,
    onCreateZip: () -> Unit,
    onShareAll: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val df = DecimalFormat("#.##")
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Results Summary Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF0FDF4),
            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Batch Processing Complete!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF14532D)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Converted:", fontSize = 12.sp, color = Color(0xFF166534))
                        Text("${result.successCount} / ${result.results.size} Files", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF14532D))
                    }
                    Column {
                        Text("Original Total:", fontSize = 12.sp, color = Color(0xFF166534))
                        Text(formatBytes(result.totalOriginalBytes), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF14532D))
                    }
                    Column {
                        Text("Output Total:", fontSize = 12.sp, color = Color(0xFF166534))
                        Text(formatBytes(result.totalOutputBytes), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF16A34A))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Files Results Scrollable List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(result.results) { idx, single ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (single.isSuccess) Color(0xFFF8FAFC) else Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, if (single.isSuccess) Color(0xFFE2E8F0) else Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (single.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (single.isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = single.originalName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatBytes(single.originalSizeBytes)} ➔ ${formatBytes(single.outputSizeBytes)} (${single.outputFormat})",
                                    fontSize = 11.sp,
                                    color = if (single.isSuccess) Color(0xFF059669) else Color(0xFFDC2626)
                                )
                            }
                        }

                        if (single.isSuccess) {
                            IconButton(
                                onClick = { onSaveSingleToGallery(single.outputFile) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Save to Gallery",
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons Strip: Gallery, Downloads, ZIP & Share
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Save All to Gallery (PRO PRIMARY)
            Button(
                onClick = onSaveAllToGallery,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save All to Gallery (Photos)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }

            // Row 2: Save to Downloads & ZIP Archive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveAllToDownloads,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Downloads", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onCreateZip,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (zipFile != null) "ZIP Ready!" else "ZIP Archive", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Row 3: Share All & Batch Again
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShareAll,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share All", fontSize = 12.5.sp)
                }

                OutlinedButton(
                    onClick = onReset,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("Batch Again", fontSize = 12.5.sp)
                }
            }
        }
    }
}
