package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.core.TimeConstants
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing app lock state and authentication.
 */
@Singleton
class AppLockRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {

    /**
     * Flow indicating if app lock is enabled
     */
    val isAppLockEnabled: Flow<Boolean> = userPreferencesRepository.isAppLockEnabled

    /**
     * Flow indicating timeout duration in minutes
     */
    val timeoutMinutes: Flow<Int> = userPreferencesRepository.appLockTimeoutMinutes

    /**
     * Whether the user unlocked (or changed a lock setting, which counts as one)
     * since the app last left the screen. The "Immediately" timeout locks
     * whenever it's false. In memory, so a new process starts locked.
     */
    private val unlockedSinceBackground = MutableStateFlow(false)

    /** Every PennyWise screen has left the foreground (not a rotation). */
    fun onAppBackgrounded() {
        unlockedSinceBackground.value = false
    }

    /**
     * Enable or disable app lock
     * Uses atomic update to prevent race conditions
     */
    suspend fun setAppLockEnabled(enabled: Boolean) {
        // Before the write, so "Immediately" doesn't lock the screen it was set on.
        unlockedSinceBackground.value = true
        // Use atomic update to set both enabled and timestamp together
        userPreferencesRepository.setAppLockEnabledWithTimestamp(enabled)
    }

    /**
     * Set the timeout duration in minutes
     * 0 = immediately, 1 = 1 minute, 5 = 5 minutes, etc.
     * Also updates auth timestamp to prevent immediate lock
     */
    suspend fun setTimeoutMinutes(minutes: Int) {
        unlockedSinceBackground.value = true
        // Use atomic update to set both timeout and timestamp together
        userPreferencesRepository.setAppLockTimeoutWithTimestamp(minutes)
    }

    /** True the first time only; see [UserPreferencesRepository.claimAppLockNoScreenLockNotice]. */
    suspend fun claimNoScreenLockNotice(): Boolean = userPreferencesRepository.claimAppLockNoScreenLockNotice()

    /**
     * Update the last authentication timestamp to current time
     */
    suspend fun updateAuthTimestamp() {
        unlockedSinceBackground.value = true
        userPreferencesRepository.setLastAuthTimestamp(System.currentTimeMillis())
    }

    /**
     * Check if the app should be locked based on timeout
     * Returns true if app lock is enabled and timeout has elapsed
     */
    suspend fun shouldLockApp(): Boolean = shouldLock(
        isEnabled = userPreferencesRepository.isAppLockEnabled.first(),
        timeoutMinutes = userPreferencesRepository.getAppLockTimeoutMinutes(),
        lastAuthTimestamp = userPreferencesRepository.getLastAuthTimestamp(),
        unlockedSinceBackground = unlockedSinceBackground.value,
        now = System.currentTimeMillis()
    )

    /**
     * Flow that emits whether the app should be locked
     * Combines app lock enabled state with timeout calculation
     */
    fun shouldLockAppFlow(): Flow<Boolean> = combine(
        // Distinct: every DataStore write re-emits these, and re-checking then would
        // lock mid-use once a timeout has passed since the unlock (a settings toggle).
        userPreferencesRepository.isAppLockEnabled.distinctUntilChanged(),
        userPreferencesRepository.appLockTimeoutMinutes.distinctUntilChanged(),
        userPreferencesRepository.getLastAuthTimestampFlow().distinctUntilChanged(),
        unlockedSinceBackground
    ) { isEnabled, timeoutMinutes, lastAuthTimestamp, unlocked ->
        shouldLock(isEnabled, timeoutMinutes, lastAuthTimestamp, unlocked, System.currentTimeMillis())
    }

    /**
     * Get the configured timeout in minutes
     */
    suspend fun getTimeoutMinutes(): Int {
        return userPreferencesRepository.getAppLockTimeoutMinutes()
    }
}

/**
 * "Immediately" (timeout 0) locks each time the app comes back from the
 * background; any other timeout locks once that long has passed since the last
 * unlock.
 */
internal fun shouldLock(
    isEnabled: Boolean,
    timeoutMinutes: Int,
    lastAuthTimestamp: Long,
    unlockedSinceBackground: Boolean,
    now: Long
): Boolean {
    if (!isEnabled) return false
    if (timeoutMinutes == 0) return !unlockedSinceBackground
    // No unlock recorded yet
    if (lastAuthTimestamp == 0L) return true
    return now - lastAuthTimestamp >= timeoutMinutes * TimeConstants.MILLIS_PER_MINUTE
}
