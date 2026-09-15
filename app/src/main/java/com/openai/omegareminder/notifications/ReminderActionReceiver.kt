package com.openai.omegareminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openai.omegareminder.OmegaReminderApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderNotifier.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        val pending = goAsync()
        val app = context.applicationContext as OmegaReminderApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderNotifier.ACTION_DONE -> app.coordinator.complete(reminderId)
                    ReminderNotifier.ACTION_SNOOZE -> app.coordinator.snooze(
                        reminderId,
                        intent.getIntExtra(ReminderNotifier.EXTRA_SNOOZE_MINUTES, 0),
                    )
                }
            } finally { pending.finish() }
        }
    }
}
