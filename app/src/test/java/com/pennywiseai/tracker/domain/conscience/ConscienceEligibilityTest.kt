package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `business spending is out, whether the row or its account says so`() {
        val bank = eligible.copy(bankName = "Test Bank", accountNumber = "1234")
        val inScope = ConscienceEligibility.inScope(emptySet(), mapOf(ProfileEntity.BUSINESS_ID to setOf("Test Bank_1234")))
        assertFalse(inScope(bank))
        assertFalse(inScope(eligible.copy(profileId = ProfileEntity.BUSINESS_ID)))
        assertTrue(inScope(bank.copy(profileId = ProfileEntity.PERSONAL_ID)))
        assertTrue(inScope(bank.copy(accountNumber = "9999")))
    }

    @Test
    fun `an ignored account's rows are out`() {
        val bank = eligible.copy(bankName = "Test Bank", accountNumber = "1234")
        val inScope = ConscienceEligibility.inScope(setOf("Test Bank_1234"), emptyMap())
        assertFalse(inScope(bank))
        assertTrue(inScope(bank.copy(accountNumber = "9999")))
        assertTrue(inScope(eligible.copy(bankName = null, accountNumber = null)))
    }

    @Test
    fun `an ordinary expense becomes a purchase`() {
        assertEquals(
            ConsciencePurchase(at, BigDecimal("12.50"), "JOD", ConscienceCategory.FOOD, 7, "Test Merchant", false),
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
        assertTrue(history(eligible.copy(transactionType = TransactionType.INCOME)).isEmpty())
        assertTrue(history(eligible.copy(category = "Bills")).isEmpty())
    }

    @Test
    fun `history keeps the row's id, merchant and recurring flag, split parts share the id`() {
        val recurring = history(eligible.copy(id = 9, isRecurring = true)).single()
        assertEquals(9L, recurring.transactionId)
        assertEquals("Test Merchant", recurring.merchant)
        assertTrue(recurring.recurring)

        val row = eligible.copy(id = 11, amount = BigDecimal("100"), category = "Bills")
        val parts = history(row, "Food & Dining" to "30", "Eats" to "20", "Bills" to "50")
        assertEquals(listOf(11L), parts.map { it.transactionId }.distinct())
        assertTrue(parts.none { it.recurring })
    }
}
