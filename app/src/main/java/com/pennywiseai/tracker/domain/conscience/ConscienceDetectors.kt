package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.domain.spending.RecurringCharge
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Proves facts about a fresh purchase from the purchases before it. Money is
 * only ever totalled within one category and one currency; counts and absences
 * may span currencies.
 */
object ConscienceDetectors {
    private val MIN_SPREAD: Duration = Duration.ofMinutes(30)

    private const val COMEBACK_WINDOW_DAYS = 60L
    private const val COMEBACK_MIN_ACTIVE_DAYS = 8
    private const val MIN_COMEBACK_GAP = 3
    private const val MAX_COMEBACK_GAP = 30

    private const val RECORD_WINDOW_DAYS = 60L
    private const val RECORD_MIN_ACTIVE_DAYS = 8
    private val RECORD_MARGIN = BigDecimal("1.2")
    private val RECORD_MAX_SHARE = BigDecimal("0.6")

    private val LATE_FROM: LocalTime = LocalTime.of(23, 0)
    private val LATE_UNTIL: LocalTime = LocalTime.of(5, 0)
    private const val RENEWAL_CLOCK_MINUTES = 60

    private const val SPIKE_BASELINE_WEEKS = 8
    private const val SPIKE_MIN_ACTIVE_DAYS = 12
    private const val SPIKE_MIN_POSITIVE_WEEKS = 6
    private val SPIKE_MEDIAN_FACTOR = BigDecimal("1.8")
    private val SPIKE_MAD_FACTOR = BigDecimal("3")
    private val SPIKE_P90_FACTOR = BigDecimal("1.15")
    private val SPIKE_MAX_SHARE = BigDecimal("0.5")
    private val SPIKE_LEVEL_SHIFT = BigDecimal("1.3")

    private const val POSITIVE_BASELINE_WEEKS = 4
    private val POSITIVE_MAX_WEEK_SHARE = BigDecimal("0.4")
    private val POSITIVE_DROP = BigDecimal("0.8")
    private const val POSITIVE_MIN_ACTIVE_DAYS = 3
    private const val POSITIVE_ACTIVE_RATIO = 0.75

    private const val WEEKLY_WEEKS = 6L
    private const val WEEKLY_MIN_WEEKS = 4
    private const val WEEKLY_MIN_ACTIVE_DAYS = 8
    private const val WEEKLY_LEAD = 2

    /**
     * Every fact that holds for [current], in [ConscienceKind] order, with
     * [ConscienceFact.Hello] always last: the cadence decides which one speaks.
     * [history] may hold purchases after [current]; only earlier ones count.
     */
    fun detect(
        current: ConsciencePurchase,
        history: List<ConsciencePurchase>,
        ledgerStart: LocalDate?,
        activeDates: Set<LocalDate> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ConscienceFact> {
        val today = current.at.toLocalDate()
        val prior = history.filter { it.isBefore(current) }
        val inCategory = prior.filter { it.category == current.category }
        val sameMoney = inCategory.filter { it.currency == current.currency }
        val facts = listOfNotNull(
            positive(current, inCategory, today, ledgerStart, activeDates),
            comeback(current, inCategory, today),
            repeat(current, inCategory, zone),
            weekly(current, inCategory, today),
            lateNight(current, prior),
            recordDay(current, sameMoney, today, ledgerStart),
            spike(current, sameMoney, today, ledgerStart),
        ).sortedBy { it.kind.ordinal }
        return facts + ConscienceFact.Hello(current.category)
    }

    /** One transaction, its split parts added back together. */
    private class Buy(
        val at: LocalDateTime,
        val amount: BigDecimal,
        val currency: String,
        val merchant: String,
        val charge: BigDecimal = amount,
    ) {
        val date: LocalDate get() = at.toLocalDate()
    }

    private fun ConsciencePurchase.isBefore(current: ConsciencePurchase): Boolean =
        at.isBefore(current.at) || (at == current.at && transactionId < current.transactionId)

    private fun ConsciencePurchase.toBuy() = Buy(at, amount, currency, merchant)

    private fun List<ConsciencePurchase>.byTransaction(): List<Buy> =
        groupBy { it.transactionId }.values.map { parts ->
            val first = parts.first()
            Buy(first.at, parts.sumOf { it.amount }, first.currency, first.merchant, first.charge)
        }

    private fun List<ConsciencePurchase>.total(): BigDecimal = sumOf { it.amount }

    private fun ConsciencePurchase.isOn(from: LocalDate, to: LocalDate): Boolean {
        val date = at.toLocalDate()
        return !date.isBefore(from) && !date.isAfter(to)
    }

    private fun median(values: List<BigDecimal>): BigDecimal {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]).divide(BigDecimal(2))
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * A burst of separate purchases. Recurring charges aren't a burst, and only
     * purchases [MIN_SPREAD] apart count, so a bank's duplicate alerts make one.
     */
    private fun repeat(current: ConsciencePurchase, inCategory: List<ConsciencePurchase>, zone: ZoneId): ConscienceFact? {
        if (current.recurring) return null
        // Elapsed hours, not wall-clock ones: a DST change must not stretch "6 hours".
        fun LocalDateTime.instant() = atZone(zone).toInstant()
        val now = current.at.instant()
        val buys = inCategory.filter { !it.recurring }.byTransaction()
        for ((hours, minCount) in listOf(6 to 3, 24 to 4)) {
            val from = now.minus(Duration.ofHours(hours.toLong()))
            val times = buys.map { it.at.instant() }.filter { !it.isBefore(from) } + now
            var count = 0
            var last: Instant? = null
            for (time in times.sorted()) {
                if (last == null || Duration.between(last, time) >= MIN_SPREAD) {
                    count++
                    last = time
                }
            }
            if (count >= minCount) return ConscienceFact.Repeat(current.category, count, hours)
        }
        return null
    }

