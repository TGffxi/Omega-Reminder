package com.openai.omegareminder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class OccurrencePolicyTest {
    private val policy = OccurrencePolicy()
    private val now = Instant.parse("2026-09-15T06:00:00Z")

    @Test fun retryIsExactlyFifteenMinutes() {
        assertEquals(Instant.parse("2026-09-15T06:15:00Z"), policy.retryAt(now))
    }

    @Test fun snoozeSupportsExactlyTenThirtyAndSixtyMinutes() {
        assertEquals(Instant.parse("2026-09-15T06:10:00Z"), policy.snoozeUntil(now, 10))
        assertEquals(Instant.parse("2026-09-15T06:30:00Z"), policy.snoozeUntil(now, 30))
        assertEquals(Instant.parse("2026-09-15T07:00:00Z"), policy.snoozeUntil(now, 60))
        assertThrows(IllegalArgumentException::class.java) { policy.snoozeUntil(now, 15) }
    }
}
