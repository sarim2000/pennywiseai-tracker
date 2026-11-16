package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.utils.toLatinDigits
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

/**
 * What a spending figure is narrowed to: [window] (null for Home's period, this budget cycle so
 * far), rows at [merchant] (its name or the user's alias for it) and on [account] (a
 * "bank_last4" key, as the transaction list's account filter keys it).
 */
data class SpendingFilter(
    val window: SpendingComparison.Window? = null,
    val merchant: String? = null,
    val account: String? = null,
) {
    /** Narrowed to some rows: Home's figure, forgiven loans included, no longer applies. */
    val narrowed: Boolean get() = merchant != null || account != null

    fun keeps(tx: TransactionEntity, aliases: Map<String, String>): Boolean =
        (account == null || accountKey(tx) == account) &&
            (merchant == null || tx.merchantName.contains(merchant, ignoreCase = true) || aliases[tx.merchantName]?.contains(merchant, ignoreCase = true) == true)

    companion object {
        /** The transaction list's account filter keys a row the same way (presentation/common/Filters.kt). */
        fun accountKey(tx: TransactionEntity) = "${tx.bankName}_${tx.accountNumber}"
        fun accountKey(account: AccountBalanceEntity) = IgnoredAccountsStore.keyFor(account.bankName, account.accountLast4)
    }
}

/**
 * The chat's query_spending arguments, checked: dates and choices the app understands, or the
 * reason it can't answer. A period it can't read is said, never answered with this month.
 */
object SpendingQuery {
    enum class GroupBy { NONE, CATEGORY, MERCHANT, ACCOUNT }

    enum class Problem {
        /** A period name the app doesn't know. */
        PERIOD,
        /** A date it can't read, e.g. 2025-02-29. */
        DATE,
        /** An end date without a start. */
        NO_START,
        /** The end comes before the start. */
        ORDER,
        /** It hasn't started yet. */
        FUTURE,
    }

    sealed interface Dates {
        /** Home's period: the budget cycle so far. */
        data object Home : Dates
        data class Of(val window: SpendingComparison.Window) : Dates
        data class Invalid(val problem: Problem, val value: String = "", val other: String = "") : Dates
    }

    private val YEAR_MONTH = Regex("(?:19|20)\\d\\d-\\d\\d")
    private val YEAR = Regex("(?:19|20)\\d\\d")
    private val HOME = setOf("this_month", "month", "current_month", "this_cycle", "cycle")

    // A count of days: "the last 10 days", "آخر ٣ أيام", "पिछले 10 दिन".
    private val DAYS = Regex("\\bdays\\b|أيام|ايام|يومين|يومان|दिन", RegexOption.IGNORE_CASE)

    // "till today", "aaj tak", "right now": the days so far, not today alone.
    private val SO_FAR = Regex(
        "\\b(?:right now|just now|(?:till|until|untill|up to|upto|as of|through) today|aaj tak)\\b|आज तक" +
            "|(?:حتى|لحد|لغاية|إلى|الى)\\s*(?:ال)?يوم(?![\\p{L}\\p{M}])",
        RegexOption.IGNORE_CASE
    )

    // "a week ago", "two months back", "قبل أسبوع": a day, or days, before this week or month.
    private val WEEKS_OR_MONTHS_AGO = Regex(
        "\\b(?:weeks?|months?)\\s+(?:ago|back)\\b|\\b(?:hafte|hafta|mahine|mahina)\\s*(?:pehle|pahle)\\b" +
            "|(?:हफ्ते|हफ़्ते|हफ्ता|महीने|महीना)\\s*पहले" +
            "|قبل\\s*(?:[\\d٠-٩]+\\s*)?(?:ال)?(?:اسبوع|أسبوع|اسابيع|أسابيع|شهر|اشهر|أشهر|شهور)",
        RegexOption.IGNORE_CASE
    )

