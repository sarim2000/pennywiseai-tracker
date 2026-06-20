package com.pennywiseai.tracker.data.manager

import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.core.TimeConstants
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class SmsScanParamsInput(
    val forceResync: Boolean = false,
    val lastScanTimestamp: Long? = null,
    val scanMonths: Int,
    val scanAllTime: Boolean,
    val scanUseCustomDate: Boolean,
    val scanCustomDateMillis: Long? = null,
    val lastScanPeriod: Int? = null,
    val nowMillis: Long,
)

data class SmsScanParams(
    val scanStartTime: Long,
    val needsFullScan: Boolean,
)

object SmsScanParamsCalculator {

    fun compute(input: SmsScanParamsInput, zoneId: ZoneId = ZoneId.systemDefault()): SmsScanParams {
        val lastScanTimestamp = input.lastScanTimestamp ?: 0L
        val lastScanPeriod = input.lastScanPeriod ?: 0
        val now = input.nowMillis

        val scanAllTimeToggled = input.scanAllTime &&
            lastScanPeriod != Constants.SmsProcessing.SCAN_PERIOD_ALL_TIME
        val scanAllTimeToggledOff = !input.scanAllTime &&
            lastScanPeriod == Constants.SmsProcessing.SCAN_PERIOD_ALL_TIME
        val customDateToggled = input.scanUseCustomDate &&
            lastScanPeriod != Constants.SmsProcessing.SCAN_PERIOD_CUSTOM_DATE
        val customDateToggledOff = !input.scanUseCustomDate &&
            lastScanPeriod == Constants.SmsProcessing.SCAN_PERIOD_CUSTOM_DATE
        val needsFullScan = input.forceResync || lastScanTimestamp == 0L ||
            (lastScanPeriod >= 0 && input.scanMonths > lastScanPeriod) ||
            scanAllTimeToggled || scanAllTimeToggledOff ||
            customDateToggled || customDateToggledOff

        val periodLimit = scanPeriodStartTime(
            scanAllTime = input.scanAllTime,
            scanUseCustomDate = input.scanUseCustomDate,
            scanCustomDateMillis = input.scanCustomDateMillis,
            scanMonths = input.scanMonths,
            nowMillis = now,
            zoneId = zoneId,
        )

        val scanStartTime = if (needsFullScan) {
            periodLimit
        } else {
            val threeDaysAgo = now - TimeConstants.MILLIS_PER_3_DAYS
            maxOf(minOf(lastScanTimestamp, threeDaysAgo), periodLimit)
        }

        return SmsScanParams(scanStartTime = scanStartTime, needsFullScan = needsFullScan)
    }

    fun resolveLastScanPeriod(
        scanAllTime: Boolean,
        scanUseCustomDate: Boolean,
        scanMonths: Int,
    ): Int = when {
        scanAllTime -> Constants.SmsProcessing.SCAN_PERIOD_ALL_TIME
        scanUseCustomDate -> Constants.SmsProcessing.SCAN_PERIOD_CUSTOM_DATE
        else -> scanMonths
    }

    fun scanPeriodStartTime(
        scanAllTime: Boolean,
        scanUseCustomDate: Boolean,
        scanCustomDateMillis: Long?,
        scanMonths: Int,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (scanAllTime) return 0L

        if (scanUseCustomDate) {
            return scanCustomDateMillis
                ?: monthBasedScanStartTime(scanMonths, nowMillis, zoneId)
        }

        return monthBasedScanStartTime(scanMonths, nowMillis, zoneId)
    }

    fun monthBasedScanStartTime(
        scanMonths: Int,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .minusMonths(scanMonths.toLong())
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun normalizePickerDateToLocalStartOfDay(
        pickerMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        return Instant.ofEpochMilli(pickerMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
