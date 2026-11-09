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
    data class Row(val currency: String, val current: BigDecimal, val previous: BigDecimal, val changePercent: BigDecimal?)

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

    /** What counts as spending, as in the month summary: credit too. */
    private fun spending(rows: List<TransactionWithSplits>) = rows.forTotals()
        .filter { it.transaction.transactionType == TransactionType.EXPENSE || it.transaction.transactionType == TransactionType.CREDIT }

    /** Same rules as the month summary (what counts toward totals, credit as spending); a category gets its split share. */
    fun spend(rows: List<TransactionWithSplits>, category: String? = null): Map<String, BigDecimal> =
        spending(rows)
            .mapNotNull { row ->
                val amount = if (category == null) row.transaction.amount
                    else row.getAmountByCategory().entries.filter { it.key.equals(category, ignoreCase = true) }
                        .takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { acc, e -> acc + e.value } ?: return@mapNotNull null
                row.transaction.currency to amount
            }
            .sumByCurrency({ it.first }, { it.second })
            .mapValues { it.value.amount }

    /** Each currency's biggest categories, largest first, split shares included. Never ranked across currencies. */
    fun topCategories(rows: List<TransactionWithSplits>, limit: Int = 5): Map<String, List<Pair<String, BigDecimal>>> =
        spending(rows)
            .flatMap { row -> row.getAmountByCategory().map { (category, amount) -> Triple(row.transaction.currency, category, amount) } }
            .groupBy { it.first }
            .toSortedMap()
            .mapValues { (_, items) ->
                items.groupBy({ it.second }, { it.third })
                    .map { (category, amounts) -> category to amounts.fold(BigDecimal.ZERO, BigDecimal::add) }
                    .sortedByDescending { it.second }
                    .take(limit)
            }

    /** Whole percent, kept as BigDecimal: a tiny previous total can't overflow into a flipped sign. */
    internal fun change(current: BigDecimal, previous: BigDecimal): BigDecimal? =
        if (previous.signum() <= 0) null
        else (current - previous).multiply(BigDecimal(100)).divide(previous, 0, RoundingMode.HALF_UP)

    /**
     * Words matched from the start of a word only ("साल" isn't in "मसालों"), and open at the end for
     * Arabic and Hindi endings. An Arabic word may carry up to two of و/ف/ب/ل/ك/ه ("وهالشهر") and ال, or the ل of a contracted "للشهر", in front. English words
     * close with \b themselves.
     */
    private fun words(vararg alternatives: String) = Regex(
        "(?<![\\p{L}\\p{M}])[وفبلكه]{0,2}(?:ال|ل)?(?:${alternatives.joinToString("|")})", RegexOption.IGNORE_CASE
    )

    // The user's own words beat the model's pick; both words at once is a comparison we can't make.
    private val WEEK = words("weeks?\\b", "أسبوع", "اسبوع", "أسابيع", "هفته", "हफ्त", "हफ़्त", "सप्ताह", "haft[ea]\\b")
    private val MONTH = words("months?\\b", "شهر", "أشهر", "اشهر", "महीन", "माह(?![\\p{L}\\p{M}])", "mahin[ae]\\b", "mahinon\\b")

    // Named months and years: answering "September vs August" with this month's figures would be wrong
    // under a correct-looking header.
    // ponytail: English "may" is left out, it's mostly the verb, so "compare May with now" gets this month.
    // Refuses "November vs October" too, even when that is this month and last; the user rephrases.
    private val UNSUPPORTED = words(
        "(?:january|february|march|april|june|july|august|september|october|november|december|years?)\\b",
        "يناير", "فبراير", "مارس", "أبريل", "ابريل", "مايو", "يونيو", "يوليو", "أغسطس", "اغسطس", "سبتمبر",
        "أكتوبر", "اكتوبر", "نوفمبر", "ديسمبر", "كانون", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز",
        "أيلول", "تشرين", "سنة", "سنوات", "العام", "أعوام",
        "जनवरी", "फ़रवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर",
        "दिसंबर", "साल", "वर्ष", "saal\\b"
    )
    // Two different years ("2025 vs 2024"), or one after a word that makes it a date ("in 2025").
    // A lone number is more likely an amount ("my 2000 rent").
    private val YEAR_NUMBER = Regex("(?<![\\d.,])(?:19|20)\\d\\d(?![\\d.,])")
    private val YEAR_IN_CONTEXT = Regex(
        "(?:\\b(?:in|of|from|since|during|vs\\.?|versus|than)|في|عام|سنة|सन|वर्ष)\\s+(?:19|20)\\d\\d(?![\\d.,])",
        RegexOption.IGNORE_CASE
    )

    // "Last month" / "pichle hafte": the previous period by name.
    private val PREVIOUS = Regex(
        "\\b(?:last|previous|past|prior)\\s+(?:week|month)\\b" +
            "|(?:الشهر|الأسبوع|الاسبوع)\\s+(?:الماضي|الفائت|السابق|اللي فات|الي فات)" +
            "|(?:पिछले|पिछला|pichh?le|pichh?la)\\s+(?:हफ्त|हफ़्त|सप्ताह|महीन|माह|haft|mahin|week|month)",
        RegexOption.IGNORE_CASE
    )

    /**
     * The user names last month or last week. The month-only lookups can't answer that, so a call to
     * one of them is answered as a comparison instead of with this month's figure.
     */
    fun namesThePreviousPeriod(userMessage: String) = PREVIOUS.containsMatchIn(userMessage)

    // A comparison is of spending: "how much income last month?" stays with the summary.
    private val INCOME = Regex(
        "\\b(?:income|received|receive|earned|earn|salary|kamai|aamdani)\\b|دخل|راتب|استلمت|قبضت|कमाई|आमदनी|आय|वेतन|तनख्वाह",
        RegexOption.IGNORE_CASE
    )

    fun asksAboutIncome(userMessage: String) = INCOME.containsMatchIn(userMessage)

    /** The period to compare, or null when it isn't one we support. */
    fun period(userMessage: String, modelPick: Any?): Period? {
        val week = WEEK.containsMatchIn(userMessage)
        val month = MONTH.containsMatchIn(userMessage)
        val years = YEAR_NUMBER.findAll(userMessage).map { it.value }.toSet().size
        return when {
            (week && month) || years > 1 || YEAR_IN_CONTEXT.containsMatchIn(userMessage) || UNSUPPORTED.containsMatchIn(userMessage) -> null
            week -> Period.WEEK
            month -> Period.MONTH
            modelPick == null || modelPick.toString().isBlank() -> Period.MONTH
            else -> Period.entries.firstOrNull { it.name.equals(modelPick.toString().trim(), ignoreCase = true) }
        }
    }
}
