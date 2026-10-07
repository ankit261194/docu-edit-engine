package com.docu.editor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CoPresent
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pixel-Perfect Replica of CamScanner Tools Hub Screen.
 * Categorized sections matching media_1791339973625.jpg and media_1791339973621.jpg:
 * Convert, Import, Edit, AI Tools, Scan, Other.
 */

data class ToolGridItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val iconTint: Color,
    val circleBgColor: Color,
    val hasRedBadge: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun CamScannerToolsTab(
    onConvertToWord: () -> Unit,
    onConvertToExcel: () -> Unit,
    onConvertToPpt: () -> Unit,
    onCountCam: () -> Unit,
    onPdfToImages: () -> Unit,
    onPdfToLongImage: () -> Unit,
    onCamScannerAi: () -> Unit,
    onImportImages: () -> Unit,
    onImportFiles: () -> Unit,
    onSign: () -> Unit,
    onAddWatermark: () -> Unit,
    onMergeFiles: () -> Unit,
    onExtractPdfPages: () -> Unit,
    onReorderPages: () -> Unit,
    onLockPdf: () -> Unit,
    onEraseMarks: () -> Unit,
    onSmartErase: () -> Unit,
    onSolverAi: () -> Unit,
    onEnhanceDocuments: () -> Unit,
    onRestorePhoto: () -> Unit,
    onScanIdCards: () -> Unit,
    onExtractText: () -> Unit,
    onIdPhotoMaker: () -> Unit,
    onScanToExcel: () -> Unit,
    onFormulaOcr: () -> Unit,
    onBookDewarp: () -> Unit,
    onSlidesScan: () -> Unit,
    onWhiteboardScan: () -> Unit,
    onTimestampScan: () -> Unit,
    onScanCode: () -> Unit
) {
    val convertItems = listOf(
        ToolGridItem("word", "To Word", Icons.Default.Description, Color(0xFF2563EB), Color(0xFFEFF6FF), onClick = onConvertToWord),
        ToolGridItem("excel", "To Excel", Icons.Default.TableChart, Color(0xFF16A34A), Color(0xFFF0FDF4), onClick = onConvertToExcel),
        ToolGridItem("ppt", "To PPT", Icons.Default.Slideshow, Color(0xFFEA580C), Color(0xFFFFF7ED), onClick = onConvertToPpt),
        ToolGridItem("count", "CountCam", Icons.Default.Numbers, Color(0xFF0284C7), Color(0xFFE0F2FE), onClick = onCountCam),
        ToolGridItem("pdf_img", "PDF to Images", Icons.Default.PhotoLibrary, Color(0xFF0D9488), Color(0xFFCCFBF1), onClick = onPdfToImages),
        ToolGridItem("pdf_long", "PDF to Long Image", Icons.Default.ViewDay, Color(0xFF0284C7), Color(0xFFE0F2FE), onClick = onPdfToLongImage),
        ToolGridItem("cs_ai", "DocuScan AI", Icons.Default.AutoAwesome, Color(0xFF059669), Color(0xFFD1FAE5), onClick = onCamScannerAi)
    )

    val importItems = listOf(
        ToolGridItem("imp_img", "Import Images", Icons.Default.PhotoAlbum, Color(0xFF0D9488), Color(0xFFCCFBF1), onClick = onImportImages),
        ToolGridItem("imp_file", "Import Files", Icons.Default.FolderOpen, Color(0xFF2563EB), Color(0xFFEFF6FF), onClick = onImportFiles)
    )

    val editItems = listOf(
        ToolGridItem("sign", "Sign", Icons.Default.HistoryEdu, Color(0xFF0D9488), Color(0xFFCCFBF1), onClick = onSign),
        ToolGridItem("watermark", "Add Watermark", Icons.Default.Security, Color(0xFF3B82F6), Color(0xFFEFF6FF), onClick = onAddWatermark),
        ToolGridItem("merge", "Merge Files", Icons.Default.MergeType, Color(0xFF0284C7), Color(0xFFE0F2FE), onClick = onMergeFiles),
        ToolGridItem("extract_pages", "Extract PDF Pages", Icons.Default.Layers, Color(0xFF2563EB), Color(0xFFEFF6FF), onClick = onExtractPdfPages),
        ToolGridItem("reorder", "Reorder Pages", Icons.Default.SwapVert, Color(0xFF059669), Color(0xFFD1FAE5), onClick = onReorderPages),
        ToolGridItem("lock", "Lock", Icons.Default.Lock, Color(0xFF10B981), Color(0xFFECFDF5), onClick = onLockPdf)
    )

    val aiToolsItems = listOf(
        ToolGridItem("erase_marks", "Erase Marks", Icons.Default.CleaningServices, Color(0xFF4F46E5), Color(0xFFEEF2FF), onClick = onEraseMarks),
        ToolGridItem("smart_erase", "Smart Erase", Icons.Default.AutoFixHigh, Color(0xFF2563EB), Color(0xFFEFF6FF), hasRedBadge = true, onClick = onSmartErase),
        ToolGridItem("solver_ai", "Solver AI", Icons.Default.Psychology, Color(0xFF059669), Color(0xFFD1FAE5), hasRedBadge = true, onClick = onSolverAi),
        ToolGridItem("enhance", "Enhance Documents", Icons.Default.Hd, Color(0xFF2563EB), Color(0xFFEFF6FF), onClick = onEnhanceDocuments),
        ToolGridItem("restore_photo", "Restore Photo", Icons.Default.CoPresent, Color(0xFFE11D48), Color(0xFFFFE4E6), hasRedBadge = true, onClick = onRestorePhoto)
    )

    val scanItems = listOf(
        ToolGridItem("id_cards", "ID Cards", Icons.Default.Badge, Color(0xFF0284C7), Color(0xFFE0F2FE), onClick = onScanIdCards),
        ToolGridItem("ocr", "Extract Text", Icons.Default.TextFields, Color(0xFF0D9488), Color(0xFFCCFBF1), onClick = onExtractText),
        ToolGridItem("id_photo", "ID Photo Maker", Icons.Default.CenterFocusStrong, Color(0xFF2563EB), Color(0xFFEFF6FF), onClick = onIdPhotoMaker),
        ToolGridItem("scan_excel", "Scan to Excel", Icons.Default.TableChart, Color(0xFF16A34A), Color(0xFFF0FDF4), onClick = onScanToExcel),
        ToolGridItem("formula", "Formula", Icons.Default.Functions, Color(0xFF059669), Color(0xFFD1FAE5), onClick = onFormulaOcr),
        ToolGridItem("book", "Book", Icons.Default.MenuBook, Color(0xFF0284C7), Color(0xFFE0F2FE), onClick = onBookDewarp),
        ToolGridItem("slides", "Slides", Icons.Default.PresentToAll, Color(0xFFEA580C), Color(0xFFFFF7ED), onClick = onSlidesScan),
        ToolGridItem("whiteboard", "Whiteboard", Icons.Default.DriveFileMove, Color(0xFF3B82F6), Color(0xFFEFF6FF), onClick = onWhiteboardScan),
        ToolGridItem("timestamp", "Timestamp", Icons.Default.Schedule, Color(0xFF2563EB), Color(0xFFEFF6FF), onClick = onTimestampScan)
    )

    val otherItems = listOf(
        ToolGridItem("scan_code", "Scan Code", Icons.Default.QrCodeScanner, Color(0xFF2563EB), Color(0xFFEFF6FF), onClick = onScanCode)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Spacer
        item(span = { GridItemSpan(4) }) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- 1. CONVERT SECTION ---
        item(span = { GridItemSpan(4) }) {
            SectionHeader("Convert")
        }
        for (item in convertItems) {
            item {
                CamScannerToolIcon(item)
            }
        }

        // --- 2. IMPORT SECTION ---
        item(span = { GridItemSpan(4) }) {
            SectionHeader("Import")
        }
        for (item in importItems) {
            item {
                CamScannerToolIcon(item)
            }
        }

        // --- 3. EDIT SECTION ---
        item(span = { GridItemSpan(4) }) {
            SectionHeader("Edit")
        }
        for (item in editItems) {
            item {
                CamScannerToolIcon(item)
            }
        }

        // --- 4. AI TOOLS SECTION ---
        item(span = { GridItemSpan(4) }) {
            SectionHeader("AI Tools")
        }
        for (item in aiToolsItems) {
            item {
                CamScannerToolIcon(item)
            }
        }

        // --- 5. SCAN SECTION ---
        item(span = { GridItemSpan(4) }) {
            SectionHeader("Scan")
        }
        for (item in scanItems) {
            item {
                CamScannerToolIcon(item)
            }
        }

        // --- 6. OTHER SECTION ---
        item(span = { GridItemSpan(4) }) {
            SectionHeader("Other")
        }
        for (item in otherItems) {
            item {
                CamScannerToolIcon(item)
            }
        }

        // Bottom padding so content is not obscured by bottom bar
        item(span = { GridItemSpan(4) }) {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun CamScannerToolIcon(item: ToolGridItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { item.onClick() }
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // Soft Circular Pastel Container (CamScanner Style)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(item.circleBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Red notification dot (as in screenshot for Smart Erase, Solver AI, Restore Photo)
            if (item.hasRedBadge) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
