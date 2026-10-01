package com.docu.editor.core.signature.pki

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date

/**
 * Enterprise PKI X.509 / PKCS#12 Digital Certificate Manager.
 * Supports loading official CA/DSC tokens (.pfx, .p12, Class 3) as well as
 * generating standard on-device self-signed 2048-bit RSA legal certificates.
 */
data class PkiCertificateInfo(
    val alias: String,
    val commonName: String,
    val organization: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: Date,
    val validUntil: Date,
    val privateKey: PrivateKey,
    val certificateChain: Array<X509Certificate>,
    val sourceFileName: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PkiCertificateInfo
        return serialNumber == other.serialNumber && alias == other.alias
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + serialNumber.hashCode()
        return result
    }
}

object PkiCertificateManager {

    private const val DEFAULT_KEYSTORE_FILENAME = "docuedit_user_pki.p12"
    private const val KEY_ALIAS = "DocuEditSigner"

    /**
     * Loads a PKCS#12 (.pfx or .p12) keystore from an input stream with password.
     */
    suspend fun loadFromPkcs12(
        inputStream: InputStream,
        password: CharArray,
        sourceName: String = "External Certificate"
    ): Result<PkiCertificateInfo> = withContext(Dispatchers.IO) {
        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            keyStore.load(inputStream, password)

            val aliases = keyStore.aliases()
            var selectedAlias: String? = null
            var privateKey: PrivateKey? = null

            while (aliases.hasMoreElements()) {
                val a = aliases.nextElement()
                if (keyStore.isKeyEntry(a)) {
                    val key = keyStore.getKey(a, password)
                    if (key is PrivateKey) {
                        selectedAlias = a
                        privateKey = key
                        break
                    }
                }
            }

            if (selectedAlias == null || privateKey == null) {
                return@withContext Result.failure(Exception("No private key found in PKCS#12 keystore. Please verify file and password."))
            }

            val rawChain = keyStore.getCertificateChain(selectedAlias)
            if (rawChain.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No certificate chain found for key alias '$selectedAlias'."))
            }

            val x509Chain = rawChain.mapNotNull { it as? X509Certificate }.toTypedArray()
            if (x509Chain.isEmpty()) {
                return@withContext Result.failure(Exception("Certificate is not an X.509 format certificate."))
            }

            val primaryCert = x509Chain[0]
            val subjectDn = primaryCert.subjectX500Principal.name
            val cn = extractDnField(subjectDn, "CN").ifBlank { selectedAlias }
            val org = extractDnField(subjectDn, "O").ifBlank { "Personal Digital Signature" }

            val info = PkiCertificateInfo(
                alias = selectedAlias,
                commonName = cn,
                organization = org,
                issuer = primaryCert.issuerX500Principal.name,
                serialNumber = primaryCert.serialNumber.toString(16).uppercase(),
                validFrom = primaryCert.notBefore,
                validUntil = primaryCert.notAfter,
                privateKey = privateKey,
                certificateChain = x509Chain,
                sourceFileName = sourceName
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates an official, standard 2048-bit RSA X.509 self-signed PKCS#12 certificate
     * right on the Android device, valid for 5 years.
     */
    suspend fun generateSelfSignedCertificate(
        context: Context,
        commonName: String,
        organization: String,
        countryCode: String = "IN",
        password: CharArray = "docuedit2026".toCharArray()
    ): Result<PkiCertificateInfo> = withContext(Dispatchers.IO) {
        try {
            // 1. Generate 2048-bit RSA KeyPair
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048, SecureRandom())
            val keyPair = keyGen.generateKeyPair()

            // 2. Configure Validity (5 Years)
            val now = Calendar.getInstance()
            val startDate = now.time
            now.add(Calendar.YEAR, 5)
            val endDate = now.time

            // 3. Subject and Issuer DN
            val safeCn = commonName.ifBlank { "DocuEdit Certified User" }
            val safeOrg = organization.ifBlank { "DocuEdit Digital Signature Authority" }
            val x500Name = X500Name("CN=$safeCn, O=$safeOrg, C=$countryCode")
            val serialNumber = BigInteger(64, SecureRandom())

            // 4. Build X.509 v3 Certificate with BouncyCastle
            val certBuilder = JcaX509v3CertificateBuilder(
                x500Name,
                serialNumber,
                startDate,
                endDate,
                x500Name,
                keyPair.public
            )

            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val certHolder = certBuilder.build(signer)
            val x509Cert = JcaX509CertificateConverter().getCertificate(certHolder)

            // 5. Store in PKCS#12 Keystore on Device
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            val chain = arrayOf<X509Certificate>(x509Cert)
            ks.setKeyEntry(KEY_ALIAS, keyPair.private, password, chain)

            val p12File = File(context.filesDir, DEFAULT_KEYSTORE_FILENAME)
            FileOutputStream(p12File).use { fos ->
                ks.store(fos, password)
            }

            val info = PkiCertificateInfo(
                alias = KEY_ALIAS,
                commonName = safeCn,
                organization = safeOrg,
                issuer = x500Name.toString(),
                serialNumber = serialNumber.toString(16).uppercase(),
                validFrom = startDate,
                validUntil = endDate,
                privateKey = keyPair.private,
                certificateChain = chain,
                sourceFileName = DEFAULT_KEYSTORE_FILENAME
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if a previously saved device PKCS#12 certificate exists and loads it.
     */
    suspend fun getSavedDeviceCertificate(
        context: Context,
        password: CharArray = "docuedit2026".toCharArray()
    ): PkiCertificateInfo? = withContext(Dispatchers.IO) {
        val p12File = File(context.filesDir, DEFAULT_KEYSTORE_FILENAME)
        if (!p12File.exists()) return@withContext null

        try {
            FileInputStream(p12File).use { fis ->
                loadFromPkcs12(fis, password, p12File.name).getOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDnField(dn: String, field: String): String {
        val parts = dn.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("$field=", ignoreCase = true)) {
                return trimmed.substring(field.length + 1).trim()
            }
        }
        return ""
    }
}
