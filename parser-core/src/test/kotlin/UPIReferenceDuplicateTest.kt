package com.pennywiseai.parser.core

import com.pennywiseai.parser.core.bank.JKBankParser
import com.pennywiseai.parser.core.bank.SBIBankParser
import com.pennywiseai.parser.core.bank.SouthIndianBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.tracker.data.manager.UPIDeduplicator
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class UPIReferenceDuplicateTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // Parser correctness — @TestFactory suites (unchanged, these were fine)
    // ─────────────────────────────────────────────────────────────────────────────

    @TestFactory
    fun `SBI parser extracts reference from UPI credit SMS`(): List<DynamicTest> {
        val parser = SBIBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "SBI UPI credit - account 4821 - Rahul Kumar",
                message = "Dear SBI User, your A/c X4821-credited by Rs.25730 on 160ct25 transfer from RAHUL KUMAR Ref No 528902998134 -SBI",
                sender = "SBIBK",
                expected = ExpectedTransaction(
                    amount       = BigDecimal("25730"),
                    currency     = "INR",
                    type         = TransactionType.INCOME,
                    merchant     = "RAHUL KUMAR",
                    accountLast4 = "4821",
                    reference    = "528902998134"
                )
            ),
            ParserTestCase(
                name = "SBI UPI credit - account 7377 - ShamAkram",
                message = "Dear SBI User, your A/c X7377-credited by Rs.15000 on 26Dec25 transfer from ShamAkram Ref No 190200588907 -SBI",
                sender = "SBIBK",
                expected = ExpectedTransaction(
                    amount       = BigDecimal("15000"),
                    currency     = "INR",
                    type         = TransactionType.INCOME,
                    merchant     = "ShamAkram",
                    accountLast4 = "7377",
                    reference    = "190200588907"
                )
            )
        )

        return ParserTestUtils.runTestSuite(
            parser    = parser,
            testCases = testCases,
            suiteName = "SBI UPI Reference Extraction"
        )
    }

    @TestFactory
    fun `South Indian Bank parser extracts reference from UPI credit SMS`(): List<DynamicTest> {
        val parser = SouthIndianBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "South Indian Bank UPI credit - account 4821 - RahulKU",
                message = "UPI Credit:INR Rs.25730.00 in A/c X4821. Info: UPI/ICICI/528908998134/ RAHULKU on 16-10-25 12:11:27. Final balance is Rs.29990.75 -South Indian Bank",
                sender = "SOUTHINDIANBANK",
                expected = ExpectedTransaction(
                    amount       = BigDecimal("25730.00"),
                    currency     = "INR",
                    type         = TransactionType.INCOME,
                    accountLast4 = "4821",
                    balance      = BigDecimal("29990.75"),
                    reference    = "528908998134"
                )
            ),
            ParserTestCase(
                name = "South Indian Bank UPI credit - account 7377 - Sham Ak",
                message = "UPI Credit:INR Rs.15000.00 in A/c X7377. Info: UPI/TGRB/190200588907/ Sham Ak on 26-12-25 19:02:01.Final balance is Rs.34567.67 -South Indian Bank",
                sender = "SIBSMS",
                expected = ExpectedTransaction(
                    amount       = BigDecimal("15000.00"),
                    currency     = "INR",
                    type         = TransactionType.INCOME,
                    accountLast4 = "7377",
                    balance      = BigDecimal("34567.67"),
                    reference    = "190200588907"
                )
            )
        )

        return ParserTestUtils.runTestSuite(
            parser    = parser,
            testCases = testCases,
            suiteName = "South Indian Bank UPI Reference Extraction"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Deduplicator tests — real UPIDeduplicator + mocked repository
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * SBI ref 528902998134 vs SIB ref 528908998134 — references differ,
     * so Check 2 cannot catch it. Check 3 (account+amount+time) must fire.
     */
    @Test
    fun `Deduplicator - different references are caught by account+amount+time fallback`() = runBlocking {
        val repo         = mockk<TransactionRepository>()
        val deduplicator = UPIDeduplicator(repo)
        val timestamp    = System.currentTimeMillis()

        val sbiParsed = SBIBankParser().parse(
            "Dear SBI User, your A/c X4821-credited by Rs.25730 on 160ct25 transfer from RAHUL KUMAR Ref No 528902998134 -SBI",
            "SBIBK", timestamp
        )!!
        val sibParsed = SouthIndianBankParser().parse(
            "UPI Credit:INR Rs.25730.00 in A/c X4821. Info: UPI/ICICI/528908998134/ RAHULKU on 16-10-25 12:11:27. Final balance is Rs.29990.75 -South Indian Bank",
            "SOUTHINDIANBANK", timestamp
        )!!

        // Parser correctness
        assertEquals("4821",         sbiParsed.accountLast4)
        assertEquals("4821",         sibParsed.accountLast4)
        assertEquals(0, BigDecimal("25730").compareTo(sbiParsed.amount))
        assertEquals(0, BigDecimal("25730").compareTo(sibParsed.amount))
        assertEquals(TransactionType.INCOME, sbiParsed.type)
        assertEquals(TransactionType.INCOME, sibParsed.type)
        assertEquals("528902998134", sbiParsed.reference)
        assertEquals("528908998134", sibParsed.reference)
        assertNotEquals(sbiParsed.reference, sibParsed.reference,
            "References must differ so Check 2 cannot fire")

        // SBI SMS arrives first — no duplicates, save it
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) } returns null
        coEvery { repo.insertTransaction(any()) } returns Unit

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.NotDuplicate::class.java,
            deduplicator.checkDuplicateAndSave(sbiParsed, timestamp),
            "SBI transaction should be saved as first arrival"
        )
        coVerify(exactly = 1) { repo.insertTransaction(any()) }

        // SIB SMS: different hash, different ref — same account+amount+type within window
        val savedEntity = sbiParsed.toEntity()
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime("4821", sbiParsed.amount, any(), any(), any()) } returns savedEntity

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.Duplicate::class.java,
            deduplicator.checkDuplicateAndSave(sibParsed, timestamp),
            "SIB transaction must be caught as duplicate via account+amount+time"
        )
        coVerify(exactly = 1) { repo.insertTransaction(any()) } // still only one insert
    }

    /**
     * Both SBI and SIB carry the SAME reference 190200588907.
     * Check 2 (reference match) must catch it — Check 3 must never be reached.
     */
    @Test
    fun `Deduplicator - same reference is caught by reference check`() = runBlocking {
        val repo         = mockk<TransactionRepository>()
        val deduplicator = UPIDeduplicator(repo)
        val timestamp    = System.currentTimeMillis()

        val sbiParsed = SBIBankParser().parse(
            "Dear SBI User, your A/c X7377-credited by Rs.15000 on 26Dec25 transfer from ShamAkram Ref No 190200588907 -SBI",
            "SBIBK", timestamp
        )!!
        val sibParsed = SouthIndianBankParser().parse(
            "UPI Credit:INR Rs.15000.00 in A/c X7377. Info: UPI/TGRB/190200588907/ Sham Ak on 26-12-25 19:02:01.Final balance is Rs.34567.67 -South Indian Bank",
            "SIBSMS", timestamp
        )!!

        // Parser correctness
        assertEquals("7377", sbiParsed.accountLast4)
        assertEquals("7377", sibParsed.accountLast4)
        assertEquals(0, BigDecimal("15000").compareTo(sbiParsed.amount))
        assertEquals(0, BigDecimal("15000").compareTo(sibParsed.amount))
        assertEquals("190200588907", sbiParsed.reference)
        assertEquals("190200588907", sibParsed.reference)
        assertEquals(sbiParsed.reference, sibParsed.reference,
            "References must match so Check 2 fires")

        // SBI SMS first — save
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) } returns null
        coEvery { repo.insertTransaction(any()) } returns Unit

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.NotDuplicate::class.java,
            deduplicator.checkDuplicateAndSave(sbiParsed, timestamp)
        )

        // SIB SMS: different hash, same reference → Check 2 fires
        val savedEntity = sbiParsed.toEntity()
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference("190200588907", sbiParsed.amount, any(), any()) } returns savedEntity

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.Duplicate::class.java,
            deduplicator.checkDuplicateAndSave(sibParsed, timestamp),
            "Must be caught at Check 2 (same reference)"
        )

        // Check 3 must NEVER have been called
        coVerify(exactly = 0) { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { repo.insertTransaction(any()) }
    }

    /**
     * Exact same SMS body received twice (SMS delivery retry).
     * Check 1 (generateTransactionId hash) must catch it —
     * Check 2 and 3 must never be reached.
     */
    @Test
    fun `Deduplicator - exact duplicate SMS is caught by hash check`() = runBlocking {
        val repo         = mockk<TransactionRepository>()
        val deduplicator = UPIDeduplicator(repo)
        val timestamp    = System.currentTimeMillis()

        val parsed = SBIBankParser().parse(
            "Dear SBI User, your A/c X4821-credited by Rs.25730 on 160ct25 transfer from RAHUL KUMAR Ref No 528902998134 -SBI",
            "SBIBK", timestamp
        )!!

        // First arrival — save
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) } returns null
        coEvery { repo.insertTransaction(any()) } returns Unit

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.NotDuplicate::class.java,
            deduplicator.checkDuplicateAndSave(parsed, timestamp)
        )

        // Same SMS again — repo returns the saved entity for this hash
        val savedEntity = parsed.toEntity()
        coEvery { repo.getTransactionByHash(parsed.generateTransactionId()) } returns savedEntity

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.Duplicate::class.java,
            deduplicator.checkDuplicateAndSave(parsed, timestamp),
            "Retried SMS must be caught at Check 1 (hash)"
        )

        // Check 2 and 3 must never run
        coVerify(exactly = 0) { repo.getTransactionByReference(any(), any(), any(), any()) }
        coVerify(exactly = 0) { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { repo.insertTransaction(any()) }
    }

    /**
     * Previously deleted transaction must not be re-saved.
     */
    @Test
    fun `Deduplicator - previously deleted transaction is blocked`() = runBlocking {
        val repo         = mockk<TransactionRepository>()
        val deduplicator = UPIDeduplicator(repo)
        val timestamp    = System.currentTimeMillis()

        val parsed = SBIBankParser().parse(
            "Dear SBI User, your A/c X4821-credited by Rs.25730 on 160ct25 transfer from RAHUL KUMAR Ref No 528902998134 -SBI",
            "SBIBK", timestamp
        )!!

        val deletedEntity = parsed.toEntity().copy(isDeleted = true)
        coEvery { repo.getTransactionByHash(parsed.generateTransactionId()) } returns deletedEntity

        val result = deduplicator.checkDuplicateAndSave(parsed, timestamp)
        assertInstanceOf(UPIDeduplicator.DeduplicationResult.Duplicate::class.java, result)
        assertTrue(
            (result as UPIDeduplicator.DeduplicationResult.Duplicate).reason.contains("deleted"),
            "Reason should mention deletion"
        )
        coVerify(exactly = 0) { repo.insertTransaction(any()) }
    }

    /**
     * Same account and amount but 20 minutes apart — outside the 10-minute window.
     * Must NOT be flagged as duplicate.
     */
    @Test
    fun `Deduplicator - same account and amount outside time window is NOT a duplicate`() = runBlocking {
        val repo            = mockk<TransactionRepository>()
        val deduplicator    = UPIDeduplicator(repo)
        val firstTimestamp  = System.currentTimeMillis()
        val secondTimestamp = firstTimestamp + (20 * 60 * 1000L)

        val parsed1 = SBIBankParser().parse(
            "Dear SBI User, your A/c X4821-credited by Rs.25730 on 160ct25 transfer from RAHUL KUMAR Ref No 528902998134 -SBI",
            "SBIBK", firstTimestamp
        )!!
        val parsed2 = SBIBankParser().parse(
            "Dear SBI User, your A/c X4821-credited by Rs.25730 on 170ct25 transfer from RAHUL KUMAR Ref No 528999001234 -SBI",
            "SBIBK", secondTimestamp
        )!!

        // Repo always returns null — nothing in window for either call
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) } returns null
        coEvery { repo.insertTransaction(any()) } returns Unit

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.NotDuplicate::class.java,
            deduplicator.checkDuplicateAndSave(parsed1, firstTimestamp)
        )
        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.NotDuplicate::class.java,
            deduplicator.checkDuplicateAndSave(parsed2, secondTimestamp),
            "Transaction 20 minutes later must NOT be deduplicated"
        )
        coVerify(exactly = 2) { repo.insertTransaction(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // JK Bank
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `JK Bank parser - all IMPS SMS variants parse correctly`() {
        val parser    = JKBankParser()
        val timestamp = System.currentTimeMillis()

        val sms1 = "A/C XXXX9651 Credited by INR 5000 at 09:02 by mTFTR/IMPSPSP2AI/IMPSINWARD/5. A/C Bal is INR 6046.22 Cr, Available Bal is INR 6046.22 Cr JK BANK."
        val sms3 = "IMPS Fund transfer of Rs.5000.00 successfully credited to your A/C XXXX9651 with RRN No.536565960299984 dated 31-12-2025. Amt received from ZERODHA BROKING LIMITED DSCNCNB having A/C No.XXXXXXX0164. JK BANK"

        val p1 = parser.parse(sms1, "JKBANK", timestamp)
        val p3 = parser.parse(sms3, "JKBANK", timestamp)

        assertNotNull(p1, "Short IMPS SMS should parse")
        assertNotNull(p3, "Detailed IMPS SMS with RRN should parse")

        assertEquals("9651", p1!!.accountLast4)
        assertEquals("9651", p3!!.accountLast4)
        assertEquals(0, BigDecimal("5000").compareTo(p1.amount))
        assertEquals(0, BigDecimal("5000").compareTo(p3.amount))
        assertEquals(TransactionType.INCOME, p1.type)
        assertEquals(TransactionType.INCOME, p3.type)
        assertEquals("536565960299984", p3.reference, "Detailed SMS must carry RRN")
    }

    @Test
    fun `JK Bank - exact duplicate SMS is caught by hash`() = runBlocking {
        val repo         = mockk<TransactionRepository>()
        val deduplicator = UPIDeduplicator(repo)
        val parser       = JKBankParser()
        val timestamp    = System.currentTimeMillis()

        val sms    = "A/C XXXX9651 Credited by INR 5000 at 09:02 by mTFTR/IMPSPSP2AI/IMPSINWARD/5. A/C Bal is INR 6046.22 Cr, Available Bal is INR 6046.22 Cr JK BANK."
        val parsed = parser.parse(sms, "JKBANK", timestamp)!!

        // Verify same SMS body always yields same transaction ID
        val parsed2 = parser.parse(sms, "JKBANK", timestamp)!!
        assertEquals(
            parsed.generateTransactionId(),
            parsed2.generateTransactionId(),
            "Same SMS body must produce same transaction ID"
        )

        // First arrival — save
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) } returns null
        coEvery { repo.insertTransaction(any()) } returns Unit

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.NotDuplicate::class.java,
            deduplicator.checkDuplicateAndSave(parsed, timestamp)
        )

        // Same SMS again
        val savedEntity = parsed.toEntity()
        coEvery { repo.getTransactionByHash(parsed.generateTransactionId()) } returns savedEntity

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.Duplicate::class.java,
            deduplicator.checkDuplicateAndSave(parsed, timestamp),
            "Retried SMS delivery must be caught by hash"
        )

        coVerify(exactly = 0) { repo.getTransactionByReference(any(), any(), any(), any()) }
        coVerify(exactly = 1) { repo.insertTransaction(any()) }
    }

    @Test
    fun `JK Bank - short IMPS SMS and detailed RRN SMS caught by account+amount+time`() = runBlocking {
        val repo         = mockk<TransactionRepository>()
        val deduplicator = UPIDeduplicator(repo)
        val parser       = JKBankParser()
        val timestamp    = System.currentTimeMillis()

        val sms1 = "A/C XXXX9651 Credited by INR 5000 at 09:02 by mTFTR/IMPSPSP2AI/IMPSINWARD/5. A/C Bal is INR 6046.22 Cr, Available Bal is INR 6046.22 Cr JK BANK."
        val sms3 = "IMPS Fund transfer of Rs.5000.00 successfully credited to your A/C XXXX9651 with RRN No.536565960299984 dated 31-12-2025. Amt received from ZERODHA BROKING LIMITED DSCNCNB having A/C No.XXXXXXX0164. JK BANK"

        val p1 = parser.parse(sms1, "JKBANK", timestamp)!!
        val p3 = parser.parse(sms3, "JKBANK", timestamp)!!

        // Short SMS saves first
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime(any(), any(), any(), any(), any()) } returns null
        coEvery { repo.insertTransaction(any()) } returns Unit

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.NotDuplicate::class.java,
            deduplicator.checkDuplicateAndSave(p1, timestamp)
        )

        // Detailed SMS: different hash, short SMS has no reference so Check 2 skipped,
        // Check 3 catches via account+amount+time
        val savedEntity = p1.toEntity()
        coEvery { repo.getTransactionByHash(any()) } returns null
        coEvery { repo.getTransactionByReference(any(), any(), any(), any()) } returns null
        coEvery { repo.getTransactionByAccountAmountTime("9651", p1.amount, any(), any(), any()) } returns savedEntity

        assertInstanceOf(
            UPIDeduplicator.DeduplicationResult.Duplicate::class.java,
            deduplicator.checkDuplicateAndSave(p3, timestamp),
            "Detailed RRN SMS must be caught as duplicate of short IMPS SMS via account+amount+time"
        )

        coVerify(exactly = 1) { repo.insertTransaction(any()) }
    }
}