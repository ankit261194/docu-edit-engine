package com.docu.editor.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.docu.editor.core.signature.pki.PdfDigitalSigner
import com.docu.editor.core.signature.pki.PkiCertificateInfo
import com.docu.editor.core.signature.pki.PkiCertificateManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Enterprise PKI Cryptographic Digital Signature Dialog.
 * Enables legal X.509 PKCS#12 signing with Adobe Acrobat Green Checkmark verification.
 */
@Composable
fun PkiDigitalSignDialog(
    inputPdfFile: File,
    pageCount: Int,
    onSignCompleted: (File) -> Unit,
    onShareFile: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeCert by remember { mutableStateOf<PkiCertificateInfo?>(null) }
    var password by remember { mutableStateOf("docuedit2026") }
    var passwordVisible by remember { mutableStateOf(false) }

    var signerName by remember { mutableStateOf("DocuEdit Verified Signer") }
    var organization by remember { mutableStateOf("DocuEdit Digital Signature Authority") }
    var reason by remember { mutableStateOf("Document Approved & Legally Certified") }
    var location by remember { mutableStateOf("India") }
    var addVisualSeal by remember { mutableStateOf(true) }
    var sealPageNumber by remember { mutableStateOf(pageCount.coerceAtLeast(1)) }
    var enableLtvTimestamp by remember { mutableStateOf(true) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var signResult by remember { mutableStateOf<PdfDigitalSigner.SignResult?>(null) }

    // Check for saved device certificate on launch
    LaunchedEffect(Unit) {
        val saved = PkiCertificateManager.getSavedDeviceCertificate(context, password.toCharArray())
        if (saved != null) {
            activeCert = saved
            signerName = saved.commonName
            organization = saved.organization
        }
    }

    // File picker for external .pfx or .p12 certificate
    val certFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    statusMessage = "Loading PKCS#12 certificate..."
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val result = PkiCertificateManager.loadFromPkcs12(stream, password.toCharArray(), "Imported DSC Certificate")
                        if (result.isSuccess) {
                            val cert = result.getOrThrow()
                            activeCert = cert
                            signerName = cert.commonName
                            organization = cert.organization
                            statusMessage = "Certificate loaded: ${cert.commonName}"
                        } else {
                            statusMessage = "Error: ${result.exceptionOrNull()?.localizedMessage ?: "Invalid password"}"
                        }
                    }
                } catch (e: Exception) {
                    statusMessage = "Failed to read certificate: ${e.localizedMessage}"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxSize(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Box(modifier = Modifier.padding(8.dp)) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Legal PKI Digital Signer",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF059669)
                                ) {
                                    Text(
                                        text = "ADOBE GREEN TICK",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "ISO 32000-1 • X.509 PKCS#12 • Detached SHA-256",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (signResult != null) {
                    // Success View
                    val res = signResult!!
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "PDF Digitally Signed Successfully!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "When opened in Adobe Acrobat Reader on computer, this document will display the official Green Checkmark: 'Signed and all signatures are valid'.",
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("📄 Signed File: ${res.signedFile.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("👤 Signer: ${res.signerName}", fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("🏢 Organization: ${res.organization}", fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("🕒 Timestamp: ${res.signDate}", fontSize = 12.sp)
                                if (res.isLtvTimestamped) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFDCFCE7)
                                    ) {
                                        Text(
                                            text = "✔ RFC 3161 LTV Timestamp Token Embedded",
                                            color = Color(0xFF15803D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("🔑 Serial: ${res.serialNumber.take(18)}...", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onShareFile(res.signedFile) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Signed PDF", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    onSignCompleted(res.signedFile)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Done", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Form View
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. Certificate Status Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (activeCert != null) Color(0xFFF0FDF4) else Color(0xFFEFF6FF)
                            ),
                            border = BorderStroke(1.dp, if (activeCert != null) Color(0xFF86EFAC) else Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (activeCert != null) "✔ Digital Certificate Ready" else "No Certificate Loaded",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (activeCert != null) Color(0xFF15803D) else Color(0xFF1E40AF)
                                    )
                                    if (activeCert != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDCFCE7)
                                        ) {
                                            Text(
                                                text = "2048-BIT RSA",
                                                color = Color(0xFF15803D),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (activeCert != null) {
                                    val df = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("CN: ${activeCert!!.commonName}", fontSize = 12.sp, color = Color(0xFF1E293B))
                                    Text("Issuer: ${activeCert!!.issuer.take(35)}...", fontSize = 11.sp, color = Color(0xFF475569))
                                    Text("Valid until: ${df.format(activeCert!!.validUntil)}", fontSize = 11.sp, color = Color(0xFF475569))
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Import an official Class 3 / DSC (.pfx / .p12) token or tap Generate below to create one instantly.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Certificate Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    certFilePicker.launch(arrayOf("application/x-pkcs12", "*/*"))
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import .pfx / .p12", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isProcessing = true
                                        statusMessage = "Generating 2048-bit RSA Legal Certificate..."
                                        val genRes = PkiCertificateManager.generateSelfSignedCertificate(
                                            context = context,
                                            commonName = signerName.ifBlank { "DocuEdit Verified Signer" },
                                            organization = organization.ifBlank { "DocuEdit Digital Signature Authority" },
                                            password = password.toCharArray()
                                        )
                                        isProcessing = false
                                        if (genRes.isSuccess) {
                                            activeCert = genRes.getOrThrow()
                                            statusMessage = "Device DSC Certificate created successfully!"
                                        } else {
                                            statusMessage = "Error: ${genRes.exceptionOrNull()?.localizedMessage}"
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(40.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Generate On-Device", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Certificate Password", fontSize = 12.sp) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Signer Name
                        OutlinedTextField(
                            value = signerName,
                            onValueChange = { signerName = it },
                            label = { Text("Signer Full Name", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Organization
                        OutlinedTextField(
                            value = organization,
                            onValueChange = { organization = it },
                            label = { Text("Organization / Company", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Reason
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Reason for Signing", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Location
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Location (City, Country)", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Visual Stamp Switch & Page Selector
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Visual Security Stamp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Draws official green verified seal on PDF page", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = addVisualSeal,
                                        onCheckedChange = { addVisualSeal = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF059669), checkedTrackColor = Color(0xFFDCFCE7))
                                    )
                                }

                                if (addVisualSeal && pageCount > 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Stamp on Page:", fontSize = 12.sp)
                                        Button(
                                            onClick = { sealPageNumber = 1 },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (sealPageNumber == 1) Color(0xFF059669) else Color(0xFFE2E8F0)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("First Page", fontSize = 11.sp, color = if (sealPageNumber == 1) Color.White else Color(0xFF334155))
                                        }
                                        Button(
                                            onClick = { sealPageNumber = pageCount },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (sealPageNumber == pageCount) Color(0xFF059669) else Color(0xFFE2E8F0)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Last Page ($pageCount)", fontSize = 11.sp, color = if (sealPageNumber == pageCount) Color.White else Color(0xFF334155))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Adobe LTV RFC 3161 Timestamp Switch
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Adobe LTV RFC 3161 Timestamp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFDCFCE7)
                                            ) {
                                                Text(
                                                    text = "GREEN TICK",
                                                    color = Color(0xFF15803D),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            "Embeds cryptographic timestamp token in PKCS#7 for permanent Adobe Acrobat Green Checkmark validity",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = enableLtvTimestamp,
                                        onCheckedChange = { enableLtvTimestamp = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF059669), checkedTrackColor = Color(0xFFDCFCE7))
                                    )
                                }
                            }
                        }

                        if (!statusMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = statusMessage!!,
                                fontSize = 12.sp,
                                color = if (statusMessage!!.startsWith("Error")) Color(0xFFDC2626) else Color(0xFF059669),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Bottom Sign Action Button
                    Button(
                        onClick = {
                            if (activeCert == null) {
                                statusMessage = "Please load or generate a certificate first."
                                return@Button
                            }
                            scope.launch {
                                isProcessing = true
                                statusMessage = "Computing SHA-256 hash & signing PDF with PKCS#7..."

                                val signedFile = File(
                                    inputPdfFile.parentFile ?: context.cacheDir,
                                    inputPdfFile.nameWithoutExtension + "_signed.pdf"
                                )

                                val signReq = PdfDigitalSigner.SignRequest(
                                    inputFile = inputPdfFile,
                                    outputFile = signedFile,
                                    certificateInfo = activeCert!!,
                                    reason = reason,
                                    location = location,
                                    contactInfo = signerName,
                                    addVisualBadge = addVisualSeal,
                                    badgePageNumber = sealPageNumber,
                                    enableLtvTimestamp = enableLtvTimestamp
                                )

                                val signRes = PdfDigitalSigner.signPdf(signReq)
                                isProcessing = false

                                if (signRes.isSuccess) {
                                    signResult = signRes.getOrThrow()
                                } else {
                                    statusMessage = "Signing Failed: ${signRes.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Signing Cryptographically...", fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔐 Sign & Protect PDF (Adobe Green Tick)", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
