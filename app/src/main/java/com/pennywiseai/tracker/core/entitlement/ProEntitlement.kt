package com.pennywiseai.tracker.core.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only "may this user use Pro right now?" for code below `billing`
 * (`data`, workers). `billing.EntitlementGate` implements it and
 * `di/EntitlementContractsModule` binds it, so the data layer gates on Pro
 * without importing `billing`.
 */
interface ProEntitlement {

    /**
     * `null` while the Play and license caches load, then `true` or `false`.
     * Background schedules wait on `null` instead of treating startup as a
     * revocation.
     */
    val resolvedProEntitlement: StateFlow<Boolean?>

    /** `true` only when entitlement resolved to Pro. Unresolved reads as `false`. */
    val isProEntitled: StateFlow<Boolean>
}
