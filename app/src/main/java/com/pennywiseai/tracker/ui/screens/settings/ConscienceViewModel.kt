package com.pennywiseai.tracker.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.manager.ConscienceNotifier
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.conscience.ConscienceAddress
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConscienceUiState(
    val enabled: Boolean = false,
    val voice: ConscienceVoice = ConscienceVoice.ENGLISH,
    val address: ConscienceAddress = ConscienceAddress.MASCULINE,
    val frequency: ConscienceFrequency = ConscienceFrequency.NORMAL
)

@HiltViewModel
class ConscienceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    val state: StateFlow<ConscienceUiState> = combine(
        preferences.conscienceEnabled,
        preferences.conscienceVoice,
        preferences.conscienceAddress,
        preferences.conscienceFrequency,
        ::ConscienceUiState
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConscienceUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setConscienceEnabled(enabled) }
    }

    fun setVoice(voice: ConscienceVoice) {
        viewModelScope.launch { preferences.setConscienceVoice(voice) }
    }

    fun setAddress(address: ConscienceAddress) {
        viewModelScope.launch { preferences.setConscienceAddress(address) }
    }

    fun setFrequency(frequency: ConscienceFrequency) {
        viewModelScope.launch { preferences.setConscienceFrequency(frequency) }
    }

    /** Posts the sample; false when notifications are blocked, so the screen can say why nothing came. */
    fun tryIt(): Boolean {
        if (!ConscienceNotifier.canPost(context)) return false
        val current = state.value
        ConscienceNotifier.postSample(context, current.voice, current.address, current.frequency)
        return true
    }
}
