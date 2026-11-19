package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.widget.WidgetRefresher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** The "Remember" offer belongs to the transaction, merchant and category that made it. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailRuleOfferTest {
    private var stored = TransactionEntity(
        id = 1, amount = BigDecimal("40"), merchantName = "Store A", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 1, 1, 10, 0),
        transactionHash = "h"
    )
    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        coEvery { getTransactionById(1) } answers { stored }
        coEvery { updateCategory(1, any()) } answers { stored = stored.copy(category = secondArg()) }
        coEvery { hasSplits(1) } returns false
    }
    private val mappings = mockk<MerchantMappingRepository>(relaxed = true) {
        coEvery { getCategoryForMerchant(any()) } returns null
    }
    private val prefs = mockk<com.pennywiseai.tracker.data.preferences.UserPreferencesRepository>(relaxed = true) {
        every { unifiedCurrencyMode } returns flowOf(false)
    }

    private fun viewModel() = TransactionDetailViewModel(
        transactions, mappings, mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), prefs, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true)
    ).apply { loadTransaction(1) }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WidgetRefresher)
        every { WidgetRefresher.refreshTransactionWidgets(any()) } just runs
    }

    @After fun tearDown() {
        unmockkObject(WidgetRefresher)
        Dispatchers.resetMain()
    }

    @Test
    fun `an unchanged transaction keeps the offer and remembers it`() {
        val vm = viewModel()
        vm.quickUpdateCategory("Food")
        vm.loadTransaction(1)
        assertEquals("Food", vm.ruleSuggestion.value)
        vm.acceptRuleSuggestion()
        coVerify { mappings.setMapping("Store A", "Food", any()) }
    }

    @Test
    fun `renaming the merchant or changing the category drops the offer`() {
        val vm = viewModel()
        vm.quickUpdateCategory("Food")
        stored = stored.copy(merchantName = "Store B")
        vm.loadTransaction(1)
        assertNull(vm.ruleSuggestion.value)
        vm.acceptRuleSuggestion()
        coVerify(exactly = 0) { mappings.setMapping(any(), any(), any()) }

        stored = stored.copy(merchantName = "Store A", category = "Travel")
        vm.loadTransaction(1)
        assertNull(vm.ruleSuggestion.value)
    }

    @Test
    fun `removing splits then cancelling keeps the saved split status`() {
        coEvery { transactions.hasSplits(1) } returns true
        every { transactions.getSplitsForTransaction(1) } returns flowOf(emptyList())
        val vm = viewModel()
        vm.enterEditMode()
        vm.removeSplits()
        vm.cancelEdit()
        assertEquals(true, vm.hasSplits.value)
    }
}
