package com.pennywiseai.tracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {

    @Test
    fun `digits from an Arabic keyboard become an amount`() {
        assertEquals("3.5", AmountInput.normalize("٣٫٥", decimalComma = false))
        assertEquals("1250", AmountInput.normalize("١٬٢٥٠", decimalComma = false))
        assertEquals("", AmountInput.normalize("", decimalComma = false))
    }

    @Test
    fun `a comma is a decimal point only where the locale writes it so`() {
        // en-IN: "1,250" is twelve hundred and fifty, not 1.25.
        assertEquals("1250", AmountInput.normalize("1,250", decimalComma = false))
        assertEquals("12.750", AmountInput.normalize("12,750", decimalComma = true))
        assertNull(AmountInput.normalize("1,2,3", decimalComma = true))
    }

    @Test
    fun `a second decimal point or a letter is refused`() {
        assertNull(AmountInput.normalize("1.2.3", decimalComma = false))
        assertNull(AmountInput.normalize("1٫2.", decimalComma = false))
        assertNull(AmountInput.normalize("-5", decimalComma = false))
        assertNull(AmountInput.normalize("5a", decimalComma = false))
    }

    @Test
    fun `sums take the keyboard's ASCII operators in the bar's form`() {
        assertEquals("120+35", AmountInput.normalize("120+35", decimalComma = false))
        assertEquals("10−2×3÷4", AmountInput.normalize("10-2*3/4", decimalComma = false))
        assertEquals("12+", AmountInput.normalize("12+", decimalComma = false))
    }

    @Test
    fun `a sum can't start with an operator or have two in a row`() {
        assertNull(AmountInput.normalize("+12", decimalComma = false))
        assertNull(AmountInput.normalize("−12", decimalComma = false))
        assertNull(AmountInput.normalize("12++3", decimalComma = false))
        assertNull(AmountInput.normalize("1.2.3+4", decimalComma = false))
    }

    @Test
    fun `an operator is added at the end, replacing one already there`() {
        assertEquals("12+", AmountInput.append("12", '+'))
        assertEquals("12×", AmountInput.append("12+", '×'))
        assertEquals("", AmountInput.append("", '+'))
    }

    @Test
    fun `value reads a plain amount or a finished sum`() {
        assertEquals("155.00", AmountInput.value("120+35", 2)?.toPlainString())
        assertEquals("12.5", AmountInput.value("12.5", 2)?.toPlainString())
        assertNull(AmountInput.value("12+", 2))
        assertTrue(AmountInput.hasOperator("1÷2"))
        assertFalse(AmountInput.hasOperator("12.5"))
    }

    @Test
    fun `fold gives the result, ignoring a trailing operator, only above zero`() {
        assertEquals("155", AmountInput.fold("120+35", 2))
        assertEquals("72.5", AmountInput.fold("12.5+30×2", 2))
        assertEquals("12", AmountInput.fold("12+", 2))
        assertEquals("3.33", AmountInput.fold("10÷3", 2))
        assertNull(AmountInput.fold("5−5", 2))
        assertNull(AmountInput.fold("5÷0", 2))
        assertNull(AmountInput.fold("12", 2))
    }
}
