package com.pennywiseai.tracker.ui.viewmodel

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.utils.BackgroundAccessState
import com.pennywiseai.tracker.utils.DeviceOem
import com.pennywiseai.tracker.utils.isBackgroundRestricted
import com.pennywiseai.tracker.utils.isBatteryUnrestricted
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackgroundAccessViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val system = MutableStateFlow<BackgroundAccessState?>(null)

    /** Null until every source has answered, so the list never flashes a wrong status. */
    val state: StateFlow<BackgroundAccessState?> =
        combine(system.filterNotNull(), preferences.backgroundOemStepConfirmedOn) { current, device ->
            current.copy(oemConfirmed = device == Build.DEVICE)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Call on resume: every fix happens in system Settings, outside the app. */
    fun refresh() {
        val status = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
        status.addListener(
            {
                system.value = BackgroundAccessState(
                    batteryUnrestricted = context.isBatteryUnrestricted(),
                    backgroundRestricted = context.isBackgroundRestricted(),
                    unusedRestrictionsStatus = runCatching { status.get() }.getOrNull(),
                    oem = DeviceOem.current
                )
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun setOemConfirmed(confirmed: Boolean) {
        viewModelScope.launch {
            preferences.setBackgroundOemStepConfirmedOn(Build.DEVICE.takeIf { confirmed })
        }
    }
}
