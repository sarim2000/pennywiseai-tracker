package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.R
import java.util.Locale

/** A hand-written line. [id] is stable: the no-repeat log and the retired set store it. */
data class ConscienceLine(val id: String, val res: Int)

/**
 * The words a line's placeholders take, already in the app language. Kept
 * apart from Android so the rendering rules are plain functions.
 */
interface ConscienceWords {
    val locale: Locale
    fun hours(hours: Int): String
    fun category(category: ConscienceCategory): String
    fun percent(percent: Int): String
}

/**
 * Every line the conscience can say lives in a conscience_lines.xml resource as
 * conscience_line_<pool>_<nn>. That file stays out of Crowdin, so a language
 * exists only once a speaker has written and read its lines by hand.
 *
 * Writing rules: a line states only what its [ConscienceFact] proved, never an
 * amount or a merchant. Food & Dining covers eating out and coffee too, so food
 * lines never claim the user ordered in. Lines don't mark the user's gender.
 * Allowed placeholders: {hours} {count} {category} {percent}. Ids are stored:
 * never reuse or renumber one.
 */
object ConscienceLines {

    fun pool(fact: ConscienceFact): List<ConscienceLine> = when (fact) {
        is ConscienceFact.Repeat -> if (fact.category == ConscienceCategory.FOOD) REPEAT_FOOD else REPEAT
        is ConscienceFact.LateNight -> if (fact.category == ConscienceCategory.FOOD) LATE_NIGHT_FOOD else LATE_NIGHT
        is ConscienceFact.Positive -> POSITIVE
    }

    val all: List<ConscienceLine> by lazy {
        listOf(REPEAT_FOOD, REPEAT, LATE_NIGHT_FOOD, LATE_NIGHT, POSITIVE).flatten()
    }

    /** The key the logs store: the same joke in another language is a different line. */
    fun key(contentLocale: String, line: ConscienceLine): String = "$contentLocale:${line.id}"

    /**
     * Whether the lines are written in the app's language. In a language without
     * its own lines, Android falls back to the English ones, and the feature hides
     * rather than tease someone in a language they didn't choose.
     */
    fun speaks(contentLocale: String, appLanguage: String): Boolean =
        Locale.forLanguageTag(contentLocale).language == appLanguage

    private val PLACEHOLDER = Regex("""\{(hours|count|category|percent)\}""")

    // Not after "…": several languages carry on lowercase after an ellipsis.
    private val SENTENCE_START = Regex("""(^|[.!?]\s+)$""")

    /**
     * Fills [template] for [fact]. Null when anything is left unresolved, so a
     * broken line is skipped rather than shown.
     */
    fun render(template: String, fact: ConscienceFact, words: ConscienceWords): String? {
        var unresolved = false
        val filled = PLACEHOLDER.replace(template) { match ->
            val value = when (match.groupValues[1]) {
                "category" -> words.category(fact.category)
                "hours" -> (fact as? ConscienceFact.Repeat)?.let { words.hours(it.hours) }
                "count" -> (fact as? ConscienceFact.Repeat)?.count?.toString()
                "percent" -> (fact as? ConscienceFact.Positive)?.let { words.percent(it.percent) }
                else -> null
            }
            if (value == null) unresolved = true
            val startsSentence = SENTENCE_START.containsMatchIn(template.substring(0, match.range.first))
            if (startsSentence) value.orEmpty().replaceFirstChar { it.titlecase(words.locale) } else value.orEmpty()
        }
        if (unresolved || '{' in filled || '}' in filled || filled.isBlank()) return null
        return filled.replaceFirstChar { if (it.isLowerCase()) it.titlecase(words.locale) else it.toString() }
    }

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
}
