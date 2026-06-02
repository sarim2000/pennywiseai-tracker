package com.pennywiseai.tracker.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play Billing 9 implementation. Single [BillingClient] instance per
 * process (Google's docs are strict about this — multiple clients cause
 * duplicate callbacks).
 *
 * Threading: all suspend functions hop to [Dispatchers.IO] before touching
 * BillingClient. StateFlow emissions are thread-safe via MutableStateFlow.
 *
 * Lifecycle: the client is started lazily on first read. We don't bind to
 * Activity / lifecycle here — the whole API is process-scoped and the
 * `enableAutoServiceReconnection()` flag (added in Billing v8) handles
 * transient disconnects without ceremony.
 *
 * Pro UX: on construction we publish the cached `isPro` from DataStore
 * immediately so the first frame after cold start doesn't flicker
 * `false → true`. The real value lands once the first
 * `queryPurchasesAsync` resolves.
 */
@Singleton
class PlayBillingGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : PurchaseGateway {

    private val _isPro = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _products = MutableStateFlow<List<ProProduct>>(emptyList())
    override val products: StateFlow<List<ProProduct>> = _products.asStateFlow()

    /** Cached `ProductDetails` keyed by SKU. Required to launch a purchase. */
    private val productDetailsCache = mutableMapOf<String, ProductDetails>()
    private val productDetailsMutex = Mutex()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        applicationScope.launch {
            handlePurchasesUpdated(result, purchases)
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    private val connectionMutex = Mutex()

    init {
        // Hydrate the cached `isPro` so UI doesn't flicker on cold start.
        // Genuine verification follows when `refresh()` lands.
        applicationScope.launch {
            _isPro.value = preferences.proCachedIsPro.first()
        }
    }

    override suspend fun refresh(): PurchaseResult = withContext(Dispatchers.IO) {
        when (val connect = ensureConnected()) {
            is PurchaseResult.Success -> {
                // Refresh both halves of state in parallel — products for the
                // paywall, purchases for the entitlement flag.
                queryProductsInternal()
                queryPurchasesInternal()
                PurchaseResult.Success
            }
            else -> connect
        }
    }

    override suspend fun launchPurchase(
        activity: Activity,
        productId: String,
    ): PurchaseResult = withContext(Dispatchers.Main) {
        val connect = ensureConnected()
        if (connect !is PurchaseResult.Success) return@withContext connect

        // ProductDetails must be fresh per Google's guidance — re-query if
        // we don't have it cached (e.g. paywall opened before refresh).
        val details = productDetailsMutex.withLock { productDetailsCache[productId] }
            ?: run {
                queryProductsInternal()
                productDetailsMutex.withLock { productDetailsCache[productId] }
            }
            ?: return@withContext PurchaseResult.Failed(
                code = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                debugMessage = "Product details for $productId unavailable",
            )

        val params = buildFlowParams(details) ?: return@withContext PurchaseResult.Failed(
            code = BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            debugMessage = "Could not build BillingFlowParams for $productId",
        )

        val launchResult = billingClient.launchBillingFlow(activity, params)
        when (launchResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> PurchaseResult.Success
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.UserCancelled
            else -> PurchaseResult.Failed(launchResult.responseCode, launchResult.debugMessage)
        }
    }

    // region: connection

    /**
     * Awaits a ready BillingClient. Cheap on subsequent calls — the client
     * tracks its own state and `startConnection` is a no-op when already
     * connected.
     */
    private suspend fun ensureConnected(): PurchaseResult = connectionMutex.withLock {
        if (billingClient.isReady) return@withLock PurchaseResult.Success

        suspendCancellableCoroutine<PurchaseResult> { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (!cont.isActive) return
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        cont.resume(PurchaseResult.Success)
                    } else {
                        cont.resume(
                            PurchaseResult.ServiceUnavailable(
                                billingResult.responseCode,
                                billingResult.debugMessage,
                            ),
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // No-op: enableAutoServiceReconnection() means the
                    // library reconnects on the next API call. The original
                    // suspendCancellableCoroutine already resumed (or not),
                    // and subsequent operations will re-enter ensureConnected.
                }
            })
        }
    }

    // endregion

    // region: query

    private suspend fun queryProductsInternal() {
        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProSku.SUBSCRIPTIONS.map { sku ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(sku)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()

        val inappParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProSku.ONE_TIME.map { sku ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(sku)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()

        val subResult = billingClient.queryProductDetails(subParams)
        val inappResult = billingClient.queryProductDetails(inappParams)

        val all = buildList {
            subResult.productDetailsList?.let { addAll(it) }
            inappResult.productDetailsList?.let { addAll(it) }
        }

        productDetailsMutex.withLock {
            productDetailsCache.clear()
            all.forEach { productDetailsCache[it.productId] = it }
        }

        _products.value = all.mapNotNull { it.toProProduct() }
    }

    private suspend fun queryPurchasesInternal() {
        val subPurchases = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
        )
        val inappPurchases = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )

        val allPurchases = buildList {
            subPurchases.purchasesList.let { addAll(it) }
            inappPurchases.purchasesList.let { addAll(it) }
        }

        // Acknowledge any purchased-but-unacked entitlement. Failing to
        // acknowledge within 3 days auto-refunds — we ack as soon as we see
        // a purchase, even if we discovered it via a restore-purchases flow.
        allPurchases
            .filter {
                it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged
            }
            .forEach { acknowledgePurchase(it) }

        val ownsActiveProSku = allPurchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it in ProSku.ALL }
        }

        updateProState(ownsActiveProSku)
    }

    // endregion

    // region: purchase update handler

    private suspend fun handlePurchasesUpdated(
        result: BillingResult,
        purchases: List<Purchase>?,
    ) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            if (!purchase.isAcknowledged) acknowledgePurchase(purchase)
                            if (purchase.products.any { it in ProSku.ALL }) {
                                updateProState(true)
                            }
                        }
                        Purchase.PurchaseState.PENDING -> {
                            // Out-of-band payment (UPI Collect, cash). Do
                            // NOT grant entitlement yet. Play will fire a
                            // fresh update when the payment completes.
                            Log.i(TAG, "Pending purchase: ${purchase.products}")
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit // expected
            else -> Log.w(
                TAG,
                "Purchase update failed: code=${result.responseCode} msg=${result.debugMessage}",
            )
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(
                TAG,
                "Acknowledge failed: code=${result.responseCode} msg=${result.debugMessage}",
            )
        }
    }

    // endregion

    private suspend fun updateProState(isPro: Boolean) {
        _isPro.value = isPro
        preferences.setProCachedIsPro(isPro)
    }

    /**
     * Subscriptions need an offer token; one-time products take the bare
     * ProductDetails. Returns null if we can't determine a valid flow
     * configuration.
     */
    private fun buildFlowParams(details: ProductDetails): BillingFlowParams? {
        val productParams = when (details.productType) {
            BillingClient.ProductType.SUBS -> {
                val offerToken = details.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.offerToken
                    ?: return null
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            }
            BillingClient.ProductType.INAPP -> {
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            }
            else -> return null
        }
        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
    }

    private fun ProductDetails.toProProduct(): ProProduct? {
        return when (productType) {
            BillingClient.ProductType.SUBS -> {
                val offer = subscriptionOfferDetails?.firstOrNull() ?: return null
                val phase = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return null
                ProProduct(
                    sku = productId,
                    type = if (productId == ProSku.ANNUAL) {
                        ProProduct.ProductType.SUBSCRIPTION_ANNUAL
                    } else {
                        ProProduct.ProductType.SUBSCRIPTION_MONTHLY
                    },
                    priceFormatted = phase.formattedPrice,
                    priceMicros = phase.priceAmountMicros,
                    currencyCode = phase.priceCurrencyCode,
                    offerToken = offer.offerToken,
                )
            }
            BillingClient.ProductType.INAPP -> {
                val offer = oneTimePurchaseOfferDetails ?: return null
                ProProduct(
                    sku = productId,
                    type = if (productId == ProSku.LIFETIME_FOUNDER) {
                        ProProduct.ProductType.LIFETIME_FOUNDER
                    } else {
                        ProProduct.ProductType.LIFETIME
                    },
                    priceFormatted = offer.formattedPrice,
                    priceMicros = offer.priceAmountMicros,
                    currencyCode = offer.priceCurrencyCode,
                )
            }
            else -> null
        }
    }

    private companion object {
        const val TAG = "PlayBillingGateway"
    }
}
