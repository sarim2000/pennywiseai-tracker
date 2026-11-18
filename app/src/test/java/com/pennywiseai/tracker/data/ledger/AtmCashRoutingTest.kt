package com.pennywiseai.tracker.data.ledger

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AtmCashRoutingTest {
    private val now = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun row(
        merchant: String = "ATM Withdrawal",
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "INR",
        account: String? = "1111",
        sms: String? = "Rs.500 withdrawn at ATM"
    ) = TransactionEntity(
        amount = BigDecimal("500"), merchantName = merchant, category = "Others", transactionType = type,
        dateTime = now, transactionHash = "h", bankName = "Test Bank", accountNumber = account, currency = currency, smsBody = sms
    )

    private fun account(type: String?, currency: String = "INR", last4: String = "CASH") = AccountBalanceEntity(
        bankName = "Cash", accountLast4 = last4, balance = BigDecimal.ZERO, timestamp = now,
        accountType = type, currency = currency
    )

    private val wallets = listOf(account("SAVINGS", last4 = "2222"), account("CASH")).cashWallets()

    @Test
    fun `an ATM withdrawal moves from the bank to the Cash wallet`() {
        val routed = row(merchant = "ATM - Main Road").routeAtmToCashWallet(wallets)
        assertEquals(TransactionType.TRANSFER, routed.transactionType)
        assertEquals("1111" to "Test Bank", routed.fromAccount to routed.fromBankName)
        assertEquals("CASH" to "Cash", routed.toAccount to routed.toBankName)
        assertEquals("Cash" to "CASH", routed.walletLeg())
        // Axis names it just "ATM", with no withdrawal word in the SMS.
        val axis = row(merchant = "ATM", sms = "INR 2000 debited A/c no. XX1234 on AXIS BANK L")
        assertEquals(TransactionType.TRANSFER, axis.routeAtmToCashWallet(wallets).transactionType)
        val generic = row(merchant = "ATM Transaction", sms = "Rs.500 withdrawn at ATM")
        assertEquals(TransactionType.TRANSFER, generic.routeAtmToCashWallet(wallets).transactionType)
    }

    @Test
    fun `a debit card withdrawal leaves its linked account`() {
        val routed = row().routeAtmToCashWallet(wallets, sourceAccount = "9999")
        assertEquals("9999", routed.fromAccount)
    }

    @Test
    fun `everything else is left alone`() {
        for (r in listOf(
            row(merchant = "Atmosphere Cafe"),
            row(merchant = "ATM Charges"),
            row(merchant = "ATM Withdrawal Fee"),
            row(merchant = "ATM Transaction", sms = "debited with Rs.25.00 through Debitcard on other bank ATM"),
            row(type = TransactionType.INCOME),
            row(currency = "USD"),
            row(account = null),
        )) {
            assertSame(r, r.routeAtmToCashWallet(wallets))
        }
        val r = row()
        assertSame(r, r.routeAtmToCashWallet(emptyList()))
    }
}
