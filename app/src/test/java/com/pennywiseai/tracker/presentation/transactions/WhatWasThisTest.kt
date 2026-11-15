package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.LOAN
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.OWN_TRANSFER
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.SAVING
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.SPLIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class WhatWasThisTest {
    private fun txn(type: TransactionType, lentShares: BigDecimal? = null, loanId: Long? = null) = TransactionEntity(
        amount = BigDecimal("450.00"),
        merchantName = "Cafe",
        category = "Food",
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 11, 1, 10, 0),
        currency = "INR",
        transactionHash = "h",
        lentSharesTotal = lentShares,
        loanId = loanId,
        bankName = "Bank A",
        accountNumber = "1111"
    )

    @Test
    fun `a plain expense offers every answer`() =
        assertEquals(listOf(LOAN, SPLIT, SAVING, OWN_TRANSFER), whatWasThisOptions(txn(TransactionType.EXPENSE), hasLoan = false))

    @Test
    fun `a transaction already on a loan or split offers nothing`() {
        assertEquals(emptyList<WhatWasThis>(), whatWasThisOptions(txn(TransactionType.EXPENSE, loanId = 1), hasLoan = true))
        assertEquals(emptyList<WhatWasThis>(), whatWasThisOptions(txn(TransactionType.INCOME, loanId = 1), hasLoan = true))
        assertEquals(emptyList<WhatWasThis>(), whatWasThisOptions(txn(TransactionType.EXPENSE, BigDecimal.TEN), hasLoan = false))
    }

    @Test
    fun `income can be a loan or an own transfer only`() =
        assertEquals(listOf(LOAN, OWN_TRANSFER), whatWasThisOptions(txn(TransactionType.INCOME), hasLoan = false))

    @Test
    fun `a paired transfer leg offers nothing`() =
        assertEquals(emptyList<WhatWasThis>(), whatWasThisOptions(txn(TransactionType.TRANSFER).copy(transferPairId = "p"), hasLoan = false))

    @Test
    fun `a transfer offers only the loan answer`() =
        assertEquals(listOf(LOAN), whatWasThisOptions(txn(TransactionType.TRANSFER), hasLoan = false))

    @Test
    fun `money out turned into a transfer keeps its account as the source`() {
        val expense = txn(TransactionType.EXPENSE)
        val edited = expense.copy(transactionType = TransactionType.TRANSFER).withKnownTransferLeg(expense)
        assertEquals("1111" to "Bank A", edited.fromAccount to edited.fromBankName)
        assertEquals(null, edited.toAccount)
        assertFalse(transferWithoutLegs(expense, edited))
    }

    @Test
    fun `money in turned into a transfer keeps its account as the destination`() {
        val income = txn(TransactionType.INCOME)
        val edited = income.copy(transactionType = TransactionType.TRANSFER).withKnownTransferLeg(income)
        assertEquals("1111" to "Bank A", edited.toAccount to edited.toBankName)
        assertEquals(null, edited.fromAccount)
    }

    @Test
    fun `a transfer with neither leg is refused`() {
        val expense = txn(TransactionType.EXPENSE).copy(accountNumber = null)
        val edited = expense.copy(transactionType = TransactionType.TRANSFER).withKnownTransferLeg(expense)
        assertTrue(transferWithoutLegs(expense, edited))
        // An existing transfer, or a paired leg, is left alone.
        assertFalse(transferWithoutLegs(edited, edited))
        assertFalse(transferWithoutLegs(expense, edited.copy(transferPairId = "p")))
    }

    @Test
    fun `the other leg must be on the same profile`() {
        val out = txn(TransactionType.EXPENSE).copy(id = 1)
        val into = txn(TransactionType.INCOME).copy(id = 2, accountNumber = "2222", dateTime = out.dateTime.plusMinutes(5))
        assertEquals(into, ownTransferPartner(out, listOf(out, into), emptyMap()))
        // The credit landed on a Business account: not this Personal transfer's other leg.
        val business = mapOf(2L to setOf("Bank A_2222"))
        assertEquals(null, ownTransferPartner(out, listOf(out, into), business))
        // Already on a loan: left alone, and a nearer loan credit doesn't hide the real leg.
        assertEquals(null, ownTransferPartner(out, listOf(out, into.copy(loanId = 7)), emptyMap()))
        val nearerLoan = into.copy(id = 3, loanId = 7, dateTime = out.dateTime.plusMinutes(1))
        assertEquals(into, ownTransferPartner(out, listOf(out, nearerLoan, into), emptyMap()))
    }

    @Test
    fun `an account picked in the editor is the leg that's kept`() {
        val expense = txn(TransactionType.EXPENSE)
        val edited = expense.copy(accountNumber = "3333", bankName = "Bank B")
        val transfer = edited.copy(transactionType = TransactionType.TRANSFER).withKnownTransferLeg(edited)
        assertEquals("3333" to "Bank B", transfer.fromAccount to transfer.fromBankName)
    }

    @Test
    fun `a refund or a row split across categories is never taken as the other leg`() {
        val out = txn(TransactionType.EXPENSE).copy(id = 1)
        val into = txn(TransactionType.INCOME).copy(id = 2, accountNumber = "2222", dateTime = out.dateTime.plusMinutes(5))
        val nearerRefund = into.copy(id = 3, budgetImpactType = BudgetImpactType.DEDUCT_SPENT, dateTime = out.dateTime.plusMinutes(1))
        assertEquals(into, ownTransferPartner(out, listOf(out, nearerRefund, into), emptyMap()))
        // From the incoming side: a categorised split expense stays as it is.
        assertEquals(null, ownTransferPartner(into, listOf(out, into), emptyMap(), categorySplitIds = setOf(1L)))
    }

    @Test
    fun `the same amount at another scale still pairs`() {
        val out = txn(TransactionType.EXPENSE).copy(id = 1, amount = BigDecimal("100.00"))
        val into = txn(TransactionType.INCOME).copy(
            id = 2, amount = BigDecimal("100.000"), accountNumber = "2222", dateTime = out.dateTime.plusMinutes(5)
        )
        assertEquals(into, ownTransferPartner(out, listOf(out, into), emptyMap()))
    }
}
