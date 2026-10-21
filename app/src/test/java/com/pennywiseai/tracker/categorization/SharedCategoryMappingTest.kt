package com.pennywiseai.tracker.categorization

import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * First-guess category quality (#678).
 *
 * The keyword lists are brand-heavy, which left everyday Indian eateries
 * uncategorised while a handful of single common words quietly claimed
 * unrelated merchants. Both directions are pinned here: a keyword that is
 * too narrow costs a guess, one that is too broad costs a *wrong* guess,
 * and the wrong guess is the one users complain about.
 *
 * Rule order matters — the first matching rule wins — so anything added to
 * FOOD is also being taken away from every category below it.
 */
class SharedCategoryMappingTest {

    private fun assertCategory(expected: String, merchant: String) =
        assertEquals("merchant: $merchant", expected, SharedCategoryMapping.getCategory(merchant))

    // --- everyday eateries that used to fall through to "Others" ---

    @Test
    fun `generic Indian food vocabulary is recognised`() {
        // Canteens and bakeries were named in Play review feedback.
        assertCategory("Food & Dining", "IIT CANTEEN")
        assertCategory("Food & Dining", "MONGINIS BAKERY")
        assertCategory("Food & Dining", "SHARMA DHABA")
        assertCategory("Food & Dining", "SRI SAI SWEETS")
        assertCategory("Food & Dining", "PARAGON BIRYANI")
        assertCategory("Food & Dining", "SARAVANA IDLI DOSA")
        assertCategory("Food & Dining", "CHAI POINT")
        assertCategory("Food & Dining", "SHREE TIFFIN SERVICE")
        assertCategory("Food & Dining", "GUPTA JUICE CENTRE")
        assertCategory("Food & Dining", "NATURALS ICE CREAM")
        assertCategory("Food & Dining", "WOW MOMOS")
    }

    @Test
    fun `established brands still match`() {
        assertCategory("Food & Dining", "SWIGGY")
        assertCategory("Food & Dining", "ZOMATO ONLINE")
        assertCategory("Food & Dining", "DOMINOS PIZZA")
        assertCategory("Groceries", "BIGBASKET")
        assertCategory("Groceries", "BLINKIT")
        assertCategory("Groceries", "DMART")
    }

    // --- words that were claiming merchants they had no business claiming ---

    @Test
    fun `a locality named Salt Lake is not a restaurant`() {
        // "salt" matched any merchant in Kolkata's Salt Lake.
        assertCategory("Healthcare", "SALT LAKE MEDICAL STORE")
    }

    @Test
    fun `a chemist or lab pulled out of Shopping lands in Healthcare`() {
        // Every word in SHOPPING_EXCLUDE must be a Healthcare keyword too,
        // or excluding it just drops the merchant into Others.
        assertCategory("Healthcare", "SHARMA CHEMIST MART")
        assertCategory("Healthcare", "GUPTA CHEMISTS")
        assertCategory("Healthcare", "THYROCARE DIAGNOSTIC CENTRE")
        assertCategory("Healthcare", "LAL PATH DIAGNOSTICS")
    }

    @Test
    fun `Zoom is not a grocery store`() {
        // "zoom" is a UAE convenience brand, but the bare word took the
        // video-conferencing subscription with it.
        assertEquals(
            "the multi-word brand should still match",
            "Groceries",
            SharedCategoryMapping.getCategory("ZOOM SITE")
        )
        val zoomVideo = SharedCategoryMapping.getCategory("Zoom Video Communications")
        assertEquals("Others", zoomVideo)
    }

    @Test
    fun `the word more does not make everything a grocery run`() {
        assertCategory("Groceries", "MORE MEGASTORE")
        assertCategory("Others", "buy more save more")
    }

    @Test
    fun `ordinary words are not food`() {
        assertCategory("Shopping", "NALA TEXTILES")
        assertCategory("Personal Care", "CRAVINGS SALON")
    }

    // --- income routing is unchanged ---

    @Test
    fun `income keeps its own routing`() {
        assertEquals("Salary", SharedCategoryMapping.determineCategory("ACME SALARY", "INCOME"))
        assertEquals("Refunds", SharedCategoryMapping.determineCategory("AMAZON REFUND", "INCOME"))
        assertEquals("Income", SharedCategoryMapping.determineCategory("IIT CANTEEN", "INCOME"))
    }

    @Test
    fun `an unknown merchant still falls back to Others`() {
        assertCategory("Others", "QWERTYUIOP PRIVATE LIMITED")
    }

    // --- Jordan: 71% of one real ledger fell through to "Others" ---

    @Test
    fun `Jordanian shops and services are recognised`() {
        assertCategory("Groceries", "HYPERMAX - SWEIFIEH")
        assertCategory("Groceries", "AL NOOR SUPERMARKET")
        assertCategory("Groceries", "SUPER MARKET AL AMAL")
        assertCategory("Groceries", "LUMI MARKET / KHALDA")
        assertCategory("Groceries", "MILITARY CONSUMER EST MARKA")
        assertCategory("Food & Dining", "AL SAFA BAKERIES")
        assertCategory("Food & Dining", "THE CAKE SHOP")
        assertCategory("Shopping", "www.shein.com")
        assertCategory("Shopping", "AL AMAL STORES")
        assertCategory("Shopping", "AL RAWDA HOME CENTER")
        assertCategory("Transportation", "AMMAN VISION TRANSPORT")
        assertCategory("Mobile", "ZAIN WEBSITE AND SELFC")
        assertCategory("Mobile", "ORANGE MOBILE")
        assertCategory("Mobile", "UMNIAH E-PAY")
        assertCategory("Entertainment", "PRIME CINEMAS AL ABDALI")
        assertCategory("Entertainment", "TELEGRAM PREMIUM")
    }

    @Test
    fun `a person called Zain is not a phone bill`() {
        assertCategory("Others", "ZAIN AHMAD")
        // "marketing" is not a market, and Al-Manaseer is a family before it's a fuel chain.
        assertCategory("Others", "DIGITAL MARKETING CO")
        assertCategory("Others", "MOHAMMAD AL MANASEER")
    }

    @Test
    fun `market keeps off pharmacies, marketplaces and funds`() {
        assertCategory("Healthcare", "MARKET PHARMACY")
        assertCategory("Shopping", "AMAZON MARKET PLACE")
        assertCategory("Groceries", "CARREFOUR")
    }
}
