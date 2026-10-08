package com.docu.editor.ui.dialogs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.expense.ExpenseAuditManager
import com.docu.editor.core.expense.ExpenseCategory
import com.docu.editor.core.expense.ExpenseExcelExporter
import com.docu.editor.core.expense.ExpenseReceiptItem
import com.docu.editor.core.expense.MonthlyExpenseSummary
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAuditorDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Analytics, 1: Receipts
    var availableMonths by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var selectedPeriod by remember { mutableStateOf<Pair<Int, Int>?>(null) } // null = All time

    var allReceipts by remember { mutableStateOf<List<ExpenseReceiptItem>>(emptyList()) }
    var analyticsSummary by remember { mutableStateOf<MonthlyExpenseSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    // Search and Category Filter for Receipts Tab
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<ExpenseCategory?>(null) }

    // Editing Receipt Dialog State
    var editingReceipt by remember { mutableStateOf<ExpenseReceiptItem?>(null) }

    val reloadData: () -> Unit = {
        scope.launch {
            isLoading = true
            val months = ExpenseAuditManager.getAvailableMonths(context)
            availableMonths = months
            if (selectedPeriod == null && months.isNotEmpty()) {
                selectedPeriod = months.first()
            }

            val yr = selectedPeriod?.first ?: 0
            val mo = selectedPeriod?.second ?: 0

            val receipts = ExpenseAuditManager.getReceiptsForPeriod(context, yr, mo)
            allReceipts = receipts

            val analytics = ExpenseAuditManager.getMonthlyAnalytics(context, yr, mo)
            analyticsSummary = analytics
            isLoading = false
        }
    }

    LaunchedEffect(selectedPeriod) {
        reloadData()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("₹", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF4ADE80))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Smart Expense & GST Auditor",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Vyapar / Khatabook Manager · On-Device",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val syncedCount = ExpenseAuditManager.syncWithDocumentHistory(context)
                                Toast.makeText(
                                    context,
                                    if (syncedCount > 0) "Audited $syncedCount new receipt(s) from vault!" else "All vault receipts are up to date",
                                    Toast.LENGTH_SHORT
                                ).show()
                                reloadData()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync from Vault",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Month Selector Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All Time" chip
                FilterChip(
                    selected = selectedPeriod == null,
                    onClick = { selectedPeriod = null },
                    label = { Text("All Time", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )

                availableMonths.forEach { (year, month) ->
                    val monthName = DateFormatSymbols.getInstance(Locale.ENGLISH).months[month - 1]
                    val label = "$monthName $year"
                    val isSelected = selectedPeriod?.first == year && selectedPeriod?.second == month
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPeriod = Pair(year, month) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF38BDF8)
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .height(44.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "📊 Analytics & GST",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color.White else Color(0xFF94A3B8)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "🧾 Receipts (${allReceipts.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color.White else Color(0xFF94A3B8)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (selectedTab == 0) {
                        AnalyticsTabContent(
                            summary = analyticsSummary,
                            receipts = allReceipts,
                            isExporting = isExporting,
                            onExportExcel = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        val periodStr = selectedPeriod?.let {
                                            val mName = DateFormatSymbols.getInstance(Locale.ENGLISH).months[it.second - 1]
                                            "$mName ${it.first}"
                                        } ?: "All Time"
                                        val file = ExpenseExcelExporter.generateTaxReportFile(
                                            context = context,
                                            receipts = allReceipts,
                                            periodTitle = periodStr
                                        )
                                        ExpenseExcelExporter.shareReport(context, file)
                                        Toast.makeText(context, "Exported Excel CA Report!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            }
                        )
                    } else {
                        ReceiptsListTabContent(
                            receipts = allReceipts,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            selectedCategory = selectedCategoryFilter,
                            onCategoryFilterChange = { selectedCategoryFilter = it },
                            onEditReceipt = { editingReceipt = it },
                            onDeleteReceipt = { r ->
                                scope.launch {
                                    ExpenseAuditManager.deleteReceipt(context, r.id)
                                    reloadData()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet for Editing / Verifying Receipt
    editingReceipt?.let { receipt ->
        EditReceiptBottomSheet(
            receipt = receipt,
            onDismiss = { editingReceipt = null },
            onSave = { updated ->
                scope.launch {
                    ExpenseAuditManager.saveReceipt(context, updated)
                    editingReceipt = null
                    reloadData()
                }
            }
        )
    }
}

@Composable
private fun AnalyticsTabContent(
    summary: MonthlyExpenseSummary?,
    receipts: List<ExpenseReceiptItem>,
    isExporting: Boolean,
    onExportExcel: () -> Unit
) {
    if (summary == null || receipts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🧾", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Receipts Audited in this Period",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap Refresh icon at top right to auto-scan bills from your vault.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top 3 KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Total Spend
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Total Spent", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹%,.2f".format(summary.totalSpend),
                        color = Color(0xFF4ADE80),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${summary.totalBillsCount} Invoices",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }

            // Total GST Claimable
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("GST Credit (ITC)", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹%,.2f".format(summary.totalGstCredit),
                        color = Color(0xFF38BDF8),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Input Tax Claim",
                        color = Color(0xFF38BDF8).copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Secondary Metrics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏪", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Top Vendor", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        Text(
                            summary.topMerchant,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📊", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Avg Bill", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        Text(
                            "₹%,.2f".format(summary.averageBillAmount),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 1-Click CA Tax Filing Excel Exporter Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExportExcel() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Export CA Tax Report (.xlsx)",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clean Excel sheet with GST formulas for Accountant",
                            color = Color(0xFFA7F3D0),
                            fontSize = 11.5.sp
                        )
                    }
                }

                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Category Breakdown Card Section
        Text(
            text = "Category Breakdown & ITC Eligibility",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            summary.categoryBreakdown.forEach { stat ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stat.category.iconEmoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stat.category.displayName,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${stat.count})",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            }

                            Text(
                                text = "₹%,.2f".format(stat.totalAmount),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { (stat.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(stat.category.colorHex),
                            trackColor = Color(0xFF334155)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "%.1f%% of total".format(stat.percentage),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (stat.category.isGstEligible) "✅ ITC Eligible" else "ℹ️ Personal / Non-ITC",
                                color = if (stat.category.isGstEligible) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptsListTabContent(
    receipts: List<ExpenseReceiptItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: ExpenseCategory?,
    onCategoryFilterChange: (ExpenseCategory?) -> Unit,
    onEditReceipt: (ExpenseReceiptItem) -> Unit,
    onDeleteReceipt: (ExpenseReceiptItem) -> Unit
) {
    val filtered = remember(receipts, searchQuery, selectedCategory) {
        receipts.filter { r ->
            val matchesCategory = selectedCategory == null || r.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                r.merchantName.contains(searchQuery, ignoreCase = true) ||
                r.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                r.gstin.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by merchant, invoice or GSTIN...", fontSize = 12.5.sp, color = Color(0xFF64748B)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategoryFilterChange(null) },
                label = { Text("All", fontSize = 11.5.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2563EB),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFF1E293B),
                    labelColor = Color(0xFF94A3B8)
                )
            )

            ExpenseCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onCategoryFilterChange(if (selectedCategory == cat) null else cat) },
                    label = { Text("${cat.iconEmoji} ${cat.displayName}", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(cat.colorHex),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching receipts found.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.5.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    ReceiptCard(
                        item = item,
                        onEdit = { onEditReceipt(item) },
                        onDelete = { onDeleteReceipt(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ReceiptCard(
    item: ExpenseReceiptItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Merchant & Total Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.merchantName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.billDate} · Inv: ${item.invoiceNumber.ifBlank { "N/A" }}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp
                    )
                }

                Text(
                    text = item.formattedGrandTotal(),
                    color = Color(0xFF4ADE80),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Badges and Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(item.category.colorHex).copy(alpha = 0.2f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${item.category.iconEmoji} ${item.category.displayName}",
                            color = Color(item.category.colorHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // GST status badge
                    if (item.hasValidGstin) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "GSTIN Verified",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Total GST amount if > 0
                    if (item.totalGst > 0) {
                        Text(
                            text = "Tax: ${item.formattedTotalGst()}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditReceiptBottomSheet(
    receipt: ExpenseReceiptItem,
    onDismiss: () -> Unit,
    onSave: (ExpenseReceiptItem) -> Unit
) {
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var merchantName by remember { mutableStateOf(receipt.merchantName) }
    var invoiceNo by remember { mutableStateOf(receipt.invoiceNumber) }
    var billDate by remember { mutableStateOf(receipt.billDate) }
    var gstin by remember { mutableStateOf(receipt.gstin) }
    var grandTotalStr by remember { mutableStateOf("%.2f".format(Locale.ROOT, receipt.grandTotal)) }
    var subtotalStr by remember { mutableStateOf("%.2f".format(Locale.ROOT, receipt.subtotal)) }
    var cgstStr by remember { mutableStateOf("%.2f".format(Locale.ROOT, receipt.cgst)) }
    var sgstStr by remember { mutableStateOf("%.2f".format(Locale.ROOT, receipt.sgst)) }
    var igstStr by remember { mutableStateOf("%.2f".format(Locale.ROOT, receipt.igst)) }
    var selectedCategory by remember { mutableStateOf(receipt.category) }
    var paymentMode by remember { mutableStateOf(receipt.paymentMode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = editSheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit & Verify Receipt", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            OutlinedTextField(
                value = merchantName,
                onValueChange = { merchantName = it },
                label = { Text("Merchant / Store Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = billDate,
                    onValueChange = { billDate = it },
                    label = { Text("Date (dd/MM/yyyy)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = invoiceNo,
                    onValueChange = { invoiceNo = it },
                    label = { Text("Invoice / Bill No") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = gstin,
                onValueChange = { gstin = it.uppercase(Locale.ROOT) },
                label = { Text("Vendor GSTIN (15 Digits)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = grandTotalStr,
                    onValueChange = { grandTotalStr = it },
                    label = { Text("Grand Total (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = subtotalStr,
                    onValueChange = { subtotalStr = it },
                    label = { Text("Subtotal (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            // GST Breakdown
            Text("GST Breakdown", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = cgstStr,
                    onValueChange = { cgstStr = it },
                    label = { Text("CGST (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = sgstStr,
                    onValueChange = { sgstStr = it },
                    label = { Text("SGST (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = igstStr,
                    onValueChange = { igstStr = it },
                    label = { Text("IGST (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            // Category Selector
            Text("Expense Category", fontSize = 12.sp, color = Color(0xFF94A3B8))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExpenseCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text("${cat.iconEmoji} ${cat.displayName}", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val grand = grandTotalStr.toDoubleOrNull() ?: 0.0
                    val sub = subtotalStr.toDoubleOrNull() ?: 0.0
                    val c = cgstStr.toDoubleOrNull() ?: 0.0
                    val s = sgstStr.toDoubleOrNull() ?: 0.0
                    val i = igstStr.toDoubleOrNull() ?: 0.0
                    val totalG = c + s + i

                    val updated = receipt.copy(
                        merchantName = merchantName.trim(),
                        invoiceNumber = invoiceNo.trim(),
                        billDate = billDate.trim(),
                        gstin = gstin.trim(),
                        grandTotal = grand,
                        subtotal = sub,
                        cgst = c,
                        sgst = s,
                        igst = i,
                        totalGst = totalG,
                        category = selectedCategory,
                        paymentMode = paymentMode.trim(),
                        verifiedByUser = true
                    )
                    onSave(updated)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Verify Receipt", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
