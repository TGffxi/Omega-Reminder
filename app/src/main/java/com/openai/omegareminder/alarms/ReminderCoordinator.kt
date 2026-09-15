package com.openai.omegareminder.alarms

import com.openai.omegareminder.data.ActiveOccurrenceEntity
import com.openai.omegareminder.data.ReminderEntity
import com.openai.omegareminder.data.ReminderRepository
import com.openai.omegareminder.domain.*
import com.openai.omegareminder.notifications.ReminderNotifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

class ReminderCoordinator(
    private val repository: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val scheduleCalculator: ScheduleCalculator = ScheduleCalculator(),
    private val occurrencePolicy: OccurrencePolicy = OccurrencePolicy(),
) {
    private val mutex = Mutex()

    suspend fun save(draft: ReminderDraft): Long = mutex.withLock {
        val now = Instant.now()
        val old = if (draft.id == 0L) null else repository.getReminder(draft.id)
        val entity = ReminderEntity(
            id = draft.id,
            name = draft.name.trim(),
            enabled = draft.enabled,
            hour = draft.localTime.hour,
            minute = draft.localTime.minute,
            recurrenceType = draft.recurrenceType.name,
            oneTimeEpochDay = draft.oneTimeDate?.toEpochDay(),
            weekdayMask = draft.weekdayMask,
            presentationMode = draft.presentationMode.name,
            createdAtEpochMillis = old?.createdAtEpochMillis ?: now.toEpochMilli(),
            updatedAtEpochMillis = now.toEpochMilli(),
            lastCompletedAtEpochMillis = old?.lastCompletedAtEpochMillis,
        )
        val returned = repository.saveReminder(entity)
        val id = if (draft.id == 0L) returned else draft.id
        val saved = repository.getReminder(id) ?: return@withLock id

        scheduler.cancel(id, AlarmKind.REGULAR)
        if (!saved.enabled) {
            scheduler.cancelAll(id)
            repository.clearOccurrence(id)
            notifier.cancel(id)
        } else if (repository.getOccurrence(id) == null) {
            scheduleNextRegular(saved, now)
        }
        id
    }

    suspend fun setEnabled(reminderId: Long, enabled: Boolean) = mutex.withLock {
        val reminder = repository.getReminder(reminderId) ?: return@withLock
        val now = Instant.now()
        repository.saveReminder(reminder.copy(enabled = enabled, updatedAtEpochMillis = now.toEpochMilli()))
        scheduler.cancelAll(reminderId)
        if (!enabled) {
            repository.clearOccurrence(reminderId)
            notifier.cancel(reminderId)
        } else {
            repository.clearOccurrence(reminderId)
            val enabledReminder = reminder.copy(enabled = true, updatedAtEpochMillis = now.toEpochMilli())
            if (!scheduleNextRegular(enabledReminder, now)) {
                repository.saveReminder(enabledReminder.copy(enabled = false))
            }
        }
    }

    suspend fun delete(reminderId: Long) = mutex.withLock {
        scheduler.cancelAll(reminderId)
        notifier.cancel(reminderId)
        repository.deleteReminder(reminderId)
    }

    suspend fun handleRegularDue(reminderId: Long, scheduledForMillis: Long) = mutex.withLock {
        val reminder = repository.getReminder(reminderId) ?: return@withLock
        if (!reminder.enabled) return@withLock
        if (repository.getOccurrence(reminderId) != null) return@withLock
        scheduler.cancel(reminderId, AlarmKind.REGULAR)
        val now = Instant.now()
        val retryAt = occurrencePolicy.retryAt(now)
        val occurrence = ActiveOccurrenceEntity(
            reminderId = reminderId,
            scheduledForEpochMillis = if (scheduledForMillis > 0) scheduledForMillis else now.toEpochMilli(),
            status = OccurrenceStatus.OUTSTANDING.name,
            snoozedUntilEpochMillis = null,
            lastPresentedAtEpochMillis = now.toEpochMilli(),
            nextRetryAtEpochMillis = retryAt.toEpochMilli(),
        )
        repository.saveOccurrence(occurrence)
        notifier.show(reminder, occurrence, now)
        scheduler.scheduleRetry(reminderId, retryAt)
    }

    suspend fun handleRetry(reminderId: Long) = mutex.withLock {
        val reminder = repository.getReminder(reminderId) ?: return@withLock
        val occurrence = repository.getOccurrence(reminderId) ?: return@withLock
        if (!reminder.enabled || occurrence.occurrenceStatus() != OccurrenceStatus.OUTSTANDING) return@withLock
        val now = Instant.now()
        val retryAt = occurrencePolicy.retryAt(now)
        val updated = occurrence.copy(
            lastPresentedAtEpochMillis = now.toEpochMilli(),
            nextRetryAtEpochMillis = retryAt.toEpochMilli(),
        )
        repository.saveOccurrence(updated)
        notifier.show(reminder, updated, now)
        scheduler.scheduleRetry(reminderId, retryAt)
    }

    suspend fun snooze(reminderId: Long, minutes: Int) = mutex.withLock {
        val reminder = repository.getReminder(reminderId) ?: return@withLock
        if (reminder.mode() != PresentationMode.FULLSCREEN_SNOOZE) return@withLock
        val occurrence = repository.getOccurrence(reminderId) ?: return@withLock
        val target = occurrencePolicy.snoozeUntil(Instant.now(), minutes)
        repository.saveOccurrence(
            occurrence.copy(
                status = OccurrenceStatus.SNOOZED.name,
                snoozedUntilEpochMillis = target.toEpochMilli(),
                nextRetryAtEpochMillis = null,
            )
        )
        scheduler.cancel(reminderId, AlarmKind.RETRY)
        scheduler.scheduleSnooze(reminderId, target)
        notifier.cancel(reminderId)
    }

    suspend fun handleSnoozeDue(reminderId: Long) = mutex.withLock {
        val reminder = repository.getReminder(reminderId) ?: return@withLock
        val occurrence = repository.getOccurrence(reminderId) ?: return@withLock
        if (!reminder.enabled || occurrence.occurrenceStatus() != OccurrenceStatus.SNOOZED) return@withLock
        val now = Instant.now()
        val retryAt = occurrencePolicy.retryAt(now)
        val updated = occurrence.copy(
            status = OccurrenceStatus.OUTSTANDING.name,
            snoozedUntilEpochMillis = null,
            lastPresentedAtEpochMillis = now.toEpochMilli(),
            nextRetryAtEpochMillis = retryAt.toEpochMilli(),
        )
        repository.saveOccurrence(updated)
        notifier.show(reminder, updated, now)
        scheduler.scheduleRetry(reminderId, retryAt)
    }

    suspend fun complete(reminderId: Long) = mutex.withLock {
        val reminder = repository.getReminder(reminderId) ?: return@withLock
        val occurrence = repository.getOccurrence(reminderId) ?: return@withLock
        val now = Instant.now()
        scheduler.cancelAll(reminderId)
        notifier.cancel(reminderId)
        val disable = reminder.schedule().recurrenceType == RecurrenceType.ONE_TIME
        repository.completeReminder(reminder, now.toEpochMilli(), disable)
        if (!disable) scheduleNextRegular(reminder, now)
        @Suppress("UNUSED_VARIABLE") val consumed = occurrence
    }

    suspend fun rebuildAll() = mutex.withLock {
        val now = Instant.now()
        val occurrences = repository.getOccurrences().associateBy { it.reminderId }
        repository.getReminders().forEach { reminder ->
            scheduler.cancelAll(reminder.id)
            if (!reminder.enabled) {
                notifier.cancel(reminder.id)
                return@forEach
            }
            val occurrence = occurrences[reminder.id]
            if (occurrence == null) {
                val referenceMillis = maxOf(
                    reminder.createdAtEpochMillis,
                    reminder.lastCompletedAtEpochMillis ?: Long.MIN_VALUE,
                )
                val missed = scheduleCalculator.latestMissedOccurrence(
                    reminder.schedule(),
                    Instant.ofEpochMilli(referenceMillis),
                    now,
                    ZoneId.systemDefault(),
                )
                if (missed != null) {
                    val retryAt = occurrencePolicy.retryAt(now)
                    val recovered = ActiveOccurrenceEntity(
                        reminderId = reminder.id,
                        scheduledForEpochMillis = missed.toEpochMilli(),
                        status = OccurrenceStatus.OUTSTANDING.name,
                        snoozedUntilEpochMillis = null,
                        lastPresentedAtEpochMillis = now.toEpochMilli(),
                        nextRetryAtEpochMillis = retryAt.toEpochMilli(),
                    )
                    repository.saveOccurrence(recovered)
                    notifier.show(reminder, recovered, now)
                    scheduler.scheduleRetry(reminder.id, retryAt)
                } else if (!scheduleNextRegular(reminder, now) && reminder.schedule().recurrenceType == RecurrenceType.ONE_TIME) {
                    repository.saveReminder(reminder.copy(enabled = false, updatedAtEpochMillis = now.toEpochMilli()))
                }
            } else when (occurrence.occurrenceStatus()) {
                OccurrenceStatus.SNOOZED -> {
                    val target = occurrence.snoozedUntil()
                    if (target != null && target.isAfter(now)) {
                        scheduler.scheduleSnooze(reminder.id, target)
                    } else {
                        val retryAt = occurrencePolicy.retryAt(now)
                        val updated = occurrence.copy(
                            status = OccurrenceStatus.OUTSTANDING.name,
                            snoozedUntilEpochMillis = null,
                            lastPresentedAtEpochMillis = now.toEpochMilli(),
                            nextRetryAtEpochMillis = retryAt.toEpochMilli(),
                        )
                        repository.saveOccurrence(updated)
                        notifier.show(reminder, updated, now)
                        scheduler.scheduleRetry(reminder.id, retryAt)
                    }
                }
                OccurrenceStatus.OUTSTANDING -> {
                    val retryAt = occurrencePolicy.retryAt(now)
                    val updated = occurrence.copy(
                        lastPresentedAtEpochMillis = now.toEpochMilli(),
                        nextRetryAtEpochMillis = retryAt.toEpochMilli(),
                    )
                    repository.saveOccurrence(updated)
                    notifier.show(reminder, updated, now)
                    scheduler.scheduleRetry(reminder.id, retryAt)
                }
            }
        }
    }

    private fun scheduleNextRegular(reminder: ReminderEntity, now: Instant): Boolean {
        val next = scheduleCalculator.nextOccurrence(reminder.schedule(), now, ZoneId.systemDefault()) ?: return false
        scheduler.scheduleRegular(reminder.id, next)
        return true
    }
}
