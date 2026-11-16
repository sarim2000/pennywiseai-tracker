package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.data.model.ChatLink
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChatLinkRouteTest {
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov16 = LocalDate.of(2026, 11, 16)

    @Test
    fun `a chat figure opens its days and category, pinned to its currency`() {
        assertEquals(
            Transactions(
                category = "Food",
                period = "CUSTOM",
                currency = "USD",
                startDate = nov1.toEpochDay(),
                endDate = nov16.toEpochDay(),
                currencyOnly = true,
            ),
            transactionsFor(ChatLink("See", nov1, nov16, "Food", "USD"))
        )
    }
}