    private val THIS_YEAR = Regex(
        "\\b(?:this|current)\\s+year\\b|\\bis\\s+saal\\b|इस\\s+(?:साल|वर्ष)|هالسنة" +
            "|(?:هذه|هذي)\\s+السنة|هذا\\s+العام|(?:السنة|العام)\\s+(?:الحالية|الحالي|دي)",
        RegexOption.IGNORE_CASE
    )
    private val LAST_YEAR = Regex(
        "\\b(?:last|previous)\\s+year\\b|\\bpichh?le\\s+saal\\b|पिछले\\s+(?:साल|वर्ष)" +
            "|(?:السنة|العام)\\s+(?:الماضية|الماضي|الفائتة|الفائت|السابقة|السابق|اللي فاتت|اللي فات)",
        RegexOption.IGNORE_CASE
    )

    private fun text(value: Any?) = value?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    // Months by name. English "may" is mostly the verb, so it's left out; "كانون" and "تشرين" alone
    // are two months each.
    private val MONTHS: Map<String, Month> = mapOf(
        "january" to Month.JANUARY, "february" to Month.FEBRUARY, "march" to Month.MARCH, "april" to Month.APRIL,
        "june" to Month.JUNE, "july" to Month.JULY, "august" to Month.AUGUST, "september" to Month.SEPTEMBER,
        "sept" to Month.SEPTEMBER, "october" to Month.OCTOBER, "november" to Month.NOVEMBER, "december" to Month.DECEMBER,
        "يناير" to Month.JANUARY, "فبراير" to Month.FEBRUARY, "مارس" to Month.MARCH, "أبريل" to Month.APRIL,
        "ابريل" to Month.APRIL, "إبريل" to Month.APRIL, "مايو" to Month.MAY, "يونيو" to Month.JUNE, "يوليو" to Month.JULY,
        "أغسطس" to Month.AUGUST, "اغسطس" to Month.AUGUST, "سبتمبر" to Month.SEPTEMBER, "أكتوبر" to Month.OCTOBER,
        "اكتوبر" to Month.OCTOBER, "نوفمبر" to Month.NOVEMBER, "ديسمبر" to Month.DECEMBER,
        "كانون الثاني" to Month.JANUARY, "شباط" to Month.FEBRUARY, "آذار" to Month.MARCH, "نيسان" to Month.APRIL,
        "أيار" to Month.MAY, "حزيران" to Month.JUNE, "تموز" to Month.JULY, "أيلول" to Month.SEPTEMBER,
        "تشرين الأول" to Month.OCTOBER, "تشرين الثاني" to Month.NOVEMBER, "كانون الأول" to Month.DECEMBER,
    )
    private val MONTH_ALT = MONTHS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    // The month, an Arabic و/ف/ب/ل in front, and the year after it: "October 2025.", "October of
    // 2025", "أكتوبر عام 2025"; "oct" isn't one. Digits are read as Latin ones first.
    private val MONTH_NAME = Regex(
        "(?<![\\p{L}\\p{M}])[وفبل]{0,2}($MONTH_ALT)(?![\\p{L}\\p{M}])" +
            "(?:[\\s,،_-]+(?:(?:of|من|عام|سنة)\\s+)?((?:19|20)\\d\\d)(?!\\d|[.,]\\d))?",
        RegexOption.IGNORE_CASE
    )

    // A year written elsewhere as a date: "in 2025, how much in October?". A lone number, or one
    // after "than" or "of", is more likely an amount ("more than 2000 in October?").
    private val OTHER_YEAR = Regex(
        "(?:\\b(?:in|from|since|during|vs\\.?|versus)|في|عام|سنة)\\s+(?:19|20)\\d\\d(?!\\d|[.,]\\d)",
        RegexOption.IGNORE_CASE
    )
    private val YEAR_BEFORE_MONTH = Regex(
        "(?<![\\d.,])(?:19|20)\\d\\d[\\s,،_-]+[وفبل]{0,2}(?:$MONTH_ALT)(?![\\p{L}\\p{M}])",
        RegexOption.IGNORE_CASE
    )

