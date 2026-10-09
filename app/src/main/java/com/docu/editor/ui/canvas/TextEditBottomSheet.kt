package com.docu.editor.ui.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.font.FontClassification
import com.docu.editor.core.font.FontMatcher
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.model.FontWeightEstimate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditBottomSheet(
    item: DetectedTextItem,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    allDetectedItems: List<DetectedTextItem> = emptyList(),
    onCopyText: (String) -> Unit = {},
    onQuickErase: () -> Unit = {},
    onQuickHighlight: () -> Unit = {},
    onQuickBlackout: () -> Unit = {},
    onApplyEdit: (
        newText: String,
        fontClassification: FontClassification,
        isBold: Boolean,
        sizeMultiplier: Float,
        colorRgb: Int,
        alignment: android.graphics.Paint.Align,
        useCloudAi: Boolean,
        cameraBlurSigma: Float,
        paperBlendStrength: Float,
        baselineNudgePx: Float,
        inkToneDarkness: Float
    ) -> Unit
) {
    // 100% Context-Aware Document & Line Typography Auto Detection:
    val sameLineItems = remember(item.id, allDetectedItems) {
        val lineH = maxOf(16f, item.boundingBox.height().toFloat())
        val rad = Math.toRadians(item.rotationAngle.toDouble())
        val cosA = kotlin.math.cos(rad).toFloat()
        val sinA = kotlin.math.sin(rad).toFloat()
        val targetCenterX = item.boundingBox.exactCenterX()
        val targetCenterY = item.boundingBox.exactCenterY()
        allDetectedItems.filter {
            if (it.id == item.id) return@filter false
            val vOverlap = minOf(it.boundingBox.bottom, item.boundingBox.bottom) - maxOf(it.boundingBox.top, item.boundingBox.top)
            val minH = minOf(it.boundingBox.height(), item.boundingBox.height()).toFloat()
            val hasVerticalOverlap = vOverlap > minH * 0.25f
            val dx = it.boundingBox.exactCenterX() - targetCenterX
            val dy = it.boundingBox.exactCenterY() - targetCenterY
            val perpDist = kotlin.math.abs(-dx * sinA + dy * cosA)
            hasVerticalOverlap || (perpDist < maxOf(28f, lineH * 1.45f))
        }
    }

    val documentDominantFont = remember(allDetectedItems) {
        if (allDetectedItems.isEmpty()) null
        else {
            val serifCount = allDetectedItems.count { it.typography.isSerif || it.typography.terminalFlareRatio >= 1.28f }
            if (serifCount.toFloat() / allDetectedItems.size >= 0.65f) FontClassification.SERIF
            else null
        }
    }

    val lineDominantFont = remember(sameLineItems) {
        if (sameLineItems.isEmpty()) null
        else {
            if (sameLineItems.any { it.text.any { c -> c in '\u0900'..'\u097F' } }) {
                FontClassification.DEVANAGARI
            } else {
                val serifInLine = sameLineItems.count { it.typography.isSerif || it.typography.terminalFlareRatio >= 1.28f }
                val serifRatio = serifInLine.toFloat() / sameLineItems.size
                if (serifRatio >= 0.60f) FontClassification.SERIF else FontClassification.SANS_SERIF
            }
        }
    }

    val autoDetectedClassification = remember(item.id, documentDominantFont, lineDominantFont) {
        FontMatcher.classifyFromMetrics(
            text = item.text,
            metrics = item.typography,
            bounds = item.boundingBox,
            documentDominantFont = documentDominantFont,
            lineDominantFont = lineDominantFont
        )
    }

    val lineIsBold = sameLineItems.isNotEmpty() && (
        sameLineItems.count {
            (it.typography.estimatedFontWeight in listOf(FontWeightEstimate.BOLD, FontWeightEstimate.EXTRA_BOLD, FontWeightEstimate.MEDIUM)) ||
            it.typography.strokeWidthRatio >= 0.10f ||
            it.typography.glyphDensity >= 0.20f ||
            it.typography.numericFontWeight >= 600
        }.toFloat() / sameLineItems.size >= 0.40f
    )

    val itemIsBold = (item.typography.estimatedFontWeight in listOf(FontWeightEstimate.BOLD, FontWeightEstimate.EXTRA_BOLD, FontWeightEstimate.MEDIUM)) ||
        item.typography.strokeWidthRatio >= 0.10f ||
        item.typography.glyphDensity >= 0.20f ||
        item.typography.numericFontWeight >= 600

    val autoDetectedBold = remember(item.id, sameLineItems) {
        if (sameLineItems.isNotEmpty()) lineIsBold else itemIsBold
    }

    // Full text selection on open: typing instantly replaces the original word cleanly
    var editedText by remember(item.id) {
        mutableStateOf(
            TextFieldValue(
                text = item.text,
                selection = TextRange(0, item.text.length)
            )
        )
    }
    var selectedFontType by remember(item.id) { mutableStateOf(autoDetectedClassification) }
    var isBold by remember(item.id) { mutableStateOf(autoDetectedBold) }
    var sizeMultiplier by remember(item.id) { mutableFloatStateOf(1.0f) }
    var selectedColorRgb by remember(item.id) { mutableIntStateOf(item.inkColorRgb) } // Default to document original ink
    var selectedAlignment by remember(item.id) { mutableStateOf(android.graphics.Paint.Align.LEFT) }
    var showCustomColorPicker by remember(item.id) { mutableStateOf(false) }
    var customHue by remember(item.id) { mutableFloatStateOf(0f) }

    // Guaranteed Autofetch Synchronization: Whenever the tapped item changes,
    // immediately re-populate the text, font, weight, and ink color.
    LaunchedEffect(item.id, item.text) {
        editedText = TextFieldValue(
            text = item.text,
            selection = TextRange(0, item.text.length)
        )
        selectedFontType = autoDetectedClassification
        isBold = autoDetectedBold
        selectedColorRgb = item.inkColorRgb
        sizeMultiplier = 1.0f
    }

    // Pro Realism & Camera Photo Tuning States:
    var cameraBlurSigma by remember(item.id) { mutableFloatStateOf(0.0f) }
    var paperBlendStrength by remember(item.id) { mutableFloatStateOf(0.0f) }
    var baselineNudgePx by remember(item.id) { mutableFloatStateOf(0f) }
    var inkToneDarkness by remember(item.id) { mutableFloatStateOf(1.0f) }
    var showProRealismControls by remember(item.id) { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val fontMatcher = remember(context) { FontMatcher(context) }
    val previewTypeface = remember(selectedFontType, isBold) {
        fontMatcher.getDocumentTypeface(selectedFontType, isBold)
    }
    val previewFontFamily = remember(previewTypeface) {
        FontFamily(previewTypeface)
    }

    val isDark = isSystemInDarkTheme()
    val sheetBg = if (isDark) Color(0xFF1E293B) else Color.White
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val surfaceBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val surfaceBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val inputBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val chipUnselectedBg = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val chipUnselectedText = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = sheetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Document Text",
                        color = textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Auto-matched to original document typography",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Tap Instant Quick Actions Strip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Copy
                    QuickActionButton(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy",
                        tint = Color(0xFF2563EB),
                        onClick = { onCopyText(item.text); onDismiss() }
                    )
                    // 2. Erase / Whiteout
                    QuickActionButton(
                        icon = Icons.Default.Clear,
                        label = "Erase",
                        tint = Color(0xFF64748B),
                        onClick = { onQuickErase(); onDismiss() }
                    )
                    // 3. Highlight
                    QuickActionButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Highlight",
                        tint = Color(0xFFD97706),
                        onClick = { onQuickHighlight(); onDismiss() }
                    )
                    // 4. Blackout / Redact
                    QuickActionButton(
                        icon = Icons.Default.Block,
                        label = "Blackout",
                        tint = Color(0xFFDC2626),
                        onClick = { onQuickBlackout(); onDismiss() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-Detection Badge (CamScanner style indicator)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.4f) else Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2563EB) else Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-Matched Font: ${selectedFontType.displayName} • ${if (isBold) "Bold" else "Regular"}",
                        color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Text Input Field
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                label = { Text("Replacement Text", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold) },
                placeholder = { Text("Type new word to replace '${item.text}'", color = textSecondary) },
                trailingIcon = {
                    if (editedText.text.isNotEmpty()) {
                        IconButton(onClick = { editedText = TextFieldValue("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = textSecondary)
                        }
                    }
                },
                singleLine = false,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary,
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = surfaceBorder,
                    focusedContainerColor = inputBg,
                    unfocusedContainerColor = inputBg
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 1-Tap Fast Text Utility Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Wipe / Clear Text
                AssistChip(
                    onClick = { editedText = TextFieldValue("") },
                    label = { Text("✕ Clear Text", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (editedText.text.isEmpty()) Color(0xFFEF4444).copy(alpha = 0.15f) else surfaceBg,
                        labelColor = if (editedText.text.isEmpty()) Color(0xFFEF4444) else textPrimary
                    )
                )
                // 2. Reset / Revert to original
                AssistChip(
                    onClick = { editedText = TextFieldValue(item.text, selection = TextRange(0, item.text.length)) },
                    label = { Text("↺ Reset: \"${item.text}\"", fontSize = 11.sp) }
                )
                // 3. UPPERCASE
                AssistChip(
                    onClick = { editedText = editedText.copy(text = editedText.text.uppercase()) },
                    label = { Text("AA UPPERCASE", fontSize = 11.sp) }
                )
                // 4. lowercase
                AssistChip(
                    onClick = { editedText = editedText.copy(text = editedText.text.lowercase()) },
                    label = { Text("aa lowercase", fontSize = 11.sp) }
                )
                // 5. Title Case / Capitalize
                AssistChip(
                    onClick = {
                        editedText = editedText.copy(
                            text = editedText.text.split(" ").joinToString(" ") { word ->
                                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                            }
                        )
                    },
                    label = { Text("Aa Capitalize", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Comparison Preview Box
            Surface(
                color = surfaceBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "LIVE VISUAL COMPARISON:",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Original Document:",
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = item.text,
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(surfaceBorder)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Preview:",
                                color = Color(0xFF3B82F6),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = editedText.text.ifEmpty { "Sample" },
                                color = Color(selectedColorRgb),
                                fontSize = (14 * sizeMultiplier).sp,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = previewFontFamily
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Typography Controls Card
            Surface(
                color = surfaceBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Family:", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${selectedFontType.category} • ${selectedFontType.displayName}",
                            color = Color(0xFF3B82F6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FontClassification.entries.forEach { font ->
                            FilterChip(
                                selected = selectedFontType == font,
                                onClick = { selectedFontType = font },
                                label = { Text(font.displayName.substringBefore(" /"), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2563EB),
                                    selectedLabelColor = Color.White,
                                    containerColor = chipUnselectedBg,
                                    labelColor = chipUnselectedText
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: Alignment Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Alignment:", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple(android.graphics.Paint.Align.LEFT, "Left", "⫷"),
                                Triple(android.graphics.Paint.Align.CENTER, "Center", "≡"),
                                Triple(android.graphics.Paint.Align.RIGHT, "Right", "⫸")
                            ).forEach { (align, name, symbol) ->
                                val isSelected = selectedAlignment == align
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF2563EB) else chipUnselectedBg,
                                    modifier = Modifier.clickable { selectedAlignment = align }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(symbol, fontSize = 13.sp, color = if (isSelected) Color.White else chipUnselectedText, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(name, fontSize = 11.sp, color = if (isSelected) Color.White else chipUnselectedText, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 3: Bold Toggle + Color Palette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bold Toggle Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isBold) Color(0xFF2563EB) else chipUnselectedBg,
                            modifier = Modifier.clickable { isBold = !isBold }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.FormatBold,
                                    contentDescription = "Bold",
                                    tint = if (isBold) Color.White else chipUnselectedText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBold) "BOLD (On)" else "Normal",
                                    color = if (isBold) Color.White else chipUnselectedText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // Ink Color Palette Scrollable
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Text("Ink:", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            // Document Original Ink
                            ColorChipLight(
                                color = item.inkColor,
                                label = "Orig",
                                isSelected = selectedColorRgb == item.inkColorRgb && !showCustomColorPicker,
                                onClick = { selectedColorRgb = item.inkColorRgb; showCustomColorPicker = false }
                            )

                            // Charcoal / Laser Black
                            ColorChipLight(
                                color = Color(0xFF222428),
                                label = "Charcoal",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(34, 36, 40) && !showCustomColorPicker,
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(34, 36, 40); showCustomColorPicker = false }
                            )

                            // Navy Blue
                            ColorChipLight(
                                color = Color(0xFF0F2B5C),
                                label = "Navy",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(15, 43, 92) && !showCustomColorPicker,
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(15, 43, 92); showCustomColorPicker = false }
                            )

                            // Legal Blue
                            ColorChipLight(
                                color = Color(0xFF1E3A8A),
                                label = "Blue",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(30, 58, 138) && !showCustomColorPicker,
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(30, 58, 138); showCustomColorPicker = false }
                            )

                            // Red Official Stamp
                            ColorChipLight(
                                color = Color(0xFFDC2626),
                                label = "Red",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(220, 38, 38) && !showCustomColorPicker,
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(220, 38, 38); showCustomColorPicker = false }
                            )

                            // Green Stamp
                            ColorChipLight(
                                color = Color(0xFF16A34A),
                                label = "Green",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(22, 163, 74) && !showCustomColorPicker,
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(22, 163, 74); showCustomColorPicker = false }
                            )

                            // Purple Ink
                            ColorChipLight(
                                color = Color(0xFF7E22CE),
                                label = "Purple",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(126, 34, 206) && !showCustomColorPicker,
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(126, 34, 206); showCustomColorPicker = false }
                            )

                            // Brown Vintage
                            ColorChipLight(
                                color = Color(0xFF9A3412),
                                label = "Brown",
                                isSelected = selectedColorRgb == android.graphics.Color.rgb(154, 52, 18) && !showCustomColorPicker,
                                onClick = { selectedColorRgb = android.graphics.Color.rgb(154, 52, 18); showCustomColorPicker = false }
                            )

                            // Custom Spectrum Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showCustomColorPicker) Color(0xFF2563EB) else chipUnselectedBg,
                                modifier = Modifier.clickable { showCustomColorPicker = !showCustomColorPicker }
                            ) {
                                Text(
                                    text = "🎨 Custom",
                                    fontSize = 11.sp,
                                    color = if (showCustomColorPicker) Color.White else chipUnselectedText,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (showCustomColorPicker) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hue Spectrum:", fontSize = 11.sp, color = textSecondary, fontWeight = FontWeight.SemiBold)
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(selectedColorRgb))
                                    .border(1.dp, Color.Gray, CircleShape)
                            )
                        }
                        Slider(
                            value = customHue,
                            onValueChange = { hue ->
                                customHue = hue
                                val hsv = floatArrayOf(hue, 0.85f, 0.65f)
                                selectedColorRgb = android.graphics.Color.HSVToColor(hsv)
                            },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(selectedColorRgb),
                                activeTrackColor = Color(0xFF6366F1),
                                inactiveTrackColor = surfaceBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 4: Fine-Tune Font Size Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatSize, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Size: ${(sizeMultiplier * 100).toInt()}%",
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = chipUnselectedBg,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { sizeMultiplier = (sizeMultiplier - 0.05f).coerceAtLeast(0.60f) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = textPrimary, modifier = Modifier.size(16.dp))
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = chipUnselectedBg,
                                modifier = Modifier
                                    .clickable { sizeMultiplier = 1.0f }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = chipUnselectedText)
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = chipUnselectedBg,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { sizeMultiplier = (sizeMultiplier + 0.05f).coerceAtMost(1.80f) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = textPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Slider(
                        value = sizeMultiplier,
                        onValueChange = { sizeMultiplier = it },
                        valueRange = 0.60f..1.80f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2563EB),
                            activeTrackColor = Color(0xFF2563EB),
                            inactiveTrackColor = surfaceBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pro Camera Photo Realism & Micro-Alignment Tuning Card
            Surface(
                color = surfaceBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, surfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProRealismControls = !showProRealismControls },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera Photo Realism & Alignment Tuning", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(if (showProRealismControls) "Hide ▲" else "Adjust ▼", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (showProRealismControls) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // 1. Camera Blur / Edge Softness Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Edge Softness (Camera Blur):", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${String.format(java.util.Locale.US, "%.1f", cameraBlurSigma)} px ${if (cameraBlurSigma <= 0.2f) "(Digital Vector)" else "(Camera Lens)"}",
                                color = Color(0xFF2563EB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = cameraBlurSigma,
                            onValueChange = { cameraBlurSigma = it },
                            valueRange = 0f..2.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB),
                                inactiveTrackColor = surfaceBorder
                            )
                        )

                        // 2. Microscopic Baseline Nudge (±px)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Baseline Nudge (Vertical Lock):", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipUnselectedBg,
                                    modifier = Modifier.size(26.dp).clickable { baselineNudgePx -= 1f }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    }
                                }
                                Text(
                                    text = "${baselineNudgePx.toInt()} px",
                                    color = Color(0xFF2563EB),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipUnselectedBg,
                                    modifier = Modifier.size(26.dp).clickable { baselineNudgePx += 1f }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = chipUnselectedBg,
                                    modifier = Modifier.clickable { baselineNudgePx = 0f }.padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text("0", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chipUnselectedText)
                                }
                            }
                        }
                        Slider(
                            value = baselineNudgePx,
                            onValueChange = { baselineNudgePx = it },
                            valueRange = -8f..8f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB),
                                inactiveTrackColor = surfaceBorder
                            )
                        )

                        // 3. Paper Blending & Grain Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Paper Grain & Fiber Blending:", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(paperBlendStrength * 100).toInt()}%", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = paperBlendStrength,
                            onValueChange = { paperBlendStrength = it },
                            valueRange = 0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB),
                                inactiveTrackColor = surfaceBorder
                            )
                        )

                        // 4. Ink Tone Match Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ink Tone Darkness / Match:", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(inkToneDarkness * 100).toInt()}%", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = inkToneDarkness,
                            onValueChange = { inkToneDarkness = it },
                            valueRange = 0.5f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB),
                                inactiveTrackColor = surfaceBorder
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Instant Local Apply (Default CamScanner Auto Mode)
                Button(
                    onClick = {
                        onApplyEdit(editedText.text, selectedFontType, isBold, sizeMultiplier, selectedColorRgb, selectedAlignment, false, cameraBlurSigma, paperBlendStrength, baselineNudgePx, inkToneDarkness)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto Apply", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Smart Cloud AI Apply
                Button(
                    onClick = {
                        onApplyEdit(editedText.text, selectedFontType, isBold, sizeMultiplier, selectedColorRgb, selectedAlignment, true, cameraBlurSigma, paperBlendStrength, baselineNudgePx, inkToneDarkness)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Smart AI Magic", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ColorChipLight(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF94A3B8),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}
