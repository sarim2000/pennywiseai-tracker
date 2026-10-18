package com.pennywiseai.tracker.presentation.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #876: the edit screen showed "Wallet" after picking HDFC ••1155. */
class AccountMatchForTest {
    private fun acc(bank: String, last4: String, name: String) =
        TransactionDetailViewModel.AccountInfo(bankName = bank, accountLast4 = last4, displayName = name, isCreditCard = false)

    private val wallet = acc("Paytm", "1155", "Wallet")
    private val hdfc = acc("HDFC Bank", "1155", "HDFC Bank ••1155")
    private val shortNumber = acc("Cash Box", "55", "Cash Box")

    @Test
    fun `same number on two accounts is told apart by bank`() {
        assertEquals(hdfc, listOf(wallet, hdfc).matchFor("1155", "HDFC Bank"))
        assertEquals(wallet, listOf(wallet, hdfc).matchFor("1155", "Paytm"))
    }

    @Test
    fun `without a bank an ambiguous number matches nothing`() {
        assertNull(listOf(wallet, hdfc).matchFor("1155", null))
    }

    @Test
    fun `a short last-4 no longer suffix-matches other numbers`() {
        assertEquals(hdfc, listOf(shortNumber, hdfc).matchFor("1155", null))
        assertEquals(shortNumber, listOf(shortNumber, hdfc).matchFor("55", null))
    }

    @Test
    fun `a full account number still matches its last 4`() {
        assertEquals(hdfc, listOf(hdfc).matchFor("XXXXXX1155", "HDFC Bank"))
    }

    @Test
    fun `unknown bank falls back to the only account with that number`() {
        assertEquals(hdfc, listOf(hdfc).matchFor("1155", "Manual Entry"))
    }

    @Test
    fun `blank number matches nothing`() {
        assertNull(listOf(hdfc).matchFor("", "HDFC Bank"))
    }

    /** #913: a Cash account added without a number is ("Cash", ""). */
    @Test
    fun `blank number matches only the account with that bank and no number`() {
        val cash = acc("Cash", "", "Cash")
        val pettyCash = acc("Petty Cash", "", "Petty Cash")
        assertEquals(cash, listOf(hdfc, pettyCash, cash).matchFor("", "Cash"))
        assertNull(listOf(cash).matchFor("", null))
        assertNull(listOf(cash).matchFor(null, "Cash"))
    }
}
