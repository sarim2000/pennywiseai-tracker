package com.pennywiseai.tracker.data.manager

import android.content.Context
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.conscience.ConscienceCandidate
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory
import com.pennywiseai.tracker.domain.conscience.ConscienceFact
import com.pennywiseai.tracker.domain.conscience.ConscienceLine
import com.pennywiseai.tracker.domain.conscience.ConscienceLines
import com.pennywiseai.tracker.domain.conscience.ConscienceWords
import com.pennywiseai.tracker.widget.inAppLanguage
import java.util.Locale

/**
 * The conscience's words in the app language, which can differ from the
 * device's. Build one per use: the language can change while the process lives.
 */
class ConscienceText(context: Context) {
    private val localized: Context = context.inAppLanguage()

    /** The language the lines actually came from, after any fallback to English. */
    val contentLocale: String = localized.getString(R.string.conscience_content_locale)

    /** False in an app language with no lines of its own: the toggle hides and nothing fires. */
    val speaksAppLanguage: Boolean =
        ConscienceLines.speaks(contentLocale, localized.resources.configuration.locales[0].language)

    private val words = object : ConscienceWords {
        override val locale: Locale = Locale.forLanguageTag(contentLocale)
        override fun hours(hours: Int) =
            localized.resources.getQuantityString(R.plurals.conscience_hours, hours, hours.toString())
        override fun category(category: ConscienceCategory) = localized.getString(
            when (category) {
                ConscienceCategory.FOOD -> R.string.conscience_category_food
                ConscienceCategory.ENTERTAINMENT -> R.string.conscience_category_entertainment
                ConscienceCategory.SHOPPING -> R.string.conscience_category_shopping
            }
        )
        override fun percent(percent: Int) = localized.getString(R.string.conscience_percent, percent.toString())
    }

    fun render(fact: ConscienceFact, line: ConscienceLine): String? =
        ConscienceLines.render(localized.getString(line.res), fact, words)

    /** The fact with only the lines that render in this language. */
    fun candidate(fact: ConscienceFact): ConscienceCandidate =
        ConscienceCandidate(fact, ConscienceLines.pool(fact).filter { render(fact, it) != null })

    fun string(res: Int): String = localized.getString(res)
}
