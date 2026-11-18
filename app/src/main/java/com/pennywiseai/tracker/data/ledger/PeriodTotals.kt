package com.pennywiseai.tracker.data.ledger

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.math.BigDecimal

/**
 * Income, spend and net per currency: the figures behind Home's balance card, and the
 * chat's answers about the same period. Currencies are never added together here;
 * Home's combinedBreakdown converts them into one when asked to.
 */
fun breakdownByCurrency(
    transactions: List<TransactionEntity>,
    countCreditAsExpense: Boolean = false
): Map<String, TransactionRepository.MonthlyBreakdown> {
    // "Exclude from analytics" transactions stay in history & account balances
    // but must not count toward the home card's income/spend figures — same as
    // the Analytics screen, budgets and AI summaries already do (#451). Loan
    // exclusion already happened upstream in loadHomeData(); forTotals() is
    // idempotent, so this stays correct if that upstream filter ever moves.
    return transactions
        .forTotals()
        .groupBy { it.currency }.mapValues { (_, txs) ->
            // A "Refund" (INCOME + DEDUCT_SPENT) is the reversal of a previous
            // expense, so it should shrink "Spent this month" and not appear as
            // new income. "Extra budget" (ADD_TO_LIMIT) is real money in — leave
            // it in the income total.
            val refundTotal = txs
                .filter {
                    it.transactionType == TransactionType.INCOME &&
                        it.budgetImpactType == BudgetImpactType.DEDUCT_SPENT
                }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
            val income = txs
                .filter {
                    it.transactionType == TransactionType.INCOME &&
                        it.budgetImpactType != BudgetImpactType.DEDUCT_SPENT
                }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
            val rawExpenses = txs
                .filter {
                    it.transactionType == TransactionType.EXPENSE ||
                        // Fold credit-card spend into expenses when the user opted in (#705).
                        (countCreditAsExpense && it.transactionType == TransactionType.CREDIT)
                }
                .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
            val expenses = (rawExpenses - refundTotal).coerceAtLeast(BigDecimal.ZERO)
            TransactionRepository.MonthlyBreakdown(
                total = income - expenses,
                income = income,
                expenses = expenses
            )
        }
}

/** Several currencies' amounts as one figure in [currency]; [leftOut] had no rate and aren't in [total]. */
data class ConvertedEstimate(
    val currency: String,
    val total: BigDecimal,
    val leftOut: Map<String, BigDecimal>,
)

/**
 * [byCurrency] converted into [target] and added: an estimate, for a reader who wants one
 * figure. [convert] returns null for a pair that has never had a rate; that amount is
 * listed in [ConvertedEstimate.leftOut] instead of being counted at face value (#670).
 */
suspend fun convertedEstimate(
    byCurrency: Map<String, BigDecimal>,
    target: String,
    convert: suspend (amount: BigDecimal, from: String, to: String) -> BigDecimal?
): ConvertedEstimate {
    var total = BigDecimal.ZERO
    val leftOut = linkedMapOf<String, BigDecimal>()
    for ((currency, amount) in byCurrency) {
        val converted = if (currency.equals(target, ignoreCase = true)) amount else convert(amount, currency, target)
        if (converted == null) leftOut[currency] = amount else total += converted
    }
    return ConvertedEstimate(target, total, leftOut)
}

/**
 * Home's "Spent" as one figure in [target]: [spent] and [forgiven] loans each converted, then
 * added. A conversion rounds to [target]'s minor unit, so adding them first and converting
 * once can land a cent away from Home's card; Home and the chat both total it here.
 */
suspend fun convertedSpent(
    spent: Map<String, BigDecimal>,
    forgiven: Map<String, BigDecimal>,
    target: String,
    convert: suspend (amount: BigDecimal, from: String, to: String) -> BigDecimal?
): ConvertedEstimate {
    val a = convertedEstimate(spent, target, convert)
    val b = convertedEstimate(forgiven, target, convert)
    val leftOut = (a.leftOut.keys + b.leftOut.keys).associateWith {
        (a.leftOut[it] ?: BigDecimal.ZERO) + (b.leftOut[it] ?: BigDecimal.ZERO)
    }
    return ConvertedEstimate(target, a.total + b.total, leftOut)
}
