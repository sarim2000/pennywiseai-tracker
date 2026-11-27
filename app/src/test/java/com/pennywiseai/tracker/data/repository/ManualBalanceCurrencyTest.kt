package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A manual account's balance is opening + its rows, and only rows in the
 * account's own currency count: the sum doesn't convert, so a $10 row came off
 * a ₹ balance as ₹10, on the import that added it or on any later recompute.
 */
class ManualBalanceCurrencyTest {
    private val time = LocalDateTime.of(2026, 1, 15, 12, 0)
    private var opening: AccountBalanceEntity? = null
    private var current = balanceRow(AccountBalanceRepository.SOURCE_MANUAL, "1000")
    private val ledger = mutableListOf<TransactionEntity>()

    private val balanceDao = mockk<AccountBalanceDao>(relaxed = true) {
        // Any other account (a transfer's far leg) has no balance yet.
        coEvery { getOpeningRow(any(), any()) } returns null
        coEvery { getLatestBalance(any(), any()) } returns null
        coEvery { getOpeningRow(BANK, ACCT) } answers { opening }
        coEvery { getLatestBalance(BANK, ACCT) } answers { current }
        coEvery { getManualCurrentRow(BANK, ACCT) } answers { current }
        coEvery { countSmsSourcedBalances(BANK, ACCT) } returns 0
        coEvery { getEarliestBalanceTimestamp(BANK, ACCT) } returns time
        coEvery { insertBalance(any()) } answers {
            val row = firstArg<AccountBalanceEntity>()
            if (row.sourceType == AccountBalanceRepository.SOURCE_OPENING) opening = row else current = row
            1L
        }
        coEvery { updateBalanceAndTimestampById(any(), any(), any()) } answers {
            current = current.copy(balance = secondArg())
        }
    }
    private val transactionDao = mockk<TransactionDao>(relaxed = true) {
        coEvery { getTransactionsForAccountStrict(BANK, ACCT) } answers { ledger.toList() }
        coEvery { getTransfersForAccount(ACCT) } answers { ledger.filter { it.transactionType == TransactionType.TRANSFER } }
    }
    private val database = mockk<PennyWiseDatabase>()
    private val repo = AccountBalanceRepository(balanceDao, transactionDao, database, mockk(relaxed = true))

    private fun balanceRow(source: String, balance: String) = AccountBalanceEntity(
        bankName = BANK, accountLast4 = ACCT, balance = BigDecimal(balance), timestamp = time,
        sourceType = source, currency = "INR"
    )

    private fun row(id: Long, amount: String, currency: String) = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = "Synthetic", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = time, transactionHash = "h$id",
        bankName = BANK, accountNumber = ACCT, currency = currency
    )

    /** What an import does: pin, write the rows, re-derive. */
    private suspend fun import(vararg rows: TransactionEntity) {
        repo.pinManualOpenings(*rows)
        ledger += rows
        repo.recomputeManualBalances(rows.toList())
    }

    private fun assertBalance(expected: String) = assertEquals(0, BigDecimal(expected).compareTo(current.balance))

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        opening = balanceRow(AccountBalanceRepository.SOURCE_OPENING, "1000")
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    @Test
    fun `a batch mixing currencies moves the balance by its own-currency rows`() = runBlocking {
        import(row(1, "100", "INR"), row(2, "10", "USD"))

        assertBalance("900")
    }

    @Test
    fun `a foreign row an earlier import added doesn't come off a later one`() = runBlocking {
        import(row(1, "10", "USD"))
        assertBalance("1000")

        import(row(2, "100", "INR"))

        assertBalance("900")
    }

    @Test
    fun `a legacy account's pinned opening ignores rows in another currency`() = runBlocking {
        opening = null
        current = balanceRow(AccountBalanceRepository.SOURCE_MANUAL, "900")
        ledger += listOf(row(1, "100", "INR"), row(2, "10", "USD"))

        repo.ensureManualOpening(BANK, ACCT)

        assertEquals(0, BigDecimal("1000").compareTo(opening!!.balance))
    }

    @Test
    fun `a transfer in another currency doesn't move the account`() = runBlocking {
        val transfer = row(1, "10", "USD").copy(
            transactionType = TransactionType.TRANSFER,
            fromBankName = BANK, fromAccount = ACCT, toBankName = "Other Bank", toAccount = "9999"
        )

        import(transfer, row(2, "100", "INR"))

        assertBalance("900")
    }

    private companion object {
        const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"
        const val BANK = "Cash Wallet"
        const val ACCT = "1111"
    }
}
