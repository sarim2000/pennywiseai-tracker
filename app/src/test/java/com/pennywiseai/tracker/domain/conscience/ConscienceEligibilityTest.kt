package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class ConscienceEligibilityTest {

    private val at = LocalDateTime.of(2026, 6, 15, 12, 0)
    private val keys = mapOf(
        "Food & Dining" to "Food & Dining",
        "Eats" to "Food & Dining",
        "Bills" to "Bills",
    )

    private val eligible = TransactionEntity(
        id = 7,
        amount = BigDecimal("12.50"),
        merchantName = "Test Merchant",
        category = "Food & Dining",
        transactionType = TransactionType.EXPENSE,
        dateTime = at,
        transactionHash = "hash",
        currency = "JOD",
    )

    private fun purchase(
        tx: TransactionEntity,
        countCredit: Boolean = false,
        splitIds: Set<Long> = emptySet(),
    ) = ConscienceEligibility.toPurchase(tx, keys, countCredit, splitIds)

    @Test
    fun `business spending is silent`() {
        assertNull(purchase(eligible.copy(profileId = 2L)))
        assertNotNull(purchase(eligible.copy(profileId = 1L)))
    }

    @Test
    fun `an ordinary expense becomes a purchase`() {
        assertEquals(
            ConsciencePurchase(at, BigDecimal("12.50"), "JOD", ConscienceCategory.FOOD),
            purchase(eligible),
        )
    }

    @Test
    fun `non-spending types are out`() {
        for (type in listOf(TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.INVESTMENT)) {
            assertNull(type.name, purchase(eligible.copy(transactionType = type), countCredit = true))
        }
    }

    @Test
    fun `credit follows the toggle`() {
        val credit = eligible.copy(transactionType = TransactionType.CREDIT)
        assertNull(purchase(credit, countCredit = false))
        assertNotNull(purchase(credit, countCredit = true))
    }

    @Test
    fun `each flag excludes`() {
        assertNull(purchase(eligible.copy(isDeleted = true)))
        assertNull(purchase(eligible.copy(excludedFromAnalytics = true)))
        assertNull(purchase(eligible.copy(isRecurring = true)))
        assertNull(purchase(eligible.copy(loanId = 1)))
        assertNull(purchase(eligible.copy(lentSharesTotal = BigDecimal("2"))))
    }

    @Test
    fun `a category-split transaction is out`() {
        assertNull(purchase(eligible, splitIds = setOf(7)))
        assertNotNull(purchase(eligible, splitIds = setOf(8)))
    }

    @Test
    fun `zero and negative amounts are out`() {
        assertNull(purchase(eligible.copy(amount = BigDecimal("0.00"))))
        assertNull(purchase(eligible.copy(amount = BigDecimal("-1"))))
    }

    @Test
    fun `a renamed builtin category resolves through the map`() {
        assertEquals(ConscienceCategory.FOOD, purchase(eligible.copy(category = "Eats"))?.category)
    }

    @Test
    fun `custom, unmapped and non-allowlisted categories are silent`() {
        assertNull(purchase(eligible.copy(category = "My Custom Category")))
        assertNull(purchase(eligible.copy(category = "Bills")))
        // Lookup is exact, not a fuzzy name match.
        assertNull(purchase(eligible.copy(category = "food & dining")))
    }
}
