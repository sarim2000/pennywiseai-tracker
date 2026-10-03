package com.pennywiseai.tracker.presentation.add

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SharedTextAmountTest {

    private fun amount(text: String) = SharedTextAmount.extract(text)

    @Test
    fun `currency-tagged amount wins over other numbers`() {
        assertEquals(BigDecimal("1200.50"), amount("Paid Rs. 1,200.50 to Cafe on 03-10-26. UPI Ref 412345678901"))
        assertEquals(BigDecimal("450"), amount("You paid ₹450 to Swiggy"))
        assertEquals(BigDecimal("99"), amount("INR 99 debited from A/c XX1234"))
        assertEquals(BigDecimal("125000"), amount("Salary of Rs 1,25,000 credited"))
    }

    @Test
    fun `bare number is used only in a short note`() {
        assertEquals(BigDecimal("450"), amount("450 swiggy"))
        assertEquals(BigDecimal("80.5"), amount("chai 80.5"))
        assertNull(amount("Your order 12345 from the store was delivered to the address on file today"))
    }

    @Test
    fun `dates and zero are not amounts`() {
        assertNull(amount("lunch 03/10"))
        assertNull(amount("Rs 0 cashback"))
        assertNull(amount("hello"))
        assertNull(amount("Thanks to all our users 50 times over, see you at the next meetup"))
    }
}
