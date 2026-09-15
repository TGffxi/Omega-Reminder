package com.openai.omegareminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM active_occurrences")
    fun observeAll(): Flow<List<ActiveOccurrenceEntity>>

    @Query("SELECT * FROM active_occurrences")
    suspend fun getAll(): List<ActiveOccurrenceEntity>

    @Query("SELECT * FROM active_occurrences WHERE reminderId = :reminderId")
    suspend fun getByReminderId(reminderId: Long): ActiveOccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(occurrence: ActiveOccurrenceEntity)

    @Query("DELETE FROM active_occurrences WHERE reminderId = :reminderId")
    suspend fun deleteByReminderId(reminderId: Long)
}
