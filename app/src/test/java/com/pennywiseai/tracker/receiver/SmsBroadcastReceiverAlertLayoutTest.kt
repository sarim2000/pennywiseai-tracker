package com.pennywiseai.tracker.receiver

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which actions a transaction alert gets; the picker always takes the third slot. */
class SmsBroadcastReceiverAlertLayoutTest {
    private fun layout(type: TransactionType, uncategorized: Boolean, excluded: Boolean = false, bill: Boolean = false) =
        SmsBroadcastReceiver.alertLayout(type, uncategorized, excluded, bill)

    private fun inTrip(type: TransactionType, uncategorized: Boolean, excluded: Boolean = false, bill: Boolean = false) =
        SmsBroadcastReceiver.alertLayout(type, uncategorized, excluded, bill, tripAction = true)

    @Test
    fun `a trip row's Remove from trip takes a quick pick's slot and never pushes past three actions`() {
        // Not an expense + Remove from trip + Change
        assertEquals(0 to true, inTrip(TransactionType.EXPENSE, uncategorized = false))
        assertEquals(0 to true, inTrip(TransactionType.EXPENSE, uncategorized = true))
        // Track/Update price + Remove from trip + More
        assertEquals(0 to false, inTrip(TransactionType.EXPENSE, uncategorized = true, bill = true))
        // an already-excluded unsorted row keeps one quick pick
        assertEquals(1 to false, inTrip(TransactionType.EXPENSE, uncategorized = true, excluded = true))
    }

    @Test
    fun `sorted spending and income get Not an expense plus Change`() {
        listOf(TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.INCOME).forEach {
            assertEquals(it.name, 0 to true, layout(it, uncategorized = false))
        }
    }

    @Test
    fun `unsorted spending and income get one quick pick, Not an expense, More`() {
        listOf(TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.INCOME).forEach {
            assertEquals(it.name, 1 to true, layout(it, uncategorized = true))
        }
    }

    @Test
    fun `transfers and investments keep two quick picks and never offer exclusion`() {
        listOf(TransactionType.TRANSFER, TransactionType.INVESTMENT).forEach {
            assertEquals(it.name, 2 to false, layout(it, uncategorized = true))
            assertEquals(it.name, 0 to false, layout(it, uncategorized = false))
        }
    }

    @Test
    fun `an already-excluded transaction gets no exclusion button`() {
        assertEquals(2 to false, layout(TransactionType.EXPENSE, uncategorized = true, excluded = true))
        assertEquals(0 to false, layout(TransactionType.EXPENSE, uncategorized = false, excluded = true))
    }

    @Test
    fun `a bill's Track or Update price replaces Not an expense`() {
        assertEquals(1 to false, layout(TransactionType.EXPENSE, uncategorized = true, bill = true))
        assertEquals(0 to false, layout(TransactionType.EXPENSE, uncategorized = false, bill = true))
    }
}
