package com.openai.omegareminder.domain

import java.time.*

class ScheduleCalculator {
    fun nextOccurrence(schedule: ReminderSchedule, now: Instant, zone: ZoneId): Instant? {
        return when (schedule.recurrenceType) {
            RecurrenceType.ONE_TIME -> schedule.oneTimeDate
                ?.let { resolve(it, schedule.localTime, zone) }
                ?.takeIf { it.isAfter(now) }

            RecurrenceType.DAILY -> {
                val today = now.atZone(zone).toLocalDate()
                val todayInstant = resolve(today, schedule.localTime, zone)
                if (todayInstant.isAfter(now)) todayInstant
                else resolve(today.plusDays(1), schedule.localTime, zone)
            }

            RecurrenceType.WEEKDAYS -> {
                if (schedule.weekdayMask == 0) return null
                val today = now.atZone(zone).toLocalDate()
                (0L..7L).asSequence()
                    .map { today.plusDays(it) }
                    .filter { isSelected(it.dayOfWeek, schedule.weekdayMask) }
                    .map { resolve(it, schedule.localTime, zone) }
                    .firstOrNull { it.isAfter(now) }
            }
        }
    }


    fun latestMissedOccurrence(
        schedule: ReminderSchedule,
        activeSinceExclusive: Instant,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        fun valid(candidate: Instant): Instant? =
            candidate.takeIf { it.isAfter(activeSinceExclusive) && !it.isAfter(now) }

        return when (schedule.recurrenceType) {
            RecurrenceType.ONE_TIME -> schedule.oneTimeDate
                ?.let { resolve(it, schedule.localTime, zone) }
                ?.let(::valid)

            RecurrenceType.DAILY -> {
                val today = now.atZone(zone).toLocalDate()
                val todayCandidate = resolve(today, schedule.localTime, zone)
                valid(todayCandidate) ?: valid(resolve(today.minusDays(1), schedule.localTime, zone))
            }

            RecurrenceType.WEEKDAYS -> {
                if (schedule.weekdayMask == 0) return null
                val today = now.atZone(zone).toLocalDate()
                (0L..7L).asSequence()
                    .map { today.minusDays(it) }
                    .filter { isSelected(it.dayOfWeek, schedule.weekdayMask) }
                    .map { resolve(it, schedule.localTime, zone) }
                    .mapNotNull(::valid)
                    .firstOrNull()
            }
        }
    }

    private fun isSelected(day: DayOfWeek, mask: Int): Boolean {
        val bit = 1 shl (day.value - 1)
        return mask and bit != 0
    }

    private fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
        val local = LocalDateTime.of(date, time)
        val rules = zone.rules
        val offsets = rules.getValidOffsets(local)
        val zoned = when {
            offsets.size == 1 -> ZonedDateTime.ofLocal(local, zone, offsets.first())
            offsets.size >= 2 -> ZonedDateTime.ofLocal(local, zone, offsets.first())
            else -> {
                val transition = requireNotNull(rules.getTransition(local))
                ZonedDateTime.ofLocal(transition.dateTimeAfter, zone, transition.offsetAfter)
            }
        }
        return zoned.toInstant()
    }
}
