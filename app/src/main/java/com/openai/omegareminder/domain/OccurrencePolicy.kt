package com.openai.omegareminder.domain

import java.time.Duration
import java.time.Instant

class OccurrencePolicy {
    fun retryAt(from: Instant): Instant = from.plus(Duration.ofMinutes(15))

    fun snoozeUntil(from: Instant, minutes: Int): Instant {
        require(minutes in setOf(10, 30, 60)) { "Unsupported snooze duration: $minutes" }
        return from.plus(Duration.ofMinutes(minutes.toLong()))
    }
}
