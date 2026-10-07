package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docu.editor.core.watermark.WatermarkEngine

private enum class WatermarkStudioTab(val label: String) {
    TEXT("Security Text"),
    IMAGE("Company Logo"),
    BATES("Legal Bates")
}

@Composable
fun WatermarkDialog(
    pageCount: Int = 1,
    onApplyWatermark: (WatermarkEngine.WatermarkConfig, Boolean) -> Unit,
    onApplyImageWatermark: ((WatermarkEngine.ImageWatermarkConfig, Boolean) -> Unit)? = null,
    onApplyBatesNumbering: ((WatermarkEngine.BatesNumberingConfig, Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(WatermarkStudioTab.TEXT) }

    // --- State: Security Text Watermark ---
    var watermarkText by remember { mutableStateOf("FOR VERIFICATION ONLY") }
    var isTiled by remember { mutableStateOf(true) }
    var opacityPercent by remember { mutableFloatStateOf(25f) }
    var rotationDegrees by remember { mutableFloatStateOf(-35f) }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var selectedFontFamily by remember { mutableStateOf(WatermarkEngine.WatermarkFontFamily.SANS_BOLD) }
    var isOutlineOnly by remember { mutableStateOf(false) }

    // --- State: Company Logo / Image Watermark ---
    var logoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLogoTiled by remember { mutableStateOf(false) }
    var logoOpacityPercent by remember { mutableFloatStateOf(30f) }
    var logoRotationDegrees by remember { mutableFloatStateOf(0f) }
    var logoScalePercent by remember { mutableFloatStateOf(35f) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    logoBitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {}
        }
    }

    // --- State: Legal Bates Numbering ---
    var batesPrefix by remember { mutableStateOf("CONF-") }
    var batesStartNumber by remember { mutableIntStateOf(1) }
    var batesDigits by remember { mutableIntStateOf(6) }
    var batesSuffix by remember { mutableStateOf("") }
    var batesPosition by remember { mutableStateOf(WatermarkEngine.BatesPosition.BOTTOM_RIGHT) }
    var batesIncludeBox by remember { mutableStateOf(true) }
    var batesIncludePageCounter by remember { mutableStateOf(false) }

    val presets = listOf(
        "FOR VERIFICATION ONLY",
        "CONFIDENTIAL",
        "SAMPLE COPY",
        "OFFICIAL USE ONLY",
        "DO NOT COPY",
        "BANK KYC ONLY"
    )

    val colorOptions = listOf(
        Pair("Slate Grey", Color(0xFF64748B)),
        Pair("Official Red", Color(0xFFDC2626)),
        Pair("Navy Blue", Color(0xFF1D4ED8)),
        Pair("Forest Green", Color(0xFF059669)),
        Pair("Solid Black", Color(0xFF0F172A))
    )

    val batesPrefixPresets = listOf(
        "CONF-",
        "EXHIBIT-",
        "CASE-",
        "PLAINTIFF-",
        "DEF-",
        "DOC-"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2563EB).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Watermark & Security Studio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                "Anti-Tamper Seals, Logos & Legal Bates Numbering",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Studio Tabs (Text Watermark / Logo Watermark / Legal Bates Numbering)
                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    containerColor = Color(0xFFF8FAFC),
                    contentColor = Color(0xFF2563EB),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                            color = Color(0xFF2563EB),
                            height = 3.dp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    WatermarkStudioTab.values().forEach { tab ->
                        Tab(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            text = {
                                Text(
                                    tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == tab) Color(0xFF2563EB) else Color(0xFF64748B)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (activeTab) {
                        WatermarkStudioTab.TEXT -> {
                            // --- Tab 1: Security Text Watermark ---
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Watermark Text (Macros: {DATE}, {TIME}, {PAGE})") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Dynamic Macro Insert Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Pair("+ Date", " {DATE}"),
                                    Pair("+ Time", " {TIME}"),
                                    Pair("+ Page No.", " Page {PAGE}"),
                                    Pair("+ Total Pages", " of {TOTAL_PAGES}")
                                ).forEach { (macroLabel, macroVal) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEFF6FF),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                        modifier = Modifier.clickable {
                                            watermarkText += macroVal
                                        }
                                    ) {
                                        Text(
                                            text = macroLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Presets
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presets.forEach { preset ->
                                    val isSelected = watermarkText == preset
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                        modifier = Modifier.clickable { watermarkText = preset }
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) Color.White else Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Layout Mode
                            Text("Layout Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isTiled) Color(0xFF2563EB) else Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, if (isTiled) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f).clickable { isTiled = true }
                                ) {
                                    Text(
                                        text = "📐 Repeating Grid",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTiled) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isTiled) Color(0xFF2563EB) else Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, if (!isTiled) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f).clickable { isTiled = false }
                                ) {
                                    Text(
                                        text = "🎯 Center Mark",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isTiled) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Opacity Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Opacity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                Text("${opacityPercent.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                            Slider(
                                value = opacityPercent,
                                onValueChange = { opacityPercent = it },
                                valueRange = 10f..80f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2563EB), activeTrackColor = Color(0xFF2563EB))
                            )

                            // Rotation Angle Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Rotation Angle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                Text("${rotationDegrees.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                            Slider(
                                value = rotationDegrees,
                                onValueChange = { rotationDegrees = it },
                                valueRange = -90f..90f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2563EB), activeTrackColor = Color(0xFF2563EB))
                            )

                            // Quick Angle Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(-45f, -35f, 0f, 35f, 45f).forEach { deg ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (rotationDegrees == deg) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                        modifier = Modifier.weight(1f).clickable { rotationDegrees = deg }
                                    ) {
                                        Text(
                                            text = "${deg.toInt()}°",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            color = if (rotationDegrees == deg) Color.White else Color(0xFF475569),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Colors & Hollow Outline
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ink Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Outline Only", fontSize = 11.sp, color = Color(0xFF64748B))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = isOutlineOnly,
                                        onCheckedChange = { isOutlineOnly = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2563EB))
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                colorOptions.forEachIndexed { index, pair ->
                                    val isSelected = selectedColorIndex == index
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(pair.second)
                                            .clickable { selectedColorIndex = index }
                                            .then(
                                                if (isSelected) Modifier.border(3.dp, Color(0xFF2563EB), CircleShape) else Modifier
                                            )
                                    )
                                }
                            }
                        }

                        WatermarkStudioTab.IMAGE -> {
                            // --- Tab 2: Company Logo / Image Watermark ---
                            if (logoBitmap == null) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { logoPickerLauncher.launch("image/*") },
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Select Official Logo / Seal", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                        Text("PNG or JPEG with transparent or white background", fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = logoBitmap!!.asImageBitmap(),
                                        contentDescription = "Picked Logo",
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Selected Logo", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                        Text("${logoBitmap!!.width} x ${logoBitmap!!.height} px", fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                    OutlinedButton(
                                        onClick = { logoPickerLauncher.launch("image/*") },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Change", fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Layout Mode
                            Text("Logo Layout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (!isLogoTiled) Color(0xFF2563EB) else Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, if (!isLogoTiled) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f).clickable { isLogoTiled = false }
                                ) {
                                    Text(
                                        text = "🎯 Center Emblem",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isLogoTiled) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isLogoTiled) Color(0xFF2563EB) else Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, if (isLogoTiled) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.weight(1f).clickable { isLogoTiled = true }
                                ) {
                                    Text(
                                        text = "📐 Tiled Repeating",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLogoTiled) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Scale Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Logo Size (% width)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                Text("${logoScalePercent.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                            Slider(
                                value = logoScalePercent,
                                onValueChange = { logoScalePercent = it },
                                valueRange = 15f..80f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2563EB), activeTrackColor = Color(0xFF2563EB))
                            )

                            // Opacity Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Logo Opacity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                Text("${logoOpacityPercent.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                            Slider(
                                value = logoOpacityPercent,
                                onValueChange = { logoOpacityPercent = it },
                                valueRange = 10f..90f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2563EB), activeTrackColor = Color(0xFF2563EB))
                            )

                            // Rotation Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Logo Rotation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                Text("${logoRotationDegrees.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                            Slider(
                                value = logoRotationDegrees,
                                onValueChange = { logoRotationDegrees = it },
                                valueRange = -90f..90f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2563EB), activeTrackColor = Color(0xFF2563EB))
                            )
                        }

                        WatermarkStudioTab.BATES -> {
                            // --- Tab 3: Legal Bates Numbering ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = batesPrefix,
                                    onValueChange = { batesPrefix = it },
                                    label = { Text("Prefix") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = batesStartNumber.toString(),
                                    onValueChange = { batesStartNumber = it.toIntOrNull() ?: 1 },
                                    label = { Text("Start #") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Bates Prefix Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                batesPrefixPresets.forEach { p ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (batesPrefix == p) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                        modifier = Modifier.clickable { batesPrefix = p }
                                    ) {
                                        Text(
                                            text = p,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (batesPrefix == p) Color.White else Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Digits Padding Slider
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Zero Padding Digits", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                Text("$batesDigits digits", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                            Slider(
                                value = batesDigits.toFloat(),
                                onValueChange = { batesDigits = it.toInt() },
                                valueRange = 3f..8f,
                                steps = 4,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF2563EB), activeTrackColor = Color(0xFF2563EB))
                            )

                            // Live Bates Badge Preview
                            val sampleBates = "${batesPrefix}${batesStartNumber.toString().padStart(batesDigits, '0')}${batesSuffix}" +
                                    if (batesIncludePageCounter) " | Page 1 of $pageCount" else ""
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Live Bates Stamp Preview", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                    ) {
                                        Text(
                                            text = sampleBates,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Positioning Grid (6 Zones)
                            Text("Bates Placement Zone", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        WatermarkEngine.BatesPosition.TOP_LEFT,
                                        WatermarkEngine.BatesPosition.TOP_CENTER,
                                        WatermarkEngine.BatesPosition.TOP_RIGHT
                                    ).forEach { pos ->
                                        val isSel = batesPosition == pos
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                            modifier = Modifier.weight(1f).clickable { batesPosition = pos }
                                        ) {
                                            Text(
                                                text = pos.displayName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = if (isSel) Color.White else Color(0xFF475569),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        WatermarkEngine.BatesPosition.BOTTOM_LEFT,
                                        WatermarkEngine.BatesPosition.BOTTOM_CENTER,
                                        WatermarkEngine.BatesPosition.BOTTOM_RIGHT
                                    ).forEach { pos ->
                                        val isSel = batesPosition == pos
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSel) Color(0xFF2563EB) else Color(0xFFF1F5F9),
                                            modifier = Modifier.weight(1f).clickable { batesPosition = pos }
                                        ) {
                                            Text(
                                                text = pos.displayName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = if (isSel) Color.White else Color(0xFF475569),
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Extra Toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("White Contrast Legibility Box", fontSize = 11.sp, color = Color(0xFF334155))
                                Switch(
                                    checked = batesIncludeBox,
                                    onCheckedChange = { batesIncludeBox = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2563EB))
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Include 'Page X of Y' Counter", fontSize = 11.sp, color = Color(0xFF334155))
                                Switch(
                                    checked = batesIncludePageCounter,
                                    onCheckedChange = { batesIncludePageCounter = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2563EB))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons (Single Page vs Multi-Page All)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pageCount > 1) {
                        OutlinedButton(
                            onClick = {
                                when (activeTab) {
                                    WatermarkStudioTab.TEXT -> {
                                        val config = WatermarkEngine.WatermarkConfig(
                                            text = watermarkText.trim(),
                                            isTiled = isTiled,
                                            opacityPercent = opacityPercent.toInt(),
                                            colorRgb = colorOptions[selectedColorIndex].second.toArgb(),
                                            rotationDegrees = rotationDegrees,
                                            fontFamily = selectedFontFamily,
                                            isOutlineOnly = isOutlineOnly
                                        )
                                        onApplyWatermark(config, true)
                                    }
                                    WatermarkStudioTab.IMAGE -> {
                                        val bmp = logoBitmap ?: return@OutlinedButton
                                        val config = WatermarkEngine.ImageWatermarkConfig(
                                            logoBitmap = bmp,
                                            isTiled = isLogoTiled,
                                            opacityPercent = logoOpacityPercent.toInt(),
                                            rotationDegrees = logoRotationDegrees,
                                            scalePercent = logoScalePercent.toInt()
                                        )
                                        onApplyImageWatermark?.invoke(config, true)
                                    }
                                    WatermarkStudioTab.BATES -> {
                                        val config = WatermarkEngine.BatesNumberingConfig(
                                            prefix = batesPrefix,
                                            startNumber = batesStartNumber,
                                            digitCount = batesDigits,
                                            suffix = batesSuffix,
                                            position = batesPosition,
                                            includeBackgroundBox = batesIncludeBox,
                                            includePageCounter = batesIncludePageCounter
                                        )
                                        onApplyBatesNumbering?.invoke(config, true)
                                    }
                                }
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All $pageCount Pages", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            when (activeTab) {
                                WatermarkStudioTab.TEXT -> {
                                    val config = WatermarkEngine.WatermarkConfig(
                                        text = watermarkText.trim(),
                                        isTiled = isTiled,
                                        opacityPercent = opacityPercent.toInt(),
                                        colorRgb = colorOptions[selectedColorIndex].second.toArgb(),
                                        rotationDegrees = rotationDegrees,
                                        fontFamily = selectedFontFamily,
                                        isOutlineOnly = isOutlineOnly
                                    )
                                    onApplyWatermark(config, false)
                                }
                                WatermarkStudioTab.IMAGE -> {
                                    val bmp = logoBitmap ?: return@Button
                                    val config = WatermarkEngine.ImageWatermarkConfig(
                                        logoBitmap = bmp,
                                        isTiled = isLogoTiled,
                                        opacityPercent = logoOpacityPercent.toInt(),
                                        rotationDegrees = logoRotationDegrees,
                                        scalePercent = logoScalePercent.toInt()
                                    )
                                    onApplyImageWatermark?.invoke(config, false)
                                }
                                WatermarkStudioTab.BATES -> {
                                    val config = WatermarkEngine.BatesNumberingConfig(
                                        prefix = batesPrefix,
                                        startNumber = batesStartNumber,
                                        digitCount = batesDigits,
                                        suffix = batesSuffix,
                                        position = batesPosition,
                                        includeBackgroundBox = batesIncludeBox,
                                        includePageCounter = batesIncludePageCounter
                                    )
                                    onApplyBatesNumbering?.invoke(config, false)
                                }
                            }
                            onDismiss()
                        },
                        enabled = when (activeTab) {
                            WatermarkStudioTab.TEXT -> watermarkText.isNotBlank()
                            WatermarkStudioTab.IMAGE -> logoBitmap != null
                            WatermarkStudioTab.BATES -> true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (pageCount > 1) "Apply Current Page" else "Apply Watermark",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
