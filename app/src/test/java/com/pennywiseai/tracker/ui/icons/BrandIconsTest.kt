package com.pennywiseai.tracker.ui.icons

import com.pennywiseai.tracker.R
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandIconsTest {
    @Test fun `banks named "of India" get their own logo, not Bank of India's`() {
        assertEquals(R.drawable.ic_brand_union_bank, BrandIcons.getIconResource("Union Bank of India"))
        assertEquals(R.drawable.ic_brand_sbi, BrandIcons.getIconResource("State Bank of India"))
        assertEquals(R.drawable.ic_brand_central_bank_of_india, BrandIcons.getIconResource("Central Bank of India"))
        assertEquals(R.drawable.ic_brand_bank_of_india, BrandIcons.getIconResource("Bank of India"))
    }
}
