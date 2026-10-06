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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
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
    ENHANCE("Enhance", Icons.Default.AutoFixHigh),
    CLEAN("Clean", Icons.Default.AutoAwesome),
    ANNOTATE("Annotate", Icons.Default.Edit),
    TOOLS("Tools", Icons.Default.Description)
}

/**
 * Premium CamScanner-Grade Document Bottom Bar Dock.
 * Categorized, clean, 2-tier design eliminating cluttered 20-tool horizontal scrolling:
 * Tier 1: Category Navigation (Enhance | Clean | Annotate | Tools + Export)
 * Tier 2: Category-Specific Action Tools & Interactive Sliders
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
    onInsertImageClicked: () -> Unit = {},
    onCanvaStickersClicked: () -> Unit = {},
    magicEraserBrushRadius: Float = 28f,
    onMagicEraserBrushRadiusChanged: (Float) -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }

    // Smart Category Auto-Selection based on active mode
    var activeCategory by remember(activeMode, showFiltersRow) {
        mutableStateOf(
            when {
                showFiltersRow || activeMode == EditorToolMode.FILTERS -> EditorCategory.ENHANCE
                activeMode in listOf(EditorToolMode.MAGIC_ERASER, EditorToolMode.WHITEOUT, EditorToolMode.REDACTION, EditorToolMode.LASSO_SELECT) -> EditorCategory.CLEAN
                activeMode in listOf(EditorToolMode.ADD_TEXT, EditorToolMode.TEXT_EDIT, EditorToolMode.SHAPES, EditorToolMode.HIGHLIGHTER, EditorToolMode.MARKUP_PEN) -> EditorCategory.ANNOTATE
                else -> EditorCategory.ENHANCE
            }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // Contextual Control Sub-Bars (Only visible when active tool requires fine-tuning)

            // 1. Magic Filters Row & Sliders
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
                                        DocumentFilterMode.MAGIC_COLOR -> "✨ Magic Color"
                                        DocumentFilterMode.SHADOW_REMOVER -> "🌤️ Remove Shadow"
                                        DocumentFilterMode.WATERMARK_REMOVER -> "🧹 Erase Watermark"
                                        DocumentFilterMode.FINGER_REMOVER -> "🖐️ Remove Fingers"
                                        DocumentFilterMode.BOOK_DEWARP -> "📖 Flatten Page"
                                        DocumentFilterMode.CLEAN_BW -> "📄 Clean B&W"
                                        DocumentFilterMode.GRAYSCALE -> "🔘 Grayscale"
                                        else -> filter.displayName
                                    }
                                    Text(
                                        text = chipText,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
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
                                    selectedContainerColor = if (isMagicColor) Color(0xFF059669) else Color(0xFF2563EB),
                                    containerColor = if (isMagicColor) Color(0xFFD1FAE5) else MaterialTheme.colorScheme.surfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                activeTrackColor = Color(0xFF059669)
                            )
                        )
                        Text(
                            text = "Contrast: ${(contrast * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = contrast,
                            onValueChange = {
                                contrast = it
                                onBrightnessContrastChanged(brightness, contrast)
                            },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF059669),
                                activeTrackColor = Color(0xFF059669)
                            )
                        )
                    }
                }
            }

            // 2. Whiteout Eraser Brush Slider
            AnimatedVisibility(visible = activeMode == EditorToolMode.WHITEOUT) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
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
                            text = "Whiteout Brush: ${whiteoutBrushRadius.toInt()}px",
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
                                thumbColor = Color(0xFF059669),
                                activeTrackColor = Color(0xFF059669)
                            )
                        )
                    }
                }
            }

            // 3. Magic Eraser Brush Slider
            AnimatedVisibility(visible = activeMode == EditorToolMode.MAGIC_ERASER) {
                Surface(
                    color = Color(0xFF047857).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
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
                            text = "🪄 Magic Eraser: ${magicEraserBrushRadius.toInt()}px",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                        Slider(
                            value = magicEraserBrushRadius,
                            onValueChange = onMagicEraserBrushRadiusChanged,
                            valueRange = 8f..60f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF059669),
                                activeTrackColor = Color(0xFF10B981)
                            )
                        )
                    }
                }
            }

            // 4. Highlighter Sub-Bar: Color Palette + Width Slider
            AnimatedVisibility(visible = activeMode == EditorToolMode.HIGHLIGHTER) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🖍️ Highlighter Color:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val hlColors = listOf(
                                    Pair("Yellow", android.graphics.Color.rgb(255, 235, 59)),
                                    Pair("Green", android.graphics.Color.rgb(132, 204, 22)),
                                    Pair("Pink", android.graphics.Color.rgb(244, 63, 94)),
                                    Pair("Cyan", android.graphics.Color.rgb(14, 165, 233))
                                )
                                hlColors.forEach { (_, rgb) ->
                                    val isCur = markupColorRgb == rgb
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(rgb))
                                            .border(if (isCur) 2.5.dp else 1.dp, if (isCur) Color.Black else Color.Gray, CircleShape)
                                            .clickable { onMarkupColorChanged(rgb) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Width: ${markupStrokeWidth.toInt()}px", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Slider(
                                value = markupStrokeWidth,
                                onValueChange = onMarkupStrokeWidthChanged,
                                valueRange = 10f..60f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF59E0B),
                                    activeTrackColor = Color(0xFFF59E0B)
                                )
                            )
                        }
                    }
                }
            }

            // 5. Pen Drawing Sub-Bar
            AnimatedVisibility(visible = activeMode == EditorToolMode.MARKUP_PEN) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✏️ Pen Color:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val penColors = listOf(
                                    Pair("Red", android.graphics.Color.rgb(220, 38, 38)),
                                    Pair("Blue", android.graphics.Color.rgb(37, 99, 235)),
                                    Pair("Black", android.graphics.Color.rgb(15, 23, 42)),
                                    Pair("Green", android.graphics.Color.rgb(22, 163, 74))
                                )
                                penColors.forEach { (_, rgb) ->
                                    val isCur = penColorRgb == rgb
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(rgb))
                                            .border(if (isCur) 2.5.dp else 1.dp, if (isCur) Color.White else Color.Gray, CircleShape)
                                            .clickable { onPenColorChanged(rgb) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pen Size: ${penStrokeWidth.toInt()}px", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Slider(
                                value = penStrokeWidth,
                                onValueChange = onPenStrokeWidthChanged,
                                valueRange = 2f..16f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFDC2626),
                                    activeTrackColor = Color(0xFFDC2626)
                                )
                            )
                        }
                    }
                }
            }

            // 6. Shapes Sub-Bar
            AnimatedVisibility(visible = activeMode == EditorToolMode.SHAPES) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Shapes:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val shapes = listOf(
                                    Pair(ShapeType.RECTANGLE, "▭ Box"),
                                    Pair(ShapeType.ARROW, "➔ Arrow"),
                                    Pair(ShapeType.LINE, "── Line"),
                                    Pair(ShapeType.CIRCLE, "◯ Circle")
                                )
                                shapes.forEach { (type, label) ->
                                    val isCur = selectedShapeType == type
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCur) Color(0xFF059669) else MaterialTheme.colorScheme.surface)
                                            .border(1.dp, if (isCur) Color(0xFF059669) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                            .clickable { onShapeTypeSelected(type) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (isCur) Color.White else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Redaction Sub-Bar
            AnimatedVisibility(visible = activeMode == EditorToolMode.REDACTION) {
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Blackout Redaction",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Drag box or tap to censor",
                            fontSize = 11.sp,
                            color = Color(0xFFFCA5A5)
                        )
                    }
                }
            }

            // 8. Lasso Selection Action Strip
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

            Spacer(modifier = Modifier.height(4.dp))

            // Tier 1: Dedicated Category Tools Row (Clean, Smooth, Contextual)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (activeCategory) {
                    EditorCategory.ENHANCE -> {
                        ToolDockButton(
                            icon = Icons.Default.Crop,
                            label = "4-Corner Crop",
                            isSelected = false,
                            onClick = onInteractiveCropClicked
                        )
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
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            label = "Book Dewarp",
                            isSelected = false,
                            onClick = onBookDewarpClicked
                        )
                    }
                    EditorCategory.CLEAN -> {
                        ToolDockButton(
                            icon = Icons.Default.AutoAwesome,
                            label = "Magic Eraser",
                            isSelected = activeMode == EditorToolMode.MAGIC_ERASER,
                            onClick = { onModeSelected(EditorToolMode.MAGIC_ERASER) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Clear,
                            label = "Whiteout",
                            isSelected = activeMode == EditorToolMode.WHITEOUT,
                            onClick = { onModeSelected(EditorToolMode.WHITEOUT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Block,
                            label = "Blackout Redact",
                            isSelected = activeMode == EditorToolMode.REDACTION,
                            onClick = { onModeSelected(EditorToolMode.REDACTION) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.SelectAll,
                            label = "Lasso Select",
                            isSelected = activeMode == EditorToolMode.LASSO_SELECT,
                            onClick = { onModeSelected(EditorToolMode.LASSO_SELECT) }
                        )
                    }
                    EditorCategory.ANNOTATE -> {
                        ToolDockButton(
                            icon = Icons.Default.Edit,
                            label = "Add Text",
                            isSelected = activeMode == EditorToolMode.ADD_TEXT,
                            onClick = { onModeSelected(EditorToolMode.ADD_TEXT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Description,
                            label = "Edit Text",
                            isSelected = activeMode == EditorToolMode.TEXT_EDIT,
                            onClick = { onModeSelected(EditorToolMode.TEXT_EDIT) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Draw,
                            label = "Signature",
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
                            icon = Icons.Default.Category,
                            label = "Shapes",
                            isSelected = activeMode == EditorToolMode.SHAPES,
                            onClick = { onModeSelected(EditorToolMode.SHAPES) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Highlighter",
                            isSelected = activeMode == EditorToolMode.HIGHLIGHTER,
                            onClick = { onModeSelected(EditorToolMode.HIGHLIGHTER) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Draw,
                            label = "Pen Draw",
                            isSelected = activeMode == EditorToolMode.MARKUP_PEN,
                            onClick = { onModeSelected(EditorToolMode.MARKUP_PEN) }
                        )
                        ToolDockButton(
                            icon = Icons.Default.Layers,
                            label = "Add Photo",
                            isSelected = false,
                            onClick = onInsertImageClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Security,
                            label = "Badges",
                            isSelected = false,
                            onClick = onCanvaStickersClicked
                        )
                    }
                    EditorCategory.TOOLS -> {
                        ToolDockButton(
                            icon = Icons.Default.Description,
                            label = "OCR Extract",
                            isSelected = false,
                            onClick = onExtractTextClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Layers,
                            label = "Pages Overview",
                            isSelected = false,
                            onClick = onPagesOverviewClicked
                        )
                        ToolDockButton(
                            icon = Icons.Default.Compress,
                            label = "PDF Toolbox",
                            isSelected = false,
                            onClick = onCompressClicked
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tier 2: Category Navigation Anchor Tabs + Quick Share Button
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorCategory.values().forEach { category ->
                            val isSelected = activeCategory == category
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF059669) else Color.Transparent,
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
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = category.label,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Quick Share / Export Pill Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF059669).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.4f)),
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
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Export",
                                color = Color(0xFF059669),
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
                .background(if (isSelected) Color(0xFF059669) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
