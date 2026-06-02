package com.pennywiseai.tracker.billing

/**
 * UI-facing product model. Translated from Play's `ProductDetails` so the
 * paywall layer doesn't import any billing-library types — keeps the UI
 * portable across flavors (F-Droid build never sees `ProductDetails` at
 * compile time because its source set doesn't link to billing-ktx).
 */
data class ProProduct(
    val sku: String,
    val type: ProductType,
    /** Localized price string from Play (e.g. "₹1,999.00", "$24.99"). */
    val priceFormatted: String,
    /** Price in micro-units; useful for computing "save X%" between tiers. */
    val priceMicros: Long,
    /** ISO 4217 currency code (e.g. "INR"). */
    val currencyCode: String,
    /** For subscription products: the offer token Play needs to launch the flow. */
    val offerToken: String? = null,
) {
    enum class ProductType { SUBSCRIPTION_MONTHLY, SUBSCRIPTION_ANNUAL, LIFETIME, LIFETIME_FOUNDER }
}

/**
 * SKU IDs configured in Play Console. Keep in sync with the products you
 * create there; mismatched IDs surface as `unfetchedProductList` entries
 * at runtime.
 *
 * `LIFETIME_FOUNDER` is a separate SKU rather than a discounted lifetime so
 * the founder window can be retired by simply hiding it from the catalog —
 * existing founder buyers keep their entitlement and the main `LIFETIME`
 * SKU's price is never touched.
 */
object ProSku {
    const val MONTHLY = "pro_monthly"
    const val ANNUAL = "pro_annual"
    const val LIFETIME = "pro_lifetime"
    const val LIFETIME_FOUNDER = "pro_lifetime_founder"

    val SUBSCRIPTIONS = listOf(MONTHLY, ANNUAL)
    val ONE_TIME = listOf(LIFETIME, LIFETIME_FOUNDER)
    val ALL = SUBSCRIPTIONS + ONE_TIME
}
