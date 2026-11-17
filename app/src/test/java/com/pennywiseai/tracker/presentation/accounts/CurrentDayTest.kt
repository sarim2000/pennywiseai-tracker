package com.pennywiseai.tracker.presentation.accounts

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class CurrentDayTest {
    @Test
    fun theDayMovesOnAtMidnight() = runTest {
        val opened = LocalDateTime.of(2026, 11, 17, 23, 58)
        val days = currentDay { opened.plus(Duration.ofMillis(testScheduler.currentTime)) }.take(3).toList()

        assertEquals(
            listOf(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19)),
            days,
        )
        // Each day arrives just after its midnight, not a poll later.
        assertEquals(Duration.ofMinutes(2).plusDays(1).toMillis() + 1, testScheduler.currentTime)
    }
}
