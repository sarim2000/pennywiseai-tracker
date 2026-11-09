package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Cached Pro state, the website license, and the usage limits around them. Backed by the
 * single user_preferences DataStore through [UserPreferencesRepository]; keys and defaults
 * are pinned by the UserPreferences golden tests.
 */
interface BillingPreferences {
    /** A first-frame hint only; the real source of truth is the billing provider. */
    val proCachedIsPro: Flow<Boolean>

    suspend fun setProCachedIsPro(isPro: Boolean)

    val storedLicense: Flow<StoredLicense?>

    suspend fun setStoredLicense(license: StoredLicense?)

    /** Epoch millis of the last successful statement import, or null if there hasn't been one. */
    val lastStatementImportAt: Flow<Long?>

    suspend fun markStatementImported(epochMillis: Long)

    /** Decides and records the support nudge in one edit: true at most once per cooldown. */
    suspend fun claimSupportNudge(): Boolean
}
