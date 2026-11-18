package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.ledger.breakdownByCurrency
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** Home's "Spent" follows an account's ignore status while the screen is open (#826). */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeTrackedRowsTest {
    private fun account(last4: String) = AccountBalanceEntity(
        bankName = "Bank",
        accountLast4 = last4,
        balance = BigDecimal.ZERO,
        timestamp = LocalDateTime.of(2026, 3, 1, 12, 0),
    )

    private fun spend(amount: String, last4: String) =
        tx(amount, TransactionType.EXPENSE).copy(bankName = "Bank", accountNumber = last4)

    @Test fun `ignoring an account takes its rows out of Spent, un-ignoring puts them back`() = runTest {
        val rows = listOf(
            spend("100", "1111"),
            spend("50", "9999"),
            spend("30", "4444"), // a card drawn on 9999, stored under the card's digits
        )
        val ignored = MutableStateFlow(emptySet<String>())
        // Home's "Spent" pipeline: the tracked rows, then the per-currency breakdown.
        val spent = trackedRowsFilter(
            profileId = flowOf(null),
            balances = flowOf(listOf(account("1111"), account("9999"))),
            ignored = ignored,
            cards = flowOf(listOf(CardEntity(cardLast4 = "4444", cardType = CardType.DEBIT, bankName = "Bank", accountLast4 = "9999"))),
        ).map { keep -> breakdownByCurrency(rows.filter(keep).forTotals()).getValue("INR").expenses.toPlainString() }

        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { spent.collect { seen += it } }
        ignored.value = setOf(IgnoredAccountsStore.keyFor("Bank", "9999"))
        ignored.value = emptySet()

        assertEquals(listOf("180", "100", "180"), seen)
    }
}
