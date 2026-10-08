package com.docu.editor.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.core.pdf.AcroFormFieldItem

/**
 * Enterprise Form Assistant Dialog — 100% Pro.
 * Supports native interactive AcroForm PDF fields AND
 * auto-detected fillable blanks/check-boxes on non-fillable flat scanned forms.
 */
@Composable
fun AcroFormEditDialog(
    formFields: List<AcroFormFieldItem>,
    onDismiss: () -> Unit,
    onSaveFields: (Map<String, String>) -> Unit
) {
    val isFlatForm = remember(formFields) { formFields.any { it.isFlatField } }
    val allFields = remember { mutableStateListOf<AcroFormFieldItem>().apply { addAll(formFields) } }

    val fieldValues = remember {
        mutableStateMapOf<String, String>().apply {
            formFields.forEach { put(it.name, it.value) }
        }
    }

    var showAddFieldDialog by remember { mutableStateOf(false) }
    var newFieldName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isFlatForm) Color(0xFFEFF6FF) else Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFlatForm) Icons.Default.Edit else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (isFlatForm) Color(0xFF2563EB) else Color(0xFFD97706),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isFlatForm) "Smart Document Form Filler" else "Fillable PDF Form",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isFlatForm) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFDBEAFE)
                                ) {
                                    Text(
                                        text = "AUTO-DETECT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isFlatForm) {
                                "${allFields.size} Fillable Blanks & Checkboxes Detected"
                            } else {
                                "${allFields.size} Native Interactive Fields"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Helpful banner for flat detected forms
                if (isFlatForm) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Underlined blanks and checkboxes auto-detected. Fill your responses below to cleanly stamp them into position.",
                                fontSize = 11.sp,
                                color = Color(0xFF15803D),
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Fields list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allFields) { field ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = field.name,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (field.isFlatField) {
                                        Text(
                                            text = if (field.type == "CHECKBOX") "Checkbox [ ]" else "Underlined Blank",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                if (field.type == "CHECKBOX") {
                                    val isChecked = fieldValues[field.name]?.let {
                                        it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true)
                                    } ?: false
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                fieldValues[field.name] = if (checked) "true" else "false"
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = if (isFlatForm) Color(0xFF2563EB) else MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isChecked) "Checked (Marked Yes)" else "Unchecked (Blank)",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isChecked) Color(0xFF1E293B) else Color(0xFF64748B)
                                        )
                                    }
                                } else {
                                    val currentValue = fieldValues[field.name] ?: ""
                                    OutlinedTextField(
                                        value = currentValue,
                                        onValueChange = { fieldValues[field.name] = it },
                                        singleLine = field.type != "OTHER",
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            if (currentValue.isNotEmpty()) {
                                                IconButton(onClick = { fieldValues[field.name] = "" }) {
                                                    Icon(
                                                        Icons.Default.Clear,
                                                        contentDescription = "Clear",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        },
                                        placeholder = { Text("Type ${field.name}...", fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Add Custom Field button at bottom of list
                    item {
                        OutlinedButton(
                            onClick = { showAddFieldDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Custom Field Blank", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { onSaveFields(fieldValues.toMap()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFlatForm) Color(0xFF2563EB) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFlatForm) "Burn & Apply to Form" else "Save Form Fields",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showAddFieldDialog) {
        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            title = { Text("Add Custom Field Blank", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFieldName,
                    onValueChange = { newFieldName = it },
                    label = { Text("Field Label (e.g. Place, Mobile)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFieldName.trim()
                        if (name.isNotEmpty()) {
                            val customField = AcroFormFieldItem(
                                name = name,
                                type = "TEXT",
                                value = "",
                                isReadOnly = false,
                                isFlatField = isFlatForm,
                                boundsLeft = 100,
                                boundsTop = 200,
                                boundsRight = 500,
                                boundsBottom = 260
                            )
                            allFields.add(customField)
                            fieldValues[name] = ""
                            newFieldName = ""
                            showAddFieldDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
