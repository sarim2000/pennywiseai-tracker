package com.pennywiseai.tracker.domain.loan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #824. The DAO's repayment and principal sums intentionally carry no currency
 * filter, so this guard is the only thing keeping a loan's totals in one
 * currency. If it ever stops holding, the cross-currency bugs come straight
 * back — and worse, a principal excluded from the recount can delete the loan.
 */
class LoanCurrencyRulesTest {

    @Test
    fun `changing the currency of a linked transaction is blocked`() {
        assertTrue(LoanCurrencyRules.blocksCurrencyChange(7L, "INR", "USD"))
    }

    @Test
    fun `an unlinked transaction can change currency freely`() {
        assertFalse(LoanCurrencyRules.blocksCurrencyChange(null, "INR", "USD"))
    }

    @Test
    fun `editing a linked transaction without touching currency is allowed`() {
        assertFalse(LoanCurrencyRules.blocksCurrencyChange(7L, "INR", "INR"))
    }

    @Test
    fun `case is not a currency change`() {
        assertFalse(LoanCurrencyRules.blocksCurrencyChange(7L, "USD", "usd"))
    }
}
