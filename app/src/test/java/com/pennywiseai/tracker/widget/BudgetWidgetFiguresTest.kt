package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.homeBudgetSummary
import com.pennywiseai.tracker.presentation.home.assertAmount
import com.pennywiseai.tracker.presentation.home.fakeConvert
import com.pennywiseai.tracker.presentation.home.heroBudget
import com.pennywiseai.tracker.presentation.home.tx
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The Budget widget's figures (#1420). */
class BudgetWidgetFiguresTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val march = BudgetWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 31)
    private val week = BudgetWindow(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15), 7)

    private fun budget(
        id: Long,
        limit: String,
        categories: List<Pair<String, String>> = emptyList(),
        window: BudgetWindow = march,
    ) = BudgetWithCategories(
        BudgetEntity(
            id = id,
            name = "Budget $id",
            limitAmount = BigDecimal(limit),
            periodType = if (window == week) BudgetPeriodType.WEEKLY else BudgetPeriodType.MONTHLY,
            startDate = window.start,
            endDate = window.end,
            groupType = BudgetGroupType.LIMIT,
        ),
        categories.map { (name, amount) -> BudgetCategoryEntity(budgetId = id, categoryName = name, budgetAmount = BigDecimal(amount)) },
    )

    /**
     * The widget's figures for [budgets], with ₹50,000 of income, the page's 22 days left
     * in March, the page's limits added up as its savings spend, and ₹1,000 saved last period.
     */
    private suspend fun figures(vararg budgets: BudgetWithCategories): Pair<BudgetWidgetData, BudgetOverallSummary> {
        val raw = BudgetGroupSpendingRaw(
            budgetsWithCategories = budgets.toList(),
            windowedSpend = emptyList(),
            daysElapsed = 0,
            daysRemaining = 0,
            today = today,
            globalStartDay = 1,
            isCurrentMonth = true,
            allTransactions = listOf(
                tx("5000", TransactionType.EXPENSE, category = "Food"),
                tx("3000", TransactionType.EXPENSE, category = "Shopping"),
            ).map { TransactionWithSplits(it, emptyList()) },
            currentWindows = budgets.associate { it.budget.id to if (it.budget.periodType == BudgetPeriodType.WEEKLY) week else march },
        )
        val summary = homeBudgetSummary(raw, displayCurrency = "INR", baseCurrency = "INR", convert = fakeConvert())
        val widget = widgetBudgetFigures(
            summary,
            today,
            pageDaysRemaining = 22,
            income = BigDecimal("50000"),
            incomeCurrency = "INR",
            pageLimitSpent = summary.totalLimitSpent,
            prevSavings = BigDecimal("1000"),
        )
        return widget to summary
    }

    @Test fun `an all-spending budget alone is the total, not added to an overlapping category budget`() = runTest {
        val (widget, summary) = figures(budget(1, "40000"), budget(2, "16500", listOf("Food" to "16500")))
        assertAmount("40000", widget.totalLimit)
        assertAmount("8000", widget.totalSpent)
        assertAmount("32000", widget.remaining)
        assertEquals(20f, widget.percentageUsed, 0.01f)
        // 32,000 over the 22 days left in March.
        assertAmount("1455", widget.dailyAllowance)
        // The figure Home's hero shows.
        val hero = summary.heroBudget(today)!!
        assertAmount(hero.limit.toPlainString(), widget.totalLimit)
        assertAmount(hero.remaining.toPlainString(), widget.remaining)
    }

    @Test fun `without an all-spending budget the category budgets add up`() = runTest {
        val (widget, _) = figures(
            budget(1, "16500", listOf("Food" to "16500")),
            budget(2, "5000", listOf("Shopping" to "5000")),
        )
        assertAmount("21500", widget.totalLimit)
        assertAmount("8000", widget.totalSpent)
        assertAmount("13500", widget.remaining)
        // Saved / Overspent: income less the page's limits, its arrow against last period.
        assertAmount("42000", widget.netSavings)
        assertAmount("41000", widget.savingsDelta!!)
    }

    @Test fun `with no all-spending budget the per-day figure spreads what's left over the page's days`() = runTest {
        // The weekly budget comes first: Home's summary divides by its 6 days left.
        val (widget, summary) = figures(
            budget(1, "16500", listOf("Food" to "16500"), window = week),
            budget(2, "5000", listOf("Shopping" to "5000")),
        )
        assertAmount("13500", widget.remaining)
        // 13,500 over the page's 22 days left, as the widget did before (#1420).
        assertAmount("614", widget.dailyAllowance)
        assertAmount("2250", summary.dailyAllowance)
    }

    @Test fun `savings count the all-spending budget's spend once, with no arrow`() = runTest {
        // The issue's setup: an overall ₹40,000 limit and a Food ₹16,500 one.
        val (widget, summary) = figures(budget(1, "40000"), budget(2, "16500", listOf("Food" to "16500")))
        // The page's limits added up count Food's ₹5,000 twice.
        assertAmount("13000", summary.totalLimitSpent)
        assertAmount("42000", widget.netSavings)
        assertEquals(84f, widget.savingsRate, 0.01f)
        // Last period's figure added up every limit, so no arrow against it.
        assertNull(widget.savingsDelta)
    }
}
