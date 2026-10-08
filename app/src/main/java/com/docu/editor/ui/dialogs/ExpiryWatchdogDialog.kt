package com.docu.editor.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docu.editor.core.classification.DocumentClassificationEngine.CanonicalCategory
import com.docu.editor.core.classification.ExpiryWatchdogEngine
import com.docu.editor.core.classification.ExpiryWatchdogScheduler
import com.docu.editor.core.history.SavedDocumentItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Enterprise Expiry Watchdog & Smart Auto-Vault Dialog.
 * Enables 1-tap Google Calendar integration, system alarm reminders (15d & 3d before),
 * AI document re-classification, and expiry date management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryWatchdogDialog(
    document: SavedDocumentItem,
    onSave: (newCategory: String, newSubtype: String, expiryDate: String, expiryEpoch: Long, hasReminder: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategory by remember { mutableStateOf(document.category.ifBlank { "Identity" }) }
    var subtypeText by remember { mutableStateOf(document.documentSubtype.ifBlank { "Official Document" }) }
    var expiryDateStr by remember { mutableStateOf(document.expiryDateString) }
    var expiryEpochMs by remember {
        mutableLongStateOf(
            if (document.expiryEpochMs > 0L) document.expiryEpochMs
            else parseDateToEpoch(document.expiryDateString)
        )
    }
    var hasAlarmSet by remember { mutableStateOf(document.hasCalendarReminder) }

    val categoriesList = listOf("Identity", "Bills & Finance", "Health", "Academics", "Legal", "General")

    // Days remaining calculation
    val now = System.currentTimeMillis()
    val daysLeft = if (expiryEpochMs > 0L) {
        ((expiryEpochMs - now) / (1000L * 60L * 60L * 24L)).toInt()
    } else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Vault & Expiry Watchdog",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = document.title,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expiry Status Banner
            if (daysLeft != null) {
                val bannerColor = when {
                    daysLeft < 0 -> Color(0xFFFEF2F2)
                    daysLeft in 0..7 -> Color(0xFFFFF7ED)
                    daysLeft in 8..30 -> Color(0xFFFEFCE8)
                    else -> Color(0xFFF0FDF4)
                }
                val borderColor = when {
                    daysLeft < 0 -> Color(0xFFEF4444)
                    daysLeft in 0..7 -> Color(0xFFF97316)
                    daysLeft in 8..30 -> Color(0xFFEAB308)
                    else -> Color(0xFF22C55E)
                }
                val textColor = when {
                    daysLeft < 0 -> Color(0xFFB91C1C)
                    daysLeft in 0..7 -> Color(0xFFC2410C)
                    daysLeft in 8..30 -> Color(0xFFA16207)
                    else -> Color(0xFF15803D)
                }
                val statusText = when {
                    daysLeft < 0 -> "⚠️ Expired ${kotlin.math.abs(daysLeft)} days ago ($expiryDateStr)"
                    daysLeft == 0 -> "🚨 Due / Expiring Today ($expiryDateStr)!"
                    daysLeft in 1..7 -> "⚠️ Urgent: Expires in $daysLeft days ($expiryDateStr)"
                    daysLeft in 8..30 -> "⏳ Expiring Soon: $daysLeft days remaining ($expiryDateStr)"
                    else -> "✅ Active: Valid for $daysLeft more days ($expiryDateStr)"
                }

                Surface(
                    color = bannerColor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Expiry Date Input & Quick Presets
            Text(
                text = "EXPIRY / RENEWAL / DUE DATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = expiryDateStr,
                onValueChange = {
                    expiryDateStr = it
                    val parsed = parseDateToEpoch(it)
                    if (parsed > 0L) expiryEpochMs = parsed
                },
                placeholder = { Text("e.g. 14/05/2028 or 14 May 2028", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFD97706),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Preset Buttons (+1M, +6M, +1Y, +5Y, +10Y)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fun addTime(field: Int, amount: Int, label: String) {
                    val cal = Calendar.getInstance()
                    if (expiryEpochMs > 0L) cal.timeInMillis = expiryEpochMs
                    cal.add(field, amount)
                    expiryEpochMs = cal.timeInMillis
                    expiryDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
                }

                PresetChip(label = "+1 Month (Bill)") { addTime(Calendar.MONTH, 1, "+1M") }
                PresetChip(label = "+6 Months") { addTime(Calendar.MONTH, 6, "+6M") }
                PresetChip(label = "+1 Year") { addTime(Calendar.YEAR, 1, "+1Y") }
                PresetChip(label = "+5 Years") { addTime(Calendar.YEAR, 5, "+5Y") }
                PresetChip(label = "+10 Years (ID/DL)") { addTime(Calendar.YEAR, 10, "+10Y") }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 1-Tap Google Calendar & Local Alarm Actions
            Text(
                text = "AUTOMATED REMINDERS & ALERTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Google Calendar Button
                Button(
                    onClick = {
                        val epoch = if (expiryEpochMs > 0L) expiryEpochMs else parseDateToEpoch(expiryDateStr)
                        if (epoch > 0L) {
                            ExpiryWatchdogScheduler.addToGoogleCalendar(
                                context = context,
                                docTitle = document.title,
                                expiryEpochMs = epoch,
                                notes = "Document Type: $subtypeText ($selectedCategory)"
                            )
                        } else {
                            Toast.makeText(context, "Please set a valid expiry date first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Calendar", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // System Alarm Notification Button (15d & 3d before)
                Button(
                    onClick = {
                        val epoch = if (expiryEpochMs > 0L) expiryEpochMs else parseDateToEpoch(expiryDateStr)
                        if (epoch > 0L) {
                            val scheduled15 = ExpiryWatchdogScheduler.scheduleLocalReminder(context, document.id, document.title, epoch, 15)
                            val scheduled3 = ExpiryWatchdogScheduler.scheduleLocalReminder(context, document.id, document.title, epoch, 3)
                            hasAlarmSet = true
                            Toast.makeText(context, "System reminders scheduled for 15 days & 3 days before expiry! ⏰", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Please set a valid expiry date first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasAlarmSet) Color(0xFF047857) else Color(0xFFD97706)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (hasAlarmSet) Icons.Default.Check else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasAlarmSet) "Alarm Active" else "Set Alarms",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Category Selection Chips
            Text(
                text = "DOCUMENT CATEGORY",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (cat in categoriesList) {
                    val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF2563EB) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtype Editor
            OutlinedTextField(
                value = subtypeText,
                onValueChange = { subtypeText = it },
                label = { Text("Document Subtype / Tag") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = {
                        val finalEpoch = if (expiryEpochMs > 0L) expiryEpochMs else parseDateToEpoch(expiryDateStr)
                        onSave(selectedCategory, subtypeText.trim(), expiryDateStr.trim(), finalEpoch, hasAlarmSet)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save & Apply", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun parseDateToEpoch(dateStr: String): Long {
    if (dateStr.isBlank()) return 0L
    val clean = dateStr.replace(',', ' ').trim().replace("\\s+".toRegex(), " ")
    val patterns = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
        "d/M/yyyy", "d-M-yyyy", "d.M.yyyy",
        "yyyy-MM-dd",
        "dd MMM yyyy", "d MMM yyyy",
        "MMM dd yyyy"
    )
    for (pat in patterns) {
        try {
            val sdf = SimpleDateFormat(pat, Locale.US).apply { isLenient = false }
            val parsed = sdf.parse(clean)
            if (parsed != null) return parsed.time
        } catch (_: Exception) {}
    }
    return 0L
}
