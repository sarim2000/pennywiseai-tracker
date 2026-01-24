package com.pennywiseai.tracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.ui.components.WhatsNewContent
import com.pennywiseai.tracker.ui.components.WhatsNewVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _whatsNewVersion = MutableStateFlow<WhatsNewVersion?>(null)
    val whatsNewVersion: StateFlow<WhatsNewVersion?> = _whatsNewVersion.asStateFlow()

    init {
        checkWhatsNew()
    }

    private fun checkWhatsNew() {
        viewModelScope.launch {
            val lastSeenVersion = userPreferencesRepository.getLastSeenAppVersion()
            val currentVersion = BuildConfig.VERSION_NAME

            when {
                // First install - mark current version, don't show dialog
                lastSeenVersion == null -> {
                    userPreferencesRepository.setLastSeenAppVersion(currentVersion)
                }
                // App was updated - show changelog if available
                lastSeenVersion != currentVersion -> {
                    val changelog = WhatsNewContent.parseFromAssets(context)
                    if (changelog != null) {
                        _whatsNewVersion.value = changelog
                    } else {
                        // No changelog file, update silently
                        userPreferencesRepository.setLastSeenAppVersion(currentVersion)
                    }
                }
            }
        }
    }

    fun dismissWhatsNew() {
        viewModelScope.launch {
            userPreferencesRepository.setLastSeenAppVersion(BuildConfig.VERSION_NAME)
            _whatsNewVersion.value = null
        }
    }
}
