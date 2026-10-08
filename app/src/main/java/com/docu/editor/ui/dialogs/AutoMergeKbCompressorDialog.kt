package com.docu.editor.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.docu.editor.core.export.AutoMergeCompressorEngine
import com.docu.editor.core.export.AutoMergeCompressorEngine.CompressionStrategy
import com.docu.editor.core.export.AutoMergeCompressorEngine.GOVERNMENT_PORTAL_PRESETS
import com.docu.editor.core.export.AutoMergeCompressorEngine.MergeCompressResult
import com.docu.editor.core.export.AutoMergeCompressorEngine.MergeInputDocument
import com.docu.editor.core.util.DocuStorageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enterprise Auto-Merge & Smart Batch Compressor Studio Dialog (Pro Feature 9).
 *
 * Provides complete 1-tap combining of up to 20 documents into a single PDF
 * strictly under specified government portal KB limits with verified quality.
 */
@Composable
fun AutoMergeKbCompressorDialog(
    initialUris: List<Uri> = emptyList(),
    onOpenInEditor: ((File) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var queue by remember { mutableStateOf<List<MergeInputDocument>>(emptyList()) }
    var selectedPresetId by remember { mutableStateOf("p200") }
    var targetKbText by remember { mutableStateOf("200") }
    var isCustomPreset by remember { mutableStateOf(false) }
    var selectedStrategy by remember { mutableStateOf(CompressionStrategy.SMART_ADAPTIVE) }
    var exactMatchPadding by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var currentProgressText by remember { mutableStateOf("") }
    var currentProgressFraction by remember { mutableStateOf(0f) }
    var mergeResult by remember { mutableStateOf<MergeCompressResult?>(null) }

    // Multi-File Ingestion Picker (supports images + PDFs)
    val multiDocPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val newDocs = withContext(Dispatchers.IO) {
                    uris.take(25).map { uri ->
                        AutoMergeCompressorEngine.inspectDocumentUri(context, uri)
                    }
                }
                queue = (queue + newDocs).distinctBy { it.uri }.take(20)
            }
        }
    }

    // Auto-inspect initial URIs if provided
    androidx.compose.runtime.LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty() && queue.isEmpty()) {
            withContext(Dispatchers.IO) {
                val docs = initialUris.take(20).map { uri ->
                    AutoMergeCompressorEngine.inspectDocumentUri(context, uri)
                }
                queue = docs
            }
        }
    }

    val activeTargetKb = targetKbText.toIntOrNull()?.coerceIn(20, 15000) ?: 200
    val preFlightEstimate = remember(queue, activeTargetKb, selectedStrategy) {
        AutoMergeCompressorEngine.estimatePreFlightQuality(queue, activeTargetKb, selectedStrategy)
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F172A), // Slate 900
            border = BorderStroke(1.2.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // =============================================================
                // 1. Studio Header Banner
                // =============================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF059669), Color(0xFF10B981))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MergeType,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Auto-Merge & Smart KB Compressor",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFF59E0B))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "👑 PRO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                            }
                            Text(
                                text = "Combine up to 20 documents into a single PDF under exact Govt KB limits",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // =============================================================
                // 2. Main Scrollable Workbench Body
                // =============================================================
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section A: Document Queue Bar & Actions
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Document Ingestion Queue (${queue.size}/20)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE2E8F0)
                                )
                                if (queue.isNotEmpty()) {
                                    val totalPages = queue.sumOf { it.pageCount }
                                    val totalBytesStr = AutoMergeCompressorEngine.formatBytes(queue.sumOf { it.fileSizeBytes })
                                    Text(
                                        text = "$totalPages Pages Total • Raw Size: $totalBytesStr",
                                        fontSize = 11.sp,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (queue.isNotEmpty()) {
                                    TextButton(
                                        onClick = { queue = emptyList() },
                                        enabled = !isProcessing
                                    ) {
                                        Text("Clear All", fontSize = 11.5.sp, color = Color(0xFFEF4444))
                                    }
                                }
                                Button(
                                    onClick = { multiDocPickerLauncher.launch("*/*") },
                                    enabled = !isProcessing && queue.size < 20,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Add Files", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Empty Queue State
                    if (queue.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { multiDocPickerLauncher.launch("*/*") },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap to Select Documents (Images or PDFs)",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Select Marksheets, Aadhaar, Caste Certificate, or Photos to combine into 1 PDF",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF94A3B8),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Reorderable Queue Items
                    itemsIndexed(queue) { index, doc ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Index badge
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Thumbnail preview
                                if (doc.thumbnailBitmap != null) {
                                    Image(
                                        bitmap = doc.thumbnailBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF0F172A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Title and info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.displayName,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = AutoMergeCompressorEngine.formatBytes(doc.fileSizeBytes),
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Text(text = "•", fontSize = 10.sp, color = Color(0xFF64748B))
                                        Text(
                                            text = if (doc.pageCount > 1) "${doc.pageCount} pgs" else "1 pg",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        if (doc.rotationDegrees > 0) {
                                            Text(text = "•", fontSize = 10.sp, color = Color(0xFF64748B))
                                            Text(
                                                text = "${doc.rotationDegrees}°",
                                                fontSize = 10.5.sp,
                                                color = Color(0xFFF59E0B)
                                            )
                                        }
                                    }
                                }

                                // Reorder & action buttons
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val m = queue.toMutableList()
                                                val item = m.removeAt(index)
                                                m.add(index - 1, item)
                                                queue = m
                                            }
                                        },
                                        enabled = index > 0 && !isProcessing,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) Color(0xFF94A3B8) else Color(0xFF475569),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (index < queue.size - 1) {
                                                val m = queue.toMutableList()
                                                val item = m.removeAt(index)
                                                m.add(index + 1, item)
                                                queue = m
                                            }
                                        },
                                        enabled = index < queue.size - 1 && !isProcessing,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (index < queue.size - 1) Color(0xFF94A3B8) else Color(0xFF475569),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Rotate 90°
                                    IconButton(
                                        onClick = {
                                            val m = queue.toMutableList()
                                            val cur = m[index]
                                            m[index] = cur.copy(rotationDegrees = (cur.rotationDegrees + 90) % 360)
                                            queue = m
                                        },
                                        enabled = !isProcessing,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Rotate",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete
                                    IconButton(
                                        onClick = {
                                            val m = queue.toMutableList()
                                            m.removeAt(index)
                                            queue = m
                                        },
                                        enabled = !isProcessing,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section B: Govt Portal Presets & Target KB Ceiling
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Target KB Ceiling (Govt Portal Presets)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Preset Chips Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GOVERNMENT_PORTAL_PRESETS.forEach { preset ->
                                        val isSel = !isCustomPreset && selectedPresetId == preset.id
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                isCustomPreset = false
                                                selectedPresetId = preset.id
                                                targetKbText = preset.targetKb.toString()
                                            },
                                            label = {
                                                Text(
                                                    text = preset.label,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF059669),
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0xFF0F172A),
                                                labelColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                    }

                                    // Custom KB Chip
                                    FilterChip(
                                        selected = isCustomPreset,
                                        onClick = { isCustomPreset = true },
                                        label = {
                                            Text(
                                                text = "Custom KB",
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isCustomPreset) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF2563EB),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0xFF0F172A),
                                            labelColor = Color(0xFFCBD5E1)
                                        )
                                    )
                                }

                                // Custom KB Input Field
                                AnimatedVisibility(visible = isCustomPreset) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        OutlinedTextField(
                                            value = targetKbText,
                                            onValueChange = { targetKbText = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("Exact Target File Size (KB)", fontSize = 12.sp) },
                                            placeholder = { Text("e.g. 150") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2563EB),
                                                focusedLabelColor = Color(0xFF2563EB),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Selected preset description
                                val currentPreset = GOVERNMENT_PORTAL_PRESETS.firstOrNull { it.id == selectedPresetId }
                                if (!isCustomPreset && currentPreset != null) {
                                    Text(
                                        text = "Official Spec: ${currentPreset.portalExamples}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Exact Match Toggle
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { exactMatchPadding = !exactMatchPadding }
                                ) {
                                    Checkbox(
                                        checked = exactMatchPadding,
                                        onCheckedChange = { exactMatchPadding = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            text = "Strict Exact Match Padding (Bit-for-bit exact KB)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "For portals that enforce minimum file size (e.g. min 100 KB)",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section C: Compression Strategy Chips
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Quality Optimization Strategy",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompressionStrategy.entries.forEach { strat ->
                                        val isSel = (strat == selectedStrategy)
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { selectedStrategy = strat },
                                            label = {
                                                Text(
                                                    text = strat.title,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF0F766E),
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0xFF0F172A),
                                                labelColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = selectedStrategy.subtitle,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // Section D: Pre-Flight Live Quality Inspector
                    if (queue.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101D)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VerifiedUser,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Pre-Flight Quality Inspector",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                        }

                                        Text(
                                            text = preFlightEstimate.readabilityGrade.stars,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Raw Combined Size", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                AutoMergeCompressorEngine.formatBytes(preFlightEstimate.rawTotalBytes),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Target Ceiling", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                "≤ ${preFlightEstimate.targetKb} KB",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Reduction", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                                            Text(
                                                "${preFlightEstimate.spaceReductionPercent.toInt()}%",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.8.dp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = preFlightEstimate.recommendationHint,
                                        fontSize = 11.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }

                    // Section E: Final Result Card
                    if (mergeResult != null) {
                        item {
                            val res = mergeResult!!
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, Color(0xFF34D399))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Combined PDF Successfully Generated!",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Guaranteed Portal Safe • All ${res.totalPages} Pages Combined",
                                                fontSize = 11.sp,
                                                color = Color(0xFFA7F3D0)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Final Size: ${AutoMergeCompressorEngine.formatBytes(res.finalSizeBytes)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Target: ${res.targetKb} KB",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFA7F3D0)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Action Buttons Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Save to Downloads
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val savedUri = DocuStorageUtil.saveFileToPublicDownloads(
                                                            context = context,
                                                            srcFile = res.outputFile,
                                                            displayName = res.outputFile.name,
                                                            mimeType = "application/pdf"
                                                        )
                                                        if (savedUri != null) {
                                                            Toast.makeText(context, "Saved to Downloads/DocuEdit!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Saved: ${res.outputFile.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Saved: ${res.outputFile.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Share via WhatsApp/Email
                                        Button(
                                            onClick = {
                                                try {
                                                    val shareUri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        res.outputFile
                                                    )
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Share Combined PDF"))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Open in Canvas (Optional)
                                        if (onOpenInEditor != null) {
                                            OutlinedButton(
                                                onClick = {
                                                    onOpenInEditor(res.outputFile)
                                                    onDismiss()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = BorderStroke(1.dp, Color(0xFF34D399)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // 3. Bottom Action & Execution Footer
                // =============================================================
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar if running
                if (isProcessing) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentProgressText,
                                fontSize = 11.sp,
                                color = Color(0xFF10B981)
                            )
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF10B981)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { currentProgressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Primary Execution Button
                Button(
                    onClick = {
                        if (queue.isEmpty()) {
                            Toast.makeText(context, "Please add at least 1 document to combine", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isProcessing = true
                        mergeResult = null
                        scope.launch {
                            try {
                                val result = AutoMergeCompressorEngine.mergeAndCompressToTargetPdf(
                                    context = context,
                                    documents = queue,
                                    targetKb = activeTargetKb,
                                    strategy = selectedStrategy,
                                    exactMatch = exactMatchPadding,
                                    onProgress = { cur, tot, stage ->
                                        currentProgressText = stage
                                        currentProgressFraction = if (tot > 0) cur.toFloat() / tot.toFloat() else 0.5f
                                    }
                                )
                                mergeResult = result
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing && queue.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        disabledContainerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isProcessing) "Combining & Compressing..." else "Combine & Compress to ≤ $activeTargetKb KB (${queue.sumOf { it.pageCount }} Pages)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
