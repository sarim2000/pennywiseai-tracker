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
    fun `past days settle, today is pending and later days are empty`() {
        val p = noSpend.progress(listOf(row(1, "Shopping")), today = start.plusDays(3))
        assertEquals(
            listOf(DayStatus.CLEAN, DayStatus.MISSED, DayStatus.CLEAN, DayStatus.TODAY, DayStatus.FUTURE, DayStatus.FUTURE, DayStatus.FUTURE),
            p.statuses(),
        )
        assertEquals(4, p.dayNumber)
        assertEquals(2, p.cleanDays)
        assertFalse(p.finished)
        assertEquals(listOf(1L), p.days[1].misses.map { it.id })
    }

    @Test
    fun `an expense today misses it at once`() {
        val p = noSpend.progress(listOf(row(2, "Shopping")), today = start.plusDays(2))
        assertEquals(DayStatus.MISSED, p.today?.status)
    }

    @Test
    fun `no-spend is won once the third clean day has ended`() {
        val rows = listOf(row(0, "Bills"), row(2, "Shopping"))
        assertFalse(noSpend.progress(rows, today = start.plusDays(4)).won) // days 1 and 3 clean, 4 pending
        val p = noSpend.progress(rows, today = start.plusDays(5))
        assertTrue(p.won)
        assertTrue(p.finished)
    }

    @Test
    fun `a category week only counts that category, in any split`() {
        val rows = listOf(row(0, "Shopping"), row(1, "Bills", FOOD_AND_DINING), row(3, "Groceries"))
        val p = noFood.progress(rows, today = start.plusDays(7))
        assertEquals(DayStatus.CLEAN, p.days[0].status)
        assertEquals(DayStatus.MISSED, p.days[1].status)
        assertEquals(6, p.cleanDays)
        assertTrue(p.finished)
        assertFalse(p.won)
        assertFalse(noFood.progress(rows, today = start.plusDays(3)).winnable)
    }

    @Test
    fun `a certain loss still waits for the window to end`() {
        // A day-7 expense corrected to a transfer the same afternoon can still clear the day.
        val rows = (1..6).map { row(it, FOOD_AND_DINING) }
        val lastDay = noFood.progress(rows, today = start.plusDays(6))
        assertFalse(lastDay.winnable)
        assertFalse(lastDay.finished)
        assertTrue(noFood.progress(rows, today = start.plusDays(7)).finished)
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
    fun `a run with no id matches nothing, and old JSON grows an id that the next save keeps`() {
        val bare = ChallengeRun(
            kind = ChallengeKind.CATEGORY_WEEK,
            categoryName = FOOD_AND_DINING,
            startEpochDay = start.toEpochDay(),
        )
        assertFalse(bare.sameAs(bare))
        assertEquals(bare, ChallengeState(active = bare).giveUp(bare).active)
        val raw = """{"active":{"kind":"CATEGORY_WEEK","categoryName":"Food & Dining","startEpochDay":${start.toEpochDay()},"length":7,"target":7},"history":[{"run":{"kind":"NO_SPEND_DAYS","startEpochDay":${start.toEpochDay()}},"cleanDays":2,"won":false,"seen":true}]}"""
        val loaded = ChallengeState.fromJson(raw)
        val active = loaded.active!!
        assertEquals(FOOD_AND_DINING, active.categoryName)
        assertTrue(active.id != null)
        assertTrue(loaded.history.single().run.id != null)
        assertFalse(active.sameAs(loaded.history.single().run))
        // A second read of the same old JSON assigns the same ids, so give-up still matches.
        val reread = ChallengeState.fromJson(raw)
        assertEquals(loaded, reread)
        assertNull(reread.giveUp(active).active)
        assertEquals(loaded, ChallengeState.fromJson(loaded.toJson()))
        assertNull(loaded.giveUp(active.copy(categoryName = "Dining")).active)
    }

    @Test
    fun `a clean category week is won when it ends, not before`() {
        val rows = listOf(row(2, "Shopping"))
        assertFalse(noFood.progress(rows, today = start.plusDays(6)).finished) // the last day is still today
        assertTrue(noFood.progress(rows, today = start.plusDays(7)).won)
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
}
