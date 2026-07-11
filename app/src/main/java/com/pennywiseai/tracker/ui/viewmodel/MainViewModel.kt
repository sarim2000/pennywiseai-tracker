package com.pennywiseai.tracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.data.contacts.ContactsResolver
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.MerchantAliasRepository
import kotlinx.coroutines.flow.map
import com.pennywiseai.tracker.ui.components.WhatsNewContent
import com.pennywiseai.tracker.ui.components.WhatsNewVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val merchantAliasRepository: MerchantAliasRepository,
    internal val contactsResolver: ContactsResolver
) : ViewModel() {

    private val _whatsNewVersion = MutableStateFlow<WhatsNewVersion?>(null)
    val whatsNewVersion: StateFlow<WhatsNewVersion?> = _whatsNewVersion.asStateFlow()

    // Project the toggle as a StateFlow so MainScreen can synchronously
    // build a CompositionLocal closure over the latest value without
    // collecting on every render path.
    val useContactsForVpa: StateFlow<Boolean> = userPreferencesRepository.useContactsForVpa
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Raw merchant name -> user alias (#583). Projected as a StateFlow so
    // MainScreen can build the merchant-display closure over the latest map
    // without collecting on every render path.
    val merchantAliases: StateFlow<Map<String, String>> =
        merchantAliasRepository.getAllAliases()
            .map { list -> list.associate { it.merchantName to it.alias } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
