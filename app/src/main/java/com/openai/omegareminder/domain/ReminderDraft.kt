package com.openai.omegareminder.domain

import java.time.LocalDate
import java.time.LocalTime

data class ReminderDraft(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val localTime: LocalTime,
    val recurrenceType: RecurrenceType,
    val oneTimeDate: LocalDate? = null,
    val weekdayMask: Int = 0,
    val presentationMode: PresentationMode,
)
