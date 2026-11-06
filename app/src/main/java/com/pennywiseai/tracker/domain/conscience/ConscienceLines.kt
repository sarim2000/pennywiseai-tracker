package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.R
import java.time.DayOfWeek
import java.util.Locale

/** A hand-written line. [id] is stable: the no-repeat log and the feedback store it. */
data class ConscienceLine(val id: String, val res: Int)

/**
 * The words a line's placeholders take, already in the app language. Kept
 * apart from Android so the rendering rules are plain functions.
 */
interface ConscienceWords {
    val locale: Locale
    fun days(days: Int): String
    fun hours(hours: Int): String
    fun category(category: ConscienceCategory): String
    fun weekday(day: DayOfWeek): String
    fun percent(percent: Int): String
}

/**
 * Every line the conscience can say lives in res/values-* as
 * conscience_line_<pool>_<nn>, so the voice follows the app language.
 *
 * Writing rules: a line states only what its [ConscienceFact] proved, never an
 * amount or a merchant. Food & Dining covers eating out and coffee too, so food
 * lines never claim the user ordered in. A language that marks the user's gender
 * writes `{masculine|feminine}` where the forms differ. Allowed placeholders:
 * {days} {hours} {count} {category} {weekday} {percent}. Ids are stored: never
 * reuse or renumber one.
 */
object ConscienceLines {

    fun pool(fact: ConscienceFact): List<ConscienceLine> = when (fact) {
        is ConscienceFact.Comeback -> if (fact.category == ConscienceCategory.FOOD) COMEBACK_FOOD else COMEBACK
        is ConscienceFact.RecordDay -> RECORD_DAY
        is ConscienceFact.Repeat -> if (fact.category == ConscienceCategory.FOOD) REPEAT_FOOD else REPEAT
        is ConscienceFact.LateNight -> if (fact.category == ConscienceCategory.FOOD) LATE_NIGHT_FOOD else LATE_NIGHT
        is ConscienceFact.Spike -> SPIKE
        is ConscienceFact.Positive -> POSITIVE
        is ConscienceFact.Weekly -> WEEKLY
        is ConscienceFact.Hello -> if (fact.category == ConscienceCategory.FOOD) HELLO_FOOD else HELLO
    }

    val all: List<ConscienceLine> by lazy {
        listOf(
            COMEBACK_FOOD, COMEBACK, RECORD_DAY, REPEAT_FOOD, REPEAT, LATE_NIGHT_FOOD, LATE_NIGHT,
            SPIKE, POSITIVE, WEEKLY, HELLO_FOOD, HELLO,
        ).flatten()
    }

    /** The key the logs store: the same joke in another language is a different line. */
    fun key(contentLocale: String, line: ConscienceLine): String = "$contentLocale:${line.id}"

    /** One fact of every kind, for the screen's examples and the sample notification. */
    val sampleFacts: List<ConscienceFact> = listOf(
        ConscienceFact.Comeback(ConscienceCategory.FOOD, 5),
        ConscienceFact.Repeat(ConscienceCategory.SHOPPING, 3, 6),
        ConscienceFact.Positive(ConscienceCategory.FOOD, 25, 0),
        ConscienceFact.LateNight(ConscienceCategory.ENTERTAINMENT),
        ConscienceFact.Weekly(ConscienceCategory.FOOD, DayOfWeek.THURSDAY),
        ConscienceFact.RecordDay(ConscienceCategory.SHOPPING),
        ConscienceFact.Spike(ConscienceCategory.ENTERTAINMENT),
        ConscienceFact.Hello(ConscienceCategory.SHOPPING),
    )

    /** The [index]th example: walks every kind before it repeats one, then moves along each pool. */
    fun sample(index: Int): Pair<ConscienceFact, ConscienceLine> {
        val safe = index.mod(Int.MAX_VALUE)
        val fact = sampleFacts[safe % sampleFacts.size]
        val pool = pool(fact)
        return fact to pool[(safe / sampleFacts.size) % pool.size]
    }

    private val ALTERNATION = Regex("""\{([^{}|]*)\|([^{}|]*)\}""")
    private val PLACEHOLDER = Regex("""\{(days|hours|count|category|weekday|percent)\}""")

    // Not after "…": several languages carry on lowercase after an ellipsis.
    private val SENTENCE_START = Regex("""(^|[.!?]\s+)$""")

    /** Whether any of these templates marks the user's gender, so the address choice matters. */
    fun marksAddress(templates: Iterable<String>): Boolean = templates.any { ALTERNATION.containsMatchIn(it) }

