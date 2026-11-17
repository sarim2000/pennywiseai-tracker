package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * How well a cycle is going and being tracked, 0–100, in one currency: a health
 * indicator for the user, built from parts they can see and change. Each part is
 * 0–100 too; one without data is left out and the rest share its weight.
 */
data class PeriodScore(
    val currency: String,
    val score: Int,
    /** Share of income kept; null without income. */
    val savings: Part.Savings?,
    /**
     * Budgets in this currency on or under an even pace; null without any. One with
     * spending it couldn't convert reads low, so it's left out rather than counted on track.
     */
    val budgets: Part.Budgets?,
    val categorised: Part.Categorised,
) {
    sealed interface Part {
        val points: Int

        data class Savings(val percent: Int, override val points: Int) : Part
        data class Budgets(val onTrack: Int, val total: Int, override val points: Int) : Part
        data class Categorised(val categorised: Int, val total: Int, override val points: Int) : Part
    }

    /**
     * How much [part] counts toward [score], in percent: its weight shared with the
     * other parts present, so together they make 100.
     */
    fun shareOf(part: Part): Int {
        val total = listOfNotNull(savings, budgets, categorised).sumOf { weightOf(it) }
        return BigDecimal(weightOf(part) * 100).divide(BigDecimal(total), 0, RoundingMode.HALF_UP).toInt()
    }

    companion object {
        private fun weightOf(part: Part): Int = when (part) {
            is Part.Savings -> SAVINGS_WEIGHT
            is Part.Budgets -> BUDGETS_WEIGHT
            is Part.Categorised -> CATEGORISED_WEIGHT
        }

        /**
         * [spends] are the cycle's spending rows in [currency]; [isUncategorised] tells
         * which have no real category. Null with fewer than [MIN_ROWS] spends, or with
         * neither income nor a budget: one part alone isn't a score worth showing.
         */
        fun of(
            currency: String,
            income: BigDecimal,
            spent: BigDecimal,
            budgets: List<BudgetStatus>,
            spends: List<TransactionEntity>,
            isUncategorised: (String) -> Boolean,
        ): PeriodScore? {
            if (spends.size < MIN_ROWS) return null
            val savings = income.takeIf { it.signum() > 0 }?.let {
                val percent = (it - spent).multiply(HUNDRED).divide(it, 0, RoundingMode.HALF_UP).toInt()
                // Keeping a fifth of income is full marks; spending all of it or more is none.
                Part.Savings(percent, (percent * 100 / FULL_SAVINGS_PERCENT).coerceIn(0, 100))
            }
            val running = budgets.filter { it.currency == currency && it.isRunning && !it.incomplete }
            val budgetPart = running.takeIf { it.isNotEmpty() }?.let { list ->
                val onTrack = list.count { it.isOnTrack }
                Part.Budgets(onTrack, list.size, onTrack * 100 / list.size)
            }
            if (savings == null && budgetPart == null) return null
            val categorised = spends.count { !isUncategorised(it.category) }
            val categorisedPart = Part.Categorised(categorised, spends.size, categorised * 100 / spends.size)

            val weighted = listOfNotNull(
                savings?.let { it.points to SAVINGS_WEIGHT },
                budgetPart?.let { it.points to BUDGETS_WEIGHT },
                categorisedPart.points to CATEGORISED_WEIGHT,
            )
            val score = BigDecimal(weighted.sumOf { (points, weight) -> points * weight })
                .divide(BigDecimal(weighted.sumOf { it.second }), 0, RoundingMode.HALF_UP).toInt()
            return PeriodScore(currency, score, savings, budgetPart, categorisedPart)
        }

        const val MIN_ROWS = 10
        private const val FULL_SAVINGS_PERCENT = 20
        private const val SAVINGS_WEIGHT = 40
        private const val BUDGETS_WEIGHT = 30
        private const val CATEGORISED_WEIGHT = 30
        private val HUNDRED = BigDecimal(100)
    }
}
