import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.IconProvider
import com.pennywiseai.tracker.ui.icons.IconResource

class CategoryMappingTest {

    @Test
    fun testCategoryMapping() {
        // Test some key merchants from each category
        assertEquals("Food & Dining", getCategory("Piri Piri Flaming Grill"))
        assertEquals("Food & Dining", getCategory("Naixue"))
        assertEquals("Food & Dining", getCategory("BN-Icon Siam"))
        assertEquals("Food & Dining", getCategory("Amici"))

        assertEquals("Food & Dining", getCategory("PF Changs"))
        assertEquals("Food & Dining", getCategory("Bombay Bungalow"))
        assertEquals("Food & Dining", getCategory("Heytea"))
        assertEquals("Food & Dining", getCategory("Google Nomadtable"))
        assertEquals("Food & Dining", getCategory("GHL*JHAROKA BY INDUS BANGKOK  11 TH"))
        assertEquals("Food & Dining", getCategory("GHLJHAROKA BY INDUS BANGKOK  11 TH"))
        assertEquals("Food & Dining", getCategory("Ksher  *AKARASKYROOFTOBangkok TH"))


        assertEquals("Groceries", getCategory("7-11"))

        assertEquals("Transportation", getCategory("Airports of Thailand"))
        assertEquals("Transportation", getCategory("Expressway"))
        assertEquals("Transportation", getCategory("Grab A-123"))
        assertEquals("Transportation", getCategory("SATS T1"))
        assertEquals("Transportation", getCategory("Pyxbolt Services"))

        assertEquals("Shopping", getCategory("Uniqlo TRX"))
        assertEquals("Shopping", getCategory("Lyn"))
        assertEquals("Shopping", getCategory("Apple Central World"))
        assertEquals("Shopping", getCategory("OpenAI ChatGPT"))
        assertEquals("Shopping", getCategory("Sukhumvit City Mall"))
        assertEquals("Shopping", getCategory("The Emsphere"))
        assertEquals("Shopping", getCategory("Central World"))
        assertEquals("Shopping", getCategory("The Empire Tower"))

        assertEquals("Entertainment", getCategory("Major Cineplex"))
        assertEquals("Entertainment", getCategory("Ticketmelon"))
        assertEquals("Entertainment", getCategory("2C2P Major Cineplex"))

        assertEquals("Travel", getCategory("Four Points by Sheraton"))
        assertEquals("Travel", getCategory("Crowne Plaza KLCC"))
        assertEquals("Travel", getCategory("Dusit Thani Bangkok"))
        assertEquals("Travel", getCategory("Marina Bay Sands"))
        assertEquals("Travel", getCategory("Hilton Garden Inn"))
        assertEquals("Travel", getCategory("WESTIN KL-FRONT OFFICE KUALA LUMPUR MY MY"))
        assertEquals("Travel", getCategory("WWW.MAGNOLIASSERVICEDRBANGKOK TH"))
        assertEquals("Travel", getCategory("FOUR POINTS BY SHERATOBANGKOK 11 TH"))


        assertEquals("Healthcare", getCategory("Life Pharm"))
        assertEquals("Healthcare", getCategory("Bumrungrad"))
        assertEquals("Healthcare", getCategory("Medex"))
        assertEquals("Healthcare", getCategory("BOOTS_4287 C.WORLD 3 FBANGKOK TH"))

        assertEquals("Personal Care", getCategory("Sultans of Shave"))
        assertEquals("Personal Care", getCategory("Mandarin Oriental Spa"))
        assertEquals("Personal Care", getCategory("Truefitt and Hill"))
        assertEquals("Personal Care", getCategory("Phetsathorn Co.,Ltd."))

        assertEquals("Tax", getCategory("Abu Dhabi Judicial Dep"))
        assertEquals("Tax", getCategory("Sharjah Finance Depart"))

        assertEquals("Bills & Utilities", getCategory("Tamdeed Projects"))
        assertEquals("Bills & Utilities", getCategory("WWW.PAYSOLUT*WWW.PAYSOBANGKOK  TH"))

        assertEquals("Banking", getCategory("My Fatoorah"))
        assertEquals("Banking", getCategory("Transfer: 002 → 001"))
        assertEquals("Banking", getCategory("Transfer from 001 to 002"))
        assertEquals("Banking", getCategory("Transfer to 0001"))

        // Test some should remain as Others (too generic)
        assertEquals("Others", getCategory("Twin Made"))
    }

