package com.pennywiseai.shared.domain.money

import com.pennywiseai.shared.SharedAccountItem
import com.pennywiseai.shared.SharedBudgetCategoryBreakdown
import com.pennywiseai.shared.SharedBudgetItem
import com.pennywiseai.shared.SharedSubscriptionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTotalsTest {

    private fun account(balanceMinor: Long, currency: String = "INR", isCreditCard: Boolean = false) =
        SharedAccountItem(
            bankName = "Bank",
            accountLast4 = "0000",
            balanceMinor = balanceMinor,
            currency = currency,
            accountType = null,
            isCreditCard = isCreditCard,
            lastUpdatedEpochMillis = 0L
        )

    private fun subscription(amountMinor: Long, state: String = "ACTIVE", currency: String = "INR") =
        SharedSubscriptionItem(
            id = 0L,
            merchantName = "Service",
            amountMinor = amountMinor,
            category = null,
            currency = currency,
            state = state,
            nextPaymentEpochMillis = null,
            createdAtEpochMillis = 0L
        )

    @Test
    fun accountTotals_subtractsCardOutstanding() {
        val totals = MoneyTotals.accountTotals(
            listOf(account(24_567_890), account(4_523_400, isCreditCard = true))
        )
        assertEquals(listOf(CurrencyTotal("INR", 20_044_490)), totals)
    }

    @Test
    fun accountTotals_keepsCurrenciesApart() {
        val totals = MoneyTotals.accountTotals(
            listOf(account(50_000), account(125_000, "USD"), account(10_000, "USD", isCreditCard = true))
        )
        assertEquals(listOf(CurrencyTotal("INR", 50_000), CurrencyTotal("USD", 115_000)), totals)
    }

    @Test
    fun activeSubscriptionTotals_matchesStoredUppercaseState_perCurrency() {
        val totals = MoneyTotals.activeSubscriptionTotals(
            listOf(
                subscription(64_900),
                subscription(17_900, state = "active"),
                subscription(99_900, state = "CANCELLED"),
                subscription(299, currency = "USD")
            )
        )
        assertEquals(listOf(CurrencyTotal("INR", 82_800), CurrencyTotal("USD", 299)), totals)
    }

    @Test
    fun percentUsed_roundsDownTheSameEverywhere() {
        assertEquals(116, MoneyTotals.percentUsed(349_900, 300_000)) // 116.63%
        assertEquals(99, MoneyTotals.percentUsed(99_600, 100_000))
        assertEquals(0, MoneyTotals.percentUsed(500, 0))
        val budget = SharedBudgetItem(
            id = 1, name = "Shopping", limitMinor = 300_000, spentMinor = 349_900,
            periodType = "MONTHLY", groupType = "LIMIT", currency = "INR", isActive = true,
            categoryBreakdowns = listOf(SharedBudgetCategoryBreakdown("Food", 800_000, 101_900))
        )
        assertEquals(116, budget.percentUsed)
        assertEquals(12, budget.categoryBreakdowns.single().percentUsed) // 12.74%
    }

    @Test
    fun spendingTypes_excludeIncomeTransfersAndInvestments() {
        assertTrue(MoneyTotals.isSpendingType("EXPENSE"))
        assertTrue(MoneyTotals.isSpendingType("CREDIT"))
        assertFalse(MoneyTotals.isSpendingType("INCOME"))
        assertFalse(MoneyTotals.isSpendingType("TRANSFER"))
        assertFalse(MoneyTotals.isSpendingType("INVESTMENT"))
    }
}
