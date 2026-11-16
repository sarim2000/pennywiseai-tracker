package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalReserveTest {
    private val today = LocalDate.of(2026, 11, 16)
    private var nextId = 1L

    /** 12 months out, so it needs [target] / 12 a month. */
    private fun goal(target: String, currency: String = "INR", saved: String = "0", archived: Boolean = false) =
        GoalRules.progress(
            GoalEntity(
                id = nextId++, name = "Goal", targetAmount = BigDecimal(target), currency = currency,
                dueDate = today.plusMonths(12), alreadySaved = BigDecimal(saved), isArchived = archived,
                createdAt = today.minusMonths(1).atStartOfDay(),
            ),
            emptyList(),
            today,
        )

    @Test
    fun `sets aside each currency's monthly saving for the days left in the cycle`() {
        // Cycle 1–30 November, 15 of its 30 days left from the 16th.
        val reserve = GoalReserve.of(
            listOf(goal("12000"), goal("24000"), goal("1200", currency = "USD")),
            emptyList(),
            today, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
        )

        assertEquals(setOf(Money(BigDecimal("1500"), "INR"), Money(BigDecimal("50"), "USD")), reserve.toSet())
    }

    @Test
    fun `on payday the whole month's saving is set aside`() {
        val payday = LocalDate.of(2026, 11, 25)
        val (start, end) = BudgetCycle.currentCycle(payday, 25)

        assertEquals(payday, start)
        assertEquals(listOf(Money(BigDecimal("1000"), "INR")), GoalReserve.of(listOf(goal("12000")), emptyList(), payday, start, end))
    }

    @Test
    fun `reached and archived goals set nothing aside`() {
        val reserve = GoalReserve.of(
            listOf(goal("1000", saved = "1000"), goal("12000", archived = true)),
            emptyList(),
            today, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
        )

        assertTrue(reserve.isEmpty())
    }

    @Test
    fun `what was already put in this cycle comes off the month's saving`() {
        val start = LocalDate.of(2026, 11, 1)
        val end = LocalDate.of(2026, 11, 30)
        val done = goal("12000")
        val half = goal("12000")
        val contributions = listOf(
            GoalContributionEntity(goalId = done.goal.id, amount = BigDecimal("1000"), date = LocalDate.of(2026, 11, 3)),
            GoalContributionEntity(goalId = half.goal.id, amount = BigDecimal("500"), date = LocalDate.of(2026, 11, 3)),
            // Last cycle's doesn't count toward this one.
            GoalContributionEntity(goalId = half.goal.id, amount = BigDecimal("500"), date = LocalDate.of(2026, 10, 28)),
        )

        // Half's 1000 a month less the 500 put in, for 15 of 30 days: 250. Done's is saved.
        assertEquals(listOf(Money(BigDecimal("250"), "INR")), GoalReserve.of(listOf(done, half), contributions, today, start, end))
    }
}
