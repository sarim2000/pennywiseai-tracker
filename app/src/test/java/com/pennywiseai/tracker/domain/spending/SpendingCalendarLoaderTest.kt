package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.utils.Money
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SpendingCalendarLoaderTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val october = YearMonth.of(2026, 10)

    private val transactions = mockk<TransactionRepository>()
    private val prefs = mockk<UserPreferencesRepository>()
    private val cards = mockk<CardRepository>()
    private val ignored = mockk<IgnoredAccountsStore>()
    private val balances = mockk<AccountBalanceRepository>()
    private val upcoming = mockk<UpcomingPaymentsLoader>()
    private val loader = SpendingCalendarLoader(
        PeriodReviewLoader(transactions, mockk(), mockk(), balances, prefs, ignored, cards),
        upcoming,
        prefs,
    )

    private val cardBill = Bill("Card", Money(BigDecimal("2500"), "INR"), LocalDate.of(2026, 10, 25), source = BillSource.Card("Test Bank", "4444"))

    init {
        every { prefs.selectedProfileId } returns flowOf(null)
        every { prefs.countCreditCardAsExpense } returns flowOf(false)
        every { cards.getAllCards() } returns flowOf(emptyList())
        every { ignored.keysFlow } returns flowOf(emptySet())
        every { balances.getAllLatestBalances() } returns flowOf(emptyList())
        every { transactions.getTransactionsWithSplitsFiltered(any<LocalDate>(), any<LocalDate>()) } returns flowOf(emptyList())
        every { upcoming.observe(any(), any(), any(), any()) } returns flowOf(Upcoming(listOf(cardBill), emptyList()))
    }

    private fun row(day: Int, account: String, profileId: Long? = null) = TransactionWithSplits(
        TransactionEntity(
            id = day.toLong(), amount = BigDecimal("100"), merchantName = "Test Shop", category = "Shopping",
            currency = "INR", transactionType = TransactionType.EXPENSE, dateTime = october.atDay(day).atTime(12, 0),
            transactionHash = "h$day", bankName = "Test Bank", accountNumber = account, profileId = profileId,
        ),
        emptyList(),
    )

    @Test
    fun `a month that's over still shows its card bill that's owed`() = runTest {
        val days = loader.observe(october, today).first()

        assertEquals(listOf(cardBill), days.getValue(cardBill.dueDate).planned)
        // Asked with the calendar's rule: card charges stay on their own days.
        verify { upcoming.observe(today, october.atEndOfMonth(), keepCardCharges = true, from = october.atDay(1)) }
    }

    @Test
    fun `a month ahead walks its payments from its own first day`() = runTest {
        val ahead = YearMonth.from(today).plusMonths(15)

        loader.observe(ahead, today).first()

        verify { upcoming.observe(today, ahead.atEndOfMonth(), keepCardCharges = true, from = ahead.atDay(1)) }
    }

    @Test
    fun `this month shows last month's unpaid card bill today`() = runTest {
        val days = loader.observe(YearMonth.from(today), today).first()

        assertEquals(listOf(cardBill), days.getValue(today).planned)
    }

    @Test
    fun `spend from an ignored account or another profile stays out`() = runTest {
        every { upcoming.observe(any(), any(), any(), any()) } returns flowOf(Upcoming(emptyList(), emptyList()))
        every { prefs.selectedProfileId } returns flowOf(ProfileEntity.PERSONAL_ID)
        every { ignored.keysFlow } returns flowOf(setOf(IgnoredAccountsStore.keyFor("Test Bank", "1111")))
        every { transactions.getTransactionsWithSplitsFiltered(any<LocalDate>(), any<LocalDate>()) } returns flowOf(
            listOf(row(3, "1111"), row(4, "2222", profileId = 2), row(5, "2222"))
        )

        val days = loader.observe(october, today).first()

        assertEquals(listOf(october.atDay(5)), days.keys.toList())
    }
}
