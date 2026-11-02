package com.pennywiseai.tracker.receiver

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which actions a transaction alert gets; the picker always takes the third slot. */
class SmsBroadcastReceiverAlertLayoutTest {
    private fun layout(type: TransactionType, uncategorized: Boolean, excluded: Boolean = false, bill: Boolean = false) =
        SmsBroadcastReceiver.alertLayout(type, uncategorized, excluded, bill)

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
