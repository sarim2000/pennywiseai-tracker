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

    @Test
    fun `a new kind of period starts from today, not the review's day`() {
        // A November 30 review still open on December 1.
        val opened = ShareStudioSelection(kind = ShareKind.RECAP, period = ReviewSelection(ReviewKind.WEEK, -1, LocalDate.of(2026, 11, 30)))
        val dec1 = LocalDate.of(2026, 12, 1)
        val budget = opened.withKind(ShareKind.BUDGET, dec1)
        assertEquals(YearMonth.of(2026, 11), budgetMonth(budget.period))
        assertEquals(ReviewSelection(ReviewKind.MONTH, -1, dec1), opened.withPeriodKind(ReviewKind.MONTH, dec1).period)
        // A card switch that keeps the period keeps its day too.
        assertEquals(opened.period, opened.withKind(ShareKind.SPENDING, dec1).period)
    }

    @Test
    fun `an achievement opens on that one alone, Share all on every one earned`() {
        val one = routeSelection(SavedStateHandle(mapOf("kind" to "ACHIEVEMENTS", "achievement" to "goal_halfway")), today)
        assertEquals(ShareKind.ACHIEVEMENTS, one.kind)
        assertEquals(setOf("goal_halfway"), one.achievements)
        assertNull(routeSelection(SavedStateHandle(mapOf("kind" to "ACHIEVEMENTS")), today).achievements)
    }

    @Test
    fun `picking achievements goes back to every one once all are on`() {
        val earned = listOf("a", "b", "c")
        assertEquals(setOf("a", "c"), toggleAchievement(null, "b", shown = false, earned))
        assertEquals(emptySet<String>(), toggleAchievement(setOf("a"), "a", shown = false, earned))
        assertNull(toggleAchievement(setOf("a", "c"), "b", shown = true, earned))
    }

    @Test
    fun `an achievements card leads with badges, not a figure, and only offers its date`() {
        assertFalse(ShareKind.ACHIEVEMENTS.needsFigure)
        assertEquals(setOf(ShareDetail.PERIOD), effectiveDetails(ShareKind.ACHIEVEMENTS, ShareDetail.entries.toSet()))
    }

    @Test
    fun `a progress card starts with every section, is monthly, and keeps a figure on its rows`() {
        val opened = ShareStudioSelection(kind = ShareKind.RECAP, period = ReviewSelection(ReviewKind.WEEK, -1, today))
        val progress = opened.withKind(ShareKind.PROGRESS, today)
        assertEquals(ProgressSection.entries.toSet(), progress.sections)
        assertEquals(ReviewKind.MONTH, progress.period.kind)
        assertTrue(ShareDetail.PERCENTAGES in effectiveDetails(ShareKind.PROGRESS, emptySet()))
    }

    @Test
    fun `a progress card shows the first three goals, and a fourth waits for one to go`() {
        val shown = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(1L, 2L, 3L), progressGoalIds(null, shown))
        val full = toggleGoal(null, 4L, on = true, shown)
        assertEquals(setOf(1L, 2L, 3L), full)
        val swapped = toggleGoal(toggleGoal(full, 2L, on = false, shown), 4L, on = true, shown)
        assertEquals(listOf(1L, 3L, 4L), progressGoalIds(swapped, shown))
        // A goal that's gone since it was picked doesn't hold a place.
        assertEquals(listOf(1L), progressGoalIds(setOf(1L, 9L), shown))
    }

    @Test
    fun `unticking every badge doesn't follow the user to another card`() {
        val none = ShareStudioSelection(kind = ShareKind.ACHIEVEMENTS, period = ReviewSelection(ReviewKind.MONTH, -1, today), achievements = emptySet())
        assertNull(none.withKind(ShareKind.PROGRESS, today).achievements)
        val some = none.copy(achievements = setOf("saver"))
        assertEquals(setOf("saver"), some.withKind(ShareKind.PROGRESS, today).achievements)
    }

    @Test
    fun `a stale pick brings its picker back instead of leaving an empty section`() {
        // Goal 1 was picked, then deleted: only goal 2 is left and nothing shows.
        assertTrue(progressGoalIds(setOf(1L), listOf(2L)).isEmpty())
        assertTrue(showsPicker(options = 1, shown = 0))
        // Picking it from there puts it on the card.
        assertEquals(listOf(2L), progressGoalIds(toggleGoal(setOf(1L), 2L, on = true, listOf(2L)), listOf(2L)))
        // One item already on the card: its section chip is enough.
        assertFalse(showsPicker(options = 1, shown = 1))
        assertTrue(showsPicker(options = 2, shown = 2))
        assertFalse(showsPicker(options = 0, shown = 0))
    }
}
