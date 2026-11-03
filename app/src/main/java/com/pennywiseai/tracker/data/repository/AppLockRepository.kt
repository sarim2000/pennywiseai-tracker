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
     * Whether the app is unlocked since it last came on screen: set by an unlock,
     * a lock setting change (which counts as one) or a check that finds the
     * timeout hasn't run out; cleared when the app leaves the screen. Nothing
     * locks while it's true. In memory, so a new process starts from the
     * persisted timestamp.
     */
    private val unlockedSinceBackground = MutableStateFlow(false)

    /**
     * Every PennyWise screen has left the foreground (not a rotation). Leaving
     * unlocked restarts the timeout: "After 5 minutes" counts from here, not
     * from the unlock. The timestamp is persisted first, so a check in between
     * never sees the flag cleared against the old time.
     */
    suspend fun onAppBackgrounded() {
        if (!unlockedSinceBackground.value) return
        if (userPreferencesRepository.isAppLockEnabled.first()) {
            userPreferencesRepository.setLastAuthTimestamp(System.currentTimeMillis())
        }
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
     * Check if the app should be locked based on timeout. Called as a screen
     * comes up: when the timeout hasn't run out, the app is unlocked from here
     * on, so staying on screen past it doesn't lock.
     */
    suspend fun shouldLockApp(): Boolean {
        val locked = shouldLock(
            isEnabled = userPreferencesRepository.isAppLockEnabled.first(),
            timeoutMinutes = userPreferencesRepository.getAppLockTimeoutMinutes(),
            lastAuthTimestamp = userPreferencesRepository.getLastAuthTimestamp(),
            unlockedSinceBackground = unlockedSinceBackground.value,
            now = System.currentTimeMillis()
        )
        if (!locked) unlockedSinceBackground.value = true
        return locked
    }

    /**
     * Flow that emits whether the app should be locked
     * Combines app lock enabled state with timeout calculation
     */
    fun shouldLockAppFlow(): Flow<Boolean> = lockStateFlow(
        userPreferencesRepository.isAppLockEnabled,
        userPreferencesRepository.appLockTimeoutMinutes,
        userPreferencesRepository.getLastAuthTimestampFlow(),
        unlockedSinceBackground
    )

    /**
     * Get the configured timeout in minutes
     */
    suspend fun getTimeoutMinutes(): Int {
        return userPreferencesRepository.getAppLockTimeoutMinutes()
    }
}

/**
 * [shouldLock] as a flow. Re-checks only when an input really changes, since
 * every DataStore write re-emits the settings. While the app is unlocked on
 * screen the flag keeps it unlocked whatever the other inputs say, so the
 * separate DataStore values of one settings write arriving one at a time can't
 * lock it mid-change.
 */
internal fun lockStateFlow(
    isEnabled: Flow<Boolean>,
    timeoutMinutes: Flow<Int>,
    lastAuthTimestamp: Flow<Long>,
    unlockedSinceBackground: Flow<Boolean>,
    now: () -> Long = System::currentTimeMillis
): Flow<Boolean> = combine(
    isEnabled.distinctUntilChanged(),
    timeoutMinutes.distinctUntilChanged(),
    lastAuthTimestamp.distinctUntilChanged(),
    unlockedSinceBackground
) { enabled, timeout, lastAuth, unlocked -> shouldLock(enabled, timeout, lastAuth, unlocked, now()) }

/**
 * Never locks while the app is unlocked on screen. Once it has left: with
 * "Immediately" (timeout 0) it locks on return; otherwise once that long has
 * passed since it was last unlocked on screen ([lastAuthTimestamp], refreshed
 * on leaving), or if the clock now reads earlier than that.
 */
internal fun shouldLock(
    isEnabled: Boolean,
    timeoutMinutes: Int,
    lastAuthTimestamp: Long,
    unlockedSinceBackground: Boolean,
    now: Long
): Boolean {
    if (!isEnabled || unlockedSinceBackground) return false
    if (timeoutMinutes == 0) return true
    // No unlock recorded yet
    if (lastAuthTimestamp == 0L) return true
    val away = now - lastAuthTimestamp
    return away < 0 || away >= timeoutMinutes * TimeConstants.MILLIS_PER_MINUTE
}
