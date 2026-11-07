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

/**
 * Proves facts about a fresh purchase from the purchases before it. Money is
 * only ever totalled within one category and one currency; counts may span
 * currencies.
 */
object ConscienceDetectors {
    private val MIN_SPREAD: Duration = Duration.ofMinutes(30)

    private val LATE_FROM: LocalTime = LocalTime.of(23, 0)
    private val LATE_UNTIL: LocalTime = LocalTime.of(5, 0)
    private const val RENEWAL_CLOCK_MINUTES = 60

    private const val POSITIVE_BASELINE_WEEKS = 4
    private val POSITIVE_MAX_WEEK_SHARE = BigDecimal("0.4")
    private val POSITIVE_DROP = BigDecimal("0.8")
    private const val POSITIVE_MIN_ACTIVE_DAYS = 3
    private const val POSITIVE_ACTIVE_RATIO = 0.75

    /**
     * Every fact that holds for [current], in [ConscienceKind] order: the cadence
     * decides which one speaks.
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
        return listOfNotNull(
            positive(current, inCategory, today, ledgerStart, activeDates),
            repeat(current, inCategory, zone),
            lateNight(current, prior),
        ).sortedBy { it.kind.ordinal }
    }

    /** One transaction, however many split parts it has. */
    private class Buy(
        val at: LocalDateTime,
        val currency: String,
        val merchant: String,
        val charge: BigDecimal,
    ) {
        val date: LocalDate get() = at.toLocalDate()
    }

    private fun ConsciencePurchase.isBefore(current: ConsciencePurchase): Boolean =
        at.isBefore(current.at) || (at == current.at && transactionId < current.transactionId)

    private fun List<ConsciencePurchase>.byTransaction(): List<Buy> =
        groupBy { it.transactionId }.values.map { parts ->
            val first = parts.first()
            Buy(first.at, first.currency, first.merchant, first.charge)
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

        val totals = weeks.map { it.total() }
        val dates = weeks.map { week -> week.map { it.at.toLocalDate() }.distinct().size }
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
        return ConscienceFact.Positive(current.category, percent.coerceIn(1, 99))
    }
}
