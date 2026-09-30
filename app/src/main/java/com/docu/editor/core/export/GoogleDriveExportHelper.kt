package com.docu.editor.core.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Enterprise Native Google Drive Direct Uploader.
 * Uses Android Intent & FileProvider to launch the official Google Drive "Save to Drive" UI
 * without requiring any OAuth 2.0 Client ID, Google Cloud Console setup, or 2-Step Verification.
 * Works seamlessly with whatever Google Account is signed in on the user's Android device.
 */
object GoogleDriveExportHelper {

    private const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"

    fun saveToGoogleDrive(
        context: Context,
        file: File,
        mimeType: String = "application/pdf",
        documentTitle: String = file.nameWithoutExtension
    ): Boolean {
        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // Direct intent targeted to official Google Drive app
            val driveIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, documentTitle)
                putExtra(Intent.EXTRA_TITLE, documentTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(GOOGLE_DRIVE_PACKAGE)
            }

            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(driveIntent, 0)

            if (activities.isNotEmpty()) {
                driveIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(driveIntent)
                Toast.makeText(context, "Opening Google Drive: Save to Drive...", Toast.LENGTH_SHORT).show()
                true
            } else {
                // If specific Drive package isn't directly resolved, launch system chooser
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
                Toast.makeText(context, "Select Google Drive from the app list", Toast.LENGTH_LONG).show()
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Google Drive error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
