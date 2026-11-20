package com.pennywiseai.tracker.domain.smsimport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What one scan did with the messages it examined. Every message lands in exactly
 * one bucket, so [examined] is their sum.
 */
@Serializable
data class SmsScanCounts(
    /** Booked as a new transaction. */
    val saved: Int = 0,
    /** Already in PennyWise: the same SMS, the same charge from another channel, or a merged duplicate. */
    val duplicates: Int = 0,
    /** Left out on purpose: an ignored account or a blocking rule. */
    val excluded: Int = 0,
    /** Looked like a bank message no parser reads; filed under Unrecognized SMS. */
    val unrecognised: Int = 0,
    /**
     * Not a transaction (promos, OTPs, people), or a message that couldn't be read: a
     * parser that threw, or a known bank's message no parser matched.
     */
    val discarded: Int = 0,
    /** A balance update or a subscription mandate rather than a transaction. */
    val special: Int = 0,
    /** Read, but storing it threw: a transaction, a balance or mandate, or an unrecognised message. */
    val failed: Int = 0,
    /** In the inbox, but its row couldn't be read, so the scan skipped it. */
    val notRead: Int = 0,
) {
    val examined: Int get() = saved + duplicates + excluded + unrecognised + discarded + special + failed + notRead
}

@Serializable
enum class SmsScanState { RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * The latest scan, as the worker left it. RUNNING is written before any work, so a scan
 * killed with its process stays RUNNING and reads as interrupted, never as a success.
 */
@Serializable
data class SmsScanRecord(
    val state: SmsScanState = SmsScanState.RUNNING,
    val startedAt: Long = 0L,
    val finishedAt: Long? = null,
    val counts: SmsScanCounts = SmsScanCounts(),
    /** When a scan last finished with everything saved: this one, or an earlier one. */
    val lastSucceededAt: Long? = null,
    /**
     * This scan read the whole scan window, or owed it to an earlier one that didn't
     * finish clean. The incremental cursor doesn't cover that window, so while such a
     * scan ends without saving everything, the next scan has to read it all again.
     */
    val fullWindow: Boolean = false,
    /** Scans in a row, up to this one, that ended with failed saves. A clean one resets it. */
    val failedSaveStreak: Int = 0,
) {
    /**
     * Failed saves were retried [MAX_SAVE_RETRIES] times and still fail: the scans move on
     * past them, or a save that always throws would re-read the window on every Sync and
     * keep the warning up for good.
     */
    val gaveUpOnFailedSaves: Boolean get() = failedSaveStreak > MAX_SAVE_RETRIES

    /** The next scan must read the whole window: see [fullWindow]. */
    val owesFullWindow: Boolean
        get() = fullWindow && (state != SmsScanState.SUCCEEDED || (counts.failed > 0 && !gaveUpOnFailedSaves))

    /**
     * Whether this record may be stored over [current]. A scan replaced mid-run can write
     * its outcome after its replacement started, even after it finished; that late write
     * must not overwrite the newer scan's record and its whole-window marker. A scan's
     * RUNNING record always lands: nothing is newer than a scan that is just starting.
     */
    fun mayReplace(current: SmsScanRecord?): Boolean =
        state == SmsScanState.RUNNING || current == null || current.startedAt <= startedAt

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        /** How many scans after the first one try failed saves again before moving on. */
        const val MAX_SAVE_RETRIES = 3

        private val json = Json { ignoreUnknownKeys = true }

        /** Null for nothing stored, or for a record this version can't read. */
        fun fromJson(raw: String?): SmsScanRecord? =
            raw?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** What the check-up shows for the latest scan. */
enum class LastScanStatus {
    NEVER,
    RUNNING,
    SUCCEEDED,

    /** Finished, but some messages it read couldn't be saved. */
    SAVE_FAILED,
    FAILED,
    CANCELLED,
    INTERRUPTED;

    /** The scan ended without finishing, so new messages may be missing. */
    val endedEarly: Boolean get() = this == FAILED || this == CANCELLED || this == INTERRUPTED

    /** Messages may be missing: the scan ended early, or some of what it read wasn't saved. */
    val needsAttention: Boolean get() = endedEarly || this == SAVE_FAILED
}

/**
 * [scanRunning] is WorkManager's word on whether a scan is queued or running now. A
 * RUNNING record without one means the process died mid-scan.
 */
fun lastScanStatus(record: SmsScanRecord?, scanRunning: Boolean): LastScanStatus = when {
    scanRunning -> LastScanStatus.RUNNING
    record == null -> LastScanStatus.NEVER
    else -> when (record.state) {
        SmsScanState.RUNNING -> LastScanStatus.INTERRUPTED
        SmsScanState.SUCCEEDED ->
            if (record.counts.failed > 0) LastScanStatus.SAVE_FAILED else LastScanStatus.SUCCEEDED
        SmsScanState.FAILED -> LastScanStatus.FAILED
        SmsScanState.CANCELLED -> LastScanStatus.CANCELLED
    }
}
