package com.pennywiseai.tracker.data.manager

import android.app.GrammaticalInflectionManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.conscience.ConscienceAddress
import com.pennywiseai.tracker.domain.conscience.ConscienceCandidate
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory
import com.pennywiseai.tracker.domain.conscience.ConscienceFact
import com.pennywiseai.tracker.domain.conscience.ConscienceLine
import com.pennywiseai.tracker.domain.conscience.ConscienceLines
import com.pennywiseai.tracker.domain.conscience.ConscienceWords
import com.pennywiseai.tracker.widget.inAppLanguage
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The conscience's words in the app language, which can differ from the
 * device's. Build one per use: the language can change while the process lives.
 */
class ConscienceText(context: Context) {
    val localized: Context = context.inAppLanguage()

    /** The language the lines actually came from, after any fallback to English. */
    val contentLocale: String = localized.getString(R.string.conscience_content_locale)

    private val words = object : ConscienceWords {
        // The lines' own language, not the configured one: a fallback to English must not get another language's weekdays.
        override val locale: Locale = Locale.forLanguageTag(contentLocale)
        override fun days(days: Int) =
            localized.resources.getQuantityString(R.plurals.conscience_days, days, days.toString())
        override fun hours(hours: Int) =
            localized.resources.getQuantityString(R.plurals.conscience_hours, hours, hours.toString())
        override fun category(category: ConscienceCategory) = localized.getString(
            when (category) {
                ConscienceCategory.FOOD -> R.string.conscience_category_food
                ConscienceCategory.ENTERTAINMENT -> R.string.conscience_category_entertainment
                ConscienceCategory.SHOPPING -> R.string.conscience_category_shopping
            }
        )
        override fun weekday(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        override fun percent(percent: Int) = localized.getString(R.string.conscience_percent, percent.toString())
    }

    fun template(line: ConscienceLine): String = localized.getString(line.res)

    fun render(fact: ConscienceFact, line: ConscienceLine, address: ConscienceAddress): String? =
        ConscienceLines.render(template(line), fact, isFeminine(address), words)

    /** The fact with only the lines that render in this language. */
    fun candidate(fact: ConscienceFact, address: ConscienceAddress): ConscienceCandidate =
        ConscienceCandidate(fact, ConscienceLines.pool(fact).filter { render(fact, it, address) != null })

    /** Whether this language's lines change with the user's gender, so the choice is worth showing. */
    fun marksAddress(): Boolean = ConscienceLines.marksAddress(ConscienceLines.all.map(::template))

    fun string(res: Int): String = localized.getString(res)

    private fun isFeminine(address: ConscienceAddress): Boolean = when (address) {
        ConscienceAddress.FEMININE -> true
        ConscienceAddress.MASCULINE -> false
        ConscienceAddress.AUTO -> systemSaysFeminine()
    }

    /** Android 14's per-app grammatical gender, when the user set one. */
    private fun systemSaysFeminine(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val manager = localized.getSystemService(GrammaticalInflectionManager::class.java) ?: return false
        return manager.applicationGrammaticalGender == Configuration.GRAMMATICAL_GENDER_FEMININE
    }
}
