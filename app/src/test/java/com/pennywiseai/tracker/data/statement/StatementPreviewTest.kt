package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType as ParsedType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** The preview (#13) must say exactly what the import then does. */
class StatementPreviewTest {

    private val day = LocalDate.of(2025, 1, 2)
    private val account = StatementAccount("Sample Bank", "1234", "INR")

    private val csv = """
        Date,Narration,Debit,Credit,Balance
        02/01/2025,UPI/512345678901/GROCERY MART,450.00,,9550.00
        02/01/2025,COFFEE,120.00,,9430.00
        03/01/2025,SALARY,,50000.00,59430.00
        04/01/2025,RENT,15000.00,,44430.00
        04/01/2025,TEA,20.00,,44410.00
    """.trimIndent()

    private fun statement(): List<ParsedTransaction> {
        val rows = StatementTableParser.readCsv(csv)
        return StatementTableParser.parse(rows, StatementTableParser.guessMapping(rows), account).transactions
    }

    private fun seededStore() = InMemoryStore().apply {
        // An SMS for the grocery row with the same UPI reference (matches by reference)…
        seed(sms(BigDecimal("450.00"), TransactionType.EXPENSE, day, reference = "512345678901", merchant = "Grocery Mart"))
        // …and one for the salary with no reference (matches by amount + date).
        seed(sms(BigDecimal("50000"), TransactionType.INCOME, day.plusDays(1), merchant = "Employer"))
    }

    @Test
    fun `preview counts equal what the import writes`() = runBlocking {
        val parsed = statement()

        val previewStore = seededStore()
        val plan = StatementImportProcessor(previewStore).plan(parsed)
        assertEquals(2, plan.matchedCount)
        assertEquals(3, plan.newIndices.size)
        assertTrue("planning writes nothing", previewStore.writes == 0)

        val importStore = seededStore()
        val result = StatementImportProcessor(importStore).process(parsed)
        assertEquals(plan.newIndices.size, result.imported)
        assertEquals(plan.matchedCount, result.skippedDuplicates + result.enriched)
        assertEquals(plan.rows.size, result.totalParsed)

        // Applying the previewed plan writes the same rows a direct import does.
        StatementImportProcessor(previewStore).apply(plan)
        assertEquals(importStore.rows.map { it.transactionHash }.toSet(), previewStore.rows.map { it.transactionHash }.toSet())
    }

    @Test
    fun `unticked rows are not written and a re-import matches everything`() = runBlocking {
        val parsed = statement()
        val store = seededStore()
        val processor = StatementImportProcessor(store)
        val plan = processor.plan(parsed)
        val untick = plan.newIndices.first()

        val result = processor.apply(plan, excluded = setOf(untick))
        assertEquals(plan.newIndices.size - 1, result.imported)
        assertFalse(store.rows.any { it.transactionHash == plan.rows[untick].parsed.generateTransactionId() })

        val again = processor.plan(parsed)
        // Everything imported is matched; only the unticked row is still new.
        assertEquals(listOf(untick), again.newIndices)
    }

    @Test
    fun `an SMS saved while the preview is open is matched when the import re-plans`() = runBlocking {
        val parsed = statement()
        val store = seededStore()
        val processor = StatementImportProcessor(store)
        val preview = processor.plan(parsed)
        val rent = parsed.indexOfFirst { it.merchant == "RENT" }
        assertTrue(rent in preview.newIndices)

        // The bank's SMS for the rent lands before the user taps Import.
        store.seed(sms(BigDecimal("15000"), TransactionType.EXPENSE, day.plusDays(2), merchant = "Landlord"))
        val keep = preview.newIndices.toSet()
        val atCommit = processor.plan(parsed)
        val result = processor.apply(atCommit, excluded = atCommit.rows.indices.toSet() - keep)

        assertEquals(preview.newIndices.size - 1, result.imported)
        assertEquals(1, store.rows.count { it.amount.compareTo(BigDecimal("15000")) == 0 })
    }

    private fun table(csv: String): List<ParsedTransaction> {
        val rows = StatementTableParser.readCsv(csv.trimIndent())
        return StatementTableParser.parse(rows, StatementTableParser.guessMapping(rows), account).transactions
    }

    @Test
    fun `a reference matches an SMS stamped a day after the statement's date`() = runBlocking {
        // The SMS arrived on the 3rd; the statement books the payment on the 2nd.
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("450.00"), TransactionType.EXPENSE, day.plusDays(1), reference = "612345678901", merchant = "Grocery Mart"))
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day.plusDays(1), reference = "612345678902", merchant = "Cafe"))
        }
        val parsed = table(
            """
            Txn Date,Narration,Chq/Ref No,Debit,Credit,Balance
            02/01/2025,UPI/612345678901/GROCERY MART,612345678901,450.00,,9550.00
            02/01/2025,COFFEE,612345678902,120.00,,9430.00
            02/01/2025,SMS CHARGES,,59.00,,9371.00
            """
        )
        assertEquals("612345678902", parsed[1].reference) // from the Reference column
        val plan = StatementImportProcessor(store).plan(parsed)
        assertEquals(listOf(2), plan.newIndices)
        assertEquals(2, plan.count(StatementRowDecision.SkipReason.REFERENCE))
    }

    @Test
    fun `without a reference, an amount match may sit a day off in the same direction`() = runBlocking {
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day.plusDays(1), merchant = "Cafe"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Debit,Credit
                02/01/2025,COFFEE,120.00,
                """
            )
        )
        assertTrue(plan.newIndices.isEmpty())
        assertEquals(1, plan.count(StatementRowDecision.SkipReason.AMOUNT_DATE))
    }

    @Test
    fun `a run of rows whose SMS all lag a day matches one to one`() = runBlocking {
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day.plusDays(1), merchant = "UPI Transaction"))
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day.plusDays(2), merchant = "UPI Transaction"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Debit,Credit
                02/01/2025,COFFEE,120.00,
                03/01/2025,COFFEE,120.00,
                """
            )
        )
        assertTrue(plan.newIndices.isEmpty())
        // A next-day SMS proves a duplicate, not its merchant: nothing is enriched.
        assertEquals(2, plan.count(StatementRowDecision.SkipReason.AMOUNT_DATE))
    }

    @Test
    fun `a row doesn't take the next day's SMS from that day's own row`() = runBlocking {
        // Only the 3rd's payment has an SMS: the 2nd is the one to import.
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("500.00"), TransactionType.EXPENSE, day.plusDays(1), merchant = "UPI Transaction"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Debit,Credit
                02/01/2025,MILK,500.00,
                03/01/2025,MILK,500.00,
                """
            )
        )
        assertEquals(listOf(0), plan.newIndices)
    }

    @Test
    fun `a row with its own day's SMS and a next-day one enriches its own day's`() = runBlocking {
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("500.00"), TransactionType.EXPENSE, day, merchant = "UPI Transaction"))
            seed(sms(BigDecimal("500.00"), TransactionType.EXPENSE, day.plusDays(1), merchant = "UPI Transaction"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Debit,Credit
                02/01/2025,MILK,500.00,
                """
            )
        )
        val enriched = (plan.rows.single().decision as StatementRowDecision.Enrich).merged
        assertEquals(day, enriched.dateTime.toLocalDate())
    }

    @Test
    fun `an SMS the day before a date-only row is not a match`() = runBlocking {
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day.minusDays(1), merchant = "Cafe"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Debit,Credit
                02/01/2025,COFFEE,120.00,
                """
            )
        )
        assertEquals(listOf(0), plan.newIndices)
    }

    @Test
    fun `zero-padded references match and all-zero ones match nothing`() = runBlocking {
        // Two days off: only the reference can match.
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("450.00"), TransactionType.EXPENSE, day.plusDays(2), reference = "412345678901", merchant = "Grocery Mart"))
            seed(sms(BigDecimal("99.00"), TransactionType.EXPENSE, day.plusDays(2), reference = "0", merchant = "Shop"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Ref No,Debit,Credit
                02/01/2025,GROCERY,0000412345678901,450.00,
                02/01/2025,SHOP,000000,99.00,
                """
            )
        )
        assertEquals(1, plan.count(StatementRowDecision.SkipReason.REFERENCE))
        assertEquals(listOf(1), plan.newIndices)
    }

    @Test
    fun `a same-day reversal is not swallowed by the debit's SMS`() = runBlocking {
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day, merchant = "Cafe"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Debit,Credit
                02/01/2025,CAFE,120.00,
                02/01/2025,CAFE REVERSAL,,120.00
                """
            )
        )
        // The debit matches its SMS; the ₹120 credit is new money in.
        assertEquals(listOf(1), plan.newIndices)
    }

    @Test
    fun `a row unticked last time is still new next time`() = runBlocking {
        // Two different ₹500 payments on one day; the user imported only the second.
        val parsed = table(
            """
            Date,Narration,Debit,Credit
            02/01/2025,PLUMBER,500.00,
            02/01/2025,ELECTRICIAN,500.00,
            """
        )
        val store = InMemoryStore()
        val processor = StatementImportProcessor(store)
        processor.apply(processor.plan(parsed), excluded = setOf(0))

        // Re-opened: the electrician's exact re-import is reserved before the
        // plumber row may match it by amount, so the plumber is still selectable.
        val again = processor.plan(parsed)
        assertEquals(listOf(0), again.newIndices)
        assertEquals(1, again.count(StatementRowDecision.SkipReason.HASH))
    }

    @Test
    fun `two identical rows with one SMS are one match and one new row`() = runBlocking {
        val store = InMemoryStore().apply {
            seed(sms(BigDecimal("20.00"), TransactionType.EXPENSE, day, merchant = "Tea"))
        }
        val plan = StatementImportProcessor(store).plan(
            table(
                """
                Date,Narration,Debit,Credit
                02/01/2025,TEA,20.00,
                02/01/2025,TEA,20.00,
                """
            )
        )
        assertEquals(1, plan.matchedCount)
        assertEquals(1, plan.newIndices.size)
    }

    @Test
    fun `imported rows don't carry the statement's running balance`() = runBlocking {
        val store = InMemoryStore()
        val parsed = statement()
        assertTrue(parsed.all { it.balance != null }) // kept for the closing-balance check…
        StatementImportProcessor(store).process(parsed)
        assertTrue(store.rows.all { it.balanceAfter == null }) // …but never stored as a bank-reported balance
    }

    @Test
    fun `a same-day same-amount transaction on another account is not a match`() = runBlocking {
        val store = InMemoryStore().apply {
            // ₹120 on the same day, but on a different account and in another currency.
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day, merchant = "Elsewhere").copy(accountNumber = "9999"))
            seed(sms(BigDecimal("120.00"), TransactionType.EXPENSE, day, merchant = "Abroad").copy(currency = "USD", transactionHash = "usd"))
        }
        val plan = StatementImportProcessor(store).plan(statement())
        val coffee = plan.rows.indexOfFirst { it.parsed.merchant == "COFFEE" }
        assertTrue(coffee in plan.newIndices)
    }

    @Test
    fun `balance check compares closing balances and corrects the current one`() {
        val parsed = statement()
        val closing = StatementBalanceCheck.closingRow(parsed)!!
        assertEquals(BigDecimal("44410.00"), closing.balance)
        assertEquals(LocalDate.of(2025, 1, 4), StatementBalanceCheck.dateOf(closing))
        // Newest-first statements close on their first row.
        assertEquals(closing, StatementBalanceCheck.closingRow(parsed.reversed()))

        val check = check(statement = "44410.00", app = "44000.00")
        assertEquals(BigDecimal("410.00"), check.difference)
        assertFalse(check.matches)
        assertTrue(check(statement = "44410.00", app = "44410").matches)
        assertNull(check(statement = "44410.00", app = null).difference)
    }

    @Test
    fun `set balance never overwrites a newer balance and is idempotent`() {
        fun correction(app: String?, current: String?, manual: Boolean, after: Boolean) =
            StatementBalanceCheck.correctionFor(
                statementBalance = BigDecimal("1100"),
                appBalance = app?.let(::BigDecimal),
                currentBalance = current?.let(::BigDecimal),
                isManual = manual,
                recordedAfterStatement = after
            )
        // SMS-tracked, nothing recorded after the statement: the statement's own figure.
        assertEquals(BigDecimal("1100"), correction(app = "1000", current = "1000", manual = false, after = false))
        // A newer bank balance exists: an old closing figure must not replace it.
        assertNull(correction(app = "1000", current = "1500", manual = false, after = true))
        // Nothing to correct from, or nothing to correct.
        assertNull(correction(app = null, current = "1500", manual = false, after = false))
        assertNull(correction(app = "1100", current = "1100", manual = false, after = false))
        // Manual (derived) account: today's figure shifted by the difference keeps later activity...
        assertEquals(BigDecimal("1600"), correction(app = "1000", current = "1500", manual = true, after = true))
        // ...and once applied its as-of balance moves with it, so a repeat check offers nothing.
        assertNull(correction(app = "1100", current = "1600", manual = true, after = true))
    }

    @Test
    fun `one-day newest-first statement closes on its first row`() {
        val rows = StatementTableParser.readCsv(
            """
            Date,Narration,Debit,Credit,Balance
            02/01/2025,LUNCH,200.00,,800.00
            02/01/2025,COFFEE,100.00,,1000.00
            """.trimIndent()
        )
        val parsed = StatementTableParser.parse(rows, StatementTableParser.guessMapping(rows), account).transactions
        assertEquals(BigDecimal("800.00"), StatementBalanceCheck.closingRow(parsed)!!.balance)
    }

    private fun check(statement: String, app: String?) = StatementBalanceCheck(
        bankName = "Sample Bank",
        accountLast4 = "1234",
        accountLabel = "Sample Bank ••1234",
        currency = "INR",
        asOf = LocalDate.of(2025, 1, 4),
        statementBalance = BigDecimal(statement),
        appBalance = app?.let(::BigDecimal)
    )

    private fun sms(
        amount: BigDecimal,
        type: TransactionType,
        date: LocalDate,
        reference: String? = null,
        merchant: String
    ) = TransactionEntity(
        amount = amount,
        merchantName = merchant,
        category = "Others",
        transactionType = type,
        dateTime = date.atTime(9, 30),
        smsBody = "synthetic sms",
        bankName = "Sample Bank",
        accountNumber = "1234",
        transactionHash = "sms-${amount.toPlainString()}-$date",
        reference = reference,
        currency = "INR"
    )

    /** A tiny in-memory transactions table with the DAO queries' semantics. */
    private class InMemoryStore : StatementImportProcessor.TransactionStore {
        val rows = mutableListOf<TransactionEntity>()
        var writes = 0
        private var nextId = 1L

        fun seed(t: TransactionEntity) { rows += t.copy(id = nextId++) }

        override suspend fun getTransactionsByHashes(hashes: List<String>) =
            rows.filter { it.transactionHash in hashes }

        override suspend fun getTransactionsBetween(start: LocalDateTime, end: LocalDateTime) =
            rows.filter { !it.isDeleted && it.dateTime in start..end }

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            writes++
            rows.replaceAll { if (it.id == transaction.id) transaction else it }
        }

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            writes++
            transactions.forEach { t -> if (rows.none { it.transactionHash == t.transactionHash }) seed(t) }
        }
    }
}
