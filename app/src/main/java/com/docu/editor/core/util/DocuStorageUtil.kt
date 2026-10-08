package com.docu.editor.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Universal Scoped Storage Utility for DocuEdit.
 * Provides safe, 100% compliant file saving to Public Downloads & Gallery
 * across all Android versions (Android 10 to 15 / API 29+), eliminating EACCES permission errors.
 */
object DocuStorageUtil {

    /**
     * Saves an existing file from internal cache to public Downloads.
     * Returns the public Content Uri or null on failure.
     */
    fun saveFileToPublicDownloads(
        context: Context,
        srcFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        return try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocuEdit")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            contentResolver.openOutputStream(targetUri)?.use { outStream ->
                FileInputStream(srcFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(targetUri, contentValues, null, null)
            }

            targetUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a Bitmap directly to the public Pictures/Gallery (DocuEdit album).
     * Returns the public Content Uri or null on failure.
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 100
    ): Uri? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
            val fullName = if (format == Bitmap.CompressFormat.PNG) {
                if (displayName.endsWith(".png", true)) displayName else "$displayName.png"
            } else {
                if (displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true)) displayName else "$displayName.jpg"
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DocuEdit")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            contentResolver.openOutputStream(targetUri)?.use { outStream ->
                bitmap.compress(format, quality, outStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(targetUri, contentValues, null, null)
            }

            targetUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves raw byte array to public Downloads.
     */
    fun saveBytesToPublicDownloads(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String
    ): Uri? {
        return try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocuEdit")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            contentResolver.openOutputStream(targetUri)?.use { outStream ->
                outStream.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(targetUri, contentValues, null, null)
            }

            targetUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates an Intent to open a file with an external app (Word, Excel, Adobe Acrobat, etc.).
     */
    fun openFileWithExternalApp(
        context: Context,
        fileUri: Uri,
        mimeType: String,
        chooserTitle: String = "Open With"
    ) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Creates an Intent to share a file.
     */
    fun shareFile(
        context: Context,
        fileUri: Uri,
        mimeType: String,
        title: String = "Share Document"
    ) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Gets a shareable FileProvider Uri for a local cache file.
     */
    fun getShareableUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
