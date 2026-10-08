package com.docu.editor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.DialogProperties
import com.docu.editor.core.ocr.model.DetectedTextItem

/**
 * Enterprise CamScanner-Grade OCR Text Extraction & Recognition Dialog.
 * Aggregates all recognized text in reading order, provides instant 1-tap "Copy All",
 * text search, document export as plain text (.txt), Microsoft Word (.docx),
 * and Gemini Vision Handwriting transcription AI.
 */
@Composable
fun OcrTextExtractDialog(
    detectedItems: List<DetectedTextItem>,
    onCopyAll: (String) -> Unit,
    onShareTxt: (String) -> Unit,
    onShareCsv: (String) -> Unit = {},
    onShareDocx: (String) -> Unit = {},
    onHandwritingAiRequest: ((onComplete: (String?) -> Unit) -> Unit)? = null,
    onOfflineHandwritingRequest: ((onComplete: (String?) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    // Reconstruct full text in natural reading order (line by line)
    val reconstructedText = remember(detectedItems) {
        if (detectedItems.isEmpty()) {
            "No text recognized on this page. Tap 'Scan' or ensure lighting is clear."
        } else {
            // Sort top-to-bottom, then left-to-right
            val sorted = detectedItems.sortedWith(
                compareBy<DetectedTextItem> { it.boundingBox.top / 20 }
                    .thenBy { it.boundingBox.left }
            )
            val sb = StringBuilder()
            var lastTop = -1
            for (item in sorted) {
                if (lastTop != -1 && kotlin.math.abs(item.boundingBox.top - lastTop) > item.boundingBox.height() * 0.8f) {
                    sb.append("\n")
                } else if (lastTop != -1) {
                    sb.append(" ")
                }
                sb.append(item.text)
                lastTop = item.boundingBox.top
            }
            sb.toString()
        }
    }

    var editableText by remember(reconstructedText) { mutableStateOf(reconstructedText) }
    var searchQuery by remember { mutableStateOf("") }

    val wordCount = remember(editableText) {
        editableText.split("\\s+".toRegex()).count { it.isNotBlank() }
    }
    val charCount = remember(editableText) { editableText.length }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxSize(0.90f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF)
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Extract Text (OCR)",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Text(
                                        text = "DOCUEDIT AI OCR",
                                        color = Color(0xFF15803D),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$wordCount words • $charCount characters • ${detectedItems.size} blocks",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val matchCount = remember(searchQuery, editableText) {
                    if (searchQuery.isBlank()) 0
                    else Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(editableText).count()
                }

                // Search Bar with Live Occurrence Count
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search word in extracted text...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (matchCount > 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = "$matchCount found",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (matchCount > 0) Color(0xFF15803D) else Color(0xFFB91C1C),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Proofreading Text Area
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        OutlinedTextField(
                            value = editableText,
                            onValueChange = { editableText = it },
                            modifier = Modifier.fillMaxSize(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Row 1: Premium AI & Word Superpowers (CamScanner)
                var isTranscribing by remember { mutableStateOf(false) }
                var isOfflineTranscribing by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 100% Offline Handwriting OCR (No internet)
                    Button(
                        onClick = {
                            if (onOfflineHandwritingRequest != null && !isOfflineTranscribing) {
                                isOfflineTranscribing = true
                                onOfflineHandwritingRequest { result ->
                                    isOfflineTranscribing = false
                                    if (!result.isNullOrBlank()) {
                                        editableText = result
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp)
                    ) {
                        if (isOfflineTranscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Transcribing...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Text(text = "✍️ Offline HW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // 2. Cloud AI Vision
                    OutlinedButton(
                        onClick = {
                            if (onHandwritingAiRequest != null && !isTranscribing) {
                                isTranscribing = true
                                onHandwritingAiRequest { result ->
                                    isTranscribing = false
                                    if (!result.isNullOrBlank()) {
                                        editableText = result
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF7C3AED).copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        if (isTranscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF7C3AED),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Cloud...", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                        } else {
                            Text(text = "⚡ Cloud AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                        }
                    }

                    // 3. Export Word (.docx)
                    Button(
                        onClick = {
                            onShareDocx(editableText)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "📄 Word", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Buttons (Copy, TXT, Excel CSV)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy All to Clipboard
                    Button(
                        onClick = {
                            onCopyAll(editableText)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy Text",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Share as TXT
                    OutlinedButton(
                        onClick = {
                            onShareTxt(editableText)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TXT File",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Export Table to Excel (CSV)
                    Button(
                        onClick = {
                            val csv = convertDetectedItemsToCsv(detectedItems)
                            onShareCsv(csv)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .weight(1.15f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Excel (CSV)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * CamScanner-Grade Table to Excel (CSV) tabular reconstruction algorithm.
 * Groups detected OCR text items by their vertical row coordinates,
 * sorts each row from left to right into columns, and generates standard CSV.
 */
private fun convertDetectedItemsToCsv(items: List<DetectedTextItem>): String {
    if (items.isEmpty()) return ""
    val sortedY = items.sortedBy { it.boundingBox.top }
    val rows = mutableListOf<MutableList<DetectedTextItem>>()

    for (item in sortedY) {
        val matchingRow = rows.find { row ->
            val avgCenterY = row.map { it.boundingBox.centerY() }.average()
            val height = item.boundingBox.height().coerceAtLeast(18)
            kotlin.math.abs(item.boundingBox.centerY() - avgCenterY) <= height * 0.65f
        }
        if (matchingRow != null) {
            matchingRow.add(item)
        } else {
            rows.add(mutableListOf(item))
        }
    }

    rows.sortBy { row -> row.minOf { it.boundingBox.top } }
    val sb = StringBuilder()
    for (row in rows) {
        row.sortBy { it.boundingBox.left }
        val line = row.joinToString(",") { item ->
            val escaped = item.text.replace("\"", "\"\"")
            if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
                "\"$escaped\""
            } else {
                escaped
            }
        }
        sb.append(line).append("\n")
    }
    return sb.toString()
}
