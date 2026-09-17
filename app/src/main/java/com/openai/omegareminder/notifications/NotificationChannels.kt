package com.openai.omegareminder.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.openai.omegareminder.R

object NotificationChannels {
    const val REMINDERS = "visual_reminders"
    const val OVERLAY_SERVICE = "overlay_service"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val reminderChannel = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(reminderChannel)

        val overlayChannel = NotificationChannel(
            OVERLAY_SERVICE,
            "Aktive Vollbild-Erinnerung",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Wird nur angezeigt, solange eine Vollbild-Erinnerung offen ist."
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = null
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(overlayChannel)
    }
}