    // A day in it, "15 October" or "October 15th": days the app doesn't look up one by one.
    private val DAY_IN_MONTH = Regex(
        "(?<![\\d٠-٩.,])[\\d٠-٩]{1,2}(?:st|nd|rd|th)?\\s+(?:of\\s+)?[وفبل]{0,2}(?:$MONTH_ALT)(?![\\p{L}\\p{M}])" +
            "|(?<![\\p{L}\\p{M}])(?:$MONTH_ALT)\\s+[\\d٠-٩]{1,2}(?![\\d٠-٩])",
        RegexOption.IGNORE_CASE
    )

    // Words just before a month that make its days fewer or more than the month: "since
    // October", "the first week of October", "end of October", "mid-October", "منذ أكتوبر".
    private val NOT_THE_MONTH_BEFORE = setOf(
        "since", "from", "after", "before", "until", "till", "til", "untill", "upto", "through", "thru",
        "week", "weeks", "fortnight", "half", "end", "middle", "mid", "start", "beginning", "early", "late", "first",
        "منذ", "من", "بعد", "قبل", "حتى", "لغاية", "لحد", "إلى", "الى", "أسبوع", "اسبوع", "نصف", "نص",
        "آخر", "اخر", "أول", "اول", "منتصف", "بداية", "نهاية", "أواخر", "اواخر", "أوائل", "اوائل", "مطلع",
    )

    // And just after it: "October onwards", "October till now", "أكتوبر وحتى اليوم".
    private val NOT_THE_MONTH_AFTER = setOf(
        "onward", "onwards", "since", "until", "till", "til", "untill", "upto", "حتى", "لغاية", "لحد",
    )

    // "to" and "through" only when they reach a day ("October to date", "October through 15th"):
    // "paid in October to the landlord" and "October through my card" are October.
    private val TO = setOf("to", "إلى", "الى")
    private val THROUGH = setOf("through", "thru")
    private val NOW = setOf("now", "today", "date", "الآن", "الان", "يوم", "هالحين")

    // "last November", "نوفمبر الماضي": in November, last year's.
    private val LAST_BEFORE = setOf("last", "previous", "past")
    private val LAST_AFTER = setOf("ماضي", "ماضية", "فائت", "فائتة", "سابق", "منصرم")

    // A word as written and without a leading Arabic و/ف, then ب/ال/لل: "والأسبوع" is أسبوع,
    // "للمطاعم" مطاعم.
    private fun forms(word: String): Set<String> {
        val bare = word.removePrefix("و").removePrefix("ف")
        val stem = if (bare.startsWith("لل")) bare.drop(2) else bare.removePrefix("ب").removePrefix("ال")
        return setOf(word, bare, stem).filter { it.isNotEmpty() }.toSet()
    }

