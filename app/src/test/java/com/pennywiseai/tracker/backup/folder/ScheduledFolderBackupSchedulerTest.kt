package com.pennywiseai.tracker.backup.folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ScheduledFolderBackupSchedulerTest {

    @Test
    fun computeInitialDelayMs_beforeTwoAm_targetsSameDay() {
        val now = LocalDateTime.of(2026, 6, 21, 1, 30)
        val delayMs = ScheduledFolderBackupScheduler.computeInitialDelayMs(
            now = now,
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(TimeUnit.MINUTES.toMillis(30), delayMs)
    }

    @Test
    fun computeInitialDelayMs_afterTwoAm_targetsNextDay() {
        val now = LocalDateTime.of(2026, 6, 21, 3, 0)
        val delayMs = ScheduledFolderBackupScheduler.computeInitialDelayMs(
            now = now,
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(TimeUnit.HOURS.toMillis(23), delayMs)
    }

    @Test
    fun computeInitialDelayMs_atTwoAm_targetsNextDay() {
        val now = LocalDateTime.of(2026, 6, 21, 2, 0)
        val delayMs = ScheduledFolderBackupScheduler.computeInitialDelayMs(
            now = now,
            zoneId = ZoneId.of("UTC")
        )
        assertEquals(TimeUnit.HOURS.toMillis(24), delayMs)
    }

    @Test
    fun computeInitialDelayMs_isAlwaysPositive() {
        val now = LocalDateTime.of(2026, 6, 21, 12, 45)
        val delayMs = ScheduledFolderBackupScheduler.computeInitialDelayMs(
            now = now,
            zoneId = ZoneId.of("UTC")
        )
        assertTrue(delayMs > 0)
    }
}
