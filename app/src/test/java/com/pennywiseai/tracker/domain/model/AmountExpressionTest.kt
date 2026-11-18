package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.domain.model.AmountExpression.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AmountExpressionTest {

    private fun eval(input: String, digits: Int = 2) =
        AmountExpression.evaluate(input, digits)?.toPlainString()

    @Test
    fun `multiplication binds tighter than addition, rounded to the currency's decimals`() {
        assertEquals("72.50", eval("12.5+30×2"))
        assertEquals("73", eval("12.5+30×2", digits = 0))
        assertEquals("72.500", eval("12.5+30×2", digits = 3))
    }

    @Test
    fun `brackets, subtraction and division`() {
        assertEquals("85.00", eval("(12.5+30)×2"))
        assertEquals("3.33", eval("10÷3"))
        assertEquals("3.333", eval("10÷3", digits = 3))
        assertEquals("5.00", eval("10−5"))
        assertEquals("1.00", eval("10-4-5"))
        assertEquals("2.00", eval("8/2/2"))
        assertEquals("4.50", eval("((1.5))*3"))
    }

    @Test
    fun `division stays exact until the final rounding`() {
        // 1÷3×3 is exactly 1, so this is 0.105 and rounds up; a 34-digit quotient gave 0.10.
        assertEquals("0.11", eval("1÷3×3−0.895"))
        assertEquals("1.00", eval("1÷3+1÷3+1÷3"))
        assertEquals("33.33", eval("100÷3"))
    }

    @Test
    fun `halves round up`() {
        assertEquals("0.13", eval("0.125"))
        assertEquals("3", eval("2.5", digits = 0))
    }

    @Test
    fun `a plain amount is its own result`() {
        assertEquals("250.00", eval("250"))
        assertEquals("0.50", eval(".5"))
        assertEquals("5.00", eval("5."))
    }

    @Test
    fun `negative and zero results come back for the caller to reject`() {
        assertEquals("-20.00", eval("10-30"))
        assertEquals("-5.00", eval("-5"))
        assertEquals("0.00", eval("5-5"))
    }

    @Test
    fun `division by zero is no result`() {
        assertNull(eval("5÷0"))
        assertNull(eval("5/(2-2)"))
    }

    @Test
    fun `malformed input is no result`() {
        listOf("", "+", "12+", "×3", "(1+2", "1+2)", "1..2", "1.2.3", ".", "()", "1e5", "abc", "2(3)")
            .forEach { assertNull("'$it'", eval(it)) }
    }

    @Test
    fun `huge numbers and over-long input are no result`() {
        assertNull(eval("999999999999999×10"))
        assertNull(eval("1000000000000000"))
        assertEquals("999999999999999.00", eval("999999999999999"))
        assertNull(eval("1+".repeat(40) + "1"))
        assertNull(eval("(".repeat(40) + "1" + ")".repeat(40)))
        // Over-long is rejected whole, never read as a shorter (different) number.
        assertNull(eval("0".repeat(63) + "12"))
    }

    @Test
    fun `local digits and the Arabic decimal mark are read`() {
        assertEquals("15.50", eval("١٢٫٥+٣"))
    }

    @Test
    fun `no amount says why, so the keypad can explain it`() {
        assertEquals(Outcome.DividesByZero, AmountExpression.check("5÷0", 2))
        assertEquals(Outcome.DividesByZero, AmountExpression.check("1+5/(2-2)×3", 2))
        assertEquals(Outcome.TooLarge, AmountExpression.check("999999999999999×10", 2))
        listOf("", "12+", "(1+2", "1..2", "1+".repeat(40) + "1")
            .forEach { assertEquals("'$it'", Outcome.Unfinished, AmountExpression.check(it, 2)) }
        assertEquals(Outcome.Amount(BigDecimal("2.50")), AmountExpression.check("5÷2", 2))
    }
}
