package com.docu.editor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.core.scanner.TimestampEngine

@Composable
fun TimestampDialog(
    initialCompanyName: String = "DOCUEDIT ENTERPRISE",
    initialLocation: String = "Connaught Place, New Delhi",
    initialLat: Double = 28.6315,
    initialLon: Double = 77.2167,
    onApplyTimestamp: (TimestampEngine.TimestampConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var companyName by remember { mutableStateOf(initialCompanyName) }
    var locationAddress by remember { mutableStateOf(initialLocation) }
    var inspectorName by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(TimestampEngine.BadgeStyle.STUDIO_FROSTED_GLASS) }
    var selectedPosition by remember { mutableStateOf(TimestampEngine.BadgePosition.BOTTOM_RIGHT) }
    var includeCryptoHash by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Timestamp & GPS Geotag Pro",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "Burns a certified date-time badge with GPS geotags & tamper-proof cryptographic EXIF metadata for legal/insurance evidence.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Studio Badge Style
                Text(
                    "Studio Badge Design",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStyle == TimestampEngine.BadgeStyle.STUDIO_FROSTED_GLASS,
                        onClick = { selectedStyle = TimestampEngine.BadgeStyle.STUDIO_FROSTED_GLASS },
                        label = { Text("Frosted Glass", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedStyle == TimestampEngine.BadgeStyle.OFFICIAL_EVIDENCE_BOX,
                        onClick = { selectedStyle = TimestampEngine.BadgeStyle.OFFICIAL_EVIDENCE_BOX },
                        label = { Text("Official Evidence", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Company Name Field
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("Organization / Company Header", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Location Field
                OutlinedTextField(
                    value = locationAddress,
                    onValueChange = { locationAddress = it },
                    label = { Text("Location Address / Site Name", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color(0xFF2563EB)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Inspector Name Field
                OutlinedTextField(
                    value = inspectorName,
                    onValueChange = { inspectorName = it },
                    label = { Text("Inspector / Officer Name (Optional)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Badge Position
                Text(
                    "Stamp Placement",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPosition == TimestampEngine.BadgePosition.BOTTOM_RIGHT,
                        onClick = { selectedPosition = TimestampEngine.BadgePosition.BOTTOM_RIGHT },
                        label = { Text("Bottom Right", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPosition == TimestampEngine.BadgePosition.BOTTOM_LEFT,
                        onClick = { selectedPosition = TimestampEngine.BadgePosition.BOTTOM_LEFT },
                        label = { Text("Bottom Left", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cryptographic EXIF checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeCryptoHash = !includeCryptoHash },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeCryptoHash,
                        onCheckedChange = { includeCryptoHash = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2563EB))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Tamper-Proof EXIF + SHA-256 Hash", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                        Text("Injects GPS & cryptographic hash directly into file headers", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val config = TimestampEngine.TimestampConfig(
                            companyName = companyName.trim().ifBlank { "DOCUEDIT ENTERPRISE" },
                            locationAddress = locationAddress.trim().ifBlank { "Verified Location" },
                            inspectorName = inspectorName.trim(),
                            latitude = initialLat,
                            longitude = initialLon,
                            style = selectedStyle,
                            position = selectedPosition,
                            includeCryptoHash = includeCryptoHash
                        )
                        onApplyTimestamp(config)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Apply Timestamp Badge & Geotag",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
