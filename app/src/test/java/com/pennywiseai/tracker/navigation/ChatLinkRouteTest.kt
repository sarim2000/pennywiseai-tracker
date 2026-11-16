package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.model.ChatLink
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChatLinkRouteTest {
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov16 = LocalDate.of(2026, 11, 16)

    @Test
    fun `a chat figure opens its days and category, pinned to its currency and profile`() {
        assertEquals(
            Transactions(
                category = "Food",
                period = "CUSTOM",
                currency = "USD",
                startDate = nov1.toEpochDay(),
                endDate = nov16.toEpochDay(),
                currencyOnly = true,
                profilePinned = true,
                profileId = ProfileEntity.BUSINESS_ID,
            ),
            transactionsFor(ChatLink("See", nov1, nov16, "Food", "USD", ProfileEntity.BUSINESS_ID))
        )
        // All profiles is a scope of its own, not "whatever Home shows".
        val all = transactionsFor(ChatLink("See", nov1, nov16, null, "USD", null))
        assertEquals(true to null, all.profilePinned to all.profileId)
    }

    @Test
    fun `a figure for one account opens on that account`() {
        assertEquals("HDFC_1234", transactionsFor(ChatLink("See", nov1, nov16, null, "INR", null, "HDFC_1234")).account)
    }
}
