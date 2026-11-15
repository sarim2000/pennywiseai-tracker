package com.pennywiseai.tracker.domain.loan

import com.pennywiseai.tracker.data.database.entity.LoanDirection

/**
 * What the part of a repayment beyond what's still owed is.
 *
 * You owed someone ₹1,000 and paid ₹1,500: by default the extra ₹500 is your
 * own spend (#831). When it was really money they now owe you (an employer's
 * advance you overspent, a friend you paid back too much), the loan turns
 * around instead: it becomes a ₹500 loan the other way, so the payment counts
 * nothing in your totals and the ₹500 waits to be repaid.
 */
enum class LoanOverflow {
    /** The extra counts as ordinary spend or income (#831). */
    COUNT_AS_OWN,

    /** The extra opens the debt the other way: the loan flips direction. */
    FLIP_DEBT
}

fun LoanDirection.opposite(): LoanDirection = when (this) {
    LoanDirection.LENT -> LoanDirection.BORROWED
    LoanDirection.BORROWED -> LoanDirection.LENT
}
