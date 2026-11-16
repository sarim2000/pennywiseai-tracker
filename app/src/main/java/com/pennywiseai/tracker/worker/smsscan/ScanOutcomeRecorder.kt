package com.pennywiseai.tracker.worker.smsscan

import android.util.Log
import com.pennywiseai.tracker.data.preferences.ScanPreferences
import com.pennywiseai.tracker.domain.smsimport.SmsScanCounts
import com.pennywiseai.tracker.domain.smsimport.SmsScanRecord
import com.pennywiseai.tracker.domain.smsimport.SmsScanState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Brackets one scan with the [SmsScanRecord]s the SMS check-up reads: RUNNING before any
 * work, SUCCEEDED only once the scan returns, FAILED when it throws, and CANCELLED when it
 * is cancelled or its work was stopped. A scan that dies with its process stays RUNNING,
 * which reads as interrupted, so no early end ever shows as a success.
 */
internal class ScanOutcomeRecorder(
    private val preferences: ScanPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Set once counting starts; a scan that ends before that records no counts. */
    var stats: ProcessingStats? = null

    /** Runs [scan] and rethrows whatever it throws, once the outcome is recorded. */
    suspend fun <T> run(isStopped: () -> Boolean, scan: suspend () -> T): T {
        val startedAt = clock()
        record(SmsScanRecord(SmsScanState.RUNNING, startedAt))
        val result = try {
            scan()
        } catch (e: Exception) {
            val state = if (e is CancellationException || isStopped()) SmsScanState.CANCELLED else SmsScanState.FAILED
            // The coroutine may already be cancelled; the record still has to land.
            withContext(NonCancellable) { record(finished(state, startedAt)) }
            throw e
        }
        record(finished(SmsScanState.SUCCEEDED, startedAt))
        return result
    }

    private fun finished(state: SmsScanState, startedAt: Long) = SmsScanRecord(
        state = state,
        startedAt = startedAt,
        finishedAt = clock(),
        counts = stats?.counts() ?: SmsScanCounts(),
    )

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
