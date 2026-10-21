package com.pennywiseai.tracker.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

/** The two one-tap answers on an unsorted transaction alert. */
class SmsBroadcastReceiverQuickPicksTest {
    private val expense = listOf("Food & Dining", "Groceries", "Shopping", "Others")

    @Test
    fun `most-used categories of the transaction's type come first`() =
        // "Income" and "Banking"-style picks from the other type never show on a purchase.
        assertEquals(
            listOf("Shopping", "Groceries"),
            SmsBroadcastReceiver.quickPicks(listOf("Others", "Income", "Shopping", "Groceries"), expense, exclude = "Others")
        )

    @Test
    fun `a new install with no history still gets two buttons`() =
        assertEquals(
            listOf("Food & Dining", "Groceries"),
            SmsBroadcastReceiver.quickPicks(emptyList(), expense, exclude = "Others")
        )

    @Test
    fun `Others is the question, never an answer`() =
        assertEquals(
            listOf("Shopping"),
            SmsBroadcastReceiver.quickPicks(listOf("Others"), listOf("Others", "Shopping"), exclude = "Others")
        )
}
