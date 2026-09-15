package com.openai.omegareminder.domain

import java.time.LocalDate
import java.time.LocalTime

enum class RecurrenceType { ONE_TIME, DAILY, WEEKDAYS }
enum class PresentationMode { FULLSCREEN_SNOOZE, FULLSCREEN_NO_SNOOZE }
enum class OccurrenceStatus { OUTSTANDING, SNOOZED }

data class ReminderSchedule(
    val localTime: LocalTime,
    val recurrenceType: RecurrenceType,
    val oneTimeDate: LocalDate? = null,
    val weekdayMask: Int = 0,
)
