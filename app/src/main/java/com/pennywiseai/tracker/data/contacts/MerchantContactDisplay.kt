package com.pennywiseai.tracker.data.contacts

/**
 * Render-time helper that swaps a UPI-style merchant for a matching
 * contact name when the user has opted into [useContactsForVpa]. Lives in
 * the data layer so transaction lists, transaction details, the home
 * recent-transactions card, and anywhere else that renders a merchant
 * string can share one consistent rule.
 *
 * No-op when:
 *  - the toggle is off (returns the original merchant)
 *  - the merchant doesn't contain a 10-digit phone (very common: most
 *    merchants are plain text like "AMAZON" or "SWIGGY")
 *  - the contact lookup misses (returns the original merchant)
 *
 * VPA phone extraction handles the common Indian-UPI shapes:
 *   - `9876543210@paytm`                → 9876543210
 *   - `Q987654321@ybl` (PhonePe prefix) → 987654321 (only 9 digits, skipped)
 *   - `9876543210`                       → 9876543210 (bare number)
 *   - `+919876543210@paytm`              → 9876543210 (drop country code)
 *
 * We require exactly 10 digits because that's the Indian mobile-number
 * length; PhoneLookup will normalise away any leading +91 from the
 * contact side.
 */
fun displayMerchantName(
    merchant: String?,
    useContactsForVpa: Boolean,
    resolver: ContactsResolver
): String? {
    if (merchant.isNullOrBlank()) return merchant
    if (!useContactsForVpa) return merchant
    val phone = extractIndianMobile(merchant) ?: return merchant
    return resolver.resolve(phone) ?: merchant
}

// Compiled once. Captures an optional +91 prefix followed by exactly 10
// digits. Lookbehind / lookahead reject longer digit runs without
// false-positive-ing on the underscores common in Indian VPAs (\b would).
private val INDIAN_MOBILE_REGEX = Regex("""(?<!\d)(?:\+?91)?(\d{10})(?!\d)""")

/**
 * Pulls a 10-digit Indian mobile number out of [text] if one is present.
 * Returns the last 10 digits of the matched run so a `+91` prefix is
 * silently dropped. Returns null when no 10-digit (or 12-digit
 * country-code) run is present.
 */
internal fun extractIndianMobile(text: String): String? {
    val match = INDIAN_MOBILE_REGEX.find(text) ?: return null
    return match.groupValues[1]
}
