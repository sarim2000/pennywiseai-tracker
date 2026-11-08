package com.pennywiseai.tracker.data.preferences

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.edit
import com.pennywiseai.tracker.domain.model.BudgetCycle

/**
 * The country whose weekend a last-working-day budget cycle rolls back over. It's saved the
 * first time it's read and when the user picks that cycle, so a trip abroad with another SIM
 * doesn't move past cycles' dates.
 */
object WeekendCountryStore {
    private const val PREFS = "budget_cycle"
    private const val KEY = "weekend_country"

    /** At app start: the saved country, or the SIM's, saved from now on. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        BudgetCycle.weekendCountry = prefs.getString(KEY, null) ?: refresh(context)
    }

    /** When the user picks the last-working-day cycle: today's SIM country becomes the one used. */
    fun refresh(context: Context): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val country = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, country) }
        BudgetCycle.weekendCountry = country
        return country
    }
}
