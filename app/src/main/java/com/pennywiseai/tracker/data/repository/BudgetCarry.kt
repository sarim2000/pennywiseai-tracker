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
    val spend = budgetSpend(group, previous, parentOf, convert)
    val spent = spend.takeUnless { it.incomplete }?.amount
    // Last period's limit as its card showed it: a budget made of category limits
    // (no overall limit) adds its extra-budget boosts to them, so they count here too.
    val limit = if (group.budget.limitAmount.signum() > 0) base else base + spend.boost
    return BudgetCarry(
        BudgetRollover.carry(Money(limit, currency), spent?.let { Money(it, currency) }),
        CarryGap.NO_RATE.takeIf { spent == null }
    )
}

/**
 * What a budget spent, in [convert]'s currency. [incomplete]: some of it had no
 * rate, so [amount] leaves it out. [boost]: extra budget (ADD_TO_LIMIT income)
 * that raised its category limits.
 */
data class BudgetSpend(val amount: BigDecimal, val incomplete: Boolean = false, val boost: BigDecimal = BigDecimal.ZERO)

/**
 * A budget's spending as its carry adds it up: every expense for a budget with no
 * categories, else its own categories (a parent covering its sub-categories, #374)
 * and type buckets, refunds taken off. A budget with rollover on shows the same sum
 * live (Budgets card, Home, Budget History), so what it shows spent is what it
 * carries; rollover-off budgets keep each screen's own sum. Spending [convert] has no
 * rate for is left out and marked [BudgetSpend.incomplete]. Outside unified mode,
 * pass only the budget's own currency's [transactions] and a [convert] that keeps
 * amounts as they are.
 */
internal suspend fun budgetSpend(
    group: BudgetWithCategories,
    transactions: List<TransactionWithSplits>,
    parentOf: Map<String, String>,
    convert: suspend (fromCurrency: String, amount: BigDecimal) -> BigDecimal?
): BudgetSpend {
    val counted = transactions.forTotals()
    if (group.categories.isEmpty()) {
        // Credited to parents so a refund filed under a parent comes off its
        // sub-categories' spending; only top-level categories are summed, so each
        // row counts once.
        val all = aggregateBudgetCategorySpending(
            counted, convertSplit = convert, convertIncome = { convert(it.currency, it.amount) }, parentOf = parentOf
        )
        val topLevel = all.categoryAmounts.filterKeys { it !in parentOf }.values
        return BudgetSpend(
            (topLevel + all.typeAmounts.values).fold(BigDecimal.ZERO, BigDecimal::add),
            all.skippedCategories.isNotEmpty() || all.skippedTypes.isNotEmpty()
        )
    }
    val agg = aggregateBudgetCategorySpending(
        counted, convertSplit = convert, convertIncome = { convert(it.currency, it.amount) }, parentOf = parentOf
    )
    val names = group.categories.map { it.categoryName }.toSet()
    // A child listed with its parent is already inside the parent's total.
    val rows = group.categories.filter { it.matchType != null || parentOf[it.categoryName] !in names }
    val categories = rows.filter { it.matchType == null }.map { it.categoryName }.toSet()
    val types = rows.mapNotNull { it.matchType }.toSet()
    return BudgetSpend(
        categories.fold(BigDecimal.ZERO) { sum, name -> sum + (agg.categoryAmounts[name] ?: BigDecimal.ZERO) } +
            types.fold(BigDecimal.ZERO) { sum, type -> sum + (agg.typeAmounts[type] ?: BigDecimal.ZERO) },
        categories.any { it in agg.skippedCategories } || types.any { it in agg.skippedTypes },
        // Every category row's boost, as the card's per-category limits add them.
        group.categories.filter { it.matchType == null }
            .fold(BigDecimal.ZERO) { sum, c -> sum + (agg.categoryLimitBoosts[c.categoryName] ?: BigDecimal.ZERO) }
    )
}
