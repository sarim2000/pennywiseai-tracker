package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.utils.countingTransferPairsOnce
import com.pennywiseai.tracker.utils.forTotals
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The rows an Analytics type view totals: those of [filter]'s types (Spending =
 * expense + card spend, #898), cut to what counts toward totals — loan-linked and
 * analytics-excluded parts drop out (#451, #800); they stay in history and balance.
 *
 * The Income view also drops refunds (INCOME + DEDUCT_SPENT): they offset spending
 * and are netted out of the spend views instead, so counting them as income too
 * would double-count them. Mirrors the Home card (#629).
 */
internal fun analyticsRowsFor(
    rows: List<TransactionWithSplits>,
    filter: TransactionTypeFilter
): List<TransactionWithSplits> {
    val types = filter.transactionTypes
    return (if (types == null) rows else rows.filter { it.transaction.transactionType in types })
        .forTotals()
        .filter {
            !(filter == TransactionTypeFilter.INCOME &&
                it.transaction.budgetImpactType == BudgetImpactType.DEDUCT_SPENT)
        }
        .countingTransferPairsOnce { it.transaction }
}

/** [gross] less [refund], floored at zero: a spend bucket never shows negative spend. */
internal fun netOf(gross: BigDecimal, refund: BigDecimal?): BigDecimal =
    (gross - (refund ?: BigDecimal.ZERO)).coerceAtLeast(BigDecimal.ZERO)

/** Refund value to net off each spend surface, keyed like that surface's rows. */
internal class RefundNetting(
    val total: BigDecimal = BigDecimal.ZERO,
    val byCategory: Map<String, BigDecimal> = emptyMap(),
    /** Keyed "bankName_accountNumber". */
    val byAccount: Map<String, BigDecimal> = emptyMap(),
    val byMerchant: Map<String, BigDecimal> = emptyMap(),
    val byDay: Map<LocalDate, BigDecimal> = emptyMap()
)

/**
 * The type view Analytics opens on. With "count credit card as expense" on, Home's
 * "Spent" is Expense + Card, so open on Spending to show the same figure. Off, open
 * on Expense: those users count the card bill payment instead, and adding card
 * spends would count the same money twice.
 */
internal fun defaultTypeFilter(countCreditCardAsExpense: Boolean): TransactionTypeFilter =
    if (countCreditCardAsExpense) TransactionTypeFilter.SPENDING else TransactionTypeFilter.EXPENSE

/**
 * Refunds (INCOME + DEDUCT_SPENT) to net out of a spend view ([TransactionTypeFilter.netsRefunds]:
 * Expense and Spending), so every spend surface (total, category, account, merchant
 * and the trend) agrees with the Home card and the Transactions page, which are net
 * of refunds. Other views get nothing to net.
 *
 * [rows] is the untyped, scoped list: the type filter dropped these refunds, so they're
 * pulled back in here. A refund comes off its budgetCategory (rolled up via [parentOf];
 * skipped when outside [categoryFilter]), its account, its merchant (when it carries the
 * same merchant name) and its day. The FULL refund always comes off the headline total,
 * while each bucket floors at zero ([netOf]); so an over-refund or orphaned refund can
 * leave the total below the sum of the visible rows, exactly as on the Home card (#704).
 *
 * [amountOf] gives the refund in the view's currency, or null to skip it (unified mode,
 * never-rated pair: never face-value a foreign amount, #670).
 */
internal suspend fun refundNetting(
    rows: List<TransactionWithSplits>,
    filter: TransactionTypeFilter,
    categoryFilter: Set<String>?,
    parentOf: Map<String, String>,
    amountOf: suspend (TransactionEntity) -> BigDecimal?
): RefundNetting {
    if (!filter.netsRefunds) return RefundNetting()
    var total = BigDecimal.ZERO
    val byCategory = mutableMapOf<String, BigDecimal>()
    val byAccount = mutableMapOf<String, BigDecimal>()
    val byMerchant = mutableMapOf<String, BigDecimal>()
    val byDay = mutableMapOf<LocalDate, BigDecimal>()
    fun <K> MutableMap<K, BigDecimal>.add(key: K, amount: BigDecimal) { this[key] = (this[key] ?: BigDecimal.ZERO) + amount }
    for (tw in rows) {
        val tx = tw.transaction.forTotals() ?: continue
        if (tx.transactionType != TransactionType.INCOME || tx.budgetImpactType != BudgetImpactType.DEDUCT_SPENT) continue
        val rawCategory = (tx.budgetCategory ?: "").ifEmpty { "Others" }
        if (categoryFilter != null && rawCategory !in categoryFilter) continue
        val amount = amountOf(tx) ?: continue
        if (amount <= BigDecimal.ZERO) continue
        total += amount
        byCategory.add(parentOf[rawCategory] ?: rawCategory, amount)
        byAccount.add("${tx.bankName}_${tx.accountNumber}", amount)
        byMerchant.add(tx.merchantName, amount)
        byDay.add(tx.dateTime.toLocalDate(), amount)
    }
    return RefundNetting(total, byCategory, byAccount, byMerchant, byDay)
}
