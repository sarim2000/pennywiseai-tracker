package com.pennywiseai.tracker.domain.conscience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConscienceRemarksTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun remark(id: Long, at: Long = now, key: String = "en:hello_0$id") =
        ConscienceRemark(id = id, atMillis = at, text = "line $id", kind = ConscienceKind.HELLO, lineKey = key)

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
        // Reopened later with nothing new: the old one is gone and can't be rated.
        val later = ConscienceRemarks.fresh(stored, now)
        assertEquals(listOf(2L), later.map { it.id })
        assertNull(ConscienceRemarks.react(later, ConscienceFeedback(), 1, ConscienceReaction.LIKED))
        assertTrue(ConscienceRemarks.fresh(stored, now + day).isEmpty())
    }

    @Test
    fun `adding the same remark twice keeps one`() {
        val once = ConscienceRemarks.add(emptyList(), remark(1), now)
        assertEquals(1, ConscienceRemarks.add(once, remark(1), now).size)
    }

    @Test
    fun `the first reaction wins and lands in the feedback`() {
        val remarks = listOf(remark(1, key = "en:spike_02"))
        val (liked, feedback) = ConscienceRemarks.react(remarks, ConscienceFeedback(), 1, ConscienceReaction.LIKED)!!
        assertEquals(ConscienceReaction.LIKED, liked.single().reaction)
        assertEquals(setOf("en:spike_02"), feedback.liked)
        assertNull(ConscienceRemarks.react(liked, feedback, 1, ConscienceReaction.RETIRED))
    }

    @Test
    fun `retiring a liked line drops the like`() {
        val feedback = ConscienceFeedback(liked = setOf("en:spike_02"))
        val (_, after) = ConscienceRemarks.react(listOf(remark(1, key = "en:spike_02")), feedback, 1, ConscienceReaction.RETIRED)!!
        assertTrue(after.liked.isEmpty())
        assertEquals(setOf("en:spike_02"), after.retired)
    }

    @Test
    fun `liking an older remark doesn't bring a retired line back`() {
        val feedback = ConscienceFeedback(retired = setOf("en:spike_02"))
        val (remarks, after) = ConscienceRemarks.react(listOf(remark(1, key = "en:spike_02")), feedback, 1, ConscienceReaction.LIKED)!!
        assertEquals(ConscienceReaction.LIKED, remarks.single().reaction)
        assertEquals(setOf("en:spike_02"), after.retired)
        assertTrue(after.liked.isEmpty())
    }

    @Test
    fun `an unknown remark changes nothing`() {
        assertNull(ConscienceRemarks.react(listOf(remark(1)), ConscienceFeedback(), 99, ConscienceReaction.LIKED))
    }
}
