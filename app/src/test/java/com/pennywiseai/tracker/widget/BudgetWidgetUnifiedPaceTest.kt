package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.repository.BudgetCarry
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.WindowSpending
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The owl's pace in unified mode, through the worker's own totals: the page holds
 * every budget's windows this month, but each budget is paced on its own window.
 */
class BudgetWidgetUnifiedPaceTest {

    @Test
    fun `last week's spending doesn't slow this week's owl`() = runTest {
        val today = LocalDate.parse("2026-11-17")
        val thisWeek = window("2026-11-16", "2026-11-22")
        val lastWeek = window("2026-11-09", "2026-11-15")
        val earlier = expense("300", "2026-11-10")
        val weekly = budget(limit = "700")
        val raw = raw(
            today,
            weekly,
            current = thisWeek,
            windows = listOf(WindowSpending(1, thisWeek, emptyList()), WindowSpending(1, lastWeek, listOf(earlier)))
        )

        val pace = paceGroups(raw)
        assertAmount("0", pace.single().totalActual)
        assertEquals(false, widgetOverPace(today, pace))
    }

    @Test
    fun `a cycle from the 25th counts neither the page's spending nor its extra budget`() = runTest {
        // Day 3 of 30 on a 3,000 Food limit: an even spend is 300, a day's budget 100.
        val today = LocalDate.parse("2026-11-27")
        val cycle = window("2026-11-25", "2026-12-24")
        val calendar = window("2026-11-01", "2026-11-30")
        val beforeCycle = listOf(
            expense("1000", "2026-11-10"),
            // Extra budget from before the cycle: it doesn't raise this cycle's limit.
            TransactionWithSplits(
                transaction(
                    "5000", "2026-11-12", TransactionType.INCOME,
                    budgetCategory = "Food", impact = BudgetImpactType.ADD_TO_LIMIT
                ),
                emptyList()
            )
        )
        val inCycle = listOf(expense("200", "2026-11-26"))
        val food = budget(limit = "0", categories = listOf("Food" to "3000"))
        // A second, calendar-month budget is why the page reaches back to the 1st.
        val monthly = budget(id = 2, limit = "100000")
        val raw = raw(
            today,
            food,
            monthly,
            current = cycle,
            otherCurrent = calendar,
            windows = listOf(
                WindowSpending(1, cycle, inCycle),
                WindowSpending(2, calendar, beforeCycle + inCycle)
            )
        )

        val foodPace = paceGroups(raw).first { it.group.budget.id == 1L }
        assertAmount("200", foodPace.totalActual)
        assertAmount("3000", foodPace.totalBudget)
        assertEquals(false, widgetOverPace(today, listOf(foodPace)))
        // Counted over the page, as the figures still are (#1420), it would be over pace.
        val pageFood = unifiedGroupSpending(raw, carries(raw), asIs, { it }, ownWindows = false)
            .first { it.group.budget.id == 1L }
        assertEquals(true, widgetOverPace(today, listOf(pageFood)))
    }

    private val asIs: suspend (String, BigDecimal) -> BigDecimal? = { _, amount -> amount }

    private suspend fun paceGroups(raw: BudgetGroupSpendingRaw) =
        unifiedGroupSpending(raw, carries(raw), asIs, { it }, ownWindows = true)

    private fun carries(raw: BudgetGroupSpendingRaw) =
        raw.budgetsWithCategories.associate { it.budget.id to BudgetCarry.none("INR") }

    private fun raw(
        today: LocalDate,
        vararg budgets: BudgetWithCategories,
        current: BudgetWindow,
        otherCurrent: BudgetWindow = current,
        windows: List<WindowSpending>
    ) = BudgetGroupSpendingRaw(
        budgetsWithCategories = budgets.toList(),
        windowedSpend = windows,
        daysElapsed = 0,
        daysRemaining = 0,
        today = today,
        globalStartDay = 1,
        isCurrentMonth = true,
        allTransactions = windows.flatMap { it.transactions }.distinctBy { it.transaction.id },
        currentWindows = budgets.associate { it.budget.id to if (it.budget.id == 1L) current else otherCurrent }
    )

    private fun budget(id: Long = 1, limit: String, categories: List<Pair<String, String>> = emptyList()) =
        BudgetWithCategories(
            BudgetEntity(
                id = id, name = "Budget $id", limitAmount = BigDecimal(limit), periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.parse("2026-11-01"), endDate = LocalDate.parse("2026-11-30"), currency = "INR"
            ),
            categories.map { (name, amount) -> BudgetCategoryEntity(budgetId = id, categoryName = name, budgetAmount = BigDecimal(amount)) }
        )

    private fun window(start: String, end: String): BudgetWindow {
        val from = LocalDate.parse(start)
        val to = LocalDate.parse(end)
        return BudgetWindow(from, to, (to.toEpochDay() - from.toEpochDay() + 1).toInt())
    }

    private fun expense(amount: String, date: String) =
        TransactionWithSplits(transaction(amount, date, TransactionType.EXPENSE), emptyList())

    private var nextId = 1L

    private fun transaction(
        amount: String,
        date: String,
        type: TransactionType,
        budgetCategory: String? = null,
        impact: BudgetImpactType? = null
    ): TransactionEntity {
        val id = nextId++
        return TransactionEntity(
            id = id,
            amount = BigDecimal(amount),
            merchantName = "Test shop",
            category = "Food",
            transactionType = type,
            dateTime = LocalDate.parse(date).atTime(12, 0),
            transactionHash = "test-$id",
            currency = "INR",
            budgetCategory = budgetCategory,
            budgetImpactType = impact
        )
    }

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
}
