package com.pennywiseai.tracker.presentation.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantKeyForApplyTest {

    @Test
    fun `renaming the merchant in the same edit keys on the original name`() {
        // Future SMS still say "Zomato", so the mapping and tag rule must too.
        assertEquals("Zomato", merchantKeyForApply(originalMerchant = "Zomato", savedMerchant = "Zomato Gold"))
    }

    @Test
    fun `an unrenamed merchant keys on its own name`() {
        assertEquals("Zomato", merchantKeyForApply(originalMerchant = "Zomato", savedMerchant = "Zomato"))
    }

    @Test
    fun `without a loaded original the saved name is used`() {
        assertEquals("Zomato", merchantKeyForApply(originalMerchant = null, savedMerchant = "Zomato"))
    }
}
