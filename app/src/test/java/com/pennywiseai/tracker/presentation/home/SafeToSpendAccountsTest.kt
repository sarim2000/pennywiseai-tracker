package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.domain.spending.SafeToSpend
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun summary(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 9, currency = "INR",
    )

    private fun cap(vararg groups: BudgetGroupSpending) = summary(*groups).safeToSpendCap(today)

    @Test fun `cap is the tightest all-expense spending limit running today`() {
        val food = BudgetCategoryEntity(budgetId = 0, categoryName = "Food", budgetAmount = BigDecimal.ONE)
        val ended = budget("100", start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 9, 30))
        assertEquals(
            Money(BigDecimal("5000"), "INR"),
            cap(budget("8000"), budget("5000"), budget("10", listOf(food)), budget("20", type = BudgetGroupType.TARGET), ended),
        )
        assertNull(cap(ended))
    }

    private val periodEnd = LocalDate.of(2026, 10, 31)

    /** Safe to spend as Home works it out: the balances, capped by what the loaded budgets leave. */
    private fun safeToSpend(budgets: BudgetOverallSummary) = SafeToSpend.calculate(
        listOf(Money(BigDecimal("96869.54"), "INR")), emptyList(), today, periodEnd, budgets.safeToSpendCap(today),
    ).single()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `Safe to spend waits for the budgets instead of going out uncapped`() = runTest {
        val state = MutableStateFlow(HomeUiState())
        val seen = mutableListOf<SafeToSpend>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            state.loadedBudgets().map(::safeToSpend).toList(seen)
        }
        // A cold start's order: balances and the month land before the budgets.
        state.value = state.value.copy(isBalanceReady = true, isMonthReady = true)
        assertEquals(emptyList<SafeToSpend>(), seen)

        state.value = state.value.copy(budgetSummary = summary(budget("984")))
        assertEquals(1, seen.size)
        assertTrue(seen.single().isCapped)
        assertAmount("984", seen.single().amount)
    }

    @Test fun `hero waits for the month, the budgets and Safe to spend`() {
        val account = account("Bank", "96869.54")
        val budgets = summary(budget("984"))
        val loaded = SafeToSpendUi(safeToSpend(budgets), emptyList(), periodEnd, listOf(account), emptySet(), emptyList())
        // Balances first: before the fix this alone showed "Spent this month ₹0".
        val balances = HomeUiState(isBalanceReady = true, accountBalances = listOf(account))
        assertFalse(balances.isHeroReady(null))
        assertFalse(balances.copy(isMonthReady = true).isHeroReady(null))
        val withBudgets = balances.copy(isMonthReady = true, budgetSummary = budgets)
        assertFalse(withBudgets.isHeroReady(null))
        assertTrue(withBudgets.isHeroReady(loaded))
        assertFalse(withBudgets.copy(isMonthReady = false).isHeroReady(loaded))
        assertFalse(withBudgets.copy(budgetSummary = null).isHeroReady(loaded))
        assertFalse(withBudgets.copy(isBalanceReady = false).isHeroReady(loaded))
    }

    @Test fun `new user's hero shows without accounts or a budget`() =
        // No accounts: no Safe to spend to wait for. A summary with no groups: no budget.
        assertTrue(HomeUiState(isBalanceReady = true, isMonthReady = true, budgetSummary = summary()).isHeroReady(null))
}
