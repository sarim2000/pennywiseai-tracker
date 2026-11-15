package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
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
 * Each row of a marked pair is one leg: edits and deletes move only its own
 * account, and a manual account's sum sees it once. These all moved both accounts
 * per row, so a pair moved them twice.
 */
class TransferPairBalanceTest {
    private val time = LocalDateTime.of(2026, 1, 1, 12, 0)
    private val balances = mutableMapOf(SOURCE to BigDecimal("900"), DESTINATION to BigDecimal("100"))
    private var manual = false
    private var transfers = emptyList<TransactionEntity>()

    private val balanceDao = mockk<AccountBalanceDao>(relaxed = true) {
        coEvery { getLatestBalance(any(), any()) } answers {
            val account = secondArg<String>()
            AccountBalanceEntity(
                bankName = firstArg(), accountLast4 = account, balance = balances.getValue(account), timestamp = time,
                sourceType = if (manual) AccountBalanceRepository.SOURCE_MANUAL else "TRANSACTION"
            )
        }
        coEvery { getOpeningRow(any(), any()) } returns null
        coEvery { countSmsSourcedBalances(any(), any()) } returns 0
        coEvery { insertBalance(any()) } answers {
            val row = firstArg<AccountBalanceEntity>()
            balances[row.accountLast4] = row.balance
            1L
        }
    }
    private val transactionDao = mockk<TransactionDao>(relaxed = true) {
        coEvery { getTransfersForAccount(any()) } answers { transfers }
        coEvery { getTransactionsForAccountStrict(any(), any()) } returns emptyList()
    }
    private val database = mockk<PennyWiseDatabase>()
    private val repo = AccountBalanceRepository(balanceDao, transactionDao, database, mockk(relaxed = true))

    private val source = leg(1, SOURCE)
    private val destination = leg(2, DESTINATION)

    private fun leg(id: Long, account: String, amount: String = "100", pairId: String? = "pair") = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = "Synthetic", category = "Others",
        transactionType = TransactionType.TRANSFER, dateTime = time, transactionHash = "h$id",
        bankName = bank(account), accountNumber = account,
        fromAccount = SOURCE, toAccount = DESTINATION, fromBankName = bank(SOURCE), toBankName = bank(DESTINATION),
        transferPairId = pairId
    )

    private fun bank(account: String) = "Bank $account"

    private fun assertBalances(sourceBalance: String, destinationBalance: String) {
        assertEquals(0, BigDecimal(sourceBalance).compareTo(balances[SOURCE]))
        assertEquals(0, BigDecimal(destinationBalance).compareTo(balances[DESTINATION]))
    }

    @Before
    fun setUp() {
        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

    @Test
    fun `deleting one leg reverts only its own account`() = runBlocking {
        repo.applyDeleteBalanceShift(source)
        assertBalances("1000", "100")
    }

    @Test
    fun `deleting both legs undoes the transfer once, and restoring puts it back`() = runBlocking {
        repo.applyDeleteBalanceShift(source)
        repo.applyDeleteBalanceShift(destination)
        assertBalances("1000", "0")
        repo.applyRestoreBalanceShift(source)
        repo.applyRestoreBalanceShift(destination)
        assertBalances("900", "100")
    }

    @Test
    fun `editing both legs' amount applies the change once`() = runBlocking {
        repo.applyTransactionBalanceShift(source, source.copy(amount = BigDecimal("120")))
        repo.applyTransactionBalanceShift(destination, destination.copy(amount = BigDecimal("120")))
        assertBalances("880", "120")
    }

    @Test
    fun `turning a leg back into what it was leaves balances alone`() = runBlocking {
        repo.applyTransactionBalanceShift(source, source.copy(transactionType = TransactionType.EXPENSE))
        repo.applyTransactionBalanceShift(destination, destination.copy(transactionType = TransactionType.INCOME))
        assertBalances("900", "100")
    }

    @Test
    fun `a standalone transfer still moves both accounts`() = runBlocking {
        repo.applyDeleteBalanceShift(leg(3, SOURCE, pairId = null))
        assertBalances("1000", "0")
    }

    @Test
    fun `a manual account's sum counts only its own leg`() = runBlocking {
        manual = true
        transfers = listOf(source, destination)
        val before = time.minusDays(1)
        assertEquals(0, BigDecimal("1000").compareTo(repo.balanceAsOf(bank(SOURCE), SOURCE, before)))
        assertEquals(0, BigDecimal("0").compareTo(repo.balanceAsOf(bank(DESTINATION), DESTINATION, before)))
    }

    @Test
    fun `a merchant-only edit doesn't recompute balances`() = runBlocking {
        manual = true
        repo.reflectTransactionEdit(source, source.copy(merchantName = "Renamed", category = "Food"))
        coVerify(exactly = 0) { transactionDao.getTransfersForAccount(any()) }
        coVerify(exactly = 0) { balanceDao.insertBalance(any()) }
    }

    private companion object {
        const val SOURCE = "1111"
        const val DESTINATION = "2222"
    }
}
