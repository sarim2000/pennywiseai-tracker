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
import com.pennywiseai.tracker.presentation.home.assertAmount
import com.pennywiseai.tracker.presentation.home.fakeConvert
import com.pennywiseai.tracker.presentation.home.heroBudget
import com.pennywiseai.tracker.presentation.home.homeBudgetSummary
import com.pennywiseai.tracker.presentation.home.tx
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The Budget widget's figures (#1420). */
class BudgetWidgetFiguresTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val march = BudgetWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 31)

    private fun budget(id: Long, limit: String, categories: List<Pair<String, String>> = emptyList()) = BudgetWithCategories(
        BudgetEntity(
            id = id,
            name = "Budget $id",
            limitAmount = BigDecimal(limit),
            periodType = BudgetPeriodType.MONTHLY,
            startDate = march.start,
            endDate = march.end,
            groupType = BudgetGroupType.LIMIT,
        ),
        categories.map { (name, amount) -> BudgetCategoryEntity(budgetId = id, categoryName = name, budgetAmount = BigDecimal(amount)) },
    )

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
            currentWindows = budgets.associate { it.budget.id to march },
        )
        val summary = homeBudgetSummary(raw, displayCurrency = "INR", baseCurrency = "INR", convert = fakeConvert())
        return widgetBudgetFigures(summary, today) to summary
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
    }
}
