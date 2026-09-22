package com.pennywiseai.tracker.presentation.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.billing.PurchaseLauncher
import com.pennywiseai.tracker.billing.PurchaseResult
import com.pennywiseai.tracker.BuildConfig
import com.pennywiseai.tracker.billing.license.LicenseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [UpgradeSheet]. Pro can't be bought in the app, so this only reads
 * entitlement ([EntitlementGate], for the member/upgrade variant and the
 * celebration), activates license keys, and re-queries Play on Restore for
 * users who bought Pro there while that was possible.
 */
@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val purchaseLauncher: PurchaseLauncher,
    private val entitlementGate: EntitlementGate,
    private val licenseManager: LicenseManager,
) : ViewModel() {

    private val initialEntitled = entitlementGate.isProEntitled.value

    private val _state = MutableStateFlow(UpgradeUiState(isAlreadyEntitled = initialEntitled))
    val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

    // One-shot events (currently just Dismiss). Modeled as a Channel rather
    // than persistent state so they fire exactly once even if the VM
    // survives across sheet open/close cycles. Sticky state was the source
    // of the "Active row not clickable after purchase until restart" bug —
    // didBecomePro stayed true in state, LaunchedEffect re-triggered on
    // next sheet open, sheet auto-dismissed on frame 1.
    private val _events = Channel<UpgradeEvent>(Channel.BUFFERED)
    val events: Flow<UpgradeEvent> = _events.receiveAsFlow()

    init {
        // On a fresh false → true transition (a key activated, or Restore
        // landed an entitlement mid-sheet), trigger the celebration content.
        // The actual sheet dismiss is gated on the celebration finishing.
        viewModelScope.launch {
            // Keep the sheet's member/upgrade variant in step with the gate.
            // The ViewModel outlives the sheet (Activity-scoped), so a value
            // captured once at init went stale after a license activation or
            // a Play refresh landed. Celebrate only on a false→true edge,
            // judged against the last value we handled — not drop(1), which
            // would lose an update that raced the constructor.
            var last = initialEntitled
            entitlementGate.isProEntitled.collect { entitled ->
                val becamePro = entitled && !last
                last = entitled
                _state.update { ui ->
                    ui.copy(isAlreadyEntitled = entitled, showCelebration = ui.showCelebration || becamePro)
                }
            }
        }

        viewModelScope.launch {
            combine(licenseManager.license, licenseManager.isLicensed) { license, licensed ->
                license?.productName to licensed
            }.collect { (productName, licensed) ->
                _state.update { it.copy(isLicensed = licensed, licenseProductName = productName) }
            }
        }

        refresh()
    }

    // region: license key

    fun onShowLicenseDialog() {
        _state.update { it.copy(showLicenseDialog = true, licenseError = null, licenseCanMove = false) }
    }

    fun onDismissLicenseDialog() {
        if (_state.value.isActivating) return
        _state.update { it.copy(showLicenseDialog = false, licenseError = null, licenseCanMove = false) }
    }

    fun onActivateLicense(key: String) = runLicense { licenseManager.activate(key) }

    fun onMoveLicenseHere(key: String, email: String) = runLicense { licenseManager.moveHere(key, email) }

    fun onRemoveLicense() {
        viewModelScope.launch {
            _state.update { it.copy(isActivating = true) }
            licenseManager.remove()
            _state.update { it.copy(isActivating = false) }
        }
    }

    private fun runLicense(action: suspend () -> LicenseManager.ActivationOutcome) {
        viewModelScope.launch {
            _state.update { it.copy(isActivating = true, licenseError = null, licenseCanMove = false) }
            val outcome = action()
            _state.update { ui ->
                when (outcome) {
                    LicenseManager.ActivationOutcome.Activated ->
                        ui.copy(isActivating = false, showLicenseDialog = false)
                    LicenseManager.ActivationOutcome.InvalidKey ->
                        ui.copy(isActivating = false, licenseError = "That key isn't valid. Check for typos and try again.")
                    LicenseManager.ActivationOutcome.ActiveElsewhere ->
                        ui.copy(
                            isActivating = false,
                            licenseError = "This key is already active on another device.",
                            licenseCanMove = BuildConfig.LICENSE_MOVE_URL.isNotBlank(),
                        )
                    LicenseManager.ActivationOutcome.Offline ->
                        ui.copy(isActivating = false, licenseError = "Couldn't reach the license server. Check your connection and try again.")
                }
            }
        }
    }

    // endregion

    /**
     * Called by the UI when the celebration view finishes — either the
     * auto-timer elapses or the user taps Continue. Clears the
     * showCelebration flag (so reopening the sheet doesn't re-run the
     * celebration) and fires a one-shot Dismiss event.
     */
    fun markCelebrationComplete() {
        _state.update { it.copy(showCelebration = false) }
        _events.trySend(UpgradeEvent.Dismiss)
    }

    /** Re-queries Play for an entitlement bought back when Pro was sold there. */
    fun onRestore() {
        viewModelScope.launch {
            _state.update { it.copy(isPurchasing = true, errorMessage = null) }
            val result = purchaseLauncher.refresh()
            handleRefreshResult(result)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = purchaseLauncher.refresh()
            handleRefreshResult(result)
        }
    }

    private fun handleRefreshResult(result: PurchaseResult) {
        when (result) {
            is PurchaseResult.Success -> _state.update {
                it.copy(isLoading = false, isPurchasing = false)
            }
            is PurchaseResult.ServiceUnavailable -> _state.update {
                it.copy(
                    isLoading = false,
                    isPurchasing = false,
                    errorMessage = "Couldn't reach Play Store. Try again later.",
                )
            }
            else -> _state.update { it.copy(isLoading = false, isPurchasing = false) }
        }
    }
}
