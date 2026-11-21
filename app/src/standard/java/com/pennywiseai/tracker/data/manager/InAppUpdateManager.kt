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
 * in the background, then [restartPrompt] asks the UI for a restart.
 */
@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private val _restartPrompt = MutableStateFlow(false)
    val restartPrompt: StateFlow<Boolean> = _restartPrompt

    // ponytail: both once per process, so returning from Play's own dialog (a resume),
    // "No thanks" or "Later" doesn't loop it; the next cold start asks again.
    private var offered = false
    private var restartDeclined = false

    private fun downloaded() {
        if (!restartDeclined) _restartPrompt.value = true
    }

    // A singleton, registered for the process: it holds no Activity.
    private val listener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> downloaded()
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
                info.installStatus() == InstallStatus.DOWNLOADED -> downloaded()
                !offered &&
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    !activity.isFinishing -> {
                    // Only a flow that started counts, so a failed launch is retried next resume.
                    offered = try {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            UPDATE_REQUEST_CODE
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start update flow", e)
                        false
                    }
                }
            }
        }.addOnFailureListener { Log.e(TAG, "Update check failed", it) }
    }

    /** Installs the downloaded update; Play restarts the app. On failure the next resume asks again. */
    fun completeUpdate() {
        _restartPrompt.value = false
        appUpdateManager.completeUpdate().addOnFailureListener { Log.e(TAG, "Update install failed", it) }
    }

    /** "Later": no more restart prompts until the next cold start. */
    fun dismissRestart() {
        restartDeclined = true
        _restartPrompt.value = false
    }

    private companion object {
        const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 123
    }
}
