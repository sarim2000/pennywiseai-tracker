package com.pennywiseai.tracker.domain.achievements

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.GoalDetail
import com.pennywiseai.tracker.data.repository.GoalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AchievementsTest {

    private val today = LocalDate.parse("2026-11-16")

    private fun day(value: String) = LocalDate.parse(value)

    private fun bd(value: String) = BigDecimal(value)

    // --- Builders: synthetic data only.

    private fun goal(
        target: String,
        alreadySaved: String = "0",
        created: String = "2026-01-10",
        currency: String = "JOD",
        vararg moves: Pair<String, String>,
    ): GoalDetail {
        val entity = GoalEntity(
            id = 1, name = "Synthetic goal", targetAmount = bd(target), currency = currency,
            dueDate = day("2027-12-31"), alreadySaved = bd(alreadySaved), createdAt = day(created).atStartOfDay(),
        )
        val contributions = moves.mapIndexed { i, (date, amount) ->
            GoalContributionEntity(id = i + 1L, goalId = 1, amount = bd(amount), date = day(date))
        }
        return GoalDetail(GoalRules.progress(entity, contributions, today), contributions)
    }

    /** One record a day from [start], [count] of them. */
    private fun daily(start: String, count: Int): List<LocalDate> = (0 until count).map { day(start).plusDays(it.toLong()) }

    // --- Tiers and display

    @Test
    fun `progress and next threshold follow the tier earned`() {
        val locked = Achievement(AchievementId.TRACKED, tier = 0, progress = 40, unlockedOn = null)
        assertFalse(locked.earned)
        assertEquals(100, locked.nextThreshold)
        assertEquals(0.4f, locked.fractionToNext, 0.001f)

        val maxed = Achievement(AchievementId.TRACKED, tier = 4, progress = 6_000, unlockedOn = today)
        assertNull(maxed.nextThreshold)
        assertEquals(5_000, maxed.threshold)
        assertEquals(1f, maxed.fractionToNext, 0.001f)
    }

    @Test
    fun `earned come first, newest first, then the closest to earning`() {
        val list = listOf(
            Achievement(AchievementId.STREAK, 0, 1, null),
            Achievement(AchievementId.TRACKED, 1, 120, day("2026-03-01")),
            Achievement(AchievementId.GOALS_REACHED, 0, 0, null),
            Achievement(AchievementId.STREAK, 1, 3, day("2026-09-01")),
            Achievement(AchievementId.TRACKED, 0, 80, null),
        ).forDisplay()
        assertEquals(
            listOf(AchievementId.STREAK, AchievementId.TRACKED, AchievementId.TRACKED, AchievementId.STREAK, AchievementId.GOALS_REACHED),
            list.map { it.id },
        )
    }

    @Test
    fun `contract keys are unique and fit the share contract`() {
        val keys = AchievementId.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.length <= 40 })
        assertEquals(AchievementId.GOALS_REACHED, AchievementId.fromKey("goals_reached"))
        assertNull(AchievementId.fromKey("nope"))
    }

    // --- Transactions tracked

    @Test
    fun `transactions tracked unlocks on the day of the record that crossed each tier`() {
        val a = trackedAchievement(daily("2026-01-01", 520).shuffled(java.util.Random(7)))
        assertEquals(2, a.tier)
        // The 500th record, one a day from 1 January.
        assertEquals(day("2026-01-01").plusDays(499), a.unlockedOn)
        assertEquals(520, a.progress)
        assertEquals(1_000, a.nextThreshold)
    }

    @Test
    fun `fewer than a hundred transactions is locked`() {
        val a = trackedAchievement(daily("2026-01-01", 99))
        assertEquals(0, a.tier)
        assertNull(a.unlockedOn)
        assertEquals(99, a.progress)
    }

    // --- Months in a row

    @Test
    fun `a streak counts calendar months in a row and unlocks on the month's first record`() {
        val recorded = listOf("2026-01-20", "2026-02-03", "2026-02-01", "2026-03-15", "2026-04-02").map(::day)
        val a = streakAchievement(recorded, day("2026-04-30"))
        assertEquals(1, a.tier)
        // Three months in a row reached on March's first record.
        assertEquals(day("2026-03-15"), a.unlockedOn)
        assertEquals(4, a.progress)
    }

    @Test
    fun `a gap restarts the streak but keeps tiers earned before it`() {
        val recorded = listOf("2025-01-05", "2025-02-05", "2025-03-05", "2025-05-05", "2025-06-05").map(::day)
        val a = streakAchievement(recorded, day("2025-06-20"))
        assertEquals(1, a.tier)
        assertEquals(day("2025-03-05"), a.unlockedOn)
        assertEquals(2, a.progress)
    }

    @Test
    fun `a month just started doesn't break the current streak, one missed does`() {
        val recorded = listOf("2026-08-10", "2026-09-10", "2026-10-10").map(::day)
        assertEquals(3, streakAchievement(recorded, day("2026-11-02")).progress)
        assertEquals(0, streakAchievement(recorded, day("2026-12-02")).progress)
    }

    // --- Goals

    @Test
    fun `a goal is reached on the contribution that took it to its target`() {
        val g = goal("1000", "100", "2026-01-10", "JOD", "2026-03-01" to "300", "2026-02-01" to "500", "2026-04-01" to "200")
        // 100 + 500 (Feb) + 300 (Mar) = 900, then 1100 in April.
        assertEquals(day("2026-04-01"), goalsReachedAchievement(listOf(g), today).unlockedOn)
    }

    @Test
    fun `a goal started at its target is reached on the day it was made, and a withdrawal doesn't undo it`() {
        val started = goal("1000", alreadySaved = "1000", created = "2026-05-05")
        assertEquals(day("2026-05-05"), goalsReachedAchievement(listOf(started), today).unlockedOn)
        val dipped = goal("1000", "0", "2026-01-10", "JOD", "2026-02-01" to "1000", "2026-03-01" to "-400")
        assertTrue(goalsReachedAchievement(listOf(dipped), today).earned)
    }

    @Test
    fun `a contribution dated after today doesn't reach a goal yet`() {
        val g = goal("1000", "0", "2026-01-10", "JOD", "2026-02-01" to "200", today.plusMonths(1).toString() to "900")
        assertEquals(0, goalsReachedAchievement(listOf(g), today).tier)
    }

    @Test
    fun `goals reached counts goals in any currency, never their amounts`() {
        val goals = listOf(
            goal("100", "0", "2026-01-01", "JOD", "2026-06-01" to "100"),
            goal("5000", "0", "2026-01-01", "INR", "2026-03-01" to "6000"),
            goal("900", "100"),
        )
        val a = goalsReachedAchievement(goals, today)
        assertEquals(1, a.tier)
        assertEquals(2, a.progress)
        assertEquals(day("2026-03-01"), a.unlockedOn)
        assertEquals(0, goalsReachedAchievement(emptyList(), today).tier)
    }

    @Test
    fun `a goal without a target reaches nothing`() {
        assertNull(dateReached(GoalEntity(targetAmount = BigDecimal.ZERO), emptyList(), today))
    }

    // --- All of them

    @Test
    fun `every achievement is worked out, in order, and an empty history earns none`() {
        val all = achievementsOf(emptyList(), emptyList(), today)
        assertEquals(AchievementId.entries.toList(), all.map { it.id })
        assertTrue(all.none { it.earned })
    }
}
