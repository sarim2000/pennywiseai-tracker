package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.domain.spending.BudgetRollover
import com.pennywiseai.tracker.utils.Money
import java.math.BigDecimal

/** Why a budget carried nothing: some of last period's spending couldn't be counted. */
enum class CarryGap {
    /** Unified mode: it had no exchange rate. */
    NO_RATE
}

/**
 * What a budget carries into the period on screen, in that screen's currency.
 * [gap]: some of last period's spending couldn't be counted, so nothing was
 * carried rather than guess.
 */
data class BudgetCarry(val amount: Money, val gap: CarryGap? = null) {
    val incomplete: Boolean get() = gap != null

    /** [limit] (this budget's limit in [amount]'s currency) with the carry added. */
    fun appliedTo(limit: BigDecimal): BigDecimal =
        BudgetRollover.effectiveLimit(Money(limit, amount.currency), amount).amount

    companion object {
        fun none(currency: String) = BudgetCarry(Money.zero(currency))
    }
}

/**
 * Percent of [limit] (with [carry] already in it) that [spent] used. Rollover can
 * carry a budget's limit down to 0 (a negative [carry]): that limit is all used up.
 */
fun budgetPercentUsed(spent: BigDecimal, limit: BigDecimal, carry: BigDecimal): Float = when {
    limit.signum() > 0 -> (spent.toFloat() / limit.toFloat() * 100f).coerceAtLeast(0f)
    carry.signum() < 0 -> 100f
    else -> 0f
}

/**
 * The budget's own limit in its stored (base) currency: the group limit, or the sum
 * of its categories' when it has none. Never a limit something was already added to.
 */
fun BudgetWithCategories.baseLimit(): BigDecimal =
    budget.limitAmount.takeIf { it.signum() > 0 }
        ?: categories.fold(BigDecimal.ZERO) { sum, c -> sum + c.budgetAmount }

/**
 * The one place every budget figure gets its carry from. [previous] holds the
 * transactions of the period the budget carries from (null: it carries nothing; see
 * [BudgetRollover.sourceWindow]), in every currency. [convert] puts an amount into
 * [currency] and returns null without a rate; limits are stored in [baseCurrency].
 */
suspend fun budgetCarry(
    group: BudgetWithCategories,
    previous: List<TransactionWithSplits>?,
    currency: String,
    baseCurrency: String,
    parentOf: Map<String, String>,
    convert: suspend (fromCurrency: String, amount: BigDecimal) -> BigDecimal?
): BudgetCarry {
    if (previous == null) return BudgetCarry.none(currency)
    val base = convert(baseCurrency, group.baseLimit()) ?: return BudgetCarry(Money.zero(currency), CarryGap.NO_RATE)
    val spent = budgetSpendOrNull(group, previous, parentOf, convert)
    return BudgetCarry(
        BudgetRollover.carry(Money(base, currency), spent?.let { Money(it, currency) }),
        CarryGap.NO_RATE.takeIf { spent == null }
    )
}

/**
 * What [group] spent in [transactions], as its card adds it up: every expense for a
 * budget with no categories, else its own categories and type buckets, refunds
 * taken off. Null when some of it had no rate in [convert].
 */
internal suspend fun budgetSpendOrNull(
    group: BudgetWithCategories,
    transactions: List<TransactionWithSplits>,
    parentOf: Map<String, String>,
    convert: suspend (fromCurrency: String, amount: BigDecimal) -> BigDecimal?
): BigDecimal? {
    val counted = transactions.forTotals()
    if (group.categories.isEmpty()) {
        // Parents aren't credited here: every row is summed once.
        val all = aggregateBudgetCategorySpending(
            counted, convertSplit = convert, convertIncome = { convert(it.currency, it.amount) }
        )
        if (all.skippedCategories.isNotEmpty() || all.skippedTypes.isNotEmpty()) return null
        return (all.categoryAmounts.values + all.typeAmounts.values).fold(BigDecimal.ZERO, BigDecimal::add)
    }
    val agg = aggregateBudgetCategorySpending(
        counted, convertSplit = convert, convertIncome = { convert(it.currency, it.amount) }, parentOf = parentOf
    )
    val names = group.categories.map { it.categoryName }.toSet()
    // A child listed with its parent is already inside the parent's total.
    val rows = group.categories.filter { it.matchType != null || parentOf[it.categoryName] !in names }
    if (rows.any { if (it.matchType != null) it.matchType in agg.skippedTypes else it.categoryName in agg.skippedCategories }) {
        return null
    }
    return rows.fold(BigDecimal.ZERO) { sum, row ->
        val amount = if (row.matchType != null) agg.typeAmounts[row.matchType] else agg.categoryAmounts[row.categoryName]
        sum + (amount ?: BigDecimal.ZERO)
    }
}
