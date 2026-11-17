package com.pennywiseai.tracker.data.manager

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Play In-App Reviews.
 * Prompts users to rate the app after certain conditions are met.
 */
@Singleton
class InAppReviewManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)

    companion object {
        private const val TAG = "InAppReviewManager"
        private const val DAYS_BEFORE_PROMPT = 1L // Days to wait before showing review
        private const val MINIMUM_TRANSACTIONS = 10 // Minimum transactions before showing review
    }

    /**
     * Checks if eligible and shows review prompt if conditions are met.
     * Should be called from an activity context.
     *
     * @param activity The activity to launch the review flow from
     * @param transactionCount Optional transaction count for additional eligibility check
     */
    suspend fun checkAndShowReviewIfEligible(
        activity: ComponentActivity,
        transactionCount: Int = 0
    ) {
        if (!isEligible(transactionCount)) {
            Log.d(TAG, "Not eligible for review prompt")
            return
        }

        Log.d(TAG, "User is eligible for review prompt, requesting review flow")
        requestAndLaunchReview(activity)
    }

    /**
     * Checks if the user is eligible for a review prompt.
     * Conditions:
     * 1. At least 3 days since first launch
     * 2. Haven't shown review prompt before
     * 3. (Optional) Has minimum number of transactions
     */
    private suspend fun isEligible(transactionCount: Int): Boolean {
        // Check if already shown
        if (userPreferencesRepository.hasShownReviewPrompt()) {
            Log.d(TAG, "Review already shown before")
            return false
        }

        // Check first launch time
        val firstLaunchTime = userPreferencesRepository.getFirstLaunchTime().first()
        if (firstLaunchTime == null) {
            // First time launching, record it
            userPreferencesRepository.setFirstLaunchTime(System.currentTimeMillis())
            Log.d(TAG, "First launch recorded")
            return false
        }

        // Calculate days since first launch
        val currentTime = System.currentTimeMillis()
        val daysSinceFirstLaunch = TimeUnit.MILLISECONDS.toDays(currentTime - firstLaunchTime)

        Log.d(TAG, "Days since first launch: $daysSinceFirstLaunch")

        // Check if enough days have passed
        if (daysSinceFirstLaunch < DAYS_BEFORE_PROMPT) {
            Log.d(TAG, "Not enough days passed (need $DAYS_BEFORE_PROMPT days)")
            return false
        }

        // Optional: Check transaction count if provided
        if (transactionCount > 0 && transactionCount < MINIMUM_TRANSACTIONS) {
            Log.d(TAG, "Not enough transactions: $transactionCount (need $MINIMUM_TRANSACTIONS)")
            return false
        }

        return true
    }

    /**
     * Requests ReviewInfo and launches the review flow.
     * Following Google's guidelines:
     * - Don't show error messages to users
     * - Continue normal app flow regardless of outcome
     */
    private fun requestAndLaunchReview(activity: ComponentActivity) {
        val request = reviewManager.requestReviewFlow()

        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                Log.d(TAG, "ReviewInfo obtained successfully")

                // Launch the review flow immediately
                launchReviewFlow(activity, reviewInfo)
            } else {
                // There was some problem, log but don't show to user
                val exception = task.exception
                if (exception is ReviewException) {
                    @ReviewErrorCode val errorCode = exception.errorCode
                    Log.e(TAG, "Review request failed with error code: $errorCode")
                } else {
                    Log.e(TAG, "Review request failed", exception)
                }
            }
        }
    }

    /**
     * Launches the actual review flow.
     * The API doesn't indicate whether the user reviewed or not.
     */
    private fun launchReviewFlow(activity: ComponentActivity, reviewInfo: ReviewInfo) {
        Log.d(TAG, "Launching review flow")

        val flow = reviewManager.launchReviewFlow(activity, reviewInfo)

        flow.addOnCompleteListener { task ->
            // The flow has finished. The API does not indicate whether the user
            // reviewed or not, or even whether the review dialog was shown.
            // Mark as shown regardless of outcome
            Log.d(TAG, "Review flow completed, success: ${task.isSuccessful}")
            if (!task.isSuccessful) {
                Log.e(TAG, "Review flow failed", task.exception)
            } else {
                Log.d(TAG, "Review flow successful")
            }

            // Mark review as shown in a coroutine
            CoroutineScope(Dispatchers.IO).launch {
                userPreferencesRepository.markReviewPromptShown()
                Log.d(TAG, "Review prompt marked as shown")
            }
        }
    }

    /**
     * Special method to trigger review after successful SMS import.
     * This is a good moment to ask for review as user just had a positive experience.
     */
    suspend fun checkAfterSmsImport(
        activity: ComponentActivity,
        importedCount: Int
    ) {
        // Only show if imported significant number of transactions
        if (importedCount >= 50) {
            Log.d(TAG, "Checking review eligibility after importing $importedCount transactions")
            checkAndShowReviewIfEligible(activity, importedCount)
        }
    }
}
