package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Hidden amounts and App Lock. Backed by the single user_preferences DataStore through
 * [UserPreferencesRepository]; keys and defaults are pinned by the UserPreferences golden tests.
 */
interface PrivacyPreferences {
    /** What Home shows: hidden until the user reveals it. */
    val isBalanceHidden: Flow<Boolean>

    /** What every other screen shows: only a saved choice masks them. */
    val isAmountHiddenEverywhere: Flow<Boolean>

    /**
     * Flips the saved choice in one read-modify-write and returns the new value.
     * [hiddenWhenUnset] is what the caller was showing before any choice was saved.
     */
    suspend fun toggleBalanceHidden(hiddenWhenUnset: Boolean = true): Boolean

    val shakeToHideAmounts: Flow<Boolean>

    suspend fun setShakeToHideAmounts(enabled: Boolean)

    /** True only the first time it's called. */
    suspend fun claimShakeToHideHint(): Boolean

    val isAppLockEnabled: Flow<Boolean>

    suspend fun setAppLockEnabled(enabled: Boolean)

    /** Saves the switch and, when enabling, the auth time in one edit, so no reader sees enabled with no auth time. */
    suspend fun setAppLockEnabledWithTimestamp(enabled: Boolean)

    val appLockTimeoutMinutes: Flow<Int>

    suspend fun getAppLockTimeoutMinutes(): Int

    /** Saves the timeout and resets the auth time in one edit, so changing it doesn't lock at once. */
    suspend fun setAppLockTimeoutWithTimestamp(minutes: Int)

    suspend fun getLastAuthTimestamp(): Long

    fun getLastAuthTimestampFlow(): Flow<Long>

    suspend fun setLastAuthTimestamp(timestamp: Long)

    /** True only the first time it's called. */
    suspend fun claimAppLockNoScreenLockNotice(): Boolean
}
