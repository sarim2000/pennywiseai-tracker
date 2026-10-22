package java.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Expected values are what java.math.BigDecimal returns for the same calls. */
class BigDecimalShimTest {
    @Test
    fun parsesLikeTheJdk() {
        assertEquals("450.00", BigDecimal("450.00").toPlainString())
        assertEquals("0.5", BigDecimal(".5").toPlainString())
        assertEquals("5", BigDecimal("5.").toPlainString())
        assertEquals("-12.30", BigDecimal("-12.30").toPlainString())
        assertEquals("1500", BigDecimal("1.5E3").toPlainString())
        assertEquals(2, BigDecimal("1.25").scale())
        for (bad in listOf("", "1,000", " 1", "1.2.3", "abc", ".", "-")) {
            assertFailsWith<NumberFormatException>(bad) { BigDecimal(bad) }
        }
    }

    @Test
    fun roundsLikeTheJdk() {
        assertEquals("2.35", BigDecimal("2.345").setScale(2, RoundingMode.HALF_UP).toPlainString())
        assertEquals("-2.35", BigDecimal("-2.345").setScale(2, RoundingMode.HALF_UP).toPlainString())
        assertEquals("2.34", BigDecimal("2.345").setScale(2, RoundingMode.HALF_EVEN).toPlainString())
        assertEquals("450.00", BigDecimal("450").setScale(2).toPlainString())
        assertFailsWith<ArithmeticException> { BigDecimal("1.234").setScale(2) }
    }

    @Test
    fun comparesByValueButEqualsByScale() {
        assertEquals(0, BigDecimal("2.0").compareTo(BigDecimal("2.00")))
        assertNotEquals(BigDecimal("2.0"), BigDecimal("2.00"))
        assertTrue(BigDecimal("1000") >= BigDecimal.valueOf(1000))
        assertTrue(BigDecimal("-0.01") < BigDecimal.ZERO)
    }

    @Test
    fun arithmeticKeepsScale() {
        assertEquals("12.75", (BigDecimal("10.5") + BigDecimal("2.25")).toPlainString())
        assertEquals("8.25", (BigDecimal("10.5") - BigDecimal("2.25")).toPlainString())
        assertEquals("23.625", (BigDecimal("10.5") * BigDecimal("2.25")).toPlainString())
        assertEquals("-10.5", BigDecimal("10.5").negate().toPlainString())
        assertEquals(10, BigDecimal("10.99").toInt())
    }

    @Test
    fun rejectsAmountsTooLongForALong() {
        assertFailsWith<NumberFormatException> { BigDecimal("1234567890123456789") }
    }
}
