package com.docu.editor.ui.canvas

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

/**
 * Live Horizontal Multi-Page Thumbnail Strip & Range Selector.
 * Allows 1-tap instant page switching, page selection checkboxes,
 * batch export, batch delete, batch rotate, and quick add-page.
 */
@Composable
fun PageThumbnailStrip(
    pageCount: Int,
    currentPageIndex: Int,
    pageThumbnails: Map<Int, Bitmap>,
    onSelectPage: (Int) -> Unit,
    onAddPageClicked: () -> Unit,
    onDeleteSelectedPages: (Set<Int>) -> Unit,
    onExportSelectedPages: (Set<Int>) -> Unit,
    onRotateSelectedPages: (Set<Int>) -> Unit,
    onOpenPagesOverview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedPages = remember { mutableStateListOf<Int>() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        color = Color(0xF00F172A),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            // Mode Header / Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSelectMode) "${selectedPages.size}/$pageCount Selected" else "Pages ($pageCount)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isSelectMode = !isSelectMode
                                if (!isSelectMode) selectedPages.clear()
                            },
                        color = if (isSelectMode) Color(0xFF2563EB) else Color(0xFF1E293B)
                    ) {
                        Text(
                            text = if (isSelectMode) "Done" else "Select Pages",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenPagesOverview() },
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text(
                            text = "🗂️ Grid Studio",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (isSelectMode) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Select All",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    if (selectedPages.size == pageCount) {
                                        selectedPages.clear()
                                    } else {
                                        selectedPages.clear()
                                        for (i in 0 until pageCount) selectedPages.add(i)
                                    }
                                }
                                .padding(4.dp)
                        )
                    }
                }

                if (isSelectMode && selectedPages.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onExportSelectedPages(selectedPages.toSet()) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Selected", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onRotateSelectedPages(selectedPages.toSet()) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate Selected", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = {
                                onDeleteSelectedPages(selectedPages.toSet())
                                selectedPages.clear()
                                isSelectMode = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontal Reel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 0 until pageCount) {
                    val isCurrent = (i == currentPageIndex)
                    val isChecked = selectedPages.contains(i)
                    val bmp = pageThumbnails[i]

                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .border(
                                width = if (isCurrent) 2.5.dp else if (isChecked) 2.dp else 1.dp,
                                color = if (isCurrent) Color(0xFF38BDF8) else if (isChecked) Color(0xFF2563EB) else Color(0xFF334155),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (isSelectMode) {
                                    if (isChecked) selectedPages.remove(i) else selectedPages.add(i)
                                } else {
                                    onSelectPage(i)
                                }
                            }
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${i + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(52.dp).background(Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Bottom Page Number Label
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .align(Alignment.BottomCenter)
                                .background(if (isCurrent) Color(0xFF0284C7) else Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${i + 1}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Selection Checkbox Indicator
                        if (isSelectMode) {
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isChecked) Color(0xFF2563EB) else Color(0x99000000))
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }

                // Add Page Button (+)
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.5f))
                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                        .clickable { onAddPageClicked() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = "Add Page", tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                        Text("Add", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
