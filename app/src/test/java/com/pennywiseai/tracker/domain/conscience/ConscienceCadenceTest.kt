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

    private fun fired(vararg agoMillis: Long) = ConscienceLog(firedAtMillis = agoMillis.map { now - it })

    private fun allows(log: ConscienceLog, frequency: ConscienceFrequency) =
        ConscienceCadence.allows(log, frequency, now)

    @Test
    fun `normal blocks at 23h59 and allows at 24h`() {
        assertFalse(allows(fired(23 * hour + 59 * minute), ConscienceFrequency.NORMAL))
        assertTrue(allows(fired(24 * hour), ConscienceFrequency.NORMAL))
        assertTrue(allows(ConscienceLog(), ConscienceFrequency.NORMAL))
    }

    @Test
    fun `normal blocks a fourth in 7 days and allows once the oldest leaves`() {
        assertFalse(allows(fired(1 * day, 3 * day, 7 * day - minute), ConscienceFrequency.NORMAL))
        assertTrue(allows(fired(1 * day, 3 * day, 7 * day), ConscienceFrequency.NORMAL))
        assertTrue(allows(fired(1 * day, 3 * day), ConscienceFrequency.NORMAL))
    }

    @Test
    fun `less allows one per rolling week`() {
        assertFalse(allows(fired(7 * day - minute), ConscienceFrequency.LESS))
        assertTrue(allows(fired(7 * day), ConscienceFrequency.LESS))
        assertTrue(allows(ConscienceLog(), ConscienceFrequency.LESS))
    }

    private val pool = listOf(ConscienceLine("a", "A"), ConscienceLine("b", "B"))

    private fun pick(log: ConscienceLog, onDay: Long = today, seed: Int = 1) =
        ConscienceCadence.pickLine(pool, log, onDay, Random(seed))

    @Test
    fun `a line is not repeated within 30 days and returns afterwards`() {
        val log = ConscienceLog(lineUsedOnEpochDay = mapOf("a" to today))
        repeat(20) { seed -> assertEquals("b", pick(log, today + 29, seed)?.id) }
        val both = ConscienceLog(lineUsedOnEpochDay = mapOf("a" to today, "b" to today - 5))
        assertEquals("a", pick(both, today + 30)?.id)
    }

    @Test
    fun `exhausted pool returns null, empty pool too`() {
        val log = ConscienceLog(lineUsedOnEpochDay = mapOf("a" to today, "b" to today - 29))
        assertNull(pick(log))
        assertNull(ConscienceCadence.pickLine(emptyList(), ConscienceLog(), today, Random(1)))
    }

    @Test
    fun `pick reaches every eligible line`() {
        val ids = (0 until 50).mapNotNull { pick(ConscienceLog(), seed = it)?.id }.toSet()
        assertEquals(setOf("a", "b"), ids)
        assertNotNull(pick(ConscienceLog()))
    }

    @Test
    fun `record appends the fire and line and prunes stale entries`() {
        val log = ConscienceLog(
            firedAtMillis = listOf(now - 7 * day, now - 7 * day + minute),
            lineUsedOnEpochDay = mapOf("old" to today - 30, "kept" to today - 29),
        )
        assertEquals(
            ConscienceLog(
                firedAtMillis = listOf(now - 7 * day + minute, now),
                lineUsedOnEpochDay = mapOf("kept" to today - 29, "new" to today),
            ),
            ConscienceCadence.record(log, "new", now, today),
        )
    }

    @Test
    fun `encode and decode round-trip`() {
        val log = ConscienceLog(
            firedAtMillis = listOf(now - day, now),
            lineUsedOnEpochDay = mapOf("jo.food.1" to today, "en.shop.2" to today - 3),
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
                "other|5",
                "fire|123",
                "line|ok|9",
            ),
        )
        assertEquals(ConscienceLog(listOf(123L), mapOf("ok" to 9L)), log)
    }
}
