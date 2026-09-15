package com.openai.omegareminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.openai.omegareminder.domain.PresentationMode
import com.openai.omegareminder.domain.RecurrenceType
import com.openai.omegareminder.domain.ReminderSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val recurrenceType: String,
    val oneTimeEpochDay: Long?,
    val weekdayMask: Int,
    val presentationMode: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastCompletedAtEpochMillis: Long?,
) {
    fun schedule(): ReminderSchedule = ReminderSchedule(
        localTime = LocalTime.of(hour, minute),
        recurrenceType = RecurrenceType.valueOf(recurrenceType),
        oneTimeDate = oneTimeEpochDay?.let(LocalDate::ofEpochDay),
        weekdayMask = weekdayMask,
    )

    fun mode(): PresentationMode = PresentationMode.valueOf(presentationMode)
    fun lastCompletedAt(): Instant? = lastCompletedAtEpochMillis?.let(Instant::ofEpochMilli)
}
