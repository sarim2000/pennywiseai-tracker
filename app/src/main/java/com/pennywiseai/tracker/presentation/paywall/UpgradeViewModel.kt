package com.pennywiseai.tracker.presentation.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.billing.EntitlementSource
import com.pennywiseai.tracker.billing.ProSku
import com.pennywiseai.tracker.billing.PurchaseLauncher
import com.pennywiseai.tracker.billing.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [UpgradeSheet]. Reads from [EntitlementSource] (catalog) and
 * [EntitlementGate] (auto-dismiss when Pro lands); writes through
 * [PurchaseLauncher]. Notice the constructor depends only on the narrow
 * interfaces it actually needs — never the full `PurchaseGateway`
 * (Interface Segregation).
 *
 * The sheet's lifecycle is "open → purchase or dismiss." We refresh the
 * catalog on init so the user sees current prices on every open (Play
 * prices localize per-account, so caching across cold starts isn't safe).
 */
@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val entitlementSource: EntitlementSource,
    private val purchaseLauncher: PurchaseLauncher,
    private val entitlementGate: EntitlementGate,
) : ViewModel() {

    private val _state = MutableStateFlow(UpgradeUiState())
    val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

    init {
        // Surface the catalog as it arrives.
        viewModelScope.launch {
            entitlementSource.products.collect { products ->
                _state.update { current ->
                    current.copy(
                        products = products,
                        // Default selection prefers founder lifetime → regular
                        // lifetime → annual → monthly. Anchors lifetime as
                        // the recommended choice without hard-coding SKUs in
                        // the UI layer.
                        selectedSku = current.selectedSku ?: products.preferredDefaultSku(),
                        isLoading = products.isEmpty() && current.isLoading,
                    )
                }
            }
        }

        // Auto-dismiss the sheet once the entitlement flips to Pro — covers
        // both fresh-purchase and restore-purchases paths uniformly.
        viewModelScope.launch {
            entitlementGate.isProEntitled.collect { entitled ->
                if (entitled) _state.update { it.copy(didBecomePro = true) }
            }
        }

        // Kick off a fresh catalog + entitlement read.
        refresh()
    }

    fun onSelectPlan(sku: String) {
        _state.update { it.copy(selectedSku = sku) }
    }

    /**
     * User tapped the CTA. Launches the Play purchase flow for whichever SKU
     * is currently selected. Outcome lands via the gateway's purchase
     * listener — which updates [EntitlementGate.isProEntitled] which triggers
     * the auto-dismiss collector above.
     */
    fun onPurchase(activity: Activity) {
        val sku = _state.value.selectedSku ?: return
        viewModelScope.launch {
            _state.update { it.copy(isPurchasing = true, errorMessage = null) }
            val result = purchaseLauncher.launchPurchase(activity, sku)
            handlePurchaseResult(result)
        }
    }

    /**
     * Settings → "Restore Purchases" entry-point. Re-queries Play; outcome
     * is observed via [EntitlementGate].
     */
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
            // Success / Pending close the flow; isPro lands via the
            // entitlement collector, which dismisses the sheet.
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
            // Any other state from refresh is unexpected (refresh shouldn't
            // produce Pending / UserCancelled) — fall back to a generic state.
            else -> _state.update { it.copy(isLoading = false, isPurchasing = false) }
        }
    }

    private fun List<com.pennywiseai.tracker.billing.ProProduct>.preferredDefaultSku(): String? {
        val skus = map { it.sku }.toSet()
        return when {
            ProSku.LIFETIME_FOUNDER in skus -> ProSku.LIFETIME_FOUNDER
            ProSku.LIFETIME in skus -> ProSku.LIFETIME
            ProSku.ANNUAL in skus -> ProSku.ANNUAL
            ProSku.MONTHLY in skus -> ProSku.MONTHLY
            else -> firstOrNull()?.sku
        }
    }
}
