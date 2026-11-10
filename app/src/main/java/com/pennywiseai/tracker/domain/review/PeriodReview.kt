package com.pennywiseai.tracker.domain.review

import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.domain.spending.analyticsRowsFor
import com.pennywiseai.tracker.domain.spending.defaultTypeFilter
import com.pennywiseai.tracker.domain.spending.netOf
import com.pennywiseai.tracker.domain.spending.refundNetting
import com.pennywiseai.tracker.utils.forTotals
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The three reviews are one calculation over a different range. */
enum class ReviewKind {
    WEEK, MONTH, YEAR;

    companion object {
        fun fromName(value: String?): ReviewKind = entries.firstOrNull { it.name == value } ?: MONTH
    }
}

/** A period under review and the period it is compared with, both inclusive. */
data class ReviewRange(
    val kind: ReviewKind,
    val start: LocalDate,
    val end: LocalDate,
    val previousStart: LocalDate,
    val previousEnd: LocalDate,
    /** False for the year still running, whose comparison stops at the same date last year. */
    val isComplete: Boolean,
)

/** The newest period that can be reviewed: a week or cycle only once it has ended. */
internal fun latestReviewOffset(kind: ReviewKind): Int = if (kind == ReviewKind.YEAR) 0 else -1

/** In January the year worth opening is the one that just ended, not eleven days of the new one. */
internal fun defaultReviewOffset(kind: ReviewKind, today: LocalDate): Int =
    if (kind == ReviewKind.YEAR && today.monthValue == 1) -1 else latestReviewOffset(kind)

/** The [reviewRange] offset of the week holding [date], never newer than the last full week. */
internal fun weekOffsetOf(date: LocalDate, today: LocalDate, firstDayOfWeek: DayOfWeek): Int {
    val weekOf = TemporalAdjusters.previousOrSame(firstDayOfWeek)
    return ChronoUnit.WEEKS.between(today.with(weekOf), date.with(weekOf)).toInt()
        .coerceAtMost(latestReviewOffset(ReviewKind.WEEK))
}

/** [offset] counts back from the period containing [today], which is 0. */
internal fun reviewRange(
    kind: ReviewKind,
    offset: Int,
    today: LocalDate,
    cycleStartDay: Int,
    firstDayOfWeek: DayOfWeek,
): ReviewRange = when (kind) {
    ReviewKind.WEEK -> {
        val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).plusWeeks(offset.toLong())
        ReviewRange(kind, start, start.plusDays(6), start.minusWeeks(1), start.minusDays(1), isComplete = true)
    }
    ReviewKind.MONTH -> {
        var cycle = BudgetCycle.currentCycle(today, cycleStartDay)
        repeat(-offset) { cycle = BudgetCycle.previousCycle(cycle, cycleStartDay) }
        val previous = BudgetCycle.previousCycle(cycle, cycleStartDay)
        ReviewRange(kind, cycle.first, cycle.second, previous.first, previous.second, isComplete = true)
    }
    ReviewKind.YEAR -> {
        val year = today.year + offset
        if (offset == 0) {
            ReviewRange(
                kind, LocalDate.of(year, 1, 1), today,
                LocalDate.of(year - 1, 1, 1), today.minusYears(1), isComplete = false
            )
        } else {
            ReviewRange(
                kind, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31),
                LocalDate.of(year - 1, 1, 1), LocalDate.of(year - 1, 12, 31), isComplete = true
            )
        }
    }
}

data class CategoryLine(val name: String, val amount: BigDecimal, val previous: BigDecimal?)

data class MonthPoint(val month: YearMonth, val spent: BigDecimal)

/** One currency's review. Amounts in different currencies never meet. */
data class CurrencyReview(
    val currency: String,
    val spent: BigDecimal,
    /** Null when the previous period can't be compared fairly. */
    val previousSpent: BigDecimal?,
    val income: BigDecimal,
    /** Every row that counts toward any total: spend, income, refunds and transfers alike. */
    val transactionCount: Int,
    val categories: List<CategoryLine>,
    /** Year only. Months are net of refunds; a forgiven loan counts in [spent] alone. */
    val months: List<MonthPoint>,
) {
    /** False for a currency shown only because it fell to nothing since the previous period. */
    val hasActivity: Boolean get() = transactionCount > 0 || spent.signum() > 0
}

data class PeriodReview(
    val range: ReviewRange,
    val currencies: List<CurrencyReview>,
    /** The first and last transaction actually recorded in the range, for an honest coverage label. */
    val firstRecorded: LocalDate?,
    val lastRecorded: LocalDate?,
    /** Anything recorded before this period, so going further back can show something. */
    val hasOlder: Boolean,
)

