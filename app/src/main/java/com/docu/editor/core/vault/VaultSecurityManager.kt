package com.docu.editor.core.vault

import android.content.Context
import android.content.SharedPreferences

/**
 * Enterprise Security & Duress Decoy Manager for DocuEdit Private Vault.
 * 
 * Features:
 * 1. Real Master PIN verification.
 * 2. Decoy / Duress PIN verification: Silently switches vault state to Decoy mode
 *    if coerced to unlock under duress.
 * 3. Biometric unlock preference toggle.
 * 4. Salted SHA-256 PIN storage.
 */
class VaultSecurityManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class VaultMode {
        REAL,   // Authentic confidential documents
        DECOY   // Plausible deniability: realistic grocery & utility dummy bills
    }

    sealed class VerificationResult {
        data class Success(val mode: VaultMode) : VerificationResult()
        data class Failed(val remainingAttempts: Int) : VerificationResult()
        object LockedOut : VerificationResult()
    }

    companion object {
        private const val PREFS_NAME = "docuedit_vault_sec_prefs"
        private const val KEY_IS_SETUP = "vault_is_setup"
        private const val KEY_REAL_PIN_HASH = "vault_real_pin_hash"
        private const val KEY_REAL_PIN_SALT = "vault_real_pin_salt"
        private const val KEY_DECOY_PIN_HASH = "vault_decoy_pin_hash"
        private const val KEY_DECOY_PIN_SALT = "vault_decoy_pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
        private const val KEY_FAILED_ATTEMPTS = "vault_failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "vault_lockout_timestamp"

        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 30_000L // 30s lockout after 5 consecutive failures
    }

    /**
     * Checks if the user has already configured the vault master PIN.
     */
    fun isVaultSetup(): Boolean {
        return prefs.getBoolean(KEY_IS_SETUP, false) && prefs.getString(KEY_REAL_PIN_HASH, null) != null
    }

    /**
     * Checks if Decoy PIN is configured.
     */
    fun isDecoyConfigured(): Boolean {
        return prefs.getString(KEY_DECOY_PIN_HASH, null) != null
    }

    /**
     * Checks if Biometric authentication is enabled for Real Vault.
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Initial setup for Real PIN and optional Decoy PIN.
     */
    fun setupVault(realPin: String, decoyPin: String? = null, enableBiometric: Boolean = true): Boolean {
        if (realPin.length < 4) return false
        if (!decoyPin.isNullOrBlank() && decoyPin == realPin) return false // Real and Decoy PINs must be distinct

        val realSalt = VaultCryptoEngine.generateSalt()
        val realHash = VaultCryptoEngine.hashPin(realPin, realSalt)

        val editor = prefs.edit()
            .putBoolean(KEY_IS_SETUP, true)
            .putString(KEY_REAL_PIN_HASH, realHash)
            .putString(KEY_REAL_PIN_SALT, realSalt)
            .putBoolean(KEY_BIOMETRIC_ENABLED, enableBiometric)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)

        if (!decoyPin.isNullOrBlank() && decoyPin.length >= 4) {
            val decoySalt = VaultCryptoEngine.generateSalt()
            val decoyHash = VaultCryptoEngine.hashPin(decoyPin, decoySalt)
            editor.putString(KEY_DECOY_PIN_HASH, decoyHash)
            editor.putString(KEY_DECOY_PIN_SALT, decoySalt)
        } else {
            editor.remove(KEY_DECOY_PIN_HASH)
            editor.remove(KEY_DECOY_PIN_SALT)
        }

        editor.apply()
        return true
    }

    /**
     * Updates or removes Decoy PIN.
     */
    fun updateDecoyPin(newDecoyPin: String?): Boolean {
        if (newDecoyPin.isNullOrBlank()) {
            prefs.edit().remove(KEY_DECOY_PIN_HASH).remove(KEY_DECOY_PIN_SALT).apply()
            return true
        }

        val realSalt = prefs.getString(KEY_REAL_PIN_SALT, "") ?: ""
        val realHash = prefs.getString(KEY_REAL_PIN_HASH, "") ?: ""
        if (realSalt.isNotEmpty() && VaultCryptoEngine.hashPin(newDecoyPin, realSalt) == realHash) {
            return false // Decoy PIN cannot be identical to Real PIN
        }

        val decoySalt = VaultCryptoEngine.generateSalt()
        val decoyHash = VaultCryptoEngine.hashPin(newDecoyPin, decoySalt)
        prefs.edit()
            .putString(KEY_DECOY_PIN_HASH, decoyHash)
            .putString(KEY_DECOY_PIN_SALT, decoySalt)
            .apply()
        return true
    }

    /**
     * Updates Real Master PIN.
     */
    fun changeRealPin(currentPin: String, newPin: String): Boolean {
        if (newPin.length < 4) return false
        val realSalt = prefs.getString(KEY_REAL_PIN_SALT, null) ?: return false
        val currentHash = prefs.getString(KEY_REAL_PIN_HASH, null) ?: return false

        if (VaultCryptoEngine.hashPin(currentPin, realSalt) != currentHash) {
            return false
        }

        // Check against decoy PIN
        val decoySalt = prefs.getString(KEY_DECOY_PIN_SALT, null)
        val decoyHash = prefs.getString(KEY_DECOY_PIN_HASH, null)
        if (decoySalt != null && decoyHash != null) {
            if (VaultCryptoEngine.hashPin(newPin, decoySalt) == decoyHash) {
                return false
            }
        }

        val newSalt = VaultCryptoEngine.generateSalt()
        val newHash = VaultCryptoEngine.hashPin(newPin, newSalt)
        prefs.edit()
            .putString(KEY_REAL_PIN_HASH, newHash)
            .putString(KEY_REAL_PIN_SALT, newSalt)
            .apply()
        return true
    }

    /**
     * Verifies entered PIN.
     * Evaluates against both Real PIN and Decoy PIN.
     */
    fun verifyPin(enteredPin: String): VerificationResult {
        val now = System.currentTimeMillis()
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        if (now < lockoutTime) {
            return VerificationResult.LockedOut
        }

        val realSalt = prefs.getString(KEY_REAL_PIN_SALT, null)
        val realHash = prefs.getString(KEY_REAL_PIN_HASH, null)
        val decoySalt = prefs.getString(KEY_DECOY_PIN_SALT, null)
        val decoyHash = prefs.getString(KEY_DECOY_PIN_HASH, null)

        // 1. Check Real PIN
        if (realSalt != null && realHash != null) {
            val candidateHash = VaultCryptoEngine.hashPin(enteredPin, realSalt)
            if (candidateHash == realHash) {
                resetFailedAttempts()
                return VerificationResult.Success(VaultMode.REAL)
            }
        }

        // 2. Check Decoy PIN
        if (decoySalt != null && decoyHash != null) {
            val candidateDecoyHash = VaultCryptoEngine.hashPin(enteredPin, decoySalt)
            if (candidateDecoyHash == decoyHash) {
                resetFailedAttempts()
                // Silently open in Decoy Mode!
                return VerificationResult.Success(VaultMode.DECOY)
            }
        }

        // 3. Failed attempt handling
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (failed >= MAX_FAILED_ATTEMPTS) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, failed)
                .putLong(KEY_LOCKOUT_TIMESTAMP, now + LOCKOUT_DURATION_MS)
                .apply()
            return VerificationResult.LockedOut
        } else {
            prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failed).apply()
            val remaining = MAX_FAILED_ATTEMPTS - failed
            return VerificationResult.Failed(remaining)
        }
    }

    private fun resetFailedAttempts() {
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCKOUT_TIMESTAMP, 0L).apply()
    }
}
