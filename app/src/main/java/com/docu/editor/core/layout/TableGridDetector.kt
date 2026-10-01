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
    val items: List<DetectedTextItem>
)

data class DetectedTable(
    val bounds: Rect,
    val rowCount: Int,
    val colCount: Int,
    val rows: List<List<DetectedTableCell>>,
    val isRuledGrid: Boolean
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

    private fun clusterItemsIntoGrid(
        items: List<DetectedTextItem>,
        tableBounds: Rect,
        isRuled: Boolean
    ): DetectedTable? {
        if (items.isEmpty()) return null

        // 1. Group items into distinct horizontal rows
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

        // Sort each row left to right
        rowClusters.forEach { it.sortBy { item -> item.boundingBox.left } }

        // 2. Identify global column anchor coordinates across all multi-item rows
        val multiItemRows = rowClusters.filter { it.size >= 2 }
        if (multiItemRows.isEmpty()) return null

        val columnLefts = mutableListOf<Double>()
        for (row in multiItemRows) {
            for (item in row) {
                val left = item.boundingBox.left.toDouble()
                val matchedCol = columnLefts.indices.find { idx ->
                    abs(columnLefts[idx] - left) <= 35.0
                }
                if (matchedCol != null) {
                    columnLefts[matchedCol] = (columnLefts[matchedCol] + left) / 2.0
                } else {
                    columnLefts.add(left)
                }
            }
        }
        columnLefts.sort()

        val numCols = max(2, columnLefts.size)
        val gridRows = mutableListOf<List<DetectedTableCell>>()

        for ((rIdx, row) in rowClusters.withIndex()) {
            val cellsInRow = mutableListOf<DetectedTableCell>()
            for (cIdx in 0 until numCols) {
                val colAnchor = columnLefts.getOrElse(cIdx) { 0.0 }
                val nextColAnchor = columnLefts.getOrElse(cIdx + 1) { tableBounds.right.toDouble() }

                val itemsInCell = row.filter { item ->
                    val cx = item.boundingBox.centerX().toDouble()
                    if (cIdx == numCols - 1) {
                        cx >= colAnchor - 20.0
                    } else {
                        cx >= colAnchor - 20.0 && cx < nextColAnchor - 15.0
                    }
                }

                val cellText = itemsInCell.joinToString(" ") { it.text.trim() }
                val cellBounds = if (itemsInCell.isNotEmpty()) {
                    Rect(
                        itemsInCell.minOf { it.boundingBox.left },
                        itemsInCell.minOf { it.boundingBox.top },
                        itemsInCell.maxOf { it.boundingBox.right },
                        itemsInCell.maxOf { it.boundingBox.bottom }
                    )
                } else {
                    Rect(colAnchor.toInt(), row.minOf { it.boundingBox.top }, nextColAnchor.toInt(), row.maxOf { it.boundingBox.bottom })
                }

                cellsInRow.add(
                    DetectedTableCell(
                        rowIndex = rIdx,
                        colIndex = cIdx,
                        bounds = cellBounds,
                        text = cellText,
                        items = itemsInCell
                    )
                )
            }
            gridRows.add(cellsInRow)
        }

        return DetectedTable(
            bounds = tableBounds,
            rowCount = gridRows.size,
            colCount = numCols,
            rows = gridRows,
            isRuledGrid = isRuled
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
