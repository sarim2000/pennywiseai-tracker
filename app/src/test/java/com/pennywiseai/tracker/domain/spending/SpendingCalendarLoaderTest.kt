package com.pennywiseai.tracker.domain.spending

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
        every { upcoming.observe(any(), any(), any()) } returns flowOf(Upcoming(listOf(cardBill), emptyList()))
    }

    @Test
    fun `a month that's over still shows its card bill that's owed`() = runTest {
        val days = loader.observe(october, today).first()

        assertEquals(listOf(cardBill), days.getValue(cardBill.dueDate).planned)
        // Asked with the calendar's rule: card charges stay on their own days.
        verify { upcoming.observe(today, october.atEndOfMonth(), keepCardCharges = true) }
    }

    @Test
    fun `this month shows last month's unpaid card bill today`() = runTest {
        val days = loader.observe(YearMonth.from(today), today).first()

        assertEquals(listOf(cardBill), days.getValue(today).planned)
    }
}
