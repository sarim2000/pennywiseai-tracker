package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.ledger.countingTransferPairsOnce
import com.pennywiseai.tracker.data.ledger.forTotals
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Converts an amount between currencies, with the contract of
 * `CurrencyConversionService.convertAmountOrNull`: the amount comes back unchanged
 * when the currencies match (ignoring case), and null means the pair has never had
 * a rate, so the caller leaves the amount out instead of counting it at face value (#670).
 */
internal typealias ConvertAmount = suspend (amount: BigDecimal, from: String, to: String) -> BigDecimal?

/**
 * Income, spend and net per currency for Home's balance card. Currencies are never
 * added together here; [combinedBreakdown] converts them into one when asked to.
 */
internal fun breakdownByCurrency(
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

/** One breakdown in [targetCurrency]: each currency's figures converted, then added. */
internal suspend fun combinedBreakdown(
    breakdownMap: Map<String, TransactionRepository.MonthlyBreakdown>,
    targetCurrency: String,
    convert: ConvertAmount
): TransactionRepository.MonthlyBreakdown {
    var totalTotal = BigDecimal.ZERO
    var totalIncome = BigDecimal.ZERO
    var totalExpenses = BigDecimal.ZERO

    for ((currency, breakdown) in breakdownMap) {
        if (currency == targetCurrency) {
            totalTotal += breakdown.total
            totalIncome += breakdown.income
            totalExpenses += breakdown.expenses
        } else {
            // One rate serves all three figures; if the pair has never had a
            // rate, skip this currency's breakdown entirely (#670).
            val rateTotal = convert(breakdown.total, currency, targetCurrency) ?: continue
            totalTotal += rateTotal
            totalIncome += convert(breakdown.income, currency, targetCurrency) ?: BigDecimal.ZERO
            totalExpenses += convert(breakdown.expenses, currency, targetCurrency) ?: BigDecimal.ZERO
        }
    }

    return TransactionRepository.MonthlyBreakdown(
        total = totalTotal,
        income = totalIncome,
        expenses = totalExpenses
    )
}

/** Per-currency amounts as one figure in [selectedCurrency]; a never-rated currency is left out. */
internal suspend fun combinedTotal(
    byCurrency: Map<String, BigDecimal>,
    selectedCurrency: String,
    convert: ConvertAmount
): BigDecimal {
    if (byCurrency.isEmpty()) return BigDecimal.ZERO
    var total = BigDecimal.ZERO
    for ((currency, amount) in byCurrency) {
        total += if (currency == selectedCurrency) amount
        else convert(amount, currency, selectedCurrency)
            ?: continue // never-rated pair — skip, don't face-value (#670)
    }
    return total
}

/** The "Money in motion" figures: money that moved but was neither income nor spend. */
internal data class MotionTotals(
    val credit: BigDecimal,
    val transfer: BigDecimal,
    val investment: BigDecimal,
)

/**
 * Adds up [rows] into [MotionTotals], a transfer pair counted once. [amountOf] gives each
 * row's amount in the figure's currency, or null to leave the row out (no rate). It's
 * asked for every row before its type is checked. Inline so a caller can convert in it.
 */
internal inline fun moneyInMotion(
    rows: Iterable<TransactionEntity>,
    amountOf: (TransactionEntity) -> BigDecimal?
): MotionTotals {
    var credit = BigDecimal.ZERO
    var transfer = BigDecimal.ZERO
    var investment = BigDecimal.ZERO
    for (tx in rows.countingTransferPairsOnce { it }) {
        val amount = amountOf(tx) ?: continue
        when (tx.transactionType) {
            TransactionType.CREDIT -> credit += amount
            TransactionType.TRANSFER -> transfer += amount
            TransactionType.INVESTMENT -> investment += amount
            else -> { /* skip */ }
        }
    }
    return MotionTotals(credit, transfer, investment)
}

/**
 * The last day of the [previous] cycle to compare with: as many days into it as [today]
 * is into the cycle that started on [cycleStart], and never past its end.
 */
internal fun samePointCutoff(
    previous: Pair<LocalDate, LocalDate>,
    cycleStart: LocalDate,
    today: LocalDate
): LocalDate {
    val elapsed = ChronoUnit.DAYS.between(cycleStart, today).coerceAtLeast(0)
    return minOf(previous.first.plusDays(elapsed), previous.second)
}

/**
 * In unified mode, each account relabelled to [targetCurrency] at [rateOf]'s rate.
 * An account with no rate keeps its own currency; native mode returns [entities] as is.
 */
internal suspend fun convertAccounts(
    entities: List<AccountBalanceEntity>,
    targetCurrency: String,
    isUnifiedMode: Boolean,
    rateOf: suspend (from: String, to: String) -> BigDecimal?
): List<AccountBalanceEntity> {
    if (!isUnifiedMode) return entities
    return entities.map { account ->
        if (account.currency == targetCurrency) {
            account
        } else {
            // Fetch-on-miss: getExchangeRate lazily fetches the rate when it isn't
            // cached yet, so the very first render converts instead of showing the
            // raw native amount mislabelled with the display currency (the #545/MZN
            // bug: a $400 account drawn as "MZN400"). Only relabel to the target
            // currency when a rate actually exists — otherwise keep the account's
            // native currency so the row shows an honest "$400", not "MZN400".
            val rate = rateOf(account.currency, targetCurrency)
            if (rate != null) {
                // Keep full precision here and let display formatting round. Rounding
                // balance and creditLimit separately would make "available = limit -
                // balance" round twice and drift a cent on foreign credit cards.
                account.copy(
                    balance = account.balance.multiply(rate),
                    creditLimit = account.creditLimit?.multiply(rate),
                    currency = targetCurrency
                )
            } else {
                account
            }
        }
    }
}
