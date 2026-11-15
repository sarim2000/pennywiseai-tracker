package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.billing.license.LicenseManager
import com.pennywiseai.tracker.core.entitlement.LicenseRestorer
import com.pennywiseai.tracker.core.entitlement.ProEntitlement
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `core.entitlement` contracts to their `billing` implementations,
 * so `data` reads Pro access and restores licenses without importing `billing`.
 *
 * Lives in `main` for both flavors: [EntitlementGate] and [LicenseManager] are
 * shared code. What differs per flavor is the `EntitlementSource` behind the
 * gate (Play Billing on standard, always-Pro on F-Droid), bound in each
 * flavor's `BillingModule`. Both implementations are `@Singleton`, so these
 * bindings hand out the same instances the rest of the app injects directly.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EntitlementContractsModule {

    @Binds
    abstract fun bindProEntitlement(impl: EntitlementGate): ProEntitlement

    @Binds
    abstract fun bindLicenseRestorer(impl: LicenseManager): LicenseRestorer
}
