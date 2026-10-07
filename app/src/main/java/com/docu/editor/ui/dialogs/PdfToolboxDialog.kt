package com.docu.editor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.docu.editor.core.pdf.PdfCompressionEngine

enum class PdfToolboxTab(val label: String, val icon: String) {
    COMPRESS("Compress", "🗜️"),
    SECURITY("Security", "🔒"),
    SPLIT("Split", "✂️"),
    MERGE("Merge", "📎"),
    PAGES("Pages", "📑")
}

@Composable
fun PdfToolboxDialog(
    onCompressSelected: (dpi: Int, quality: Int) -> Unit,
    onPasswordProtectSelected: (
        userPassword: String,
        ownerPassword: String,
        canPrint: Boolean,
        canExtractContent: Boolean,
        canModify: Boolean,
        canFillInForm: Boolean,
        keyLength: Int
    ) -> Unit,
    onUnlockPdfSelected: (password: String) -> Unit = {},
    onPkiSignSelected: () -> Unit = {},
    onSplitAllSelected: () -> Unit = {},
    onSplitByRangeSelected: (range: String) -> Unit = {},
    onSplitIntoChunksSelected: (chunkSize: Int) -> Unit = {},
    onExtractImagesSelected: (quality: Int) -> Unit = {},
    onMergeFilesSelected: () -> Unit = {},
    onOpenPageStudioSelected: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(PdfToolboxTab.COMPRESS) }

    // Compress State
    var selectedPreset by remember { mutableStateOf(PdfCompressionEngine.CompressionPreset.BALANCED_OFFICE) }
    var customDpi by remember { mutableIntStateOf(150) }
    var customQuality by remember { mutableIntStateOf(75) }
    var isCustomCompress by remember { mutableStateOf(false) }

    // Security State
    var securitySubMode by remember { mutableStateOf(0) } // 0: Lock, 1: Unlock, 2: PKI Sign
    var userPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var ownerPassword by remember { mutableStateOf("") }
    var canPrint by remember { mutableStateOf(true) }
    var canExtractContent by remember { mutableStateOf(false) }
    var canModify by remember { mutableStateOf(false) }
    var canFillInForm by remember { mutableStateOf(true) }
    var keyLength by remember { mutableIntStateOf(128) }
    var unlockPassword by remember { mutableStateOf("") }

    // Split State
    var splitSubMode by remember { mutableStateOf(0) } // 0: Range, 1: All Pages, 2: Chunks, 3: JPGs
    var rangeSpec by remember { mutableStateOf("1-3, 5") }
    var chunkSize by remember { mutableIntStateOf(2) }
    var imgQuality by remember { mutableIntStateOf(92) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
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
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PDF Toolbox Studio",
                                color = Color(0xFF0F172A),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enterprise Document Suite",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PdfToolboxTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            color = if (isSelected) Color(0xFF059669) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedTab = tab }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = tab.icon,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    PdfToolboxTab.COMPRESS -> {
                        Text(
                            text = "Compression Presets",
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        PdfCompressionEngine.CompressionPreset.values().forEach { preset ->
                            val isSelected = !isCustomCompress && selectedPreset == preset
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFECFDF5) else Color(0xFFF8FAFC))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF059669) else Color(0xFFE2E8F0),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        isCustomCompress = false
                                        selectedPreset = preset
                                    }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when (preset) {
                                                PdfCompressionEngine.CompressionPreset.MAXIMUM_COMPRESSION -> "⚡ Extreme Compression"
                                                PdfCompressionEngine.CompressionPreset.BALANCED_OFFICE -> "⭐ Recommended (Office)"
                                                PdfCompressionEngine.CompressionPreset.HIGH_QUALITY_PRINT -> "💎 High Quality (Print)"
                                            },
                                            color = if (isSelected) Color(0xFF047857) else Color(0xFF1E293B),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) Color(0xFFD1FAE5) else Color(0xFFE2E8F0)
                                        ) {
                                            Text(
                                                text = "${preset.dpi} DPI • Q${preset.jpegQuality}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF065F46) else Color(0xFF64748B),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = preset.description,
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Custom Tuning Option
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCustomCompress) Color(0xFFECFDF5) else Color(0xFFF8FAFC))
                                .border(
                                    1.dp,
                                    if (isCustomCompress) Color(0xFF059669) else Color(0xFFE2E8F0),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { isCustomCompress = true }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚙️ Custom DPI & Quality Tuning",
                                        color = if (isCustomCompress) Color(0xFF047857) else Color(0xFF1E293B),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (isCustomCompress) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Resolution: $customDpi DPI", fontSize = 11.sp, color = Color(0xFF475569))
                                    Slider(
                                        value = customDpi.toFloat(),
                                        onValueChange = { customDpi = it.toInt() },
                                        valueRange = 72f..300f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF059669), activeTrackColor = Color(0xFF059669))
                                    )
                                    Text("Image Quality: $customQuality%", fontSize = 11.sp, color = Color(0xFF475569))
                                    Slider(
                                        value = customQuality.toFloat(),
                                        onValueChange = { customQuality = it.toInt() },
                                        valueRange = 40f..95f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFF059669), activeTrackColor = Color(0xFF059669))
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val dpi = if (isCustomCompress) customDpi else selectedPreset.dpi
                                val quality = if (isCustomCompress) customQuality else selectedPreset.jpegQuality
                                onCompressSelected(dpi, quality)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val targetDpi = if (isCustomCompress) customDpi else selectedPreset.dpi
                            Text("Compress & Save (${targetDpi} DPI)", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    PdfToolboxTab.SECURITY -> {
                        // Sub-mode tabs: 0: Lock, 1: Unlock, 2: PKI Sign
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("AES Lock", "Unlock PDF", "PKI Signature").forEachIndexed { index, title ->
                                val isSelected = securitySubMode == index
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFE11D48) else Color(0xFFF1F5F9),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { securitySubMode = index }
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        modifier = Modifier.padding(vertical = 7.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        when (securitySubMode) {
                            0 -> {
                                Text(
                                    text = "128-bit / 256-bit AES Document Encryption",
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = userPassword,
                                    onValueChange = { userPassword = it },
                                    label = { Text("Open Document Password") },
                                    singleLine = true,
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFE11D48),
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ownerPassword,
                                    onValueChange = { ownerPassword = it },
                                    label = { Text("Master / Owner Password (Optional)") },
                                    singleLine = true,
                                    placeholder = { Text("Leave blank to auto-generate", fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFE11D48),
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Permission checkboxes
                                Text("Permissions & Restrictions", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = canPrint,
                                        onCheckedChange = { canPrint = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE11D48))
                                    )
                                    Text("Allow Printing Document", fontSize = 11.5.sp, color = Color(0xFF1E293B))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = canExtractContent,
                                        onCheckedChange = { canExtractContent = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE11D48))
                                    )
                                    Text("Allow Copying Text & Images", fontSize = 11.5.sp, color = Color(0xFF1E293B))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = canFillInForm,
                                        onCheckedChange = { canFillInForm = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE11D48))
                                    )
                                    Text("Allow Form Filling & Comments", fontSize = 11.5.sp, color = Color(0xFF1E293B))
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        if (userPassword.isNotBlank()) {
                                            onPasswordProtectSelected(
                                                userPassword.trim(),
                                                ownerPassword.trim().ifBlank { userPassword.trim() + "_owner" },
                                                canPrint,
                                                canExtractContent,
                                                canModify,
                                                canFillInForm,
                                                keyLength
                                            )
                                        }
                                    },
                                    enabled = userPassword.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE11D48),
                                        disabledContainerColor = Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Encrypt & Protect PDF", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            1 -> {
                                Text(
                                    text = "Unlock & Remove PDF Password",
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Permanently removes password protection and generates an unencrypted PDF copy.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = unlockPassword,
                                    onValueChange = { unlockPassword = it },
                                    label = { Text("Current Document Password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (unlockPassword.isNotBlank()) {
                                            onUnlockPdfSelected(unlockPassword.trim())
                                        }
                                    },
                                    enabled = unlockPassword.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unlock & Save Decrypted PDF", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            2 -> {
                                Text(
                                    text = "Legal PKI Digital Signature",
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Cryptographically signs the PDF using an X.509 PKCS#12 (.pfx/.p12) digital certificate with Adobe Green Tick verification.",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = onPkiSignSelected,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open PKI Certificate Signer", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    PdfToolboxTab.SPLIT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Range", "All Pages", "Chunks", "JPGs").forEachIndexed { index, label ->
                                val isSelected = splitSubMode == index
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFF1F5F9),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { splitSubMode = index }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        modifier = Modifier.padding(vertical = 7.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        when (splitSubMode) {
                            0 -> {
                                Text("Extract Custom Page Ranges", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("Extract specific pages into a new PDF (e.g. 1-3, 5, 8-10).", fontSize = 11.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = rangeSpec,
                                    onValueChange = { rangeSpec = it },
                                    label = { Text("Page Range Expression") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF4F46E5),
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("1-3", "1-5", "1, 3, 5", "2, 4, 6").forEach { preset ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFEEF2FF),
                                            modifier = Modifier.clickable { rangeSpec = preset }
                                        ) {
                                            Text(
                                                text = preset,
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF4338CA),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (rangeSpec.isNotBlank()) onSplitByRangeSelected(rangeSpec.trim())
                                    },
                                    enabled = rangeSpec.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Extract Range to PDF", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            1 -> {
                                Text("Split Every Page into Single PDFs", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("Breaks every page of this document into individual page_1.pdf, page_2.pdf files in Downloads.", fontSize = 11.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = onSplitAllSelected,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Split All Pages into Individual Files", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            2 -> {
                                Text("Split into Fixed Chunks", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("Divides the PDF into equal blocks of N pages.", fontSize = 11.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(2, 3, 5, 10).forEach { size ->
                                        val isSelected = chunkSize == size
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFF1F5F9),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { chunkSize = size }
                                        ) {
                                            Text(
                                                text = "Every $size",
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF475569),
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { onSplitIntoChunksSelected(chunkSize) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Split into Chunks of $chunkSize Pages", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            3 -> {
                                Text("Extract All Pages to High-Res JPGs", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("Renders every page into high-resolution JPEG images saved straight to Downloads.", fontSize = 11.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(10.dp))

                                Text("Image Quality: $imgQuality%", fontSize = 11.sp, color = Color(0xFF475569))
                                Slider(
                                    value = imgQuality.toFloat(),
                                    onValueChange = { imgQuality = it.toInt() },
                                    valueRange = 70f..100f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF0D9488), activeTrackColor = Color(0xFF0D9488))
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { onExtractImagesSelected(imgQuality) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Extract All Pages to JPG Photos", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    PdfToolboxTab.MERGE -> {
                        Text(
                            text = "Batch PDF & Image Merge",
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select multiple PDF documents or camera images to combine into a single unified multi-page PDF.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onMergeFilesSelected,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Files to Merge", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    PdfToolboxTab.PAGES -> {
                        Text(
                            text = "Visual Page Organizer & Reorder Studio",
                            color = Color(0xFF0F172A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Rearrange, rotate 90°, delete, duplicate, or insert new pages in an intuitive visual thumbnail grid.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onOpenPageStudioSelected,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Visual Page Grid Studio", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
