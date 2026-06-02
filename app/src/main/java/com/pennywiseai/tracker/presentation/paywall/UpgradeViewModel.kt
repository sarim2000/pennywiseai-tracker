package com.pennywiseai.tracker.presentation.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.billing.EntitlementSource
import com.pennywiseai.tracker.billing.ProProduct
import com.pennywiseai.tracker.billing.PurchaseLauncher
import com.pennywiseai.tracker.billing.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
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
) : ViewModel() {

    private val initialEntitled = entitlementGate.isProEntitled.value

    private val _state = MutableStateFlow(UpgradeUiState(isAlreadyEntitled = initialEntitled))
    val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

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

        // Auto-dismiss ONLY on a fresh false → true transition (purchase
        // succeeded or restore landed an entitlement mid-sheet). drop(1)
        // skips the StateFlow's replay of the current value, so a user
        // who was already Pro when they opened the sheet doesn't see it
        // auto-close on frame 1.
        viewModelScope.launch {
            entitlementGate.isProEntitled
                .drop(1)
                .filter { it }
                .collect { _state.update { ui -> ui.copy(didBecomePro = true) } }
        }

        refresh()
    }

    fun onSelectPlan(key: String) {
        _state.update { it.copy(selectedKey = key) }
    }

    /**
     * User tapped the CTA. Resolves the selected key to a [ProProduct] and
     * hands it to the gateway — which needs the offer token (subscription
     * base plans) and the SKU together. Outcome lands via the gateway's
     * purchase listener → [EntitlementGate.isProEntitled] → auto-dismiss
     * collector above.
     */
    fun onPurchase(activity: Activity) {
        val state = _state.value
        val product = state.products.firstOrNull { it.key == state.selectedKey } ?: return
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
