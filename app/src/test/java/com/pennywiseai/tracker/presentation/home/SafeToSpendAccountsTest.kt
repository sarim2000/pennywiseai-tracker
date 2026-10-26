package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class SafeToSpendAccountsTest {
    private fun account(name: String, balance: String, type: String? = null, last4: String = "1234") =
        AccountBalanceEntity(
            bankName = name,
            accountLast4 = last4,
            balance = BigDecimal(balance),
            timestamp = LocalDateTime.of(2026, 10, 24, 12, 0),
            accountType = type,
        )

    private fun counted(vararg accounts: AccountBalanceEntity, excluded: Set<String> = emptySet()) =
        countedInSafeToSpend(accounts.toList(), excluded).map { it.bankName }

    @Test fun `negative cash account is left out`() =
        assertEquals(listOf("Bank"), counted(account("Bank", "9600", "SAVINGS"), account("Home box", "-795", "CASH", "CASH")))

    @Test fun `negative wallet is left out`() =
        assertEquals(emptyList<String>(), counted(account("eMola", "-10", last4 = AccountBalanceEntity.WALLET_ACCOUNT_MARKER)))

    @Test fun `overdrawn bank account still counts`() =
        assertEquals(listOf("Bank", "SMS bank"), counted(account("Bank", "-500", "CURRENT"), account("SMS bank", "-20")))

    @Test fun `positive cash account counts`() =
        assertEquals(listOf("Wallet"), counted(account("Wallet", "300", "CASH", "CASH")))

    @Test fun `user-excluded account is left out`() =
        assertEquals(listOf("B"), counted(account("A", "100"), account("B", "200"), excluded = setOf("A_1234")))

    @Test fun `no spending last month hides the change`() =
        assertNull(monthlyChangePercent(BigDecimal("1705"), BigDecimal.ZERO))

    @Test fun `zero with a scale is still no spending`() =
        assertNull(monthlyChangePercent(BigDecimal("1705"), BigDecimal("0.00")))

    @Test fun `change is a whole percent of last month`() {
        assertEquals(50, monthlyChangePercent(BigDecimal("150"), BigDecimal("100")))
        assertEquals(-25, monthlyChangePercent(BigDecimal("75"), BigDecimal("100")))
        assertEquals(0, monthlyChangePercent(BigDecimal("100"), BigDecimal("100")))
    }

    private val today = LocalDate.of(2026, 10, 23)

    private fun budget(
        remaining: String,
        categories: List<BudgetCategoryEntity> = emptyList(),
        type: BudgetGroupType = BudgetGroupType.LIMIT,
        start: LocalDate = LocalDate.of(2026, 10, 1),
        end: LocalDate = LocalDate.of(2026, 10, 31),
    ) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(name = "B", limitAmount = BigDecimal.TEN, periodType = BudgetPeriodType.MONTHLY, startDate = start, endDate = end, groupType = type),
            categories,
        ),
        categorySpending = emptyList(),
        totalBudget = BigDecimal.TEN, totalActual = BigDecimal.ZERO, remaining = BigDecimal(remaining),
        percentageUsed = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 9, daysElapsed = 23,
        isTrackingAllExpenses = categories.isEmpty(),
        windowStart = start, windowEnd = end, windowDays = (end.toEpochDay() - start.toEpochDay() + 1).toInt(),
    )

    private fun cap(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 9, currency = "INR",
    ).safeToSpendCap(today)

    @Test fun `cap is the tightest all-expense spending limit running today`() {
        val food = BudgetCategoryEntity(budgetId = 0, categoryName = "Food", budgetAmount = BigDecimal.ONE)
        val ended = budget("100", start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 9, 30))
        assertEquals(
            Money(BigDecimal("5000"), "INR"),
            cap(budget("8000"), budget("5000"), budget("10", listOf(food)), budget("20", type = BudgetGroupType.TARGET), ended),
        )
        assertNull(cap(ended))
    }
}
