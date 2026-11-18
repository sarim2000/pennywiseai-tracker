package com.pennywiseai.tracker.worker.smsscan

import android.os.Process
import android.os.Trace
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "OptimizedSmsReaderWorker"

/**
 * The scan's topology: one feed into bounded channels, parallel parsers, one sequential
 * saver and one balance consumer. A coroutineScope, so a failing stage cancels the rest
 * and [run] only returns normally once every stage has finished. A feed that breaks off
 * is the exception: what it already read is still saved, then [run] throws its error.
 */
internal class SmsScanPipeline(
    private val source: SmsMessageSource,
    private val classify: (ScannedSms) -> ParseResult,
    private val isTaught: (ScannedSms) -> Boolean,
    private val writer: ScanTransactionWriter,
    private val parserConcurrency: Int,
    private val onProgress: suspend (ProcessingStats) -> Unit,
    private val onTransactionsChanged: () -> Unit,
) {
    companion object {
        const val PARSE_CHANNEL_CAPACITY    = 512
        const val RESULT_CHANNEL_CAPACITY   = 512
        const val UNRECOGNIZED_BATCH_SIZE   = 50
    }

    suspend fun run(scanStartTime: Long, stats: ProcessingStats) = coroutineScope {
        val feed    = Channel<ScannedSms>(PARSE_CHANNEL_CAPACITY)
        val results = Channel<ParseResult>(RESULT_CHANNEL_CAPACITY)

        // Stage 1 – Feed (streams SMS cursor + RCS directly into the channel). A read error
        // ends the feed instead of cancelling the scan, so the messages already read (up to
        // both channels' worth) are saved; a bad row can't keep the ones before it out.
        val feedError = AtomicReference<Exception?>(null)
        launch(Dispatchers.IO) {
            try {
                source.streamTo(feed, scanStartTime)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                feedError.set(e)
            } finally {
                feed.close()
            }
        }

        // Stage 2 – Parse (all available CPU cores)
        val parserJobs = (1..parserConcurrency).map { id ->
            launch(Dispatchers.Default) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
                Trace.beginSection("parse.$id")
                try {
                    for (sms in feed) {
                        try {
                            results.send(classify(sms))
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e(TAG, "Error parsing SMS from ${sms.sender}: ${e.message}")
                            results.send(ParseResult.Discard(sms))
                        }
                    }
                } finally {
                    Trace.endSection()
                }
            }
        }

        // Close results once all parsers finish
        launch(Dispatchers.Default) {
            parserJobs.joinAll()   // joinAll() preferred over forEach { it.join() }
            results.close()
        }

        // Stage 3 – Save (single sequential coroutine — no balance race conditions)
        // Balance updates run concurrently via a dedicated consumer so they never block the saver.
        val balanceUpdates = Channel<DeferredBalanceUpdate>(Channel.BUFFERED)
        val balanceUpdater = launch(Dispatchers.IO) {
            for (update in balanceUpdates) {
                try { writer.applyBalanceUpdate(update) } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Balance update failed: ${e.message}")
                }
            }
        }

        val saver = launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            val unrecognizedBatch = ArrayList<ScannedSms>(UNRECOGNIZED_BATCH_SIZE)
            var widgetNeedsUpdate = false
            var lastReportTime = 0L

            Trace.beginSection("save")
            try {
                for (result in results) {
                    val p = stats.processed.incrementAndGet()
                    stats.recordCompletion()

                    // Each message counts in exactly one outcome (see SmsScanCounts).
                    when (result) {
                        is ParseResult.Discard            -> stats.discarded.incrementAndGet()
                        is ParseResult.StoreUnrecognized  -> {
                            stats.unrecognised.incrementAndGet()
                            unrecognizedBatch.add(result.sms)
                            if (unrecognizedBatch.size >= UNRECOGNIZED_BATCH_SIZE)
                                flushUnrecognized(unrecognizedBatch, stats)
                        }
                        is ParseResult.SpecialNotification -> {
                            try {
                                result.onSave()
                                stats.special.incrementAndGet()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e(TAG, "Error saving special notification: ${e.message}")
                                stats.failed.incrementAndGet()
                            }
                        }
                        is ParseResult.Transaction -> {
                            stats.parsed.incrementAndGet()
                            Trace.beginSection("saveTransaction")
                            try {
                                when (writer.saveTransaction(result.parsed, result.sms, stats, balanceUpdates)) {
                                    SaveOutcome.SAVED -> {
                                        stats.saved.incrementAndGet()
                                        widgetNeedsUpdate = true
                                    }
                                    SaveOutcome.UPDATED_DUPLICATE -> {
                                        stats.duplicates.incrementAndGet()
                                        widgetNeedsUpdate = true
                                    }
                                    SaveOutcome.SKIPPED_DUPLICATE -> {
                                        stats.duplicates.incrementAndGet()
                                    }
                                    SaveOutcome.EXCLUDED -> stats.excluded.incrementAndGet()
                                    SaveOutcome.FAILED -> stats.failed.incrementAndGet()
                                }
                            } finally {
                                Trace.endSection()
                            }
                        }
                    }

                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastReportTime >= 250L || p == 1 || p == stats.total) {
                        onProgress(stats)
                        lastReportTime = nowMs
                    }
                }
            } finally {
                Trace.endSection()
            }

            if (unrecognizedBatch.isNotEmpty()) flushUnrecognized(unrecognizedBatch, stats)
            if (widgetNeedsUpdate) {
                onTransactionsChanged()
            }
        }

        saver.join()
        balanceUpdates.close()
        balanceUpdater.join()
        // Not a finished scan: the caller must neither record a success nor move the cursor.
        val readError = feedError.get()
        if (readError != null) throw readError
    }

    /** What couldn't be filed moves from unrecognised to failed, so the cursor stays put. */
    private suspend fun flushUnrecognized(batch: ArrayList<ScannedSms>, stats: ProcessingStats) {
        val lost = writer.flushUnrecognizedBatch(batch, isTaught)
        if (lost > 0) {
            stats.unrecognised.addAndGet(-lost)
            stats.failed.addAndGet(lost)
        }
    }
}
