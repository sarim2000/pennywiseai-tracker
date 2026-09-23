package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Regression coverage for [aggregateBudgetCategorySpending] — the single
 * shared function behind the Budgets screen, Home's budget card, and the
 * Budget home-screen widget for any budget with categories assigned.
 */
class AggregateBudgetCategorySpendingTest {

    @Test
    fun `excludes loan-linked transactions from category spending`() = runBlocking {
        val transactions = listOf(
            transaction(amount = BigDecimal("100.00"), category = "Food"),
            // Loan disbursement categorized as Food — must not count toward the budget.
            transaction(amount = BigDecimal("900.00"), category = "Food", loanId = 1L)
        )

        val (categoryAmounts, _, _) = aggregateBudgetCategorySpending(
            transactions = transactions,
            convertSplit = { _, amount -> amount },
            convertIncome = { tx -> tx.amount }
        )

        assertEquals(BigDecimal("100.00"), categoryAmounts["Food"])
    }

    @Test
    fun `excludes loan-linked type-bucket transactions`() = runBlocking {
        val transactions = listOf(
            transaction(amount = BigDecimal("200.00"), type = TransactionType.INVESTMENT),
            transaction(amount = BigDecimal("800.00"), type = TransactionType.INVESTMENT, loanId = 1L)
        )

        val (_, _, typeAmounts) = aggregateBudgetCategorySpending(
            transactions = transactions,
            convertSplit = { _, amount -> amount },
            convertIncome = { tx -> tx.amount }
        )

        assertEquals(BigDecimal("200.00"), typeAmounts[TransactionType.INVESTMENT.name])
    }

    private fun transaction(
        amount: BigDecimal,
        category: String = "Food",
        type: TransactionType = TransactionType.EXPENSE,
        loanId: Long? = null
    ): TransactionWithSplits {
        val entity = TransactionEntity(
            amount = amount,
            merchantName = "Merchant",
            category = category,
            transactionType = type,
            dateTime = LocalDateTime.now(),
            currency = "INR",
            transactionHash = "hash_" + (1..100000).random(),
            loanId = loanId
        )
        return TransactionWithSplits(transaction = entity, splits = emptyList())
    }
}
