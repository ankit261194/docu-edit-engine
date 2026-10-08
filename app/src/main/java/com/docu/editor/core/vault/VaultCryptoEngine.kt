package com.docu.editor.core.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Enterprise AES-256-GCM Hardware-Backed Cryptographic Engine.
 * 
 * Security Guarantees:
 * 1. AndroidKeyStore hardware-backed master key (TrustZone / StrongBox secure enclave).
 * 2. Authenticated encryption with AES-GCM (Ciphertext confidentiality + integrity tag).
 * 3. Unique cryptographically random 12-byte IV per encryption operation.
 * 4. Zero plaintext leakage: files decrypted strictly in-memory.
 */
object VaultCryptoEngine {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "DocuEdit_Hardware_Master_Vault_Key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    data class EncryptedPayload(
        val iv: ByteArray,
        val ciphertext: ByteArray
    ) {
        fun toCombinedByteArray(): ByteArray {
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            return combined
        }

        companion object {
            fun fromCombinedByteArray(combined: ByteArray): EncryptedPayload {
                require(combined.size > IV_LENGTH_BYTES) { "Invalid encrypted payload size" }
                val iv = ByteArray(IV_LENGTH_BYTES)
                val ciphertext = ByteArray(combined.size - IV_LENGTH_BYTES)
                System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)
                System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.size)
                return EncryptedPayload(iv, ciphertext)
            }
        }
    }

    /**
     * Retrieves or generates the hardware-backed AES-256 key from AndroidKeyStore.
     */
    @Synchronized
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val entry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        // Generate new AES-256 hardware-backed key
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        // Attempt StrongBox hardware security if supported on modern devices
        try {
            builder.setIsStrongBoxBacked(true)
            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        } catch (_: Exception) {
            // Fallback to standard TrustZone AndroidKeyStore
            val fallbackBuilder = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
            keyGenerator.init(fallbackBuilder.build())
            return keyGenerator.generateKey()
        }
    }

    /**
     * Encrypts in-memory byte array using AES-256-GCM.
     */
    fun encryptBytes(plaintext: ByteArray): EncryptedPayload {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedPayload(iv, ciphertext)
    }

    /**
     * Decrypts in-memory payload using AES-256-GCM with integrity validation.
     */
    fun decryptBytes(payload: EncryptedPayload): ByteArray {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(payload.ciphertext)
    }

    /**
     * Encrypts a source file and writes combined [IV + Ciphertext] directly to destination file.
     */
    fun encryptFile(sourceFile: File, destinationEncryptedFile: File) {
        val plainBytes = FileInputStream(sourceFile).use { it.readBytes() }
        val payload = encryptBytes(plainBytes)
        FileOutputStream(destinationEncryptedFile).use { fos ->
            fos.write(payload.toCombinedByteArray())
            fos.flush()
        }
    }

    /**
     * Decrypts an encrypted file ([IV + Ciphertext]) in-memory.
     */
    fun decryptFile(encryptedFile: File): ByteArray {
        val combinedBytes = FileInputStream(encryptedFile).use { it.readBytes() }
        val payload = EncryptedPayload.fromCombinedByteArray(combinedBytes)
        return decryptBytes(payload)
    }

    /**
     * Cryptographic SHA-256 hash with salt for secure PIN validation.
     */
    fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /**
     * Generates a cryptographically secure random salt string.
     */
    fun generateSalt(): String {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }
}
