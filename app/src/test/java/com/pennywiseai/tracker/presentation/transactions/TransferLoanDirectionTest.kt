package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #1043: a transfer marked as a borrowed loan is money in, so it becomes INCOME; lent stays a transfer. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferLoanDirectionTest {
    private val txn = TransactionEntity(
        id = 1, amount = BigDecimal("500"), merchantName = "Friend", category = "Transfer",
        transactionType = TransactionType.TRANSFER, dateTime = LocalDateTime.of(2026, 1, 1, 10, 0),
        transactionHash = "h"
    )
    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        coEvery { getTransactionById(1) } returns txn
        every { getSplitsForTransaction(1) } returns flowOf(emptyList())
    }
    private val loans = mockk<LoanRepository>(relaxed = true) {
        coEvery { findActiveLoanForPerson(any(), any(), any()) } returns null
    }
    private val prefs = mockk<com.pennywiseai.tracker.data.preferences.UserPreferencesRepository>(relaxed = true) {
        every { unifiedCurrencyMode } returns flowOf(false)
    }

    private fun viewModel() = TransactionDetailViewModel(
        transactions, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), loans, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), prefs, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true)
    ).apply { loadTransaction(1) }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `borrowed transfer is retyped income before the loan opens`() {
        viewModel().createLoanFromTransaction("Friend", LoanDirection.BORROWED, null)
        coVerify { transactions.updateTransaction(match { it.id == 1L && it.transactionType == TransactionType.INCOME }) }
        coVerify { loans.createLoan("Friend", LoanDirection.BORROWED, BigDecimal("500"), "INR", null, 1) }
    }

    @Test
    fun `lent transfer keeps its type`() {
        viewModel().createLoanFromTransaction("Friend", LoanDirection.LENT, null)
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        coVerify { loans.createLoan("Friend", LoanDirection.LENT, BigDecimal("500"), "INR", null, 1) }
    }
}
