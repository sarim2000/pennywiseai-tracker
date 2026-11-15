package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.LOAN
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.OWN_TRANSFER
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.SAVING
import com.pennywiseai.tracker.presentation.transactions.WhatWasThis.SPLIT
import org.junit.Assert.assertEquals
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
        loanId = loanId
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
    fun `a transfer offers only the loan answer`() =
        assertEquals(listOf(LOAN), whatWasThisOptions(txn(TransactionType.TRANSFER), hasLoan = false))
}
