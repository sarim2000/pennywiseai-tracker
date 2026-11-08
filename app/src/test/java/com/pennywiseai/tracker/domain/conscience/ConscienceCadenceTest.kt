package com.pennywiseai.tracker.domain.conscience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ConscienceCadenceTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val now = 1_800_000_000_000L
    private val today = 20_000L
    private val locale = "en"

    private fun fired(vararg agoMillis: Long) = ConscienceLog(firedAtMillis = agoMillis.map { now - it })

    private fun allows(log: ConscienceLog) = ConscienceCadence.allows(log, now)

    @Test
    fun `blocks at 23h59 and allows at 24h`() {
        assertFalse(allows(fired(23 * hour + 59 * minute)))
        assertTrue(allows(fired(24 * hour)))
        assertTrue(allows(ConscienceLog()))
    }

    @Test
    fun `blocks a fourth in 7 days and allows once the oldest leaves`() {
        assertFalse(allows(fired(1 * day, 3 * day, 7 * day - minute)))
        assertTrue(allows(fired(1 * day, 3 * day, 7 * day)))
        assertTrue(allows(fired(1 * day, 3 * day)))
    }

    private val a = ConscienceLine("a", 1)
    private val b = ConscienceLine("b", 2)
    private val pool = listOf(a, b)
    private fun key(line: ConscienceLine) = ConscienceLines.key(locale, line)

    private fun pick(
        log: ConscienceLog,
        onDay: Long = today,
        seed: Int = 1,
        retired: Set<String> = emptySet(),
    ) = ConscienceCadence.pickLine(pool, locale, log, retired, onDay, Random(seed))

    @Test
    fun `a line is not repeated within 30 days and returns afterwards`() {
        val log = ConscienceLog(lineUsedOnEpochDay = mapOf(key(a) to today))
        repeat(20) { seed -> assertEquals("b", pick(log, today + 29, seed)?.id) }
        val both = ConscienceLog(lineUsedOnEpochDay = mapOf(key(a) to today, key(b) to today - 5))
        assertEquals("a", pick(both, today + 30)?.id)
    }

    @Test
    fun `the cooldown is per language, so a line in another language is a different line`() {
        val log = ConscienceLog(lineUsedOnEpochDay = mapOf("ar:a" to today, "ar:b" to today))
        assertNotNull(pick(log))
    }

    @Test
    fun `exhausted pool returns null, empty pool too`() {
        val log = ConscienceLog(lineUsedOnEpochDay = mapOf(key(a) to today, key(b) to today - 29))
        assertNull(pick(log))
        assertNull(ConscienceCadence.pickLine(emptyList(), locale, ConscienceLog(), emptySet(), today, Random(1)))
    }

    @Test
    fun `pick reaches every eligible line`() {
        val ids = (0 until 50).mapNotNull { pick(ConscienceLog(), seed = it)?.id }.toSet()
        assertEquals(setOf("a", "b"), ids)
    }

    @Test
    fun `a retired line never comes back`() {
        val retired = setOf(key(a))
        repeat(50) { seed -> assertEquals("b", pick(ConscienceLog(), seed = seed, retired = retired)?.id) }
        assertNull(pick(ConscienceLog(lineUsedOnEpochDay = mapOf(key(b) to today)), retired = retired))
    }

    private val food = ConscienceCategory.FOOD

    private fun choose(
        candidates: List<ConscienceCandidate>,
        log: ConscienceLog = ConscienceLog(),
    ) = ConscienceCadence.choose(candidates, log, emptySet(), locale, now, today, Random(1))

    @Test
    fun `choose takes the first candidate that can speak`() {
        val repeat = ConscienceFact.Repeat(food, 3, 6)
        val late = ConscienceFact.LateNight(food)
        val choice = choose(listOf(ConscienceCandidate(repeat, pool), ConscienceCandidate(late, pool)))
        assertEquals(repeat, choice?.fact)
        assertEquals("en:${choice?.line?.id}", choice?.lineKey)
    }

    @Test
    fun `a kind on cooldown in a category steps aside for the next candidate`() {
        val repeat = ConscienceFact.Repeat(food, 3, 6)
        val late = ConscienceFact.LateNight(food)
        val log = ConscienceLog(kindFiredAtMillis = mapOf("REPEAT_FOOD" to now - 3 * day + minute))
        assertEquals(late, choose(listOf(ConscienceCandidate(repeat, pool), ConscienceCandidate(late, pool)), log)?.fact)

        val cooled = ConscienceLog(kindFiredAtMillis = mapOf("REPEAT_FOOD" to now - 3 * day))
        assertEquals(repeat, choose(listOf(ConscienceCandidate(repeat, pool)), cooled)?.fact)

        val otherCategory = ConscienceFact.Repeat(ConscienceCategory.SHOPPING, 3, 6)
        assertEquals(otherCategory, choose(listOf(ConscienceCandidate(otherCategory, pool)), log)?.fact)
    }

    @Test
    fun `a candidate with no lines left steps aside`() {
        val repeat = ConscienceFact.Repeat(food, 3, 6)
        val late = ConscienceFact.LateNight(food)
        assertEquals(late, choose(listOf(ConscienceCandidate(repeat, emptyList()), ConscienceCandidate(late, pool)))?.fact)
    }

    @Test
    fun `nothing is chosen when the cadence is spent`() {
        val repeat = listOf(ConscienceCandidate(ConscienceFact.Repeat(food, 3, 6), pool))
        assertNull(choose(repeat, fired(hour)))
    }

    @Test
    fun `record appends the fire, line and kind and prunes stale entries`() {
        val log = ConscienceLog(
            firedAtMillis = listOf(now - 7 * day, now - 7 * day + minute),
            lineUsedOnEpochDay = mapOf("en:old" to today - 30, "en:kept" to today - 29),
            kindFiredAtMillis = mapOf("POSITIVE_FOOD" to now - 15 * day, "POSITIVE_SHOPPING" to now - 13 * day),
        )
        val choice = ConscienceChoice(ConscienceFact.LateNight(food), ConscienceLine("new", 1), "en:new")
        assertEquals(
            ConscienceLog(
                firedAtMillis = listOf(now - 7 * day + minute, now),
                lineUsedOnEpochDay = mapOf("en:kept" to today - 29, "en:new" to today),
                kindFiredAtMillis = mapOf("POSITIVE_SHOPPING" to now - 13 * day, "LATE_NIGHT_FOOD" to now),
            ),
            ConscienceCadence.record(log, choice, now, today),
        )
    }

    @Test
    fun `encode and decode round-trip`() {
        val log = ConscienceLog(
            firedAtMillis = listOf(now - day, now),
            lineUsedOnEpochDay = mapOf("ar:repeat_food_01" to today, "en:positive_02" to today - 3),
            kindFiredAtMillis = mapOf("REPEAT_FOOD" to now - hour),
        )
        assertEquals(log, ConscienceCadence.decode(ConscienceCadence.encode(log)))
        assertEquals(ConscienceLog(), ConscienceCadence.decode(emptySet()))
    }

    @Test
    fun `malformed entries are ignored`() {
        val log = ConscienceCadence.decode(
            setOf(
                "",
                "|",
                "garbage",
                "fire",
                "fire|",
                "fire|not-a-number",
                "fire|1|2",
                "line|a",
                "line||5",
                "line|a|not-a-number",
                "line|a|b|5",
                "kind||5",
                "kind|X|y",
                "other|5",
                "fire|123",
                "line|ok|9",
                "kind|REPEAT_FOOD|7",
            ),
        )
        assertEquals(ConscienceLog(listOf(123L), mapOf("ok" to 9L), mapOf("REPEAT_FOOD" to 7L)), log)
    }
}
