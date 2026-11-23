package com.pennywiseai.tracker.domain.challenges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChallengesTest {

    private val start = LocalDate.of(2026, 11, 16)
    private val noSpend = ChallengeRun.start(ChallengeKind.NO_SPEND_DAYS, "ignored", start)
    private val noFood = ChallengeRun.start(ChallengeKind.CATEGORY_WEEK, FOOD_AND_DINING, start)

    private fun row(day: Int, vararg categories: String, id: Long = day.toLong()) =
        ChallengeRow(id, start.plusDays(day.toLong()), categories.toSet(), "Shop $id")

    private fun ChallengeProgress.statuses() = days.map { it.status }

    @Test
    fun `presets set their window and target`() {
        assertEquals(7, noSpend.length)
        assertEquals(3, noSpend.target)
        assertNull(noSpend.categoryName)
        assertEquals(7, noFood.target)
        assertEquals(start.plusDays(6), noFood.end)
    }

    @Test
    fun `a clean day settles a day after it ends, today and yesterday wait for late messages`() {
        val p = noSpend.progress(listOf(row(1, "Shopping")), today = start.plusDays(3))
        assertEquals(
            listOf(DayStatus.CLEAN, DayStatus.MISSED, DayStatus.PENDING, DayStatus.PENDING, DayStatus.FUTURE, DayStatus.FUTURE, DayStatus.FUTURE),
            p.statuses(),
        )
        assertEquals(4, p.dayNumber)
        assertEquals(1, p.cleanDays)
        assertFalse(p.finished)
        assertEquals(listOf(1L), p.days[1].misses.map { it.id })
        assertEquals(start.plusDays(3), p.today?.date)
    }

    @Test
    fun `a message for yesterday that arrives today still misses it`() {
        val today = start.plusDays(3)
        assertEquals(DayStatus.PENDING, noSpend.progress(emptyList(), today).days[2].status)
        assertEquals(DayStatus.MISSED, noSpend.progress(listOf(row(2, "Shopping")), today).days[2].status)
    }

    @Test
    fun `without tracking no day settles and nothing finishes`() {
        val p = noSpend.progress(emptyList(), today = start.plusDays(20), tracking = false)
        assertTrue(p.paused)
        assertEquals(0, p.cleanDays)
        assertFalse(p.won)
        assertFalse(p.finished)
        assertTrue(p.days.all { it.status == DayStatus.PENDING })
        // A counted expense still misses its day.
        assertEquals(DayStatus.MISSED, noSpend.progress(listOf(row(1, "Bills")), start.plusDays(3), tracking = false).days[1].status)
    }

    @Test
    fun `an expense today misses it at once`() {
        val p = noSpend.progress(listOf(row(2, "Shopping")), today = start.plusDays(2))
        assertEquals(DayStatus.MISSED, p.today?.status)
    }

    @Test
    fun `no-spend is won once the third clean day has settled`() {
        val rows = listOf(row(0, "Bills"), row(2, "Shopping"))
        assertFalse(noSpend.progress(rows, today = start.plusDays(5)).won) // days 1 and 3 clean, 4 still waiting
        val p = noSpend.progress(rows, today = start.plusDays(6))
        assertTrue(p.won)
        assertTrue(p.finished)
    }

    @Test
    fun `a category week only counts that category, in any split`() {
        val rows = listOf(row(0, "Shopping"), row(1, "Bills", FOOD_AND_DINING), row(3, "Groceries"))
        val p = noFood.progress(rows, today = start.plusDays(8))
        assertEquals(DayStatus.CLEAN, p.days[0].status)
        assertEquals(DayStatus.MISSED, p.days[1].status)
        assertEquals(6, p.cleanDays)
        assertTrue(p.finished)
        assertFalse(p.won)
        assertFalse(noFood.progress(rows, today = start.plusDays(3)).winnable)
    }

    @Test
    fun `a certain loss still waits for every day to settle`() {
        // A day-7 expense corrected to a transfer the next morning can still clear the day.
        val rows = (1..6).map { row(it, FOOD_AND_DINING) }
        val lastDay = noFood.progress(rows, today = start.plusDays(6))
        assertFalse(lastDay.winnable)
        assertFalse(lastDay.finished)
        val graceDay = noFood.progress(rows, today = start.plusDays(7))
        assertFalse(graceDay.finished)
        assertNull(graceDay.today)
        assertEquals(7, graceDay.dayNumber)
        assertTrue(noFood.progress(rows, today = start.plusDays(8)).finished)
    }

    @Test
    fun `giving up only stops the run it was asked for`() {
        val renamed = noFood.copy(categoryName = "Dining")
        assertEquals(noFood.id, renamed.id)
        assertNull(ChallengeState(active = noFood).giveUp(renamed).active)
        assertEquals(noFood, ChallengeState(active = noFood).giveUp(noSpend).active)
        // Finishing a renamed copy records the stored run.
        assertEquals(noFood, ChallengeState(active = noFood).finish(renamed, 7, true).history.first().run)
        // Another category started the same day is a different run.
        val shopping = ChallengeRun.start(ChallengeKind.CATEGORY_WEEK, "Shopping", start)
        assertEquals(shopping, ChallengeState(active = shopping).giveUp(noFood).active)
        assertFalse(noFood.sameAs(null))
    }

    @Test
    fun `a run keeps its id and profile through a save`() {
        val business = ChallengeRun.start(ChallengeKind.NO_SPEND_DAYS, null, start, profileId = 2L)
        val loaded = ChallengeState.fromJson(ChallengeState(active = business).toJson()).active!!
        assertEquals(business, loaded)
        assertEquals(2L, loaded.profileId)
        assertTrue(loaded.sameAs(business))
        assertTrue(business.celebrationKey != noSpend.celebrationKey)
    }

    @Test
    fun `a clean category week is won once its last day has settled, not before`() {
        val rows = listOf(row(2, "Shopping"))
        assertFalse(noFood.progress(rows, today = start.plusDays(6)).finished) // the last day is still today
        assertFalse(noFood.progress(rows, today = start.plusDays(7)).finished) // and waits a day for late messages
        assertTrue(noFood.progress(rows, today = start.plusDays(8)).won)
    }

    @Test
    fun `finishing keeps the newest results and ignores a run that isn't active`() {
        var state = ChallengeState(active = noSpend)
        state = state.finish(noFood, 7, true)
        assertEquals(noSpend, state.active)
        state = state.finish(noSpend, 3, true)
        assertNull(state.active)
        assertEquals(ChallengeOutcome(noSpend, 3, true), state.unseenResult)
        assertNull(state.markSeen().unseenResult)
        repeat(12) { state = state.copy(active = noFood).finish(noFood, it, false) }
        assertEquals(ChallengeState.HISTORY, state.history.size)
        assertEquals(11, state.history.first().cleanDays)
    }

    @Test
    fun `state survives a round trip and bad JSON reads as empty`() {
        val state = ChallengeState(active = noFood, history = listOf(ChallengeOutcome(noSpend, 2, false, seen = true)))
        assertEquals(state, ChallengeState.fromJson(state.toJson()))
        assertEquals(ChallengeState(), ChallengeState.fromJson("{not json"))
        assertEquals(ChallengeState(), ChallengeState.fromJson(null))
    }

    @Test
    fun `food and dining leads the categories, others never appears`() {
        val rows = listOf(
            row(0, "Shopping"), row(1, "Shopping"), row(2, "Shopping"),
            row(3, "Others"), row(4, "Others"), row(5, "Others"), row(6, "Others"),
            row(7, FOOD_AND_DINING), row(8, "Travel"), row(9, "Travel"), row(10, "Bills"),
        )
        assertEquals(listOf(FOOD_AND_DINING, "Shopping", "Travel"), challengeCategories(rows))
        assertEquals(listOf("Shopping"), challengeCategories(rows.take(3)))
        assertEquals(listOf(FOOD_AND_DINING), challengeCategories(emptyList()))
    }

    @Test
    fun `renamed food and others are found by their current names`() {
        val rows = listOf(row(0, "Misc"), row(1, "Misc"), row(2, "Eating out"), row(3, "Travel"), row(4, "Others"))
        val ranked = challengeCategories(rows, food = "Eating out", others = setOf("Others", "Misc"))
        assertEquals(listOf("Eating out", "Travel"), ranked)
        assertEquals(listOf("Eating out"), challengeCategories(emptyList(), food = "Eating out"))
    }
}
