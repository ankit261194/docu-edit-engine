package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.min

import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.docu.editor.core.signature.SignatureVaultManager
import com.docu.editor.core.signature.SavedSignatureItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignatureDialog(
    extractedBitmap: Bitmap?,
    onPickSourceImage: () -> Unit,
    onExtractSignatureClicked: (inkColor: Int) -> Unit,
    onExtractStampClicked: (isRed: Boolean) -> Unit,
    onApplyToDocument: (Bitmap) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Signature, 1: Stamp, 2: Seal, 3: Vault
    var selectedColorIndex by remember { mutableIntStateOf(0) } // 0: Blue, 1: Black, 2: Red
    var recentVaultItems by remember { mutableStateOf<List<Pair<SavedSignatureItem, Bitmap>>>(emptyList()) }

    LaunchedEffect(selectedTab) {
        val loaded = SignatureVaultManager.loadAll(context).take(5)
        val list = mutableListOf<Pair<SavedSignatureItem, Bitmap>>()
        for (item in loaded) {
            val bmp = SignatureVaultManager.loadBitmap(item)
            if (bmp != null) list.add(Pair(item, bmp))
        }
        recentVaultItems = list
    }

    val inkColors = listOf(
        Pair("Navy Blue", android.graphics.Color.rgb(10, 35, 120)),
        Pair("Pure Black", android.graphics.Color.rgb(15, 15, 15)),
        Pair("Official Red", android.graphics.Color.rgb(190, 25, 35))
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (selectedTab == 0) Icons.Default.Draw else Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFFDB2777),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedTab) {
                                0 -> "Finger Signature"
                                1 -> "Signature Extractor"
                                2 -> "Stamp Extractor"
                                else -> "Permanent Vault"
                            },
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFFF1F5F9),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFFDB2777)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "✍️ Draw",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) Color(0xFF0F172A) else Color(0xFF64748B)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "📷 Photo",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) Color(0xFF0F172A) else Color(0xFF64748B)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "🔴 Stamp",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 2) Color(0xFF0F172A) else Color(0xFF64748B)
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Text(
                                "📚 Vault",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 3) Color(0xFF0F172A) else Color(0xFF64748B)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Pick from Vault
                if (recentVaultItems.isNotEmpty() && selectedTab != 3) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "⚡ Quick Stamping (Saved in Vault):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            recentVaultItems.forEach { (item, bmp) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier
                                        .clickable {
                                            onApplyToDocument(bmp)
                                            onDismiss()
                                        }
                                        .height(34.dp)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = item.type.lowercase().replaceFirstChar { it.uppercase() },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Ink Color Selection for Drawing & Photo Signature
                if (selectedTab == 0 || selectedTab == 1) {
                    Text(
                        text = "Choose Ink Color:",
                        color = Color(0xFF1E293B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        inkColors.forEachIndexed { index, pair ->
                            val isSelected = selectedColorIndex == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedColorIndex = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pair.first,
                                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (selectedTab == 0) {
                    // Interactive Finger Signature Pad
                    SignatureDrawingPad(
                        selectedColor = Color(inkColors[selectedColorIndex].second),
                        onApplySignature = { drawnBmp ->
                            coroutineScope.launch {
                                SignatureVaultManager.saveToVault(context, drawnBmp, "SIGNATURE")
                            }
                            onApplyToDocument(drawnBmp)
                            onDismiss()
                        }
                    )
                } else if (selectedTab == 1 || selectedTab == 2) {
                    // Source Image Picker
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                            .clickable { onPickSourceImage() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (extractedBitmap != null) {
                            Image(
                                bitmap = extractedBitmap.asImageBitmap(),
                                contentDescription = "Extracted Asset",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Pick photo containing sign or stamp",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (selectedTab == 1) {
                                onExtractSignatureClicked(inkColors[selectedColorIndex].second)
                            } else {
                                onExtractStampClicked(true)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDB2777)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedTab == 1) "Extract 100% Alpha Signature" else "Isolate Red/Blue Stamp",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (extractedBitmap != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    SignatureVaultManager.saveToVault(
                                        context,
                                        extractedBitmap,
                                        if (selectedTab == 1) "SIGNATURE" else "STAMP"
                                    )
                                }
                                onApplyToDocument(extractedBitmap)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stamp On Active Document",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // Tab 3: Permanent Signature & Stamp Vault
                    SignatureVaultView(
                        onApplyToDocument = { bmp ->
                            onApplyToDocument(bmp)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureVaultView(
    onApplyToDocument: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SavedSignatureItem>>(emptyList()) }
    var loadedBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshVault() {
        scope.launch {
            val loaded = SignatureVaultManager.loadAll(context)
            items = loaded
            val bmpMap = mutableMapOf<String, Bitmap>()
            for (item in loaded) {
                val bmp = SignatureVaultManager.loadBitmap(item)
                if (bmp != null) bmpMap[item.id] = bmp
            }
            loadedBitmaps = bmpMap
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshVault()
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color(0xFFDB2777),
                modifier = Modifier.size(32.dp)
            )
        }
    } else if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "📭 Vault is Empty",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Signatures or stamps created in Draw or Photo tabs are saved here forever for 1-tap reuse!",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val bmp = loadedBitmaps[item.id]
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp, 45.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (item.type == "SIGNATURE") Color(0xFFEFF6FF) else Color(0xFFFEF2F2))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (item.type == "SIGNATURE") "✍️ SIGN" else "🔴 STAMP",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.type == "SIGNATURE") Color(0xFF2563EB) else Color(0xFFDC2626)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.timestamp)),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        SignatureVaultManager.delete(context, item.id)
                                        refreshVault()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    if (bmp != null) {
                                        onApplyToDocument(bmp)
                                    }
                                },
                                enabled = bmp != null,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureDrawingPad(
    selectedColor: Color,
    onApplySignature: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var penThickness by remember { androidx.compose.runtime.mutableFloatStateOf(4.5f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Pen Width Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pen Width: ${penThickness.toInt()}dp",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.material3.Slider(
                value = penThickness,
                onValueChange = { penThickness = it },
                valueRange = 2f..12f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentStroke = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentStroke = currentStroke + change.position
                        },
                        onDragEnd = {
                            if (currentStroke.isNotEmpty()) {
                                strokes.add(currentStroke)
                                currentStroke = emptyList()
                            }
                        },
                        onDragCancel = {
                            currentStroke = emptyList()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = penThickness.dp.toPx()
                for (stroke in strokes) {
                    for (i in 0 until stroke.size - 1) {
                        drawLine(
                            color = selectedColor,
                            start = stroke[i],
                            end = stroke[i + 1],
                            strokeWidth = strokeWidthPx,
                            cap = StrokeCap.Round
                        )
                    }
                }
                for (i in 0 until currentStroke.size - 1) {
                    drawLine(
                        color = selectedColor,
                        start = currentStroke[i],
                        end = currentStroke[i + 1],
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                }
            }

            if (strokes.isEmpty() && currentStroke.isEmpty()) {
                Text(
                    text = "✍️ Sign here with your finger",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Undo Last Stroke Button
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.size - 1)
                    }
                },
                enabled = strokes.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("↩ Undo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Clear All Button
            Button(
                onClick = {
                    strokes.clear()
                    currentStroke = emptyList()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                modifier = Modifier.weight(0.9f)
            ) {
                Text("Clear", color = Color(0xFF475569), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Apply Signature Button
            Button(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        // Render strokes to transparent Bitmap (800x400)
                        val bmp = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(bmp)
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = selectedColor.toArgb()
                            strokeWidth = penThickness * 2f
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                        }

                        var minX = Float.MAX_VALUE
                        var maxX = Float.MIN_VALUE
                        var minY = Float.MAX_VALUE
                        var maxY = Float.MIN_VALUE

                        for (stroke in strokes) {
                            for (p in stroke) {
                                if (p.x < minX) minX = p.x
                                if (p.x > maxX) maxX = p.x
                                if (p.y < minY) minY = p.y
                                if (p.y > maxY) maxY = p.y
                            }
                        }

                        val strokeW = (maxX - minX).coerceAtLeast(1f)
                        val strokeH = (maxY - minY).coerceAtLeast(1f)
                        val scale = min(720f / strokeW, 340f / strokeH).coerceAtMost(3f)
                        val offsetX = (800f - strokeW * scale) / 2f - minX * scale
                        val offsetY = (400f - strokeH * scale) / 2f - minY * scale

                        val path = android.graphics.Path()
                        for (stroke in strokes) {
                            if (stroke.isNotEmpty()) {
                                path.moveTo(stroke[0].x * scale + offsetX, stroke[0].y * scale + offsetY)
                                for (i in 1 until stroke.size) {
                                    path.lineTo(stroke[i].x * scale + offsetX, stroke[i].y * scale + offsetY)
                                }
                            }
                        }
                        c.drawPath(path, paint)
                        onApplySignature(bmp)
                    }
                },
                enabled = strokes.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.weight(1.3f)
            ) {
                Text("Apply Sign", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
