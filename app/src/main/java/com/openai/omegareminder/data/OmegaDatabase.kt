package com.openai.omegareminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReminderEntity::class, ActiveOccurrenceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OmegaDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun occurrenceDao(): OccurrenceDao

    companion object {
        fun create(context: Context): OmegaDatabase = Room.databaseBuilder(
            context.applicationContext,
            OmegaDatabase::class.java,
            "omega-reminder.db",
        ).build()
    }
}
