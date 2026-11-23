package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.domain.challenges.ChallengeKind
import com.pennywiseai.tracker.domain.challenges.ChallengeOutcome
import com.pennywiseai.tracker.domain.challenges.ChallengeRow
import com.pennywiseai.tracker.domain.challenges.ChallengeRun
import com.pennywiseai.tracker.domain.challenges.ChallengeState
import com.pennywiseai.tracker.domain.challenges.DayStatus
import com.pennywiseai.tracker.domain.challenges.FOOD_AND_DINING
import com.pennywiseai.tracker.domain.challenges.progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengeWidgetDataTest {

    private val start = LocalDate.of(2026, 11, 16)
    private val noSpend = ChallengeRun.start(ChallengeKind.NO_SPEND_DAYS, null, start)
    private val noFood = ChallengeRun.start(ChallengeKind.CATEGORY_WEEK, FOOD_AND_DINING, start)

    private fun row(day: Int, vararg categories: String) =
        ChallengeRow(day.toLong(), start.plusDays(day.toLong()), categories.toSet(), "Shop $day")

    private fun snapshot(run: ChallengeRun, rows: List<ChallengeRow>, today: LocalDate, tracking: Boolean = true) =
        challengeWidgetSnapshot(ChallengeState(active = run), run.progress(rows, today, tracking = tracking))

    @Test
    fun `nothing running and nothing to see offers to start one`() {
        assertEquals(ChallengeWidgetSnapshot(), challengeWidgetSnapshot(ChallengeState(), null))
        val seen = ChallengeState(history = listOf(ChallengeOutcome(noSpend, cleanDays = 3, won = true, seen = true)))
        assertEquals(ChallengeWidgetStatus.NONE, challengeWidgetSnapshot(seen, null).status)
    }

    @Test
    fun `a running challenge shows each day, the clean ones and the days left`() {
        val s = snapshot(noSpend, listOf(row(1, "Shopping")), today = start.plusDays(3))
        assertEquals(ChallengeWidgetStatus.ACTIVE, s.status)
        assertEquals(
            listOf(DayStatus.CLEAN, DayStatus.MISSED, DayStatus.PENDING, DayStatus.PENDING, DayStatus.FUTURE, DayStatus.FUTURE, DayStatus.FUTURE),
            s.days,
        )
        assertEquals(1, s.cleanDays)
        assertEquals(3, s.target)
        // Day 4 of 7: today and the three after it.
        assertEquals(4, s.daysLeft)
        assertNull(s.categoryLabel)
    }

    @Test
    fun `the last day has one left, its grace day none`() {
        assertEquals(1, snapshot(noFood, emptyList(), today = noFood.end).daysLeft)
        val grace = snapshot(noFood, listOf(row(0, FOOD_AND_DINING)), today = noFood.end.plusDays(1))
        assertEquals(ChallengeWidgetStatus.ACTIVE, grace.status)
        assertEquals(0, grace.daysLeft)
    }

    @Test
    fun `a week without names its category as the user reads it`() {
        val s = challengeWidgetSnapshot(ChallengeState(active = noFood), noFood.progress(emptyList(), start)) { "Food" }
        assertEquals("Food", s.categoryLabel)
        assertEquals(7, s.target)
    }

    @Test
    fun `a target reached shows the win before Plan records it`() {
        val s = snapshot(noSpend, emptyList(), today = start.plusDays(4))
        assertEquals(ChallengeWidgetStatus.WON, s.status)
        assertEquals(3, s.cleanDays)
        assertTrue(s.days.isEmpty())
    }

    @Test
    fun `a window over short of its target ends`() {
        val rows = (0..6).map { row(it, FOOD_AND_DINING) }.filter { it.id != 2L }
        val s = snapshot(noFood, rows, today = noFood.end.plusDays(2))
        assertEquals(ChallengeWidgetStatus.ENDED, s.status)
        assertEquals(1, s.cleanDays)
        assertEquals(7, s.target)
    }

    @Test
    fun `without tracking the run is paused, not lost`() {
        val s = snapshot(noSpend, emptyList(), today = start.plusDays(20), tracking = false)
        assertEquals(ChallengeWidgetStatus.ACTIVE, s.status)
        assertTrue(s.paused)
        assertEquals(0, s.daysLeft)
    }

    @Test
    fun `a recorded result shows until the user moves on from it`() {
        val lost = ChallengeState(history = listOf(ChallengeOutcome(noFood, cleanDays = 5, won = false)))
        val s = challengeWidgetSnapshot(lost, null)
        assertEquals(ChallengeWidgetStatus.ENDED, s.status)
        assertEquals(ChallengeKind.CATEGORY_WEEK, s.kind)
        assertEquals(FOOD_AND_DINING, s.categoryLabel)
        assertEquals(5, s.cleanDays)
        assertEquals(ChallengeWidgetStatus.NONE, challengeWidgetSnapshot(lost.markSeen(), null).status)
    }

    @Test
    fun `a snapshot survives the store, and one it can't read is empty`() {
        val s = snapshot(noSpend, listOf(row(1, "Shopping")), today = start.plusDays(3))
        assertEquals(s, ChallengeWidgetDataStore.decode(ChallengeWidgetDataStore.encode(s)))
        assertEquals(ChallengeWidgetSnapshot(), ChallengeWidgetDataStore.decode("{not json"))
        assertEquals(ChallengeWidgetSnapshot(), ChallengeWidgetDataStore.decode(null))
    }
}
