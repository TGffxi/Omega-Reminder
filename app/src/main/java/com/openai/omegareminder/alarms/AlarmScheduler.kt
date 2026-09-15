package com.openai.omegareminder.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.Instant

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleRegular(reminderId: Long, at: Instant) = schedule(reminderId, AlarmKind.REGULAR, at)
    fun scheduleRetry(reminderId: Long, at: Instant) = schedule(reminderId, AlarmKind.RETRY, at)
    fun scheduleSnooze(reminderId: Long, at: Instant) = schedule(reminderId, AlarmKind.SNOOZE, at)

    fun cancel(reminderId: Long, kind: AlarmKind) {
        pendingIntent(reminderId, kind, 0L, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    fun cancelAll(reminderId: Long) {
        AlarmKind.entries.forEach { cancel(reminderId, it) }
    }

    private fun schedule(reminderId: Long, kind: AlarmKind, at: Instant) {
        val trigger = at.toEpochMilli()
        val pi = requireNotNull(pendingIntent(reminderId, kind, trigger, PendingIntent.FLAG_UPDATE_CURRENT))
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun pendingIntent(reminderId: Long, kind: AlarmKind, scheduledFor: Long, modeFlag: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            data = Uri.parse("omega-reminder://alarm/$reminderId/${kind.name}")
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_KIND, kind.name)
            putExtra(EXTRA_SCHEDULED_FOR, scheduledFor)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or modeFlag
        return PendingIntent.getBroadcast(context, kind.ordinal, intent, flags)
    }

    companion object {
        const val ACTION_ALARM = "com.openai.omegareminder.ALARM"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_KIND = "alarm_kind"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
    }
}
