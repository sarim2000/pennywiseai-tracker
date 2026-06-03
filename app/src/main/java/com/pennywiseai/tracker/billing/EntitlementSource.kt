package com.pennywiseai.tracker.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of billing state. Feature gates depend on this — never on
 * the full [PurchaseGateway] — so they're not coupled to launch / refresh
 * machinery they don't use (Interface Segregation).
 *
 * Both implementations expose the same shape:
 *  - `PlayBillingGateway` (standard flavor) tracks the Google account's
 *    Play purchases.
 *  - `FdroidBillingGateway` (fdroid flavor) emits `isPro = true` and an
 *    empty product catalog (F-Droid users have no paywall by design).
 */
interface EntitlementSource {

    /**
     * Current Pro-entitlement state. `true` if the user has any active
     * Pro SKU (lifetime owned, or active subscription not in on-hold /
     * expired). Cached in DataStore so the first frame after cold start
     * doesn't flicker `false → true` while BillingClient connects.
     */
    val isPro: StateFlow<Boolean>

    /** Localized product catalog the paywall renders. Empty until refresh resolves. */
    val products: StateFlow<List<ProProduct>>
}
