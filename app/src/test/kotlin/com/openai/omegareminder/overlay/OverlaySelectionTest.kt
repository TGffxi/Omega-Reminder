package com.openai.omegareminder.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlaySelectionTest {
    @Test
    fun selectsOldestEnabledOutstandingReminder() {
        val selected = selectOldestOutstanding(
            listOf(
                OverlayCandidate(3, 3_000, enabled = true, outstanding = true),
                OverlayCandidate(1, 1_000, enabled = true, outstanding = true),
                OverlayCandidate(2, 500, enabled = false, outstanding = true),
                OverlayCandidate(4, 100, enabled = true, outstanding = false),
            )
        )

        assertEquals(1L, selected)
    }

    @Test
    fun usesReminderIdAsStableTieBreaker() {
        val selected = selectOldestOutstanding(
            listOf(
                OverlayCandidate(9, 1_000, enabled = true, outstanding = true),
                OverlayCandidate(4, 1_000, enabled = true, outstanding = true),
            )
        )

        assertEquals(4L, selected)
    }

    @Test
    fun returnsNullWhenNothingIsOutstanding() {
        assertNull(
            selectOldestOutstanding(
                listOf(
                    OverlayCandidate(1, 100, enabled = true, outstanding = false),
                    OverlayCandidate(2, 200, enabled = false, outstanding = true),
                )
            )
        )
    }
}
