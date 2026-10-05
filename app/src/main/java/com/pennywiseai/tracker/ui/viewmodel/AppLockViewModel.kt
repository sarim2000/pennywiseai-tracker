package com.pennywiseai.tracker.ui.viewmodel

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.AppLockRepository
import com.pennywiseai.tracker.domain.security.BiometricAuthManager
import com.pennywiseai.tracker.domain.security.BiometricCapability
import com.pennywiseai.tracker.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        observeAppLockState()
        checkBiometricCapability()
    }

    private fun observeAppLockState() {
        combine(
            appLockRepository.isAppLockEnabled,
            appLockRepository.timeoutMinutes,
            appLockRepository.shouldLockAppFlow()
        ) { isEnabled, timeoutMinutes, shouldLock ->
            Triple(isEnabled, timeoutMinutes, shouldLock)
        }
            .onEach { (isEnabled, timeoutMinutes, shouldLock) ->
                _uiState.update {
                    it.copy(
                        isLockEnabled = isEnabled,
                        timeoutMinutes = timeoutMinutes,
                        isLocked = shouldLock && isEnabled,
                        checks = it.checks + 1
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun checkBiometricCapability() {
        val capability = biometricAuthManager.canAuthenticate()
        _uiState.update {
            it.copy(
                biometricCapability = capability,
                canUseBiometric = capability == BiometricCapability.Available
            )
        }
    }

    /**
     * Called when authentication succeeds
     */
    fun onAuthenticationSuccess() {
        viewModelScope.launch {
            appLockRepository.updateAuthTimestamp()
            _uiState.update {
                it.copy(
                    isLocked = false,
                    authenticationError = null,
                    authenticationSucceeded = true
                )
            }
        }
    }

    /**
     * Reset authentication succeeded flag after navigation
     */
    fun resetAuthenticationSucceeded() {
        _uiState.update { it.copy(authenticationSucceeded = false) }
    }

    /**
     * Called when authentication fails
     */
    fun onAuthenticationError(errorMessage: String) {
        _uiState.update { it.copy(authenticationError = UiText.Plain(errorMessage)) }
    }

    /**
     * Called when authentication fails (wrong fingerprint, etc.)
     */
    fun onAuthenticationFailed() {
        _uiState.update { it.copy(authenticationError = UiText.Res(R.string.applock_auth_failed)) }
    }

    /**
     * Clear authentication error
     */
    fun clearAuthError() {
        _uiState.update { it.copy(authenticationError = null) }
    }

    /**
     * Enable or disable app lock
     */
    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appLockRepository.setAppLockEnabled(enabled)
        }
    }

    /**
     * Set timeout in minutes (0 = immediately)
     */
    fun setTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            appLockRepository.setTimeoutMinutes(minutes)
        }
    }

    /**
     * Manually lock the app (used when app goes to background)
     */
    fun lockApp() {
        viewModelScope.launch {
            val shouldLock = appLockRepository.shouldLockApp()
            _uiState.update { it.copy(isLocked = shouldLock, checks = it.checks + 1) }
        }
    }

    /**
     * Refresh lock state (check if app should be locked)
     */
    fun refreshLockState() {
        viewModelScope.launch {
            val shouldLock = appLockRepository.shouldLockApp()
            _uiState.update { it.copy(isLocked = shouldLock, checks = it.checks + 1) }
        }
    }

    /**
     * Trigger biometric authentication
     * This must be called with a FragmentActivity from the UI layer
     */
    fun triggerAuthentication(activity: FragmentActivity) {
        biometricAuthManager.authenticate(
            activity = activity,
            onSuccess = { onAuthenticationSuccess() },
            onError = { error -> onAuthenticationError(error) },
            onFailed = { onAuthenticationFailed() }
        )
    }
}

data class AppLockUiState(
    /**
     * How many times the lock state has been evaluated (0 = not yet loaded, so
     * isLocked means nothing). Lets a caller wait for a check that ran after
     * some event, e.g. the resume-time re-check after text is shared in.
     */
    val checks: Int = 0,
    val isLockEnabled: Boolean = false,
    val isLocked: Boolean = false,
    val timeoutMinutes: Int = 1,
    val canUseBiometric: Boolean = false,
    val biometricCapability: BiometricCapability = BiometricCapability.Unknown,
    val authenticationError: UiText? = null,
    val authenticationSucceeded: Boolean = false
)
