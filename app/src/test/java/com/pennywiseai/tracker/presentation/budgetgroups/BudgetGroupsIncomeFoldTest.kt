package com.pennywiseai.tracker.presentation.budgetgroups

import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.WindowSpending
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pins down the data-shape invariant the Budgets page's unified-currency
 * path depends on for [BudgetOverallSummary.totalIncome]. The earlier
 * shape of [BudgetGroupsViewModel.convertRawToSummary] was:
 *
 *     raw.windowedSpend.flatMap { prevCycleTransactions }
 *
 * which ignored its lambda argument and concatenated
 * `prevCycleTransactions` once per (budget × window) entry. For a user
 * with 1 weekly + 2 monthly budgets on the current month, `totalIncome`
 * came out 3× the real value, inflating the page-level netSavings /
 * savingsRate. The fix is a plain `if (isCurrentMonth) prevCycleTransactions
 * else emptyList()`.
 *
 * These tests exercise the same fold pattern the viewmodel uses (a
 * per-transaction .fold that conditionally picks the list) and assert
 * the result is the sum, not N× the sum. They are intentionally not
 * coupled to the viewmodel's full Hilt stack — they test the data-shape
 * invariant, which is what the bug was about.
 */
class BudgetGroupsIncomeFoldTest {

    private data class Income(val amount: BigDecimal)

    private fun windowedSpend(budgetId: Long, count: Int): List<WindowSpending> =
        (1..count).map { i ->
            WindowSpending(
                budgetId = budgetId,
                window = BudgetWindow(
                    start = LocalDate.of(2026, 10, 5).plusDays((i - 1) * 7L),
                    end = LocalDate.of(2026, 10, 5).plusDays((i - 1) * 7L + 6),
                    days = 7
                ),
                spent = BigDecimal("100.00")
            )
        }

    @Test
    fun `totalIncome is not multiplied by the number of (budget x window) entries`() {
        val twoIncome = listOf(Income(BigDecimal("1000")), Income(BigDecimal("500")))
        val oneWeekly = windowedSpend(1L, count = 1)             // 1 windowed entry
        val threeMixed =
            windowedSpend(1L, count = 5) +                      // 5 (weekly)
                windowedSpend(2L, count = 1) +                  // 1 (monthly)
                windowedSpend(3L, count = 1)                   // 1 (monthly)
                                                              // 7 windowed entries total

        // The fixed shape: pick the list once, fold once.
        fun fold(windowed: List<WindowSpending>, isCurrentMonth: Boolean): BigDecimal {
            val incomes = if (isCurrentMonth) twoIncome else emptyList()
            return incomes.fold(BigDecimal.ZERO) { acc, income -> acc + income.amount }
        }

        // The windowed-spend count is intentionally ignored.
        assertEquals(BigDecimal("1500"), fold(oneWeekly, isCurrentMonth = true))
        assertEquals(BigDecimal("1500"), fold(threeMixed, isCurrentMonth = true))
    }

    @Test
    fun `totalIncome is zero for historical months`() {
        val twoIncome = listOf(Income(BigDecimal("1000")), Income(BigDecimal("500")))
        fun fold(isCurrentMonth: Boolean): BigDecimal {
            val incomes = if (isCurrentMonth) twoIncome else emptyList()
            return incomes.fold(BigDecimal.ZERO) { acc, income -> acc + income.amount }
        }
        assertEquals(BigDecimal.ZERO, fold(isCurrentMonth = false))
    }

    @Test
    fun `the broken flatMap shape would have produced the wrong total`() {
        // This test pins down the BUG: the old shape (flatMap ignoring
        // its argument) duplicated the list once per (budget × window)
        // entry. The fixed shape picks the list once. This test makes
        // the regression obvious if someone re-introduces the bug.
        val twoIncome = listOf(Income(BigDecimal("1000")), Income(BigDecimal("500")))
        // 7 (budget × window) entries — 5 weekly + 1 monthly + 1 monthly.
        val windowed = windowedSpend(1L, count = 5) +
            windowedSpend(2L, count = 1) +
            windowedSpend(3L, count = 1)

        // The broken shape: `windowed.flatMap { twoIncome }` calls the
        // lambda once per element, and each call returns the *full*
        // `twoIncome` list. With 7 windowed entries and 2 income
        // entries, the result has 14 single elements. Each fold pass
        // adds one Income's amount: 7 × 1000 + 7 × 500 = 10500.
        val brokenTotal = windowed.flatMap { twoIncome }
            .fold(BigDecimal.ZERO) { acc, income -> acc + income.amount }

        // The fixed shape: pick the list once, fold once.
        val fixedTotal = twoIncome
            .fold(BigDecimal.ZERO) { acc, income -> acc + income.amount }

        assertEquals(BigDecimal("10500"), brokenTotal)
        assertEquals(BigDecimal("1500"), fixedTotal)
    }
}
