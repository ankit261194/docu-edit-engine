package com.docu.editor.core.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Enterprise Document Vault Storage Repository.
 * Handles encrypted persistence, catalog synchronization, and realistic Decoy seeding.
 */
object VaultRepository {

    data class VaultDocument(
        val id: String,
        val title: String,
        val originalFileName: String,
        val mimeType: String,
        val fileSize: Long,
        val addedTimestamp: Long,
        val isDecoy: Boolean,
        val encryptedFileName: String,
        val thumbnailBase64: String? = null
    )

    private fun getVaultDirectory(context: Context, mode: VaultSecurityManager.VaultMode): File {
        val root = File(context.filesDir, "secure_vault")
        val dir = if (mode == VaultSecurityManager.VaultMode.REAL) File(root, "real") else File(root, "decoy")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCatalogFile(context: Context, mode: VaultSecurityManager.VaultMode): File {
        return File(getVaultDirectory(context, mode), "catalog.enc")
    }

    /**
     * Reads and decrypts document catalog.
     */
    @Synchronized
    fun getDocuments(context: Context, mode: VaultSecurityManager.VaultMode): List<VaultDocument> {
        val catalogFile = getCatalogFile(context, mode)
        if (!catalogFile.exists()) {
            if (mode == VaultSecurityManager.VaultMode.DECOY) {
                seedDecoyDocuments(context)
                return getDocuments(context, mode)
            }
            return emptyList()
        }

        try {
            val decryptedBytes = VaultCryptoEngine.decryptFile(catalogFile)
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VaultDocument>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VaultDocument(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        originalFileName = obj.getString("originalFileName"),
                        mimeType = obj.getString("mimeType"),
                        fileSize = obj.getLong("fileSize"),
                        addedTimestamp = obj.getLong("addedTimestamp"),
                        isDecoy = obj.getBoolean("isDecoy"),
                        encryptedFileName = obj.getString("encryptedFileName"),
                        thumbnailBase64 = obj.optString("thumbnailBase64").takeIf { it.isNotBlank() }
                    )
                )
            }
            return list.sortedByDescending { it.addedTimestamp }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    /**
     * Encrypts and writes catalog to disk.
     */
    @Synchronized
    private fun saveCatalog(context: Context, mode: VaultSecurityManager.VaultMode, list: List<VaultDocument>) {
        val jsonArray = JSONArray()
        for (doc in list) {
            val obj = JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("originalFileName", doc.originalFileName)
                put("mimeType", doc.mimeType)
                put("fileSize", doc.fileSize)
                put("addedTimestamp", doc.addedTimestamp)
                put("isDecoy", doc.isDecoy)
                put("encryptedFileName", doc.encryptedFileName)
                put("thumbnailBase64", doc.thumbnailBase64 ?: "")
            }
            jsonArray.put(obj)
        }

        val jsonBytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
        val encryptedPayload = VaultCryptoEngine.encryptBytes(jsonBytes)
        val catalogFile = getCatalogFile(context, mode)

        FileOutputStream(catalogFile).use { fos ->
            fos.write(encryptedPayload.toCombinedByteArray())
            fos.flush()
        }
    }

    /**
     * Imports an external document into the encrypted vault.
     */
    suspend fun importDocument(
        context: Context,
        sourceFile: File,
        mode: VaultSecurityManager.VaultMode,
        customTitle: String? = null,
        deleteOriginal: Boolean = false
    ): VaultDocument = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val encFileName = "$docId.enc"
        val vaultDir = getVaultDirectory(context, mode)
        val destEncFile = File(vaultDir, encFileName)

        // Hardware AES-256-GCM encryption
        VaultCryptoEngine.encryptFile(sourceFile, destEncFile)

        val ext = sourceFile.extension.lowercase()
        val mimeType = when (ext) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val title = customTitle?.takeIf { it.isNotBlank() } ?: sourceFile.nameWithoutExtension

