package com.pennywiseai.tracker.presentation.settings

import com.pennywiseai.tracker.domain.smsimport.SmsScanCounts
import com.pennywiseai.tracker.domain.smsimport.SmsScanRecord
import com.pennywiseai.tracker.domain.smsimport.SmsScanState
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsCheckupNotImportedTest {

    private fun scanThatFound(unrecognised: Int) =
        SmsScanRecord(SmsScanState.SUCCEEDED, counts = SmsScanCounts(saved = 3, unrecognised = unrecognised))

    @Test
    fun `messages still listed are waiting for review`() {
        assertEquals(NotImported.Waiting(2), notImported(waiting = 2, lastScan = scanThatFound(5)))
    }

    @Test
    fun `an empty list after a scan that found unknown formats does not claim every message was read`() {
        // Dismissed, added by hand, or past the list's 30 days: the scan still counts them.
        assertEquals(NotImported.NoneWaiting, notImported(waiting = 0, lastScan = scanThatFound(4)))
    }

    @Test
    fun `an empty list after a scan that found no unknown formats means everything was read`() {
        assertEquals(NotImported.AllRead, notImported(waiting = 0, lastScan = scanThatFound(0)))
        assertEquals(NotImported.AllRead, notImported(waiting = 0, lastScan = null))
    }
}
