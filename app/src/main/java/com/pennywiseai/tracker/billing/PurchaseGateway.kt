package com.pennywiseai.tracker.billing

/**
 * The full billing surface. Concrete impls bind to this so Hilt has a single
 * type to provide; consumers depend on the narrower [EntitlementSource] or
 * [PurchaseLauncher] (Interface Segregation).
 */
interface PurchaseGateway : EntitlementSource, PurchaseLauncher
