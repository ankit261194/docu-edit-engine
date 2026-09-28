package com.docu.editor.ui.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun DocumentBottomBar(
    activeMode: EditorToolMode,
    activeFilter: DocumentFilterMode,
    showFiltersRow: Boolean,
    onModeSelected: (EditorToolMode) -> Unit,
    onFilterSelected: (DocumentFilterMode) -> Unit,
    onAutoCropClicked: () -> Unit,
    onCompressClicked: () -> Unit,
    onExportClicked: () -> Unit
) {
    Surface(
        color = Color(0xFF1E293B),
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Optional Filter selection row
            AnimatedVisibility(visible = showFiltersRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocumentFilterMode.values().forEach { filter ->
                        val isSelected = filter == activeFilter
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterSelected(filter) },
                            label = {
                                Text(
                                    filter.displayName,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                                selectedContainerColor = Color(0xFF2563EB),
                                containerColor = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Bottom Action Dock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolDockButton(
                    icon = Icons.Default.Edit,
                    label = "Edit Text",
                    isSelected = activeMode == EditorToolMode.TEXT_EDIT,
                    onClick = { onModeSelected(EditorToolMode.TEXT_EDIT) }
                )

                ToolDockButton(
                    icon = Icons.Default.Clear,
                    label = "Whiteout",
                    isSelected = activeMode == EditorToolMode.WHITEOUT,
                    onClick = { onModeSelected(EditorToolMode.WHITEOUT) }
                )

                ToolDockButton(
                    icon = Icons.Default.AutoFixHigh,
                    label = "Filters",
                    isSelected = showFiltersRow,
                    onClick = {
                        onModeSelected(EditorToolMode.FILTERS)
                    }
                )

                ToolDockButton(
                    icon = Icons.Default.Crop,
                    label = "Auto Crop",
                    isSelected = activeMode == EditorToolMode.CROP_DESKEW,
                    onClick = onAutoCropClicked
                )

                ToolDockButton(
                    icon = Icons.Default.Compress,
                    label = "Compress",
                    isSelected = false,
                    onClick = onCompressClicked
                )

                ToolDockButton(
                    icon = Icons.Default.Share,
                    label = "Save / Share",
                    isSelected = false,
                    onClick = onExportClicked
                )
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
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
