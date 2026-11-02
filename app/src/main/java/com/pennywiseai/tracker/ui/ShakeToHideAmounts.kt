package com.pennywiseai.tracker.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.utils.ShakeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the detector and the toggle so both outlive a rotation: the cooldown
 * carries over (shaking through a rotation can't undo the hide), and an accepted
 * shake's save isn't cancelled with the old composition.
 */
@HiltViewModel
class ShakeToHideViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository
) : ViewModel() {
    val enabled: StateFlow<Boolean> = preferences.shakeToHideAmounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** What the screen should say about a shake: the new state, and whether it's the first shake ever. */
    data class Toggled(val hidden: Boolean, val firstShake: Boolean)

    private val toggled = Channel<Toggled>(Channel.BUFFERED)
    val shakes: Flow<Toggled> = toggled.receiveAsFlow()

    private val detector = ShakeDetector()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setShakeToHideAmounts(enabled) }
    }

    fun onSample(x: Float, y: Float, z: Float, timestampNs: Long) {
        if (!detector.onSample(x, y, z, timestampNs)) return
        viewModelScope.launch {
            // Before any choice only Home is hidden, so the first shake hides everywhere.
            val hidden = preferences.toggleBalanceHidden(hiddenWhenUnset = false)
            toggled.send(Toggled(hidden, preferences.claimShakeToHideHint()))
        }
    }
}

/**
 * Shake the phone to hide / show amounts app-wide, when the user turned it on in
 * Settings. Listens only while the app is on screen ([LifecycleResumeEffect]) and
 * [active] (unlocked). A shake buzzes once; the very first one also says what
 * happened and that shaking again undoes it.
 */
@Composable
fun ShakeToHideAmounts(active: Boolean, viewModel: ShakeToHideViewModel) {
    val context = LocalContext.current
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.shakes.collect { (hidden, firstShake) ->
            // A real buzz, not a touch tick: haptic feedback constants are barely
            // felt on some phones (Samsung), and this has to be felt mid-shake.
            vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            if (firstShake) {
                Toast.makeText(
                    context,
                    if (hidden) R.string.shake_to_hide_first_hidden else R.string.shake_to_hide_first_shown,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    if (!enabled || !active) return
    LifecycleResumeEffect(viewModel) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                viewModel.onSample(x, y, z, event.timestamp)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onPauseOrDispose { sensorManager?.unregisterListener(listener) }
    }
}
