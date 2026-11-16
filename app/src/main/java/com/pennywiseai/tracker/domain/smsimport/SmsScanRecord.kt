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
    /** Not about money (promos, OTPs, people), or a message that failed to parse or save. */
    val discarded: Int = 0,
    /** A balance update or a subscription mandate rather than a transaction. */
    val special: Int = 0,
) {
    val examined: Int get() = saved + duplicates + excluded + unrecognised + discarded + special
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
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
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
    FAILED,
    CANCELLED,
    INTERRUPTED;

    /** The scan ended without finishing, so new messages may be missing. */
    val needsAttention: Boolean get() = this == FAILED || this == CANCELLED || this == INTERRUPTED
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
        SmsScanState.SUCCEEDED -> LastScanStatus.SUCCEEDED
        SmsScanState.FAILED -> LastScanStatus.FAILED
        SmsScanState.CANCELLED -> LastScanStatus.CANCELLED
    }
}
