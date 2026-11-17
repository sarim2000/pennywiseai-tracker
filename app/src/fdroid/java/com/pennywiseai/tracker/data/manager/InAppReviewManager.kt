package com.pennywiseai.tracker.data.manager

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid stub implementation of InAppReviewManager.
 * F-Droid doesn't support Google Play Services, so this does nothing.
 * Users can rate the app directly on F-Droid.
 */
@Singleton
class InAppReviewManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    companion object {
        private const val TAG = "InAppReviewManager"
    }

    /**
     * No-op for F-Droid builds.
     * F-Droid users can rate the app directly on the F-Droid app page.
     */
    suspend fun checkAndShowReviewIfEligible(
        activity: ComponentActivity,
        transactionCount: Int = 0
    ) {
        // F-Droid doesn't support Google Play In-App Reviews
        // Users need to rate on F-Droid directly
        Log.d(TAG, "In-app reviews not available on F-Droid builds")
    }

    /**
     * No-op for F-Droid builds.
     */
    suspend fun checkAfterSmsImport(
        activity: ComponentActivity,
        importedCount: Int
    ) {
        // No-op for F-Droid
        Log.d(TAG, "In-app reviews not available on F-Droid builds")
    }
}
