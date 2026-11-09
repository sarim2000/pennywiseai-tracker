package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.worker.smsscan.ProcessingStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SmsParsingProgressDialog and onboarding read these; `getLong` on an Int silently gives the default. */
class ScanProgressDataTest {

    @Test
    fun `progress data keeps every key and type the screens read`() {
        val stats = ProcessingStats(total = 10).apply {
            processed.set(4)
            parsed.set(3)
            saved.set(2)
            blocked.set(1)
        }

        val data = OptimizedSmsReaderWorker.progressData(stats)

        assertEquals(
            setOf(
                OptimizedSmsReaderWorker.PROGRESS_TOTAL,
                OptimizedSmsReaderWorker.PROGRESS_PROCESSED,
                OptimizedSmsReaderWorker.PROGRESS_PARSED,
                OptimizedSmsReaderWorker.PROGRESS_SAVED,
                OptimizedSmsReaderWorker.PROGRESS_BLOCKED,
                OptimizedSmsReaderWorker.PROGRESS_TIME_ELAPSED,
                OptimizedSmsReaderWorker.PROGRESS_ESTIMATED_TIME_REMAINING,
                OptimizedSmsReaderWorker.PROGRESS_ETA_SECONDS,
                OptimizedSmsReaderWorker.PROGRESS_MSG_PER_SEC,
                OptimizedSmsReaderWorker.PROGRESS_CURRENT_BATCH,
                OptimizedSmsReaderWorker.PROGRESS_TOTAL_BATCHES,
            ),
            data.keyValueMap.keys,
        )
        assertEquals(10, data.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL, -1))
        assertEquals(4, data.getInt(OptimizedSmsReaderWorker.PROGRESS_PROCESSED, -1))
        assertEquals(3, data.getInt(OptimizedSmsReaderWorker.PROGRESS_PARSED, -1))
        assertEquals(2, data.getInt(OptimizedSmsReaderWorker.PROGRESS_SAVED, -1))
        assertEquals(1, data.getInt(OptimizedSmsReaderWorker.PROGRESS_BLOCKED, -1))
        assertEquals(4, data.getInt(OptimizedSmsReaderWorker.PROGRESS_CURRENT_BATCH, -1))
        assertEquals(10, data.getInt(OptimizedSmsReaderWorker.PROGRESS_TOTAL_BATCHES, -1))
        assertTrue(data.keyValueMap[OptimizedSmsReaderWorker.PROGRESS_ETA_SECONDS] is Int)
        assertTrue(data.keyValueMap[OptimizedSmsReaderWorker.PROGRESS_TIME_ELAPSED] is Long)
        assertTrue(data.keyValueMap[OptimizedSmsReaderWorker.PROGRESS_ESTIMATED_TIME_REMAINING] is Long)
        assertTrue(data.keyValueMap[OptimizedSmsReaderWorker.PROGRESS_MSG_PER_SEC] is Float)
    }
}
