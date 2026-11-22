package com.pennywiseai.tracker.data.manager

import android.app.Activity
import android.content.Context
import android.util.Log
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

    private fun downloaded() {
        if (!restartDeclined) _restartPrompt.value = true
    }

    // A singleton, registered for the process: it holds no Activity.
    private val listener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> downloaded()
            InstallStatus.FAILED -> {
                // A failed download isn't a "No thanks": offer that version again.
                prefs.edit { remove(KEY_OFFERED_VERSION) }
                Log.w(TAG, "Update failed with error code: ${state.installErrorCode()}")
            }
            else -> {}
        }
    }

    init {
        appUpdateManager.registerListener(listener)
    }

    /**
     * Call each time Home shows, unlocked and onboarded: offers a new version once
     * (remembered across launches), and re-raises a download that's waiting.
     */
    fun checkForUpdate(activity: Activity) {
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
                offeredVersion = prefs.getInt(KEY_OFFERED_VERSION, NONE)
            )
            if (!offer || activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
            // Only a flow that started counts, so a failed launch is retried next time.
            val started = try {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    UPDATE_REQUEST_CODE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start update flow", e)
                false
            }
            if (started) prefs.edit { putInt(KEY_OFFERED_VERSION, info.availableVersionCode()) }
        }.addOnFailureListener {
            // Every non-Play install (sideloaded, debug) lands here: not an error.
            Log.d(TAG, "Update check unavailable: ${it.message}")
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
        private const val UPDATE_REQUEST_CODE = 123
        private const val PREFS = "in_app_update"
        private const val KEY_OFFERED_VERSION = "offered_version_code"
        private const val NONE = -1

        /**
         * Offer a version once: "No thanks" holds until a newer one appears. Never
         * while Play is already fetching or installing one.
         */
        fun shouldOffer(updateAvailable: Boolean, installStatus: Int, availableVersion: Int, offeredVersion: Int): Boolean =
            updateAvailable &&
                installStatus !in setOf(InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING) &&
                availableVersion != offeredVersion
    }
}
