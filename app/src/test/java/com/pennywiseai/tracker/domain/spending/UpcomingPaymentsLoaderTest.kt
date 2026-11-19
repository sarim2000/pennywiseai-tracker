package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The calendar shows a profile's subscriptions as its spending is scoped. */
class UpcomingPaymentsLoaderTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val business = 2L

    private fun account(last4: String, profileId: Long) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = BigDecimal.TEN,
        timestamp = today.atStartOfDay(), profileId = profileId,
    )

    private fun subscription(id: Long, last4: String?) = SubscriptionEntity(
        id = id, merchantName = "Bill $id", amount = BigDecimal.TEN, nextPaymentDate = today.plusDays(3),
        bankName = last4?.let { "Test Bank" }, accountLast4 = last4,
    )

    private fun loader(profileId: Long?, ignoredKeys: Set<String> = emptySet()): UpcomingPaymentsLoader {
        val subscriptions = mockk<SubscriptionRepository>()
        every { subscriptions.getActiveSubscriptions() } returns
            flowOf(listOf(subscription(1, "1111"), subscription(2, "2222"), subscription(3, null), subscription(4, "9999")))
        val recurring = mockk<RecurringTransactionRepository>()
        every { recurring.getActive() } returns flowOf(emptyList())
        val accounts = mockk<AccountBalanceRepository>()
        every { accounts.getAllLatestBalances() } returns
            flowOf(listOf(account("1111", ProfileEntity.PERSONAL_ID), account("2222", business)))
        val transactions = mockk<TransactionRepository>()
        every { transactions.getTransactionsBetweenDates(any<LocalDate>(), any<LocalDate>()) } returns flowOf(emptyList())
        val ignored = mockk<IgnoredAccountsStore>()
        every { ignored.keysFlow } returns flowOf(ignoredKeys)
        val prefs = mockk<UserPreferencesRepository>()
        every { prefs.selectedProfileId } returns flowOf(profileId)
        // A debit card drawing on 2222: its charges are stored under the card's digits.
        val cards = mockk<CardRepository>()
        every { cards.getAllCards() } returns
            flowOf(listOf(CardEntity(cardLast4 = "9999", cardType = CardType.DEBIT, bankName = "Test Bank", accountLast4 = "2222")))
        return UpcomingPaymentsLoader(subscriptions, recurring, accounts, CardBillsLoader(transactions), ignored, prefs, cards)
    }

    private suspend fun names(profileId: Long?, ignoredKeys: Set<String> = emptySet()) =
        loader(profileId, ignoredKeys).observe(today, today.plusDays(10)).first().map { it.name }

    @Test
    fun `a subscription with no known account shows only for Personal`() = runTest {
        assertEquals(listOf("Bill 1", "Bill 3", "Bill 4"), names(ProfileEntity.PERSONAL_ID))
        assertEquals(listOf("Bill 2"), names(business))
        assertEquals(listOf("Bill 1", "Bill 2", "Bill 3", "Bill 4"), names(null))
    }

    @Test
    fun `a subscription paid from an ignored account, or a card linked to it, doesn't show`() = runTest {
        assertEquals(listOf("Bill 1", "Bill 3"), names(null, setOf(IgnoredAccountsStore.keyFor("Test Bank", "2222"))))
    }
}
