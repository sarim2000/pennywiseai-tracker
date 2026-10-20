package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The statement's closing balance against PennyWise's balance for the same
 * account on the statement's last day (#13). One account, one [currency] —
 * the figures are never compared across currencies.
 */
data class StatementBalanceCheck(
    val bankName: String,
    val accountLast4: String,
    val accountLabel: String,
    val currency: String,
    val asOf: LocalDate,
    val statementBalance: BigDecimal,
    /** Null when PennyWise has no balance for the account on or before [asOf]. */
    val appBalance: BigDecimal?,
    /** The account's balance now, which "set balance" corrects. */
    val currentBalance: BigDecimal?
) {
    /** Statement minus PennyWise: > 0 means the bank holds more than PennyWise thinks. */
    val difference: BigDecimal? get() = appBalance?.let { statementBalance - it }

    val matches: Boolean get() = difference?.signum() == 0

    /**
     * The balance to set now so the account agrees with the statement: the
     * current balance shifted by the difference found on [asOf]. For a
     * statement that ends today this is exactly the statement's figure; for an
     * older one it keeps whatever PennyWise recorded since. Null when PennyWise
     * had no balance by [asOf]: an old closing figure must not overwrite today's.
     */
    val correctedCurrentBalance: BigDecimal?
        get() {
            val diff = difference ?: return null
            return currentBalance?.plus(diff)
        }

    companion object {
        /**
         * The statement's closing row: the last row of an oldest-first statement,
         * the first of a newest-first one. Null when no row carries a balance.
         */
        fun closingRow(rows: List<ParsedTransaction>): ParsedTransaction? {
            val withBalance = rows.filter { it.balance != null }
            if (withBalance.isEmpty()) return null
            val first = withBalance.first()
            val last = withBalance.last()
            val newestFirst = if (first.timestamp != last.timestamp) {
                first.timestamp > last.timestamp
            } else if (withBalance.size >= 2) {
                // One day only: the running balance says which way the rows run.
                val second = withBalance[1]
                val a = first.balance!!
                val b = second.balance!!
                (b - a).compareTo(second.signedAmount()) != 0 && (a - b).compareTo(first.signedAmount()) == 0
            } else {
                false
            }
            return if (newestFirst) first else last
        }

        private fun ParsedTransaction.signedAmount(): BigDecimal =
            if (type == com.pennywiseai.parser.core.TransactionType.INCOME) amount else amount.negate()

        fun dateOf(row: ParsedTransaction): LocalDate =
            Instant.ofEpochMilli(row.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
