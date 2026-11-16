package com.pennywiseai.tracker.domain.share

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.domain.review.CategoryLine
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ShareMathTest {

    private fun bd(value: String) = BigDecimal(value)

    private fun currency(
        code: String = "JOD",
        spent: String = "0",
        income: String = "0",
        previous: String? = null,
        categories: List<CategoryLine> = emptyList(),
        top: List<CategoryLine> = emptyList(),
    ) = CurrencyReview(
        currency = code, spent = bd(spent), previousSpent = previous?.let(::bd), income = bd(income),
        transactionCount = 1, categories = categories, topCategories = top, changes = categories, months = emptyList(),
    )

    private fun month(start: String, vararg currencies: CurrencyReview): PeriodReview {
        val first = LocalDate.parse(start)
        val range = ReviewRange(
            ReviewKind.MONTH, first, first.plusMonths(1).minusDays(1),
            first.minusMonths(1), first.minusDays(1), isComplete = true,
        )
        return PeriodReview(range, currencies.toList(), first, first, hasOlder = true)
    }

    // --- Savings rate

    @Test
    fun `savings rate is the share of income not spent`() {
        assertEquals(25, savingsRatePercent(bd("2000"), bd("1500")))
        assertEquals(0, savingsRatePercent(bd("1000"), bd("1000")))
        // Rounded half up to a whole percent: 1/3 kept is 33%, 2/3 is 67%.
        assertEquals(33, savingsRatePercent(bd("3"), bd("2")))
        assertEquals(67, savingsRatePercent(bd("3"), bd("1")))
    }

    @Test
    fun `spending beyond income is a negative rate, said as it is`() {
        assertEquals(-20, savingsRatePercent(bd("1000"), bd("1200")))
    }

    @Test
    fun `no income means no rate rather than a made-up one`() {
        assertNull(savingsRatePercent(BigDecimal.ZERO, bd("500")))
        assertNull(savingsRatePercent(bd("-10"), BigDecimal.ZERO))
    }

    @Test
    fun `the trend is per currency, oldest first, and skips periods without income`() {
        val reviews = listOf(
            month("2026-10-01", currency("JOD", spent = "300", income = "1000"), currency("USD", spent = "90", income = "100")),
            month("2026-08-01", currency("JOD", spent = "500", income = "1000")),
            // September: spending but no income in JOD, so no rate to plot.
            month("2026-09-01", currency("JOD", spent = "200")),
        )
        val trend = savingsTrend(reviews, "JOD")
        assertEquals(listOf(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-10-01")), trend.map { it.start })
        assertEquals(listOf(50, 70), trend.map { it.ratePercent })
        // The other currency's figures never enter it.
        assertEquals(listOf(10), savingsTrend(reviews, "USD").map { it.ratePercent })
    }

    @Test
    fun `the previous rate is compared only when the previous period is covered`() {
        val september = month("2026-09-01", currency("JOD", spent = "500", income = "1000"), currency("USD", spent = "10", income = "100"))
        // Covered: the review found a fair comparison, so last period's rate in the same currency.
        assertEquals(50, previousSavingsRate(currency("JOD", spent = "300", income = "1000", previous = "500"), september))
        // Records start too late in September: the review left out the comparison, and so does the card.
        assertNull(previousSavingsRate(currency("JOD", spent = "300", income = "1000"), september))
        assertNull(previousSavingsRate(currency("JOD", spent = "300", income = "1000", previous = "500"), null))
    }

    // --- Spending change

    @Test
    fun `drivers are the biggest moves either way, unchanged categories left out`() {
        val review = currency(
            spent = "1000", previous = "900",
            categories = listOf(
                CategoryLine("Food", bd("400"), bd("300")),
                CategoryLine("Rent", bd("500"), bd("500")),
                CategoryLine("Fuel", bd("50"), bd("200")),
                CategoryLine("Travel", bd("50"), bd("0")),
            ),
        )
        assertEquals(listOf("Fuel", "Food", "Travel"), spendingDrivers(review).map { it.name })
        assertEquals(listOf("Fuel"), spendingDrivers(review, limit = 1).map { it.name })
    }

    @Test
    fun `no comparison means no drivers`() {
        val review = currency(spent = "1000", categories = listOf(CategoryLine("Food", bd("400"), null)))
        assertTrue(spendingDrivers(review).isEmpty())
    }

    @Test
    fun `a category's change is relative to what it was, and new ones have none`() {
        assertEquals(33, categoryChangePercent(CategoryLine("Food", bd("400"), bd("300"))))
        assertEquals(-75, categoryChangePercent(CategoryLine("Fuel", bd("50"), bd("200"))))
        assertNull(categoryChangePercent(CategoryLine("Travel", bd("50"), bd("0"))))
        assertNull(categoryChangePercent(CategoryLine("Travel", bd("50"), null)))
    }

    @Test
    fun `top categories carry their share of the period's spending`() {
        val review = currency(
            spent = "1000",
            top = listOf(CategoryLine("Rent", bd("500"), null), CategoryLine("Food", bd("333"), null)),
        )
        assertEquals(listOf(50, 33), topCategoryShares(review).map { it.sharePercent })
    }

    // --- Budget

    private fun limit(
        budget: String,
        actual: String,
        type: BudgetGroupType = BudgetGroupType.LIMIT,
        windowDays: Int = 31,
    ) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(
                name = "Budget", limitAmount = bd(budget), periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.parse("2026-10-01"), endDate = LocalDate.parse("2026-10-31"), groupType = type,
            ),
            emptyList(),
        ),
        categorySpending = emptyList(),
        totalBudget = bd(budget),
        totalActual = bd(actual),
        remaining = bd(budget) - bd(actual),
        percentageUsed = 0f,
        dailyAllowance = BigDecimal.ZERO,
        daysRemaining = 0,
        daysElapsed = 31,
        windowDays = windowDays,
    )

    private fun summary(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 0, currency = "JOD",
    )

    @Test
    fun `a budget result sums spending limits only, and counts the ones kept`() {
        val result = budgetResult(
            summary(
                limit("500", "400"),
                limit("300", "350"),
                limit("200", "200"),
                // A savings target isn't a limit, however it went.
                limit("1000", "50", BudgetGroupType.TARGET),
            )
        )!!
        assertEquals(bd("950"), result.spent)
        assertEquals(bd("1000"), result.limit)
        assertEquals(95, result.usedPercent)
        assertEquals(2, result.kept)
        assertEquals(3, result.total)
        assertFalse(result.isOver)
        assertEquals(bd("50"), result.difference)
        assertEquals("JOD", result.currency)
    }

    @Test
    fun `going over is reported as over, by how much`() {
        val result = budgetResult(summary(limit("500", "650")))!!
        assertTrue(result.isOver)
        assertEquals(130, result.usedPercent)
        assertEquals(bd("150"), result.difference)
    }

    @Test
    fun `a limit with no window in the month counts toward nothing`() {
        // A one-time limit that ended last month: the repository gives it an empty window.
        val result = budgetResult(summary(limit("500", "400"), limit("300", "0", windowDays = 0)))!!
        assertEquals(bd("500"), result.limit)
        assertEquals(bd("400"), result.spent)
        assertEquals(1, result.total)
        assertNull(budgetResult(summary(limit("300", "0", windowDays = 0))))
    }

    @Test
    fun `no spending limit means no budget card`() {
        assertNull(budgetResult(summary()))
        assertNull(budgetResult(summary(limit("0", "40"), limit("100", "10", BudgetGroupType.TARGET))))
    }

    @Test
    fun `a rate below -10000 percent is unavailable, so changes between rates can't wrap`() {
        assertNull(savingsRatePercent(BigDecimal("0.01"), BigDecimal("300000")))
        assertNull(savingsRatePercent(BigDecimal("0.01"), BigDecimal("214748.37")))
        assertEquals(-10_000, savingsRatePercent(BigDecimal("1"), BigDecimal("101")))
    }
}
