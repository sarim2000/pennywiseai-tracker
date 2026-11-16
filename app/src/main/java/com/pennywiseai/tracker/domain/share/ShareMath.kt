package com.pennywiseai.tracker.domain.share

import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.domain.review.CategoryLine
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.PeriodReview
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// The figures behind the share cards. Everything here works on one currency at a time:
// a card is about one currency, and amounts in two never meet.

/**
 * (income − spent) / income for one currency's period, in whole percent. Negative when
 * more went out than came in. Null with no income, where a rate means nothing.
 *
 * Deliberately separate from the Budgets screen's savings rate, which counts only
 * spending against limits: a share card says what share of the period's income was kept.
 */
fun savingsRatePercent(income: BigDecimal, spent: BigDecimal): Int? {
    if (income.signum() <= 0) return null
    return (income - spent).multiply(BigDecimal(100))
        .divide(income, 0, RoundingMode.HALF_UP)
        .toInt()
}

fun CurrencyReview.savingsRatePercent(): Int? = savingsRatePercent(income, spent)

/** One period's savings rate, for the trend line. */
data class SavingsPoint(val start: LocalDate, val end: LocalDate, val ratePercent: Int)

/** [reviews]' savings rates in [currency], oldest first. Periods with no income are left out. */
fun savingsTrend(reviews: List<PeriodReview>, currency: String): List<SavingsPoint> =
    reviews.sortedBy { it.range.start }.mapNotNull { review ->
        review.currencies.firstOrNull { it.currency == currency }?.savingsRatePercent()
            ?.let { SavingsPoint(review.range.start, review.range.end, it) }
    }

/**
 * The categories that moved most since the previous period, biggest move first. Empty
 * when the period has no fair comparison.
 */
fun spendingDrivers(review: CurrencyReview, limit: Int = 4): List<CategoryLine> {
    if (review.previousSpent == null) return emptyList()
    return review.changes
        .filter { line -> line.previous != null && (line.amount - line.previous).signum() != 0 }
        .sortedByDescending { (it.amount - (it.previous ?: BigDecimal.ZERO)).abs() }
        .take(limit)
}

/** Whole-percent change of one category; null when it had nothing before (it's new). */
fun categoryChangePercent(line: CategoryLine): Int? {
    val previous = line.previous ?: return null
    if (previous.signum() <= 0) return null
    return (line.amount - previous).multiply(BigDecimal(100))
        .divide(previous, 0, RoundingMode.HALF_UP)
        .toInt()
}

/** A category and its share of the period's spending. */
data class CategoryShare(val name: String, val amount: BigDecimal, val sharePercent: Int)

/** The biggest categories by amount, each with its whole-percent share of [CurrencyReview.spent]. */
fun topCategoryShares(review: CurrencyReview, limit: Int = 4): List<CategoryShare> {
    val total = review.spent
    return review.topCategories.take(limit).map { line ->
        val share = if (total.signum() <= 0) 0
        else line.amount.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
        CategoryShare(line.name, line.amount, share.coerceIn(0, 100))
    }
}

/**
 * How a month's spending limits went, in [currency] — the Budgets screen's own figures,
 * so the card can't disagree with it. [kept] counts limits that finished at or under
 * their amount.
 */
data class BudgetResult(
    val currency: String,
    val spent: BigDecimal,
    val limit: BigDecimal,
    val usedPercent: Int,
    val kept: Int,
    val total: Int,
) {
    val isOver: Boolean get() = spent > limit
    val difference: BigDecimal get() = (limit - spent).abs()
}

/** Null when the month has no spending limit to report against. */
fun budgetResult(summary: BudgetOverallSummary): BudgetResult? {
    val limits = summary.groups.filter {
        it.group.budget.groupType == BudgetGroupType.LIMIT && it.totalBudget.signum() > 0
    }
    if (limits.isEmpty()) return null
    val limit = limits.fold(BigDecimal.ZERO) { sum, it -> sum + it.totalBudget }
    val spent = limits.fold(BigDecimal.ZERO) { sum, it -> sum + it.totalActual }
    return BudgetResult(
        currency = summary.currency,
        spent = spent,
        limit = limit,
        usedPercent = spent.multiply(BigDecimal(100)).divide(limit, 0, RoundingMode.HALF_UP).toInt(),
        kept = limits.count { it.totalActual <= it.totalBudget },
        total = limits.size,
    )
}
