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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.docu.editor.core.tools.PassportAttireEngine
import com.docu.editor.core.tools.PassportEnhanceEngine
import com.docu.editor.core.tools.PassportPhotoBgRemover
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IdPhotoMakerDialog(
    initialBitmap: Bitmap?,
    onPickPhotoClicked: () -> Unit,
    onSaveToGallery: (Bitmap, String, Int?) -> Unit,
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

    // Studio Facial Sharpening & Lighting
    var selectedSharpness by remember { mutableStateOf(PassportEnhanceEngine.StudioSharpness.STUDIO_PRO) }
    var enhancedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Formal Attire & Suit Replacement
    var selectedAttire by remember { mutableStateOf(PassportAttireEngine.AttireType.NONE) }
    var attireOffsetY by remember { mutableFloatStateOf(0f) }
    var attireOffsetX by remember { mutableFloatStateOf(0f) }
    var attireScale by remember { mutableFloatStateOf(1.0f) }
    var attireAnchor by remember { mutableStateOf<PassportAttireEngine.FaceAnchor?>(null) }
    var attireBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isFramingIcao by remember { mutableStateOf(false) }

    // Border and Cutting guides
    var borderThickness by remember { mutableFloatStateOf(2f) }
    var selectedBorderColor by remember { mutableIntStateOf(android.graphics.Color.BLACK) }
    var drawCutGuides by remember { mutableStateOf(true) }

    // Print sheet format
    var selectedSheetType by remember { mutableStateOf(CamScannerToolsEngine.PrintSheetType.SINGLE) }
    var customCopies by remember { mutableIntStateOf(6) }

    // Govt exam DOP strip
    var addGovtStrip by remember { mutableStateOf(false) }
    var candidateName by remember { mutableStateOf("") }
    val todayFormatted = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) }
    var dateOfPhoto by remember { mutableStateOf(todayFormatted) }
    var dopTextScale by remember { mutableFloatStateOf(1.0f) }

    // Export Quality Preset: 0 = 300 DPI Lab Print, 1 = <=50 KB Govt Exam, 2 = <=20 KB Portal
    var selectedExportPreset by remember { mutableIntStateOf(0) }

    var processedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val bgColors = listOf(
        Pair("Studio White", android.graphics.Color.WHITE),
        Pair("Light Blue", android.graphics.Color.parseColor("#BAE6FD")),
        Pair("Royal Blue", android.graphics.Color.parseColor("#1D4ED8")),
        Pair("Studio Red", android.graphics.Color.parseColor("#DC2626")),
        Pair("Visa Grey", android.graphics.Color.parseColor("#E2E8F0"))
    )

    fun renderIdPhoto(source: Bitmap? = attireBitmap ?: enhancedBitmap ?: segmentedBitmap ?: activeBitmap) {
        val src = source ?: return
        val single = CamScannerToolsEngine.createIdPhoto(
            sourceBitmap = src,
            size = selectedSize,
            backgroundColor = if (enableAiBgCutout) android.graphics.Color.TRANSPARENT else selectedBgColor,
            addBorder = (borderThickness > 0f),
            borderWidthPx = borderThickness,
            borderColor = selectedBorderColor,
            candidateName = if (addGovtStrip) candidateName else null,
            dateOfPhoto = if (addGovtStrip) dateOfPhoto else null,
            dopTextScale = dopTextScale
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

    // Pipeline Step 1: Background Removal
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
        } else {
            segmentedBitmap = src
        }
    }

    // Pipeline Step 2: Facial Sharpening & Lighting
    LaunchedEffect(segmentedBitmap, selectedSharpness) {
        val src = segmentedBitmap ?: return@LaunchedEffect
        val enhanced = PassportEnhanceEngine.enhancePortrait(src, selectedSharpness)
        enhancedBitmap = enhanced
    }

    // Pipeline Step 3: Formal Attire / Suit Overlay with Face Landmark Anchor
    LaunchedEffect(enhancedBitmap) {
        val src = enhancedBitmap ?: return@LaunchedEffect
        attireAnchor = PassportAttireEngine.detectFaceAnchor(src)
    }

    LaunchedEffect(enhancedBitmap, selectedAttire, attireOffsetY, attireOffsetX, attireScale, attireAnchor) {
        val src = enhancedBitmap ?: return@LaunchedEffect
        val suited = PassportAttireEngine.applyAttire(
            sourceBitmap = src,
            attire = selectedAttire,
            verticalShiftRatio = attireOffsetY,
            shoulderScale = attireScale,
            horizontalShiftRatio = attireOffsetX,
            anchor = attireAnchor
        )
        attireBitmap = suited
        renderIdPhoto(suited)
    }

    // Pipeline Step 4: Size, Sheet & Borders
    LaunchedEffect(
        selectedSize,
        selectedSheetType,
        customCopies,
        drawCutGuides,
        addGovtStrip,
        candidateName,
        dateOfPhoto,
        dopTextScale,
        borderThickness,
        selectedBorderColor
    ) {
        renderIdPhoto()
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
                .padding(vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
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
                                .size(42.dp)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Pro Passport & ID Studio",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF7C3AED)
                                ) {
                                    Text("PRO AI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                            Text(
                                "AI Cutout, Suit Change, Studio Sharpening & <=50KB Export",
                                fontSize = 11.sp,
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
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = currentPreview.asImageBitmap(),
                            contentDescription = "Passport Photo Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        if (isSegmenting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Applying Studio Backdrop...", color = Color.White, fontSize = 11.5.sp)
                                }
                            }
                        }
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

                // 1-Tap ICAO Passport Auto-Framing
                if (activeBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEDE9FE),
                        border = BorderStroke(1.dp, Color(0xFFC4B5FD)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isFramingIcao) {
                                coroutineScope.launch {
                                    val src = activeBitmap ?: return@launch
                                    isFramingIcao = true
                                    val targetAspect = selectedSize.widthMm.toFloat() / selectedSize.heightMm.toFloat()
                                    val framed = PassportAttireEngine.autoFrameIcaoPassport(src, targetAspect)
                                    activeBitmap = framed
                                    isFramingIcao = false
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isFramingIcao) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFF7C3AED), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analyzing Face Biometrics & Auto-Framing...", fontSize = 11.5.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📐 1-Tap ICAO Auto-Framing (72% Face Standard)", fontSize = 11.5.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 1. AI Background Cutout & Studio Lighting
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
                                Text("AI Studio Background & Lighting", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Removes home room/bed clutter with smooth studio lighting", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = enableAiBgCutout,
                            onCheckedChange = { enableAiBgCutout = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C3AED))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Studio Background Colors
                Text("Studio Backdrop Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(1.dp, Color(0xFF94A3B8), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(name, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Studio Facial Sharpening & Lighting Balancer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("✨ Facial Clarity & Sharpness", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(selectedSharpness.displayName, fontSize = 11.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (level in PassportEnhanceEngine.StudioSharpness.entries) {
                        val isSelected = selectedSharpness == level
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedSharpness = level }
                        ) {
                            Text(
                                text = level.displayName,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Formal Attire & Suit Change
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("👔 Formal Attire / Suit Change", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (selectedAttire != PassportAttireEngine.AttireType.NONE) {
                        Text(
                            "Clear Attire",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { selectedAttire = PassportAttireEngine.AttireType.NONE }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (attire in PassportAttireEngine.AttireType.entries) {
                        val isSelected = selectedAttire == attire
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.5.dp, if (isSelected) Color(0xFF7C3AED) else Color.Transparent),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedAttire = attire }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(attire.iconEmoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    attire.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // If attire selected, show Collar Height, Shoulder Scale & Horizontal Shift sliders
                if (selectedAttire != PassportAttireEngine.AttireType.NONE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🎯 Collar Alignment & Fit", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    if (attireAnchor != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFDCFCE7)) {
                                            Text("CHIN LOCKED", fontSize = 8.5.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text(
                                    "Reset Fit",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF7C3AED),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        attireOffsetY = 0f
                                        attireOffsetX = 0f
                                        attireScale = 1.0f
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Collar Height (Neck alignment)", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${(attireOffsetY * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = attireOffsetY,
                                onValueChange = { attireOffsetY = it },
                                valueRange = -0.15f..0.15f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF7C3AED), activeTrackColor = Color(0xFF7C3AED))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Shoulder Width", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${(attireScale * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = attireScale,
                                onValueChange = { attireScale = it },
                                valueRange = 0.85f..1.25f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF7C3AED), activeTrackColor = Color(0xFF7C3AED))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Horizontal Shift (Center balance)", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${(attireOffsetX * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = attireOffsetX,
                                onValueChange = { attireOffsetX = it },
                                valueRange = -0.10f..0.10f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF7C3AED), activeTrackColor = Color(0xFF7C3AED))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Photo Border Controls
                Text("🖼️ Photo Border Width & Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val borderOptions = listOf(
                        Pair("None (0px)", 0f),
                        Pair("Thin (1px)", 1f),
                        Pair("Studio (2px)", 2f),
                        Pair("Bold (4px)", 4f)
                    )
                    for ((label, px) in borderOptions) {
                        val isSelected = borderThickness == px
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { borderThickness = px }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                if (borderThickness > 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Border Color:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val borderColors = listOf(
                            Pair("Studio Black", android.graphics.Color.BLACK),
                            Pair("Dark Slate", android.graphics.Color.parseColor("#1E293B")),
                            Pair("Classic Navy", android.graphics.Color.parseColor("#1E3A8A")),
                            Pair("Slate Gray", android.graphics.Color.parseColor("#CBD5E1")),
                            Pair("Crisp White", android.graphics.Color.WHITE)
                        )
                        for ((name, cVal) in borderColors) {
                            val isSel = selectedBorderColor == cVal
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF7C3AED) else Color.Transparent),
                                modifier = Modifier.clickable { selectedBorderColor = cVal }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(cVal))
                                            .border(0.5.dp, Color(0xFF64748B), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Standard Dimension Sizes
                Text("Passport / ID Dimension Standards", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(6.dp))
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
                                .clickable { selectedSize = sizePreset }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(sizePreset.displayName, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                                Text("${sizePreset.widthMm}×${sizePreset.heightMm} mm", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Print Sheet Format Selector
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
                                .clickable { selectedSheetType = type }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
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

                if (selectedSheetType == CamScannerToolsEngine.PrintSheetType.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Copy Count:", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val copiesOptions = listOf(2, 4, 6, 8, 12, 16, 24, 32)
                        for (count in copiesOptions) {
                            val isSel = customCopies == count
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { customCopies = count }
                            ) {
                                Text(
                                    text = "$count copies",
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                if (selectedSheetType != CamScannerToolsEngine.PrintSheetType.SINGLE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Dashed Scissor Cutting Lines", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Text("Subtle dashed lines between photos on sheet", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = drawCutGuides,
                            onCheckedChange = { drawCutGuides = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C3AED))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Govt Exam Name & Date of Photo (DOP) Bottom Strip
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (addGovtStrip) Color(0xFFF5F3FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, if (addGovtStrip) Color(0xFF7C3AED) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Govt Exam Name & DOP Strip", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("SSC / UPSC / IBPS mandatory photo strip", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = addGovtStrip,
                                onCheckedChange = { addGovtStrip = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7C3AED))
                            )
                        }

                        if (addGovtStrip) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = candidateName,
                                onValueChange = { candidateName = it },
                                label = { Text("Candidate Name (e.g. AMIT KUMAR)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF7C3AED), unfocusedBorderColor = Color(0xFFCBD5E1))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = dateOfPhoto,
                                    onValueChange = { dateOfPhoto = it },
                                    label = { Text("Date of Photo (DD-MM-YYYY)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF7C3AED), unfocusedBorderColor = Color(0xFFCBD5E1))
                                )
                                Button(
                                    onClick = { dateOfPhoto = todayFormatted },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEDE9FE)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(50.dp)
                                ) {
                                    Text("Today", color = Color(0xFF7C3AED), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("DOP Strip Font Scale", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${(dopTextScale * 100).toInt()}%", fontSize = 10.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = dopTextScale,
                                onValueChange = { dopTextScale = it },
                                valueRange = 0.75f..1.25f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF7C3AED), activeTrackColor = Color(0xFF7C3AED))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 8. Output Quality & File Size Options (Direct Govt 50KB Solver)
                Text("💾 Target Export Quality & File Size", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        Triple(0, "300 DPI Lab Print", "100% Quality"),
                        Triple(1, "Govt Exam (<=50KB)", "Strict <=50 KB"),
                        Triple(2, "Small (<=20KB)", "Tight portal")
                    )
                    for ((idx, title, desc) in presets) {
                        val isSelected = selectedExportPreset == idx
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.5.dp, if (isSelected) Color(0xFF7C3AED) else Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedExportPreset = idx }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = desc,
                                    color = if (isSelected) Color(0xFF6D28D9) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 9. Action Buttons
                val ready = processedPhoto ?: activeBitmap
                val targetKb = when (selectedExportPreset) {
                    1 -> 50
                    2 -> 20
                    else -> null
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Save to Gallery
                    Button(
                        onClick = {
                            if (ready != null) {
                                val namePrefix = if (selectedSheetType == CamScannerToolsEngine.PrintSheetType.SINGLE) "Passport_Photo" else "Passport_Sheet"
                                onSaveToGallery(ready, namePrefix, targetKb)
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
                        val saveText = if (targetKb != null) "Save to Gallery (Strict <=$targetKb KB JPG)" else "Save to Gallery (300 DPI Lab Print)"
                        Text(saveText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export Printable PDF
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
                        Text("Export Printable PDF (Downloads)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // Secondary Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (ready != null) onSharePrint(ready) },
                            enabled = ready != null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print / Share", fontSize = 11.sp)
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
                            Text("Edit in Canvas", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onPickPhotoClicked,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Pick Photo", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
