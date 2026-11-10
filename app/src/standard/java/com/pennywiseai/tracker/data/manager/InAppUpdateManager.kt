package com.pennywiseai.tracker.data.manager

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Play In-App Updates.
 * Checks for available updates and triggers the update flow.
 */
@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private var updateListener: InstallStateUpdatedListener? = null
    private var snackbarHostState: SnackbarHostState? = null
    private var coroutineScope: CoroutineScope? = null
    
    /**
     * Checks for available updates and starts the update flow if available.
     * Google Play handles all the UI - showing update dialog, progress, etc.
     * @param activity The activity to start the update flow from
     * @param snackbarHostState The SnackbarHostState to show restart prompt
     * @param scope The CoroutineScope to launch the snackbar in
     */
    fun checkForUpdate(
        activity: ComponentActivity,
        snackbarHostState: SnackbarHostState? = null,
        scope: CoroutineScope? = null
    ) {
        this.snackbarHostState = snackbarHostState
        this.coroutineScope = scope
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            when {
                // Check if update is available and allowed
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    // Start flexible update flow
                    startFlexibleUpdate(activity, appUpdateInfo)
                }
                
                // Check if update was downloaded but not installed
                appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED -> {
                    // Notify user to complete update
                    popupSnackbarForCompleteUpdate()
                }
            }
        }.addOnFailureListener { exception ->
            // Log error but don't show to user - fail silently
            Log.e(TAG, "Update check failed", exception)
        }
    }
    
    /**
     * Starts the flexible update flow.
     * User can continue using the app while the update downloads in background.
     */
    private fun startFlexibleUpdate(activity: ComponentActivity, appUpdateInfo: AppUpdateInfo) {
        // Register listener to monitor update progress
        updateListener = InstallStateUpdatedListener { state ->
            handleUpdateState(state)
        }
        
        updateListener?.let {
            appUpdateManager.registerListener(it)
        }
        
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start update flow", e)
            unregisterListener()
        }
    }
    
    /**
     * Handles update state changes.
     */
    private fun handleUpdateState(state: InstallState) {
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                // Update has been downloaded
                popupSnackbarForCompleteUpdate()
                unregisterListener()
            }
            InstallStatus.INSTALLED -> {
                // Update installed successfully
                unregisterListener()
            }
            InstallStatus.FAILED -> {
                // Update failed
                Log.e(TAG, "Update failed with error code: ${state.installErrorCode()}")
                unregisterListener()
            }
            else -> {
                // Other states like DOWNLOADING, INSTALLING, etc.
                // Google Play handles the UI for these states
            }
        }
    }
    
    /**
     * Shows a Snackbar notification to complete the update.
     * Prompts the user to restart the app to apply the update.
     */
    private fun popupSnackbarForCompleteUpdate() {
        // If we have both SnackbarHostState and CoroutineScope, show the Snackbar
        val hostState = snackbarHostState
        val scope = coroutineScope
        
        if (hostState != null && scope != null) {
            scope.launch {
                val result = hostState.showSnackbar(
                    message = "Update ready! Restart the app to apply changes.",
                    actionLabel = "Restart",
                    duration = SnackbarDuration.Indefinite
                )
                
                if (result == SnackbarResult.ActionPerformed) {
                    // User clicked restart, complete the update
                    completeUpdate()
                }
            }
        } else {
            // Fallback to logging if Snackbar components aren't available
            Log.i(TAG, "Update downloaded. App will be updated on next restart.")
        }
    }
    
    /**
     * Completes the update and restarts the app.
     */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }
    
    /**
     * Unregisters the update listener.
     */
    private fun unregisterListener() {
        updateListener?.let {
            appUpdateManager.unregisterListener(it)
            updateListener = null
        }
    }
    
    /**
     * Call this in onDestroy to clean up.
     */
    fun cleanup() {
        unregisterListener()
        // A singleton: holding the screen's scope and snackbar past it leaks the Activity.
        snackbarHostState = null
        coroutineScope = null
    }
    
    companion object {
        private const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 123
    }
}