package com.pennywiseai.tracker.billing

/**
 * UI-facing model for a single purchasable "plan." Translated from Play's
 * `ProductDetails` so the paywall layer doesn't import billing-library
 * types directly — keeps the UI portable across flavors (F-Droid build
 * never sees `ProductDetails` at compile time because its source set
 * doesn't link to billing-ktx).
 *
 * One [ProProduct] corresponds to one purchasable thing on the paywall:
 *   - A managed-product `pro_lifetime` becomes one [ProProduct]. When a
 *     Play Console Discount offer is active, [originalPriceFormatted]
 *     carries the regular price for strikethrough display while
 *     [priceFormatted] carries the discounted active price.
 *   - The `pro_subscription` SKU expands into TWO ProProducts — one per
 *     base plan (monthly / annual). Each carries its own [basePlanId]
 *     and [offerToken] so the gateway can launch a flow targeted at the
 *     right base plan.
 */
data class ProProduct(
    /** Play product ID. Multiple ProProducts can share the same SKU (subscription base plans). */
    val sku: String,
    /**
     * Subscription base-plan ID (e.g. "monthly", "annual"). Null for
     * managed products, where the SKU itself is the unique identifier.
     */
    val basePlanId: String? = null,
    val type: ProductType,
    /** Localized price string for the currently-active offer (e.g. "₹999", "$11.99"). */
    val priceFormatted: String,
    /** Active-offer price in micros — useful for cross-plan math. */
    val priceMicros: Long,
    /**
     * Strikethrough "original" price for discount visualisation. Set when
     * a Play Console Discount offer is active and the catalog returned
     * both the discounted offer and the base offer; null when there's no
     * comparison to draw.
     */
    val originalPriceFormatted: String? = null,
    val originalPriceMicros: Long? = null,
    /** ISO 4217 currency code (e.g. "INR"). */
    val currencyCode: String,
    /**
     * Offer token Play needs to launch the purchase flow. Required for
     * subscriptions (selects which base plan + offer the user is buying).
     * Optional for managed products — Play resolves the right offer
     * automatically by user eligibility when not supplied.
     */
    val offerToken: String? = null,
) {
    enum class ProductType { SUBSCRIPTION_MONTHLY, SUBSCRIPTION_ANNUAL, LIFETIME }

    /** Stable unique key for UI selection state — distinguishes base plans within a sub. */
    val key: String get() = if (basePlanId != null) "$sku#$basePlanId" else sku

    /** Convenience: true when a Discount offer is actively reducing this product's price. */
    val isDiscounted: Boolean
        get() = originalPriceMicros != null && originalPriceMicros > priceMicros
}

/**
 * Play Console product IDs. Two SKUs only — a single managed `pro_lifetime`
 * (with an optional Discount offer for time-limited founder pricing) and a
 * single subscription `pro_subscription` containing both Monthly and Annual
 * base plans.
 *
 * Founder pricing on lifetime is a Play Console Discount offer, not a
 * separate SKU — the active price + originalPrice come back on [ProProduct]
 * automatically. Retiring the founder window = deactivate the discount
 * offer in Play Console; nothing in code changes.
 */
object ProSku {
    /** Managed one-time product. Owns the founder Discount offer when active. */
    const val LIFETIME = "pro_lifetime"

    /** Single subscription product. Contains both base plans below. */
    const val SUBSCRIPTION = "pro_subscription"

    // Base plan IDs inside [SUBSCRIPTION]. Keep these in sync with the
    // Base plan IDs configured in Play Console.
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_ANNUAL = "annual"

    val SUBSCRIPTIONS = listOf(SUBSCRIPTION)
    val ONE_TIME = listOf(LIFETIME)
    val ALL = SUBSCRIPTIONS + ONE_TIME
}
