package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.billing.FdroidBillingGateway
import com.pennywiseai.tracker.billing.PurchaseGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the F-Droid flavor: [PurchaseGateway] → always-Pro stub.
 * The standard flavor has its own [BillingModule] under `app/src/standard/`
 * that binds to the real Play implementation. The shared `main` sources
 * never reference either concrete class — they only see the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindPurchaseGateway(impl: FdroidBillingGateway): PurchaseGateway
}
