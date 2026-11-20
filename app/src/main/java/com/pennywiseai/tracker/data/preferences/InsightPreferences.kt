package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Insights the user dismissed, by their ids. An id names its cycle, so a dismissal
 * ends with the cycle: ids from an older cycle are dropped on the next dismissal.
 * Backed by the single user_preferences DataStore through [UserPreferencesRepository].
 */
interface InsightPreferences {
    val dismissedInsights: Flow<Set<String>>

    suspend fun dismissInsight(id: String)

    /** Undo, or "show dismissed" bringing one back. */
    suspend fun restoreInsight(id: String)
}
