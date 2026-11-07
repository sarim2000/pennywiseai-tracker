package com.pennywiseai.tracker.data.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class ConscienceFreshnessTest {

    private val zone = ZoneOffset.ofHours(3)
    private val now = LocalDateTime.of(2026, 6, 15, 12, 0)
    private val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    private val minute = 60_000L

    private fun fresh(eventMillis: Long = nowMillis, row: LocalDateTime = now) =
        ConscienceManager.isFresh(eventMillis, row, nowMillis, zone)

    @Test
    fun `an event and row from just now are fresh`() {
        assertTrue(fresh())
        assertTrue(fresh(eventMillis = nowMillis - minute, row = now.minusMinutes(1)))
    }

    @Test
    fun `exactly ten minutes either side is still fresh`() {
        assertTrue(fresh(eventMillis = nowMillis - 10 * minute, row = now.minusMinutes(10)))
        assertTrue(fresh(eventMillis = nowMillis + 10 * minute, row = now.plusMinutes(10)))
    }

    @Test
    fun `a stale event is not fresh even with a fresh row`() {
        assertFalse(fresh(eventMillis = nowMillis - 10 * minute - 1))
    }

    @Test
    fun `a new notification describing an old purchase is not fresh`() {
        assertFalse(fresh(row = now.minusDays(1)))
        assertFalse(fresh(row = now.minusMinutes(11)))
    }

    @Test
    fun `either time too far in the future is not fresh`() {
        assertFalse(fresh(eventMillis = nowMillis + 10 * minute + 1))
        assertFalse(fresh(row = now.plusMinutes(11)))
    }

    @Test
    fun `quiet hours run from 23 00 to 06 59`() {
        assertFalse(ConscienceManager.isQuietHour(LocalTime.of(22, 59)))
        assertTrue(ConscienceManager.isQuietHour(LocalTime.of(23, 0)))
        assertTrue(ConscienceManager.isQuietHour(LocalTime.MIDNIGHT))
        assertTrue(ConscienceManager.isQuietHour(LocalTime.of(6, 59)))
        assertFalse(ConscienceManager.isQuietHour(LocalTime.of(7, 0)))
    }
}
