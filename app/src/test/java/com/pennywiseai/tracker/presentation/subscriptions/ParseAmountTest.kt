package com.pennywiseai.tracker.presentation.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ParseAmountTest {
    @Test
    fun `the last separator is the decimal point`() {
        assertEquals(BigDecimal("1234.50"), parseAmount("1,234.50"))
        assertEquals(BigDecimal("1234.50"), parseAmount("1.234,50"))
        assertEquals(BigDecimal("19.900"), parseAmount("19,900"))
        assertEquals(BigDecimal("19.900"), parseAmount(" 19.900 "))
        assertEquals(BigDecimal("25"), parseAmount("25"))
    }

    @Test
    fun `blank, zero and junk are rejected`() {
        assertNull(parseAmount(""))
        assertNull(parseAmount("0"))
        assertNull(parseAmount("abc"))
    }
}
