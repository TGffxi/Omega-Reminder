package com.openai.omegareminder.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openai.omegareminder.R
import com.openai.omegareminder.data.ActiveOccurrenceEntity
import com.openai.omegareminder.data.ReminderEntity
import com.openai.omegareminder.ui.reminder.ReminderActivity
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class ReminderNotifier(private val context: Context) {
    init { NotificationChannels.ensure(context) }

    fun show(reminder: ReminderEntity, occurrence: ActiveOccurrenceEntity, now: Instant = Instant.now()) {
        val openIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("omega-reminder://show/${reminder.id}")
            putExtra(ReminderActivity.EXTRA_REMINDER_ID, reminder.id)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val overdueMinutes = max(0, Duration.between(occurrence.scheduledFor(), now).toMinutes())
        val text = if (overdueMinutes > 0) "Seit $overdueMinutes Min. offen" else "Jetzt fällig"
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.name)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(contentPi)
            .addAction(0, "Erledigt", actionPendingIntent(reminder.id, ACTION_DONE, 0))
            .addAction(0, "+10 Min", actionPendingIntent(reminder.id, ACTION_SNOOZE, 10))
            .addAction(0, "+30 Min", actionPendingIntent(reminder.id, ACTION_SNOOZE, 30))
            .addAction(0, "+60 Min", actionPendingIntent(reminder.id, ACTION_SNOOZE, 60))

        if (canUseFullScreenIntent()) builder.setFullScreenIntent(contentPi, true)
        try {
            NotificationManagerCompat.from(context).notify(notificationId(reminder.id), builder.build())
        } catch (_: SecurityException) {
            // Permission can be revoked at any time; Room remains authoritative and retries continue.
        }
    }

    fun cancel(reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    private fun actionPendingIntent(reminderId: Long, actionName: String, snoozeMinutes: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = actionName
            data = Uri.parse("omega-reminder://action/$reminderId/$actionName/$snoozeMinutes")
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            snoozeMinutes,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(reminderId: Long): Int = (reminderId xor (reminderId ushr 32)).toInt() and 0x7fffffff

    companion object {
        const val ACTION_DONE = "com.openai.omegareminder.DONE"
        const val ACTION_SNOOZE = "com.openai.omegareminder.SNOOZE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }
}
