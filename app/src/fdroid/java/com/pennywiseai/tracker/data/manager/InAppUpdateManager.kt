package com.pennywiseai.tracker.data.manager

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid stub implementation of InAppUpdateManager
 * F-Droid handles updates through their own system
 */
@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable

    private val _updateProgress = MutableStateFlow(0f)
    val updateProgress: StateFlow<Float> = _updateProgress

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState

    fun checkForUpdate(
        activity: ComponentActivity,
        snackbarHostState: SnackbarHostState? = null,
        scope: CoroutineScope? = null
    ) {
        // F-Droid handles updates through its own system
        // No Google Play Services available
    }

    fun startUpdate(activity: Activity) {
        // F-Droid handles updates
    }

    fun completeUpdate() {
        // F-Droid handles updates
    }

    fun cleanup() {
        // No cleanup needed
    }

    enum class UpdateState {
        IDLE,
        CHECKING,
        AVAILABLE,
        DOWNLOADING,
        DOWNLOADED,
        INSTALLING,
        FAILED
    }
}
