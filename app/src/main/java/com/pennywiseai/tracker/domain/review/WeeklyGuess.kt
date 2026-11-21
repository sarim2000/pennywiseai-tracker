package com.pennywiseai.tracker.domain.review

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Home's weekly game: "How much did you spend on <category> last week?", about one
 * category in one named currency of the last full week, by Review's definitions.
 */
data class WeeklyGuessQuestion(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val category: String,
    val currency: String,
    /** What the bank messages recorded: Review's net spend in [category], never zero. */
    val actual: BigDecimal,
    /** The slider's top: about twice a usual week, and always room above [actual]. */
    val max: BigDecimal,
    /** What the slider snaps to, so a guess reads as a round figure. */
    val step: BigDecimal,
    /** Quick picks at an eighth, a quarter, half and three quarters of [max]. */
    val chips: List<BigDecimal>,
)

/** Weeks of history the slider's top is drawn from. */
internal const val GUESS_HISTORY_WEEKS = 4

/** A guess this close to the recorded figure, in percent of it, counts as a hit. */
const val CLOSE_GUESS_PERCENT = 15

/**
 * The question for the last of [weeks] (per currency, oldest first, ending with the week
 * asked about; Review's [CycleTotal]s), or null when that week has nothing to ask about.
 *
 * The category is [food] when it was spent on in any currency, otherwise the biggest
 * category that isn't one of [others]. The currency is [preferred] (Home's) when the
 * category was spent in it, otherwise the one with the most spending rows that week
 * ([rowsByCurrency]). Currencies are compared by rows, never by amount.
 */
internal fun weeklyGuessQuestion(
    weeks: Map<String, List<CycleTotal>>,
    rowsByCurrency: Map<String, Int>,
    food: String,
    others: Set<String>,
    preferred: String?,
): WeeklyGuessQuestion? {
    fun lastWeek(currency: String) = weeks[currency]?.lastOrNull()?.byCategory.orEmpty()
        .filter { (name, amount) -> name !in others && amount.signum() > 0 }

    fun pick(currencies: Collection<String>): String? = currencies.firstOrNull { it == preferred }
        ?: currencies.sortedWith(compareByDescending<String> { rowsByCurrency[it] ?: 0 }.thenBy { it }).firstOrNull()

    val currency = pick(weeks.keys.filter { food in lastWeek(it) })
    val (category, chosen) = if (currency != null) {
        food to currency
    } else {
        val chosen = pick(weeks.keys.filter { lastWeek(it).isNotEmpty() }) ?: return null
        val top = lastWeek(chosen).entries.sortedWith(compareByDescending<Map.Entry<String, BigDecimal>> { it.value }.thenBy { it.key })
        top.first().key to chosen
    }
    val totals = weeks.getValue(chosen)
    val week = totals.last()
    val actual = week.byCategory.getValue(category)
    val max = guessMax(actual, totals.dropLast(1).takeLast(GUESS_HISTORY_WEEKS).filter { it.hasActivity }.map { it.byCategory[category] ?: BigDecimal.ZERO })
    // A step fine enough that some pick lands within the hit band, even for a tiny week.
    val step = generateSequence(guessStep(max)) { it.divide(BigDecimal.TEN) }
        .first { it <= actual * BigDecimal("0.3") }.plain()
    return WeeklyGuessQuestion(
        weekStart = week.start,
        weekEnd = week.end,
        category = category,
        currency = chosen,
        actual = actual,
        max = max,
        step = step,
        chips = listOf("0.125", "0.25", "0.5", "0.75").map { snap(max * BigDecimal(it), step) }
            .filter { it.signum() > 0 }.distinct(),
    )
}

/**
 * Twice the average of [history] (the weeks before, each one the user was tracking, zero
 * where the category had nothing), or twice [actual] without any. Never below 1.5× [actual],
 * so the answer isn't pinned to the slider's end; rounded up to a [niceCeil] figure.
 */
internal fun guessMax(actual: BigDecimal, history: List<BigDecimal>): BigDecimal {
    val average = if (history.isEmpty()) BigDecimal.ZERO
    else history.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(history.size), 2, RoundingMode.HALF_UP)
    val base = if (average.signum() > 0) average else actual
    return niceCeil(maxOf(base * BigDecimal(2), actual * BigDecimal("1.5")))
}

