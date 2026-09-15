package com.openai.omegareminder.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openai.omegareminder.OmegaReminderApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND)?.let { runCatching { AlarmKind.valueOf(it) }.getOrNull() }
        if (reminderId <= 0 || kind == null) return
        val pending = goAsync()
        val app = context.applicationContext as OmegaReminderApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kind) {
                    AlarmKind.REGULAR -> app.coordinator.handleRegularDue(reminderId, intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_FOR, 0L))
                    AlarmKind.RETRY -> app.coordinator.handleRetry(reminderId)
                    AlarmKind.SNOOZE -> app.coordinator.handleSnoozeDue(reminderId)
                }
            } finally { pending.finish() }
        }
    }
}
