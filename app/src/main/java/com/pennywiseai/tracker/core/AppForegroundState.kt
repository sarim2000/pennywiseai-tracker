package com.pennywiseai.tracker.core

/**
 * Whether one of the app's activities is started right now. `data` checks it to
 * hold back a notification the user would rather not get while in the app,
 * without casting the Application.
 *
 * Backed by `PennyWiseApplication.isAppInForeground` and read at call time
 * (bound in `di/AppForegroundModule.kt`).
 */
fun interface AppForegroundState {
    fun isInForeground(): Boolean
}
