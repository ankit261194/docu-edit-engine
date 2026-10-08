package com.docu.editor.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.docu.editor.core.vault.BiometricAuthHelper
import com.docu.editor.core.vault.VaultSecurityManager
import kotlinx.coroutines.launch

/**
 * Enterprise Biometric & Duress Decoy PIN Unlock Dialog.
 * Supports:
 * - Master PIN verification (Real Vault).
 * - Decoy / Duress PIN verification (Silently unlocks Decoy Vault).
 * - Hardware Biometric prompt trigger (Fingerprint / Face / Device Lock).
 * - Guided initial Setup Wizard for Master & Decoy PINs.
 */
@Composable
fun PrivateVaultUnlockDialog(
    onDismissRequest: () -> Unit,
    onUnlocked: (VaultSecurityManager.VaultMode) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val securityManager = remember { VaultSecurityManager(context) }
    val isVaultConfigured = remember { securityManager.isVaultSetup() }

    var currentPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var setupStep by remember { mutableStateOf(if (isVaultConfigured) 0 else 1) }
    // Setup wizard variables:
    var initialPin by remember { mutableStateOf("") }
    var confirmedPin by remember { mutableStateOf("") }
    var decoyPinInput by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        coroutineScope.launch {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    -25f at 50
                    25f at 100
                    -18f at 150
                    18f at 200
                    -8f at 250
                    8f at 300
                    0f at 350
                }
            )
        }
    }

    // Auto-prompt Biometrics on open if vault is already configured
    LaunchedEffect(Unit) {
        if (isVaultConfigured && securityManager.isBiometricEnabled() && activity != null) {
            BiometricAuthHelper.promptBiometricUnlock(
                activity = activity,
                title = "Unlock Private Vault",
                subtitle = "Use fingerprint or face unlock",
                onSuccess = { onUnlocked(VaultSecurityManager.VaultMode.REAL) },
                onError = { errorMessage = it },
                onFallbackToPin = { /* User stays on PIN pad */ }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            color = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Private Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                // Header & Instructions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val promptTitle = when {
                        !isVaultConfigured && setupStep == 1 -> "Create Master PIN"
                        !isVaultConfigured && setupStep == 2 -> "Confirm Master PIN"
                        !isVaultConfigured && setupStep == 3 -> "Set Decoy / Duress PIN"
                        else -> "Enter Vault PIN"
                    }

                    val promptSubtitle = when {
                        !isVaultConfigured && setupStep == 1 -> "Choose a 4-digit PIN to secure your documents"
                        !isVaultConfigured && setupStep == 2 -> "Re-enter the 4-digit master PIN"
                        !isVaultConfigured && setupStep == 3 -> "Optional: Fake PIN that opens decoy dummy bills under duress"
                        else -> "Enter master PIN or Decoy PIN to unlock"
                    }

                    Text(
                        text = promptTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = promptSubtitle,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN Dots Display
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val maxDots = 4
                        for (i in 0 until maxDots) {
                            val isFilled = i < currentPinInput.length
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) Color(0xFF10B981) else Color(0xFF334155)
                                    )
                            )
                        }
                    }

                    // Error text
                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }

                // Setup Skip Option for Decoy PIN (Step 3)
                if (!isVaultConfigured && setupStep == 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = {
                                securityManager.setupVault(initialPin, decoyPin = null)
                                onUnlocked(VaultSecurityManager.VaultMode.REAL)
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                        ) {
                            Text("Skip Decoy PIN (Finish Setup)", fontSize = 13.sp)
                        }
                    }
                }

                // Numeric Keypad
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "DEL")
                    )

                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (key in row) {
                                KeypadButton(
                                    label = key,
                                    onClick = {
                                        errorMessage = null
                                        when (key) {
                                            "DEL" -> {
                                                if (currentPinInput.isNotEmpty()) {
                                                    currentPinInput = currentPinInput.dropLast(1)
                                                }
                                            }
                                            "BIO" -> {
                                                if (isVaultConfigured && activity != null) {
                                                    BiometricAuthHelper.promptBiometricUnlock(
                                                        activity = activity,
                                                        title = "Unlock Private Vault",
                                                        onSuccess = { onUnlocked(VaultSecurityManager.VaultMode.REAL) },
                                                        onError = { errorMessage = it },
                                                        onFallbackToPin = {}
                                                    )
                                                }
                                            }
                                            else -> {
                                                if (currentPinInput.length < 4) {
                                                    currentPinInput += key
                                                    if (currentPinInput.length == 4) {
                                                        val entered = currentPinInput
                                                        if (isVaultConfigured) {
                                                            // Standard Unlock Verification
                                                            when (val res = securityManager.verifyPin(entered)) {
                                                                is VaultSecurityManager.VerificationResult.Success -> {
                                                                    onUnlocked(res.mode)
                                                                }
                                                                is VaultSecurityManager.VerificationResult.Failed -> {
                                                                    triggerShake()
                                                                    errorMessage = "Incorrect PIN (${res.remainingAttempts} attempts remaining)"
                                                                    currentPinInput = ""
                                                                }
                                                                is VaultSecurityManager.VerificationResult.LockedOut -> {
                                                                    triggerShake()
                                                                    errorMessage = "Vault locked for 30s due to repeated failures"
                                                                    currentPinInput = ""
                                                                }
                                                            }
                                                        } else {
                                                            // Setup Wizard Flow
                                                            when (setupStep) {
                                                                1 -> {
                                                                    initialPin = entered
                                                                    currentPinInput = ""
                                                                    setupStep = 2
                                                                }
                                                                2 -> {
                                                                    if (entered == initialPin) {
                                                                        confirmedPin = entered
                                                                        currentPinInput = ""
                                                                        setupStep = 3
                                                                    } else {
                                                                        triggerShake()
                                                                        errorMessage = "PINs do not match. Try again."
                                                                        currentPinInput = ""
                                                                        setupStep = 1
                                                                    }
                                                                }
                                                                3 -> {
                                                                    if (entered == initialPin) {
                                                                        triggerShake()
                                                                        errorMessage = "Decoy PIN must be different from Master PIN!"
                                                                        currentPinInput = ""
                                                                    } else {
                                                                        decoyPinInput = entered
                                                                        securityManager.setupVault(initialPin, decoyPin = entered)
                                                                        onUnlocked(VaultSecurityManager.VaultMode.REAL)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Security Note
                Text(
                    text = "🛡️ 256-Bit Hardware Keystore Protected",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (label.isNotEmpty() && label != "BIO") Color(0xFF1E293B) else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (label) {
            "DEL" -> {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(24.dp)
                )
            }
            "BIO" -> {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = "Biometric Prompt",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(32.dp)
                )
            }
            else -> {
                Text(
                    text = label,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
