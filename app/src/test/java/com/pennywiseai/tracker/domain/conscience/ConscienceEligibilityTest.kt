package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        hasSplits: Boolean = false,
    ) = ConscienceEligibility.toPurchase(tx, keys, countCredit, hasSplits)

    private fun history(tx: TransactionEntity, vararg splits: Pair<String, String>) = ConscienceEligibility.historyOf(
        TransactionWithSplits(tx, splits.map { (name, amount) ->
            TransactionSplitEntity(transactionId = tx.id, category = name, amount = BigDecimal(amount))
        }),
        keys,
    )

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
        assertNull(purchase(eligible, hasSplits = true))
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

    // --- History: silent as a trigger, still real spending ---

    private fun amounts(purchases: List<ConsciencePurchase>) =
        purchases.associate { it.category to it.amount }

    @Test
    fun `recurring and credit card spending stay in the history whatever the toggle`() {
        val recurring = eligible.copy(isRecurring = true)
        assertNull(purchase(recurring))
        assertEquals(mapOf(ConscienceCategory.FOOD to BigDecimal("12.50")), amounts(history(recurring)))

        val credit = eligible.copy(transactionType = TransactionType.CREDIT)
        assertNull(purchase(credit, countCredit = false))
        assertEquals(mapOf(ConscienceCategory.FOOD to BigDecimal("12.50")), amounts(history(credit)))
    }

    @Test
    fun `a shared bill counts the user's part, a partial loan what isn't lent`() {
        val shared = eligible.copy(amount = BigDecimal("100"), lentSharesTotal = BigDecimal("60"))
        assertNull(purchase(shared))
        assertEquals(mapOf(ConscienceCategory.FOOD to BigDecimal("40")), amounts(history(shared)))

        val partLoan = eligible.copy(amount = BigDecimal("100"), loanId = 1, loanContribution = BigDecimal("30"))
        assertEquals(mapOf(ConscienceCategory.FOOD to BigDecimal("70")), amounts(history(partLoan)))
    }

    @Test
    fun `a split row counts under its split categories, not its parent's`() {
        val row = eligible.copy(amount = BigDecimal("100"), category = "Bills")
        val purchases = history(row, "Food & Dining" to "30", "Bills" to "70")
        assertEquals(mapOf(ConscienceCategory.FOOD to BigDecimal("30")), amounts(purchases))
    }

    @Test
    fun `a shared split row shrinks its splits with the shared part`() {
        val row = eligible.copy(amount = BigDecimal("100"), lentSharesTotal = BigDecimal("50"))
        val purchases = history(row, "Food & Dining" to "60", "Bills" to "40")
        assertEquals(0, BigDecimal("30").compareTo(purchases.single().amount))
    }

    @Test
    fun `what isn't the user's spending stays out of the history`() {
        assertTrue(history(eligible.copy(excludedFromAnalytics = true)).isEmpty())
        assertTrue(history(eligible.copy(loanId = 1)).isEmpty())
        assertTrue(history(eligible.copy(profileId = 2L)).isEmpty())
        assertTrue(history(eligible.copy(transactionType = TransactionType.INCOME)).isEmpty())
        assertTrue(history(eligible.copy(category = "Bills")).isEmpty())
    }

    @Test
    fun `a recent recurring charge stops a false record and a false comeback`() {
        val today = at.toLocalDate()
        val ordinary = (2L..11L).map { today.minusDays(it) }.flatMap { day ->
            history(eligible.copy(dateTime = day.atTime(12, 0), amount = BigDecimal("10")))
        }
        val yesterday = history(
            eligible.copy(dateTime = today.minusDays(1).atTime(12, 0), amount = BigDecimal("100"), isRecurring = true)
        )
        val current = purchase(eligible.copy(amount = BigDecimal("20")))!!
        val ledgerStart = today.minusDays(365)

        assertEquals(
            ConscienceFact.RecordDay(ConscienceCategory.FOOD),
            ConscienceDetectors.detect(current, ordinary, ledgerStart),
        )
        assertNull(ConscienceDetectors.detect(current, ordinary + yesterday, ledgerStart))

        val quiet = ordinary.map { it.copy(at = it.at.minusDays(10)) }
        assertEquals(
            ConscienceFact.Comeback(ConscienceCategory.FOOD, 12),
            ConscienceDetectors.detect(current, quiet, ledgerStart),
        )
        assertNull(ConscienceDetectors.detect(current, quiet + yesterday, ledgerStart))
    }
}
