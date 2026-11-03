package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.core.TimeConstants
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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

    /** In memory, so a new process starts from the persisted timestamp alone. */
    private val session = MutableStateFlow(LockSession())

    /** The app is on screen and unlocked; nothing locks while it is. */
    val unlockedOnScreen: Flow<Boolean> = session.map { it.unlocked }.distinctUntilChanged()

    /**
     * Every PennyWise screen has left the foreground (not a rotation). Ends the
     * session at once, so a quick return or a check still running can't see it
     * as unlocked. Returns the time to persist with [recordLeftAt] when it was
     * left unlocked: a timeout counts from there after the process is gone.
     */
    fun onAppBackgrounded(): Long? {
        val now = System.currentTimeMillis()
        return now.takeIf { session.getAndUpdate { it.left(now) }.unlocked }
    }

    suspend fun recordLeftAt(time: Long) {
        if (userPreferencesRepository.isAppLockEnabled.first()) {
            userPreferencesRepository.setLastAuthTimestamp(time)
        }
    }

    /**
     * Enable or disable app lock
     * Uses atomic update to prevent race conditions
     */
    suspend fun setAppLockEnabled(enabled: Boolean) {
        // Before the write, so "Immediately" doesn't lock the screen it was set on.
        session.update { it.unlockedNow() }
        // Use atomic update to set both enabled and timestamp together
        userPreferencesRepository.setAppLockEnabledWithTimestamp(enabled)
    }

    /**
     * Set the timeout duration in minutes
     * 0 = immediately, 1 = 1 minute, 5 = 5 minutes, etc.
     * Also updates auth timestamp to prevent immediate lock
     */
    suspend fun setTimeoutMinutes(minutes: Int) {
        session.update { it.unlockedNow() }
        // Use atomic update to set both timeout and timestamp together
        userPreferencesRepository.setAppLockTimeoutWithTimestamp(minutes)
    }

    /** True the first time only; see [UserPreferencesRepository.claimAppLockNoScreenLockNotice]. */
    suspend fun claimNoScreenLockNotice(): Boolean = userPreferencesRepository.claimAppLockNoScreenLockNotice()

    /**
     * Update the last authentication timestamp to current time
     */
    suspend fun updateAuthTimestamp() {
        session.update { it.unlockedNow() }
        userPreferencesRepository.setLastAuthTimestamp(System.currentTimeMillis())
    }

    /**
     * Check if the app should be locked based on timeout. Called as a screen
     * comes up: when the timeout hasn't run out, the session resumes, so staying
     * on screen past it doesn't lock. Not if the app left while this was reading.
     */
    suspend fun shouldLockApp(): Boolean {
        val trip = session.value.trips
        val isEnabled = userPreferencesRepository.isAppLockEnabled.first()
        val timeoutMinutes = userPreferencesRepository.getAppLockTimeoutMinutes()
        val lastAuthTimestamp = userPreferencesRepository.getLastAuthTimestamp()
        val current = session.value
        val locked = shouldLock(isEnabled, timeoutMinutes, lastAuthTimestamp, current, System.currentTimeMillis())
        if (!locked) session.update { it.resumed(trip) }
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
        session
    )

    /**
     * Get the configured timeout in minutes
     */
    suspend fun getTimeoutMinutes(): Int {
        return userPreferencesRepository.getAppLockTimeoutMinutes()
    }
}

/**
 * The in-memory half of the lock: whether the app is unlocked on screen, when
 * it was last left unlocked, and how many times it has left, so a check that
 * began before the latest trip out can't resume the session it ended.
 */
internal data class LockSession(
    val unlocked: Boolean = false,
    val leftAt: Long = 0L,
    val trips: Int = 0
) {
    fun left(now: Long) = LockSession(unlocked = false, leftAt = if (unlocked) now else leftAt, trips = trips + 1)

    fun unlockedNow() = copy(unlocked = true)

    /** A check that began during [trip] found the timeout hasn't run out. */
    fun resumed(trip: Int) = if (trip == trips) copy(unlocked = true) else this
}

/**
 * [shouldLock] as a flow, re-checked with the current time on every emission:
 * while the app is unlocked on screen that's always "unlocked", and once it
 * has left, a stale "unlocked" from before the timeout ran out would let an
 * unrelated settings write unlock it.
 */
internal fun lockStateFlow(
    isEnabled: Flow<Boolean>,
    timeoutMinutes: Flow<Int>,
    lastAuthTimestamp: Flow<Long>,
    session: Flow<LockSession>,
    now: () -> Long = System::currentTimeMillis
): Flow<Boolean> = combine(isEnabled, timeoutMinutes, lastAuthTimestamp, session) { enabled, timeout, lastAuth, s ->
    shouldLock(enabled, timeout, lastAuth, s, now())
}

/**
 * Never locks while the app is unlocked on screen. Once it has left: with
 * "Immediately" (timeout 0) it locks on return; otherwise once that long has
 * passed since it was last unlocked on screen (an unlock, or leaving unlocked),
 * or if the clock now reads earlier than that.
 */
internal fun shouldLock(
    isEnabled: Boolean,
    timeoutMinutes: Int,
    lastAuthTimestamp: Long,
    session: LockSession,
    now: Long
): Boolean {
    if (!isEnabled || session.unlocked) return false
    if (timeoutMinutes == 0) return true
    val lastUnlocked = maxOf(lastAuthTimestamp, session.leftAt)
    // No unlock recorded yet
    if (lastUnlocked == 0L) return true
    val away = now - lastUnlocked
    return away < 0 || away >= timeoutMinutes * TimeConstants.MILLIS_PER_MINUTE
}