/** Whole-percent change, or null when there is nothing to compare against. */
internal fun percentChange(current: BigDecimal, previous: BigDecimal?): Int? {
    if (previous == null || previous.signum() <= 0) return null
    return (current - previous).multiply(BigDecimal(100))
        .divide(previous, 0, RoundingMode.HALF_UP).toInt()
}

/**
 * The months "2026" is really made of. A year with data from March isn't a year, so
 * the share card says "MAR – DEC 2026" rather than imply twelve months it doesn't have.
 * Null when the whole calendar year is covered.
 */
internal fun partialYearSpan(firstRecorded: LocalDate?, lastRecorded: LocalDate?): Pair<YearMonth, YearMonth>? {
    if (firstRecorded == null || lastRecorded == null) return null
    if (firstRecorded.monthValue == 1 && lastRecorded.monthValue == 12) return null
    return YearMonth.from(firstRecorded) to YearMonth.from(lastRecorded)
}

/**
 * Whether [rows] reach back far enough to compare against. Someone who started
 * tracking in late March shouldn't be told they spent 60% more than the year before.
 */
// ponytail: judged from the first row in the previous period; a sparse month can lose its comparison. Weeks are exempt because a week with a single row is normal.
private fun coversPrevious(range: ReviewRange, rows: List<TransactionWithSplits>): Boolean {
    val earliest = rows.minOfOrNull { it.transaction.dateTime.toLocalDate() } ?: return false
    if (range.kind == ReviewKind.WEEK) return true
    val days = ChronoUnit.DAYS.between(range.previousStart, range.previousEnd) + 1
    return !earliest.isAfter(range.previousStart.plusDays(days / 4))
}

private class Spend(
    val total: BigDecimal,
    val byCategory: Map<String, BigDecimal>,
    val byMonth: Map<YearMonth, BigDecimal>,
)

/** Analytics' spend definition for one currency: refunds netted, loans and exclusions out. */
private suspend fun spendOf(
    rows: List<TransactionWithSplits>,
    filter: TransactionTypeFilter,
    parentOf: Map<String, String>,
): Spend {
    val counted = analyticsRowsFor(rows, filter)
    val refunds = refundNetting(rows, filter, null, parentOf) { it.amount }

    val categories = mutableMapOf<String, BigDecimal>()
    val months = mutableMapOf<YearMonth, BigDecimal>()
    var gross = BigDecimal.ZERO
    for (row in counted) {
        gross += row.transaction.amount
        months.merge(YearMonth.from(row.transaction.dateTime), row.transaction.amount, BigDecimal::add)
        row.getAmountByCategory().forEach { (category, amount) ->
            categories.merge(category.ifEmpty { "Others" }.let { parentOf[it] ?: it }, amount, BigDecimal::add)
        }
    }
    val monthRefunds = mutableMapOf<YearMonth, BigDecimal>()
    refunds.byDay.forEach { (day, amount) -> monthRefunds.merge(YearMonth.from(day), amount, BigDecimal::add) }
    return Spend(
        total = netOf(gross, refunds.total),
        byCategory = categories.mapValues { (name, amount) -> netOf(amount, refunds.byCategory[name]) },
        byMonth = months.mapValues { (month, amount) -> netOf(amount, monthRefunds[month]) },
    )
}

/**
 * Builds the review from rows already scoped to the profile and without ignored
 * accounts. [settlementLoss] is money forgiven on lent loans in each range, which
 * Home counts as spent (#938) and no transaction row carries.
 */