        // Generate small Base64 thumbnail if image
        var thumbBase64: String? = null
        if (mimeType.startsWith("image/")) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                val bmp = android.graphics.BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
                if (bmp != null) {
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    thumbBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    bmp.recycle()
                }
            } catch (_: Exception) {}
        }

        val doc = VaultDocument(
            id = docId,
            title = title,
            originalFileName = sourceFile.name,
            mimeType = mimeType,
            fileSize = sourceFile.length(),
            addedTimestamp = System.currentTimeMillis(),
            isDecoy = (mode == VaultSecurityManager.VaultMode.DECOY),
            encryptedFileName = encFileName,
            thumbnailBase64 = thumbBase64
        )

        val existing = getDocuments(context, mode).toMutableList()
        existing.add(doc)
        saveCatalog(context, mode, existing)

        if (deleteOriginal) {
            try { sourceFile.delete() } catch (_: Exception) {}
        }

        doc
    }

    /**
     * Decrypts document in-memory (No plaintext left on disk).
     */
    suspend fun decryptDocumentBytes(
        context: Context,
        docId: String,
        mode: VaultSecurityManager.VaultMode
    ): ByteArray? = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirectory(context, mode)
        val encFile = File(vaultDir, "$docId.enc")
        if (!encFile.exists()) return@withContext null
        try {
            VaultCryptoEngine.decryptFile(encFile)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Exports and decrypts a document back to an unencrypted destination file.
     */
    suspend fun exportDocument(
        context: Context,
        docId: String,
        mode: VaultSecurityManager.VaultMode,
        targetFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val decryptedBytes = decryptDocumentBytes(context, docId, mode) ?: return@withContext false
        try {
            FileOutputStream(targetFile).use { fos ->
                fos.write(decryptedBytes)
                fos.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes document from encrypted storage and updates catalog.
     */
    @Synchronized
    fun deleteDocument(
        context: Context,
        docId: String,
        mode: VaultSecurityManager.VaultMode
    ): Boolean {
        val vaultDir = getVaultDirectory(context, mode)
        val encFile = File(vaultDir, "$docId.enc")
        try { encFile.delete() } catch (_: Exception) {}

        val list = getDocuments(context, mode).filterNot { it.id == docId }
        saveCatalog(context, mode, list)
        return true
    }

    /**
     * Seeds realistic mundane decoy documents to make the Decoy Vault 100% convincing.
     */
    private fun seedDecoyDocuments(context: Context) {
        val decoyDir = getVaultDirectory(context, VaultSecurityManager.VaultMode.DECOY)
        val dummyFiles = listOf(
            Triple("Reliance_Fresh_Grocery_Bill.pdf", "Reliance Fresh Supermarket Receipt", generateDummyReceiptPdf("Reliance Retail Ltd", "₹1,840.50", "Milk, Wheat Flour, Cooking Oil, Fresh Apples, Sugar")),
            Triple("Tata_Power_Electricity_July.pdf", "Tata Power Monthly Electricity Bill", generateDummyReceiptPdf("Tata Power Utilities", "₹2,150.00", "Domestic Power Consumption (210 Units @ ₹7.5/Unit) - Status: Paid")),
            Triple("Speed_Auto_Car_Spa_Invoice.jpg", "Speed Car Spa & Polish Invoice", generateDummyReceiptImage("Speed Auto Detailing", "₹450.00", "Foam Car Wash + Interior Vacuum Cleaning")),
            Triple("Swiggy_Family_Dinner_Order.pdf", "Swiggy Food Delivery Receipt", generateDummyReceiptPdf("Swiggy Delivery Partner", "₹820.00", "Punjabi Thali (x2), Butter Naan (x4), Paneer Tikka"))
        )

        val docsList = mutableListOf<VaultDocument>()
        for ((fileName, title, bytes) in dummyFiles) {
            val docId = UUID.randomUUID().toString()
            val encFileName = "$docId.enc"
            val encFile = File(decoyDir, encFileName)

            val payload = VaultCryptoEngine.encryptBytes(bytes)
            FileOutputStream(encFile).use { fos ->
                fos.write(payload.toCombinedByteArray())
                fos.flush()
            }

            docsList.add(
                VaultDocument(
                    id = docId,
                    title = title,
                    originalFileName = fileName,
                    mimeType = if (fileName.endsWith(".pdf")) "application/pdf" else "image/jpeg",
                    fileSize = bytes.size.toLong(),
                    addedTimestamp = System.currentTimeMillis() - (docsList.size * 86_400_000L * 4), // Staggered over past weeks
                    isDecoy = true,
                    encryptedFileName = encFileName
                )
            )
        }

        saveCatalog(context, VaultSecurityManager.VaultMode.DECOY, docsList)
    }

    private fun generateDummyReceiptPdf(merchant: String, amount: String, items: String): ByteArray {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { isAntiAlias = true }

        // Header Banner
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRect(0f, 0f, 595f, 120f, paint)

        // Merchant Name
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(merchant, 40f, 65f, paint)

        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(100, 116, 139)
        canvas.drawText("Tax Invoice & Payment Receipt • Paid via UPI", 40f, 95f, paint)

        // Amount Box
        paint.color = Color.rgb(236, 253, 245)
        canvas.drawRoundRect(40f, 150f, 555f, 230f, 16f, 16f, paint)

        paint.color = Color.rgb(5, 150, 105)
        paint.textSize = 14f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("TOTAL AMOUNT PAID", 60f, 185f, paint)

        paint.textSize = 26f
        canvas.drawText(amount, 60f, 218f, paint)

        // Items description
        paint.color = Color.rgb(30, 41, 59)
        paint.textSize = 14f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Items & Services Description:", 40f, 275f, paint)

        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(71, 85, 105)

        val words = items.split(", ")
        var yPos = 310f
        for (item in words) {
            canvas.drawText("• $item", 50f, yPos, paint)
            yPos += 26f
        }

        // Footer
        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 11f
        canvas.drawText("Generated electronically. Valid for personal accounting.", 40f, 780f, paint)

        pdfDoc.finishPage(page)
        val baos = ByteArrayOutputStream()
        pdfDoc.writeTo(baos)
        pdfDoc.close()
        return baos.toByteArray()
    }

    private fun generateDummyReceiptImage(merchant: String, amount: String, items: String): ByteArray {
        val bmp = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply { isAntiAlias = true }

        // Top bar
        paint.color = Color.rgb(30, 41, 59)
        canvas.drawRect(0f, 0f, 800f, 140f, paint)

        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(merchant, 40f, 85f, paint)

        // Amount
        paint.color = Color.rgb(16, 185, 129)
        paint.textSize = 40f
        canvas.drawText(amount, 40f, 220f, paint)

        paint.color = Color.rgb(51, 65, 85)
        paint.textSize = 24f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Service Details:", 40f, 280f, paint)
        canvas.drawText(items, 40f, 320f, paint)

        paint.color = Color.rgb(148, 163, 184)
        paint.textSize = 18f
        canvas.drawText("Thank you for your business!", 40f, 920f, paint)

        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        bmp.recycle()
        return baos.toByteArray()
    }
}
