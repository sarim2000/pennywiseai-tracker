package com.pennywiseai.tracker.domain.smsimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsScanRecordTest {

    private val counts = SmsScanCounts(saved = 4, duplicates = 3, excluded = 2, unrecognised = 5, discarded = 90, special = 1)

    @Test
    fun `examined is the sum of every outcome`() {
        assertEquals(105, counts.examined)
        assertEquals(0, SmsScanCounts().examined)
    }

    @Test
    fun `a record survives its JSON round trip`() {
        val record = SmsScanRecord(SmsScanState.SUCCEEDED, startedAt = 1_000L, finishedAt = 2_000L, counts = counts)

        assertEquals(record, SmsScanRecord.fromJson(record.toJson()))
    }

    @Test
    fun `nothing stored or an unreadable record reads as no record`() {
        assertNull(SmsScanRecord.fromJson(null))
        assertNull(SmsScanRecord.fromJson("not json"))
        assertNull(SmsScanRecord.fromJson("""{"state":"SOMETHING_NEW"}"""))
    }

    @Test
    fun `fields a newer version adds are ignored`() {
        val record = SmsScanRecord.fromJson("""{"state":"FAILED","startedAt":5,"extra":true}""")

        assertEquals(SmsScanRecord(SmsScanState.FAILED, startedAt = 5L), record)
    }

    @Test
    fun `a scan left running without work behind it was interrupted`() {
        val record = SmsScanRecord(SmsScanState.RUNNING, startedAt = 1L)

        assertEquals(LastScanStatus.INTERRUPTED, lastScanStatus(record, scanRunning = false))
        assertEquals(LastScanStatus.RUNNING, lastScanStatus(record, scanRunning = true))
    }

    @Test
    fun `a cancelled or failed scan never reads as a success`() {
        assertEquals(
            LastScanStatus.CANCELLED,
            lastScanStatus(SmsScanRecord(SmsScanState.CANCELLED, counts = counts), scanRunning = false)
        )
        assertEquals(
            LastScanStatus.FAILED,
            lastScanStatus(SmsScanRecord(SmsScanState.FAILED, counts = counts), scanRunning = false)
        )
        assertEquals(
            LastScanStatus.SUCCEEDED,
            lastScanStatus(SmsScanRecord(SmsScanState.SUCCEEDED), scanRunning = false)
        )
    }

    @Test
    fun `no record means no scan yet, unless one is running now`() {
        assertEquals(LastScanStatus.NEVER, lastScanStatus(null, scanRunning = false))
        assertEquals(LastScanStatus.RUNNING, lastScanStatus(null, scanRunning = true))
    }

    @Test
    fun `only a scan that ended early needs attention`() {
        val flagged = LastScanStatus.entries.filter { it.needsAttention }.toSet()

        assertEquals(setOf(LastScanStatus.FAILED, LastScanStatus.CANCELLED, LastScanStatus.INTERRUPTED), flagged)
    }
}
