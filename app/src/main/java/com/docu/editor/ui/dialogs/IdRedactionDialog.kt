package com.docu.editor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.core.security.IdRedactionEngine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdRedactionDialog(
    onDismiss: () -> Unit,
    onApplyRedaction: (IdRedactionEngine.RedactionOptions) -> Unit
) {
    var selectedMode by remember { mutableStateOf(IdRedactionEngine.RedactionMode.AADHAAR_MASK) }
    var redactAadhaar by remember { mutableStateOf(true) }
    var redactPan by remember { mutableStateOf(true) }
    var redactCards by remember { mutableStateOf(true) }
    var redactPassport by remember { mutableStateOf(true) }
    var redactVoterId by remember { mutableStateOf(true) }
    var redactDL by remember { mutableStateOf(true) }
    var redactPhoneEmail by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF059669)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Redact Government IDs",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Automatically detect and securely censor identity card numbers and sensitive credentials across scanned pages:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Target Filters
                Text(
                    text = "Target ID Types",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = redactAadhaar,
                        onClick = { redactAadhaar = !redactAadhaar },
                        label = { Text("Aadhaar (12-digit)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = redactPan,
                        onClick = { redactPan = !redactPan },
                        label = { Text("PAN Card", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = redactCards,
                        onClick = { redactCards = !redactCards },
                        label = { Text("Bank Cards", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = redactPassport,
                        onClick = { redactPassport = !redactPassport },
                        label = { Text("Passport", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = redactVoterId,
                        onClick = { redactVoterId = !redactVoterId },
                        label = { Text("Voter ID (EPIC)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = redactDL,
                        onClick = { redactDL = !redactDL },
                        label = { Text("Driving License", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = redactPhoneEmail,
                        onClick = { redactPhoneEmail = !redactPhoneEmail },
                        label = { Text("Phone & Email", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Redaction Style Modes
                Text(
                    text = "Censorship Style",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedMode == IdRedactionEngine.RedactionMode.AADHAAR_MASK,
                        onClick = { selectedMode = IdRedactionEngine.RedactionMode.AADHAAR_MASK },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("🔒 Legal Masking (Recommended)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Masks first 8 digits (XXXX XXXX 1234) complying with UIDAI guidelines", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    FilterChip(
                        selected = selectedMode == IdRedactionEngine.RedactionMode.SOLID_BLACKOUT,
                        onClick = { selectedMode = IdRedactionEngine.RedactionMode.SOLID_BLACKOUT },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("⬛ Confidential Blackout Box", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Draws solid opaque black bar over confidential numbers", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    FilterChip(
                        selected = selectedMode == IdRedactionEngine.RedactionMode.PIXELATE_BLUR,
                        onClick = { selectedMode = IdRedactionEngine.RedactionMode.PIXELATE_BLUR },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("░ Mosaic Pixelation Blur", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Heavy block pixelation preserving document layout", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    FilterChip(
                        selected = selectedMode == IdRedactionEngine.RedactionMode.WHITE_ERASURE,
                        onClick = { selectedMode = IdRedactionEngine.RedactionMode.WHITE_ERASURE },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("⬜ Clean Background Erasure", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Erases numbers cleanly matching document background", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val options = IdRedactionEngine.RedactionOptions(
                            mode = selectedMode,
                            redactAadhaar = redactAadhaar,
                            redactPan = redactPan,
                            redactCards = redactCards,
                            redactPassport = redactPassport,
                            redactVoterId = redactVoterId,
                            redactDrivingLicense = redactDL,
                            redactPhone = redactPhoneEmail,
                            redactEmail = redactPhoneEmail,
                            redactSsn = true
                        )
                        onApplyRedaction(options)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("✓ Scan & Redact Selected IDs", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
