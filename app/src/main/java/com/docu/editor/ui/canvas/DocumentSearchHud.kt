package com.docu.editor.ui.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Enterprise Interactive Document Search HUD (Ctrl+F / Cmd+F Style).
 *
 * Provides:
 * - Real-time word query matching with live match counter (e.g. "3 of 12")
 * - Navigation: Next match (▼) and Previous match (▲) with viewport centering
 * - Multi-lingual options: Match Case [Aa], Whole Word [W], Hindi OCR Normalizer [🇮🇳]
 * - Multi-page distribution index pills
 * - Expandable Find & Replace section
 * - Interactive tap-to-copy toast confirmation
 */
@Composable
fun DocumentSearchHud(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    currentMatchIndex: Int,
    totalMatches: Int,
    isCaseSensitive: Boolean,
    onToggleCaseSensitive: (Boolean) -> Unit,
    isWholeWord: Boolean,
    onToggleWholeWord: (Boolean) -> Unit,
    isHindiTolerant: Boolean,
    onToggleHindiTolerant: (Boolean) -> Unit,
    multiPageMatchCounts: Map<Int, Int>,
    currentPageIndex: Int,
    pdfPageCount: Int,
    onSelectPage: (Int) -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onReplace: (query: String, replaceText: String, allPages: Boolean) -> Unit,
    copiedToastText: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isReplaceExpanded by remember { mutableStateOf(false) }
    var replaceWithText by remember { mutableStateOf("") }
    var isReplaceAllPagesScope by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF20F172A),
        border = BorderStroke(1.2.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 1. Primary Search Bar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Magnifier Icon
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Query Input Box
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color(0xFFF59E0B)),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Find in document (English/हिन्दी)...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Clear Query Button
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChanged("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Live Match Counter Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                totalMatches > 0 -> Color(0xFFF59E0B).copy(alpha = 0.20f)
                                searchQuery.isNotBlank() -> Color(0xFFEF4444).copy(alpha = 0.20f)
                                else -> Color(0xFF334155).copy(alpha = 0.35f)
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                totalMatches > 0 -> Color(0xFFF59E0B).copy(alpha = 0.60f)
                                searchQuery.isNotBlank() -> Color(0xFFEF4444).copy(alpha = 0.60f)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when {
                            totalMatches > 0 -> "${currentMatchIndex + 1} of $totalMatches"
                            searchQuery.isNotBlank() -> "0 found"
                            else -> "Ready"
                        },
                        color = when {
                            totalMatches > 0 -> Color(0xFFFBBF24)
                            searchQuery.isNotBlank() -> Color(0xFFF87171)
                            else -> Color(0xFF94A3B8)
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Previous Match Button (▲)
                IconButton(
                    onClick = onNavigatePrevious,
                    enabled = totalMatches > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous Match",
                        tint = if (totalMatches > 0) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Next Match Button (▼)
                IconButton(
                    onClick = onNavigateNext,
                    enabled = totalMatches > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next Match",
                        tint = if (totalMatches > 0) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Toggle Replace Action
                IconButton(
                    onClick = { isReplaceExpanded = !isReplaceExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FindReplace,
                        contentDescription = "Find and Replace",
                        tint = if (isReplaceExpanded) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Close Search Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Search",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Filter Chips & Multi-Page Traversal Row
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Match Case Chip [Aa]
                SearchOptionFilterPill(
                    label = "Aa Case",
                    isActive = isCaseSensitive,
                    onClick = { onToggleCaseSensitive(!isCaseSensitive) }
                )

                // Whole Word Chip [W]
                SearchOptionFilterPill(
                    label = "Whole Word",
                    isActive = isWholeWord,
                    onClick = { onToggleWholeWord(!isWholeWord) }
                )

                // Hindi OCR Devanagari Normalizer Chip [🇮🇳]
                SearchOptionFilterPill(
                    label = "🇮🇳 Hindi OCR",
                    isActive = isHindiTolerant,
                    onClick = { onToggleHindiTolerant(!isHindiTolerant) }
                )

                // Multi-Page Index Distribution
                if (pdfPageCount > 1 && multiPageMatchCounts.isNotEmpty()) {
                    multiPageMatchCounts.keys.sorted().forEach { pIdx ->
                        val count = multiPageMatchCounts[pIdx] ?: 0
                        val isCurrent = pIdx == currentPageIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) Color(0xFFF59E0B).copy(alpha = 0.25f)
                                    else Color(0xFF1E293B)
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) Color(0xFFF59E0B) else Color(0xFF334155),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectPage(pIdx) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Pg ${pIdx + 1}: $count",
                                color = if (isCurrent) Color(0xFFFDE047) else Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 3. Expandable Find & Replace Row
            AnimatedVisibility(visible = isReplaceExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = replaceWithText,
                            onValueChange = { replaceWithText = it },
                            placeholder = { Text("Replace with...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Button(
                            onClick = {
                                onReplace(searchQuery, replaceWithText, isReplaceAllPagesScope)
                                replaceWithText = ""
                            },
                            enabled = searchQuery.isNotBlank() && totalMatches > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isReplaceAllPagesScope) Color(0xFF047857) else Color(0xFF059669)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = if (isReplaceAllPagesScope && pdfPageCount > 1) "Replace All Pgs" else "Replace",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (pdfPageCount > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { isReplaceAllPagesScope = !isReplaceAllPagesScope }
                        ) {
                            Checkbox(
                                checked = isReplaceAllPagesScope,
                                onCheckedChange = { isReplaceAllPagesScope = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981)),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Apply replacement across all $pdfPageCount pages",
                                fontSize = 11.sp,
                                color = if (isReplaceAllPagesScope) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            // 4. Copied Toast Notification or Pro Tip Footer
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedVisibility(
                visible = !copiedToastText.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.20f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Copied",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "📋 Copied '$copiedToastText' to clipboard",
                        color = Color(0xFFD1FAE5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (copiedToastText.isNullOrBlank() && totalMatches > 0) {
                Text(
                    text = "💡 Tap any yellow highlighted word on the document to copy it instantly",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchOptionFilterPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) Color(0xFFF59E0B).copy(alpha = 0.22f)
                else Color(0xFF1E293B)
            )
            .border(
                1.dp,
                if (isActive) Color(0xFFF59E0B) else Color(0xFF334155),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (isActive) Color(0xFFFBBF24) else Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        )
    }
}
