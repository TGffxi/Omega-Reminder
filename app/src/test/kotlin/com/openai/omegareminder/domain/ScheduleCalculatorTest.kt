package com.openai.omegareminder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.*

class ScheduleCalculatorTest {
    private val calculator = ScheduleCalculator()
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test fun dailyUsesSameDayWhenStillFuture() {
        val now = ZonedDateTime.of(2026, 9, 15, 7, 0, 0, 0, berlin).toInstant()
        val schedule = ReminderSchedule(LocalTime.of(8, 0), RecurrenceType.DAILY)
        assertEquals(ZonedDateTime.of(2026, 9, 15, 8, 0, 0, 0, berlin).toInstant(), calculator.nextOccurrence(schedule, now, berlin))
    }

    @Test fun dailyMovesToTomorrowWhenTimeAlreadyPassed() {
        val now = ZonedDateTime.of(2026, 9, 15, 9, 0, 0, 0, berlin).toInstant()
        val schedule = ReminderSchedule(LocalTime.of(8, 0), RecurrenceType.DAILY)
        assertEquals(ZonedDateTime.of(2026, 9, 16, 8, 0, 0, 0, berlin).toInstant(), calculator.nextOccurrence(schedule, now, berlin))
    }

    @Test fun weekdaysSkipsUnselectedDays() {
        val now = ZonedDateTime.of(2026, 9, 18, 9, 0, 0, 0, berlin).toInstant() // Friday
        val mondayMask = 1 shl (DayOfWeek.MONDAY.value - 1)
        val schedule = ReminderSchedule(LocalTime.of(8, 0), RecurrenceType.WEEKDAYS, weekdayMask = mondayMask)
        assertEquals(ZonedDateTime.of(2026, 9, 21, 8, 0, 0, 0, berlin).toInstant(), calculator.nextOccurrence(schedule, now, berlin))
    }

    @Test fun oneTimeInPastReturnsNull() {
        val now = ZonedDateTime.of(2026, 9, 15, 9, 0, 0, 0, berlin).toInstant()
        val schedule = ReminderSchedule(LocalTime.of(8, 0), RecurrenceType.ONE_TIME, oneTimeDate = LocalDate.of(2026, 9, 15))
        assertNull(calculator.nextOccurrence(schedule, now, berlin))
    }

    @Test fun springGapMovesToFirstValidInstantAfterGap() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 3, 28, 10, 0, 0, 0, zone).toInstant()
        val schedule = ReminderSchedule(LocalTime.of(2, 30), RecurrenceType.ONE_TIME, oneTimeDate = LocalDate.of(2026, 3, 29))
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 0, 0, 0, zone).toInstant(), calculator.nextOccurrence(schedule, now, zone))
    }

    @Test fun autumnOverlapUsesEarlierOccurrence() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, zone).toInstant()
        val schedule = ReminderSchedule(LocalTime.of(2, 30), RecurrenceType.ONE_TIME, oneTimeDate = LocalDate.of(2026, 10, 25))
        val expected = ZonedDateTime.ofLocal(LocalDateTime.of(2026, 10, 25, 2, 30), zone, ZoneOffset.ofHours(2)).toInstant()
        assertEquals(expected, calculator.nextOccurrence(schedule, now, zone))
    }
    @Test fun recoversMissedDailyOccurrenceAfterPowerOff() {
        val createdBefore = ZonedDateTime.of(2026, 9, 14, 9, 0, 0, 0, berlin).toInstant()
        val bootedAfter = ZonedDateTime.of(2026, 9, 15, 9, 0, 0, 0, berlin).toInstant()
        val schedule = ReminderSchedule(LocalTime.of(8, 0), RecurrenceType.DAILY)
        assertEquals(
            ZonedDateTime.of(2026, 9, 15, 8, 0, 0, 0, berlin).toInstant(),
            calculator.latestMissedOccurrence(schedule, createdBefore, bootedAfter, berlin),
        )
    }

    @Test fun doesNotInventMissedOccurrenceWhenReminderWasCreatedAfterTodaysTime() {
        val createdAfter = ZonedDateTime.of(2026, 9, 15, 9, 0, 0, 0, berlin).toInstant()
        val now = ZonedDateTime.of(2026, 9, 15, 9, 1, 0, 0, berlin).toInstant()
        val schedule = ReminderSchedule(LocalTime.of(8, 0), RecurrenceType.DAILY)
        assertNull(calculator.latestMissedOccurrence(schedule, createdAfter, now, berlin))
    }

}
