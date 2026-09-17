package com.openai.omegareminder

import android.app.Application
import com.openai.omegareminder.alarms.AlarmScheduler
import com.openai.omegareminder.alarms.ReminderCoordinator
import com.openai.omegareminder.data.OmegaDatabase
import com.openai.omegareminder.data.ReminderRepository
import com.openai.omegareminder.notifications.NotificationChannels
import com.openai.omegareminder.notifications.ReminderNotifier
import com.openai.omegareminder.overlay.OverlayController

class OmegaReminderApp : Application() {
    lateinit var database: OmegaDatabase
        private set
    lateinit var repository: ReminderRepository
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set
    lateinit var notifier: ReminderNotifier
        private set
    lateinit var overlayController: OverlayController
        private set
    lateinit var coordinator: ReminderCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        database = OmegaDatabase.create(this)
        repository = ReminderRepository(database)
        alarmScheduler = AlarmScheduler(this)
        notifier = ReminderNotifier(this)
        overlayController = OverlayController(this)
        coordinator = ReminderCoordinator(
            repository,
            alarmScheduler,
            notifier,
            overlayController,
        )
    }
}
