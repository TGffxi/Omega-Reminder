package com.openai.omegareminder.domain

import java.time.Instant

fun main() {
    val p = OccurrencePolicy()
    val t = Instant.parse("2026-09-15T06:00:00Z")
    check(p.retryAt(t) == Instant.parse("2026-09-15T06:15:00Z"))
    check(p.snoozeUntil(t, 10) == Instant.parse("2026-09-15T06:10:00Z"))
    check(p.snoozeUntil(t, 30) == Instant.parse("2026-09-15T06:30:00Z"))
    check(p.snoozeUntil(t, 60) == Instant.parse("2026-09-15T07:00:00Z"))
    runCatching { p.snoozeUntil(t, 15) }.onSuccess { error("15 min must be rejected") }
    println("PASS occurrence policy")
}