    /** Whether [time] is in the late-night window, 23:00 up to 05:00. */
    fun isLateNight(time: LocalTime): Boolean = !time.isBefore(LATE_FROM) || time.isBefore(LATE_UNTIL)

    private fun lateNight(current: ConsciencePurchase, prior: List<ConsciencePurchase>): ConscienceFact? {
        if (!isLateNight(current.at.toLocalTime())) return null
        return if (isScheduledRenewal(current, prior)) null else ConscienceFact.LateNight(current.category)
    }

    /**
     * Two earlier charges from the same merchant at the same amount and clock
     * time, a billing cycle apart and a cycle before this one: a renewal that
     * runs at night, not a purchase someone made at night.
     */
    private fun isScheduledRenewal(current: ConsciencePurchase, prior: List<ConsciencePurchase>): Boolean {
        val time = current.at.toLocalTime()
        val matches = prior.byTransaction()
            .filter {
                it.currency == current.currency &&
                    RecurringCharge.sameMerchant(current.merchant, it.merchant) &&
                    RecurringCharge.sameAmount(current.charge, it.charge) &&
                    clockMinutesApart(time, it.at.toLocalTime()) <= RENEWAL_CLOCK_MINUTES
            }
            .sortedByDescending { it.at }
        if (matches.size < 2) return false
        val (last, beforeLast) = matches
        val cycle = RecurringCharge.cycleForGap(ChronoUnit.DAYS.between(last.date, current.at.toLocalDate()))
            ?: return false
        return cycle == RecurringCharge.cycleForGap(ChronoUnit.DAYS.between(beforeLast.date, last.date))
    }

    private fun clockMinutesApart(a: LocalTime, b: LocalTime): Int {
        val minutes = abs(a.toSecondOfDay() - b.toSecondOfDay()) / 60
        return minOf(minutes, 24 * 60 - minutes)
    }

