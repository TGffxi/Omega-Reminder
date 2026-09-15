package com.openai.omegareminder.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openai.omegareminder.OmegaReminderApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as OmegaReminderApp
        CoroutineScope(Dispatchers.IO).launch {
            try { app.coordinator.rebuildAll() } finally { pending.finish() }
        }
    }
}
