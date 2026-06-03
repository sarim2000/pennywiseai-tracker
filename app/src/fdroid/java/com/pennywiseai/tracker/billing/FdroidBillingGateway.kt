package com.pennywiseai.tracker.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid build's [PurchaseGateway]: every user is Pro, no products to sell,
 * no flow to launch. F-Droid's inclusion criteria forbid the proprietary
 * Play Billing library, and the audience that opts into F-Droid has self-
 * selected away from IAP — making them all "Pro" is the only sane behavior
 * and aligns with AGPL's spirit.
 *
 * Honors the [PurchaseGateway] interface without throwing (Liskov) by
 * returning [PurchaseResult.Unsupported] from the write-side methods.
 */
@Singleton
class FdroidBillingGateway @Inject constructor() : PurchaseGateway {

    override val isPro: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    override val products: StateFlow<List<ProProduct>> =
        MutableStateFlow(emptyList<ProProduct>()).asStateFlow()

    override suspend fun refresh(): PurchaseResult = PurchaseResult.Unsupported

    override suspend fun launchPurchase(
        activity: Activity,
        product: ProProduct,
    ): PurchaseResult = PurchaseResult.Unsupported
}
