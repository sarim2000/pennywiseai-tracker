package com.pennywiseai.tracker.domain.review

import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodReviewLoaderTest {
    private val october = LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31)

    private val transactions = mockk<TransactionRepository>()
    private val prefs = mockk<UserPreferencesRepository>()
    private val cards = mockk<CardRepository>()
    private val ignored = mockk<IgnoredAccountsStore>()
    private val balances = mockk<AccountBalanceRepository>()
    private val loader = PeriodReviewLoader(transactions, mockk(), mockk(), balances, prefs, ignored, cards)

    init {
        every { prefs.selectedProfileId } returns flowOf(null)
        every { cards.getAllCards() } returns flowOf(emptyList())
        every { ignored.keysFlow } returns flowOf(emptySet())
        every { balances.getAllLatestBalances() } returns flowOf(emptyList())
        every { transactions.observeActiveTransactionCount() } returns flowOf(0)
    }

    private fun row(date: LocalDateTime, account: String? = null) = TransactionWithSplits(
        TransactionEntity(
            amount = BigDecimal.TEN,
            merchantName = "Test Shop",
            category = "Shopping",
            currency = "USD",
            transactionType = TransactionType.EXPENSE,
            dateTime = date,
            transactionHash = "$date",
            bankName = "Test Bank",
            accountNumber = account,
        ),
        emptyList(),
    )

    /** The DAO compares date_time as text, so a stored row is matched by the text it was written in. */
    private fun storedAs(rows: Map<String, TransactionWithSplits>) {
        val sql = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        every { transactions.getTransactionsWithSplitsFiltered(any<LocalDate>(), any<LocalDate>()) } answers {
            val from = firstArg<LocalDate>().atStartOfDay().format(sql)
            val to = secondArg<LocalDate>().atTime(23, 59, 59).format(sql)
            flowOf(rows.filterKeys { it >= from && it <= to }.values.toList())
        }
    }

    @Test
    fun `a first-day row stored in a legacy date form still counts`() = runTest {
        storedAs(
            mapOf(
                "2026-10-01 08:30:15" to row(LocalDateTime.of(2026, 10, 1, 8, 30, 15)),
                "2026-10-15T12:00:00" to row(LocalDateTime.of(2026, 10, 15, 12, 0)),
            )
        )
        assertEquals(2, loader.rowCount(october.first, october.second))
    }

    @Test
    fun `a row just outside the period does not count`() = runTest {
        // The SQL window is wider than the period, so these two come back from the query.
        storedAs(
            mapOf(
                "2026-09-30T23:00:00" to row(LocalDateTime.of(2026, 9, 30, 23, 0)),
                "2026-11-01T01:00:00" to row(LocalDateTime.of(2026, 11, 1, 1, 0)),
                "2026-10-15T12:00:00" to row(LocalDateTime.of(2026, 10, 15, 12, 0)),
            )
        )
        assertEquals(1, loader.rowCount(october.first, october.second))
    }

    @Test
    fun `a card linked to an ignored account stays out`() = runTest {
        every { ignored.keysFlow } returns flowOf(setOf(IgnoredAccountsStore.keyFor("Test Bank", "1111")))
        every { cards.getAllCards() } returns flowOf(
            listOf(CardEntity(cardLast4 = "2222", cardType = CardType.DEBIT, bankName = "Test Bank", accountLast4 = "1111"))
        )
        storedAs(
            mapOf(
                "2026-10-14T12:00:00" to row(LocalDateTime.of(2026, 10, 14, 12, 0), account = "2222"),
                "2026-10-15T12:00:00" to row(LocalDateTime.of(2026, 10, 15, 12, 0), account = "3333"),
            )
        )
        assertEquals(1, loader.rowCount(october.first, october.second))
    }

    @Test
    fun `another preference being written is not a change`() = runTest(UnconfinedTestDispatcher()) {
        // DataStore re-emits every preference on any write, here marking a banner handled.
        val dataStore = MutableStateFlow(0)
        val profile = MutableStateFlow<Long?>(null)
        every { prefs.selectedProfileId } returns dataStore.map { profile.value }

        val changes = mutableListOf<Unit>()
        backgroundScope.launch { loader.rowCountChanges().toList(changes) }
        advanceUntilIdle()
        assertEquals(1, changes.size)

        dataStore.value = 1
        advanceUntilIdle()
        assertEquals(1, changes.size)

        profile.value = 7
        dataStore.value = 2
        advanceUntilIdle()
        assertEquals(2, changes.size)
    }
}
