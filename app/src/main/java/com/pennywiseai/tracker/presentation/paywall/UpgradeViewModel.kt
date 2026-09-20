package com.pennywiseai.tracker.presentation.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.billing.EntitlementSource
import com.pennywiseai.tracker.billing.ProProduct
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
 * Backs [UpgradeSheet]. Reads from [EntitlementSource] (catalog) and
 * [EntitlementGate] (auto-dismiss when Pro lands); writes through
 * [PurchaseLauncher]. Constructor depends only on the narrow interfaces it
 * actually needs — never the full `PurchaseGateway` (Interface Segregation).
 *
 * Selection is tracked by [ProProduct.key], not raw SKU — subscription base
 * plans share a SKU (e.g. monthly + annual both live on `pro_subscription`),
 * so SKU alone can't disambiguate which plan the user picked.
 */
@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val entitlementSource: EntitlementSource,
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
        viewModelScope.launch {
            entitlementSource.products.collect { products ->
                _state.update { current ->
                    current.copy(
                        products = products,
                        isLoading = products.isEmpty() && current.isLoading,
                    )
                }
            }
        }

        // On a fresh false → true transition (purchase succeeded or
        // restore landed an entitlement mid-sheet), trigger the
        // celebration content. drop(1) skips the StateFlow's replay of
        // the current value, so users who were already Pro when they
        // opened the sheet don't see a celebration on frame 1.
        // The actual sheet dismiss is gated on [didBecomePro] which the
        // UI flips after the celebration timer (or a Continue tap).
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

    fun onSelectPlan(key: String) {
        _state.update { it.copy(selectedKey = key) }
    }

    /**
     * User tapped the CTA. The UI passes the active product (already
     * resolved from the merged live + fallback catalog) so we can launch
     * even during the initial refresh() window when [state.products] may
     * still be empty.
     *
     * Previously this looked up the product itself from `state.products`
     * and silently returned when the lookup missed — meaning a tap during
     * the catalog-load gap was a dead no-op. Now we hand whatever the UI
     * resolved straight to the gateway:
     *   - If the gateway has live ProductDetails cached → flow launches.
     *   - If not → the gateway's own retry path re-queries Play once.
     *   - If still missing → the gateway returns
     *     [PurchaseResult.Failed], which we surface as an inline error.
     *
     * The user always gets feedback: success, error, or spinner — never
     * a silent dropped tap.
     */
    fun onPurchase(activity: Activity, product: ProProduct?) {
        if (product == null) {
            _state.update {
                it.copy(errorMessage = "Plans are still loading. Try again in a moment.")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isPurchasing = true, errorMessage = null) }
            val result = purchaseLauncher.launchPurchase(activity, product)
            handlePurchaseResult(result)
        }
    }

    fun onRestore() {
        viewModelScope.launch {
            _state.update { it.copy(isPurchasing = true, errorMessage = null) }
            val result = purchaseLauncher.refresh()
            handleRefreshResult(result)
        }
    }

    fun onErrorDismissed() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = purchaseLauncher.refresh()
            handleRefreshResult(result)
        }
    }

    private fun handlePurchaseResult(result: PurchaseResult) {
        when (result) {
            is PurchaseResult.Success,
            is PurchaseResult.Pending -> _state.update { it.copy(isPurchasing = false) }

            is PurchaseResult.UserCancelled -> _state.update { it.copy(isPurchasing = false) }

            is PurchaseResult.Failed -> _state.update {
                it.copy(
                    isPurchasing = false,
                    errorMessage = result.debugMessage ?: "Purchase failed. Please try again.",
                )
            }

            is PurchaseResult.ServiceUnavailable -> _state.update {
                it.copy(
                    isPurchasing = false,
                    errorMessage = "Play Store unavailable. Check your connection and try again.",
                )
            }

            is PurchaseResult.Unsupported -> _state.update {
                it.copy(
                    isPurchasing = false,
                    errorMessage = "Purchases aren't available in this build.",
                )
            }
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