    /**
     * The last 7 days in this category and currency crossed, with this purchase,
     * a bar well above every ordinary week of the 8 before. Several purchases
     * have to make the week: one big buy is a purchase, not a spree.
     */
    private fun spike(
        current: ConsciencePurchase,
        sameMoney: List<ConsciencePurchase>,
        today: LocalDate,
        ledgerStart: LocalDate?,
    ): ConscienceFact? {
        fun weekStart(index: Int): LocalDate = today.minusDays(6L + 7L * index)
        val baselineStart = weekStart(SPIKE_BASELINE_WEEKS)
        if (ledgerStart == null || ledgerStart.isAfter(baselineStart)) return null

        val weeks = (1..SPIKE_BASELINE_WEEKS).map { index ->
            val start = weekStart(index)
            sameMoney.filter { it.isOn(start, start.plusDays(6)) }.total()
        }
        val baselineDates = sameMoney.filter { it.isOn(baselineStart, weekStart(1).plusDays(6)) }
            .map { it.at.toLocalDate() }.distinct().size
        if (baselineDates < SPIKE_MIN_ACTIVE_DAYS) return null
        if (weeks.count { it.signum() > 0 } < SPIKE_MIN_POSITIVE_WEEKS) return null
        val usual = median(weeks)
        if (usual.signum() <= 0) return null

        val mad = median(weeks.map { (it - usual).abs() })
        val sorted = weeks.sorted()
        // Nearest rank, ceil(0.9n), in integers so no float rounds it.
        val p90 = sorted[(9 * sorted.size + 9) / 10 - 1]
        val bar = maxOf(usual * SPIKE_MEDIAN_FACTOR, usual + mad * SPIKE_MAD_FACTOR, p90 * SPIKE_P90_FACTOR)

        val thisWeek = sameMoney.filter { it.isOn(weekStart(0), today) }
        val after = thisWeek.total() + current.amount
        if (after - current.amount > bar || after <= bar) return null

        val buys = thisWeek.byTransaction() + current.toBuy()
        if (buys.size < 3 || buys.map { it.date }.distinct().size < 2) return null
        if (buys.any { it.amount > after * SPIKE_MAX_SHARE }) return null

        // Two high weeks in a row is a new normal or a season, not a spike.
        val older = median(weeks.subList(2, SPIKE_BASELINE_WEEKS)) * SPIKE_LEVEL_SHIFT
        if (weeks[0] > older && weeks[1] > older) return null
        return ConscienceFact.Spike(current.category)
    }

    /**
     * Last completed Monday-Sunday week came in well under the usual week of
     * the four before, same category and currency, while the ledger was still
     * being fed: a week the user simply didn't log is no win.
     */
    private fun positive(
        current: ConsciencePurchase,
        inCategory: List<ConsciencePurchase>,
        today: LocalDate,
        ledgerStart: LocalDate?,
        activeDates: Set<LocalDate>,
    ): ConscienceFact? {
        val thisMonday = mondayOf(today)
        // Index 0 is the week just finished; 1..4 its baseline.
        val starts = (1..POSITIVE_BASELINE_WEEKS + 1).map { thisMonday.minusWeeks(it.toLong()) }
        if (ledgerStart == null || ledgerStart.isAfter(starts.last())) return null

        val weeks = starts.map { start -> inCategory.filter { it.isOn(start, start.plusDays(6)) } }
        // "Less on food" is only true when one currency carried the whole category:
        // a cheaper week in one can hide a dearer one in another.
        if (weeks.flatten().any { it.currency != current.currency }) return null

        val money = weeks.map { week -> week.filter { it.currency == current.currency } }
        val totals = money.map { it.total() }
        val dates = money.map { week -> week.map { it.at.toLocalDate() }.distinct().size }
        if (dates[0] < 1) return null
        if (dates.drop(1).count { it >= 2 } < POSITIVE_BASELINE_WEEKS - 1) return null

        val baseline = totals.drop(1)
        val combined = baseline.sumOf { it }
        if (baseline.any { it > combined * POSITIVE_MAX_WEEK_SHARE }) return null
        val usual = median(baseline)
        val week = totals[0]
        if (week > usual * POSITIVE_DROP) return null
        if (baseline.count { week < it } < POSITIVE_BASELINE_WEEKS - 1) return null

        val active = starts.map { start -> activeDates.count { !it.isBefore(start) && !it.isAfter(start.plusDays(6)) } }
        if (active[0] < POSITIVE_MIN_ACTIVE_DAYS) return null
        if (active[0] < POSITIVE_ACTIVE_RATIO * median(active.drop(1))) return null

        val percent = (usual - week).multiply(BigDecimal(100)).divide(usual, 0, RoundingMode.FLOOR).toInt()
        return ConscienceFact.Positive(current.category, percent.coerceIn(1, 99), starts[0].toEpochDay())
    }