    /**
     * Fills [template] for [fact]. Null when anything is left unresolved, so a
     * broken translation is skipped rather than shown.
     */
    fun render(template: String, fact: ConscienceFact, feminine: Boolean, words: ConscienceWords): String? {
        val addressed = ALTERNATION.replace(template) { match ->
            if (feminine) match.groupValues[2] else match.groupValues[1]
        }
        var unresolved = false
        val filled = PLACEHOLDER.replace(addressed) { match ->
            val value = when (match.groupValues[1]) {
                "category" -> words.category(fact.category)
                "days" -> (fact as? ConscienceFact.Comeback)?.let { words.days(it.days) }
                "hours" -> (fact as? ConscienceFact.Repeat)?.let { words.hours(it.hours) }
                "count" -> (fact as? ConscienceFact.Repeat)?.count?.toString()
                "weekday" -> (fact as? ConscienceFact.Weekly)?.let { words.weekday(it.weekday) }
                "percent" -> (fact as? ConscienceFact.Positive)?.let { words.percent(it.percent) }
                else -> null
            }
            if (value == null) unresolved = true
            val startsSentence = SENTENCE_START.containsMatchIn(addressed.substring(0, match.range.first))
            if (startsSentence) value.orEmpty().replaceFirstChar { it.titlecase(words.locale) } else value.orEmpty()
        }
        if (unresolved || '{' in filled || '}' in filled || filled.isBlank()) return null
        return filled.replaceFirstChar { if (it.isLowerCase()) it.titlecase(words.locale) else it.toString() }
    }

    private val COMEBACK_FOOD = listOf(
        ConscienceLine("comeback_food_01", R.string.conscience_line_comeback_food_01),
        ConscienceLine("comeback_food_02", R.string.conscience_line_comeback_food_02),
        ConscienceLine("comeback_food_03", R.string.conscience_line_comeback_food_03),
        ConscienceLine("comeback_food_04", R.string.conscience_line_comeback_food_04),
        ConscienceLine("comeback_food_05", R.string.conscience_line_comeback_food_05),
        ConscienceLine("comeback_food_06", R.string.conscience_line_comeback_food_06),
        ConscienceLine("comeback_food_07", R.string.conscience_line_comeback_food_07),
        ConscienceLine("comeback_food_08", R.string.conscience_line_comeback_food_08),
        ConscienceLine("comeback_food_09", R.string.conscience_line_comeback_food_09),
        ConscienceLine("comeback_food_10", R.string.conscience_line_comeback_food_10),
        ConscienceLine("comeback_food_11", R.string.conscience_line_comeback_food_11),
        ConscienceLine("comeback_food_12", R.string.conscience_line_comeback_food_12),
    )

    private val COMEBACK = listOf(
        ConscienceLine("comeback_01", R.string.conscience_line_comeback_01),
        ConscienceLine("comeback_02", R.string.conscience_line_comeback_02),
        ConscienceLine("comeback_03", R.string.conscience_line_comeback_03),
        ConscienceLine("comeback_04", R.string.conscience_line_comeback_04),
        ConscienceLine("comeback_05", R.string.conscience_line_comeback_05),
        ConscienceLine("comeback_06", R.string.conscience_line_comeback_06),
        ConscienceLine("comeback_07", R.string.conscience_line_comeback_07),
        ConscienceLine("comeback_08", R.string.conscience_line_comeback_08),
        ConscienceLine("comeback_09", R.string.conscience_line_comeback_09),
        ConscienceLine("comeback_10", R.string.conscience_line_comeback_10),
        ConscienceLine("comeback_11", R.string.conscience_line_comeback_11),
        ConscienceLine("comeback_12", R.string.conscience_line_comeback_12),
    )

    private val RECORD_DAY = listOf(
        ConscienceLine("record_day_01", R.string.conscience_line_record_day_01),
        ConscienceLine("record_day_02", R.string.conscience_line_record_day_02),
        ConscienceLine("record_day_03", R.string.conscience_line_record_day_03),
        ConscienceLine("record_day_04", R.string.conscience_line_record_day_04),
        ConscienceLine("record_day_05", R.string.conscience_line_record_day_05),
        ConscienceLine("record_day_06", R.string.conscience_line_record_day_06),
        ConscienceLine("record_day_07", R.string.conscience_line_record_day_07),
        ConscienceLine("record_day_08", R.string.conscience_line_record_day_08),
        ConscienceLine("record_day_09", R.string.conscience_line_record_day_09),
        ConscienceLine("record_day_10", R.string.conscience_line_record_day_10),
        ConscienceLine("record_day_11", R.string.conscience_line_record_day_11),
        ConscienceLine("record_day_12", R.string.conscience_line_record_day_12),
    )

