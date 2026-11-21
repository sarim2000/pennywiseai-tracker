package com.pennywiseai.tracker.data.manager

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** F-Droid stub: F-Droid and IzzyOnDroid deliver updates themselves. */
@Singleton
class InAppUpdateManager @Inject constructor() {
    val restartPrompt: StateFlow<Boolean> = MutableStateFlow(false)

    fun onResume(activity: Activity) {}

    fun completeUpdate() {}

    fun dismissRestart() {}
}
