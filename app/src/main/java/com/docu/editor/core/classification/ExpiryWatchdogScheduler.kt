package com.docu.editor.core.classification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.widget.Toast
import com.docu.editor.core.util.DocuNotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles scheduling of system local alarms and Google Calendar integration
 * for document expiry dates and payment deadlines.
 */
class DocuExpiryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val docTitle = intent.getStringExtra(EXTRA_DOC_TITLE) ?: "Document"
        val expiryDateString = intent.getStringExtra(EXTRA_EXPIRY_DATE) ?: "Soon"
        val daysLeft = intent.getIntExtra(EXTRA_DAYS_LEFT, 0)

        DocuNotificationHelper.showExpiryAlertNotification(
            context = context,
            docTitle = docTitle,
            expiryDateString = expiryDateString,
            daysLeft = daysLeft
        )
    }

    companion object {
        const val EXTRA_DOC_TITLE = "extra_doc_title"
        const val EXTRA_EXPIRY_DATE = "extra_expiry_date"
        const val EXTRA_DAYS_LEFT = "extra_days_left"
    }
}

object ExpiryWatchdogScheduler {

    /**
     * Universal 1-Tap Google Calendar Export.
     * Launches the native Android Calendar insert contract without requiring intrusive calendar write permissions.
     */
    fun addToGoogleCalendar(
        context: Context,
        docTitle: String,
        expiryEpochMs: Long,
        notes: String = ""
    ) {
        val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expiryEpochMs))
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "Due / Expiry: $docTitle")
            putExtra(
                CalendarContract.Events.DESCRIPTION,
                "Document expiry / renewal watchdog reminder created by DocuEdit.\n" +
                "Expiry Date: $dateFormatted\n" +
                if (notes.isNotBlank()) "Notes: $notes\n" else ""
            )
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, expiryEpochMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, expiryEpochMs + (60 * 60 * 1000L)) // 1 hr block
            putExtra(CalendarContract.Events.ALL_DAY, true)
            putExtra(CalendarContract.Events.HAS_ALARM, 1)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
            Toast.makeText(context, "Opening Google Calendar...", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "No calendar application installed on this device.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Schedules a local notification reminder [daysBefore] the expiry date.
     * E.g. 15 days or 3 days prior.
     */
    fun scheduleLocalReminder(
        context: Context,
        docId: String,
        docTitle: String,
        expiryEpochMs: Long,
        daysBefore: Int
    ): Boolean {
        val triggerTimeMs = expiryEpochMs - (daysBefore * 86_400_000L)
        val now = System.currentTimeMillis()

        // If the reminder trigger time is in the past, trigger immediate alert or skip
        val effectiveTriggerMs = if (triggerTimeMs <= now) {
            // If already within the window, schedule in 10 seconds for user verification
            now + 10_000L
        } else {
            triggerTimeMs
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expiryEpochMs))

        val intent = Intent(context, DocuExpiryAlarmReceiver::class.java).apply {
            putExtra(DocuExpiryAlarmReceiver.EXTRA_DOC_TITLE, docTitle)
            putExtra(DocuExpiryAlarmReceiver.EXTRA_EXPIRY_DATE, formattedDate)
            putExtra(DocuExpiryAlarmReceiver.EXTRA_DAYS_LEFT, daysBefore)
        }

        val requestCode = (docId.hashCode() xor daysBefore) and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effectiveTriggerMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, effectiveTriggerMs, pendingIntent)
            }
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    fun cancelLocalReminder(context: Context, docId: String, daysBefore: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DocuExpiryAlarmReceiver::class.java)
        val requestCode = (docId.hashCode() xor daysBefore) and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