    private fun wordsOf(text: String): List<String> =
        text.lowercase().replace(Regex("\\bup\\s+to\\b"), "upto").split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * The one calendar month [message] names ("in October", "في أكتوبر", "october_2025"), in the
     * year written after it, else [year], else the latest one begun by [today] ("last November",
     * in November, is last year's). Null when it names none, two, or a day in it; PERIOD when
     * the words beside it ask for other days ("since October", "end of October").
     */
    fun monthNamed(message: String, today: LocalDate, year: Int? = null): Dates? {
        val text = message.toLatinDigits()
        val found = MONTH_NAME.findAll(text).toList()
        val month = found.mapNotNull { MONTHS[it.groupValues[1].lowercase()] }.toSet().singleOrNull() ?: return null
        if (DAY_IN_MONTH.containsMatchIn(text)) return null
        val written = found.mapNotNull { it.groupValues[2].toIntOrNull() }.toSet()
        if (written.size > 1) return null
        // A year written apart from the month ("in 2025, ... October", "2025 October") is never
        // swapped for the latest month begun.
        val rest = found.foldRight(text) { m, t -> t.replaceRange(m.range, " ") }
        if (OTHER_YEAR.containsMatchIn(rest) || YEAR_BEFORE_MONTH.containsMatchIn(text)) {
            return Dates.Invalid(Problem.PERIOD, message.trim())
        }
        val before = found.map { wordsOf(text.substring(0, it.range.first)).takeLast(2).map(::forms) }
        val after = found.map { wordsOf(text.substring(it.range.last + 1)).take(2).map(::forms) }
        val otherDays = before.any { w -> w.any { it.any(NOT_THE_MONTH_BEFORE::contains) } } ||
            after.any { w ->
                w.firstOrNull()?.any(NOT_THE_MONTH_AFTER::contains) == true ||
                    (w.firstOrNull()?.any(TO::contains) == true && w.getOrNull(1)?.any(NOW::contains) == true) ||
                    (w.firstOrNull()?.any(THROUGH::contains) == true && w.getOrNull(1)?.any { it in NOW || it.first().isDigit() } == true)
            }
        if (otherDays) return Dates.Invalid(Problem.PERIOD, message.trim())
        val last = before.any { w -> w.lastOrNull()?.any(LAST_BEFORE::contains) == true } ||
            after.any { w -> w.firstOrNull()?.any(LAST_AFTER::contains) == true || (w.firstOrNull()?.contains("اللي") == true && w.getOrNull(1)?.contains("فات") == true) }
        val latest = if (month.value > today.monthValue || (last && month == today.month)) today.year - 1 else today.year
        val asked = YearMonth.of(written.singleOrNull() ?: year ?: latest, month)
        return window(asked.atDay(1), asked.atEndOfMonth(), today, message.trim())
    }

    /** Whether [text] names a month by name, as [monthNamed] reads one. */
    fun namesMonth(text: String): Boolean = MONTH_NAME.containsMatchIn(text)

    /** The model spelled a month out as the period ("October", "october_last_year"), no dates. */
    fun spelledMonth(period: Any?, from: Any?, to: Any?): Boolean =
        text(from) == null && text(to) == null && text(period)?.let { namesMonth(it) } == true

    /** The year [yearNamed] names, "this_year" or "last_year", by [today]. */
    fun yearOf(named: String, today: LocalDate): Int = if (named == "this_year") today.year else today.year - 1

    /**
     * [message] without the [merchant] and [account] the call names, the account's digits too:
     * "24/7" or "the card ending in 2019" is a name there, not a date or a year.
     */
    fun withoutNames(message: String, merchant: Any?, account: Any?): String =
        listOfNotNull(text(merchant), text(account), text(account)?.filter(Char::isDigit)?.takeIf { it.length >= 3 })
            .sortedByDescending { it.length }
            .fold(message) { m, name -> m.replace(name, " ", ignoreCase = true) }

    /**
     * "this_year" or "last_year" when [message] names one of them (not both), with the rest of
     * the message, which can still name days within it ("March this year"). Null for neither.
     */
    fun yearNamed(message: String): Pair<String, String>? {
        val (thisYear, lastYear) = THIS_YEAR.containsMatchIn(message) to LAST_YEAR.containsMatchIn(message)
        return when {
            thisYear == lastYear -> null
            thisYear -> "this_year" to THIS_YEAR.replace(message, " ")
            else -> "last_year" to LAST_YEAR.replace(message, " ")
        }
    }

    /** The model left the period to the app: no period beyond this month, and no dates. */
    fun defaultPeriod(period: Any?, from: Any?, to: Any?): Boolean =
        text(from) == null && text(to) == null && (text(period)?.let { normalized(it) in HOME } ?: true)

    private fun normalized(period: String) = period.lowercase().replace(Regex("[\\s-]+"), "_")

    /**
     * The days asked about, up to the end of [now]'s day: [from] and [to] (YYYY-MM-DD) when given,
     * else [period]. "this_month" and an empty period are Home's budget cycle; "last_month" is the
     * whole cycle before it; a month written YYYY-MM and a year are calendar ones.
     */
    fun dates(period: Any?, from: Any?, to: Any?, now: LocalDateTime, firstDayOfWeek: DayOfWeek, cycleStartDay: Int): Dates {
        val today = now.toLocalDate()
        val (f, t) = text(from) to text(to)
        if (f != null || t != null) {
            if (f == null) return Dates.Invalid(Problem.NO_START, t!!)
            val start = day(f) ?: return Dates.Invalid(Problem.DATE, f)
            val end = t?.let { day(it) ?: return Dates.Invalid(Problem.DATE, it) } ?: return window(start, today, today, f)
            if (end.isBefore(start)) return Dates.Invalid(Problem.ORDER, f, t)
            return window(start, end, today, f)
        }
        val raw = text(period) ?: return Dates.Home
        val p = normalized(raw)
        return when {
            p in HOME -> Dates.Home
            p == "last_month" || p == "previous_month" -> {
                val (start, end) = BudgetCycle.previousCycle(BudgetCycle.currentCycle(today, cycleStartDay), cycleStartDay)
                window(start, end, today, raw)
            }
            p == "this_week" || p == "week" -> window(today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)), today, today, raw)
            p == "last_week" || p == "previous_week" -> {
                val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks(1)
                window(start, start.plusDays(6), today, raw)
            }
            p == "today" -> window(today, today, today, raw)
            p == "yesterday" -> window(today.minusDays(1), today.minusDays(1), today, raw)
            p == "this_year" || p == "year" || p == "current_year" -> window(today.withDayOfYear(1), today, today, raw)
            p == "last_year" || p == "previous_year" -> Year.from(today).minusYears(1).let { window(it.atDay(1), it.atMonth(12).atEndOfMonth(), today, raw) }
            YEAR_MONTH.matches(raw) -> runCatching { YearMonth.parse(raw) }.getOrNull()
                ?.let { window(it.atDay(1), it.atEndOfMonth(), today, raw) } ?: Dates.Invalid(Problem.PERIOD, raw)
            YEAR.matches(raw) -> Year.parse(raw).let { window(it.atDay(1), it.atMonth(12).atEndOfMonth(), today, raw) }
            // "October", "october_2025", "october_last_year": a month by name, in the year it says.
            else -> raw.replace(Regex("[_-]+"), " ").let { spelled ->
                val year = yearNamed(spelled)
                monthNamed(year?.second ?: spelled, today, year?.let { yearOf(it.first, today) })
            } ?: Dates.Invalid(Problem.PERIOD, raw)
        }
    }

    /**
     * The days [message] names when the model left the period empty: one day ("today", "yesterday",
     * "3 days ago", "a week ago") is that day; days the app can't count ("on Monday", "the last
     * 10 days", "a month ago") are PERIOD, never this month instead. Null when it names none,
     * only the days so far ("till today", "aaj tak"), or a week or a month: those decide.
     */
    fun daysNamed(message: String, today: LocalDate): Dates? {
        val text = SO_FAR.replace(message, " ")
        val days = ChatMessageFacts.statedDays(text)
        fun day(): Dates? = days.singleOrNull()?.let { today.minusDays(it.toLong()) }?.let { window(it, it, today, message) }
        if (SpendingComparison.namesWeekOrMonth(text)) {
            return if (WEEKS_OR_MONTHS_AGO.containsMatchIn(text)) day() ?: Dates.Invalid(Problem.PERIOD) else null
        }
        return when {
            days.size == 1 -> day()
            days.isNotEmpty() || ChatMessageFacts.statesADay(text) || DAYS.containsMatchIn(text) -> Dates.Invalid(Problem.PERIOD)
            else -> null
        }
    }

    /** [start] to [end], cut at the end of [today]: a figure never claims days still to come. */
    private fun window(start: LocalDate, end: LocalDate, today: LocalDate, asked: String): Dates =
        if (start.isAfter(today)) Dates.Invalid(Problem.FUTURE, asked)
        else Dates.Of(SpendingComparison.Window(start.atStartOfDay(), minOf(end, today).atTime(LocalTime.MAX)))

    /** ISO dates only, checked strictly: 2025-02-29 isn't a day. */
    private fun day(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    /** The grouping asked for; null for one the app doesn't offer. */
    fun groupBy(value: Any?): GroupBy? = when (text(value)?.lowercase()?.removeSuffix("s")) {
        null, "none", "total", "no" -> GroupBy.NONE
        "category", "categorie" -> GroupBy.CATEGORY
        "merchant", "shop", "store", "payee" -> GroupBy.MERCHANT
        "account", "bank", "card" -> GroupBy.ACCOUNT
        else -> null
    }

    // "by card", "per category", "across my cards", "category-wise", "top category", "حسب البطاقة",
    // "لكل فئة", "أي بطاقة", "أكثر فئة". A bare "أي" is mostly "any" ("أي شي"), "أكثر" "more than".
    private val BREAKDOWN = Regex(
        "\\b(?:by|per|each|which|breakdown|split|across|wise|categories|cards|accounts)\\b" +
            "|\\b(?:top|most|biggest|highest)\\s+(?:category|card|account)\\b" +
            "|(?<![\\p{L}\\p{M}])(?:حسب|بحسب|لكل|توزيع|تقسيم|[وبل]?(?:ال)?(?:فئات|بطاقات|حسابات|تصنيفات))(?![\\p{L}\\p{M}])" +
            "|(?<![\\p{L}\\p{M}])[وب]?(?:أي|اي|أكثر|اكثر|أعلى|اعلى)\\s+(?:ال)?(?:فئة|بطاقة|حساب|تصنيف)",
        RegexOption.IGNORE_CASE
    )

    /**
     * The grouping that answers [message]. Qwen2.5 fills group_by in for a plain total
     * ("category" for "how much at Swiggy?"): a ranking of the one merchant, account or category
     * asked about is that total, and so is a merchant's figure the words don't ask to break down.
     * "Amazon spending by card" keeps its accounts.
     */
    fun grouping(by: GroupBy, category: String?, merchant: String?, account: String?, message: String): GroupBy = when {
        by == GroupBy.MERCHANT && merchant != null -> GroupBy.NONE
        merchant != null && !BREAKDOWN.containsMatchIn(message) -> GroupBy.NONE
        by == GroupBy.ACCOUNT && account != null -> GroupBy.NONE
        by == GroupBy.CATEGORY && category != null -> GroupBy.NONE
        else -> by
    }

    /**
     * Whether [message] names [category], or its [label] as the app shows it ("طعام ومطاعم" for
     * "Food & Dining"), by a word of it: "food", "الطعام". Beside a merchant Qwen2.5 adds a
     * category of its own ("Shopping" for "how much at Swiggy?"), which narrows the merchant's
     * figure to nothing; the merchant alone says what was spent there. A word counts when it
     * differs from the category's only in its ending ("grocery", "Groceries"): "transactions"
     * isn't "Transportation". Pass [message] without the names the call gives ([withoutNames]).
     */
    fun namesCategory(category: String, message: String, label: String? = null): Boolean {
        val words = norm(message).split(" ").flatMap(::forms)
        return listOfNotNull(category, label).flatMap { norm(it).split(" ") }.flatMap(::forms).filter { it.length >= 3 }
            .any { w ->
                words.any {
                    val shared = it.commonPrefixWith(w).length
                    shared >= minOf(4, w.length) && shared >= minOf(it.length, w.length) - 2
                }
            }
    }

    /**
     * Words that name no bank or alias: "my credit card ending 1234", "SBI Bank". Many banks'
     * names carry them, so they never pick one account over another.
     */
    private val ACCOUNT_WORDS = setOf(
        "my", "the", "a", "c", "ac", "acc", "acct", "account", "card", "credit", "debit", "savings", "saving", "current",
        "bank", "ending", "ends", "end", "in", "with", "of", "on", "at", "no", "number", "x", "xx", "xxx", "xxxx",
        "بطاقة", "بطاقتي", "البطاقة", "حساب", "حسابي", "الحساب", "رقم", "ائتمان", "ائتمانية", "الائتمانية", "مدى", "المنتهية", "تنتهي", "في",
        "بنك", "البنك", "بنكي", "مصرف", "المصرف",
    )

    /**
     * Whether [message] names the [account] a call gave: its last digits, or a word of it that
     * isn't a generic one ("HDFC", not "card"). An account the model filled in on its own
     * restricts nothing the user asked about.
     */
    fun namesAccount(message: String, account: String): Boolean {
        val digits = account.filter(Char::isDigit)
        val said = norm(message).split(" ")
        return (digits.length >= 3 && digits.takeLast(4) in message.filter(Char::isDigit)) ||
            norm(account.filterNot(Char::isDigit)).split(" ").any { it.isNotEmpty() && it !in ACCOUNT_WORDS && it in said }
    }

    sealed interface AccountMatch {
        data class One(val account: AccountBalanceEntity) : AccountMatch
        /** Several accounts answer to it, e.g. two cards ending in the same digits. */
        data class Several(val accounts: List<AccountBalanceEntity>) : AccountMatch
        data object None : AccountMatch
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()

    /**
     * The account the user named: its alias or "Bank 1234" exactly first (or "Paytm WALLET", as
     * the chat's prompt lists a wallet), then its last digits (with the words of its name, when
     * a name was given; alone only when none was), then the bank or alias alone. Generic words
     * ("bank", "card", "حساب") name no account. The first of those that matches anything
     * decides; two accounts there is a question for the user, not a guess.
     */
    fun account(asked: String, accounts: List<AccountBalanceEntity>): AccountMatch {
        val wanted = norm(asked)
        if (wanted.isEmpty()) return AccountMatch.None
        val digits = asked.filter(Char::isDigit)
        // "HDFC Bank 1234" names HDFC: "bank" is in SBI Bank's name too, and decides nothing.
        val words = norm(asked.filterNot(Char::isDigit)).split(" ").filter { it.isNotEmpty() && it !in ACCOUNT_WORDS }
        fun names(a: AccountBalanceEntity) = listOfNotNull(a.alias?.takeIf { it.isNotBlank() }, a.bankName).map(::norm)
        fun nameWords(a: AccountBalanceEntity) = names(a).flatMap { it.split(" ") }.filter { it.isNotEmpty() && it !in ACCOUNT_WORDS }.toSet()
        fun endsIn(a: AccountBalanceEntity) = digits.length >= 3 && a.accountLast4.endsWith(digits.takeLast(4))
        val tiers = listOf<(AccountBalanceEntity) -> Boolean>(
            { a ->
                wanted == norm(a.displayLabel) || wanted == norm(AccountBalanceEntity.accountLabel(a.bankName, a.accountLast4)) ||
                    wanted == norm("${a.bankName} ${a.accountLast4}")
            },
            // "sbi card 1234": the digits, and every word of a name given is one of its own.
            // "HDFC 1234" isn't SBI's card ending 1234, nor another one when HDFC's is ignored.
            { a -> endsIn(a) && words.isNotEmpty() && nameWords(a).containsAll(words) },
            // "card 1234", "1234": the digits alone, when no name came with them.
            { a -> endsIn(a) && words.isEmpty() },
            // "hdfc", "HDFC bank": the bank or alias alone, read past its generic words.
            { a -> digits.isEmpty() && words.isNotEmpty() && names(a).any { it == wanted || it.startsWith(wanted) || nameWords(a).containsAll(words) } },
        )
        for (tier in tiers) {
            val hits = accounts.filter(tier).distinctBy { SpendingFilter.accountKey(it) }
            if (hits.size == 1) return AccountMatch.One(hits.single())
            if (hits.size > 1) return AccountMatch.Several(hits)
        }
        return AccountMatch.None
    }
}
