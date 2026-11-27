package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.widget.WidgetRefresher
import io.mockk.anyVararg
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #1484: a save writes only what this edit changed, over the row as stored now. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailStaleSaveTest {
    private var stored = TransactionEntity(
        id = 1, amount = BigDecimal("40"), merchantName = "Store A", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 1, 1, 10, 0),
        transactionHash = "h", description = "old note"
    )
    private val written = mutableListOf<TransactionEntity>()
    private val transactions = mockk<TransactionRepository>(relaxed = true) {
        coEvery { getTransactionById(1) } answers { stored }
        coEvery { hasSplits(1) } returns false
        coEvery { updateTransaction(any()) } answers {
            written += firstArg<TransactionEntity>()
            stored = firstArg()
        }
    }
    private val tags = mockk<TagRepository>(relaxed = true) {
        coEvery { getTagNamesForTransaction(1) } returns emptyList()
    }
    private val prefs = mockk<com.pennywiseai.tracker.data.preferences.UserPreferencesRepository>(relaxed = true) {
        every { unifiedCurrencyMode } returns flowOf(false)
    }

    private val balances = mockk<AccountBalanceRepository>(relaxed = true)

    private fun viewModel() = TransactionDetailViewModel(
        transactions, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        balances, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        tags, mockk(relaxed = true), prefs, mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true)
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
    fun `editing only the note keeps a category changed elsewhere`() {
        val vm = viewModel()
        vm.enterEditMode()
        stored = stored.copy(category = "Food")
        vm.updateDescription("new note")
        vm.saveChanges()
        assertEquals("Food", written.single().category)
        assertEquals("new note", written.single().description)
    }

    @Test
    fun `an edited category wins over one changed elsewhere`() {
        val vm = viewModel()
        vm.enterEditMode()
        stored = stored.copy(category = "Food")
        vm.updateCategory("Travel")
        vm.saveChanges()
        assertEquals("Travel", written.single().category)
    }

    @Test
    fun `a change landing after the early read still survives the write`() {
        val vm = viewModel()
        vm.enterEditMode()
        // The save suspends between its first read and the write (balances, receipt).
        coEvery { balances.pinManualOpenings(*anyVararg()) } answers { stored = stored.copy(category = "Food") }
        vm.updateDescription("new note")
        vm.saveChanges()
        assertEquals("Food", written.single().category)
        assertEquals("new note", written.single().description)
    }

    @Test
    fun `splits that no longer match a changed stored amount stop the save`() {
        val vm = viewModel()
        vm.enterEditMode()
        vm.enableSplitMode()
        stored = stored.copy(amount = BigDecimal("80"))
        vm.updateDescription("new note")
        vm.saveChanges()
        assertTrue(written.isEmpty())
        assertEquals(UiText.Res(R.string.txn_detail_error_splits_total), vm.errorMessage.value)
    }

    @Test
    fun `an account picked in the edit is merged with its bank, not with the stored number`() {
        val start = stored.copy(accountNumber = "1234", bankName = "Bank A")
        val edited = start.copy(bankName = "Bank B")
        val fresh = start.copy(accountNumber = "5678", bankName = "Bank C")
        val merged = mergeEditOntoFresh(start, edited, fresh)
        assertEquals("1234", merged.accountNumber)
        assertEquals("Bank B", merged.bankName)
        val untouched = mergeEditOntoFresh(start, start, fresh)
        assertEquals("5678", untouched.accountNumber)
        assertEquals("Bank C", untouched.bankName)
    }

    @Test
    fun `exclusion changed elsewhere survives a save that did not touch it`() {
        val vm = viewModel()
        vm.enterEditMode()
        stored = stored.copy(excludedFromAnalytics = true)
        vm.updateDescription("new note")
        vm.saveChanges()
        assertTrue(written.single().excludedFromAnalytics)
    }
}
