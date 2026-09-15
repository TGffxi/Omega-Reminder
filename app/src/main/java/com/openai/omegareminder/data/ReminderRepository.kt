package com.openai.omegareminder.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val db: OmegaDatabase) {
    val reminders: Flow<List<ReminderEntity>> = db.reminderDao().observeAll()
    val occurrences: Flow<List<ActiveOccurrenceEntity>> = db.occurrenceDao().observeAll()

    suspend fun getReminders(): List<ReminderEntity> = db.reminderDao().getAll()
    suspend fun getOccurrences(): List<ActiveOccurrenceEntity> = db.occurrenceDao().getAll()
    suspend fun getReminder(id: Long): ReminderEntity? = db.reminderDao().getById(id)
    suspend fun getOccurrence(id: Long): ActiveOccurrenceEntity? = db.occurrenceDao().getByReminderId(id)
    suspend fun saveReminder(entity: ReminderEntity): Long {
        return if (entity.id == 0L) db.reminderDao().insert(entity)
        else {
            db.reminderDao().update(entity)
            entity.id
        }
    }
    suspend fun saveOccurrence(entity: ActiveOccurrenceEntity) = db.occurrenceDao().upsert(entity)
    suspend fun clearOccurrence(id: Long) = db.occurrenceDao().deleteByReminderId(id)

    suspend fun deleteReminder(id: Long) = db.withTransaction {
        db.occurrenceDao().deleteByReminderId(id)
        db.reminderDao().deleteById(id)
    }

    suspend fun completeReminder(reminder: ReminderEntity, completedAtMillis: Long, disable: Boolean) = db.withTransaction {
        db.occurrenceDao().deleteByReminderId(reminder.id)
        db.reminderDao().update(
            reminder.copy(
                enabled = if (disable) false else reminder.enabled,
                lastCompletedAtEpochMillis = completedAtMillis,
                updatedAtEpochMillis = completedAtMillis,
            )
        )
    }
}
