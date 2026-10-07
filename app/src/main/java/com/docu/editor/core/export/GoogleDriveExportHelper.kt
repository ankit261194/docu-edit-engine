package com.docu.editor.core.export

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Enterprise Native Google Drive Direct Uploader & Cloud Exporter.
 * Uses Android Intent & FileProvider to launch the official Google Drive "Save to Drive" UI
 * without requiring OAuth 2.0 Client ID or Google Cloud Console setup.
 * Supports:
 * - Single file dispatch (PDF, XLSX, DOCX, PNG, JPG).
 * - Multi-file batch dispatch (ACTION_SEND_MULTIPLE).
 * - Auto-detection of Google Drive app installation with graceful system fallback.
 * - Auto-MIME type mapping from file extensions.
 */
object GoogleDriveExportHelper {

    const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"

    data class DriveExportResult(
        val success: Boolean,
        val isDirectAppLaunched: Boolean,
        val message: String
    )

    fun isGoogleDriveInstalled(context: Context): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(GOOGLE_DRIVE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(GOOGLE_DRIVE_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resolveMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun saveToGoogleDrive(
        context: Context,
        file: File,
        mimeType: String = resolveMimeType(file),
        documentTitle: String = file.nameWithoutExtension
    ): DriveExportResult {
        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val driveInstalled = isGoogleDriveInstalled(context)

            if (driveInstalled) {
                val driveIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, documentTitle)
                    putExtra(Intent.EXTRA_TITLE, documentTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(GOOGLE_DRIVE_PACKAGE)
                }

                val activities = context.packageManager.queryIntentActivities(driveIntent, 0)
                if (activities.isNotEmpty()) {
                    context.startActivity(driveIntent)
                    Toast.makeText(context, "Opening Google Drive: Save to Drive...", Toast.LENGTH_SHORT).show()
                    return DriveExportResult(true, true, "Google Drive opened")
                }
            }

            // Fallback: System chooser targeting cloud drives / file managers
            val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, documentTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(chooserIntent, "Save to Google Drive / Cloud").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Toast.makeText(context, "Select Google Drive from the app list", Toast.LENGTH_SHORT).show()
            DriveExportResult(true, false, "Chooser opened")
        } catch (e: Exception) {
            val err = "Google Drive error: ${e.localizedMessage}"
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            DriveExportResult(false, false, err)
        }
    }

    fun saveMultipleToGoogleDrive(
        context: Context,
        files: List<File>,
        mimeType: String = "application/pdf",
        batchTitle: String = "Batch_Scans_${System.currentTimeMillis()}"
    ): DriveExportResult {
        if (files.isEmpty()) return DriveExportResult(false, false, "No files to export")

        return try {
            val uris = ArrayList<Uri>()
            for (file in files) {
                uris.add(
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                )
            }

            val driveInstalled = isGoogleDriveInstalled(context)

            if (driveInstalled) {
                val driveIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_SUBJECT, batchTitle)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(GOOGLE_DRIVE_PACKAGE)
                }

                val activities = context.packageManager.queryIntentActivities(driveIntent, 0)
                if (activities.isNotEmpty()) {
                    context.startActivity(driveIntent)
                    Toast.makeText(context, "Opening Google Drive for ${files.size} documents...", Toast.LENGTH_SHORT).show()
                    return DriveExportResult(true, true, "Google Drive batch opened")
                }
            }

            val chooserIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, batchTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(chooserIntent, "Save Batch to Google Drive / Cloud").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Toast.makeText(context, "Select Google Drive from the app list", Toast.LENGTH_SHORT).show()
            DriveExportResult(true, false, "Chooser batch opened")
        } catch (e: Exception) {
            val err = "Google Drive batch error: ${e.localizedMessage}"
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            DriveExportResult(false, false, err)
        }
    }
}
