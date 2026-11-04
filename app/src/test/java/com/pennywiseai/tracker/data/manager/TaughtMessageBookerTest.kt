package com.pennywiseai.tracker.data.manager

import com.pennywiseai.parser.core.MessageTemplates
import com.pennywiseai.parser.core.TeachSelection
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.entity.UnrecognizedSmsEntity
import com.pennywiseai.tracker.data.preferences.LearnedSendersStore
import com.pennywiseai.tracker.data.preferences.TaughtTemplatesStore
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/** Made-up messages: what happens to a taught message when it's read and confirmed (#279). */
class TaughtMessageBookerTest {
    private val sender = "AD-ZQTBNK"
    private val sample = "Paid SAR 50.00 at Juice Bar"
    private val later = "Paid SAR 75.00 at City Cafe"

    private val processor = mockk<SmsTransactionProcessor>()
    private val store = mockk<TaughtTemplatesStore>()
    private val learned = mockk<LearnedSendersStore>()
    private val waiting = mockk<UnrecognizedSmsRepository>(relaxed = true)
    private val cards = mockk<CardRepository>()
    private val booker = TaughtMessageBooker(processor, store, learned, waiting, cards)

    private val template = MessageTemplates.build(
        id = "t", senderKey = "ZQTBNK", body = sample,
        selection = TeachSelection(
            amount = sample.indexOf("50.00") until sample.indexOf("50.00") + 5,
            merchant = sample.indexOf("Juice Bar") until sample.length
        ),
        type = TransactionType.EXPENSE, currency = "SAR", bankName = "Zqt Bank"
    )!!

    @Before
    fun setUp() {
        every { store.forSender(any()) } returns listOf(template)
        every { learned.accountFor(any()) } returns null
        coEvery { cards.getCard(any(), any()) } returns null
    }

    @Test
    fun `reads a later message of the taught shape`() {
        val parsed = booker.read(sender, later, 1L)!!
        assertEquals("City Cafe", parsed.merchant)
        assertEquals("SAR", parsed.currency)
        assertEquals(later, parsed.smsBody)
    }

    @Test
    fun `a bank parser that reads the message wins`() {
        // "HDFCBK" has a real parser, and this is a message it books.
        val body = "Rs.500.00 debited from HDFC Bank A/c XX1234 on 12-10-26 to VPA shop@upi. Ref 123456789012."
        val hdfc = "VM-HDFCBK"
        val parser = com.pennywiseai.parser.core.bank.BankParserFactory.getParsers(hdfc).firstOrNull()
        assertNotNull("expected a parser for $hdfc", parser)
        org.junit.Assume.assumeNotNull(parser!!.parse(body, hdfc, 1L))
        val shape = MessageTemplates.build(
            id = "h", senderKey = "HDFCBK", body = body,
            selection = TeachSelection(amount = body.indexOf("500.00") until body.indexOf("500.00") + 6),
            type = TransactionType.EXPENSE, currency = "INR", bankName = "HDFC"
        )!!
        every { store.forSender(hdfc) } returns listOf(shape)
        assertNull(booker.read(hdfc, body, 1L))
        assertFalse(booker.formatChanged(hdfc, body, 1L))
    }

    @Test
    fun `the learned account fills in when the message names none`() {
        every { learned.accountFor(sender) } returns LearnedSendersStore.Account("My Bank", "4321", "SAR")
        val parsed = booker.read(sender, later, 1L)!!
        assertEquals("My Bank", parsed.bankName)
        assertEquals("4321", parsed.accountLast4)
    }

    @Test
    fun `a learned account in another currency stays out`() {
        every { learned.accountFor(sender) } returns LearnedSendersStore.Account("My Bank", "4321", "USD")
        val parsed = booker.read(sender, later, 1L)!!
        assertEquals("Zqt Bank", parsed.bankName)
        assertNull(parsed.accountLast4)
    }

    @Test
    fun `new money wording from a taught sender means its format changed`() {
        assertTrue(booker.formatChanged(sender, "You spent SAR 75.00 today", 1L))
        assertFalse("the shape still fits", booker.formatChanged(sender, later, 1L))
        assertFalse("not about money", booker.formatChanged(sender, "Your parcel has arrived", 1L))
        every { store.forSender(any()) } returns emptyList()
        assertFalse("nothing was taught", booker.formatChanged(sender, "You spent SAR 75.00 today", 1L))
    }

    @Test
    fun `booking goes through the shared save path with the SMS as its identity`() = runBlocking {
        coEvery { processor.saveParsedTransaction(any(), any(), any()) } returns
            SmsTransactionProcessor.ProcessingResult(true, transactionId = 7)
        val parsed = booker.read(sender, later, 1L)!!
        assertEquals(TaughtMessageBooker.Outcome.SAVED, booker.book(parsed))
        coVerify {
            processor.saveParsedTransaction(
                // The prefix is what keeps the row through "Rebuild from scratch".
                match { it.smsBody == later && it.sender == sender && it.transactionHash!!.startsWith("taught_") },
                later,
                any()
            )
        }
        coVerify { waiting.settle(sender, later, any()) }
    }

