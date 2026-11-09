package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.forTotals
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * "This month vs last month" for the chat's compareSpending tool (M4), worked out by code: the
 * model only picks the period. Per currency, never converted, so a rate change can't move it.
 */
object SpendingComparison {
    enum class Period { MONTH, WEEK }

    data class Window(val start: LocalDateTime, val end: LocalDateTime)

    /** One currency's spend in both windows. [changePercent] is null when there was nothing before. */
    data class Row(val currency: String, val current: BigDecimal, val previous: BigDecimal, val changePercent: Int?)

    sealed interface Result {
        val current: Window
        val previous: Window
        data class Rows(override val current: Window, override val previous: Window, val rows: List<Row>) : Result
        /** No transactions at all in [missing]: the app can't tell "spent nothing" from "wasn't tracking". */
        data class NoData(override val current: Window, override val previous: Window, val missing: Window) : Result
    }

    /**
     * The period so far, and the previous one up to the same day and time. Once today's day is past
     * the end of last month (31 Mar vs Feb), the previous window is that whole month.
     */
    fun windows(period: Period, now: LocalDateTime, firstDayOfWeek: DayOfWeek): Pair<Window, Window> = when (period) {
        Period.MONTH -> {
            val prev = YearMonth.from(now).minusMonths(1)
            val prevEnd = if (now.dayOfMonth > prev.lengthOfMonth()) prev.atEndOfMonth().atTime(LocalTime.MAX) else now.minusMonths(1)
            Window(now.toLocalDate().withDayOfMonth(1).atStartOfDay(), now) to Window(prev.atDay(1).atStartOfDay(), prevEnd)
        }
        Period.WEEK -> {
            val start = now.toLocalDate().with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).atStartOfDay()
            Window(start, now) to Window(start.minusWeeks(1), now.minusWeeks(1))
        }
    }

    /**
     * [current] and [previous] are every non-deleted transaction in each window, unfiltered, so an
     * empty window is told apart from one with no spending. [category] null means all spending.
     */
    fun compare(
        windows: Pair<Window, Window>,
        current: List<TransactionWithSplits>,
        previous: List<TransactionWithSplits>,
        category: String? = null
    ): Result {
        val (cw, pw) = windows
        if (previous.isEmpty()) return Result.NoData(cw, pw, pw)
        if (current.isEmpty()) return Result.NoData(cw, pw, cw)
        val now = spend(current, category)
        val before = spend(previous, category)
        val rows = (now.keys + before.keys).sorted().map { currency ->
            val c = now[currency] ?: BigDecimal.ZERO
            val p = before[currency] ?: BigDecimal.ZERO
            Row(currency, c, p, change(c, p))
        }
        return Result.Rows(cw, pw, rows)
    }

    /** Same rules as the month summary (what counts toward totals, credit as spending); a category gets its split share. */
    private fun spend(rows: List<TransactionWithSplits>, category: String?): Map<String, BigDecimal> =
        rows.forTotals()
            .filter { it.transaction.transactionType == TransactionType.EXPENSE || it.transaction.transactionType == TransactionType.CREDIT }
            .mapNotNull { row ->
                val amount = if (category == null) row.transaction.amount
                    else row.getAmountByCategory().entries.filter { it.key.equals(category, ignoreCase = true) }
                        .takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { acc, e -> acc + e.value } ?: return@mapNotNull null
                row.transaction.currency to amount
            }
            .sumByCurrency({ it.first }, { it.second })
            .mapValues { it.value.amount }

    internal fun change(current: BigDecimal, previous: BigDecimal): Int? =
        if (previous.signum() <= 0) null
        else (current - previous).multiply(BigDecimal(100)).divide(previous, 0, RoundingMode.HALF_UP).toInt()

    // The user's own words beat the model's pick; both words at once is a comparison we can't make.
    private val WEEK = Regex("\\bweeks?\\b|أسبوع|اسبوع|أسابيع|هفته|हफ्त|हफ़्त|सप्ताह|\\bhaft[ea]\\b", RegexOption.IGNORE_CASE)
    private val MONTH = Regex("\\bmonths?\\b|شهر|أشهر|اشهر|महीन|माह|\\bmahin[ae]\\b", RegexOption.IGNORE_CASE)

    // Named months and years: answering "September vs August" with this month's figures would be wrong
    // under a correct-looking header. "May" is left out, it's mostly the verb.
    // ponytail: refuses "November vs October" too, even when that is this month and last; the user rephrases.
    private val UNSUPPORTED = Regex(
        "\\b(?:january|february|march|april|june|july|august|september|october|november|december|years?|20\\d\\d)\\b" +
            "|يناير|فبراير|مارس|أبريل|ابريل|مايو|يونيو|يوليو|أغسطس|اغسطس|سبتمبر|أكتوبر|اكتوبر|نوفمبر|ديسمبر" +
            "|كانون|شباط|آذار|نيسان|أيار|حزيران|تموز|أيلول|تشرين|سنة|السنة" +
            "|जनवरी|फ़रवरी|फरवरी|मार्च|अप्रैल|जून|जुलाई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर|साल|वर्ष|\\bsaal\\b",
        RegexOption.IGNORE_CASE
    )

    /** The period to compare, or null when it isn't one we support. */
    fun period(userMessage: String, modelPick: Any?): Period? {
        val week = WEEK.containsMatchIn(userMessage)
        val month = MONTH.containsMatchIn(userMessage)
        return when {
            (week && month) || UNSUPPORTED.containsMatchIn(userMessage) -> null
            week -> Period.WEEK
            month -> Period.MONTH
            modelPick == null || modelPick.toString().isBlank() -> Period.MONTH
            else -> Period.entries.firstOrNull { it.name.equals(modelPick.toString().trim(), ignoreCase = true) }
        }
    }
}
