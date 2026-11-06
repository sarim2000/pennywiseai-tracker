package com.pennywiseai.tracker.domain.conscience

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object ConscienceDetectors {
    private const val MIN_LEDGER_DAYS = 60L
    private const val WINDOW_DAYS = 60L
    private const val MIN_ACTIVE_DAYS = 10
    private const val MIN_COMEBACK_GAP = 3
    private const val MAX_COMEBACK_GAP = 30

    fun detect(
        current: ConsciencePurchase,
        history: List<ConsciencePurchase>,
        ledgerStart: LocalDate?,
    ): ConscienceFact? {
        val today = current.at.toLocalDate()
        if (ledgerStart == null || ledgerStart.isAfter(today.minusDays(MIN_LEDGER_DAYS))) return null
        val prior = history.filter {
            it.category == current.category && it.currency == current.currency && !it.at.isAfter(current.at)
        }
        return comeback(current, prior, today) ?: recordDay(current, prior, today)
    }

    private fun comeback(
        current: ConsciencePurchase,
        prior: List<ConsciencePurchase>,
        today: LocalDate,
    ): ConscienceFact? {
        val dates = prior.map { it.at.toLocalDate() }.distinct().sorted()
        val lastActive = dates.lastOrNull() ?: return null
        if (lastActive == today) return null
        val gap = ChronoUnit.DAYS.between(lastActive, today).toInt()

        val baseline = dates.filter { !it.isBefore(lastActive.minusDays(WINDOW_DAYS)) }
        if (baseline.size < MIN_ACTIVE_DAYS) return null
        val gaps = baseline.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.sorted()
        val median = (gaps[gaps.size / 2] + gaps[(gaps.size - 1) / 2]) / 2.0
        // Beating the longest gap on record keeps a weekly routine quiet: a
        // Mon-Thu orderer has a median of 1, yet Monday is no comeback.
        val threshold = maxOf(MIN_COMEBACK_GAP, ceil(3 * median).toInt(), gaps.last().toInt() + 1)

        // A threshold above the cap leaves this range empty: an infrequent
        // spender stays silent rather than being held to a lower bar.
        return if (gap in threshold..MAX_COMEBACK_GAP) ConscienceFact.Comeback(current.category, gap) else null
    }

    private fun recordDay(
        current: ConsciencePurchase,
        prior: List<ConsciencePurchase>,
        today: LocalDate,
    ): ConscienceFact? {
        val windowStart = today.minusDays(WINDOW_DAYS)
        val dayTotals = prior
            .filter { val date = it.at.toLocalDate(); !date.isBefore(windowStart) && date.isBefore(today) }
            .groupBy { it.at.toLocalDate() }
            .mapValues { (_, purchases) -> purchases.fold(BigDecimal.ZERO) { sum, p -> sum + p.amount } }
        if (dayTotals.size < MIN_ACTIVE_DAYS) return null

        val maxDay = dayTotals.values.max()
        val before = prior.filter { it.at.toLocalDate() == today }.fold(BigDecimal.ZERO) { sum, p -> sum + p.amount }
        val after = before + current.amount
        return if (before <= maxDay && after > maxDay) ConscienceFact.RecordDay(current.category) else null
    }
}
