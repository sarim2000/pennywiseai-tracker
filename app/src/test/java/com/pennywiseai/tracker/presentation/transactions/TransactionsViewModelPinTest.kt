package com.pennywiseai.tracker.presentation.transactions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** A review's category that fell to zero: Travel spent in September, only Shopping in October. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelPinTest {
    private val october = LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31)
    private val september = row(1, "Travel", LocalDate.of(2026, 9, 2))
    private val shopping = row(2, "Shopping", LocalDate.of(2026, 10, 3))

    private fun row(id: Long, category: String, date: LocalDate) = TransactionEntity(
        id = id,
        amount = BigDecimal.TEN,
        merchantName = "Test Shop",
        category = category,
        currency = "USD",
        transactionType = TransactionType.EXPENSE,
        dateTime = date.atTime(12, 0),
        transactionHash = "pin-$id",
    )

    private lateinit var viewModel: TransactionsViewModel

    // Main and the ViewModel's background work share this one thread and virtual clock.
    private val dispatcher = UnconfinedTestDispatcher()

    // The screen sets its filters and its pin in one go on the main thread. Here Main is unconfined, so
    // the auto-clear could run between the two: rows wait until the test has done both.
    private val filtersSet = MutableStateFlow(false)
    private fun <T> afterFiltersSet(value: () -> T) = filtersSet.filter { it }.map { value() }

    // The profile Home has selected.
    private val homeProfile = MutableStateFlow<Long?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val rows = listOf(september, shopping)
        val transactions = mockk<TransactionRepository> {
            every { getAllTransactions() } returns afterFiltersSet { rows }
            every { getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) } answers {
                val range = firstArg<LocalDateTime>()..secondArg<LocalDateTime>()
                afterFiltersSet { rows.filter { it.dateTime in range } }
            }
            every { observeSplitsForActiveTransactions() } returns flowOf(emptyList())
            every { getAllCurrencies() } returns flowOf(listOf("USD"))
            every { observeRecentCategoryUsage() } returns flowOf(emptyList())
            every { getCurrenciesForPeriod(any(), any()) } returns flowOf(listOf("USD"))
        }
        val categories = mockk<CategoryRepository> {
            every { getAllCategories() } returns flowOf(
                listOf(CategoryEntity(name = "Travel", color = "#000000"), CategoryEntity(name = "Shopping", color = "#000000"))
            )
        }
        val tags = mockk<TagRepository> {
            every { observeTransactionTagNames() } returns flowOf(emptyMap())
            every { observeAllTagNames() } returns flowOf(emptyList())
        }
        val aliases = mockk<MerchantAliasRepository> { every { getAllAliases() } returns flowOf(emptyList()) }
        val prefs = mockk<UserPreferencesRepository> {
            every { baseCurrency } returns flowOf("USD")
            every { displayCurrency } returns flowOf("USD")
            every { unifiedCurrencyMode } returns flowOf(true)
            every { selectedProfileId } returns homeProfile
            every { budgetCycleStartDay } returns flowOf(1)
            coEvery { getBudgetCycleStartDay() } returns 1
            every { smsScanMonths } returns flowOf(3)
            every { smsScanAllTime } returns flowOf(true)
            every { smsScanUseCustomDate } returns flowOf(false)
            every { smsScanCustomDate } returns flowOf(null)
        }
        val accounts = mockk<AccountBalanceRepository> { every { getAllLatestBalances() } returns flowOf(emptyList()) }
        val cards = mockk<CardRepository> { every { getAllCards() } returns flowOf(emptyList()) }
        val ignored = mockk<IgnoredAccountsStore> { every { keysFlow } returns flowOf(emptySet()) }
        val profiles = mockk<ProfileRepository> { every { observeAllProfiles() } returns flowOf(emptyList()) }
        val groups = mockk<TransactionGroupRepository> { every { getAllGroups() } returns flowOf(emptyList()) }
        viewModel = TransactionsViewModel(
            transactions, categories, tags, aliases, prefs, mockk(relaxed = true), accounts, cards, ignored,
            profiles, groups, mockk(relaxed = true), mockk(relaxed = true), SavedStateHandle(), mockk<Context>(relaxed = true), dispatcher,
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun openTravelInOctober() = viewModel.applyInitialFilters(
        "Travel", null, "CUSTOM", "USD", october.first.toEpochDay(), october.second.toEpochDay(),
    )

    @Test
    fun `a pinned list keeps a category with no rows and shows nothing`() = runTest(dispatcher) {
        openTravelInOctober()
        viewModel.pinCurrency("USD")
        filtersSet.value = true

        withTimeout(10_000) { viewModel.availableCategories.first { "Shopping" in it } }
        // Idle: an auto-clear, and the list after it, would have landed by now.
        advanceUntilIdle()
        assertEquals("Travel", viewModel.categoryFilter.value)
        assertTrue(!viewModel.uiState.value.isLoading && viewModel.uiState.value.transactions.isEmpty())
        assertEquals("USD", viewModel.selectedCurrency.value)
    }

    @Test
    fun `a category name opens as written, a percent or plus sign in it too`() = runTest(dispatcher) {
        listOf("Savings 10%", "Food + Drinks").forEach { name ->
            viewModel.applyNavigationFilters(name, null, "CUSTOM", "USD", october.first.toEpochDay(), october.second.toEpochDay())
            assertEquals(name, viewModel.categoryFilter.value)
        }
    }

    @Test
    fun `a list pinned to a figure's profile keeps it while Home switches`() = runTest(dispatcher) {
        homeProfile.value = ProfileEntity.BUSINESS_ID
        assertEquals(ProfileEntity.BUSINESS_ID, viewModel.selectedProfileId.value)
        // The figure counted every profile: the list does too, Business still selected on Home.
        viewModel.pinProfile(null)
        assertEquals(null, viewModel.selectedProfileId.value)
        homeProfile.value = ProfileEntity.PERSONAL_ID
        assertEquals(null, viewModel.selectedProfileId.value)
        // The screen pins again on every return: only the first counts.
        viewModel.pinProfile(ProfileEntity.BUSINESS_ID)
        assertEquals(null, viewModel.selectedProfileId.value)
    }

    @Test
    fun `any other list still drops a category the period no longer has`() = runTest(dispatcher) {
        openTravelInOctober()
        filtersSet.value = true

        withTimeout(10_000) { viewModel.categoryFilter.first { it == null } }
        val shown = withTimeout(10_000) { viewModel.uiState.first { it.transactions.isNotEmpty() } }
        assertEquals(listOf(shopping.id), shown.transactions.map { it.id })
    }
}
