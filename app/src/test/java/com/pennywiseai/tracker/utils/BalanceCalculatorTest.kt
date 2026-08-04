package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class BalanceCalculatorTest {

    @Test
    fun `explicit balance takes priority over all logic`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = BigDecimal("100.00"),
            isCreditCard = false,
            transactionType = TransactionType.INCOME,
            transactionAmount = BigDecimal("50.00"),
            currentBalance = BigDecimal("500.00")
        )
        assertEquals(BigDecimal("100.00"), newBalance)
    }

    @Test
    fun `credit card income transaction subtracts from outstanding balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.INCOME,
            transactionAmount = BigDecimal("30.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("70.00"), newBalance)
    }

    @Test
    fun `credit card income transaction supports negative outstanding balance representing overpayment`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.INCOME,
            transactionAmount = BigDecimal("150.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("-50.00"), newBalance)
    }

    @Test
    fun `credit card credit purchase adds to outstanding balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.CREDIT,
            transactionAmount = BigDecimal("50.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("150.00"), newBalance)
    }

    @Test
    fun `debit income adds to standard account balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = false,
            transactionType = TransactionType.INCOME,
            transactionAmount = BigDecimal("50.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("150.00"), newBalance)
    }

    @Test
    fun `debit expense subtracts from standard account balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = false,
            transactionType = TransactionType.EXPENSE,
            transactionAmount = BigDecimal("40.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("60.00"), newBalance)
    }

    @Test
    fun `debit investment subtracts from standard account balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = false,
            transactionType = TransactionType.INVESTMENT,
            transactionAmount = BigDecimal("40.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("60.00"), newBalance)
    }

    @Test
    fun `debit transfer preserves standard account balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = false,
            transactionType = TransactionType.TRANSFER,
            transactionAmount = BigDecimal("100.00"),
            currentBalance = BigDecimal("500.00")
        )
        assertEquals(BigDecimal("500.00"), newBalance)
    }

    @Test
    fun `credit card with null current balance defaults to zero`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.CREDIT,
            transactionAmount = BigDecimal("50.00"),
            currentBalance = null
        )
        assertEquals(BigDecimal("50.00"), newBalance)
    }

    @Test
    fun `credit card transfer preserves outstanding balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.TRANSFER,
            transactionAmount = BigDecimal("100.00"),
            currentBalance = BigDecimal("500.00")
        )
        assertEquals(BigDecimal("500.00"), newBalance)
    }

    @Test
    fun `credit card expense adds to outstanding balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.EXPENSE,
            transactionAmount = BigDecimal("75.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("175.00"), newBalance)
    }

    @Test
    fun `credit card investment adds to outstanding balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.INVESTMENT,
            transactionAmount = BigDecimal("150.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("250.00"), newBalance)
    }

    @Test
    fun `debit expense supports negative balance representing overdraft`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = false,
            transactionType = TransactionType.EXPENSE,
            transactionAmount = BigDecimal("150.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("-50.00"), newBalance)
    }

    @Test
    fun `debit investment supports negative balance representing overdraft`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = false,
            transactionType = TransactionType.INVESTMENT,
            transactionAmount = BigDecimal("150.00"),
            currentBalance = BigDecimal("100.00")
        )
        assertEquals(BigDecimal("-50.00"), newBalance)
    }

    @Test
    fun `debit credit preserves standard account balance`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = false,
            transactionType = TransactionType.CREDIT,
            transactionAmount = BigDecimal("100.00"),
            currentBalance = BigDecimal("500.00")
        )
        assertEquals(BigDecimal("500.00"), newBalance)
    }

    // ── signedBalanceEffect — the shared sign convention for add/edit/delete ──

    @Test
    fun `signed effect on debit account follows money direction`() {
        val a = BigDecimal("100.00")
        assertEquals(a, BalanceCalculator.signedBalanceEffect(false, TransactionType.INCOME, a))
        assertEquals(a.negate(), BalanceCalculator.signedBalanceEffect(false, TransactionType.EXPENSE, a))
        assertEquals(a.negate(), BalanceCalculator.signedBalanceEffect(false, TransactionType.INVESTMENT, a))
        assertEquals(a.negate(), BalanceCalculator.signedBalanceEffect(false, TransactionType.CREDIT, a))
    }

    @Test
    fun `signed effect on credit card tracks outstanding owed`() {
        val a = BigDecimal("100.00")
        // Spending raises outstanding; income (refund/repayment) lowers it.
        assertEquals(a, BalanceCalculator.signedBalanceEffect(true, TransactionType.EXPENSE, a))
        assertEquals(a, BalanceCalculator.signedBalanceEffect(true, TransactionType.CREDIT, a))
        assertEquals(a, BalanceCalculator.signedBalanceEffect(true, TransactionType.INVESTMENT, a))
        assertEquals(a.negate(), BalanceCalculator.signedBalanceEffect(true, TransactionType.INCOME, a))
    }

    @Test
    fun `transfers have no single-account effect - their legs carry it`() {
        val a = BigDecimal("100.00")
        assertEquals(0, BalanceCalculator.signedBalanceEffect(false, TransactionType.TRANSFER, a).signum())
        assertEquals(0, BalanceCalculator.signedBalanceEffect(true, TransactionType.TRANSFER, a).signum())
    }

    @Test
    fun `edit net effect - expense to income on a debit account swings by twice the amount`() {
        val a = BigDecimal("100.00")
        val net = BalanceCalculator.signedBalanceEffect(false, TransactionType.INCOME, a) -
            BalanceCalculator.signedBalanceEffect(false, TransactionType.EXPENSE, a)
        assertEquals(BigDecimal("200.00"), net)
    }

    // ── transferLegEffect — credit-card-aware transfer legs ──

    @Test
    fun `transfer leg on debit account moves with the money`() {
        val a = BigDecimal("100.00")
        assertEquals(a, BalanceCalculator.transferLegEffect(false, incoming = true, a))
        assertEquals(a.negate(), BalanceCalculator.transferLegEffect(false, incoming = false, a))
    }

    @Test
    fun `transfer leg on credit card flips sign - a payment reduces outstanding`() {
        val a = BigDecimal("100.00")
        assertEquals(a.negate(), BalanceCalculator.transferLegEffect(true, incoming = true, a))
        assertEquals(a, BalanceCalculator.transferLegEffect(true, incoming = false, a))
    }
}
