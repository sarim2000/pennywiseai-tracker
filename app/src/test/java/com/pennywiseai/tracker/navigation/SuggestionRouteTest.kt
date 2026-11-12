package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.domain.model.SuggestionDraft
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every draft field lands in the Add route under the same name. Synthetic data only. */
class SuggestionRouteTest {

    @Test
    fun `route carries every draft field`() {
        val draft = SuggestionDraft(
            amount = "12.500",
            merchant = "Test Store",
            bankName = "Test Bank",
            accountLast4 = "1234",
            currency = "USD",
            type = "EXPENSE",
            dateTime = "2024-03-05T14:30:15",
            unrecognizedSmsId = 42,
        )
        val route = draft.toRoute()
        assertEquals(
            AddTransaction(
                amount = "12.500",
                merchant = "Test Store",
                bankName = "Test Bank",
                accountLast4 = "1234",
                currency = "USD",
                type = "EXPENSE",
                dateTime = "2024-03-05T14:30:15",
                unrecognizedSmsId = 42,
                openId = route.openId,
            ),
            route,
        )
    }
}
