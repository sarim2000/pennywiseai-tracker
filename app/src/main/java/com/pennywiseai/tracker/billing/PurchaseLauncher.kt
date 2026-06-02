package com.pennywiseai.tracker.billing

import android.app.Activity

/**
 * Write-side of billing. Only the paywall and the "Restore Purchases" entry
 * in Settings depend on this. Feature gates read from [EntitlementSource];
 * they never need to launch a flow themselves.
 */
interface PurchaseLauncher {

    /**
     * Re-queries Play for the user's current entitlements and product catalog.
     * Called automatically on cold start. Expose manually via Settings →
     * "Restore Purchases" so users who reinstalled / formatted / changed
     * Google account can force a re-check.
     */
    suspend fun refresh(): PurchaseResult

    /**
     * Kicks off the Google Play purchase sheet for [productId]. The actual
     * entitlement update arrives asynchronously through Play Billing's
     * `PurchasesUpdatedListener`, which feeds back into [EntitlementSource.isPro].
     */
    suspend fun launchPurchase(activity: Activity, productId: String): PurchaseResult
}
