package com.pennywiseai.tracker.domain.loan

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

/**
 * How a loan's linked transactions add up. Money in is INCOME and money out is
 * every other type: the same split "Mark as loan" uses to pick a direction.
 * Before, only EXPENSE counted as money out, so a ₹220 UPI repayment that the
 * parser typed CREDIT or TRANSFER showed in the loan's history yet left it at
 * "0% repaid" (#876).
 *
 * A loan's rows all share its currency (only same-currency transactions can be
 * linked), so summing them is safe.
 */
object LoanMath {
    /** True when a [type] row adds to a [direction] loan's principal; any other linked row repays it. */
    fun isPrincipal(direction: LoanDirection, type: TransactionType): Boolean =
        (type == TransactionType.INCOME) == (direction == LoanDirection.BORROWED)

    /** What [linked] adds to a [direction] loan's principal. */
    fun principal(direction: LoanDirection, linked: List<TransactionEntity>): BigDecimal =
        loanPart(linked) { isPrincipal(direction, it.transactionType) }

    /** What [linked] has repaid of a [direction] loan. */
    fun repaid(direction: LoanDirection, linked: List<TransactionEntity>): BigDecimal =
        loanPart(linked) { !isPrincipal(direction, it.transactionType) }

    /**
     * Whether retyping a row from [original] to [edited] crosses the money-in /
     * money-out line. On a loan-linked row that would turn principal into a
     * repayment (or back): a borrowed ₹1,000 retyped TRANSFER would repay itself.
     */
    fun crossesDirection(original: TransactionType, edited: TransactionType): Boolean =
        (original == TransactionType.INCOME) != (edited == TransactionType.INCOME)

    /** Whether an edit must be refused: it would retype a loan-linked row across that line. Unlink it first. */
    fun blocksTypeChange(linkedLoanId: Long?, original: TransactionType, edited: TransactionType): Boolean =
        linkedLoanId != null && crossesDirection(original, edited)

    /** [updated] as a rule may write it over [persisted]: a loan-linked row keeps a type on its side of the line. */
    fun keepLoanDirection(persisted: TransactionEntity, updated: TransactionEntity): TransactionEntity =
        if (blocksTypeChange(persisted.loanId, persisted.transactionType, updated.transactionType)) {
            updated.copy(transactionType = persisted.transactionType)
        } else {
            updated
        }

    /** Still owed: never below zero, an overpayment's extra counts as ordinary money (#831). */
    fun remaining(originalAmount: BigDecimal, repaid: BigDecimal): BigDecimal =
        (originalAmount - repaid).coerceAtLeast(BigDecimal.ZERO)

    // Each row counts only its loan part (#681), and never once deleted.
    private fun loanPart(linked: List<TransactionEntity>, keep: (TransactionEntity) -> Boolean): BigDecimal =
        linked.filter { !it.isDeleted && keep(it) }
            .fold(BigDecimal.ZERO) { sum, t -> sum + (t.loanContribution ?: t.amount) }
}
