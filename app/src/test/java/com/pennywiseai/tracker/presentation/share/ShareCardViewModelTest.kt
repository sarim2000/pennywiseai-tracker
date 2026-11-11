package com.pennywiseai.tracker.presentation.share

import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.data.share.ShareHero
import com.pennywiseai.tracker.data.share.SharePeriod
import com.pennywiseai.tracker.data.share.ShareWindow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The share card's pipeline: saved config, profile, review window and the live
 * account/card data it is derived from. The pure counts (bank texts, merchants) are
 * covered by PeriodReviewTest; these pin the wiring around them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareCardViewModelTest {
    private val personal = 1L
    private val business = 2L

    private val savedConfig = CompletableDeferred<ShareCardConfig>()
    private val persisted = mutableListOf<ShareCardConfig>()
    private val profileId = MutableStateFlow<Long?>(null)

    private val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private val balances = MutableStateFlow<List<AccountBalanceEntity>>(emptyList())
    private val subscriptions = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    private val ignored = MutableStateFlow<Set<String>>(emptySet())
    private val cards = MutableStateFlow<List<CardEntity>>(emptyList())

    /** False = a period/profile query started from now on hangs, like a slow database. */
    private val queriesAnswer = MutableStateFlow(true)

    private val transactionRepository = mockk<TransactionRepository> {
        every { getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) } answers {
            flow {
                queriesAnswer.first { it }
                emitAll(transactions)
            }
        }
    }
    private val preferences = mockk<UserPreferencesRepository> {
        every { shareCardConfig } returns flow { emit(savedConfig.await()) }
        coEvery { setShareCardConfig(any()) } answers { persisted += firstArg<ShareCardConfig>() }
        every { selectedProfileId } returns profileId
        every { budgetCycleStartDay } returns flowOf(1)
    }

    private lateinit var viewModel: ShareCardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = ShareCardViewModel(
            transactionRepository = transactionRepository,
            subscriptionRepository = mockk { every { getActiveSubscriptions() } returns subscriptions },
            accountBalanceRepository = mockk { every { getAllLatestBalances() } returns balances },
            userPreferencesRepository = preferences,
            ignoredAccountsStore = mockk { every { keysFlow } returns ignored },
            cardRepository = mockk { every { getAllCards() } returns cards },
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun tx(
        id: Long,
        account: String,
        date: LocalDate = LocalDate.of(2026, 10, 5),
        bank: String = "TESTBANK",
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal.TEN,
        merchantName = "Shop $id",
        category = "Food",
        currency = "USD",
        transactionType = TransactionType.EXPENSE,
        dateTime = date.atTime(12, 0),
        transactionHash = "share-$id",
        bankName = bank,
        accountNumber = account,
        smsSender = "TESTBANK",
    )

    private fun account(last4: String, profile: Long) = AccountBalanceEntity(
        bankName = "TESTBANK",
        accountLast4 = last4,
        balance = BigDecimal.ZERO,
        timestamp = LocalDateTime.of(2026, 10, 1, 9, 0),
        currency = "USD",
        profileId = profile,
    )

    private fun subscription(id: Long) = SubscriptionEntity(
        id = id,
        merchantName = "Streaming $id",
        amount = BigDecimal.ONE,
        nextPaymentDate = LocalDate.of(2026, 11, 1),
    )

    private fun TestScope.loadSaved(config: ShareCardConfig = ShareCardConfig(period = SharePeriod.ALL_TIME)) {
        savedConfig.complete(config)
        backgroundScope.launch { viewModel.data.collect {} }
        advanceUntilIdle()
    }

    // --- Saved config

    @Test
    fun `nothing is queried until the saved config has loaded`() = runTest {
        backgroundScope.launch { viewModel.data.collect {} }
        advanceUntilIdle()

        assertNull(viewModel.data.value)
        verify(exactly = 0) { transactionRepository.getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) }

        transactions.value = listOf(tx(1, "1111"))
        savedConfig.complete(ShareCardConfig(period = SharePeriod.ALL_TIME))
        advanceUntilIdle()

        assertEquals(1, viewModel.data.value?.transactionCount)
        assertNull("all time has no period label", viewModel.data.value?.periodLabel)
    }

    @Test
    fun `an edit made before the saved config loads is replayed on top of it`() = runTest {
        viewModel.updateConfig { it.copy(period = SharePeriod.ALL_TIME) }
        savedConfig.complete(ShareCardConfig(hero = ShareHero.MERCHANTS, period = SharePeriod.LAST_MONTH))
        advanceUntilIdle()

        val expected = ShareCardConfig(hero = ShareHero.MERCHANTS, period = SharePeriod.ALL_TIME)
        assertEquals(expected, viewModel.config.value)
        // The early edit persisted a default-derived config; the merge has to be saved after it.
        assertEquals(expected, persisted.last())
    }

    // --- Selection changes

    @Test
    fun `switching profile shows loading, never the previous profile's numbers`() = runTest {
        transactions.value = listOf(tx(1, "1111"), tx(2, "2222"), tx(3, "2222"))
        balances.value = listOf(account("1111", personal), account("2222", business))
        profileId.value = personal
        loadSaved()
        assertEquals(1, viewModel.data.value?.transactionCount)

        queriesAnswer.value = false
        profileId.value = business
        advanceUntilIdle()
        assertNull(viewModel.data.value)

        queriesAnswer.value = true
        advanceUntilIdle()
        assertEquals(2, viewModel.data.value?.transactionCount)
    }

    @Test
    fun `opening on a review window shows loading, then counts only that window`() = runTest {
        val october = ShareWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "OCTOBER 2026")
        transactions.value = listOf(
            tx(1, "1111", LocalDate.of(2026, 10, 5)),
            tx(2, "1111", LocalDate.of(2026, 11, 1)),
        )
        loadSaved()
        assertEquals(2, viewModel.data.value?.transactionCount)

        queriesAnswer.value = false
        viewModel.setWindow(october)
        advanceUntilIdle()
        assertNull(viewModel.data.value)

        queriesAnswer.value = true
        advanceUntilIdle()
        assertEquals(1, viewModel.data.value?.transactionCount)
        assertEquals("OCTOBER 2026", viewModel.data.value?.periodLabel)
    }

    // --- Live data

    @Test
    fun `an account moved to another profile leaves the open card`() = runTest {
        transactions.value = listOf(tx(1, "1111"), tx(2, "2222"))
        balances.value = listOf(account("1111", personal), account("2222", personal))
        profileId.value = personal
        loadSaved()
        assertEquals(2, viewModel.data.value?.transactionCount)

        balances.value = listOf(account("1111", personal), account("2222", business))
        advanceUntilIdle()

        assertEquals(1, viewModel.data.value?.transactionCount)
    }

    @Test
    fun `ignoring an account drops its linked card's purchases from the open card`() = runTest {
        balances.value = listOf(account("1111", personal))
        cards.value = listOf(CardEntity(cardLast4 = "9999", cardType = CardType.DEBIT, bankName = "TESTBANK", accountLast4 = "1111"))
        transactions.value = listOf(tx(1, "9999"), tx(2, "5555"))
        loadSaved()
        assertEquals(2, viewModel.data.value?.transactionCount)

        ignored.value = setOf("TESTBANK_1111")
        advanceUntilIdle()

        assertEquals(1, viewModel.data.value?.transactionCount)
    }

    @Test
    fun `a card linked to an ignored account after opening is dropped too`() = runTest {
        balances.value = listOf(account("1111", personal))
        ignored.value = setOf("TESTBANK_1111")
        transactions.value = listOf(tx(1, "9999"), tx(2, "5555"))
        loadSaved()
        assertEquals(2, viewModel.data.value?.transactionCount)

        cards.value = listOf(CardEntity(cardLast4 = "9999", cardType = CardType.DEBIT, bankName = "TESTBANK", accountLast4 = "1111"))
        advanceUntilIdle()

        assertEquals(1, viewModel.data.value?.transactionCount)
    }

    // --- Subscriptions

    @Test
    fun `a review window never claims today's subscriptions`() = runTest {
        subscriptions.value = listOf(subscription(1), subscription(2))
        loadSaved()
        assertEquals(2, viewModel.data.value?.subscriptionCount)

        viewModel.setWindow(ShareWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "OCTOBER 2026"))
        advanceUntilIdle()
        val windowed = viewModel.data.value
        assertNotNull(windowed)
        assertEquals(0, windowed!!.subscriptionCount)
        assertEquals(0, windowed.countFor(ShareHero.SUBSCRIPTIONS))

        viewModel.setWindow(null)
        advanceUntilIdle()
        assertEquals(2, viewModel.data.value?.subscriptionCount)
    }
}
