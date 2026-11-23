package com.pennywiseai.tracker.data.manager

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** F-Droid stub: F-Droid and IzzyOnDroid deliver updates themselves. */
@Singleton
class InAppUpdateManager @Inject constructor() {
    val restartPrompt: StateFlow<Boolean> = MutableStateFlow(false)

    fun checkForUpdate(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {}

    fun onUpdateFlowResult(resultCode: Int) {}

    fun completeUpdate() {}

    fun dismissRestart() {}
}
