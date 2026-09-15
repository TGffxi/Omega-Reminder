package com.openai.omegareminder.domain

import java.time.*

fun main() {
    val c = ScheduleCalculator()
    val zone = ZoneId.of("Europe/Berlin")
    fun z(y:Int,m:Int,d:Int,h:Int,min:Int)=ZonedDateTime.of(y,m,d,h,min,0,0,zone).toInstant()

    val daily = ReminderSchedule(LocalTime.of(8,0), RecurrenceType.DAILY)
    check(c.latestMissedOccurrence(daily, z(2026,9,14,9,0), z(2026,9,15,9,0), zone) == z(2026,9,15,8,0))
    check(c.latestMissedOccurrence(daily, z(2026,9,15,8,17), z(2026,9,15,9,0), zone) == null)
    check(c.latestMissedOccurrence(daily, z(2026,9,15,9,0), z(2026,9,15,9,1), zone) == null)

    val one = ReminderSchedule(LocalTime.of(8,0), RecurrenceType.ONE_TIME, oneTimeDate=LocalDate.of(2026,9,15))
    check(c.latestMissedOccurrence(one, z(2026,9,14,12,0), z(2026,9,15,9,0), zone) == z(2026,9,15,8,0))

    val mondayMask = 1 shl (DayOfWeek.MONDAY.value - 1)
    val weekly = ReminderSchedule(LocalTime.of(8,0), RecurrenceType.WEEKDAYS, weekdayMask=mondayMask)
    check(c.latestMissedOccurrence(weekly, z(2026,9,13,10,0), z(2026,9,14,9,0), zone) == z(2026,9,14,8,0))
    println("PASS missed occurrence recovery")
}
