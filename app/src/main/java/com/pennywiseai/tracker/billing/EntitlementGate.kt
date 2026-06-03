package com.pennywiseai.tracker.billing

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth feature gates depend on: "does this user have
 * access to Pro features right now?" Currently this is just a passthrough
 * over [EntitlementSource.isPro] — kept as a separate type so feature
 * gates depend on the gate concept, not the billing source, leaving room
 * to layer in other policies (promo entitlements, family-share, etc.)
 * without touching every gate site.
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
) {

    /**
     * `true` when the user owns an active Pro SKU. Hot StateFlow that
     * mirrors [EntitlementSource.isPro] verbatim today.
     */
    val isProEntitled: StateFlow<Boolean> = entitlementSource.isPro
}
