package com.openai.omegareminder.overlay

data class OverlayCandidate(
    val reminderId: Long,
    val scheduledForEpochMillis: Long,
    val enabled: Boolean,
    val outstanding: Boolean,
)

fun selectOldestOutstanding(candidates: List<OverlayCandidate>): Long? =
    candidates
        .asSequence()
        .filter { it.enabled && it.outstanding }
        .minWithOrNull(
            compareBy<OverlayCandidate> { it.scheduledForEpochMillis }
                .thenBy { it.reminderId }
        )
        ?.reminderId
