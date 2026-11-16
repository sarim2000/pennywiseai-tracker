package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BudgetStatusesTest {
    private val today = LocalDate.parse("2026-11-16")

    private fun group(
        id: Long,
        type: BudgetGroupType = BudgetGroupType.LIMIT,
        start: String = "2026-11-01",
        end: String = "2026-11-30",
    ) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(
                id = id, name = "Budget $id", limitAmount = BigDecimal("500"), periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.parse(start), endDate = LocalDate.parse(end), groupType = type,
            ),
            emptyList(),
        ),
        categorySpending = emptyList(),
        totalBudget = BigDecimal("500"), totalActual = BigDecimal("200"), remaining = BigDecimal("300"),
        percentageUsed = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 14, daysElapsed = 16,
        windowStart = LocalDate.parse(start), windowEnd = LocalDate.parse(end), windowDays = 30,
    )

    private fun summary(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 14, currency = "JOD",
    )

    @Test
    fun `only LIMIT budgets whose window holds today, in the summary's currency`() {
        val statuses = budgetStatuses(
            summary(
                group(1),
                group(2, type = BudgetGroupType.TARGET),
                group(3, type = BudgetGroupType.EXPECTED),
                group(4, start = "2026-11-01", end = "2026-11-15"),
                group(5, start = "2026-11-17", end = "2026-11-23"),
                group(6, start = "2026-11-16", end = "2026-11-16"),
            ),
            today,
        )
        assertEquals(listOf(1L, 6L), statuses.map { it.id })
        assertEquals(setOf("JOD"), statuses.map { it.currency }.toSet())
        val first = statuses.first()
        assertEquals(BigDecimal("200"), first.spent)
        assertEquals(BigDecimal("500"), first.limit)
        assertEquals(LocalDate.parse("2026-11-01"), first.windowStart)
    }
}
