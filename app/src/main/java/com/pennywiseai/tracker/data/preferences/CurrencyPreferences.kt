package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Base and display currency. Backed by the single user_preferences DataStore through
 * [UserPreferencesRepository]; keys and defaults are pinned by the UserPreferences golden tests.
 */
interface CurrencyPreferences {
    val baseCurrency: Flow<String>
    val unifiedCurrencyMode: Flow<Boolean>

    /** Falls back to [baseCurrency] until the user picks one. */
    val displayCurrency: Flow<String>

    /** An explicit choice: from then on [applyMainAccountCurrency] can't override it. */
    suspend fun updateBaseCurrency(currency: String)

    /** Sets the base currency from the main account, unless the user already chose one. */
    suspend fun applyMainAccountCurrency(currency: String)

    suspend fun setUnifiedCurrencyMode(enabled: Boolean)

    suspend fun setDisplayCurrency(currency: String)
}
