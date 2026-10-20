package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.presentation.home.spendableAccounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** Bank loan accounts (#792) follow the credit-card balance rules. */
class LoanAccountBalanceTest {

    private fun account(last4: String, balance: String, accountType: String? = null, isCreditCard: Boolean = false) =
        AccountBalanceEntity(
            bankName = "Test Bank",
            accountLast4 = last4,
            balance = BigDecimal(balance),
            timestamp = LocalDateTime.of(2026, 10, 1, 10, 0),
            accountType = accountType,
            isCreditCard = isCreditCard
        )

    private val loan = account("1111", "250000.00", accountType = "LOAN")
    private val savings = account("2222", "40000.00", accountType = "SAVINGS")
    private val smsAccount = account("3333", "15000.00")
    private val card = account("4444", "3000.00", isCreditCard = true)

    @Test
    fun `a loan account is a liability but not a credit card`() {
        assertTrue(loan.isLoan)
        assertTrue(loan.isLiability)
        assertFalse(loan.isCreditCard)
        assertEquals(AccountType.LOAN, loan.getAccountType())
        assertTrue(card.isLiability)
        assertFalse(savings.isLiability)
        assertFalse(smsAccount.isLiability)
    }

    @Test
    fun `a payment toward a loan lowers the outstanding amount`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = loan.isLiability,
            transactionType = TransactionType.INCOME,
            transactionAmount = BigDecimal("12000.00"),
            currentBalance = loan.balance
        )
        assertEquals(BigDecimal("238000.00"), newBalance)
    }

    @Test
    fun `a debit on a loan raises the outstanding amount`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = null,
            isCreditCard = loan.isLiability,
            transactionType = TransactionType.EXPENSE,
            transactionAmount = BigDecimal("500.00"),
            currentBalance = loan.balance
        )
        assertEquals(BigDecimal("250500.00"), newBalance)
    }

    @Test
    fun `an explicit outstanding from the SMS still wins on a loan`() {
        val newBalance = BalanceCalculator.calculateNewBalance(
            explicitBalance = BigDecimal("237500.00"),
            isCreditCard = loan.isLiability,
            transactionType = TransactionType.INCOME,
            transactionAmount = BigDecimal("12000.00"),
            currentBalance = loan.balance
        )
        assertEquals(BigDecimal("237500.00"), newBalance)
    }

    @Test
    fun `manual add, edit and transfer legs use the credit-card sign for a loan`() {
        assertEquals(
            BigDecimal("-12000.00"),
            BalanceCalculator.signedBalanceEffect(loan.isLiability, TransactionType.INCOME, BigDecimal("12000.00"))
        )
        // Money moved from savings into the loan pays it down.
        assertEquals(
            BigDecimal("-12000.00"),
            BalanceCalculator.transferLegEffect(loan.isLiability, incoming = true, amount = BigDecimal("12000.00"))
        )
    }

    @Test
    fun `an SMS on a loan never turns it into a credit card`() {
        assertFalse(BalanceCalculator.resolvedIsCreditCard(smsIsCard = true, existing = loan))
        assertFalse(BalanceCalculator.resolvedIsCreditCard(smsIsCard = false, existing = loan))
    }

    @Test
    fun `a card SMS does not undo a non-card type the user chose`() {
        assertFalse(BalanceCalculator.resolvedIsCreditCard(smsIsCard = true, existing = savings))
        assertFalse(BalanceCalculator.resolvedIsCreditCard(smsIsCard = true, existing = account("5555", "0", accountType = "CASH")))
    }

    @Test
    fun `untyped accounts and cards keep today's card detection`() {
        assertTrue(BalanceCalculator.resolvedIsCreditCard(smsIsCard = true, existing = smsAccount))
        assertTrue(BalanceCalculator.resolvedIsCreditCard(smsIsCard = true, existing = null))
        assertTrue(BalanceCalculator.resolvedIsCreditCard(smsIsCard = false, existing = card))
        assertTrue(BalanceCalculator.resolvedIsCreditCard(smsIsCard = false, existing = card.copy(accountType = "CREDIT")))
        assertFalse(BalanceCalculator.resolvedIsCreditCard(smsIsCard = false, existing = smsAccount))
        assertFalse(BalanceCalculator.resolvedIsCreditCard(smsIsCard = false, existing = null))
    }

    @Test
    fun `loans and cards stay out of the spendable bank total`() {
        val spendable = spendableAccounts(listOf(loan, savings, smsAccount, card))
        assertEquals(listOf(savings, smsAccount), spendable)
    }

    @Test
    fun `changing the type moves the account in or out of the bank total`() {
        val asLoan = smsAccount.copy(accountType = "LOAN")
        assertEquals(listOf(savings), spendableAccounts(listOf(savings, asLoan)))

        val backToSavings = asLoan.copy(accountType = "SAVINGS")
        assertEquals(listOf(savings, backToSavings), spendableAccounts(listOf(savings, backToSavings)))
    }
}
