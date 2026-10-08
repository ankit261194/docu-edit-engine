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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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

/**
 * Task-Driven 5-Workflow Categories inspired by Canva & CamScanner.
 * Prevents UI clutter by presenting only the relevant tools for the selected workflow.
 */
enum class WorkflowCategory(val label: String, val icon: ImageVector) {
    PDF_EDIT("PDF Edit", Icons.Default.Description),
    ERASE_CLEAN("Erase & Clean", Icons.Default.AutoFixHigh),
    DESIGN_STAMPS("Design & Stamps", Icons.Default.Palette),
    SCAN_ENHANCE("Scan & Enhance", Icons.Default.FilterFrames),
    EXPORT_CONVERT("Convert & Export", Icons.Default.Share)
}

typealias EditorCategory = WorkflowCategory

/**
 * Pro Flagship Studio Bottom Dock.
 * Highly responsive, categorized 5-tab dock with glassmorphic accents,
 * contextual tool tuning sub-bars, and crisp 👑 PRO badges on premium tools.
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
    onRubberStampClicked: () -> Unit = {},
    onIdRedactionClicked: () -> Unit = {},
    onBookSplitClicked: () -> Unit = {},
    onEraseMarksClicked: () -> Unit = {},
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
    onCanvaLayersClicked: () -> Unit = {},
    onPageDimensionsClicked: () -> Unit = {},
    onAcroFormClicked: () -> Unit = {},
    onWordExportClicked: () -> Unit = {},
    onPptxExportClicked: () -> Unit = {},
    onExcelExportClicked: () -> Unit = {},
    onPkiDigitalSignClicked: () -> Unit = {},
    onCheckUpdateClicked: () -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }

    var activeCategory by remember(activeMode, showFiltersRow) {
        mutableStateOf(
            when {
                showFiltersRow || activeMode == EditorToolMode.FILTERS -> WorkflowCategory.SCAN_ENHANCE
                activeMode in listOf(
                    EditorToolMode.MAGIC_ERASER,
                    EditorToolMode.WHITEOUT,
                    EditorToolMode.REDACTION,
                    EditorToolMode.LASSO_SELECT
                ) -> WorkflowCategory.ERASE_CLEAN
                activeMode in listOf(
                    EditorToolMode.ADD_TEXT,
                    EditorToolMode.TEXT_EDIT
                ) -> WorkflowCategory.PDF_EDIT
                activeMode in listOf(
                    EditorToolMode.SHAPES,
                    EditorToolMode.MARKUP_PEN,
                    EditorToolMode.HIGHLIGHTER
                ) -> WorkflowCategory.DESIGN_STAMPS
                else -> WorkflowCategory.PDF_EDIT
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
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentFilterMode.values().forEach { filter ->
                            val isSelected = filter == activeFilter
                            val isProFilter = filter in listOf(
                                DocumentFilterMode.MAGIC_COLOR,
                                DocumentFilterMode.STUDIO_WHITE,
                                DocumentFilterMode.VIVID_DOC,
                                DocumentFilterMode.INK_SHARPENER,
                                DocumentFilterMode.SHADOW_REMOVER,
                                DocumentFilterMode.WATERMARK_REMOVER,
                                DocumentFilterMode.FINGER_REMOVER,
                                DocumentFilterMode.BOOK_DEWARP,
                                DocumentFilterMode.BLUEPRINT
                            )
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
                                        DocumentFilterMode.ORIGINAL -> "Original"
                                        DocumentFilterMode.MAGIC_COLOR -> "Magic Color"
                                        DocumentFilterMode.PHOTO_RESTORE -> "Restore Photo"
                                        DocumentFilterMode.SHADOW_REMOVER -> "Remove Shadow"
                                        DocumentFilterMode.WATERMARK_REMOVER -> "Erase Watermark"
                                        DocumentFilterMode.FINGER_REMOVER -> "Remove Fingers"
                                        DocumentFilterMode.BOOK_DEWARP -> "Flatten Page"
                                        DocumentFilterMode.CLEAN_BW -> "Clean B&W"
                                        DocumentFilterMode.GRAYSCALE -> "Grayscale"
                                        DocumentFilterMode.VIVID_DOC -> "Vivid Doc"
                                        DocumentFilterMode.STUDIO_WHITE -> "Studio White"
                                        DocumentFilterMode.BLUEPRINT -> "Blueprint"
                                        DocumentFilterMode.SEPIA -> "Vintage Sepia"
                                        DocumentFilterMode.INK_SHARPENER -> "Ink Anti-Smudge"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = chipText,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected || isMagicColor) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        if (isProFilter) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = Color(0xFFF59E0B),
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = "PRO",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
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

            // Whiteout Brush Sub-Bar
            AnimatedVisibility(visible = activeMode == EditorToolMode.WHITEOUT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Whiteout: ${whiteoutBrushRadius.toInt()}px", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Slider(
                        value = whiteoutBrushRadius,
                        onValueChange = onWhiteoutBrushRadiusChanged,
                        valueRange = 10f..60f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF2A2D3D)
                        )
                    )
                }
            }

            // Lasso Selection Sub-Bar
            AnimatedVisibility(visible = activeMode == EditorToolMode.LASSO_SELECT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected: $selectedLassoCount words",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC084FC)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (selectedLassoCount > 0) {
                        Button(
                            onClick = onMergeEditLasso,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Merge & Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onWhiteoutLasso,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Whiteout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onClearLasso,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }
            }

            // =========================================================================
            // 2. Action Tools Row (Changes dynamically based on active Category)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (activeCategory) {
                    WorkflowCategory.PDF_EDIT -> {
                        ToolDockButton(
                            icon = Icons.Default.Edit,
                            label = "Edit Text",
                            isSelected = activeMode == EditorToolMode.TEXT_EDIT,
                            isPro = true,
                            onClick = { onModeSelected(EditorToolMode.TEXT_EDIT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.TextFields,
                            label = "Add Text",
                            isSelected = activeMode == EditorToolMode.ADD_TEXT,
                            isPro = false,
                            onClick = { onModeSelected(EditorToolMode.ADD_TEXT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Text Studio",
                            isSelected = false,
                            isPro = true,
                            onClick = onCanvaTextStudioClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.Article,
                            label = "OCR Text",
                            isSelected = false,
                            isPro = true,
                            onClick = onExtractTextClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Check,
                            label = "Form Fill",
                            isSelected = false,
                            isPro = true,
                            onClick = onAcroFormClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Draw,
                            label = "Sign PDF",
                            isSelected = false,
                            isPro = false,
                            onClick = onSignatureClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Lock,
                            label = "Redact ID",
                            isSelected = false,
                            isPro = true,
                            onClick = onIdRedactionClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.AspectRatio,
                            label = "Page Size",
                            isSelected = false,
                            isPro = false,
                            onClick = onPageDimensionsClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Layers,
                            label = "Pages Grid",
                            isSelected = false,
                            isPro = false,
                            onClick = onPagesOverviewClicked
                        )
                    }

                    WorkflowCategory.ERASE_CLEAN -> {
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Magic Eraser",
                            isSelected = activeMode == EditorToolMode.MAGIC_ERASER,
                            isPro = true,
                            onClick = { onModeSelected(EditorToolMode.MAGIC_ERASER) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.CleaningServices,
                            label = "Erase Marks",
                            isSelected = false,
                            isPro = true,
                            onClick = onEraseMarksClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.ContentCut,
                            label = "BG Remover",
                            isSelected = false,
                            isPro = true,
                            onClick = onBackgroundRemovalClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Erase Shadow",
                            isSelected = false,
                            isPro = true,
                            onClick = onApplyShadowRemover
                        )
                        ToolDockButton(
                            icon = Icons.Default.Clear,
                            label = "Remove Fingers",
                            isSelected = false,
                            isPro = true,
                            onClick = onApplyFingerRemover
                        )
                        ToolDockButton(
                            icon = Icons.Default.Brush,
                            label = "Whiteout",
                            isSelected = activeMode == EditorToolMode.WHITEOUT,
                            isPro = false,
                            onClick = { onModeSelected(EditorToolMode.WHITEOUT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Block,
                            label = "Redact Box",
                            isSelected = activeMode == EditorToolMode.REDACTION,
                            isPro = false,
                            onClick = { onModeSelected(EditorToolMode.REDACTION) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.SelectAll,
                            label = "Lasso Pick",
                            isSelected = activeMode == EditorToolMode.LASSO_SELECT,
                            isPro = true,
                            onClick = { onModeSelected(EditorToolMode.LASSO_SELECT) }
                        )
                    }

                    WorkflowCategory.DESIGN_STAMPS -> {
                        ToolDockButton(
                            icon = Icons.Default.Category,
                            label = "14 Shapes",
                            isSelected = activeMode == EditorToolMode.SHAPES,
                            isPro = false,
                            onClick = { onModeSelected(EditorToolMode.SHAPES) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.LocalPolice,
                            label = "Rubber Stamp",
                            isSelected = false,
                            isPro = true,
                            onClick = onRubberStampClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Security,
                            label = "Stickers & Seals",
                            isSelected = false,
                            isPro = true,
                            onClick = onCanvaStickersClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.ViewInAr,
                            label = "Insert Photo",
                            isSelected = false,
                            isPro = false,
                            onClick = onInsertImageClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Draw,
                            label = "Watermark",
                            isSelected = false,
                            isPro = false,
                            onClick = onWatermarkClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Palette,
                            label = "Brand Kit",
                            isSelected = false,
                            isPro = true,
                            onClick = onCanvaBrandKitClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Layers,
                            label = "Layer Stack",
                            isSelected = false,
                            isPro = true,
                            onClick = onCanvaLayersClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.FilterFrames,
                            label = "Frames & 3D",
                            isSelected = false,
                            isPro = true,
                            onClick = onCanvaMockupsClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Animation,
                            label = "Motion Presets",
                            isSelected = false,
                            isPro = true,
                            onClick = onCanvaAnimateClicked
                        )
                    }

                    WorkflowCategory.SCAN_ENHANCE -> {
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "13 Filters",
                            isSelected = showFiltersRow || activeMode == EditorToolMode.FILTERS,
                            isPro = false,
                            onClick = { onModeSelected(EditorToolMode.FILTERS) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Crop,
                            label = "Smart Crop",
                            isSelected = false,
                            isPro = false,
                            onClick = onInteractiveCropClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            label = "Rotate 90°",
                            isSelected = false,
                            isPro = false,
                            onClick = onRotateClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Auto Orient",
                            isSelected = false,
                            isPro = true,
                            onClick = onAutoOrientClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "Book Dewarp",
                            isSelected = false,
                            isPro = true,
                            onClick = onBookDewarpClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "Split Book",
                            isSelected = false,
                            isPro = true,
                            onClick = onBookSplitClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Tune,
                            label = "Adjust Sliders",
                            isSelected = false,
                            isPro = true,
                            onClick = onCanvaAdjustClicked
                        )
                    }

                    WorkflowCategory.EXPORT_CONVERT -> {
                        ToolDockButton(
                            icon = Icons.Default.Share,
                            label = "Export Sheet",
                            isSelected = false,
                            isPro = false,
                            onClick = onExportClicked
                        )
                        ToolDockButton(
                            icon = Icons.AutoMirrored.Filled.Article,
                            label = "Word (.docx)",
                            isSelected = false,
                            isPro = true,
                            onClick = onWordExportClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.PresentToAll,
                            label = "PowerPoint",
                            isSelected = false,
                            isPro = true,
                            onClick = onPptxExportClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.TableChart,
                            label = "Excel (.xlsx)",
                            isSelected = false,
                            isPro = true,
                            onClick = onExcelExportClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Compress,
                            label = "Pi7 KB Resize",
                            isSelected = false,
                            isPro = true,
                            onClick = onTargetSizeClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Description,
                            label = "PDF Toolbox",
                            isSelected = false,
                            isPro = false,
                            onClick = onCompressClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Security,
                            label = "Digital Sign",
                            isSelected = false,
                            isPro = true,
                            onClick = onPkiDigitalSignClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.CloudUpload,
                            label = "Cloud Hub",
                            isSelected = false,
                            isPro = true,
                            onClick = onCloudSyncClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.SystemUpdate,
                            label = "Check Update",
                            isSelected = false,
                            isPro = false,
                            onClick = onCheckUpdateClicked
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
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WorkflowCategory.entries.forEach { category ->
                        val isSelected = category == activeCategory
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF8B5CF6) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFFA78BFA)) else null,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { activeCategory = category }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
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
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Quick 1-Tap Export Button Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF059669).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF059669)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onExportClicked() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable tool dock button with icon, label, selection glow, and prominent 👑 PRO badge.
 */
@Composable
private fun ToolDockButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isPro: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1E2130))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
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

            if (isPro) {
                Surface(
                    color = Color(0xFFF59E0B),
                    shape = RoundedCornerShape(4.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                ) {
                    Text(
                        text = "PRO",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFFC084FC) else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
