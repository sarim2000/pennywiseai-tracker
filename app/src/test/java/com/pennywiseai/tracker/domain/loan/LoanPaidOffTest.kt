package com.pennywiseai.tracker.domain.loan

import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** A debt is paid off only when a repayment takes it from owed to settled. */
class LoanPaidOffTest {
    private val owed = LoanEntity(
        id = 4, personName = "Pal", direction = LoanDirection.LENT,
        originalAmount = BigDecimal("500"), remainingAmount = BigDecimal("200"), currency = "INR"
    )
    private val settled = owed.copy(status = LoanStatus.SETTLED, remainingAmount = BigDecimal.ZERO)

    @Test
    fun `a repayment that clears what is owed pays it off`() {
        assertTrue(LoanMath.paidOff(owed, settled))
    }

    @Test
    fun `a partial repayment does not`() {
        assertFalse(LoanMath.paidOff(owed, owed.copy(remainingAmount = BigDecimal("50"))))
    }

    @Test
    fun `an overpayment that turned the loan around left a debt the other way`() {
        val turned = owed.copy(direction = LoanDirection.BORROWED, remainingAmount = BigDecimal("100"))
        assertFalse(LoanMath.paidOff(owed, turned))
        // Even settled, the other way round is not this debt paid off.
        assertFalse(LoanMath.paidOff(owed, settled.copy(direction = LoanDirection.BORROWED)))
    }

    @Test
    fun `a loan already settled, or with nothing left, has nothing to pay off`() {
        assertFalse(LoanMath.paidOff(settled, settled))
        assertFalse(LoanMath.paidOff(owed.copy(remainingAmount = BigDecimal.ZERO), settled))
    }

    @Test
    fun `a loan that couldn't be read either side, or another loan, is not paid off`() {
        assertFalse(LoanMath.paidOff(null, settled))
        assertFalse(LoanMath.paidOff(owed, null))
        assertFalse(LoanMath.paidOff(owed, settled.copy(id = 5)))
    }
}
