package com.openai.omegareminder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.openai.omegareminder.R

object NotificationChannels {
    const val REMINDERS = "visual_reminders"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}
