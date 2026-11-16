package com.pennywiseai.tracker.presentation.share

import androidx.lifecycle.SavedStateHandle
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ShareStudioSelectionTest {

    private val today = LocalDate.parse("2026-11-16")

    @Test
    fun `amounts start off and everything else on`() {
        assertFalse(ShareDetail.AMOUNTS in DEFAULT_SHARE_DETAILS)
        assertTrue(ShareDetail.PERCENTAGES in DEFAULT_SHARE_DETAILS)
    }

    @Test
    fun `a card that needs a figure keeps percentages when both figures are off`() {
        val none = DEFAULT_SHARE_DETAILS - ShareDetail.PERCENTAGES
        assertTrue(ShareDetail.PERCENTAGES in effectiveDetails(ShareKind.SAVINGS, none))
        assertTrue(ShareDetail.PERCENTAGES in effectiveDetails(ShareKind.BUDGET, none))
        // A recap leads with a count, so it can go without either.
        assertFalse(ShareDetail.PERCENTAGES in effectiveDetails(ShareKind.RECAP, none))
        // Amounts alone are a figure.
        val amountsOnly = none + ShareDetail.AMOUNTS
        assertEquals(setOf(ShareDetail.AMOUNTS), effectiveDetails(ShareKind.SAVINGS, amountsOnly) intersect setOf(ShareDetail.AMOUNTS, ShareDetail.PERCENTAGES))
    }

    @Test
    fun `only the details a card offers are drawn`() {
        val drawn = effectiveDetails(ShareKind.SPENDING, ShareDetail.entries.toSet())
        assertFalse(ShareDetail.GOAL_NAME in drawn)
        assertFalse(ShareDetail.CHART in drawn)
        assertTrue(ShareDetail.CATEGORIES in drawn)
    }

    @Test
    fun `Home opens on a recap of the last finished cycle`() {
        val selection = routeSelection(SavedStateHandle(), today)
        assertEquals(ShareKind.RECAP, selection.kind)
        assertEquals(ReviewSelection(ReviewKind.MONTH, -1, today), selection.period)
        assertNull(selection.goalId)
    }

    @Test
    fun `a review opens on its own period, never one still running`() {
        val week = routeSelection(SavedStateHandle(mapOf("kind" to "RECAP", "reviewKind" to "WEEK", "offset" to -3)), today)
        assertEquals(ReviewSelection(ReviewKind.WEEK, -3, today), week.period)
        val tooNew = routeSelection(SavedStateHandle(mapOf("reviewKind" to "MONTH", "offset" to 0)), today)
        assertEquals(-1, tooNew.period.offset)
        // The year so far is a period a review can show.
        val year = routeSelection(SavedStateHandle(mapOf("reviewKind" to "YEAR", "offset" to 0)), today)
        assertEquals(0, year.period.offset)
    }

    @Test
    fun `a review's offset counts from the day the review chose it, not from now`() {
        // Opened just after midnight on a review chosen the day before.
        val chosen = today.minusDays(1)
        val route = mapOf("reviewKind" to "WEEK", "offset" to -1, "today" to chosen.toEpochDay())
        assertEquals(ReviewSelection(ReviewKind.WEEK, -1, chosen), routeSelection(SavedStateHandle(route), today).period)
    }

    @Test
    fun `a goal opens on that goal, and an unknown kind falls back to the recap`() {
        val goal = routeSelection(SavedStateHandle(mapOf("kind" to "GOAL", "goalId" to 7L)), today)
        assertEquals(ShareKind.GOAL, goal.kind)
        assertEquals(7L, goal.goalId)
        assertNull(routeSelection(SavedStateHandle(mapOf("goalId" to -1L)), today).goalId)
        assertEquals(ShareKind.RECAP, routeSelection(SavedStateHandle(mapOf("kind" to "NOPE")), today).kind)
    }

    @Test
    fun `the budget card is monthly and counts back calendar months like the Budgets screen`() {
        val budget = routeSelection(SavedStateHandle(mapOf("kind" to "BUDGET", "reviewKind" to "WEEK", "offset" to -2)), today)
        assertEquals(ReviewKind.MONTH, budget.period.kind)
        assertEquals(YearMonth.of(2026, 10), budgetMonth(budget.period))
        assertEquals(YearMonth.of(2025, 12), budgetMonth(ReviewSelection(ReviewKind.MONTH, -11, today)))
    }
}
