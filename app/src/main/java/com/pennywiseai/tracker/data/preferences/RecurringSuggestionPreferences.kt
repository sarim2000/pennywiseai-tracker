package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Which recurring-bill suggestions were dismissed, and the caps on offering them. Backed by
 * the single user_preferences DataStore through [UserPreferencesRepository]; keys and defaults
 * are pinned by the UserPreferences golden tests.
 */
interface RecurringSuggestionPreferences {
    /** Suggestions marked "Not recurring"; they never come back. */
    val dismissedRecurringSuggestions: Flow<Set<String>>

    suspend fun addDismissedRecurringSuggestions(keys: Collection<String>)

    suspend fun removeDismissedRecurringSuggestion(key: String)

    /** Records the offer if the caps allow it (one per merchant per [perMerchantDays], one a day overall). */
    suspend fun tryRecordRecurringOffer(key: String, today: LocalDate, perMerchantDays: Long): Boolean

    /** Undoes [tryRecordRecurringOffer] for an offer that was never shown. */
    suspend fun releaseRecurringOffer(key: String, today: LocalDate)
}
