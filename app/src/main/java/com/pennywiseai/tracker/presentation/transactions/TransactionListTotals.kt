package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.common.CurrencyGroupedTotals
import com.pennywiseai.tracker.presentation.common.CurrencyTotals
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.utils.countingTransferPairsOnce
import com.pennywiseai.tracker.utils.forTotals
import java.math.BigDecimal

internal fun calculateCurrencyGroupedTotals(transactions: List<TransactionEntity>): CurrencyGroupedTotals {
    // Loan disbursements/repayments and analytics-excluded transactions still
    // appear in the list (it's the full ledger) but must not count toward the
    // period's Income/Expense/etc. totals — loans are tracked in the Loans
    // feature, and excluded txns are opted out of every spend figure by the
    // user. Matches the Home/Analytics convention (HomeViewModel#1142).
    val nonLoanTransactions = transactions.forTotals()
    val transactionsByCurrency = nonLoanTransactions.groupBy { it.currency }

    val totalsByCurrency = transactionsByCurrency.mapValues { (currency, currencyTransactions) ->
        // A "Refund" (INCOME + DEDUCT_SPENT) is the reversal of a previous
        // expense, so it shrinks "Expenses" (floored at zero) and does not
        // appear in "Income". "Extra budget" (ADD_TO_LIMIT) is real money in
        // and stays in income — same treatment as HomeViewModel.
        val refundTotal = currencyTransactions
            .filter {
                it.transactionType == TransactionType.INCOME &&
                    it.budgetImpactType == BudgetImpactType.DEDUCT_SPENT
            }
            .sumOf { it.amount }

        val income = currencyTransactions
            .filter {
                it.transactionType == TransactionType.INCOME &&
                    it.budgetImpactType != BudgetImpactType.DEDUCT_SPENT
            }
            .sumOf { it.amount }

        val rawExpenses = currencyTransactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .sumOf { it.amount }
        val expenses = (rawExpenses - refundTotal).coerceAtLeast(BigDecimal.ZERO)

        val credit = currencyTransactions
            .filter { it.transactionType == TransactionType.CREDIT }
            .sumOf { it.amount }

        val transfer = currencyTransactions
            .filter { it.transactionType == TransactionType.TRANSFER }
            // ponytail: a pair across currencies counts in each currency, so it
            // shows twice in a unified total; dedupe before grouping if that matters.
            .countingTransferPairsOnce { it }
            .sumOf { it.amount }

        val investment = currencyTransactions
            .filter { it.transactionType == TransactionType.INVESTMENT }
            .sumOf { it.amount }

        CurrencyTotals(
            currency = currency,
            income = income,
            expenses = expenses,
            credit = credit,
            transfer = transfer,
            investment = investment,
            transactionCount = currencyTransactions.size
        )
    }

    // Note: availableCurrencies are now provided by the separate availableCurrencies StateFlow
    // We'll keep the old behavior for compatibility but the UI should use availableCurrencies property
    // Use standard currency sorting (INR first, then alphabetical)
    val filteredAvailableCurrencies = CurrencyUtils.sortCurrencies(
        totalsByCurrency.keys.toList()
    )

    return CurrencyGroupedTotals(
        totalsByCurrency = totalsByCurrency,
        availableCurrencies = filteredAvailableCurrencies,
        transactionCount = nonLoanTransactions.size
    )
}

/**
 * Collapses per-currency grouped totals into the figures for [currency]:
 * either that one currency's bucket, or — in unified mode — every bucket
 * converted into it. This is the ONLY way a single-figure total may be
 * produced from mixed currencies (hard constraint 2); shared by the list
 * totals and the selection totals so the two cards can never disagree on
 * the currency rules.
 *
 * [convertOrNull] returns null for a pair with no rate.
 */
internal suspend fun resolveFilteredTotals(
    groupedTotals: CurrencyGroupedTotals,
    currency: String,
    isUnified: Boolean,
    convertOrNull: suspend (amount: BigDecimal, from: String, to: String) -> BigDecimal?,
): FilteredTotals {
    // isNotEmpty, not size > 1: in unified mode even a single-currency set
    // needs converting when that one currency isn't the display currency —
    // a selection of only-USD rows under an INR display would otherwise
    // look up the empty INR bucket and show zeros.
    return if (isUnified && groupedTotals.totalsByCurrency.isNotEmpty()) {
        // Aggregate all currencies converted to display currency
        var income = BigDecimal.ZERO
        var expenses = BigDecimal.ZERO
        var credit = BigDecimal.ZERO
        var transfer = BigDecimal.ZERO
        var investment = BigDecimal.ZERO
        var count = 0
        for ((cur, totals) in groupedTotals.totalsByCurrency) {
            if (cur == currency) {
                income += totals.income
                expenses += totals.expenses
                credit += totals.credit
                transfer += totals.transfer
                investment += totals.investment
            } else {
                // Skip unconvertible currency pairs (add nothing) rather than
                // counting face value (#670). The transactions still appear in
                // the list, so `count` below stays complete.
                income += convertOrNull(totals.income, cur, currency) ?: BigDecimal.ZERO
                expenses += convertOrNull(totals.expenses, cur, currency) ?: BigDecimal.ZERO
                credit += convertOrNull(totals.credit, cur, currency) ?: BigDecimal.ZERO
                transfer += convertOrNull(totals.transfer, cur, currency) ?: BigDecimal.ZERO
                investment += convertOrNull(totals.investment, cur, currency) ?: BigDecimal.ZERO
            }
            count += totals.transactionCount
        }
        // Net = true cash flow (income − expenses). Credit-card spend,
        // transfers, and investments are *not* in the visible Expenses tile,
        // so subtracting them here produced a Net that didn't reconcile with
        // the user-visible math. Those channels live on the home Cash-Flow
        // card as deliberately separate categories.
        val netBalance = income - expenses
        FilteredTotals(income, expenses, credit, transfer, investment, netBalance, count)
    } else {
        val currencyTotals = groupedTotals.getTotalsForCurrency(currency)
        FilteredTotals(
            income = currencyTotals.income,
            expenses = currencyTotals.expenses,
            credit = currencyTotals.credit,
            transfer = currencyTotals.transfer,
            investment = currencyTotals.investment,
            netBalance = currencyTotals.netBalance,
            transactionCount = currencyTotals.transactionCount
        )
    }
}