/** The smallest of 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8 (times a power of ten) that holds [value]. */
internal fun niceCeil(value: BigDecimal): BigDecimal {
    if (value.signum() <= 0) return BigDecimal.ONE
    val magnitude = BigDecimal.ONE.movePointRight(value.precision() - value.scale() - 1)
    return NICE.map { magnitude * it }.first { it >= value }.plain()
}

private val NICE = listOf("1", "1.5", "2", "2.5", "3", "4", "5", "6", "8", "10").map(::BigDecimal)

/** A twentieth of [max]'s power of ten, and never a fraction of a unit: 5 for 200, 50 for 1,500. */
internal fun guessStep(max: BigDecimal): BigDecimal {
    val magnitude = BigDecimal.ONE.movePointRight(max.precision() - max.scale() - 1)
    return magnitude.divide(BigDecimal(20)).max(BigDecimal.ONE).plain()
}

/**
 * Most positions the guess slider may offer. Material 3 allocates one tick per position and
 * walks them on every drag; a tiny week after a large history would otherwise ask for
 * hundreds of millions and the allocation fails.
 */
internal const val MAX_SLIDER_POSITIONS = 1_000

/**
 * Where the thumb can sit, from 0 up to [max].
 *
 * An ordinary week is every multiple of [step]. When that would blow past [MAX_SLIDER_POSITIONS],
 * the stops are whole numbers across the same range, with one of them inside the 15% hit band
 * so a quiet week is still guessable.
 */
internal fun sliderStops(max: BigDecimal, step: BigDecimal, actual: BigDecimal): List<BigDecimal> {
    if (step.signum() <= 0 || max.signum() <= 0) return listOf(BigDecimal.ZERO)
    if (max <= step.multiply(BigDecimal(MAX_SLIDER_POSITIONS))) {
        val count = max.divide(step, 0, RoundingMode.DOWN).intValueExact()
        return (0..count).map { step.multiply(BigDecimal(it)).plain() }
    }
    val gaps = MAX_SLIDER_POSITIONS - 1
    val coarse = (0..gaps).map { i ->
        when (i) {
            0 -> BigDecimal.ZERO
            gaps -> max.plain()
            else -> max.multiply(BigDecimal(i)).divide(BigDecimal(gaps), 0, RoundingMode.HALF_UP).plain()
        }
    }.distinct()
    return withReachableHit(coarse, actual)
}

/** [stops] unchanged when one of them already hits; otherwise one interior stop moves into the band. */
private fun withReachableHit(stops: List<BigDecimal>, actual: BigDecimal): List<BigDecimal> {
    if (stops.any { isCloseGuess(it, actual) }) return stops
    val whole = actual.setScale(0, RoundingMode.HALF_UP).plain()
    val hit = if (whole.signum() > 0 && isCloseGuess(whole, actual)) whole else actual.plain()
    if (stops.size < 3) return (stops + hit).distinct().sorted()
    val index = (1 until stops.lastIndex).minBy { (stops[it] - hit).abs() }
    return stops.mapIndexed { i, value -> if (i == index) hit else value }.distinct().sorted()
}

/** [value] to the nearest [step]. */
internal fun snap(value: BigDecimal, step: BigDecimal): BigDecimal =
    value.divide(step, 0, RoundingMode.HALF_UP).multiply(step).plain()

/** Within [CLOSE_GUESS_PERCENT] of what was recorded. */
fun isCloseGuess(guess: BigDecimal, actual: BigDecimal): Boolean =
    (guess - actual).abs().multiply(BigDecimal(100)) <= actual * BigDecimal(CLOSE_GUESS_PERCENT)

/** How far off, as a whole percent of what was recorded. */
fun guessGapPercent(guess: BigDecimal, actual: BigDecimal): Int =
    (guess - actual).abs().multiply(BigDecimal(100)).divide(actual, 0, RoundingMode.HALF_UP).toInt()

/** No trailing zeros and no exponent: 200, not 2E+2, so figures compare and print plainly. */
private fun BigDecimal.plain(): BigDecimal = stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
