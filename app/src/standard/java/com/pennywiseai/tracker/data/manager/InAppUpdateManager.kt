package com.pennywiseai.tracker.data.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play In-App Updates (flexible): offered when the app opens, downloaded
 * in the background, then [updateDownloaded] asks the UI for a restart.
 */
@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private val _updateDownloaded = MutableStateFlow(false)
    val updateDownloaded: StateFlow<Boolean> = _updateDownloaded

    // ponytail: once per process, so returning from Play's own dialog (a resume) or a
    // "No thanks" doesn't loop it; the next cold start offers it again.
    private var offered = false

    // A singleton, registered for the process: it holds no Activity.
    private val listener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> _updateDownloaded.value = true
            InstallStatus.FAILED -> Log.e(TAG, "Update failed with error code: ${state.installErrorCode()}")
            else -> {}
        }
    }

    init {
        appUpdateManager.registerListener(listener)
    }

    /** Call on every resume: offers a waiting update once, and re-raises a finished download. */
    fun onResume(activity: Activity) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _updateDownloaded.value = true
                !offered &&
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    !activity.isFinishing -> {
                    offered = true
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            UPDATE_REQUEST_CODE
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start update flow", e)
                    }
                }
            }
        }.addOnFailureListener { Log.e(TAG, "Update check failed", it) }
    }

    /** Installs the downloaded update; Play restarts the app. */
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    private companion object {
        const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 123
    }
}
