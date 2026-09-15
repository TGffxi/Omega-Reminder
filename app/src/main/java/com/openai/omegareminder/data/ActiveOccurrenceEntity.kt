package com.openai.omegareminder.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.openai.omegareminder.domain.OccurrenceStatus
import java.time.Instant

@Entity(
    tableName = "active_occurrences",
    foreignKeys = [ForeignKey(
        entity = ReminderEntity::class,
        parentColumns = ["id"],
        childColumns = ["reminderId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("reminderId")],
)
data class ActiveOccurrenceEntity(
    @PrimaryKey val reminderId: Long,
    val scheduledForEpochMillis: Long,
    val status: String,
    val snoozedUntilEpochMillis: Long?,
    val lastPresentedAtEpochMillis: Long?,
    val nextRetryAtEpochMillis: Long?,
) {
    fun scheduledFor(): Instant = Instant.ofEpochMilli(scheduledForEpochMillis)
    fun occurrenceStatus(): OccurrenceStatus = OccurrenceStatus.valueOf(status)
    fun snoozedUntil(): Instant? = snoozedUntilEpochMillis?.let(Instant::ofEpochMilli)
}
