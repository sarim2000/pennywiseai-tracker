package com.pennywiseai.tracker.presentation.chat.speech

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pennywiseai.tracker.utils.startFirstAvailable

@Stable
class MicAccess(
    val granted: Boolean,
    /** Android won't show the dialog again, so only app settings can grant it. */
    val blocked: Boolean,
    val request: () -> Unit,
)

/**
 * Microphone permission for dictation. [onGranted] runs when access arrives
 * (from the dialog or from settings), not when it was already granted.
 */
@Composable
fun rememberMicAccess(onGranted: () -> Unit): MicAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var granted by remember { mutableStateOf(context.canRecordAudio()) }
    var blocked by remember { mutableStateOf(context.isMicBlocked(activity, prefs)) }
    // Set only by a mic tap. A grant from system settings with no tap must not start listening.
    var awaitingGrant by remember { mutableStateOf(false) }
    val currentOnGranted by rememberUpdatedState(onGranted)

    fun refresh() {
        val now = context.canRecordAudio()
        if (now && !granted && awaitingGrant) currentOnGranted()
        if (now) awaitingGrant = false
        granted = now
        blocked = context.isMicBlocked(activity, prefs)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Settings covers the activity. Drop the ask so returning does not listen by itself.
            if (event == Lifecycle.Event.ON_STOP) awaitingGrant = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            awaitingGrant = false
        }
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    return MicAccess(granted, blocked) {
        awaitingGrant = true
        if (context.isMicBlocked(activity, prefs)) {
            blocked = true
            context.startFirstAvailable()
        } else {
            prefs.edit { putBoolean(KEY_ASKED, true) }
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun rememberSpeechInput(): SpeechInput {
    val context = LocalContext.current
    val speech = remember(context.applicationContext) {
        SpeechInput(AndroidOnDeviceSpeechEngine(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, speech) {
        val observer = LifecycleEventObserver { _, event ->
            // The activity is going away. Stop while the mic is still ours.
            if (event == Lifecycle.Event.ON_STOP) speech.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speech.release()
        }
    }
    return speech
}

/**
 * A missing on-device language opens voice-input settings. A blocked microphone
 * permission opens app info. App info is the last resort either way.
 */
fun Context.openSpeechFailureSettings(failure: SpeechFailure) {
    val voiceSettings = failure == SpeechFailure.UNAVAILABLE || failure == SpeechFailure.LANGUAGE
    startFirstAvailable(
        if (voiceSettings) listOf(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) else emptyList(),
    )
}

private fun Context.canRecordAudio(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/** True only after a denial Android will not ask about again. The first visit is not blocked. */
private fun Context.isMicBlocked(activity: Activity?, prefs: SharedPreferences): Boolean {
    if (canRecordAudio()) return false
    if (!prefs.getBoolean(KEY_ASKED, false) || activity == null) return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.RECORD_AUDIO,
    )
}

private const val PREFS = "mic_access"
private const val KEY_ASKED = "asked"