    @Test
    fun `the same words on a later day are a new payment, the same SMS read again is not`() = runBlocking {
        val hashes = mutableListOf<String>()
        coEvery { processor.saveParsedTransaction(any(), any(), any()) } answers {
            hashes += firstArg<com.pennywiseai.parser.core.ParsedTransaction>().transactionHash!!
            assertEquals("identity is the text and the time", UnrecognizedSmsRepository.REDELIVERY_WINDOW, thirdArg<java.time.Duration?>())
            SmsTransactionProcessor.ProcessingResult(true, transactionId = 7)
        }
        booker.book(booker.read(sender, later, 1_000L)!!)
        booker.book(booker.read(sender, later, 90_000_000L)!!)
        booker.book(booker.read(sender, later, 1_000L)!!)
        assertEquals("a later day gets its own identity", 2, hashes.toSet().size)
        assertEquals("the same event keeps its identity, so the hash check catches it", hashes[0], hashes[2])
    }

    @Test
    fun `digits of a known card book as that card, so its linked account moves`() = runBlocking {
        val taught = MessageTemplates.build(
            id = "c", senderKey = "ZQTBNK", body = "Paid SAR 50.00 card 1234 now",
            selection = TeachSelection(
                amount = 9..13,
                account = "Paid SAR 50.00 card 1234 now".indexOf("1234").let { it until it + 4 }
            ),
            type = TransactionType.EXPENSE, currency = "SAR", bankName = "Zqt Bank"
        )!!
        every { store.forSender(any()) } returns listOf(taught)
        val booked = mutableListOf<Boolean>()
        coEvery { processor.saveParsedTransaction(any(), any(), any()) } answers {
            booked += firstArg<com.pennywiseai.parser.core.ParsedTransaction>().isFromCard
            SmsTransactionProcessor.ProcessingResult(true, transactionId = 7)
        }
        booker.book(booker.read(sender, "Paid SAR 7.00 card 1234 now", 1L)!!)
        coEvery { cards.getCard("Zqt Bank", "1234") } returns mockk(relaxed = true)
        booker.book(booker.read(sender, "Paid SAR 8.00 card 1234 now", 2L)!!)
        assertEquals(listOf(false, true), booked)
    }

    @Test
    fun `a duplicate or a blocked message is settled, not retried`() = runBlocking {
        coEvery { processor.saveParsedTransaction(any(), any(), any()) } returns
            SmsTransactionProcessor.ProcessingResult(false, reason = "Duplicate transaction")
        assertEquals(TaughtMessageBooker.Outcome.SETTLED, booker.book(booker.read(sender, later, 1L)!!))
        coVerify { waiting.settle(sender, later, any()) }
    }

    @Test
    fun `a failed save leaves the message waiting`() = runBlocking {
        coEvery { processor.saveParsedTransaction(any(), any(), any()) } returns
            SmsTransactionProcessor.ProcessingResult(false, reason = "disk full", failed = true)
        assertEquals(TaughtMessageBooker.Outcome.FAILED, booker.book(booker.read(sender, later, 1L)!!))
        coVerify(exactly = 0) { waiting.settle(any(), any(), any()) }
    }

    @Test
    fun `a message picked from the inbox that fails to book waits for Add`() = runBlocking {
        coEvery { processor.saveParsedTransaction(any(), any(), any()) } returns
            SmsTransactionProcessor.ProcessingResult(false, reason = "disk full", failed = true)
        assertEquals(TaughtMessageBooker.Outcome.FAILED, booker.bookMessage(sender, later, 1L))
        coVerify { waiting.insertOrRevive(match { it.sender == sender && it.smsBody == later }) }
    }

    @Test
    fun `a database error fails the booking instead of crashing, and the message still waits`() = runBlocking {
        coEvery { cards.getCard(any(), any()) } throws IllegalStateException("closed")
        val withCard = "Paid SAR 8.00 card 1234 now"
        every { store.forSender(any()) } returns listOf(
            MessageTemplates.build(
                id = "c", senderKey = "ZQTBNK", body = withCard,
                selection = TeachSelection(
                    amount = 9..12,
                    account = withCard.indexOf("1234").let { it until it + 4 }
                ),
                type = TransactionType.EXPENSE, currency = "SAR", bankName = "Zqt Bank"
            )!!
        )
        assertEquals(TaughtMessageBooker.Outcome.FAILED, booker.bookMessage(sender, withCard, 1L))
        coVerify { waiting.insertOrRevive(any()) }
    }

    @Test
    fun `a booked message leaves no waiting row`() = runBlocking {
        coEvery { processor.saveParsedTransaction(any(), any(), any()) } returns
            SmsTransactionProcessor.ProcessingResult(true, transactionId = 7)
        assertEquals(TaughtMessageBooker.Outcome.SAVED, booker.bookMessage(sender, later, 1L))
        coVerify(exactly = 0) { waiting.insertOrRevive(any()) }
    }

    @Test
    fun `tapping Add twice books once`() = runBlocking {
        coEvery { waiting.getById(5) } returns UnrecognizedSmsEntity(
            id = 5, sender = sender, smsBody = later, receivedAt = LocalDateTime.of(2026, 10, 12, 9, 0), isDeleted = true
        )
        assertEquals(TaughtMessageBooker.Outcome.SETTLED, booker.bookWaiting(5))
        coVerify(exactly = 0) { processor.saveParsedTransaction(any(), any(), any()) }
    }

    @Test
    fun `a waiting message no template reads any more is not booked`() = runBlocking {
        coEvery { waiting.getById(5) } returns UnrecognizedSmsEntity(
            id = 5, sender = sender, smsBody = later, receivedAt = LocalDateTime.of(2026, 10, 12, 9, 0)
        )
        every { store.forSender(any()) } returns emptyList()
        assertEquals(TaughtMessageBooker.Outcome.FAILED, booker.bookWaiting(5))
        coVerify(exactly = 0) { processor.saveParsedTransaction(any(), any(), any()) }
    }
}
