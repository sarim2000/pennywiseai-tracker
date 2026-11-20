package com.pennywiseai.tracker.ui.components

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountEntryTest {

    /** A field whose caller takes every amount it's handed, as most do. */
    private class Field(currency: String = "INR", private val accept: (String) -> Boolean = { true }) {
        var amount = ""
        val entry = AmountEntry(null, "", "").also {
            it.currency = currency
            it.onAmountChange = { new -> if (accept(new)) amount = new }
        }

        fun type(text: String) {
            entry.amount = amount
            entry.onValueChange(TextFieldValue(text))
            entry.amount = amount
        }

        fun operator(op: Char) {
            entry.amount = amount
            entry.operator(op)
            entry.amount = amount
        }

        val shown: String get() = entry.value.text
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
        f.entry.amount = f.amount
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
        f.entry.amount = f.amount
        assertEquals("40", f.shown)
    }
}
