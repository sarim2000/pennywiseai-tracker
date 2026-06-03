package com.pennywiseai.tracker.billing

/**
 * Outcome of a [PurchaseLauncher.refresh] or [PurchaseLauncher.launchPurchase]
 * call. Sealed so callers handle every case explicitly — including the
 * F-Droid build's [Unsupported], which lets that flavor honor the interface
 * (Liskov) without throwing.
 */
sealed class PurchaseResult {

    /** Operation succeeded. Entitlement state has been refreshed downstream. */
    data object Success : PurchaseResult()

    /** User cancelled the Play purchase sheet. Not an error. */
    data object UserCancelled : PurchaseResult()

    /**
     * Purchase is awaiting an out-of-band payment method (cash at counter,
     * UPI Collect, etc). Entitlement should NOT be granted yet. Play will
     * fire a fresh purchase update when the payment completes.
     */
    data object Pending : PurchaseResult()

    /** Billing service couldn't connect, network down, or Play out-of-date. */
    data class ServiceUnavailable(val code: Int, val debugMessage: String?) : PurchaseResult()

    /** Generic billing failure (developer error, item unavailable, etc). */
    data class Failed(val code: Int, val debugMessage: String?) : PurchaseResult()

    /**
     * Billing isn't available in this build — i.e. the F-Droid flavor.
     * Callers can no-op or surface a "Pro is auto-unlocked in this build"
     * message.
     */
    data object Unsupported : PurchaseResult()
}
