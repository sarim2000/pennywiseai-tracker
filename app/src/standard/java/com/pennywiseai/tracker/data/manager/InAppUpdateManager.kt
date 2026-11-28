package com.pennywiseai.tracker.data.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
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
 * Google Play In-App Updates (flexible): offered when Home opens, downloaded
 * in the background, then [restartPrompt] asks the UI for a restart.
 */
@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _restartPrompt = MutableStateFlow(false)
    val restartPrompt: StateFlow<Boolean> = _restartPrompt

    // ponytail: "Later" is once per process, so it doesn't re-ask on every resume
    // (a file picker or the camera resumes too); the next cold start asks again.
    private var restartDeclined = false

    // The version whose sheet is up (or was accepted) in this process: not offered again
    // until the next cold start, and recorded as declined only on "No thanks".
    private var startedVersion = NONE

    private fun downloaded() {
        if (!restartDeclined) _restartPrompt.value = true
    }

    // A singleton, registered for the process: it holds no Activity.
    private val listener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> downloaded()
            InstallStatus.FAILED -> Log.w(TAG, "Update failed with error code: ${state.installErrorCode()}")
            else -> {}
        }
    }

    init {
        appUpdateManager.registerListener(listener)
    }

    /**
     * Call each time Home shows, unlocked and onboarded: offers a new version until
     * the user declines it (remembered across launches), and re-raises a download
     * that's waiting. [launcher] reports the sheet's result to [onUpdateFlowResult].
     * [stillSafe] is asked again once Play answers: the user may have left Home since.
     */
    fun checkForUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        stillSafe: () -> Boolean
    ) {
        // Activity-scoped: removed once the activity stops, so it never fires on a dead one.
        appUpdateManager.appUpdateInfo.addOnSuccessListener(activity) { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                downloaded()
                return@addOnSuccessListener
            }
            val offer = shouldOffer(
                updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                installStatus = info.installStatus(),
                availableVersion = info.availableVersionCode(),
                declinedVersion = prefs.getInt(KEY_DECLINED_VERSION, NONE),
                startedVersion = startedVersion
            )
            if (!offer || !stillSafe() || activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
            // Only a flow that started counts, so a failed launch is retried next time.
            val started = try {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start update flow", e)
                false
            }
            if (started) startedVersion = info.availableVersionCode()
        }.addOnFailureListener {
            // Every non-Play install (sideloaded, debug) lands here: not an error.
            Log.d(TAG, "Update check unavailable: ${it.message}")
        }
    }

    /**
     * Play's sheet closed. Only "No thanks" ([Activity.RESULT_CANCELED]) is remembered
     * across launches; a failed flow, or an app killed with the sheet up, offers again
     * on the next cold start.
     */
    fun onUpdateFlowResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_CANCELED && startedVersion != NONE) {
            prefs.edit { putInt(KEY_DECLINED_VERSION, startedVersion) }
        }
    }

    /** Installs the downloaded update; Play restarts the app. On failure the next check asks again. */
    fun completeUpdate() {
        _restartPrompt.value = false
        appUpdateManager.completeUpdate().addOnFailureListener { Log.w(TAG, "Update install failed", it) }
    }

    /** "Later": no more restart prompts until the next cold start. */
    fun dismissRestart() {
        restartDeclined = true
        _restartPrompt.value = false
    }

    internal companion object {
        private const val TAG = "InAppUpdateManager"
        private const val PREFS = "in_app_update"
        private const val KEY_DECLINED_VERSION = "declined_version_code"
        private const val NONE = -1

        /**
         * Offer a version once per process: "No thanks" holds until a newer one
         * appears. Never while Play is already fetching or installing one.
         */
        fun shouldOffer(
            updateAvailable: Boolean,
            installStatus: Int,
            availableVersion: Int,
            declinedVersion: Int,
            startedVersion: Int
        ): Boolean = updateAvailable &&
            installStatus !in setOf(InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING) &&
            availableVersion != declinedVersion &&
            availableVersion != startedVersion
    }
}
