package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** "Between my accounts" → confirm: the row is re-read, and only paired or edited if it hasn't changed. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferConfirmTest {
    private val out = TransactionEntity(
        id = 1, amount = BigDecimal("500"), merchantName = "Self", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 1, 1, 10, 0),
        transactionHash = "h1", bankName = "Bank A", accountNumber = "1111"
    )
    private val into = out.copy(
        id = 2, transactionType = TransactionType.INCOME, category = "Income", transactionHash = "h2",
        bankName = "Bank B", accountNumber = "2222", dateTime = out.dateTime.plusMinutes(1)
    )

    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        every { getSplitsForTransaction(any()) } returns flowOf(emptyList())
        every { observeSplitsForActiveTransactions() } returns flowOf(emptyList())
        coEvery { getTransactionsBetweenDatesList(any(), any()) } returns listOf(out, into)
    }
    private val balances = mockk<AccountBalanceRepository>(relaxed = true) {
        coEvery { getAllLatestBalancesOnce() } returns emptyList()
    }
    private val prefs = mockk<com.pennywiseai.tracker.data.preferences.UserPreferencesRepository>(relaxed = true) {
        every { unifiedCurrencyMode } returns flowOf(false)
    }

    private fun viewModel() = TransactionDetailViewModel(
        transactions, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        balances, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), prefs, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true)
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    /** Proposes [into] for [out], then has the row read back as [now] on confirm. */
    private fun confirmWith(now: TransactionEntity): TransactionDetailViewModel {
        coEvery { transactions.getTransactionById(1) } returnsMany listOf(out, now)
        val vm = viewModel()
        vm.loadTransaction(1)
        vm.answerOwnTransfer()
        assertEquals(into, vm.proposedTransferPartner.value)
        vm.confirmTransferPartner()
        return vm
    }

    @Test
    fun `pairs when the row is unchanged`() {
        confirmWith(out)
        coVerify(exactly = 1) { transactions.markAsTransferPair(out, into) }
    }

    @Test
    fun `a row changed meanwhile is shown again, neither paired nor edited`() {
        val changed = listOf(
            out.copy(isDeleted = true), out.copy(loanId = 9), out.copy(transferPairId = "p"),
            out.copy(amount = BigDecimal("510")), out.copy(accountNumber = "3333"),
        )
        changed.forEach { now ->
            val vm = confirmWith(now)
            assertFalse(vm.isEditMode.value)
            assertEquals(null, vm.proposedTransferPartner.value)
            assertEquals(now, vm.transaction.value)
        }
        coVerify(exactly = 0) { transactions.markAsTransferPair(any(), any()) }
    }

    @Test
    fun `the other leg gone meanwhile opens the editor`() {
        coEvery { transactions.getTransactionsBetweenDatesList(any(), any()) } returnsMany listOf(listOf(out, into), listOf(out))
        val vm = confirmWith(out)
        assertTrue(vm.isEditMode.value)
        assertEquals(TransactionType.TRANSFER, vm.editableTransaction.value?.transactionType)
        assertEquals("1111", vm.editableTransaction.value?.fromAccount)
        coVerify(exactly = 0) { transactions.markAsTransferPair(any(), any()) }
    }
}
