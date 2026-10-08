package com.docu.editor.core.layout

import android.graphics.Bitmap
import android.graphics.Rect
import com.docu.editor.core.ocr.model.DetectedTextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

/**
 * Enterprise CamScanner & Adobe Acrobat-Grade Table & Multi-Column Layout Reconstruction Engine.
 * Combines OpenCV Morphological Line Kernels (for bordered/ruled tables) with
 * 2D Spatial Gutter Binning (for borderless tables & multi-column layouts).
 */
data class DetectedTableCell(
    val rowIndex: Int,
    val colIndex: Int,
    val bounds: Rect,
    val text: String,
    val items: List<DetectedTextItem>,
    val colSpan: Int = 1,
    val rowSpan: Int = 1
)

data class DetectedTable(
    val bounds: Rect,
    val rowCount: Int,
    val colCount: Int,
    val rows: List<List<DetectedTableCell>>,
    val isRuledGrid: Boolean,
    val mergedCellRefs: List<String> = emptyList()
)

data class DocumentLayout(
    val tables: List<DetectedTable>,
    val paragraphs: List<String>
)

object TableGridDetector {

    /**
     * Analyzes document bitmap and OCR text items to reconstruct exact table grids and layout structures.
     */
    suspend fun analyzeLayout(
        bitmap: Bitmap,
        items: List<DetectedTextItem>
    ): DocumentLayout = withContext(Dispatchers.Default) {
        if (items.isEmpty()) {
            return@withContext DocumentLayout(emptyList(), emptyList())
        }

        // 1. Try Ruled Table Grid Detection via OpenCV Morphological Kernels
        val ruledTables = detectRuledTables(bitmap, items)
        if (ruledTables.isNotEmpty()) {
            // Collect items that were NOT part of any detected table
            val tableItemIds = ruledTables.flatMap { it.rows.flatten().flatMap { cell -> cell.items.map { item -> item.id } } }.toSet()
            val outsideItems = items.filter { it.id !in tableItemIds }
            val outsideParagraphs = groupItemsIntoParagraphs(outsideItems)
            return@withContext DocumentLayout(ruledTables, outsideParagraphs)
        }

        // 2. Fallback to 2D Spatial Binning for Borderless / Semi-Bordered Tables
        val spatialTable = detectBorderlessTable(items)
        if (spatialTable != null && spatialTable.rowCount >= 2 && spatialTable.colCount >= 2) {
            val tableItemIds = spatialTable.rows.flatten().flatMap { cell -> cell.items.map { it.id } }.toSet()
            val outsideItems = items.filter { it.id !in tableItemIds }
            val outsideParagraphs = groupItemsIntoParagraphs(outsideItems)
            return@withContext DocumentLayout(listOf(spatialTable), outsideParagraphs)
        }

        // 3. Normal Document Paragraph Flow
        DocumentLayout(emptyList(), groupItemsIntoParagraphs(items))
    }

    /**
     * OpenCV Morphological Line Extraction to find bordered table grids (invoices, receipts, marksheets).
     */
    private fun detectRuledTables(
        bitmap: Bitmap,
        items: List<DetectedTextItem>
    ): List<DetectedTable> {
        val tables = mutableListOf<DetectedTable>()
        val matRgba = Mat()
        val matGray = Mat()
        val matBin = Mat()
        val horizontal = Mat()
        val vertical = Mat()
        val tableMask = Mat()

        try {
            Utils.bitmapToMat(bitmap, matRgba)
            Imgproc.cvtColor(matRgba, matGray, Imgproc.COLOR_RGBA2GRAY)

            // Adaptive inverse thresholding: table lines become bright white (255) on black (0)
            Imgproc.adaptiveThreshold(
                matGray,
                matBin,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                15,
                4.0
            )

            // Scale kernel lengths proportional to image dimensions
            val scaleH = max(20, bitmap.width / 40)
            val scaleV = max(20, bitmap.height / 50)

            // Horizontal line kernel
            val hKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(scaleH.toDouble(), 1.0))
            Imgproc.erode(matBin, horizontal, hKernel)
            Imgproc.dilate(horizontal, horizontal, hKernel)

            // Vertical line kernel
            val vKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(1.0, scaleV.toDouble()))
            Imgproc.erode(matBin, vertical, vKernel)
            Imgproc.dilate(vertical, vertical, vKernel)

