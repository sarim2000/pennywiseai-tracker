package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.preferences.BankAccountMergeStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SmsScanPipelineTest {

    private lateinit var h: ScanHarness

    @Before
    fun setUp() {
        h = ScanHarness()
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    /** Real dispatchers run the stages, so a deadlock has to fail instead of hanging the build. */
    private fun scanTest(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking {
        withTimeout(60_000L) { block() }
    }

    private fun List<TransactionEntity>.hashes() = map { it.transactionHash }.toSet()

    @Test
    fun `the same SMS twice in one scan books one transaction`() = scanTest {
        val sms = hdfcDebit(1)

        val stats = h.scan(listOf(sms, sms.copy(id = 2L)))

        assertEquals(1, h.store.all.size)
        assertEquals(2, stats.processed.get())
        assertEquals(2, stats.parsed.get())
        assertEquals(1, stats.saved.get())
    }

    @Test
    fun `scanning the inbox again adds nothing`() = scanTest {
        val inbox = (1..10).map(::hdfcDebit)
        h.scan(inbox)
        assertEquals(1, h.widgetRefreshes.get())

        val second = h.scan(inbox)

        assertEquals(0, second.saved.get())
        assertEquals(10, h.store.all.size)
        assertEquals("no balance applied twice", 10, h.balanceTimes.size)
        assertEquals("nothing changed, so no widget refresh", 1, h.widgetRefreshes.get())
    }

    @Test
    fun `messages read out of order are all booked, each balance at its own time`() = scanTest {
        val oldestFirst = (1..12).map(::hdfcDebit)
        val expected = oldestFirst.map {
            (h.classifier.classify(it) as ParseResult.Transaction).parsed.toEntity().transactionHash
        }.toSet()

        h.scan(oldestFirst.reversed())

        assertEquals(expected, h.store.all.hashes())
        assertEquals(h.store.all.map { it.dateTime }.sorted(), h.balanceTimes.sorted())
    }

    @Test
    fun `a transaction the user deleted stays deleted on a rescan`() = scanTest {
        val inbox = listOf(hdfcDebit(1))
        h.scan(inbox)
        // Stored under another hash, as when a parser fix changes the amount.
        h.store.updateAll { it.copy(isDeleted = true, transactionHash = "older-parser-hash") }

        val second = h.scan(inbox)

        assertEquals(0, second.saved.get())
        assertEquals(1, h.store.all.size)
        assertTrue(h.store.all.single().isDeleted)
    }

    @Test
    fun `an SMS booked under an older hash is not booked twice`() = scanTest {
        val inbox = listOf(hdfcDebit(1))
        h.scan(inbox)
        h.store.updateAll { it.copy(transactionHash = "older-parser-hash") }

        val second = h.scan(inbox)

        assertEquals(0, second.saved.get())
        assertEquals(1, h.store.all.size)
    }

    @Test
    fun `unrecognized senders are filed once at the end`() = scanTest {
        h.scan(
            listOf(
                scannedSms("AD-NEWBNK-T", "Your statement is ready", id = 1L),
                scannedSms("ZQTBNK", "Card payment EUR 12.50 at TEST SHOP", id = 2L),
                scannedSms("+10000000000", "I paid 12.50 EUR for lunch", id = 3L),
            )
        )

        assertEquals(1, h.filedUnrecognized.size)
        assertEquals(setOf("AD-NEWBNK-T", "ZQTBNK"), h.filedUnrecognized.single().map { it.sender }.toSet())
    }

    @Test
    fun `more than a batch of unrecognized SMS is filed in batches of 50`() = scanTest {
        h.scan((1..120).map { scannedSms("AD-NEWBNK-T", "Notice number $it", id = it.toLong()) })

        assertEquals(listOf(50, 50, 20), h.filedUnrecognized.map { it.size })
    }

    @Test
    fun `a taught message is offered again unless that very SMS is booked`() = scanTest {
        val booked = scannedSms("NEWBNK", "Paid SAR 50.00 at Juice Bar", id = 1L)
        val offered = scannedSms("NEWBNK", "Paid SAR 60.00 at Juice Bar", id = 2L)
        coEvery { h.transactions.getAnyBySmsAround(booked.body, any(), any(), any()) } returns transactionRow(1L)

        h.scan(
            listOf(booked, offered),
            classify = { ParseResult.StoreUnrecognized(it) },
            isTaught = { true },
        )

        coVerify(exactly = 1) { h.unrecognized.insertOrRevive(match { it.smsBody == offered.body }) }
        coVerify(exactly = 0) { h.unrecognized.insertOrRevive(match { it.smsBody == booked.body }) }
    }

    @Test
    fun `a parser that throws drops only that message`() = scanTest {
        val inbox = (1..6).map(::hdfcDebit)

        val stats = h.scan(
            inbox,
            classify = { sms -> if (sms.id == 3L) error("parser bug") else h.classifier.classify(sms) },
        )

        assertEquals(6, stats.processed.get())
        assertEquals(5, stats.parsed.get())
        assertEquals(5, h.store.all.size)
    }

    @Test
    fun `a failed save skips only that message`() = scanTest {
        val inbox = (1..6).map(::hdfcDebit)
        val failing = inbox[2].body
        h.store.beforeInsert = { row -> check(row.smsBody != failing) { "disk full" } }

        val stats = h.scan(inbox)

        assertEquals(6, stats.processed.get())
        assertEquals(5, stats.saved.get())
        assertEquals(5, h.store.all.size)
    }

    @Test
    fun `a balance that fails to save keeps the transaction`() = scanTest {
        coEvery { h.balances.insertSmsTransactionBalance(any(), any(), any(), any(), any()) } throws
            IllegalStateException("database is locked")

        val stats = h.scan((1..3).map(::hdfcDebit))

        assertEquals(3, stats.saved.get())
        assertEquals(3, h.store.all.size)
    }

    @Test
    fun `cancelling mid-scan then scanning again books every message exactly once`() = scanTest {
        val inbox = (1..20).map(::hdfcDebit)
        val inserts = AtomicInteger(0)
        val job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            h.pipeline(ListSmsSource(inbox)).run(0L, ProcessingStats(total = inbox.size))
        }
        // Room throws out of a transaction that gets cancelled, and the row rolls back.
        h.store.beforeInsert = {
            if (inserts.incrementAndGet() == 5) {
                job.cancel()
                throw CancellationException("work stopped")
            }
        }

        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse("the save lock must be released", BankAccountMergeStore.mutationMutex.isLocked)
        assertEquals("an interrupted scan refreshes nothing", 0, h.widgetRefreshes.get())
        assertEquals("the interrupted insert left no row", 4, h.store.all.size)

        h.store.beforeInsert = {}
        val second = h.scan(inbox)

        assertEquals(20, h.store.all.size)
        assertEquals(20, h.store.all.map { it.smsBody }.toSet().size)
        assertEquals(16, second.saved.get())
    }

    @Test
    fun `the feed stays bounded while saving is blocked`() = scanTest {
        val inbox = (1..5000).map(::hdfcDebit)
        val source = ListSmsSource(inbox)
        val parsers = 3
        val saving = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        h.store.beforeInsert = {
            saving.complete(Unit)
            release.await()
        }
        val job = launch(Dispatchers.Default) {
            h.pipeline(source, parserConcurrency = parsers).run(0L, ProcessingStats(total = inbox.size))
        }

        saving.await()
        while (source.sent.get() < SmsScanPipeline.PARSE_CHANNEL_CAPACITY) delay(10)
        delay(300)
        val sent = source.sent.get()

        job.cancel()
        release.complete(Unit)
        job.join()
        // The feed buffer, the result buffer, one held by each parser and the one being saved.
        val bound = SmsScanPipeline.PARSE_CHANNEL_CAPACITY + SmsScanPipeline.RESULT_CHANNEL_CAPACITY + parsers + 2
        assertTrue("sent $sent messages past a blocked saver (bound $bound)", sent <= bound)
    }

    @Test
    fun `progress ends at the total`() = scanTest {
        val inbox = (1..30).map(::hdfcDebit)
        var last = -1

        h.scan(inbox, onProgress = { last = it.processed.get() })

        assertEquals(inbox.size, last)
    }
}
