package com.pennywiseai.tracker.data.manager

import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.core.TimeConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SmsScanParamsCalculatorTest {

    private val zoneId = ZoneId.of("Asia/Kolkata")
    private val nowMillis = localStartOfDay(2025, 6, 21, 14, 30)

    @Test
    fun `custom date scan uses selected start date through today`() {
        val customStart = localStartOfDay(2025, 1, 15)

        val params = SmsScanParamsCalculator.compute(
            SmsScanParamsInput(
                lastScanTimestamp = 0L,
                scanMonths = 3,
                scanAllTime = false,
                scanUseCustomDate = true,
                scanCustomDateMillis = customStart,
                lastScanPeriod = 3,
                nowMillis = nowMillis,
            ),
            zoneId = zoneId,
        )

        assertTrue(params.needsFullScan)
        assertEquals(customStart, params.scanStartTime)
    }

    @Test
    fun `switching to custom date from months triggers full scan`() {
        val customStart = localStartOfDay(2025, 3, 1)

        val params = SmsScanParamsCalculator.compute(
            SmsScanParamsInput(
                lastScanTimestamp = nowMillis - TimeConstants.MILLIS_PER_DAY,
                scanMonths = 3,
                scanAllTime = false,
                scanUseCustomDate = true,
                scanCustomDateMillis = customStart,
                lastScanPeriod = 3,
                nowMillis = nowMillis,
            ),
            zoneId = zoneId,
        )

        assertTrue(params.needsFullScan)
        assertEquals(customStart, params.scanStartTime)
    }

    @Test
    fun `switching from custom date to months triggers full scan`() {
        val params = SmsScanParamsCalculator.compute(
            SmsScanParamsInput(
                lastScanTimestamp = nowMillis - TimeConstants.MILLIS_PER_DAY,
                scanMonths = 6,
                scanAllTime = false,
                scanUseCustomDate = false,
                lastScanPeriod = Constants.SmsProcessing.SCAN_PERIOD_CUSTOM_DATE,
                nowMillis = nowMillis,
            ),
            zoneId = zoneId,
        )

        assertTrue(params.needsFullScan)
        assertEquals(
            SmsScanParamsCalculator.monthBasedScanStartTime(6, nowMillis, zoneId),
            params.scanStartTime,
        )
    }

    @Test
    fun `incremental custom date scan respects custom start boundary`() {
        val customStart = localStartOfDay(2025, 5, 1)
        val lastScan = nowMillis - TimeConstants.MILLIS_PER_DAY

        val params = SmsScanParamsCalculator.compute(
            SmsScanParamsInput(
                lastScanTimestamp = lastScan,
                scanMonths = 3,
                scanAllTime = false,
                scanUseCustomDate = true,
                scanCustomDateMillis = customStart,
                lastScanPeriod = Constants.SmsProcessing.SCAN_PERIOD_CUSTOM_DATE,
                nowMillis = nowMillis,
            ),
            zoneId = zoneId,
        )

        assertFalse(params.needsFullScan)
        assertEquals(
            maxOf(
                minOf(lastScan, nowMillis - TimeConstants.MILLIS_PER_3_DAYS),
                customStart,
            ),
            params.scanStartTime,
        )
    }

    @Test
    fun `custom date without stored value falls back to month based start`() {
        val expectedStart = SmsScanParamsCalculator.monthBasedScanStartTime(3, nowMillis, zoneId)

        val startTime = SmsScanParamsCalculator.scanPeriodStartTime(
            scanAllTime = false,
            scanUseCustomDate = true,
            scanCustomDateMillis = null,
            scanMonths = 3,
            nowMillis = nowMillis,
            zoneId = zoneId,
        )

        assertEquals(expectedStart, startTime)
    }

    @Test
    fun `resolveLastScanPeriod stores custom date sentinel`() {
        assertEquals(
            Constants.SmsProcessing.SCAN_PERIOD_CUSTOM_DATE,
            SmsScanParamsCalculator.resolveLastScanPeriod(
                scanAllTime = false,
                scanUseCustomDate = true,
                scanMonths = 3,
            ),
        )
    }

    @Test
    fun `month based scan period still works`() {
        val expectedStart = SmsScanParamsCalculator.monthBasedScanStartTime(3, nowMillis, zoneId)

        val params = SmsScanParamsCalculator.compute(
            SmsScanParamsInput(
                lastScanTimestamp = 0L,
                scanMonths = 3,
                scanAllTime = false,
                scanUseCustomDate = false,
                lastScanPeriod = 3,
                nowMillis = nowMillis,
            ),
            zoneId = zoneId,
        )

        assertTrue(params.needsFullScan)
        assertEquals(expectedStart, params.scanStartTime)
    }

    @Test
    fun `all time scan still scans from epoch`() {
        val params = SmsScanParamsCalculator.compute(
            SmsScanParamsInput(
                lastScanTimestamp = 0L,
                scanMonths = 3,
                scanAllTime = true,
                scanUseCustomDate = false,
                lastScanPeriod = Constants.SmsProcessing.SCAN_PERIOD_ALL_TIME,
                nowMillis = nowMillis,
            ),
            zoneId = zoneId,
        )

        assertTrue(params.needsFullScan)
        assertEquals(0L, params.scanStartTime)
    }

    @Test
    fun `normalizePickerDateToLocalStartOfDay converts picker date to local midnight`() {
        val pickerMillis = LocalDate.of(2025, 1, 15)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val normalized = SmsScanParamsCalculator.normalizePickerDateToLocalStartOfDay(
            pickerMillis = pickerMillis,
            zoneId = zoneId,
        )

        assertEquals(localStartOfDay(2025, 1, 15), normalized)
    }

    private fun localStartOfDay(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Long {
        return LocalDate.of(year, month, day)
            .atTime(hour, minute)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
