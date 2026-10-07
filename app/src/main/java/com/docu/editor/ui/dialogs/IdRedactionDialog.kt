package com.docu.editor.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun IdRedactionDialog(
    onDismiss: () -> Unit,
    onApplyRedaction: (IdRedactionEngine.RedactionMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(IdRedactionEngine.RedactionMode.AADHAAR_MASK) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
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
                            text = "Auto-Redact Sensitive ID",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Automatically scan the document and securely mask Government IDs (Aadhaar, PAN, Bank Cards):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = selectedMode == IdRedactionEngine.RedactionMode.AADHAAR_MASK,
                        onClick = { selectedMode = IdRedactionEngine.RedactionMode.AADHAAR_MASK },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("🔒 Aadhaar Legal Masking", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                Text("⬛ Confidential Blackout Box", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Draws solid opaque black bar over PAN & ID numbers", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    FilterChip(
                        selected = selectedMode == IdRedactionEngine.RedactionMode.PIXELATE_BLUR,
                        onClick = { selectedMode = IdRedactionEngine.RedactionMode.PIXELATE_BLUR },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("░ Mosaic Pixelation Blur", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Heavily blurs sensitive numbers while preserving layout", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        onApplyRedaction(selectedMode)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("✓ Scan & Redact Numbers Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
