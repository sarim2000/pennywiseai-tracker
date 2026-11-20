package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.home.HomeFigureRows
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeFigureRowsRouteTest {
    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov20 = LocalDate.of(2026, 11, 20)

    @Test
    fun `a Home figure opens its type and days, pinned to its currency and profile`() {
        assertEquals(
            Transactions(
                type = "INCOME",
                period = "CUSTOM",
                currency = "USD",
                startDate = nov1.toEpochDay(),
                endDate = nov20.toEpochDay(),
                currencyOnly = true,
                profilePinned = true,
                profileId = ProfileEntity.BUSINESS_ID,
            ),
            transactionsFor(HomeFigureRows(TransactionTypeFilter.INCOME, nov1, nov20, "USD", true, ProfileEntity.BUSINESS_ID))
        )
    }

    @Test
    fun `unified mode lists every currency and every type when none is given`() {
        val rows = transactionsFor(HomeFigureRows(TransactionTypeFilter.ALL, nov1, nov20, "EUR", currencyOnly = false, profileId = null))
        assertEquals(null, rows.type)
        assertEquals("EUR" to false, rows.currency to rows.currencyOnly)
        // All profiles is a scope of its own, not "whatever Home shows".
        assertEquals(true to null, rows.profilePinned to rows.profileId)
    }

    @Test
    fun `Expenses counted with card spend opens on Spending`() {
        val rows = transactionsFor(HomeFigureRows(TransactionTypeFilter.SPENDING, nov1, nov20, "INR", true, null))
        assertEquals("SPENDING", rows.type)
    }
}
