package com.pennywiseai.tracker.domain.loan

/**
 * The one rule keeping a loan's totals in a single currency (#824).
 *
 * Every amount on a loan — principal, repayments, remaining — is in the loan's
 * own currency, and the DAO deliberately doesn't filter by currency once a
 * transaction is linked: filtering there would drop a loan's own principal from
 * `unlinkTransaction`'s recount, which can zero it and delete the loan. That
 * only holds while a linked transaction's currency cannot change, which is what
 * this guards.
 */
object LoanCurrencyRules {

    /**
     * Whether an edit must be refused because it would change the currency of a
     * transaction linked to a loan. Unlink it first.
     *
     * Case-insensitive: "usd" and "USD" are the same currency, not a change.
     */
    fun blocksCurrencyChange(
        linkedLoanId: Long?,
        originalCurrency: String,
        editedCurrency: String
    ): Boolean =
        linkedLoanId != null && !editedCurrency.equals(originalCurrency, ignoreCase = true)
}
