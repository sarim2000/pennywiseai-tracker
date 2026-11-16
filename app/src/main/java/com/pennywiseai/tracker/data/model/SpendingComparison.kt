package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.ledger.breakdownByCurrency
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
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

    /** [profile] is the one both windows counted (null for all), for the answer to name. */
    sealed interface Result {
        val current: Window
        val previous: Window
        val profile: ProfileEntity?
        /**
         * [currentSpentIn] and [previousSpentIn] are the currencies each window has spending
         * transactions in: a forgiven loan alone has none to list.
         */
        data class Rows(
            override val current: Window,
            override val previous: Window,
            val rows: List<Row>,
            val currentSpentIn: Set<String> = rows.filter { it.current.signum() > 0 }.mapTo(mutableSetOf()) { it.currency },
            val previousSpentIn: Set<String> = rows.filter { it.previous.signum() > 0 }.mapTo(mutableSetOf()) { it.currency },
            /** What each window's category list adds up to beyond its figure (negative: short of it), as [refundedOutside] gives it. */
            val currentRefunded: Map<String, BigDecimal> = emptyMap(),
            val previousRefunded: Map<String, BigDecimal> = emptyMap(),
            /** Either window counted a card purchase: see [countsCardPurchases]. */
            val cardPurchases: Boolean = false,
            override val profile: ProfileEntity? = null,
        ) : Result
        /** No transactions at all in [missing]: the app can't tell "spent nothing" from "wasn't tracking". */
        data class NoData(
            override val current: Window,
            override val previous: Window,
            val missing: Window,
            override val profile: ProfileEntity? = null,
        ) : Result
    }

    /**
     * The period so far, and the previous one up to the same point. A month is the budget cycle
     * starting on [cycleStartDay], as on Home (the calendar month by default), and the previous
     * cycle runs as long as this one has so far. Once that is past its end (31 Mar vs Feb), the
     * previous window is the whole of it.
     */
    fun windows(period: Period, now: LocalDateTime, firstDayOfWeek: DayOfWeek, cycleStartDay: Int = BudgetCycle.DEFAULT_START_DAY): Pair<Window, Window> = when (period) {
        Period.MONTH -> {
            val cycle = BudgetCycle.currentCycle(now.toLocalDate(), cycleStartDay)
            val (prevStart, prevLast) = BudgetCycle.previousCycle(cycle, cycleStartDay)
            val start = cycle.first.atStartOfDay()
            val samePoint = prevStart.atStartOfDay().plus(Duration.between(start, now))
            val prevEnd = minOf(samePoint, prevLast.atTime(LocalTime.MAX))
            Window(start, now) to Window(prevStart.atStartOfDay(), prevEnd)
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
        categories: Set<String>? = null,
        currentLoss: Map<String, BigDecimal> = emptyMap(),
        previousLoss: Map<String, BigDecimal> = emptyMap(),
        creditAsExpense: Boolean = true
    ): Result {
        val (cw, pw) = windows
        // A loan forgiven in a window is activity there too, even with no transactions.
        if (previous.isEmpty() && previousLoss.values.none { it.signum() > 0 }) return Result.NoData(cw, pw, pw)
        if (current.isEmpty() && currentLoss.values.none { it.signum() > 0 }) return Result.NoData(cw, pw, cw)
        val spentNow = spend(current, categories, creditAsExpense)
        val spentBefore = spend(previous, categories, creditAsExpense)
        val now = spentNow.plusAmounts(currentLoss)
        val before = spentBefore.plusAmounts(previousLoss)
        val rows = (now.keys + before.keys).sorted().map { currency ->
            val c = now[currency] ?: BigDecimal.ZERO
            val p = before[currency] ?: BigDecimal.ZERO
            Row(currency, c, p, change(c, p))
        }
        val refundedNow = categories?.let { refundedOutside(current, it, creditAsExpense) }.orEmpty()
        val refundedBefore = categories?.let { refundedOutside(previous, it, creditAsExpense) }.orEmpty()
        val card = countsCardPurchases(current, categories, creditAsExpense) || countsCardPurchases(previous, categories, creditAsExpense)
        return Result.Rows(cw, pw, rows, spentNow.keys, spentBefore.keys, refundedNow, refundedBefore, card)
    }

    /**
     * Which way spending went: 1 when it rose in every currency, -1 when it fell in every one,
     * 0 when it held, went both ways, or there is nothing to compare. It reads the rounded
     * [Row.changePercent] the reply prints, so a change that rounds to 0% (shown as "no change")
     * keeps the mascot's eyes level instead of contradicting the text.
     */
    fun direction(result: Result): Int =
        (result as? Result.Rows)?.rows?.map { it.changePercent?.signum() ?: if (it.current.signum() > 0) 1 else 0 }
            ?.distinct()?.singleOrNull() ?: 0

    /** Adds [other] per currency (a forgiven loan's balance onto spending), never across currencies. */
    fun Map<String, BigDecimal>.plusAmounts(other: Map<String, BigDecimal>): Map<String, BigDecimal> =
        (keys + other.keys).associateWith { (this[it] ?: BigDecimal.ZERO) + (other[it] ?: BigDecimal.ZERO) }

    /** What counts as spending, as on Home: card purchases only when the user folds them in (#705). */
    private fun spending(rows: List<TransactionWithSplits>, creditAsExpense: Boolean) = rows.forTotals()
        .filter { it.transaction.transactionType == TransactionType.EXPENSE || (creditAsExpense && it.transaction.transactionType == TransactionType.CREDIT) }

    private fun Set<String>.includes(name: String) = any { it.equals(name, ignoreCase = true) }

    /**
     * Refunds (INCOME + DEDUCT_SPENT) as (currency, category, amount): they come off spending, as on
     * Home and in Analytics. A refund's category is the budget category it gives back to.
     */
    internal fun refunds(rows: List<TransactionWithSplits>) = refundRows(rows)
        .map { Triple(it.transaction.currency, (it.transaction.budgetCategory ?: "").ifEmpty { "Others" }, it.transaction.amount) }

    private fun refundRows(rows: List<TransactionWithSplits>) = rows.forTotals()
        .filter { it.transaction.transactionType == TransactionType.INCOME && it.transaction.budgetImpactType == BudgetImpactType.DEDUCT_SPENT }

    /**
     * Same rules as Home's "spent": what counts toward totals, refunds netted per currency and floored
     * at zero. [categories] (null = all) get their split shares and the refunds given back to them.
     */
    fun spend(rows: List<TransactionWithSplits>, categories: Set<String>? = null, creditAsExpense: Boolean = true): Map<String, BigDecimal> {
        if (categories == null) {
            // All spending is Home's own figure; a currency is listed only when something was spent in it.
            val spentIn = spending(rows, creditAsExpense).mapTo(mutableSetOf()) { it.transaction.currency }
            return breakdownByCurrency(rows.map { it.transaction }, creditAsExpense)
                .filterKeys { it in spentIn }
                .mapValues { it.value.expenses }
        }
        val refunded = refundedTo(rows, categories)
        return grossSpend(rows, categories, creditAsExpense).mapValues { (currency, gross) -> (gross - (refunded[currency] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO) }
    }

    /** Refunds given back to [categories], per currency: [spend] takes them off. */
    private fun refundedTo(rows: List<TransactionWithSplits>, categories: Set<String>): Map<String, BigDecimal> =
        refunds(rows).filter { categories.includes(it.second) }
            .groupBy({ it.first }, { it.third }).mapValues { (_, a) -> a.fold(BigDecimal.ZERO, BigDecimal::add) }

    /**
     * How much more the list of [categories]' transactions adds up to than [spend]'s figure, per
     * currency; negative where the list adds up to less. The figure takes off each refund given
     * back to them whole; the list nets only a refund row's own part in them (all of it, or its
     * splits there). A refund given back to them but filed under another category, or split
     * partly into one, leaves the list higher; one given back elsewhere but split partly into
     * them leaves it lower. Both floor at zero, so this is the gap between the two and no more.
     * Currencies with no gap are left out.
     */
    fun refundedOutside(rows: List<TransactionWithSplits>, categories: Set<String>, creditAsExpense: Boolean = true): Map<String, BigDecimal> {
        val byFigure = refundedTo(rows, categories)
        val byList = refundRows(rows)
            .map { row -> row.transaction.currency to row.getAmountByCategory().filterKeys { categories.includes(it) }.values.fold(BigDecimal.ZERO, BigDecimal::add) }
            .sumByCurrency({ it.first }, { it.second })
            .mapValues { it.value.amount }
        return grossSpend(rows, categories, creditAsExpense)
            .mapValues { (c, gross) ->
                (gross - (byList[c] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO) - (gross - (byFigure[c] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO)
            }
            .filterValues { it.signum() != 0 }
    }

    /**
     * Whether [spend] counted a card purchase for [categories] (null = all): only when the user
     * folds them into spending. The Transactions list totals them under Credit, not Expenses.
     */
    fun countsCardPurchases(rows: List<TransactionWithSplits>, categories: Set<String>? = null, creditAsExpense: Boolean = true): Boolean =
        creditAsExpense && spending(rows, true).any { row ->
            row.transaction.transactionType == TransactionType.CREDIT &&
                (categories == null || row.getAmountByCategory().keys.any { categories.includes(it) })
        }

    /** How many spending transactions [spend] counted for [categories]: a split row once. */
    fun transactionsIn(rows: List<TransactionWithSplits>, categories: Set<String>, creditAsExpense: Boolean = true): Int =
        spending(rows, creditAsExpense).count { row -> row.getAmountByCategory().keys.any { categories.includes(it) } }

    private fun grossSpend(rows: List<TransactionWithSplits>, categories: Set<String>, creditAsExpense: Boolean): Map<String, BigDecimal> =
        spending(rows, creditAsExpense)
            .mapNotNull { row ->
                val amount = row.getAmountByCategory().entries.filter { categories.includes(it.key) }
                    .takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO) { acc, e -> acc + e.value } ?: return@mapNotNull null
                row.transaction.currency to amount
            }
            .sumByCurrency({ it.first }, { it.second })
            .mapValues { it.value.amount }

    /**
     * Each currency's biggest categories, largest first: split shares in, refunds given back to a
     * category out (floored at zero, and a category refunded in full drops off). Never ranked across currencies.
     */
    fun topCategories(rows: List<TransactionWithSplits>, limit: Int = 5, creditAsExpense: Boolean = true): Map<String, List<Pair<String, BigDecimal>>> =
        ranking(rows, null, null, limit, creditAsExpense)

    /**
     * [topCategories] by any [key] of a transaction (its merchant, its account), or by category
     * when [key] is null. With [categories], only their split shares count, and only refunds
     * given back to them come off. A refund comes off the group its own row falls in.
     */
    fun ranking(
        rows: List<TransactionWithSplits>,
        key: ((TransactionEntity) -> String)?,
        categories: Set<String>? = null,
        limit: Int = 5,
        creditAsExpense: Boolean = true,
    ): Map<String, List<Pair<String, BigDecimal>>> {
        val refunded = rows.forTotals()
            .filter { it.transaction.transactionType == TransactionType.INCOME && it.transaction.budgetImpactType == BudgetImpactType.DEDUCT_SPENT }
            .mapNotNull { row ->
                val category = (row.transaction.budgetCategory ?: "").ifEmpty { "Others" }
                if (categories != null && !categories.includes(category)) return@mapNotNull null
                (row.transaction.currency to (key?.invoke(row.transaction) ?: category)) to row.transaction.amount
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, a) -> a.fold(BigDecimal.ZERO, BigDecimal::add) }
        return spending(rows, creditAsExpense)
            .flatMap { row ->
                val parts = row.getAmountByCategory().filterKeys { categories == null || categories.includes(it) }
                if (key == null) parts.map { (category, amount) -> Triple(row.transaction.currency, category, amount) }
                else listOfNotNull(parts.takeIf { it.isNotEmpty() }?.let { Triple(row.transaction.currency, key(row.transaction), it.values.fold(BigDecimal.ZERO, BigDecimal::add)) })
            }
            .groupBy { it.first }
            .mapValues { (currency, items) ->
                items.groupBy({ it.second }, { it.third })
                    .map { (category, amounts) ->
                        category to (amounts.fold(BigDecimal.ZERO, BigDecimal::add) - (refunded[currency to category] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO)
                    }
                    .filter { it.second.signum() > 0 }
                    .sortedByDescending { it.second }
                    .take(limit)
            }
            .filterValues { it.isNotEmpty() }
            .toSortedMap()
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
    // A span or an offset: "the last 3 months", "two weeks ago", "the month before last". Only this
    // period and the one before it are compared; answering these with that would show the wrong dates.
    // Plurals and duals alone mean more than one period, except "both" of the two compared. A count
    // of 1 is the supported period. ponytail: Arabic "أشهر" is also "most famous", and "a two-month
    // rent payment" reads as a span, so both are refused; a refusal is rephrased, wrong dates aren't.
    private val SPAN = Regex(
        "(?<!\\bboth )\\b(?:weeks|months)\\b|\\bfortnights?\\b|\\bbefore\\s+(?:the\\s+)?last\\b" +
            "|(?<![\\p{L}\\d])(?:[2-9]|\\d{2,}|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)[\\s-]+(?:weeks?|months?)\\b" +
            "|(?<![\\p{L}\\p{M}])[وفبلكه]{0,2}(?:ال)?(?:شهرين|شهران|أسبوعين|اسبوعين|أسبوعان|اسبوعان|أشهر|اشهر|شهور|أسابيع|اسابيع)" +
            "|قبل\\s+(?:الماضي|الفائت|السابق|اللي فات|الي فات)" +
            "|(?<![\\p{L}\\d])(?:[2-9]|\\d{2,}|do|teen|char|paanch|panch|kuch|दो|तीन|चार|पांच|पाँच|कुछ)\\s+(?:हफ्त|हफ़्त|सप्ताह|महीन|माह|haft|mahin)" +
            "|महीनों|हफ्तों|हफ़्तों|\\bmahinon\\b",
        RegexOption.IGNORE_CASE
    )

    // Two different years ("2025 vs 2024"), or one after a word that makes it a date ("in 2025").
    // A lone number is more likely an amount ("my 2000 rent").
    private val YEAR_NUMBER = Regex("(?<![\\d.,])(?:19|20)\\d\\d(?![\\d.,])")
    private val YEAR_IN_CONTEXT = Regex(
        "(?:\\b(?:in|of|from|since|during|vs\\.?|versus|than|with)|في|عام|سنة|مع|सन|वर्ष)\\s+(?:19|20)\\d\\d(?![\\d.,])",
        RegexOption.IGNORE_CASE
    )
    // A written date names its own month: "2025-10", "10/2025", "15/10", "15.10.2025". A real month and
    // day only, and "." only in a full day.month.year, so "2000.50" stays an amount.
    private const val MM = "(?:0?[1-9]|1[0-2])"
    private val NUMERIC_DATE = Regex(
        "(?<![\\d.,])(?:(?:19|20)\\d\\d[-/]$MM|$MM[-/](?:19|20)\\d\\d|(?:0?[1-9]|[12]\\d|3[01])/$MM(?:/\\d{2,4})?|(?:0?[1-9]|[12]\\d|3[01])\\.$MM\\.(?:19|20)\\d\\d)(?![\\d]|[.,]\\d)"
    )

    // What a small model writes for "no category": the comparison of all spending.
    private val ALL_SPENDING = setOf("all", "any", "everything", "total", "overall", "general", "spending", "expenses", "expense", "none", "الكل", "كل", "الجميع", "sab")

    /** The model's category argument means all spending, not a category called that. */
    fun meansAllSpending(category: String) = category.trim().lowercase() in ALL_SPENDING

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

    /** The user's words name a week or a month, so those decide the days rather than a day word. */
    fun namesWeekOrMonth(userMessage: String) = WEEK.containsMatchIn(userMessage) || MONTH.containsMatchIn(userMessage)

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
            (week && month) || years > 1 || YEAR_IN_CONTEXT.containsMatchIn(userMessage) || UNSUPPORTED.containsMatchIn(userMessage) ||
                SPAN.containsMatchIn(userMessage) || NUMERIC_DATE.containsMatchIn(userMessage) -> null
            week -> Period.WEEK
            month -> Period.MONTH
            modelPick == null || modelPick.toString().isBlank() -> Period.MONTH
            else -> Period.entries.firstOrNull { it.name.equals(modelPick.toString().trim(), ignoreCase = true) }
        }
    }
}
