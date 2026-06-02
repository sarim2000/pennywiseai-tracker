package com.pennywiseai.tracker.presentation.paywall

import com.pennywiseai.tracker.billing.ProProduct

/**
 * Immutable UI state for the upgrade sheet. ViewModel emits this; Compose
 * renders it. No business logic lives here.
 */
data class UpgradeUiState(
    val products: List<ProProduct> = emptyList(),
    /**
     * Unique key of the currently-selected plan — uses [ProProduct.key]
     * (e.g. `pro_lifetime` or `pro_subscription#monthly`). Distinguishes
     * subscription base plans, which share an SKU.
     */
    val selectedKey: String? = null,
    /** Initial product/entitlement load in flight. Drives the skeleton view. */
    val isLoading: Boolean = true,
    /** `launchBillingFlow` in flight. Disables the CTA + shows spinner. */
    val isPurchasing: Boolean = false,
    /** Surfaced as snackbar; cleared by [UpgradeViewModel.onErrorDismissed]. */
    val errorMessage: String? = null,
    /**
     * True when the user already owned a Pro SKU at the moment the sheet
     * opened. Drives the "Active" content variant (status + manage-subscription
     * + restore) instead of the plan cards + buy CTA. Captured once on init;
     * never recomputed.
     */
    val isAlreadyEntitled: Boolean = false,
    /**
     * Set when entitlement TRANSITIONS from false to true mid-sheet (i.e.
     * a fresh purchase or a restore-purchases finding an entitlement).
     * The UI swaps to a celebration view; the actual dismiss is triggered
     * later via [didBecomePro]. We never set this for users who were
     * already Pro when the sheet opened.
     */
    val showCelebration: Boolean = false,
    /**
     * Set by the UI once the celebration view has finished (auto-timer or
     * user tapped "Continue"). When this flips true the sheet hides.
     */
    val didBecomePro: Boolean = false,
)
