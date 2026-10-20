package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** #911: the SMS worker's mapping cache must match like the live path's NOCASE query. */
class MerchantMappingLookupTest {

    private fun mapping(name: String, category: String, minute: Int) = MerchantMappingEntity(
        merchantName = name,
        category = category,
        updatedAt = LocalDateTime.of(2026, 10, 20, 10, minute)
    )

    @Test
    fun `lookup ignores the merchant's casing`() {
        val lookup = listOf(mapping("ZOMATO", "Travel", 0)).toCategoryLookup()
        assertEquals("Travel", lookup["Zomato"])
        assertEquals("Travel", lookup["zomato"])
    }

    @Test
    fun `newest mapping wins among case variants`() {
        val lookup = listOf(mapping("Zomato", "Shopping", 5), mapping("ZOMATO", "Travel", 1)).toCategoryLookup()
        assertEquals("Shopping", lookup["zomato"])
    }

    @Test
    fun `non-ASCII letters are not folded, like SQLite NOCASE`() {
        val lookup = listOf(mapping("CAFÉ", "Travel", 1), mapping("Café", "Food", 5)).toCategoryLookup()
        assertEquals("Travel", lookup["cafÉ"])
        assertEquals("Food", lookup["CAFé"])
    }

    @Test
    fun `timestamp tie goes to the smallest name, like the DAO`() {
        val lookup = listOf(mapping("zomato", "Bills", 3), mapping("ZOMATO", "Travel", 3)).toCategoryLookup()
        assertEquals("Travel", lookup["Zomato"])
    }
}
