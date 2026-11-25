package com.pennywiseai.tracker.domain.conscience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConscienceRemarksTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun remark(id: Long, at: Long = now, key: String = "ar:hello_0$id") =
        ConscienceRemark(id = id, atMillis = at, text = "line $id", kind = ConscienceKind.HELLO, lineKey = key)

    private fun react(
        remarks: List<ConscienceRemark>,
        id: Long,
        reaction: ConscienceReaction,
        feedback: ConscienceFeedback = ConscienceFeedback(),
        key: String = "",
    ) = ConscienceRemarks.react(remarks, feedback, id, key, reaction)

    @Test
    fun `add keeps the newest first, at most twenty, none older than 30 days`() {
        val old = remark(1, at = now - 30 * day)
        val kept = remark(2, at = now - 29 * day)
        assertEquals(listOf(3L, 2L), ConscienceRemarks.add(listOf(kept, old), remark(3), now).map { it.id })

        val many = (1L..25L).map { remark(it) }.reversed()
        val added = ConscienceRemarks.add(many, remark(26), now)
        assertEquals(ConscienceRemarks.MAX, added.size)
        assertEquals(26L, added.first().id)
    }

    @Test
    fun `remarks expire at 30 days without a new remark being added`() {
        val stored = listOf(remark(2, at = now - 29 * day), remark(1, at = now - 30 * day))
        assertEquals(listOf(2L, 1L), ConscienceRemarks.fresh(stored, now - day).map { it.id })
        assertEquals(listOf(2L), ConscienceRemarks.fresh(stored, now).map { it.id })
        assertTrue(ConscienceRemarks.fresh(stored, now + day).isEmpty())
    }

    @Test
    fun `adding the same remark twice keeps one`() {
        val once = ConscienceRemarks.add(emptyList(), remark(1), now)
        assertEquals(1, ConscienceRemarks.add(once, remark(1), now).size)
    }

    @Test
    fun `the first reaction wins and lands in the feedback`() {
        val remarks = listOf(remark(1, key = "ar:spike_02"))
        val (liked, feedback) = react(remarks, 1, ConscienceReaction.LIKED)!!
        assertEquals(ConscienceReaction.LIKED, liked.single().reaction)
        assertEquals(setOf("ar:spike_02"), feedback.liked)
        assertNull(react(liked, 1, ConscienceReaction.RETIRED, feedback, key = "ar:spike_02"))
    }

    @Test
    fun `the stored remark's line wins over the tap's`() {
        val (_, feedback) = react(listOf(remark(1, key = "ar:spike_02")), 1, ConscienceReaction.RETIRED, key = "ar:other")!!
        assertEquals(setOf("ar:spike_02"), feedback.retired)
    }

    @Test
    fun `retiring a liked line drops the like`() {
        val feedback = ConscienceFeedback(liked = setOf("ar:spike_02"))
        val (_, after) = react(listOf(remark(1, key = "ar:spike_02")), 1, ConscienceReaction.RETIRED, feedback)!!
        assertTrue(after.liked.isEmpty())
        assertEquals(setOf("ar:spike_02"), after.retired)
    }

    @Test
    fun `liking an older remark doesn't bring a retired line back`() {
        val feedback = ConscienceFeedback(retired = setOf("ar:spike_02"))
        val (remarks, after) = react(listOf(remark(1, key = "ar:spike_02")), 1, ConscienceReaction.LIKED, feedback)!!
        assertEquals(ConscienceReaction.LIKED, remarks.single().reaction)
        assertEquals(setOf("ar:spike_02"), after.retired)
        assertTrue(after.liked.isEmpty())
    }

    @Test
    fun `a remark that's gone still rates its line from the tap, with no key it changes nothing`() {
        // A pruned remark, or one posted before remarks were stored: "Not this one" must still hold.
        val (remarks, feedback) = react(listOf(remark(1)), 99, ConscienceReaction.RETIRED, key = "en:late_night_03")!!
        assertEquals(listOf(remark(1)), remarks)
        assertEquals(setOf("en:late_night_03"), feedback.retired)
        assertNull(react(listOf(remark(1)), 99, ConscienceReaction.LIKED))
    }
}
