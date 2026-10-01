package com.docu.editor.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults

@Composable
fun PagesOverviewDialog(
    pageCount: Int,
    currentPageIndex: Int,
    pageThumbnails: Map<Int, Bitmap>,
    onSelectPage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onMovePage: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRotatePage: (pageIndex: Int) -> Unit = {},
    onDuplicatePage: (pageIndex: Int) -> Unit = {},
    onDeleteMultiplePages: (pageIndices: Set<Int>) -> Unit = {},
    onAddPageFromCamera: () -> Unit = {},
    onAddPageFromGallery: () -> Unit = {},
    onAddBlankPage: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPages by remember { mutableStateOf(setOf<Int>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxSize(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dynamic Header: Normal vs Selection Mode
                if (!isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Document Pages",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$pageCount Total Pages • Tap to Jump",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pageCount > 1) {
                                OutlinedButton(
                                    onClick = {
                                        isSelectionMode = true
                                        selectedPages = emptySet()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Select", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${selectedPages.size} Selected",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                            Text(
                                text = "Tap cards to toggle",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    selectedPages = if (selectedPages.size == pageCount) emptySet() else (0 until pageCount).toSet()
                                }
                            ) {
                                Text(if (selectedPages.size == pageCount) "Deselect" else "Select All", fontSize = 11.sp)
                            }
                            if (selectedPages.isNotEmpty() && selectedPages.size < pageCount) {
                                Button(
                                    onClick = {
                                        onDeleteMultiplePages(selectedPages)
                                        isSelectionMode = false
                                        selectedPages = emptySet()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete (${selectedPages.size})", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(onClick = { isSelectionMode = false; selectedPages = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grid of Page Thumbnails
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(pageCount) { index ->
                        val isCurrent = index == currentPageIndex
                        val isSelected = selectedPages.contains(index)
                        val thumbnail = pageThumbnails[index]

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isSelectionMode && isSelected) 2.5.dp else if (isCurrent && !isSelectionMode) 2.5.dp else 1.dp,
                                color = if (isSelectionMode && isSelected) Color(0xFF2563EB) else if (isCurrent && !isSelectionMode) Color(0xFF2563EB) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelectionMode && isSelected) Color(0xFF2563EB).copy(alpha = 0.08f) else if (isCurrent) Color(0xFF2563EB).copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelectionMode) {
                                        selectedPages = if (selectedPages.contains(index)) selectedPages - index else selectedPages + index
                                    } else {
                                        onSelectPage(index)
                                        onDismiss()
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumbnail != null) {
                                        Image(
                                            bitmap = thumbnail.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    if (isSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedPages = if (checked) selectedPages + index else selectedPages - index
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2563EB)),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .background(Color(0xFF2563EB), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Current Page",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Page ${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurface
                                    )

                                    if (!isSelectionMode) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { onRotatePage(index) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                                    contentDescription = "Rotate 90°",
                                                    tint = Color(0xFF2563EB),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDuplicatePage(index) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Duplicate Page",
                                                    tint = Color(0xFF0D9488),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = { onMovePage(index, index - 1) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Move Earlier",
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                            if (index < pageCount - 1) {
                                                IconButton(
                                                    onClick = { onMovePage(index, index + 1) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = "Move Later",
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                            if (pageCount > 1) {
                                                IconButton(
                                                    onClick = { onDeletePage(index) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Page",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // + Add Page Card
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF2563EB).copy(alpha = 0.5f)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "+ Add Page",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1D4ED8)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onAddPageFromCamera()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF2563EB))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Camera", fontSize = 10.sp, color = Color(0xFF2563EB), maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onAddPageFromGallery()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF0D9488))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Gallery", fontSize = 10.sp, color = Color(0xFF0D9488), maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onAddBlankPage()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF7C3AED))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Blank", fontSize = 10.sp, color = Color(0xFF7C3AED), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
