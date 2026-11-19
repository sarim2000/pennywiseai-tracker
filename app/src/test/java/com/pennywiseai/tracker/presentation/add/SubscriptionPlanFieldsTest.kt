package com.pennywiseai.tracker.presentation.add

import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The add form's "Ends after" and "Already paid" (#1061). */
class SubscriptionPlanFieldsTest {
    private fun form(total: String, made: String = "", direction: SubscriptionDirection = SubscriptionDirection.EXPENSE) =
        SubscriptionUiState(
            serviceName = "Synthetic EMI", amount = "1500", category = "EMI",
            totalPayments = total, paymentsMade = made, direction = direction,
        )

    @Test
    fun `already paid has to leave a payment to make`() {
        assertFalse(form("12", "3").paymentsMadeError)
        assertTrue(form("12", "3").isValid)
        assertTrue(form("12", "12").paymentsMadeError)
        assertFalse(form("12", "12").isValid)
    }

    @Test
    fun `no end means already paid is ignored`() {
        assertNull(form("").endsAfter)
        assertNull(form("0").endsAfter)
        assertFalse(form("", "5").paymentsMadeError)
        // An income has no end, even with the digits left over from the expense side.
        assertNull(form("12", "20", SubscriptionDirection.INCOME).endsAfter)
        assertFalse(form("12", "20", SubscriptionDirection.INCOME).paymentsMadeError)
        assertEquals(12, form("12").endsAfter)
    }
}
