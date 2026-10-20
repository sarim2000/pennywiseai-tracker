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

        val check = check(statement = "44410.00", app = "44000.00", current = "43000.00")
        assertEquals(BigDecimal("410.00"), check.difference)
        assertFalse(check.matches)
        // Activity after the statement (current ≠ as-of) is kept: 43,000 + 410.
        assertEquals(BigDecimal("43410.00"), check.correctedCurrentBalance)

        val same = check(statement = "44410.00", app = "44410", current = "44410")
        assertTrue(same.matches)

        val unknown = check(statement = "44410.00", app = null, current = "100")
        assertNull(unknown.difference)
        assertEquals(BigDecimal("44410.00"), unknown.correctedCurrentBalance)
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

    @Test
    fun `amount spellings cover how SMS and statements write the same amount`() {
        val spellings = ImportStatementUseCase.amountSpellings(BigDecimal("500.00")).map { it.toPlainString() }.toSet()
        assertEquals(setOf("500.00", "500", "500.0"), spellings)
        assertEquals(setOf("12.5", "12.50"), ImportStatementUseCase.amountSpellings(BigDecimal("12.5")).map { it.toPlainString() }.toSet())
    }

    private fun check(statement: String, app: String?, current: String) = StatementBalanceCheck(
        bankName = "Sample Bank",
        accountLast4 = "1234",
        accountLabel = "Sample Bank ••1234",
        currency = "INR",
        asOf = LocalDate.of(2025, 1, 4),
        statementBalance = BigDecimal(statement),
        appBalance = app?.let(::BigDecimal),
        currentBalance = BigDecimal(current)
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

        override suspend fun getTransactionByHash(transactionHash: String) =
            rows.firstOrNull { it.transactionHash == transactionHash }

        override suspend fun findStatementMergeCandidate(transaction: TransactionEntity) =
            rows.firstOrNull { it.reference == transaction.reference && StatementTransactionEnricher.isStatementMatch(it, transaction) }

        override suspend fun updateTransaction(transaction: TransactionEntity) {
            writes++
            rows.replaceAll { if (it.id == transaction.id) transaction else it }
        }

        override suspend fun getTransactionByAmountAndDate(amount: BigDecimal, dateStart: LocalDateTime, dateEnd: LocalDateTime) =
            rows.filter { !it.isDeleted && it.amount.compareTo(amount) == 0 && it.dateTime in dateStart..dateEnd }

        override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
            writes++
            transactions.forEach { t -> if (rows.none { it.transactionHash == t.transactionHash }) seed(t) }
        }
    }
}