            // Table grid = union of horizontal & vertical lines
            org.opencv.core.Core.add(horizontal, vertical, tableMask)

            // Find external contours of connected table structures
            val tableContours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                tableMask,
                tableContours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            for (c in tableContours) {
                val rect = Imgproc.boundingRect(c)
                // Filter out small lines or noise: table must be at least 15% of page width & height
                if (rect.width >= bitmap.width * 0.20 && rect.height >= bitmap.height * 0.08) {
                    val tableBounds = Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
                    val tableItems = items.filter { tableBounds.contains(it.boundingBox.centerX(), it.boundingBox.centerY()) }

                    if (tableItems.size >= 4) {
                        val structuredTable = clusterItemsIntoGrid(tableItems, tableBounds, isRuled = true)
                        if (structuredTable != null) {
                            tables.add(structuredTable)
                        }
                    }
                }
                c.release()
            }
            hierarchy.release()
            hKernel.release()
            vKernel.release()
        } catch (_: Exception) {
            // Graceful fallback to spatial binning on error
        } finally {
            matRgba.release()
            matGray.release()
            matBin.release()
            horizontal.release()
            vertical.release()
            tableMask.release()
        }

        return tables
    }

    /**
     * 2D Spatial Binning for Borderless Multi-Column Tables.
     * Identifies columns by vertical whitespace gutters and rows by baseline heights.
     */
    fun detectBorderlessTable(items: List<DetectedTextItem>): DetectedTable? {
        if (items.size < 4) return null

        val minLeft = items.minOf { it.boundingBox.left }
        val maxRight = items.maxOf { it.boundingBox.right }
        val minTop = items.minOf { it.boundingBox.top }
        val maxBottom = items.maxOf { it.boundingBox.bottom }
        val tableBounds = Rect(minLeft, minTop, maxRight, maxBottom)

        return clusterItemsIntoGrid(items, tableBounds, isRuled = false)
    }

    data class ColumnSlot(
        val index: Int,
        val left: Int,
        val right: Int
    )

    private fun getColumnLetter(colIndex: Int): String {
        var num = colIndex + 1
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A'.code + rem).toChar())
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }

    /**
     * Reconstructs 2D table grid using spatial X-Histogram projection and detects merged header spans.
     * Prevents data column shift in borderless tables and complex multi-column headers.
     */
    private fun clusterItemsIntoGrid(
        items: List<DetectedTextItem>,
        tableBounds: Rect,
        isRuled: Boolean
    ): DetectedTable? {
        if (items.isEmpty()) return null

        // 1. Group items into horizontal rows based on baseline / center Y alignment
        val sortedByY = items.sortedBy { it.boundingBox.top }
        val rowClusters = mutableListOf<MutableList<DetectedTextItem>>()

        for (item in sortedByY) {
            val matchingRow = rowClusters.find { row ->
                val avgCenterY = row.map { it.boundingBox.centerY() }.average()
                val avgH = row.map { it.boundingBox.height() }.average().coerceAtLeast(12.0)
                abs(item.boundingBox.centerY() - avgCenterY) <= (avgH * 0.55)
            }

            if (matchingRow != null) {
                matchingRow.add(item)
            } else {
                rowClusters.add(mutableListOf(item))
            }
        }

        if (rowClusters.size < 2) return null

        // Sort items left-to-right within each row
        rowClusters.forEach { it.sortBy { item -> item.boundingBox.left } }

        // 2. Spatial Column Alignment via OCR X-Histogram Projection
        // Discretize the horizontal table span to discover vertical whitespace gutters (valleys)
        val tableW = max(100, tableBounds.width())
        val binSize = 8
        val numBins = (tableW / binSize) + 1
        val rowCoverage = IntArray(numBins)

        for (row in rowClusters) {
            val coveredInThisRow = BooleanArray(numBins)
            for (item in row) {
                val bLeft = ((item.boundingBox.left - tableBounds.left) / binSize).coerceIn(0, numBins - 1)
                val bRight = ((item.boundingBox.right - tableBounds.left) / binSize).coerceIn(0, numBins - 1)
                for (b in bLeft..bRight) {
                    coveredInThisRow[b] = true
                }
            }
            for (b in 0 until numBins) {
                if (coveredInThisRow[b]) rowCoverage[b]++
            }
        }

        // Identify multi-item rows to find natural column anchors
        val multiItemRows = rowClusters.filter { it.size >= 2 }
        val columnAnchors = mutableListOf<Double>()

        if (multiItemRows.isNotEmpty()) {
            for (row in multiItemRows) {
                for (item in row) {
                    val left = item.boundingBox.left.toDouble()
                    val matchedIdx = columnAnchors.indexOfFirst { abs(it - left) <= 40.0 }
                    if (matchedIdx >= 0) {
                        columnAnchors[matchedIdx] = (columnAnchors[matchedIdx] + left) / 2.0
                    } else {
                        columnAnchors.add(left)
                    }
                }
            }
        } else {
            // Fallback from raw item lefts
            for (item in items) {
                val left = item.boundingBox.left.toDouble()
                val matchedIdx = columnAnchors.indexOfFirst { abs(it - left) <= 50.0 }
                if (matchedIdx >= 0) {
                    columnAnchors[matchedIdx] = (columnAnchors[matchedIdx] + left) / 2.0
                } else {
                    columnAnchors.add(left)
                }
            }
        }

        columnAnchors.sort()
        if (columnAnchors.isEmpty()) {
            columnAnchors.add(tableBounds.left.toDouble())
            columnAnchors.add(tableBounds.right.toDouble())
        }

        // Build definitive Column Slots from X-Histogram gutters and column anchors
        val numCols = max(2, columnAnchors.size)
        val columnSlots = mutableListOf<ColumnSlot>()
        for (c in 0 until numCols) {
            val cLeft = columnAnchors[c].toInt()
            val cRight = if (c < numCols - 1) {
                val nextLeft = columnAnchors[c + 1].toInt()
                // Find potential gutter valley between cLeft and nextLeft
                val binStart = ((cLeft - tableBounds.left) / binSize).coerceIn(0, numBins - 1)
                val binEnd = ((nextLeft - tableBounds.left) / binSize).coerceIn(0, numBins - 1)
                var minRowCov = Int.MAX_VALUE
                var valleyBin = (binStart + binEnd) / 2
                for (b in binStart..binEnd) {
                    if (rowCoverage[b] < minRowCov) {
                        minRowCov = rowCoverage[b]
                        valleyBin = b
                    }
                }
                val valleyX = tableBounds.left + (valleyBin * binSize)
                max(cLeft + 15, kotlin.math.min(valleyX, nextLeft - 10))
            } else {
                tableBounds.right
            }
            columnSlots.add(ColumnSlot(c, cLeft, cRight))
        }

        // 3. Grid Row Construction & Merged Header Spanning Detection
        val gridRows = mutableListOf<List<DetectedTableCell>>()
        val mergedRefs = mutableListOf<String>()

        for ((rIdx, row) in rowClusters.withIndex()) {
            val rowNum = rIdx + 1
            val cellsInRow = MutableList<DetectedTableCell?>(numCols) { null }

            for (item in row) {
                val itemLeft = item.boundingBox.left
                val itemRight = item.boundingBox.right
                val itemWidth = item.boundingBox.width()

                // Find best matching start column slot
                var startCol = 0
                var minLeftDist = Int.MAX_VALUE
                for (c in 0 until numCols) {
                    val dist = abs(itemLeft - columnSlots[c].left)
                    if (dist < minLeftDist) {
                        minLeftDist = dist
                        startCol = c
                    }
                }

                // Check if item spans across subsequent columns (merged header cell)
                var endCol = startCol
                for (c in (startCol + 1) until numCols) {
                    if (itemRight > (columnSlots[c].left + 15)) {
                        endCol = c
                    }
                }

                val colSpan = (endCol - startCol + 1)
                val isMergedHeader = colSpan > 1 && itemWidth > 75

                if (isMergedHeader) {
                    val startLetter = getColumnLetter(startCol)
                    val endLetter = getColumnLetter(endCol)
                    mergedRefs.add("$startLetter$rowNum:$endLetter$rowNum")

                    val primaryCell = DetectedTableCell(
                        rowIndex = rIdx,
                        colIndex = startCol,
                        bounds = item.boundingBox,
                        text = item.text.trim(),
                        items = listOf(item),
                        colSpan = colSpan
                    )
                    cellsInRow[startCol] = primaryCell

                    // Pad subsequent spanned columns with empty placeholder cells to strictly prevent column shift
                    for (c in (startCol + 1)..endCol) {
                        val slotBounds = Rect(columnSlots[c].left, item.boundingBox.top, columnSlots[c].right, item.boundingBox.bottom)
                        cellsInRow[c] = DetectedTableCell(
                            rowIndex = rIdx,
                            colIndex = c,
                            bounds = slotBounds,
                            text = "",
                            items = emptyList(),
                            colSpan = 1
                        )
                    }
                } else {
                    val existing = cellsInRow[startCol]
                    if (existing != null && existing.text.isNotBlank()) {
                        // Multi-word item in same cell
                        val combinedText = "${existing.text} ${item.text.trim()}"
                        val unionBounds = Rect(
                            kotlin.math.min(existing.bounds.left, item.boundingBox.left),
                            kotlin.math.min(existing.bounds.top, item.boundingBox.top),
                            max(existing.bounds.right, item.boundingBox.right),
                            max(existing.bounds.bottom, item.boundingBox.bottom)
                        )
                        cellsInRow[startCol] = existing.copy(
                            bounds = unionBounds,
                            text = combinedText,
                            items = existing.items + item
                        )
                    } else {
                        cellsInRow[startCol] = DetectedTableCell(
                            rowIndex = rIdx,
                            colIndex = startCol,
                            bounds = item.boundingBox,
                            text = item.text.trim(),
                            items = listOf(item),
                            colSpan = 1
                        )
                    }
                }
            }

            // Fill any remaining unassigned cells in the row with clean empty cells
            val finalRowCells = (0 until numCols).map { c ->
                cellsInRow[c] ?: DetectedTableCell(
                    rowIndex = rIdx,
                    colIndex = c,
                    bounds = Rect(columnSlots[c].left, row.minOf { it.boundingBox.top }, columnSlots[c].right, row.maxOf { it.boundingBox.bottom }),
                    text = "",
                    items = emptyList(),
                    colSpan = 1
                )
            }
            gridRows.add(finalRowCells)
        }

        return DetectedTable(
            bounds = tableBounds,
            rowCount = gridRows.size,
            colCount = numCols,
            rows = gridRows,
            isRuledGrid = isRuled,
            mergedCellRefs = mergedRefs
        )
    }

    private fun groupItemsIntoParagraphs(items: List<DetectedTextItem>): List<String> {
        if (items.isEmpty()) return emptyList()

        val sorted = items.sortedBy { it.boundingBox.top }
        val lines = mutableListOf<MutableList<DetectedTextItem>>()

        for (item in sorted) {
            val match = lines.find { line ->
                val avgY = line.map { it.boundingBox.centerY() }.average()
                val avgH = line.map { it.boundingBox.height() }.average().coerceAtLeast(10.0)
                abs(item.boundingBox.centerY() - avgY) <= (avgH * 0.6)
            }
            if (match != null) {
                match.add(item)
            } else {
                lines.add(mutableListOf(item))
            }
        }

        lines.forEach { it.sortBy { item -> item.boundingBox.left } }

        val paragraphs = mutableListOf<String>()
        val currentPara = StringBuilder()

        for (line in lines) {
            val lineStr = line.joinToString(" ") { it.text.trim() }
            if (lineStr.isBlank()) continue

            if (currentPara.isEmpty()) {
                currentPara.append(lineStr)
            } else {
                currentPara.append("\n").append(lineStr)
            }
        }

        if (currentPara.isNotEmpty()) {
            paragraphs.add(currentPara.toString())
        }

        return paragraphs
    }
}
