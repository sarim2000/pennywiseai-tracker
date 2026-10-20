package com.pennywiseai.tracker

import com.pennywiseai.tracker.data.database.entity.builtinKey
import android.app.Activity
import android.app.Application
import android.content.res.Configuration as ResConfiguration
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.data.webhook.WebhookSyncScheduler
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    lateinit var categoryRepository: com.pennywiseai.tracker.data.repository.CategoryRepository

    @Inject
    lateinit var scheduledFolderBackupScheduler: com.pennywiseai.tracker.backup.folder.ScheduledFolderBackupScheduler

    @Inject
    lateinit var webhookSyncScheduler: WebhookSyncScheduler

    @Inject
    lateinit var goalRepository: com.pennywiseai.tracker.data.repository.GoalRepository

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
        // Create CategoryMapping's snapshot-state map before any composition exists.
        // Left to its first use, it could be created after a composition's snapshot
        // was taken, and reading it inside that snapshot crashed with "Reading a
        // state that was created after the snapshot was taken" (Play vitals, 2.22.0).
        CategoryMapping.userStyles
        registerActivityLifecycleCallbacks(AppLockLifecycleObserver())
        lastLocales = resources.configuration.locales

        // Re-arm the daily folder backup on process start when the user has it enabled,
        // so scheduled work survives reboots / app updates that clear WorkManager state.
        applicationScope.launch {
            if (userPreferencesRepository.isScheduledFolderBackupEnabled()) {
                scheduledFolderBackupScheduler.schedule()
            }
        }

        // Materialise recurring / scheduled manual (cash) transactions (#706):
        // a daily periodic run plus a one-shot catch-up now, so a device that was
        // off still creates anything that came due while it was down.
        com.pennywiseai.tracker.worker.RecurringTransactionWorker.enqueuePeriodic(this)
        com.pennywiseai.tracker.worker.RecurringTransactionWorker.enqueueOneShotCatchUp(this)
        applicationScope.launch { webhookSyncScheduler.observeSchedule() }

        // Goal widgets (#914): one hook for every goal write (screens, Add to goal,
        // restore, a linked transaction edited or deleted) instead of one per call site.
        // ponytail: re-queries goals on any transactions-table write; cheap at goal-list sizes.
        applicationScope.launch {
            goalRepository.observeGoals().distinctUntilChanged().drop(1).collect {
                com.pennywiseai.tracker.widget.GoalWidgetUpdateWorker.enqueueOneShot(this@PennyWiseApplication)
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

        // Mirror user category styles (color + emoji, #760) into CategoryMapping so
        // icon call sites outside ViewModels can render custom categories.
        applicationScope.launch {
            categoryRepository.getAllCategories().collectLatest { categories ->
                com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames.update(categories)
                val seedColors = com.pennywiseai.shared.data.bootstrap.DefaultCategoryData.ALL.associate { it.name to it.colorHex }
                val styles = categories.associate {
                    val key = it.builtinKey
                    it.name to CategoryMapping.UserStyle(
                        colorHex = it.color,
                        emoji = it.icon,
                        builtinKey = key?.takeIf { k -> k != it.name },
                        // A built-in still on its seeded colour uses the palette; one the
                        // user recoloured (now possible, #823) uses theirs.
                        customColor = key == null || !it.color.equals(seedColors[key], ignoreCase = true)
                    )
                }
                CategoryMapping.userStyles.keys.retainAll(styles.keys)
                CategoryMapping.userStyles.putAll(styles)
            }
        }
    }

    private var billingRefreshed = false

    /**
     * Billing-entitlement sync, once per process, the first time a screen
     * opens. Not in [onCreate]: the process also starts for an incoming SMS,
     * a bank notification, a worker or a widget, and connecting to Play
     * Billing there also queues the library's own usage logs for upload
     * (#881). Those starts don't need a fresh check — the cached entitlement
     * (DataStore) covers them. No-op on F-Droid. A failure keeps the cache.
     */
    private fun refreshBillingOnce() {
        if (billingRefreshed) return
        billingRefreshed = true
        applicationScope.launch {
            try {
                purchaseGateway.refresh()
            } catch (e: Exception) {
                android.util.Log.w("PennyWiseApp", "Initial billing refresh failed: ${e.message}", e)
            }
        }
    }

    private var lastLocales: android.os.LocaleList? = null

    // Widgets bake translated text into their stored snapshots, so rebuild them
    // when the device or per-app language changes.
    override fun onConfigurationChanged(newConfig: ResConfiguration) {
        super.onConfigurationChanged(newConfig)
        val locales = newConfig.locales
        if (lastLocales != null && locales != lastLocales) {
            com.pennywiseai.tracker.widget.WidgetRefresher.refreshTransactionWidgets(this)
        }
        lastLocales = locales
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
                refreshBillingOnce()
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
