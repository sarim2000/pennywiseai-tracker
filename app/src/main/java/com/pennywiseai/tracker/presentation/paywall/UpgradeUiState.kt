package com.pennywiseai.tracker.presentation.paywall

/**
 * Immutable UI state for the upgrade sheet. ViewModel emits this; Compose
 * renders it. No business logic lives here.
 */
data class UpgradeUiState(
    /** Initial entitlement load in flight. */
    val isLoading: Boolean = true,
    /** A Restore is in flight. */
    val isPurchasing: Boolean = false,
/** Why the last Restore failed. Shown under the sheet; cleared when one starts. */
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
     * a license key activated, or Restore finding a Play entitlement).
     * The UI swaps to a celebration view; the actual dismiss is fired
     * through [UpgradeViewModel.events] (one-shot Channel) — NOT via
     * sticky state, so a stale VM doesn't re-trigger dismiss the next
     * time the sheet opens.
     */
    val showCelebration: Boolean = false,
    /** Website license (Dodo key) currently activated on this device, if any. */
    val licenseProductName: String? = null,
    val isLicensed: Boolean = false,
    /** License-key entry dialog. */
    val showLicenseDialog: Boolean = false,
    val isActivating: Boolean = false,
    val licenseError: String? = null,
    /** Key is active on another phone; offer "Move to this device" (needs the move endpoint). */
    val licenseCanMove: Boolean = false,
)

/** One-shot UI events emitted by [UpgradeViewModel]. */
sealed class UpgradeEvent {
    /** The sheet should hide. Fired after celebration completes. */
    data object Dismiss : UpgradeEvent()
}
