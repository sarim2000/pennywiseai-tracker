package com.pennywiseai.tracker.billing

import com.pennywiseai.tracker.billing.license.LicenseManager
import com.pennywiseai.tracker.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth feature gates depend on: "does this user have
 * access to Pro features right now?" Pro comes from either rail —
 * [EntitlementSource.isPro] (Play purchase, or always-true on F-Droid) OR
 * [LicenseManager.isLicensed] (a website-sold Dodo license key). Feature
 * gates depend on this gate, not on either source, so adding a rail didn't
 * touch a single gate site.
 *
 * Earlier drafts mixed in a "legacy grandfather" rule that auto-unlocked
 * Pro for installs that predated the Pro release. We dropped it in favour
 * of the founder SKU (lifetime at a one-time-only discount), which gives
 * existing users a better deal AND keeps the revenue line honest — a
 * blanket "everyone with prior usage is free forever" leaves no path to
 * monetise the most engaged segment.
 */
@Singleton
class EntitlementGate @Inject constructor(
    entitlementSource: EntitlementSource,
    licenseManager: LicenseManager,
    @ApplicationScope scope: CoroutineScope,
) {

    /** Null while caches load, so background schedules do not treat startup as revocation. */
    val resolvedProEntitlement: StateFlow<Boolean?> =
        combine(entitlementSource.isPro, entitlementSource.isInitialized,
            licenseManager.resolvedLicenseEntitlement) { play, initialized, license ->
            when {
                play || license == true -> true
                initialized && license != null -> false
                else -> null
            }
        }.stateIn(scope, SharingStarted.Eagerly,
            if (entitlementSource.isPro.value || licenseManager.isLicensed.value) true else null)

    /** `true` when the user owns an active Pro SKU or a valid license key. */
    val isProEntitled: StateFlow<Boolean> =
        resolvedProEntitlement.map { it == true }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                entitlementSource.isPro.value || licenseManager.isLicensed.value,
            )
}
