package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import com.docu.editor.core.tools.PassportPhotoBgRemover
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docu.editor.core.tools.CamScannerToolsEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IdPhotoMakerDialog(
    initialBitmap: Bitmap?,
    onPickPhotoClicked: () -> Unit,
    onSaveToGallery: (Bitmap, String) -> Unit,
    onExportPdf: (Bitmap, String) -> Unit,
    onSharePrint: (Bitmap) -> Unit,
    onEditInCanvas: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var activeBitmap by remember { mutableStateOf(initialBitmap) }
    var selectedSize by remember { mutableStateOf(CamScannerToolsEngine.IdPhotoSize.PASSPORT_INDIA_US) }
    var selectedBgColor by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    var enableAiBgCutout by remember { mutableStateOf(true) }
    var isSegmenting by remember { mutableStateOf(false) }
    var segmentedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var selectedSheetType by remember { mutableStateOf(CamScannerToolsEngine.PrintSheetType.SINGLE) }
    var customCopies by remember { mutableIntStateOf(6) }
    var drawCutGuides by remember { mutableStateOf(true) }
    var addGovtStrip by remember { mutableStateOf(false) }
    var candidateName by remember { mutableStateOf("") }
    val todayFormatted = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) }
    var dateOfPhoto by remember { mutableStateOf(todayFormatted) }

    val bgColors = listOf(
        Pair("Studio White", android.graphics.Color.WHITE),
        Pair("Light Blue", android.graphics.Color.parseColor("#BAE6FD")),
        Pair("Royal Blue", android.graphics.Color.parseColor("#1D4ED8")),
        Pair("Studio Red", android.graphics.Color.parseColor("#DC2626")),
        Pair("Visa Grey", android.graphics.Color.parseColor("#E2E8F0"))
    )

    fun renderIdPhoto(source: Bitmap? = segmentedBitmap ?: activeBitmap) {
        val src = source ?: return
        val single = CamScannerToolsEngine.createIdPhoto(
            sourceBitmap = src,
            size = selectedSize,
            backgroundColor = if (enableAiBgCutout) android.graphics.Color.TRANSPARENT else selectedBgColor,
            addBorder = true,
            candidateName = if (addGovtStrip) candidateName else null,
            dateOfPhoto = if (addGovtStrip) dateOfPhoto else null
        )
        processedPhoto = when (selectedSheetType) {
            CamScannerToolsEngine.PrintSheetType.SINGLE -> single
            CamScannerToolsEngine.PrintSheetType.PHOTO_PAPER_4X6 ->
                CamScannerToolsEngine.createPrintableSheet(single, selectedSheetType, 8, drawCutGuides)
            CamScannerToolsEngine.PrintSheetType.A4_SHEET ->
                CamScannerToolsEngine.createPrintableSheet(single, selectedSheetType, 32, drawCutGuides)
            CamScannerToolsEngine.PrintSheetType.CUSTOM ->
                CamScannerToolsEngine.createPrintableSheet(single, selectedSheetType, customCopies, drawCutGuides)
        }
    }

    LaunchedEffect(initialBitmap) {
        activeBitmap = initialBitmap
    }

    LaunchedEffect(activeBitmap, enableAiBgCutout, selectedBgColor) {
        val src = activeBitmap ?: return@LaunchedEffect
        if (enableAiBgCutout) {
            isSegmenting = true
            val studioBg = when (selectedBgColor) {
                android.graphics.Color.WHITE -> PassportPhotoBgRemover.StudioBackground.WHITE
                android.graphics.Color.parseColor("#BAE6FD") -> PassportPhotoBgRemover.StudioBackground.LIGHT_BLUE
                android.graphics.Color.parseColor("#1D4ED8") -> PassportPhotoBgRemover.StudioBackground.DEEP_BLUE
                android.graphics.Color.parseColor("#DC2626") -> PassportPhotoBgRemover.StudioBackground.STUDIO_RED
                android.graphics.Color.parseColor("#E2E8F0") -> PassportPhotoBgRemover.StudioBackground.VISA_GRAY
                else -> PassportPhotoBgRemover.StudioBackground.WHITE
            }
            val cleaned = PassportPhotoBgRemover.removeBackgroundAndReplace(src, studioBg)
            segmentedBitmap = cleaned
            isSegmenting = false
            renderIdPhoto(cleaned)
        } else {
            segmentedBitmap = src
            renderIdPhoto(src)
        }
    }

    LaunchedEffect(
        selectedSize,
        selectedSheetType,
        customCopies,
        drawCutGuides,
        addGovtStrip,
        candidateName,
        dateOfPhoto
    ) {
        renderIdPhoto(segmentedBitmap ?: activeBitmap)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEDE9FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Badge,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Passport & ID Photo Studio",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Single click photo sheet & instant gallery export",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Preview Box
                val currentPreview = processedPhoto ?: activeBitmap
                if (currentPreview != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = currentPreview.asImageBitmap(),
                            contentDescription = "Passport Photo Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPickPhotoClicked() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Select selfie or portrait photo", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sheet Format Selector (Single, 4x6" Sheet [8], A4 Sheet [32], Custom)
                Text("Select Print Sheet Format", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sheets = listOf(
                        Triple(CamScannerToolsEngine.PrintSheetType.SINGLE, "Single", "1 Photo"),
                        Triple(CamScannerToolsEngine.PrintSheetType.PHOTO_PAPER_4X6, "4×6\" Sheet", "8 Photos"),
                        Triple(CamScannerToolsEngine.PrintSheetType.A4_SHEET, "A4 Sheet", "32 Photos"),
                        Triple(CamScannerToolsEngine.PrintSheetType.CUSTOM, "Custom", "$customCopies Photos")
                    )

                    for ((type, title, subtitle) in sheets) {
                        val isSelected = selectedSheetType == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedSheetType = type
                                    renderIdPhoto()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = subtitle,
                                    color = if (isSelected) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }

                // If Custom copies chosen, show copy count slider
                if (selectedSheetType == CamScannerToolsEngine.PrintSheetType.CUSTOM) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Number of Copies: $customCopies", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 6, 8, 12, 16, 24).forEach { count ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (customCopies == count) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (customCopies == count) Color(0xFF7C3AED) else Color.Transparent),
                                    modifier = Modifier.clickable {
                                        customCopies = count
                                        renderIdPhoto()
                                    }
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (customCopies == count) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Slider(
                        value = customCopies.toFloat(),
                        onValueChange = {
                            customCopies = it.toInt()
                            renderIdPhoto()
                        },
                        valueRange = 1f..32f,
                        steps = 30,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7C3AED),
                            activeTrackColor = Color(0xFF7C3AED)
                        )
                    )
                }

                // Cutting guidelines toggle (for sheets)
                if (selectedSheetType != CamScannerToolsEngine.PrintSheetType.SINGLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Dashed Cutting Guidelines", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Subtle hairline scissor marks between photos", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = drawCutGuides,
                            onCheckedChange = {
                                drawCutGuides = it
                                renderIdPhoto()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C3AED))
                        )
                    }
                }

                // AI Neural Background Cutout Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (enableAiBgCutout) Color(0xFFF5F3FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (enableAiBgCutout) Color(0xFF7C3AED) else Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("AI Background Cutout", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF7C3AED)
                                    ) {
                                        Text("STUDIO PRO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Text("Auto cuts room/bed background & sets studio backdrop", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isSegmenting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF7C3AED)
                            )
                        } else {
                            Switch(
                                checked = enableAiBgCutout,
                                onCheckedChange = { enableAiBgCutout = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C3AED))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Background Color Selector
                Text("Studio Background Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for ((name, colorVal) in bgColors) {
                        val isSelected = selectedBgColor == colorVal
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(
                                    BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) Color(0xFF7C3AED) else Color(0xFFCBD5E1)
                                    ),
                                    RoundedCornerShape(20.dp)
                                )
                                .background(if (isSelected) Color(0xFFF5F3FF) else MaterialTheme.colorScheme.surface)
                                .clickable {
                                    selectedBgColor = colorVal
                                    renderIdPhoto()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(1.dp, Color(0xFF94A3B8), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Standard Dimension Sizes
                Text("Passport / ID Standard Sizes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (sizePreset in CamScannerToolsEngine.IdPhotoSize.entries) {
                        val isSelected = selectedSize == sizePreset
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFF5F3FF) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Color(0xFF7C3AED) else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedSize = sizePreset
                                    renderIdPhoto()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(sizePreset.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                                Text("${sizePreset.widthMm}x${sizePreset.heightMm} mm", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Govt Exam Name & Date of Photo (DOP) Bottom Strip
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (addGovtStrip) Color(0xFFF5F3FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, if (addGovtStrip) Color(0xFF7C3AED) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Govt Exam Name & DOP Strip",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "SSC / UPSC / IBPS mandatory photo strip",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = addGovtStrip,
                                onCheckedChange = {
                                    addGovtStrip = it
                                    renderIdPhoto()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C3AED))
                            )
                        }

                        if (addGovtStrip) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = candidateName,
                                onValueChange = {
                                    candidateName = it
                                    renderIdPhoto()
                                },
                                label = { Text("Candidate Name (e.g. AMIT KUMAR)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF7C3AED),
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = dateOfPhoto,
                                    onValueChange = {
                                        dateOfPhoto = it
                                        renderIdPhoto()
                                    },
                                    label = { Text("Date of Photo (DD-MM-YYYY)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF7C3AED),
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    )
                                )
                                Button(
                                    onClick = {
                                        dateOfPhoto = todayFormatted
                                        renderIdPhoto()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEDE9FE)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Text("Today", color = Color(0xFF7C3AED), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons: Direct 1-Click Gallery & PDF Export
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ready = processedPhoto ?: activeBitmap

                    // 1. Primary Save to Gallery Button
                    Button(
                        onClick = {
                            if (ready != null) {
                                val namePrefix = if (selectedSheetType == CamScannerToolsEngine.PrintSheetType.SINGLE) "Passport_Photo" else "Passport_Sheet"
                                onSaveToGallery(ready, namePrefix)
                                onDismiss()
                            }
                        },
                        enabled = ready != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save to Gallery (High-Res JPG)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. Export Printable PDF Button
                    Button(
                        onClick = {
                            if (ready != null) {
                                val namePrefix = if (selectedSheetType == CamScannerToolsEngine.PrintSheetType.SINGLE) "Passport_Photo" else "Passport_Sheet"
                                onExportPdf(ready, namePrefix)
                                onDismiss()
                            }
                        },
                        enabled = ready != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Printable PDF (Downloads)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // 3. Secondary Actions (Share/Print, Edit in Canvas, Pick Another)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (ready != null) {
                                    onSharePrint(ready)
                                }
                            },
                            enabled = ready != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print / Share", fontSize = 11.5.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (ready != null) {
                                    onEditInCanvas(ready)
                                    onDismiss()
                                }
                            },
                            enabled = ready != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit in Canvas", fontSize = 11.5.sp)
                        }

                        OutlinedButton(
                            onClick = onPickPhotoClicked,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Pick Photo", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}