internal suspend fun buildPeriodReview(
    range: ReviewRange,
    rows: List<TransactionWithSplits>,
    previousRows: List<TransactionWithSplits>,
    creditAsExpense: Boolean,
    parentOf: Map<String, String>,
    settlementLoss: Map<String, BigDecimal> = emptyMap(),
    previousSettlementLoss: Map<String, BigDecimal> = emptyMap(),
): PeriodReview {
    val filter = defaultTypeFilter(creditAsExpense)
    val comparable = coversPrevious(range, previousRows)
    val current = rows.groupBy { it.transaction.currency }
    val previous = previousRows.groupBy { it.transaction.currency }

    // A currency used last period and not this one is still news: it fell to nothing.
    val compared = if (comparable) previous.keys + previousSettlementLoss.keys else emptySet()
    val currencies = (current.keys + settlementLoss.keys + compared).map { currency ->
        val spend = spendOf(current[currency].orEmpty(), filter, parentOf)
        val before = previous[currency]?.takeIf { comparable }?.let { spendOf(it, filter, parentOf) }
            ?: Spend(BigDecimal.ZERO, emptyMap(), emptyMap()).takeIf { comparable && previousSettlementLoss[currency] != null }
        val income = analyticsRowsFor(current[currency].orEmpty(), TransactionTypeFilter.INCOME)
            .fold(BigDecimal.ZERO) { sum, row -> sum + row.transaction.amount }
        CurrencyReview(
            currency = currency,
            spent = spend.total + (settlementLoss[currency] ?: BigDecimal.ZERO),
            previousSpent = before?.let { it.total + (previousSettlementLoss[currency] ?: BigDecimal.ZERO) },
            income = income,
            transactionCount = current[currency].orEmpty().forTotals().size,
            categories = categoryLines(range, spend, before),
            months = if (range.kind == ReviewKind.YEAR) monthPoints(range, spend) else emptyList(),
        )
    }
        .filter { it.hasActivity || (it.previousSpent?.signum() ?: 0) > 0 }
        // By activity, not by amount: 1,000 of one currency says nothing against 1,000 of another.
        .sortedWith(compareByDescending<CurrencyReview> { it.transactionCount }.thenBy { it.currency })

    val recorded = rows.map { it.transaction.dateTime.toLocalDate() }
    return PeriodReview(
        range = range,
        currencies = currencies,
        firstRecorded = recorded.minOrNull(),
        lastRecorded = recorded.maxOrNull(),
        hasOlder = previousRows.isNotEmpty(),
    )
}

/** A year reads as "where it went"; a week or cycle as "what changed", when that's known. */
private fun categoryLines(range: ReviewRange, spend: Spend, before: Spend?): List<CategoryLine> {
    val names = spend.byCategory.keys + before?.byCategory?.keys.orEmpty()
    val lines = names.map { name ->
        CategoryLine(
            name = name,
            amount = spend.byCategory[name] ?: BigDecimal.ZERO,
            previous = before?.let { it.byCategory[name] ?: BigDecimal.ZERO },
        )
    }.filter { it.amount.signum() > 0 || (it.previous?.signum() ?: 0) > 0 }

    val sorted = if (before == null || range.kind == ReviewKind.YEAR) {
        lines.sortedByDescending { it.amount }
    } else {
        lines.sortedByDescending { (it.amount - (it.previous ?: BigDecimal.ZERO)).abs() }
    }
    return sorted.take(CATEGORY_LINES)
}

private fun monthPoints(range: ReviewRange, spend: Spend): List<MonthPoint> =
    generateSequence(YearMonth.from(range.start)) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(YearMonth.from(range.end)) }
        .map { MonthPoint(it, spend.byMonth[it] ?: BigDecimal.ZERO) }
        .toList()

private const val CATEGORY_LINES = 5

/** Fewer rows than this and a period isn't worth a Home banner. */
internal const val MIN_ROWS_FOR_REVIEW_PROMPT = 20

/** The Home banner's offer: which review, and the key that marks it handled. */
data class ReviewPrompt(val kind: ReviewKind, val key: String, val start: LocalDate, val end: LocalDate)

/**
 * January's banner is the year that ended, until it's opened or dismissed; every other
 * time, the cycle that just finished. Keys: "2026" for a year, the month the cycle
 * ended in ("2026-10") for a cycle, which on calendar cycles is the value stored before.
 */
internal suspend fun reviewPromptFor(
    today: LocalDate,
    cycleStartDay: Int,
    handledYear: String?,
    handledMonth: String?,
    rowCount: suspend (LocalDate, LocalDate) -> Int,
): ReviewPrompt? {
    if (today.monthValue == 1) {
        val year = reviewRange(ReviewKind.YEAR, -1, today, cycleStartDay, DayOfWeek.MONDAY)
        val key = year.start.year.toString()
        if (handledYear != key && rowCount(year.start, year.end) >= MIN_ROWS_FOR_REVIEW_PROMPT) {
            return ReviewPrompt(ReviewKind.YEAR, key, year.start, year.end)
        }
    }
    val cycle = reviewRange(ReviewKind.MONTH, -1, today, cycleStartDay, DayOfWeek.MONDAY)
    val key = YearMonth.from(cycle.end).toString()
    if (handledMonth == key || rowCount(cycle.start, cycle.end) < MIN_ROWS_FOR_REVIEW_PROMPT) return null
    return ReviewPrompt(ReviewKind.MONTH, key, cycle.start, cycle.end)
}

/** Whether a cycle is exactly one calendar month, so a month's name is the right label for it. */
internal fun isCalendarMonth(start: LocalDate, end: LocalDate): Boolean =
    start.dayOfMonth == 1 && end == YearMonth.from(start).atEndOfMonth()
