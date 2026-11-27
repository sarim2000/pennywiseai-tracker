package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.domain.smsimport.SmsScanCounts
import com.pennywiseai.tracker.domain.smsimport.SmsScanRecord
import com.pennywiseai.tracker.domain.smsimport.SmsScanState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingCheckupTest {

    private val clean = SmsScanRecord(
        state = SmsScanState.SUCCEEDED,
        startedAt = 1_000L,
        finishedAt = 2_000L,
        lastSucceededAt = 2_000L,
    )

    private fun health(
        smsGranted: Boolean = true,
        backgroundRestricted: Boolean? = false,
        record: SmsScanRecord? = clean,
        scanRunning: Boolean = false,
    ) = trackingCheckup(smsGranted, backgroundRestricted, record, scanRunning, lastLiveSmsAt = null).health

    @Test
    fun `SMS access comes first, then a background restriction`() {
        assertEquals(TrackingHealth.SMS_ACCESS_OFF, health(smsGranted = false, backgroundRestricted = true))
        assertEquals(TrackingHealth.BACKGROUND_RESTRICTED, health(backgroundRestricted = true, scanRunning = true))
    }

    @Test
    fun `an unknown background restriction is not a problem`() {
        // Below Android 9 there's no background restriction to read.
        assertEquals(TrackingHealth.ON, health(backgroundRestricted = null))
    }

    @Test
    fun `a scan that ended early or failed saves may have missed messages`() {
        assertEquals(TrackingHealth.MESSAGES_MAY_BE_MISSING, health(record = clean.copy(state = SmsScanState.FAILED)))
        assertEquals(TrackingHealth.MESSAGES_MAY_BE_MISSING, health(record = clean.copy(state = SmsScanState.CANCELLED)))
        assertEquals(
            TrackingHealth.MESSAGES_MAY_BE_MISSING,
            health(record = clean.copy(counts = SmsScanCounts(saved = 3, failed = 1)))
        )
    }

    @Test
    fun `a RUNNING record is scanning while WorkManager runs it, and interrupted after`() {
        val running = SmsScanRecord(SmsScanState.RUNNING, startedAt = 3_000L, lastSucceededAt = 2_000L)
        assertEquals(TrackingHealth.SCANNING, health(record = running, scanRunning = true))
        assertEquals(TrackingHealth.MESSAGES_MAY_BE_MISSING, health(record = running))
    }

    @Test
    fun `last read is the newer of the last clean scan and the last live SMS`() {
        assertEquals(2_000L, trackingCheckup(true, false, clean, false, lastLiveSmsAt = 1_500L).lastReadAt)
        assertEquals(9_000L, trackingCheckup(true, false, clean, false, lastLiveSmsAt = 9_000L).lastReadAt)
        assertEquals(9_000L, trackingCheckup(true, false, null, false, lastLiveSmsAt = 9_000L).lastReadAt)
        val never = trackingCheckup(true, false, null, false, lastLiveSmsAt = null)
        assertEquals(TrackingHealth.ON, never.health)
        assertNull(never.lastReadAt)
    }
}
