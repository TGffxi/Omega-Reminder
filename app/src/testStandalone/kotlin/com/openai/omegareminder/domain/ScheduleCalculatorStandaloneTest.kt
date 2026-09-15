package com.openai.omegareminder.domain

import java.time.*

fun main() {
    val c = ScheduleCalculator()
    val zone = ZoneId.of("Europe/Berlin")
    fun eq(name: String, expected: Instant?, actual: Instant?) {
        check(expected == actual) { "$name expected=$expected actual=$actual" }
        println("PASS $name")
    }
    val nowMorning = ZonedDateTime.of(2026,9,15,7,0,0,0,zone).toInstant()
    eq("daily same day", ZonedDateTime.of(2026,9,15,8,0,0,0,zone).toInstant(), c.nextOccurrence(ReminderSchedule(LocalTime.of(8,0), RecurrenceType.DAILY), nowMorning, zone))
    val nowLate = ZonedDateTime.of(2026,9,15,9,0,0,0,zone).toInstant()
    eq("daily tomorrow", ZonedDateTime.of(2026,9,16,8,0,0,0,zone).toInstant(), c.nextOccurrence(ReminderSchedule(LocalTime.of(8,0), RecurrenceType.DAILY), nowLate, zone))
    val mondayMask = 1 shl (DayOfWeek.MONDAY.value - 1)
    val friday = ZonedDateTime.of(2026,9,18,9,0,0,0,zone).toInstant()
    eq("weekday monday", ZonedDateTime.of(2026,9,21,8,0,0,0,zone).toInstant(), c.nextOccurrence(ReminderSchedule(LocalTime.of(8,0), RecurrenceType.WEEKDAYS, weekdayMask=mondayMask), friday, zone))
    eq("one time past", null, c.nextOccurrence(ReminderSchedule(LocalTime.of(8,0), RecurrenceType.ONE_TIME, oneTimeDate=LocalDate.of(2026,9,15)), nowLate, zone))
    val springNow = ZonedDateTime.of(2026,3,28,10,0,0,0,zone).toInstant()
    eq("spring gap", ZonedDateTime.of(2026,3,29,3,0,0,0,zone).toInstant(), c.nextOccurrence(ReminderSchedule(LocalTime.of(2,30), RecurrenceType.ONE_TIME, oneTimeDate=LocalDate.of(2026,3,29)), springNow, zone))
    val autumnNow = ZonedDateTime.of(2026,10,24,10,0,0,0,zone).toInstant()
    val overlapExpected = ZonedDateTime.ofLocal(LocalDateTime.of(2026,10,25,2,30), zone, ZoneOffset.ofHours(2)).toInstant()
    eq("autumn overlap", overlapExpected, c.nextOccurrence(ReminderSchedule(LocalTime.of(2,30), RecurrenceType.ONE_TIME, oneTimeDate=LocalDate.of(2026,10,25)), autumnNow, zone))
}