    private val REPEAT_FOOD = listOf(
        ConscienceLine("repeat_food_01", R.string.conscience_line_repeat_food_01),
        ConscienceLine("repeat_food_02", R.string.conscience_line_repeat_food_02),
        ConscienceLine("repeat_food_03", R.string.conscience_line_repeat_food_03),
        ConscienceLine("repeat_food_04", R.string.conscience_line_repeat_food_04),
        ConscienceLine("repeat_food_05", R.string.conscience_line_repeat_food_05),
        ConscienceLine("repeat_food_06", R.string.conscience_line_repeat_food_06),
    )

    private val REPEAT = listOf(
        ConscienceLine("repeat_01", R.string.conscience_line_repeat_01),
        ConscienceLine("repeat_02", R.string.conscience_line_repeat_02),
        ConscienceLine("repeat_03", R.string.conscience_line_repeat_03),
        ConscienceLine("repeat_04", R.string.conscience_line_repeat_04),
        ConscienceLine("repeat_05", R.string.conscience_line_repeat_05),
        ConscienceLine("repeat_06", R.string.conscience_line_repeat_06),
    )

    private val LATE_NIGHT_FOOD = listOf(
        ConscienceLine("late_night_food_01", R.string.conscience_line_late_night_food_01),
        ConscienceLine("late_night_food_02", R.string.conscience_line_late_night_food_02),
        ConscienceLine("late_night_food_03", R.string.conscience_line_late_night_food_03),
        ConscienceLine("late_night_food_04", R.string.conscience_line_late_night_food_04),
        ConscienceLine("late_night_food_05", R.string.conscience_line_late_night_food_05),
        ConscienceLine("late_night_food_06", R.string.conscience_line_late_night_food_06),
    )

    private val LATE_NIGHT = listOf(
        ConscienceLine("late_night_01", R.string.conscience_line_late_night_01),
        ConscienceLine("late_night_02", R.string.conscience_line_late_night_02),
        ConscienceLine("late_night_03", R.string.conscience_line_late_night_03),
        ConscienceLine("late_night_04", R.string.conscience_line_late_night_04),
        ConscienceLine("late_night_05", R.string.conscience_line_late_night_05),
        ConscienceLine("late_night_06", R.string.conscience_line_late_night_06),
    )

    private val SPIKE = listOf(
        ConscienceLine("spike_01", R.string.conscience_line_spike_01),
        ConscienceLine("spike_02", R.string.conscience_line_spike_02),
        ConscienceLine("spike_03", R.string.conscience_line_spike_03),
        ConscienceLine("spike_04", R.string.conscience_line_spike_04),
        ConscienceLine("spike_05", R.string.conscience_line_spike_05),
        ConscienceLine("spike_06", R.string.conscience_line_spike_06),
    )

    private val POSITIVE = listOf(
        ConscienceLine("positive_01", R.string.conscience_line_positive_01),
        ConscienceLine("positive_02", R.string.conscience_line_positive_02),
        ConscienceLine("positive_03", R.string.conscience_line_positive_03),
        ConscienceLine("positive_04", R.string.conscience_line_positive_04),
        ConscienceLine("positive_05", R.string.conscience_line_positive_05),
        ConscienceLine("positive_06", R.string.conscience_line_positive_06),
        ConscienceLine("positive_07", R.string.conscience_line_positive_07),
        ConscienceLine("positive_08", R.string.conscience_line_positive_08),
    )

    private val WEEKLY = listOf(
        ConscienceLine("weekly_01", R.string.conscience_line_weekly_01),
        ConscienceLine("weekly_02", R.string.conscience_line_weekly_02),
        ConscienceLine("weekly_03", R.string.conscience_line_weekly_03),
        ConscienceLine("weekly_04", R.string.conscience_line_weekly_04),
        ConscienceLine("weekly_05", R.string.conscience_line_weekly_05),
        ConscienceLine("weekly_06", R.string.conscience_line_weekly_06),
    )

    private val HELLO_FOOD = listOf(
        ConscienceLine("hello_food_01", R.string.conscience_line_hello_food_01),
        ConscienceLine("hello_food_02", R.string.conscience_line_hello_food_02),
        ConscienceLine("hello_food_03", R.string.conscience_line_hello_food_03),
        ConscienceLine("hello_food_04", R.string.conscience_line_hello_food_04),
        ConscienceLine("hello_food_05", R.string.conscience_line_hello_food_05),
        ConscienceLine("hello_food_06", R.string.conscience_line_hello_food_06),
    )

    private val HELLO = listOf(
        ConscienceLine("hello_01", R.string.conscience_line_hello_01),
        ConscienceLine("hello_02", R.string.conscience_line_hello_02),
        ConscienceLine("hello_03", R.string.conscience_line_hello_03),
        ConscienceLine("hello_04", R.string.conscience_line_hello_04),
        ConscienceLine("hello_05", R.string.conscience_line_hello_05),
        ConscienceLine("hello_06", R.string.conscience_line_hello_06),
    )
}