    // ---------------------------------------------------------------------------
    // IconProvider.getTransactionIcon
    // ---------------------------------------------------------------------------

    @Test
    fun testGetTransactionIcon_noCategory_derivesFromMerchant() {
        // null category → derive from merchant name (Food & Dining)
        val result = IconProvider.getTransactionIcon("Piri Piri Flaming Grill", null)
        val iconResult: IconResource.VectorIcon = result as? IconResource.VectorIcon
            ?: throw AssertionError("Expected VectorIcon but got $result")
        val foodInfo = CategoryMapping.categories["Food & Dining"]!!
        assertEquals(foodInfo.icon, iconResult.icon)
        assertEquals(foodInfo.color, iconResult.tint)
    }

    @Test
    fun testGetTransactionIcon_withCategory_usesGivenCategory() {
        // "7-11" derives as "Groceries", but override forces "Transportation"
        val result = IconProvider.getTransactionIcon("7-11", "Transportation")
        val iconResult: IconResource.VectorIcon = result as? IconResource.VectorIcon
            ?: throw AssertionError("Expected VectorIcon but got $result")
        val transportInfo = CategoryMapping.categories["Transportation"]!!
        assertEquals(transportInfo.icon, iconResult.icon)
        assertEquals(transportInfo.color, iconResult.tint)
    }

    @Test
    fun testGetTransactionIcon_emptyCategory_fallsBackToDerived() {
        // Empty string → derive from merchant name
        val result = IconProvider.getTransactionIcon("Piri Piri Flaming Grill", "")
        val iconResult: IconResource.VectorIcon = result as? IconResource.VectorIcon
            ?: throw AssertionError("Expected VectorIcon but got $result")
        val foodInfo = CategoryMapping.categories["Food & Dining"]!!
        assertEquals(foodInfo.icon, iconResult.icon)
    }

    @Test
    fun testGetTransactionIcon_uncategorized_fallsBackToDerived() {
        // "Uncategorized" → derive from merchant name
        val result = IconProvider.getTransactionIcon("Piri Piri Flaming Grill", "Uncategorized")
        val iconResult: IconResource.VectorIcon = result as? IconResource.VectorIcon
            ?: throw AssertionError("Expected VectorIcon but got $result")
        val foodInfo = CategoryMapping.categories["Food & Dining"]!!
        assertEquals(foodInfo.icon, iconResult.icon)
    }

    @Test
    fun testGetTransactionIcon_brandIconTakesPriority() {
        // "Starbucks" has a brand drawable → returns DrawableResource even with override
        val result = IconProvider.getTransactionIcon("Starbucks", "Transportation")
        assertTrue(result is IconResource.DrawableResource, "Expected DrawableResource for a known brand")
    }

    @Test
    fun testGetTransactionIcon_nonExistentCategory_fallsBackToOthers() {
        // Non-existent category name → fallback to "Others"
        val result = IconProvider.getTransactionIcon("7-11", "NonExistentCategory")
        val iconResult: IconResource.VectorIcon = result as? IconResource.VectorIcon
            ?: throw AssertionError("Expected VectorIcon but got $result")
        val othersInfo = CategoryMapping.categories["Others"]!!
        assertEquals(othersInfo.icon, iconResult.icon)
        assertEquals(othersInfo.color, iconResult.tint)
    }

    @Test
    fun testGetTransactionIcon_overrideToOthers_usesOthers() {
        val result = IconProvider.getTransactionIcon("Piri Piri Flaming Grill", "Others")
        val iconResult: IconResource.VectorIcon = result as? IconResource.VectorIcon
            ?: throw AssertionError("Expected VectorIcon but got $result")
        val othersInfo = CategoryMapping.categories["Others"]!!
        assertEquals(othersInfo.icon, iconResult.icon)
        assertEquals(othersInfo.color, iconResult.tint)
    }
}

// Helper function to test categorization
private fun getCategory(merchantName: String): String {
    return com.pennywiseai.tracker.ui.icons.CategoryMapping.getCategory(merchantName)
}
