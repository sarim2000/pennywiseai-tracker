package com.pennywiseai.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountEntryTest {

    /** A field whose caller takes every amount it's handed, as most do; the caller hands it back. */
    private class Field(currency: String = "INR", private val accept: (String) -> Boolean = { true }) {
        var amount = ""
        val entry = AmountEntry("").also {
            it.currency = currency
            it.onAmountChange = { new -> if (accept(new)) amount = new }
        }

        fun type(text: String) {
            val before = amount
            entry.state.edit { replace(0, length, text) }
            entry.textChanged(text)
            if (amount != before) entry.amountChanged(amount)
        }

        fun operator(op: Char) {
            val before = amount
            entry.operator(op)
            if (amount != before) entry.amountChanged(amount)
        }

        val shown: String get() = entry.state.text.toString()
    }

    @Test
    fun `the caller gets what the sum comes to, and the field shows the sum`() {
        val f = Field()
        f.type("120")
        f.operator('+')
        f.type("120+35")
        assertEquals("155.00", f.amount)
        assertEquals("120+35", f.shown)
    }

    @Test
    fun `a sum still being typed counts as what's before the operator`() {
        val f = Field()
        f.type("120")
        f.operator('+')
        assertEquals("120", f.amount)
        assertEquals("120+", f.shown)
    }

    @Test
    fun `a sum that can't be an amount hands on nothing`() {
        val f = Field()
        f.type("5÷0")
        assertEquals("", f.amount)
        assertEquals("5÷0", f.shown)
    }

    @Test
    fun `folding puts the result in the field`() {
        val f = Field("JOD")
        f.type("10÷3")
        f.entry.fold()
        assertEquals("3.333", f.amount)
        assertEquals("3.333", f.shown)
    }

    @Test
    fun `a caller that keeps its last amount still shows the sum`() {
        // The transaction editor ignores an empty amount instead of clearing it.
        val f = Field { it.isNotEmpty() }
        f.type("12")
        f.type("12−12")
        assertEquals("12", f.amount)
        assertEquals("12−12", f.shown)
    }

    @Test
    fun `an amount set from outside replaces the sum`() {
        val f = Field()
        f.type("120+35")
        f.amount = "40"
        f.entry.amountChanged("40")
        assertEquals("40", f.shown)
    }

    @Test
    fun `an amount handed back late doesn't undo newer typing`() {
        val f = Field()
        f.entry.state.edit { replace(0, length, "1") }
        f.entry.textChanged("1")
        f.entry.state.edit { replace(0, length, "12") }
        f.entry.textChanged("12")
        f.entry.amountChanged("1")
        assertEquals("12", f.shown)
    }

    @Test
    fun `decimals finer than the currency are turned away`() {
        assertEquals("1.23", normalizeAmount("1.23", 2, decimalComma = false))
        assertNull(normalizeAmount("1.234", 2, decimalComma = false))
        assertEquals("1.234+2", normalizeAmount("1.234+2", 3, decimalComma = false))
        assertEquals("1.5", normalizeAmount("1,5", 2, decimalComma = true))
    }

    @Test
    fun `a restored field that disagrees with the caller takes the caller's amount`() {
        val f = Field()
        f.entry.state.edit { replace(0, length, "3500") } // restored text
        f.amount = "5500" // the caller came back with its own
        f.entry.amountChanged(f.amount)
        assertEquals("5500", f.shown)
    }

    @Test
    fun `a restored sum the caller agrees with stays`() {
        val f = Field()
        f.entry.state.edit { replace(0, length, "120+35") }
        f.entry.amountChanged("155.00")
        assertEquals("120+35", f.shown)
    }

    @Test
    fun `after a switch to fewer decimals, deleting them still works`() {
        assertEquals("12.5", normalizeAmount("12.5", 0, decimalComma = false, before = "12.55"))
        assertNull(normalizeAmount("12.555", 2, decimalComma = false, before = "12.55"))
    }
}
