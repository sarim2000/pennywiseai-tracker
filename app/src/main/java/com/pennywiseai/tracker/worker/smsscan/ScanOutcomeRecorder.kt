package com.pennywiseai.tracker.worker.smsscan

import android.util.Log
import com.pennywiseai.tracker.data.preferences.ScanPreferences
import com.pennywiseai.tracker.domain.smsimport.SmsScanCounts
import com.pennywiseai.tracker.domain.smsimport.SmsScanRecord
import com.pennywiseai.tracker.domain.smsimport.SmsScanState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Brackets one scan with the [SmsScanRecord]s the SMS check-up reads: RUNNING before any
 * work, SUCCEEDED only once the scan returns, FAILED when it throws, and CANCELLED when it
 * is cancelled or its work was stopped. A scan that dies with its process stays RUNNING,
 * which reads as interrupted, so no early end ever shows as a success. A scan that returns
 * with failed saves is SUCCEEDED with them counted, and isn't the last success either.
 */
internal class ScanOutcomeRecorder(
    private val preferences: ScanPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Set once counting starts; a scan that ends before that records no counts. */
    var stats: ProcessingStats? = null

    /**
     * Whether this scan reads the whole window: an earlier whole-window scan that didn't
     * finish clean left messages the incremental cursor won't reach (see
     * [SmsScanRecord.fullWindow]). Known once [run] starts the scan.
     */
    var fullWindow: Boolean = false
        private set

    private var startedAt = 0L
    private var lastSucceededAt: Long? = null

    /** Runs [scan] and rethrows whatever it throws, once the outcome is recorded. */
    suspend fun <T> run(isStopped: () -> Boolean, scan: suspend () -> T): T {
        startedAt = clock()
        val previous = previousRecord()
        lastSucceededAt = previous?.lastSucceededAt
        fullWindow = previous?.owesFullWindow == true
        record(running())
        val result = try {
            scan()
        } catch (e: Exception) {
            val state = if (e is CancellationException || isStopped()) SmsScanState.CANCELLED else SmsScanState.FAILED
            // The coroutine may already be cancelled; the record still has to land.
            withContext(NonCancellable) { record(finished(state)) }
            throw e
        }
        record(finished(SmsScanState.SUCCEEDED))
        return result
    }

    /** The scan reads the whole window, so until it finishes clean the next one must too. */
    suspend fun markFullWindow() {
        if (fullWindow) return
        fullWindow = true
        record(running())
    }

    private fun running() =
        SmsScanRecord(SmsScanState.RUNNING, startedAt, lastSucceededAt = lastSucceededAt, fullWindow = fullWindow)

    /**
     * A success with everything saved stamps its own finish time; anything else carries
     * the last success on, so "Scan again" never sits next to "last successful: just now".
     */
    private fun finished(state: SmsScanState): SmsScanRecord {
        val finishedAt = clock()
        val counts = stats?.counts() ?: SmsScanCounts()
        val clean = state == SmsScanState.SUCCEEDED && counts.failed == 0
        return SmsScanRecord(
            state = state,
            startedAt = startedAt,
            finishedAt = finishedAt,
            counts = counts,
            lastSucceededAt = if (clean) finishedAt else lastSucceededAt,
            fullWindow = fullWindow,
        )
    }

    private suspend fun previousRecord(): SmsScanRecord? = try {
        preferences.lastScanRecord.first()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Couldn't read the last scan record", e)
        null
    }

    /** Best effort: the check-up's record must never fail the scan itself. */
    private suspend fun record(record: SmsScanRecord) {
        try {
            preferences.setLastScanRecord(record)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Couldn't record the scan outcome", e)
        }
    }

    private companion object {
        const val TAG = "ScanOutcomeRecorder"
    }
}
