package com.pennywiseai.tracker.presentation.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** What the Chat tab hears after money goes into a goal: progress, the milestone, or nothing. */
class GoalMomentsTest {
    private val today = LocalDate.of(2026, 11, 17)

    private fun progress(saved: String, target: String = "1000", archived: Boolean = false, due: LocalDate = today.plusMonths(6)): GoalProgress {
        val goal = GoalEntity(
            id = 1, name = "Goal", targetAmount = BigDecimal(target), currency = "INR", dueDate = due,
            isArchived = archived, createdAt = today.minusMonths(1).atStartOfDay()
        )
        val rows = listOf(GoalContributionEntity(id = 1, goalId = 1, amount = BigDecimal(saved)))
        return GoalRules.progress(goal, rows, today)
    }

    private val entered = Money(BigDecimal("300"), "INR")

    @Test
    fun `a deposit that takes the goal to its target reaches it`() {
        assertEquals(MascotMoment.GoalReached, goalDepositMoment(progress("700"), progress("1000"), entered))
        // Past it counts too.
        assertEquals(MascotMoment.GoalReached, goalDepositMoment(progress("800"), progress("1100"), entered))
    }

    @Test
    fun `a missed goal that a deposit fills is reached`() {
        val due = today.minusDays(3)
        assertEquals(MascotMoment.GoalReached, goalDepositMoment(progress("700", due = due), progress("1000", due = due), entered))
    }

    @Test
    fun `a deposit short of the target is progress, with the amount as typed`() {
        val usd = Money(BigDecimal("3.50"), "USD")
        assertEquals(MascotMoment.GoalContributed(usd), goalDepositMoment(progress("100"), progress("400"), usd))
    }

    @Test
    fun `a goal already at its target hears nothing more`() {
        assertNull(goalDepositMoment(progress("1000"), progress("1300"), entered))
        assertNull(goalDepositMoment(progress("1200"), progress("1500"), entered))
    }

    @Test
    fun `an archived goal never reaches, so a deposit into it is only progress`() {
        assertEquals(
            MascotMoment.GoalContributed(entered),
            goalDepositMoment(progress("700", archived = true), progress("1000", archived = true), entered),
        )
    }

    @Test
    fun `no amount is nothing`() {
        assertNull(goalDepositMoment(progress("100"), progress("100"), Money(BigDecimal.ZERO, "INR")))
    }

    @Test
    fun `reached between two reads only from an active goal of the same id`() {
        assertTrue(GoalRules.reachedBetween(progress("700"), progress("1000")))
        assertFalse(GoalRules.reachedBetween(progress("1000"), progress("1000")))
        assertFalse(GoalRules.reachedBetween(progress("700"), progress("900")))
        assertFalse(GoalRules.reachedBetween(progress("700", archived = true), progress("1000")))
        val other = progress("1000").let { it.copy(goal = it.goal.copy(id = 2)) }
        assertFalse(GoalRules.reachedBetween(progress("700"), other))
    }
}
