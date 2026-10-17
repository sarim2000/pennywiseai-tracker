package com.pennywiseai.tracker.widget

import android.content.Context
import android.content.res.Resources
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRules
import com.pennywiseai.tracker.presentation.goals.GoalTone
import com.pennywiseai.tracker.presentation.goals.shortLine
import com.pennywiseai.tracker.presentation.goals.statusLine
import com.pennywiseai.tracker.presentation.goals.tone
import com.pennywiseai.tracker.utils.CurrencyFormatter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoalWidgetDataTest {

    private val today = LocalDate.of(2026, 10, 16)

    /** English templates keyed by resource id, formatted like Android does. */
    private val templates = mapOf(
        R.string.goals_reached to "Reached",
        R.string.goals_missed_by to "Missed by %1\$s",
        R.string.goals_behind to "Behind %1\$s",
        R.string.goals_per_month to "%1\$s/mo",
        R.string.goals_left to "%1\$s left",
        R.string.goals_line_join to "%1\$s · %2\$s",
        R.plurals.goals_months_left to "%d months left",
        R.plurals.goals_months_short to "%d mo",
    )
    private val context: Context = mockk {
        val res = mockk<Resources> {
            every { getString(any()) } answers { templates.getValue(firstArg()) }
            every { getString(any(), *anyVararg()) } answers {
                @Suppress("UNCHECKED_CAST")
                templates.getValue(firstArg()).format(*(args[1] as Array<Any?>))
            }
            every { getQuantityString(any(), any(), *anyVararg()) } answers {
                @Suppress("UNCHECKED_CAST")
                templates.getValue(firstArg()).format(*(args[2] as Array<Any?>))
            }
        }
        every { resources } returns res
    }

    private fun inr(v: BigDecimal) = CurrencyFormatter.formatCurrency(v, "INR")

    private fun progress(target: String, saved: String, due: LocalDate, created: LocalDate = today): GoalProgress =
        GoalRules.progress(
            GoalEntity(id = 1, name = "Trip", targetAmount = BigDecimal(target), alreadySaved = BigDecimal(saved), dueDate = due, createdAt = created.atStartOfDay()),
            emptyList(),
            today,
        )

    @Test
    fun `on pace shows per month and months left, the 2x2 shows what's left`() {
        val p = progress("24000", "0", today.plusMonths(12))
        assertEquals("${inr(BigDecimal("2000"))}/mo · 12 months left", p.statusLine(context))
        assertEquals("${inr(BigDecimal("24000"))} left · 12 mo", p.shortLine(context))
        assertEquals(GoalTone.NEUTRAL, p.tone())
    }

    @Test
    fun `behind pace shows how far behind`() {
        val p = progress("12000", "0", today.plusMonths(6), created = today.minusMonths(6))
        assertTrue(p.behind.signum() > 0)
        assertEquals("Behind ${inr(p.behind)}", p.statusLine(context))
        assertEquals(GoalTone.WARNING, p.tone())
    }

    @Test
    fun `reached and missed read the same on every surface`() {
        val reached = progress("1000", "1000", today.plusMonths(3))
        assertEquals("Reached", reached.statusLine(context))
        assertEquals("Reached", reached.shortLine(context))
        assertEquals(GoalTone.SUCCESS, reached.tone())

        val missed = progress("1000", "400", today.minusDays(1), created = today.minusMonths(2))
        assertEquals("Missed by ${inr(BigDecimal("600"))}", missed.statusLine(context))
        assertEquals(missed.statusLine(context), missed.shortLine(context))
        assertEquals(GoalTone.ERROR, missed.tone())
    }

    private fun item(id: Long) = GoalWidgetItem(id, "Goal $id", "#1565C0", 10, 0.1f, "line", "short")

    @Test
    fun `the 2x2 shows its chosen goal, or Removed once it's gone or archived`() {
        val items = listOf(item(1), item(2))
        assertEquals(SingleGoalState.Shown(item(2)), singleGoalState(items, 2))
        assertEquals(SingleGoalState.Removed, singleGoalState(items, 9))
        assertEquals(SingleGoalState.Removed, singleGoalState(emptyList(), 2))
    }

    @Test
    fun `an unconfigured 2x2 shows the soonest goal, or no goals`() {
        assertEquals(SingleGoalState.Shown(item(1)), singleGoalState(listOf(item(1), item(2)), null))
        assertEquals(SingleGoalState.NoGoals, singleGoalState(emptyList(), null))
    }

    @Test
    fun `the snapshot round trips and a corrupt one reads as empty`() {
        val items = listOf(item(1), item(2).copy(tone = GoalTone.WARNING))
        assertEquals(items, GoalWidgetDataStore.decode(GoalWidgetDataStore.encode(items)))
        assertEquals(emptyList<GoalWidgetItem>(), GoalWidgetDataStore.decode("{not json"))
        assertEquals(emptyList<GoalWidgetItem>(), GoalWidgetDataStore.decode(null))
    }
}
