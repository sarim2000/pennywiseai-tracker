package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.tracker.data.preferences.ScanPreferences
import com.pennywiseai.tracker.domain.smsimport.SmsScanCounts
import com.pennywiseai.tracker.domain.smsimport.SmsScanRecord
import com.pennywiseai.tracker.domain.smsimport.SmsScanState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ScanOutcomeRecorderTest {

    private val written = mutableListOf<SmsScanRecord>()
    private val stored = MutableStateFlow<SmsScanRecord?>(null)
    private val preferences = mockk<ScanPreferences>(relaxed = true).also {
        every { it.lastScanRecord } returns stored
        coEvery { it.setLastScanRecord(any()) } answers {
            written += firstArg<SmsScanRecord>()
            stored.value = firstArg()
        }
    }
    private var now = 1_000L
    private val recorder = ScanOutcomeRecorder(preferences, clock = { now })

    private val states get() = written.map { it.state }

    private fun countedStats() = ProcessingStats(total = 3).apply {
        saved.set(2)
        duplicates.set(1)
    }

    @Test
    fun `a scan that returns is recorded running, then succeeded with its counts`() = runTest {
        val result = recorder.run(isStopped = { false }) {
            assertEquals("running is written before any work", listOf(SmsScanState.RUNNING), states)
            recorder.stats = countedStats()
            now = 5_000L
            "done"
        }

        assertEquals("done", result)
        assertEquals(listOf(SmsScanState.RUNNING, SmsScanState.SUCCEEDED), states)
        assertEquals(
            SmsScanRecord(
                SmsScanState.SUCCEEDED,
                startedAt = 1_000L,
                finishedAt = 5_000L,
                counts = SmsScanCounts(saved = 2, duplicates = 1),
                lastSucceededAt = 5_000L,
            ),
            written.last(),
        )
    }

    @Test
    fun `a scan that throws is recorded failed with what it counted, and the error goes on`() = runTest {
        val error = IOException("disk full")

        val thrown = runCatching {
            recorder.run(isStopped = { false }) {
                recorder.stats = countedStats()
                throw error
            }
        }.exceptionOrNull()

        assertSame(error, thrown)
        assertEquals(listOf(SmsScanState.RUNNING, SmsScanState.FAILED), states)
        assertEquals(SmsScanCounts(saved = 2, duplicates = 1), written.last().counts)
    }

    @Test
    fun `a failure before counting starts records no counts`() = runTest {
        runCatching { recorder.run(isStopped = { false }) { error("no inbox") } }

        assertEquals(SmsScanRecord(SmsScanState.FAILED, startedAt = 1_000L, finishedAt = 1_000L), written.last())
    }

    @Test
    fun `an error after the work was stopped is recorded cancelled`() = runTest {
        runCatching { recorder.run(isStopped = { true }) { error("cursor closed") } }

        assertEquals(listOf(SmsScanState.RUNNING, SmsScanState.CANCELLED), states)
    }

    @Test
    fun `a cancelled scan is recorded cancelled, even from a cancelled coroutine`() = runTest {
        val started = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            recorder.run(isStopped = { false }) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(listOf(SmsScanState.RUNNING, SmsScanState.CANCELLED), states)
    }

    @Test
    fun `a scan that ends early keeps the last success, so it never reads as one`() = runTest {
        recorder.run(isStopped = { false }) { now = 2_000L }
        now = 3_000L

        runCatching { recorder.run(isStopped = { false }) { error("cursor closed") } }

        assertEquals(listOf(2_000L, 2_000L, 2_000L), written.drop(1).map { it.lastSucceededAt })
        assertEquals(3_000L, written.last().finishedAt)
    }

    @Test
    fun `a scan that returns with failed saves keeps them counted and the last success`() = runTest {
        recorder.run(isStopped = { false }) { now = 2_000L }
        now = 3_000L

        recorder.run(isStopped = { false }) {
            recorder.stats = countedStats().apply { failed.set(1) }
        }

        assertEquals(SmsScanState.SUCCEEDED, written.last().state)
        assertEquals(1, written.last().counts.failed)
        assertEquals("not a clean success, so it can't be the last one", 2_000L, written.last().lastSucceededAt)
    }

    @Test
    fun `a whole-window scan that lost saves makes every next scan read the window, until one is clean`() = runTest {
        recorder.run(isStopped = { false }) {
            recorder.markFullWindow()
            recorder.stats = countedStats().apply { failed.set(1) }
        }

        // An ordinary scan started next (the receiver, Home) still owes the window...
        runCatching { recorder.run(isStopped = { false }) { error("no inbox") } }
        assertTrue(recorder.fullWindow)
        // ...and the one after that reads it, and clears the debt by finishing clean.
        recorder.run(isStopped = { false }) {
            assertTrue(recorder.fullWindow)
            recorder.stats = countedStats()
        }
        recorder.run(isStopped = { false }) {}

        assertFalse(recorder.fullWindow)
    }

    @Test
    fun `a whole-window scan that dies mid-way leaves the window owed`() = runTest {
        recorder.run(isStopped = { false }) {
            recorder.markFullWindow()
            assertTrue("the running record says so before any work", stored.value!!.owesFullWindow)
            recorder.stats = countedStats()
        }
        stored.value = written[written.size - 2] // the process died: the RUNNING record stays

        recorder.run(isStopped = { false }) {}

        assertTrue(recorder.fullWindow)
    }

    @Test
    fun `an incremental scan that fails owes no whole window`() = runTest {
        runCatching { recorder.run(isStopped = { false }) { error("no inbox") } }

        recorder.run(isStopped = { false }) {}

        assertFalse(recorder.fullWindow)
    }

    @Test
    fun `with no earlier scan there is no last success`() = runTest {
        runCatching { recorder.run(isStopped = { false }) { error("no inbox") } }

        assertEquals(listOf(null, null), written.map { it.lastSucceededAt })
    }

    @Test
    fun `a record that can't be written never fails the scan`() = runTest {
        coEvery { preferences.setLastScanRecord(any()) } throws IOException("datastore broken")

        assertEquals(42, recorder.run(isStopped = { false }) { 42 })
    }

    @Test
    fun `the record write is not swallowed when the scan is cancelled`() = runTest {
        coEvery { preferences.setLastScanRecord(any()) } throws CancellationException("stopped")

        val thrown = runCatching { recorder.run(isStopped = { false }) { 42 } }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }
}
