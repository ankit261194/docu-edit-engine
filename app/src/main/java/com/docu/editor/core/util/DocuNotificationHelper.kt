package com.docu.editor.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.File

/**
 * Enterprise Notification Manager for Background Batch Document Processing.
 * Displays live progress bars and completion status when processing large batches (100+ files)
 * so operations continue smoothly even when the device is locked or minimized.
 */
object DocuNotificationHelper {

    private const val CHANNEL_ID = "docu_batch_processing"
    private const val CHANNEL_NAME = "Batch Document Operations"
    private const val CHANNEL_DESC = "Shows progress and completion for batch image resize and format conversions"
    private const val NOTIFICATION_ID = 4099

    private const val CHANNEL_ID_EXPIRY = "docu_expiry_watchdog"
    private const val CHANNEL_NAME_EXPIRY = "Document Expiry & Due Date Watchdog"
    private const val CHANNEL_DESC_EXPIRY = "Alerts for expiring documents, Driving Licenses, Passports, and Utility Bills"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
            }
            val expiryChannel = NotificationChannel(CHANNEL_ID_EXPIRY, CHANNEL_NAME_EXPIRY, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESC_EXPIRY
                setShowBadge(true)
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
            notificationManager?.createNotificationChannel(expiryChannel)
        }
    }

    fun showProgressNotification(
        context: Context,
        currentIndex: Int,
        totalCount: Int,
        fileName: String
    ) {
        initNotificationChannel(context)
        val progressPercent = if (totalCount > 0) ((currentIndex.toFloat() / totalCount.toFloat()) * 100).toInt() else 0

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Batch Resizing Documents ($progressPercent%)")
            .setContentText("[$currentIndex/$totalCount] Processing $fileName")
            .setProgress(totalCount, currentIndex, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notification permission might not be granted on Android 13+
        }
    }

    fun showCompleteNotification(
        context: Context,
        successCount: Int,
        failureCount: Int,
        targetKb: Int
    ) {
        initNotificationChannel(context)

        val summary = if (failureCount == 0) {
            "All $successCount documents resized to ≤ $targetKb KB successfully."
        } else {
            "$successCount succeeded, $failureCount failed (target: $targetKb KB)."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Batch Processing Complete 🎉")
            .setContentText(summary)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission check
        }
    }

    fun cancelNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    fun showUpdateReadyNotification(context: Context, apkFile: File, newVersion: String) {
        initNotificationChannel(context)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            5001,
            installIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("DocuEdit Update Ready 🎉")
            .setContentText("Version $newVersion downloaded. Tap to Install now.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        try {
            NotificationManagerCompat.from(context).notify(4098, builder.build())
        } catch (_: SecurityException) {}
    }

    fun showExpiryAlertNotification(
        context: Context,
        docTitle: String,
        expiryDateString: String,
        daysLeft: Int
    ) {
        initNotificationChannel(context)
        val alertTitle = if (daysLeft < 0) {
            "⚠️ Expired: $docTitle"
        } else if (daysLeft == 0) {
            "🚨 Due Today: $docTitle"
        } else {
            "⏰ Renewal Reminder: $docTitle"
        }

        val alertText = if (daysLeft < 0) {
            "Document expired on $expiryDateString (${kotlin.math.abs(daysLeft)} days ago). Tap to renew."
        } else if (daysLeft == 0) {
            "Due date is today ($expiryDateString)! Please review or take action."
        } else {
            "Expires in $daysLeft day(s) on $expiryDateString. Don't forget to renew."
        }

        val openAppIntent = android.content.Intent(context, com.docu.editor.ui.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            docTitle.hashCode(),
            openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_EXPIRY)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(alertTitle)
            .setContentText(alertText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alertText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        try {
            val notificationId = 7000 + (docTitle.hashCode() and 0x7FFF)
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }
}
