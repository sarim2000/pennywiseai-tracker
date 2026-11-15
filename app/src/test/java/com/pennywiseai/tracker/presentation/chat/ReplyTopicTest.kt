package com.pennywiseai.tracker.presentation.chat

import com.pennywiseai.tracker.data.model.ReplySubject
import com.pennywiseai.tracker.data.repository.QuickAction
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.math.BigDecimal

class ReplyTopicTest {
    /** Every tool name PennyWiseTools declares, so a new tool can't go without a shape. */
    private val tools = PennyWiseTools::class.java.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .map { it.get(null) as String }

    @Test
    fun `every tool maps to a shape of its own`() {
        assertTrue(tools.size >= 6)
        tools.forEach { assertNotEquals(it, MascotShape.Owl, replyShape(it)) }
        assertEquals(MascotShape.Receipt, replyShape(PennyWiseTools.ADD_TRANSACTIONS))
        assertEquals(MascotShape.Receipt, replyShape(PennyWiseTools.DELETE_TRANSACTION))
        assertEquals(MascotShape.Receipt, replyShape(PennyWiseTools.UPDATE_TRANSACTION))
        assertEquals(MascotShape.Pie, replyShape(PennyWiseTools.SPENDING_BY_CATEGORY))
        assertEquals(MascotShape.Bars, replyShape(PennyWiseTools.COMPARE_SPENDING))
        assertEquals(MascotShape.Coin, replyShape(PennyWiseTools.MONTH_SUMMARY))
    }

    @Test
    fun `every quick action maps to the shape of the tool that answers the same question`() {
        QuickAction.entries.forEach { assertTrue(it.name, it.tool in tools) }
        assertEquals(MascotShape.Coin, replyShape(QuickAction.MONTH_SPEND.tool))
        assertEquals(MascotShape.Pie, replyShape(QuickAction.TOP_CATEGORIES.tool))
        assertEquals(MascotShape.Bars, replyShape(QuickAction.COMPARE_MONTH.tool))
    }

    @Test
    fun `the model's own words are the owl, calm and without a badge`() {
        val topic = replyTopic(ReplySubject(messageId = "m"))
        assertEquals(ReplyTopic(messageId = "m"), topic)
        assertEquals(MascotShape.Owl, replyShape("an_unknown_tool"))
    }

    @Test
    fun `a confirmed Add is a happy receipt saying Saved`() {
        val topic = replyTopic(ReplySubject(messageId = "m", tool = PennyWiseTools.ADD_TRANSACTIONS, saved = true))
        assertEquals(MascotShape.Receipt, topic.shape)
        assertEquals(MascotMood.Happy, topic.mood)
        assertEquals(ReplyBadge.Saved, topic.badge)
        // Proposed, not yet saved: calm, no badge.
        val proposed = replyTopic(ReplySubject(messageId = "m", tool = PennyWiseTools.ADD_TRANSACTIONS))
        assertEquals(MascotMood.Calm, proposed.mood)
        assertNull(proposed.badge)
    }

    @Test
    fun `the coin shows the month total in one currency, and only counts several`() {
        val total = Money(BigDecimal("1200"), "INR")
        assertEquals(ReplyBadge.Total(total), replyTopic(ReplySubject(tool = PennyWiseTools.MONTH_SUMMARY, monthTotal = total)).badge)
        assertEquals(ReplyBadge.Currencies(2), replyTopic(ReplySubject(tool = PennyWiseTools.MONTH_SUMMARY, currencies = 2)).badge)
        assertNull(replyTopic(ReplySubject(tool = PennyWiseTools.MONTH_SUMMARY)).badge)
        // Only the coin carries a total.
        assertNull(replyTopic(ReplySubject(tool = PennyWiseTools.COMPARE_SPENDING, monthTotal = total)).badge)
    }

    @Test
    fun `pie and bars carry their figure`() {
        assertEquals(0.4f, replyTopic(ReplySubject(tool = PennyWiseTools.SPENDING_BY_CATEGORY, share = 0.4f)).value)
        assertEquals(-1f, replyTopic(ReplySubject(tool = PennyWiseTools.COMPARE_SPENDING, trend = -1)).value)
        assertEquals(1f, replyTopic(ReplySubject(tool = PennyWiseTools.COMPARE_SPENDING, trend = 1)).value)
        assertEquals(0f, replyTopic(ReplySubject(tool = PennyWiseTools.MONTH_SUMMARY, share = 0.4f)).value)
    }
}