    /**
     * Today's weekday stands out among the six complete weeks before this one:
     * active most weeks, and clearly more often than a typical other day.
     */
    private fun weekly(
        current: ConsciencePurchase,
        inCategory: List<ConsciencePurchase>,
        today: LocalDate,
    ): ConscienceFact? {
        if (current.recurring) return null
        val dates = inCategory.filter { !it.recurring }.map { it.at.toLocalDate() }.toSet()
        if (today in dates) return null
        val thisMonday = mondayOf(today)
        val from = thisMonday.minusWeeks(WEEKLY_WEEKS)
        val window = dates.filter { !it.isBefore(from) && it.isBefore(thisMonday) }
        if (window.size < WEEKLY_MIN_ACTIVE_DAYS) return null
        // Dates are distinct, so a weekday's date count is its count of active weeks.
        val weeksByDay = window.groupingBy { it.dayOfWeek }.eachCount()
        val weekday = today.dayOfWeek
        val mine = weeksByDay[weekday] ?: 0
        if (mine < WEEKLY_MIN_WEEKS) return null
        val others = DayOfWeek.entries.filter { it != weekday }.map { weeksByDay[it] ?: 0 }
        return if (mine >= WEEKLY_LEAD + median(others)) ConscienceFact.Weekly(current.category, weekday) else null
    }

    private fun comeback(
        current: ConsciencePurchase,
        inCategory: List<ConsciencePurchase>,
        today: LocalDate,
    ): ConscienceFact? {
        // A break in a category is a break in any currency.
        val dates = inCategory.map { it.at.toLocalDate() }.distinct().sorted()
        val lastActive = dates.lastOrNull() ?: return null
        if (lastActive == today) return null
        val gap = ChronoUnit.DAYS.between(lastActive, today).toInt()

        val baseline = dates.filter { !it.isBefore(lastActive.minusDays(COMEBACK_WINDOW_DAYS)) }
        if (baseline.size < COMEBACK_MIN_ACTIVE_DAYS) return null
        val gaps = baseline.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.sorted()
        val median = (gaps[gaps.size / 2] + gaps[(gaps.size - 1) / 2]) / 2.0
        // Beating the longest gap on record keeps a weekly routine quiet: a
        // Mon-Thu orderer has a median of 1, yet Monday is no comeback.
        val threshold = maxOf(MIN_COMEBACK_GAP, ceil(3 * median).toInt(), gaps.last().toInt() + 1)

        // A threshold above the cap leaves this range empty: an infrequent
        // spender stays silent rather than being held to a lower bar.
        return if (gap in threshold..MAX_COMEBACK_GAP) ConscienceFact.Comeback(current.category, gap) else null
    }

    /**
     * Today's total in this category and currency passed the highest day of the
     * prior 60 by a clear margin, with this purchase, over several purchases.
     */
    private fun recordDay(
        current: ConsciencePurchase,
        sameMoney: List<ConsciencePurchase>,
        today: LocalDate,
        ledgerStart: LocalDate?,
    ): ConscienceFact? {
        val windowStart = today.minusDays(RECORD_WINDOW_DAYS)
        if (ledgerStart == null || ledgerStart.isAfter(windowStart)) return null
        val dayTotals = sameMoney.filter { it.isOn(windowStart, today.minusDays(1)) }
            .groupBy { it.at.toLocalDate() }
            .mapValues { (_, purchases) -> purchases.total() }
        if (dayTotals.size < RECORD_MIN_ACTIVE_DAYS) return null

        val bar = dayTotals.values.max() * RECORD_MARGIN
        val todays = sameMoney.filter { it.at.toLocalDate() == today }.byTransaction() + current.toBuy()
        val after = todays.sumOf { it.amount }
        if (after - current.amount > bar || after <= bar) return null
        if (todays.size < 2 || Duration.between(todays.minOf { it.at }, current.at) < MIN_SPREAD) return null
        if (todays.any { it.amount > after * RECORD_MAX_SHARE }) return null
        return ConscienceFact.RecordDay(current.category)
    }
}
