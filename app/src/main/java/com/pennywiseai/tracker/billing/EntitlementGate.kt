package com.pennywiseai.tracker.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth feature gates depend on: "does this user have
 * access to Pro features right now?" Combines two orthogonal axes —
 *
 *  - Purchased entitlement ([EntitlementSource.isPro]): user owns a Pro SKU.
 *  - Legacy grandfathering ([LegacyUserDetector.isLegacyUser]): install
 *    predates the Pro release.
 *
 * Either one being true unlocks Pro features. Feature gates never reach
 * for either source directly — they read [isProEntitled] only, so the
 * grandfathering policy can change without touching every gate site.
 */
@Singleton
class EntitlementGate @Inject constructor(
    entitlementSource: EntitlementSource,
    legacyUserDetector: LegacyUserDetector,
    @com.pennywiseai.tracker.di.ApplicationScope applicationScope: CoroutineScope,
) {

    /**
     * `true` when the user has Pro access — by purchase OR by legacy
     * grandfathering. Hot StateFlow shared across observers; recomputed
     * whenever either source updates.
     */
    val isProEntitled: StateFlow<Boolean> = combine(
        entitlementSource.isPro,
        legacyUserDetector.isLegacyUser,
    ) { purchased, legacy -> purchased || legacy }
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
}
