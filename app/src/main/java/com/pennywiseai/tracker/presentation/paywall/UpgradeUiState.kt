package com.pennywiseai.tracker.presentation.paywall

import com.pennywiseai.tracker.billing.ProProduct

/**
 * Immutable UI state for the upgrade sheet. ViewModel emits this; Compose
 * renders it. No business logic lives here.
 */
data class UpgradeUiState(
    val products: List<ProProduct> = emptyList(),
    val selectedSku: String? = null,
    /** Initial product/entitlement load in flight. Drives the skeleton view. */
    val isLoading: Boolean = true,
    /** `launchBillingFlow` in flight. Disables the CTA + shows spinner. */
    val isPurchasing: Boolean = false,
    /** Surfaced as snackbar; cleared by [UpgradeViewModel.onErrorDismissed]. */
    val errorMessage: String? = null,
    /** Set by the gateway when Pro entitlement becomes true. UI consumes it
     *  to dismiss the sheet. */
    val didBecomePro: Boolean = false,
)
