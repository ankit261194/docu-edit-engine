package com.docu.editor.ui.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.domain.model.DocumentFilterMode
import com.docu.editor.domain.model.EditorToolMode

/**
 * Enterprise CamScanner-Grade Document Bottom Bar Dock.
 * Puts all CamScanner core capabilities within 1 tap:
 * Edit Text, Extract Text (OCR), Magic Filters, Sign & Stamp, 4-Corner Crop,
 * Whiteout Eraser, Watermark, Pages Manager, Rotate, PDF Tools, and Save/Share.
 */
@Composable
fun DocumentBottomBar(
    activeMode: EditorToolMode,
    activeFilter: DocumentFilterMode,
    showFiltersRow: Boolean,
    onModeSelected: (EditorToolMode) -> Unit,
    onFilterSelected: (DocumentFilterMode) -> Unit,
    onBrightnessContrastChanged: (brightness: Float, contrast: Float) -> Unit = { _, _ -> },
    onRotateClicked: () -> Unit,
    onInteractiveCropClicked: () -> Unit,
    onExtractTextClicked: () -> Unit,
    onSignatureClicked: () -> Unit,
    onPagesOverviewClicked: () -> Unit = {},
    onCompressClicked: () -> Unit,
    onExportClicked: () -> Unit,
    selectedLassoCount: Int = 0,
    onMergeEditLasso: () -> Unit = {},
    onWhiteoutLasso: () -> Unit = {},
    onClearLasso: () -> Unit = {},
    onWatermarkClicked: () -> Unit = {},
    onBookDewarpClicked: () -> Unit = {},
    whiteoutBrushRadius: Float = 22f,
    onWhiteoutBrushRadiusChanged: (Float) -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // CamScanner Filters Row + Fine-tune Sliders
            AnimatedVisibility(visible = showFiltersRow) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentFilterMode.values().forEach { filter ->
                            val isSelected = filter == activeFilter
                            val isMagicColor = filter == DocumentFilterMode.MAGIC_COLOR
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    brightness = 0f
                                    contrast = 1f
                                    onFilterSelected(filter)
                                },
                                label = {
                                    Text(
                                        text = if (isMagicColor) "✨ Magic Color" else filter.displayName,
                                        color = if (isSelected) Color.White else Color(0xFF1E293B),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isMagicColor) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (isMagicColor) Color(0xFFD97706) else Color(0xFF2563EB),
                                    containerColor = if (isMagicColor) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☀️ Brightness: ${brightness.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                        Text("🌓 Contrast: ${String.format("%.1f", contrast)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it },
                            onValueChangeFinished = { onBrightnessContrastChanged(brightness, contrast) },
                            valueRange = -50f..50f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB)
                            )
                        )
                        Slider(
                            value = contrast,
                            onValueChange = { contrast = it },
                            onValueChangeFinished = { onBrightnessContrastChanged(brightness, contrast) },
                            valueRange = 0.6f..1.8f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB)
                            )
                        )
                    }
                }
            }

            // Whiteout Brush Size Slider
            AnimatedVisibility(visible = activeMode == EditorToolMode.WHITEOUT) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Brush: ${whiteoutBrushRadius.toInt()}px",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = whiteoutBrushRadius,
                            onValueChange = onWhiteoutBrushRadiusChanged,
                            valueRange = 8f..60f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB)
                            )
                        )
                    }
                }
            }

            // Lasso Multi-Select Floating Action Strip
            AnimatedVisibility(visible = activeMode == EditorToolMode.LASSO_SELECT && selectedLassoCount > 0) {
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$selectedLassoCount blocks selected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onMergeEditLasso,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Merge & Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onWhiteoutLasso,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Erase All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onClearLasso,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF94A3B8)),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Tier 1: Category Selector Pills
            var selectedCategory by remember { mutableStateOf(BottomBarCategory.EDIT_OCR) }

            // Auto-switch category based on active tools
            LaunchedEffect(activeMode, showFiltersRow) {
                if (showFiltersRow) {
                    selectedCategory = BottomBarCategory.ENHANCE_FILTER
                } else if (activeMode in listOf(EditorToolMode.TEXT_EDIT, EditorToolMode.ADD_TEXT, EditorToolMode.WHITEOUT, EditorToolMode.LASSO_SELECT)) {
                    selectedCategory = BottomBarCategory.EDIT_OCR
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BottomBarCategory.values().forEach { cat ->
                    val isCatSelected = (selectedCategory == cat)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCatSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC))
                            .border(
                                1.dp,
                                if (isCatSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedCategory = cat
                                if (cat == BottomBarCategory.ENHANCE_FILTER && !showFiltersRow) {
                                    onModeSelected(EditorToolMode.FILTERS)
                                }
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.title,
                            fontSize = 10.5.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCatSelected) Color(0xFF1D4ED8) else Color(0xFF64748B),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tier 2: Category Action Dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (selectedCategory) {
                    BottomBarCategory.EDIT_OCR -> {
                        ToolDockButton(
                            icon = Icons.Default.Edit,
                            label = "Edit Text",
                            isSelected = activeMode == EditorToolMode.TEXT_EDIT,
                            onClick = { onModeSelected(EditorToolMode.TEXT_EDIT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Description,
                            label = "Extract OCR",
                            isSelected = false,
                            onClick = onExtractTextClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Check,
                            label = "Add Text",
                            isSelected = activeMode == EditorToolMode.ADD_TEXT,
                            onClick = { onModeSelected(EditorToolMode.ADD_TEXT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Clear,
                            label = "Whiteout",
                            isSelected = activeMode == EditorToolMode.WHITEOUT,
                            onClick = { onModeSelected(EditorToolMode.WHITEOUT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.SelectAll,
                            label = "Lasso",
                            isSelected = activeMode == EditorToolMode.LASSO_SELECT,
                            onClick = { onModeSelected(EditorToolMode.LASSO_SELECT) }
                        )
                    }
                    BottomBarCategory.ENHANCE_FILTER -> {
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Magic Filters",
                            isSelected = showFiltersRow,
                            onClick = { onModeSelected(EditorToolMode.FILTERS) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Crop,
                            label = "Crop / Deskew",
                            isSelected = false,
                            onClick = onInteractiveCropClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            label = "Rotate 90°",
                            isSelected = false,
                            onClick = onRotateClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "Book Dewarp",
                            isSelected = false,
                            onClick = onBookDewarpClicked
                        )
                    }
                    BottomBarCategory.SIGN_PROTECT -> {
                        ToolDockButton(
                            icon = Icons.Default.Draw,
                            label = "Sign & Stamp",
                            isSelected = false,
                            onClick = onSignatureClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Security,
                            label = "Watermark",
                            isSelected = false,
                            onClick = onWatermarkClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Compress,
                            label = "Compress PDF",
                            isSelected = false,
                            onClick = onCompressClicked
                        )
                    }
                    BottomBarCategory.PAGES_SHARE -> {
                        ToolDockButton(
                            icon = Icons.Default.Layers,
                            label = "Pages (${selectedLassoCount.let { "" }})",
                            isSelected = false,
                            onClick = onPagesOverviewClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Share,
                            label = "Save & Share",
                            isSelected = false,
                            onClick = onExportClicked
                        )
                    }
                }
            }
        }
    }
}

enum class BottomBarCategory(val title: String) {
    EDIT_OCR("✏️ Edit & OCR"),
    ENHANCE_FILTER("✨ Enhance"),
    SIGN_PROTECT("🖋️ Sign/Protect"),
    PAGES_SHARE("📑 Pages/Share")
}

@Composable
private fun ToolDockButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF2563EB) else Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF334155),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
