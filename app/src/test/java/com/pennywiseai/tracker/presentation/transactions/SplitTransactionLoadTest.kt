package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #1032: a split transaction's live splits Flow blocked the loan load, so its chip came back as "Mark as loan". */
@OptIn(ExperimentalCoroutinesApi::class)
class SplitTransactionLoadTest {
    private val txn = TransactionEntity(
        id = 1, amount = BigDecimal("400"), merchantName = "Shop", category = "Food",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 1, 1, 10, 0),
        transactionHash = "h", loanId = 3
    )
    private val loan = LoanEntity(
        id = 3, personName = "Friend", direction = LoanDirection.LENT,
        originalAmount = BigDecimal("200"), remainingAmount = BigDecimal("200")
    )
    private var activeCollectors = 0

    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        coEvery { getTransactionById(1) } returns txn
        coEvery { hasSplits(1) } returns true
        // Like Room: emits, then stays open forever.
        every { getSplitsForTransaction(1) } returns flow {
            emit(listOf(TransactionSplitEntity(transactionId = 1, category = "Food", amount = BigDecimal("400"))))
            awaitCancellation()
        }.onStart { activeCollectors++ }.onCompletion { activeCollectors-- }
    }
    private val loans = mockk<LoanRepository>(relaxed = true) {
        coEvery { getLoanById(3) } returns loan
    }

    private val prefs = mockk<com.pennywiseai.tracker.data.preferences.UserPreferencesRepository>(relaxed = true) {
        every { unifiedCurrencyMode } returns kotlinx.coroutines.flow.flowOf(false)
    }

    private fun viewModel() = TransactionDetailViewModel(
        transactions, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), loans, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), prefs, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true)
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loan still loads for a split transaction`() {
        val vm = viewModel()
        vm.loadTransaction(1)
        assertEquals(loan, vm.loan.value)
        assertEquals(1, vm.splits.value.size)
    }

    @Test
    fun `reloading keeps a single splits collector`() {
        val vm = viewModel()
        repeat(3) { vm.loadTransaction(1) }
        assertEquals(1, activeCollectors)
        assertEquals(loan, vm.loan.value)
    }
}
