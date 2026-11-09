package com.pennywiseai.tracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * The SMS scan window and what the last scan covered. Backed by the single user_preferences
 * DataStore through [UserPreferencesRepository]; keys and defaults are pinned by the
 * UserPreferences golden tests.
 */
interface ScanPreferences {
    val smsScanMonths: Flow<Int>
    val smsScanAllTime: Flow<Boolean>
    val smsScanUseCustomDate: Flow<Boolean>
    val smsScanCustomDate: Flow<Long?>

    suspend fun getSmsScanMonths(): Int
    suspend fun getSmsScanAllTime(): Boolean
    suspend fun getSmsScanUseCustomDate(): Boolean
    suspend fun getSmsScanCustomDate(): Long?

    suspend fun updateSmsScanMonths(months: Int)
    suspend fun updateSmsScanAllTime(allTime: Boolean)
    suspend fun updateSmsScanUseCustomDate(useCustomDate: Boolean)
    suspend fun updateSmsScanCustomDate(dateMillis: Long)

    fun getLastScanTimestamp(): Flow<Long?>
    fun getLastScanPeriod(): Flow<Int?>

    suspend fun setLastScanTimestamp(timestamp: Long)
    suspend fun setLastScanPeriod(period: Int)
}
