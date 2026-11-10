package com.pennywiseai.tracker.presentation.transactions

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
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
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * That the list's filter, sort, grouping and totals steps are wired to the ViewModel's live state.
 * The steps themselves are covered without a ViewModel; this catches a step fed the wrong value
 * or one read too early, which a test of the pure functions can't.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelListTest {
    // Three days ago is This Week and thirty is Earlier on either side of a midnight mid-test.
    private val thisWeek = LocalDate.now().minusDays(3)
    private val earlier = LocalDate.now().minusDays(30)

    private fun row(
        id: Long,
        amount: String = "10",
        merchant: String = "Synthetic Shop",
        category: String = "Food",
        currency: String = "USD",
        type: TransactionType = TransactionType.EXPENSE,
        date: LocalDate = thisWeek,
        last4: String = "1234",
        profileId: Long? = null,
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        currency = currency,
        transactionType = type,
        dateTime = date.atTime(12, 0),
        transactionHash = "list-$id",
        bankName = "Test Bank",
        accountNumber = last4,
        profileId = profileId,
    )

    private lateinit var viewModel: TransactionsViewModel

    // Rows wait until the test has set every filter.
    private val filtersSet = MutableStateFlow(false)
    private fun <T> afterFiltersSet(value: () -> T) = filtersSet.filter { it }.map { value() }

    private val tagsById = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    private fun start(
        rows: List<TransactionEntity>,
        tagNames: List<String> = emptyList(),
        aliases: List<MerchantAliasEntity> = emptyList(),
        splits: List<TransactionSplitEntity> = emptyList(),
        categories: List<CategoryEntity> = listOf(CategoryEntity(name = "Food", color = "#000000")),
        ignoredKeys: Set<String> = emptySet(),
        profileId: Long? = null,
        unified: Boolean = false,
        conversions: CurrencyConversionService = mockk(relaxed = true),
    ) {
        val transactions = mockk<TransactionRepository> {
            every { getAllTransactions() } returns afterFiltersSet { rows }
            // Only for the VM's first run, on its default period, which selectPeriod below replaces.
            every { getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) } returns afterFiltersSet { rows }
            every { observeSplitsForActiveTransactions() } returns flowOf(splits)
            every { getAllCurrencies() } returns flowOf(rows.map { it.currency }.distinct())
            every { getCurrenciesForPeriod(any(), any()) } returns flowOf(rows.map { it.currency }.distinct())
        }
        val categoryRepo = mockk<CategoryRepository> { every { getAllCategories() } returns flowOf(categories) }
        val tags = mockk<TagRepository> {
            every { observeTransactionTagNames() } returns tagsById
            every { observeAllTagNames() } returns flowOf(tagNames)
        }
        val aliasRepo = mockk<MerchantAliasRepository> { every { getAllAliases() } returns flowOf(aliases) }
        val prefs = mockk<UserPreferencesRepository> {
            every { baseCurrency } returns flowOf("USD")
            every { displayCurrency } returns flowOf("USD")
            every { unifiedCurrencyMode } returns flowOf(unified)
            every { selectedProfileId } returns flowOf(profileId)
            every { budgetCycleStartDay } returns flowOf(1)
            coEvery { getBudgetCycleStartDay() } returns 1
            every { smsScanMonths } returns flowOf(3)
            every { smsScanAllTime } returns flowOf(true)
            every { smsScanUseCustomDate } returns flowOf(false)
            every { smsScanCustomDate } returns flowOf(null)
        }
        val accounts = mockk<AccountBalanceRepository> { every { getAllLatestBalances() } returns flowOf(emptyList()) }
        val cards = mockk<CardRepository> { every { getAllCards() } returns flowOf(emptyList()) }
        val ignored = mockk<IgnoredAccountsStore> { every { keysFlow } returns flowOf(ignoredKeys) }
        val profiles = mockk<ProfileRepository> { every { observeAllProfiles() } returns flowOf(emptyList()) }
        val groups = mockk<TransactionGroupRepository> { every { getAllGroups() } returns flowOf(emptyList()) }
        viewModel = TransactionsViewModel(
            transactions, categoryRepo, tags, aliasRepo, prefs, conversions, accounts, cards, ignored,
            profiles, groups, mockk(relaxed = true), mockk(relaxed = true), SavedStateHandle(), mockk<Context>(relaxed = true),
        )
        viewModel.selectPeriod(TimePeriod.ALL)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = runBlocking {
        // Work on Dispatchers.Default resumes on Main: it has to finish before resetMain, or they race.
        viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
    }

    // A trigger can still be queued when the gate opens, so an older list may publish first; the
    // pipeline corrects itself, so wait for the expected list and report the last one on timeout.
    private suspend fun assertListed(expected: List<Long>) {
        filtersSet.value = true
        withTimeoutOrNull(10_000) { viewModel.uiState.first { state -> !state.isLoading && state.transactions.map { it.id } == expected } }
        assertEquals(expected, viewModel.uiState.value.transactions.map { it.id })
    }

    private fun assertSameAmount(expected: String, actual: BigDecimal) =
        assertEquals("$actual", 0, BigDecimal(expected).compareTo(actual))

    @Test
    fun `every filter narrows the same list`() = runBlocking {
        val raw = "SYNTHETIC RAW 01"
        start(
            rows = listOf(
                row(1, merchant = raw),
                row(2, merchant = "Other Shop"), // no alias, so the search misses it
                row(3, merchant = raw), // not tagged
                row(5, merchant = raw, profileId = 2), // another profile
                row(6, merchant = raw, type = TransactionType.INCOME), // type filter
                row(7, merchant = raw, last4 = "5678"), // account filter
                row(8, merchant = raw, currency = "INR"), // another currency
                row(9, merchant = raw, category = "Travel"), // category filter
            ),
            tagNames = listOf("Work Trip"),
            aliases = listOf(MerchantAliasEntity(merchantName = raw, alias = "Morning Cafe")),
            profileId = ProfileEntity.PERSONAL_ID,
        )
        tagsById.value = (1L..9L).filter { it != 3L }.associateWith { listOf("Work Trip") }

        // The debounced query only re-triggers later, so another filter must be set after it.
        viewModel.updateSearchQuery("morning")
        viewModel.setTagFilter("work trip")
        viewModel.setTransactionTypeFilter(TransactionTypeFilter.EXPENSE)
        viewModel.setAccountFilter("Test Bank_1234")
        viewModel.setCategoryFilter("Food")

        assertListed(listOf(1L))
    }

    // Apart from the list above: a row the account filter keeps can't also be on an ignored account.
    @Test
    fun `an ignored account's rows stay out`() = runBlocking {
        start(rows = listOf(row(1), row(2, last4 = "0000")), ignoredKeys = setOf("Test Bank_0000"))
        viewModel.setSortOption(SortOption.DATE_OLDEST)

        assertListed(listOf(1L))
    }

    @Test
    fun `tags arriving later re-filter the list`() = runBlocking {
        start(rows = listOf(row(1), row(2)))
        viewModel.updateSearchQuery("trip")
        viewModel.setSortOption(SortOption.DATE_OLDEST) // re-triggers with the query
        assertListed(emptyList<Long>())

        tagsById.value = mapOf(2L to listOf("Work Trip"))

        val listed = withTimeout(10_000) { viewModel.uiState.first { it.transactions.isNotEmpty() } }
        assertEquals(listOf(2L), listed.transactions.map { it.id })
    }

    @Test
    fun `a category found only in a split filters the list and stays available`() = runBlocking {
        val living = CategoryEntity(id = 10, name = "Living", color = "#000000")
        val rent = CategoryEntity(id = 11, name = "Rent", color = "#000000", parentId = 10)
        start(
            rows = listOf(row(1), row(2), row(3, category = "Rent")),
            splits = listOf(TransactionSplitEntity(transactionId = 1, category = "Groceries", amount = BigDecimal.TEN)),
            categories = listOf(CategoryEntity(name = "Food", color = "#000000"), living, rent),
        )
        viewModel.setCategoryFilter("Groceries")

        assertListed(listOf(1L))
        val available = withTimeout(10_000) { viewModel.availableCategories.first { it.isNotEmpty() } }
        assertEquals(listOf("Food", "Groceries", "Living", "Rent"), available)
        assertEquals("Groceries", viewModel.categoryFilter.value)
    }

    @Test
    fun `sort, date groups and totals follow the listed rows`() = runBlocking {
        start(
            rows = listOf(
                row(1, amount = "10"),
                row(2, amount = "30", date = earlier),
                row(3, amount = "20"),
                row(4, amount = "999", currency = "INR"),
            ),
        )
        viewModel.setSortOption(SortOption.AMOUNT_HIGHEST)

        assertListed(listOf(2L, 3L, 1L))
        val state = viewModel.uiState.value
        assertEquals(listOf(3L, 1L), state.groupedTransactions.getValue(DateGroup.THIS_WEEK).map { it.id })
        assertEquals(listOf(2L), state.groupedTransactions.getValue(DateGroup.EARLIER).map { it.id })
        val totals = withTimeout(10_000) { viewModel.filteredTotals.first { it.transactionCount > 0 } }
        assertSameAmount("60", totals.expenses)
        assertEquals(3, totals.transactionCount)
    }

    @Test
    fun `unified totals convert each currency into the display currency`() = runBlocking {
        val conversions = mockk<CurrencyConversionService> {
            // The one pair this list needs: a swapped from/to would convert nothing and fail below.
            coEvery { convertAmountOrNull(any(), any(), any(), any()) } answers {
                val fromRupees = secondArg<String>() == "INR" && thirdArg<String>() == "USD"
                if (fromRupees) firstArg<BigDecimal>().divide(BigDecimal(83)) else null
            }
        }
        start(
            rows = listOf(row(1, amount = "5"), row(2, amount = "8300", currency = "INR")),
            unified = true,
            conversions = conversions,
        )
        viewModel.setSortOption(SortOption.AMOUNT_LOWEST)

        assertListed(listOf(1L, 2L))
        val totals = withTimeout(10_000) { viewModel.filteredTotals.first { it.transactionCount > 0 } }
        assertSameAmount("105", totals.expenses)
        assertEquals(2, totals.transactionCount)
        val converted = withTimeout(10_000) { viewModel.convertedAmounts.first { it.isNotEmpty() } }
        assertSameAmount("100", converted.getValue(2L))
        assertTrue(1L !in converted)
    }
}
