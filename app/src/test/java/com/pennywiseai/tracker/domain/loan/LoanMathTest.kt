package com.pennywiseai.tracker.domain.loan

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanDirection.BORROWED
import com.pennywiseai.tracker.data.database.entity.LoanDirection.LENT
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionType.CREDIT
import com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE
import com.pennywiseai.tracker.data.database.entity.TransactionType.INCOME
import com.pennywiseai.tracker.data.database.entity.TransactionType.INVESTMENT
import com.pennywiseai.tracker.data.database.entity.TransactionType.TRANSFER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #876: a loan's balance counts every linked repayment, whatever type the parser gave it. */
class LoanMathTest {
    private var nextId = 1L
    private fun row(type: TransactionType, amount: String, contribution: String? = null, deleted: Boolean = false) =
        TransactionEntity(
            id = nextId++, amount = BigDecimal(amount), merchantName = "Pal", category = "Others",
            transactionType = type, dateTime = LocalDateTime.of(2026, 10, 7, 12, 0),
            transactionHash = "h$nextId", loanContribution = contribution?.let(::BigDecimal), isDeleted = deleted
        )

    /** What's still owed on a [direction] loan whose principal is its own linked rows. */
    private fun remaining(direction: LoanDirection, vararg linked: TransactionEntity): BigDecimal {
        val rows = linked.toList()
        return LoanMath.remaining(LoanMath.principal(direction, rows), LoanMath.repaid(direction, rows))
    }

    private fun assertOwed(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun `money in is principal only on a borrowed loan`() {
        assertTrue(LoanMath.isPrincipal(BORROWED, INCOME))
        assertTrue(LoanMath.isPrincipal(LENT, EXPENSE))
        assertFalse(LoanMath.isPrincipal(LENT, INCOME))
        for (out in listOf(EXPENSE, CREDIT, TRANSFER, INVESTMENT)) {
            assertFalse(LoanMath.isPrincipal(BORROWED, out))
            assertTrue(LoanMath.isPrincipal(LENT, out))
        }
    }

    @Test
    fun `borrowed loan partly repaid by a UPI payment typed CREDIT`() {
        // The reported case: borrow ₹500, repay ₹220 by UPI. Was "₹500 remaining, 0% repaid".
        assertOwed("280", remaining(BORROWED, row(INCOME, "500"), row(CREDIT, "220")))
    }

    @Test
    fun `borrowed loan partly repaid by a transfer or an expense`() {
        assertOwed("280", remaining(BORROWED, row(INCOME, "500"), row(TRANSFER, "220")))
        assertOwed("280", remaining(BORROWED, row(INCOME, "500"), row(EXPENSE, "220")))
    }

    @Test
    fun `borrowed loan fully repaid`() {
        assertOwed("0", remaining(BORROWED, row(INCOME, "500"), row(EXPENSE, "300"), row(CREDIT, "200")))
    }

    @Test
    fun `borrowed loan overpaid owes nothing`() {
        assertOwed("0", remaining(BORROWED, row(INCOME, "500"), row(EXPENSE, "700")))
    }

    @Test
    fun `lent loan partly repaid`() {
        assertOwed("380", remaining(LENT, row(EXPENSE, "500"), row(INCOME, "120")))
    }

    @Test
    fun `lent loan given by credit card and fully repaid`() {
        assertOwed("0", remaining(LENT, row(CREDIT, "500"), row(INCOME, "500")))
    }

    @Test
    fun `lent loan overpaid owes nothing`() {
        assertOwed("0", remaining(LENT, row(EXPENSE, "130"), row(INCOME, "500", contribution = "130")))
        assertOwed("0", remaining(LENT, row(EXPENSE, "130"), row(INCOME, "500")))
    }

    @Test
    fun `a linked row can't be retyped across the money-in line`() {
        assertTrue(LoanMath.blocksTypeChange(1, INCOME, TRANSFER))
        assertTrue(LoanMath.blocksTypeChange(1, CREDIT, INCOME))
        assertFalse(LoanMath.blocksTypeChange(1, EXPENSE, CREDIT))
        assertFalse(LoanMath.blocksTypeChange(null, INCOME, TRANSFER))
    }

    @Test
    fun `a rule can't retype a borrowed loan's principal into a repayment of itself`() {
        val borrowed = row(INCOME, "1000").copy(loanId = 1)
        val kept = LoanMath.keepLoanDirection(borrowed, borrowed.copy(transactionType = TRANSFER, category = "Bills"))
        assertEquals(INCOME, kept.transactionType)
        assertEquals("Bills", kept.category)
        assertOwed("1000", remaining(BORROWED, kept))
        // Same side of the line is fine, and unlinked rows are untouched.
        assertEquals(CREDIT, LoanMath.keepLoanDirection(row(EXPENSE, "5").copy(loanId = 1), row(CREDIT, "5")).transactionType)
        assertEquals(TRANSFER, LoanMath.keepLoanDirection(row(INCOME, "5"), row(TRANSFER, "5")).transactionType)
    }

    @Test
    fun `unlinking principal can't count a retyped repayment as principal`() {
        // Lent ₹1,000 + ₹500, ₹300 back; a rule tries to retype the repayment TRANSFER.
        val repayment = row(INCOME, "300").copy(loanId = 1)
        val kept = LoanMath.keepLoanDirection(repayment, repayment.copy(transactionType = TRANSFER))
        // After unlinking the ₹500, the principal left is the ₹1,000 alone, not ₹1,300.
        assertOwed("1000", LoanMath.principal(LENT, listOf(row(EXPENSE, "1000"), kept)))
    }

    @Test
    fun `only each row's loan part counts and deleted rows count nothing`() {
        // ₹1,000 paid, ₹400 of it lent; ₹100 back; a deleted ₹300 repayment ignored.
        val rows = listOf(row(EXPENSE, "1000", contribution = "400"), row(INCOME, "100"), row(INCOME, "300", deleted = true))
        assertOwed("400", LoanMath.principal(LENT, rows))
        assertOwed("100", LoanMath.repaid(LENT, rows))
    }
}
