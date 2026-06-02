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
    /**
     * True when the user was already Pro at the moment the sheet opened —
     * legacy-grandfathered or owns a Pro SKU. Drives the "Active" content
     * variant (status + manage-subscription + restore) instead of the plan
     * cards + buy CTA. Captured once on init; never recomputed.
     */
    val isAlreadyEntitled: Boolean = false,
    /**
     * Set when entitlement TRANSITIONS from false to true mid-sheet (i.e.
     * a fresh purchase or a restore-purchases finding an entitlement). The
     * UI consumes this to dismiss; we do NOT set it for users who were Pro
     * at open time, otherwise the sheet would auto-close on frame 1.
     */
    val didBecomePro: Boolean = false,
)
