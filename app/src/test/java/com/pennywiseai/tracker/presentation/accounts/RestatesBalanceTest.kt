package com.pennywiseai.tracker.presentation.accounts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RestatesBalanceTest {
    @Test
    fun aRenameKeepsTheAnchor() {
        assertFalse(restatesBalance(BigDecimal("900"), "INR", BigDecimal("900.00"), "INR"))
    }

    @Test
    fun aNewFigureMovesTheAnchor() {
        assertTrue(restatesBalance(BigDecimal("900"), "INR", BigDecimal("1000"), "INR"))
    }

    @Test
    fun theSameFigureInAnotherCurrencyMovesTheAnchor() {
        // Anchored at INR 1,000 and now holding INR 900: saving it as USD 900 must not
        // leave net worth saying the user entered USD 1,000.
        assertTrue(restatesBalance(BigDecimal("900"), "INR", BigDecimal("900"), "USD"))
    }

    @Test
    fun aFirstBalanceIsAnAnchor() {
        assertTrue(restatesBalance(null, null, BigDecimal("900"), "INR"))
    }
}
