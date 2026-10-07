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
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
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
import com.docu.editor.domain.model.ShapeType

enum class EditorCategory(val label: String, val icon: ImageVector) {
    ELEMENTS("Elements", Icons.Default.Category),
    TEXT("Text", Icons.Default.TextFields),
    MAGIC("Magic", Icons.Default.AutoAwesome),
    ADJUST("Adjust", Icons.Default.Tune),
    BRAND("Brand", Icons.Default.Palette),
    LAYERS("Layers", Icons.Default.Layers),
    ANIMATE("Animate", Icons.Default.Animation),
    ENHANCE("Enhance", Icons.Default.AutoFixHigh),
    TOOLS("Tools", Icons.Default.Description)
}

/**
 * Canva Pro Flagship Obsidian Studio Bottom Dock.
 * Highly responsive, categorized multi-tab dock with glassmorphic accents,
 * contextual tool tuning sub-bars, and 1-tap Pro feature triggers.
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
    onAutoOrientClicked: () -> Unit = {},
    onInteractiveCropClicked: () -> Unit,
    onExtractTextClicked: () -> Unit,
    onSignatureClicked: () -> Unit,
    onPagesOverviewClicked: () -> Unit = {},
    onCompressClicked: () -> Unit,
    onTargetSizeClicked: () -> Unit = {},
    onExportClicked: () -> Unit,
    onCloudSyncClicked: () -> Unit = {},
    selectedLassoCount: Int = 0,
    onMergeEditLasso: () -> Unit = {},
    onWhiteoutLasso: () -> Unit = {},
    onClearLasso: () -> Unit = {},
    onWatermarkClicked: () -> Unit = {},
    onBookDewarpClicked: () -> Unit = {},
    whiteoutBrushRadius: Float = 22f,
    onWhiteoutBrushRadiusChanged: (Float) -> Unit = {},
    markupColorRgb: Int = android.graphics.Color.rgb(255, 235, 59),
    markupStrokeWidth: Float = 28f,
    onMarkupColorChanged: (Int) -> Unit = {},
    onMarkupStrokeWidthChanged: (Float) -> Unit = {},
    penColorRgb: Int = android.graphics.Color.rgb(220, 38, 38),
    penStrokeWidth: Float = 6f,
    onPenColorChanged: (Int) -> Unit = {},
    onPenStrokeWidthChanged: (Float) -> Unit = {},
    selectedShapeType: ShapeType = ShapeType.RECTANGLE,
    onShapeTypeSelected: (ShapeType) -> Unit = {},
    shapeStrokeWidth: Float = 6f,
    onShapeStrokeWidthChanged: (Float) -> Unit = {},
    shapeStrokeColorRgb: Int = android.graphics.Color.rgb(220, 38, 38),
    onShapeStrokeColorChanged: (Int) -> Unit = {},
    shapeFillColor: Int? = null,
    onShapeFillColorChanged: (Int?) -> Unit = {},
    onAddShapeLayerClicked: (ShapeType) -> Unit = {},
    onBackgroundRemovalClicked: () -> Unit = {},
    onInsertImageClicked: () -> Unit = {},
    onCanvaStickersClicked: () -> Unit = {},
    magicEraserBrushRadius: Float = 28f,
    onMagicEraserBrushRadiusChanged: (Float) -> Unit = {},
    isCloudAiEraserEnabled: Boolean = true,
    hasGeminiApiKey: Boolean = false,
    onToggleCloudAiEraser: () -> Unit = {},
    onOpenCloudAiSettings: () -> Unit = {},
    onApplyShadowRemover: () -> Unit = {},
    onApplyFingerRemover: () -> Unit = {},
    onCanvaMockupsClicked: () -> Unit = {},
    onCanvaTextStudioClicked: () -> Unit = {},
    onCanvaBrandKitClicked: () -> Unit = {},
    onCanvaMagicStudioClicked: () -> Unit = {},
    onCanvaAdjustClicked: () -> Unit = {},
    onCanvaAnimateClicked: () -> Unit = {},
    onCanvaLayersClicked: () -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }

    var activeCategory by remember(activeMode, showFiltersRow) {
        mutableStateOf(
            when {
                showFiltersRow || activeMode == EditorToolMode.FILTERS -> EditorCategory.ENHANCE
                activeMode in listOf(EditorToolMode.MAGIC_ERASER, EditorToolMode.WHITEOUT, EditorToolMode.REDACTION, EditorToolMode.LASSO_SELECT) -> EditorCategory.MAGIC
                activeMode in listOf(EditorToolMode.ADD_TEXT, EditorToolMode.TEXT_EDIT) -> EditorCategory.TEXT
                activeMode == EditorToolMode.SHAPES -> EditorCategory.ELEMENTS
                else -> EditorCategory.ELEMENTS
            }
        )
    }

    Surface(
        color = Color(0xFF11131C),
        shadowElevation = 20.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, Color(0xFF26293A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // =========================================================================
            // 1. Contextual Control Sub-Bars (Visible when fine-tuning active mode)
            // =========================================================================

            // Magic Filters Row
            AnimatedVisibility(visible = showFiltersRow || activeMode == EditorToolMode.FILTERS) {
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
                                    val chipText = when (filter) {
                                        DocumentFilterMode.ORIGINAL -> "🖼️ Original"
                                        DocumentFilterMode.MAGIC_COLOR -> "✨ Magic Color"
                                        DocumentFilterMode.SHADOW_REMOVER -> "🌤️ Remove Shadow"
                                        DocumentFilterMode.WATERMARK_REMOVER -> "🧹 Erase Watermark"
                                        DocumentFilterMode.FINGER_REMOVER -> "🖐️ Remove Fingers"
                                        DocumentFilterMode.BOOK_DEWARP -> "📖 Flatten Page"
                                        DocumentFilterMode.CLEAN_BW -> "📄 Clean B&W"
                                        DocumentFilterMode.GRAYSCALE -> "🔘 Grayscale"
                                        DocumentFilterMode.VIVID_DOC -> "🎨 Vivid Doc"
                                        DocumentFilterMode.STUDIO_WHITE -> "💡 Studio White"
                                        DocumentFilterMode.BLUEPRINT -> "📐 Blueprint"
                                        DocumentFilterMode.SEPIA -> "📜 Vintage Sepia"
                                        DocumentFilterMode.INK_SHARPENER -> "🖋️ Ink Anti-Smudge"
                                    }
                                    Text(
                                        text = chipText,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
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
                                    selectedContainerColor = if (isMagicColor) Color(0xFF059669) else Color(0xFF8B5CF6),
                                    containerColor = Color(0xFF1E2130)
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
                        Text(
                            text = "Bright: ${(brightness * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        Slider(
                            value = brightness,
                            onValueChange = {
                                brightness = it
                                onBrightnessContrastChanged(brightness, contrast)
                            },
                            valueRange = -0.5f..0.5f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF059669),
                                activeTrackColor = Color(0xFF059669),
                                inactiveTrackColor = Color(0xFF2A2D3D)
                            )
                        )
                    }
                }
            }

            // Shapes Selector & Config Sub-Bar
            AnimatedVisibility(visible = activeMode == EditorToolMode.SHAPES) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShapeType.entries.forEach { shape ->
                            val isSel = (shape == selectedShapeType)
                            FilterChip(
                                selected = isSel,
                                onClick = { onShapeTypeSelected(shape) },
                                label = { Text(shape.displayName, fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF1E2130),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onAddShapeLayerClicked(selectedShapeType) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("+ Add as Moveable Layer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Color(0xFFDC2626),
                                Color(0xFF2563EB),
                                Color(0xFF059669),
                                Color(0xFFF59E0B),
                                Color(0xFF8B5CF6),
                                Color.Black,
                                Color.White
                            ).forEach { color ->
                                val rgb = android.graphics.Color.rgb(
                                    (color.red * 255).toInt(),
                                    (color.green * 255).toInt(),
                                    (color.blue * 255).toInt()
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (rgb == shapeStrokeColorRgb) 2.5.dp else 1.dp,
                                            color = if (rgb == shapeStrokeColorRgb) Color.White else Color(0xFF475569),
                                            shape = CircleShape
                                        )
                                        .clickable { onShapeStrokeColorChanged(rgb) }
                                )
                            }
                        }
                    }
                }
            }

            // Eraser Brush Sub-Bar
            AnimatedVisibility(visible = activeMode == EditorToolMode.MAGIC_ERASER) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Brush: ${magicEraserBrushRadius.toInt()}px", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Slider(
                        value = magicEraserBrushRadius,
                        onValueChange = onMagicEraserBrushRadiusChanged,
                        valueRange = 10f..80f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFF59E0B),
                            activeTrackColor = Color(0xFFF59E0B),
                            inactiveTrackColor = Color(0xFF2A2D3D)
                        )
                    )
                }
            }

            // =========================================================================
            // 2. Action Tools Row (Changes dynamically based on active Category)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (activeCategory) {
                    EditorCategory.ELEMENTS -> {
                        ToolDockButton(
                            icon = Icons.Default.Category,
                            label = "14 Shapes",
                            isSelected = activeMode == EditorToolMode.SHAPES,
                            onClick = { onModeSelected(EditorToolMode.SHAPES) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.FilterFrames,
                            label = "Frames & 3D",
                            isSelected = false,
                            onClick = onCanvaMockupsClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Security,
                            label = "Stickers & Seals",
                            isSelected = false,
                            onClick = onCanvaStickersClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.ViewInAr,
                            label = "Insert Photo",
                            isSelected = false,
                            onClick = onInsertImageClicked
                        )
                    }

                    EditorCategory.TEXT -> {
                        ToolDockButton(
                            icon = Icons.Default.TextFields,
                            label = "Text Studio",
                            isSelected = false,
                            onClick = onCanvaTextStudioClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Edit,
                            label = "Quick Text",
                            isSelected = activeMode == EditorToolMode.ADD_TEXT,
                            onClick = { onModeSelected(EditorToolMode.ADD_TEXT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Magic Write",
                            isSelected = false,
                            onClick = onCanvaTextStudioClicked
                        )
                    }

                    EditorCategory.MAGIC -> {
                        ToolDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Magic Studio",
                            isSelected = false,
                            onClick = onCanvaMagicStudioClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Magic Eraser",
                            isSelected = activeMode == EditorToolMode.MAGIC_ERASER,
                            onClick = { onModeSelected(EditorToolMode.MAGIC_ERASER) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.ContentCut,
                            label = "BG Remover",
                            isSelected = false,
                            onClick = onBackgroundRemovalClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Clear,
                            label = "Whiteout",
                            isSelected = activeMode == EditorToolMode.WHITEOUT,
                            onClick = { onModeSelected(EditorToolMode.WHITEOUT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Block,
                            label = "Redact",
                            isSelected = activeMode == EditorToolMode.REDACTION,
                            onClick = { onModeSelected(EditorToolMode.REDACTION) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.SelectAll,
                            label = "Lasso Pick",
                            isSelected = activeMode == EditorToolMode.LASSO_SELECT,
                            onClick = { onModeSelected(EditorToolMode.LASSO_SELECT) }
                        )
                    }

                    EditorCategory.ADJUST -> {
                        ToolDockButton(
                            icon = Icons.Default.Tune,
                            label = "Adjust Sliders",
                            isSelected = false,
                            onClick = onCanvaAdjustClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Magic Color",
                            isSelected = activeFilter == DocumentFilterMode.MAGIC_COLOR,
                            onClick = { onFilterSelected(DocumentFilterMode.MAGIC_COLOR) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Clear,
                            label = "Erase Shadow",
                            isSelected = false,
                            onClick = onApplyShadowRemover
                        )
                        ToolDockButton(
                            icon = Icons.Default.Clear,
                            label = "Remove Fingers",
                            isSelected = false,
                            onClick = onApplyFingerRemover
                        )
                    }

                    EditorCategory.BRAND -> {
                        ToolDockButton(
                            icon = Icons.Default.Palette,
                            label = "Brand Kit",
                            isSelected = false,
                            onClick = onCanvaBrandKitClicked
                        )
                    }

                    EditorCategory.LAYERS -> {
                        ToolDockButton(
                            icon = Icons.Default.Layers,
                            label = "Layer Stack",
                            isSelected = false,
                            onClick = onCanvaLayersClicked
                        )
                    }

                    EditorCategory.ANIMATE -> {
                        ToolDockButton(
                            icon = Icons.Default.Animation,
                            label = "Motion Presets",
                            isSelected = false,
                            onClick = onCanvaAnimateClicked
                        )
                    }

                    EditorCategory.ENHANCE -> {
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Filters",
                            isSelected = showFiltersRow || activeMode == EditorToolMode.FILTERS,
                            onClick = { onModeSelected(EditorToolMode.FILTERS) }
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            label = "Rotate",
                            isSelected = false,
                            onClick = onRotateClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Auto Orient",
                            isSelected = false,
                            onClick = onAutoOrientClicked
                        )
                    }

                    EditorCategory.TOOLS -> {
                        ToolDockButton(
                            icon = Icons.Default.Crop,
                            label = "Crop",
                            isSelected = false,
                            onClick = onInteractiveCropClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Description,
                            label = "OCR Text",
                            isSelected = false,
                            onClick = onExtractTextClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Security,
                            label = "Sign & Stamp",
                            isSelected = false,
                            onClick = onSignatureClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Draw,
                            label = "Watermark",
                            isSelected = false,
                            onClick = onWatermarkClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "Book Dewarp",
                            isSelected = false,
                            onClick = onBookDewarpClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Compress,
                            label = "Compress",
                            isSelected = false,
                            onClick = onCompressClicked
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // =========================================================================
            // 3. Category Dock Navigation (Canva Style Category Dock at Bottom)
            // =========================================================================
            Surface(
                color = Color(0xFF161824),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF26293A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorCategory.entries.forEach { category ->
                        val isSelected = category == activeCategory
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF8B5CF6) else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { activeCategory = category }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = category.label,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Export Button Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF059669).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF059669)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onExportClicked() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Document",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Export",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
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
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1E2130))
                .border(
                    width = 1.dp,
                    color = if (isSelected) Color(0xFFC084FC) else Color(0xFF2A2D3D),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFFCBD5E1),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFFC084FC) else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
