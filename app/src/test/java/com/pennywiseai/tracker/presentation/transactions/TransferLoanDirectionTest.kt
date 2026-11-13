package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
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

/** #1043: a transfer marked as a loan keeps its type; a borrowed one always opens its own loan, so LoanMath counts it as principal. */
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
        // An active loan each way that a borrowed transfer must not merge into or repay.
        coEvery { findActiveLoanForPerson(any(), any(), any()) } answers {
            LoanEntity(id = 9, personName = "Friend", direction = secondArg(),
                originalAmount = BigDecimal("100"), remainingAmount = BigDecimal("100"))
        }
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
    fun `borrowed transfer keeps its type and opens its own loan`() {
        viewModel().createLoanFromTransaction("Friend", LoanDirection.BORROWED, null)
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        coVerify(exactly = 0) { loans.recordRepayment(any(), any(), any(), any()) }
        coVerify(exactly = 0) { loans.addToExistingLoan(any(), any(), any(), any()) }
        coVerify { loans.createLoan("Friend", LoanDirection.BORROWED, BigDecimal("500"), "INR", null, 1) }
    }

    @Test
    fun `lent transfer keeps today's lookup`() {
        viewModel().createLoanFromTransaction("Friend", LoanDirection.LENT, null)
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        coVerify { loans.recordRepayment(9, 1, BigDecimal("500"), null) }
    }
}
