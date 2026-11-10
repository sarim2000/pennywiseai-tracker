package com.pennywiseai.tracker.ui.components

import android.Manifest
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pennywiseai.tracker.utils.canReadSms
import com.pennywiseai.tracker.utils.startFirstAvailable

/** SMS access as the user can act on it. */
@Stable
class SmsAccess(
    val granted: Boolean,
    /** Android won't show the dialog again, so only app settings can grant it. */
    val blocked: Boolean,
    /** Asks for SMS access, or opens app settings once Android won't ask. */
    val request: () -> Unit,
)

/**
 * Re-checked on every resume, so a grant made in app settings counts on return.
 * [onGranted] runs when access arrives (from the dialog or settings), not when it was
 * already there.
 */
@Composable
fun rememberSmsAccess(
    onGranted: () -> Unit = {},
): SmsAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var granted by remember { mutableStateOf(context.canReadSms()) }
    var blocked by remember { mutableStateOf(false) }
    val currentOnGranted by rememberUpdatedState(onGranted)

    fun refresh() {
        val now = context.canReadSms()
        if (now && !granted) currentOnGranted()
        granted = now
        // Android also reports "no rationale" before the first ask, so only a past ask counts.
        // ponytail: a dialog dismissed without an answer also reads as blocked; settings still
        // grants it, so the user is never stuck.
        blocked = !now && prefs.getBoolean(KEY_ASKED, false) && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    return SmsAccess(granted, blocked) {
        if (blocked) {
            context.startFirstAvailable()
        } else {
            prefs.edit { putBoolean(KEY_ASKED, true) }
            launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
        }
    }
}

private const val PREFS = "sms_access"
private const val KEY_ASKED = "asked"
