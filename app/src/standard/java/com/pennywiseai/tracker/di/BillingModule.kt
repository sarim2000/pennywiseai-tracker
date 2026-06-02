package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.billing.PlayBillingGateway
import com.pennywiseai.tracker.billing.PurchaseGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the standard (Play Store) flavor: [PurchaseGateway] →
 * Google Play Billing 9 impl. F-Droid has a sibling module under
 * `app/src/fdroid/` binding to the always-Pro stub. Shared `main` code
 * only ever references the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindPurchaseGateway(impl: PlayBillingGateway): PurchaseGateway
}
