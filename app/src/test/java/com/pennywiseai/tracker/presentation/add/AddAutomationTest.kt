package com.pennywiseai.tracker.presentation.add

import com.pennywiseai.tracker.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddAutomationTest {

    private fun prefill(vararg values: Pair<String, Any?>) = AddAutomation.prefill(mapOf(*values)::get)

    @Test
    fun `valid extras fill the form`() {
        val add = prefill(
            "amount" to "12.50",
            "currency" to "usd",
            "merchant" to "  Test Cafe ",
            "category" to "Food & Dining",
            "type" to "expense",
            "note" to "from tasker",
            "account_last4" to "1234",
            "bank" to "Test Bank",
        )
        assertEquals("12.50", add.amount)
        assertEquals("USD", add.currency)
        assertEquals("Test Cafe", add.merchant)
        assertEquals("Food & Dining", add.category)
        assertEquals("EXPENSE", add.type)
        assertEquals("from tasker", add.note)
        assertEquals("1234", add.accountLast4)
        assertEquals("Test Bank", add.bankName)
    }

    @Test
    fun `nothing given opens an empty form`() {
        val add = prefill()
        assertNull(add.amount)
        assertNull(add.currency)
        assertNull(add.merchant)
        assertNull(add.type)
        assertNull(add.note)
    }

    @Test
    fun `an amount that is not a positive decimal is dropped`() {
        listOf("abc", "0", "-5", "1,234.50", "", "  ", "1E999999999", "9".repeat(40)).forEach {
            assertNull(it, prefill("amount" to it).amount)
        }
    }

    @Test
    fun `an exponent at the Int limit is dropped, not expanded`() {
        // precision - scale overflows an Int here; it must still count as far too many digits.
        listOf("1E2147483647", "99E2147483647", "1E2147483640").forEach {
            assertNull(it, prefill("amount" to it).amount)
        }
    }

    @Test
    fun `fifteen whole digits is the most an amount can have`() {
        assertEquals("999999999999999", prefill("amount" to "999999999999999").amount)
        assertEquals("100000000000000", prefill("amount" to "1E14").amount)
        assertNull(prefill("amount" to "1000000000000000").amount)
        assertNull(prefill("amount" to "1E15").amount)
    }

    @Test
    fun `a number extra is read as its text`() {
        assertEquals("12.5", prefill("amount" to 12.5).amount)
        assertEquals("40", prefill("amount" to 40).amount)
        assertEquals("10000000", prefill("amount" to 1.0E7).amount)
        assertEquals("1234", prefill("account_last4" to 1234).accountLast4)
    }

    @Test
    fun `other value types are ignored`() {
        assertNull(prefill("merchant" to listOf("Test Cafe")).merchant)
        assertNull(prefill("amount" to true).amount)
    }

    @Test
    fun `an unknown currency is dropped`() {
        assertNull(prefill("currency" to "XYZ").currency)
        assertNull(prefill("currency" to "dollars").currency)
        assertEquals("AED", prefill("currency" to "AED").currency)
    }

    @Test
    fun `an amount in an unknown currency is dropped, not moved to the base currency`() {
        val add = prefill("amount" to "12.50", "currency" to "XYZ", "merchant" to "Test Cafe")
        assertNull(add.amount)
        assertNull(add.currency)
        assertEquals("Test Cafe", add.merchant)
        assertNull(prefill(MainActivity.EXTRA_ADD_AMOUNT to "12.50", MainActivity.EXTRA_ADD_CURRENCY to "XYZ").amount)
    }

    @Test
    fun `an amount without a currency is kept for the base currency`() {
        val add = prefill("amount" to "12.50")
        assertEquals("12.50", add.amount)
        assertNull(add.currency)
        assertEquals("12.50", prefill("amount" to "12.50", "currency" to "usd").amount)
    }

    @Test
    fun `an unknown type is dropped`() {
        assertNull(prefill("type" to "TRANSFER").type)
        assertNull(prefill("type" to "spend").type)
        assertEquals("INCOME", prefill("type" to "Income").type)
    }

    @Test
    fun `long text is capped`() {
        val add = prefill("merchant" to "m".repeat(500), "note" to "n".repeat(500))
        assertEquals(AddAutomation.MAX_TEXT, add.merchant?.length)
        assertEquals(AddAutomation.MAX_TEXT, add.note?.length)
    }

    @Test
    fun `the long extra names still work, the short key wins`() {
        val add = prefill(
            MainActivity.EXTRA_ADD_AMOUNT to "7.25",
            MainActivity.EXTRA_ADD_MERCHANT to "Old Name",
            "merchant" to "New Name",
        )
        assertEquals("7.25", add.amount)
        assertEquals("New Name", add.merchant)
    }
}
