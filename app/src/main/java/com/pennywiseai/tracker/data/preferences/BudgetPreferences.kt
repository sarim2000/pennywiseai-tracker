package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * When the budget cycle starts. Backed by the single user_preferences DataStore through
 * [UserPreferencesRepository]; keys and defaults are pinned by the UserPreferences golden tests.
 */
interface BudgetPreferences {
    /** Day of the month, 1..31, or BudgetCycle.LAST_WORKING_DAY. Out-of-range stored values are clamped. */
    val budgetCycleStartDay: Flow<Int>

    suspend fun getBudgetCycleStartDay(): Int

    /** Also refreshes the widgets, which compute "this cycle" from this day. */
    suspend fun updateBudgetCycleStartDay(day: Int)
}
