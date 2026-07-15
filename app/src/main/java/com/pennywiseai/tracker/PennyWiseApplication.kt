package com.pennywiseai.tracker

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PennyWiseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appLockRepository: AppLockRepository

    @Inject
    lateinit var purchaseGateway: com.pennywiseai.tracker.billing.PurchaseGateway

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var scheduledFolderBackupScheduler: com.pennywiseai.tracker.backup.folder.ScheduledFolderBackupScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activityReferences = 0
    private var isInForeground = false

    /**
     * Publicly accessible flag to check if the app is in the foreground.
     * Used by SmsBroadcastReceiver to determine whether to show notifications.
     */
    @Volatile
    var isAppInForeground: Boolean = false
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLockLifecycleObserver())

        // Initial billing-entitlement sync. No-op on F-Droid (the stub
        // gateway returns immediately). Failure here is non-fatal — the
        // cached entitlement (DataStore) keeps the UI consistent until
        // the next attempt.
        applicationScope.launch {
            try {
                purchaseGateway.refresh()
            } catch (e: Exception) {
                android.util.Log.w("PennyWiseApp", "Initial billing refresh failed: ${e.message}", e)
            }
        }

        // Re-arm the daily folder backup on process start when the user has it enabled,
        // so scheduled work survives reboots / app updates that clear WorkManager state.
        applicationScope.launch {
            if (userPreferencesRepository.isScheduledFolderBackupEnabled()) {
                scheduledFolderBackupScheduler.schedule()
            }
        }

        // Keep CurrencyFormatter's number-format style in sync with the user's
        // preference. CurrencyFormatter is a stateless object used from non-Compose
        // contexts (widgets, workers) too, so we push the value into its @Volatile
        // field rather than relying on a Compose-collected state.
        applicationScope.launch {
            userPreferencesRepository.numberFormatStyle.collectLatest { style ->
                CurrencyFormatter.numberFormatStyle = style
            }
        }
    }

    /**
     * Lifecycle observer to track app foreground/background state
     * This is used to trigger app lock when app returns from background
     */
    private inner class AppLockLifecycleObserver : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {
            activityReferences++
            if (!isInForeground) {
                // App came to foreground
                isInForeground = true
                isAppInForeground = true
                // Check if app should be locked when returning from background
                checkAndLockApp()
            }
        }

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            activityReferences--
            if (activityReferences == 0) {
                // App went to background
                isInForeground = false
                isAppInForeground = false
                // Note: We don't need to do anything here
                // The lock state will be checked when app returns to foreground
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}

        private fun checkAndLockApp() {
            applicationScope.launch {
                // The AppLockRepository will determine if app should be locked
                // based on timeout settings
                // The lock state will be observed by the AppLockViewModel
            }
        }
    }
}