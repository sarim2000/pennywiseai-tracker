package com.pennywiseai.tracker.ui.viewmodel

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpotlightViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _spotlightState = MutableStateFlow(SpotlightState())
    val spotlightState: StateFlow<SpotlightState> = _spotlightState.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = userPreferencesRepository.userPreferences.first()
            _spotlightState.value = _spotlightState.value.copy(
                shouldShowTutorial = !preferences.hasShownScanTutorial
            )
        }
    }

    fun updateFabPosition(position: Rect) {
        _spotlightState.value = _spotlightState.value.copy(
            fabPosition = position,
            showTutorial = _spotlightState.value.shouldShowTutorial
        )
    }

    fun dismissTutorial() {
        _spotlightState.value = _spotlightState.value.copy(
            showTutorial = false,
            shouldShowTutorial = false
        )
        viewModelScope.launch {
            userPreferencesRepository.markScanTutorialShown()
        }
    }

    data class SpotlightState(
        val showTutorial: Boolean = false,
        val shouldShowTutorial: Boolean = false,
        val fabPosition: Rect? = null
    )
}
