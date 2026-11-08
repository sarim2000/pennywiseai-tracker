package com.pennywiseai.tracker.data.preferences

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore.Companion.isIgnored
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore.Companion.keyFor
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore.Companion.withLinkedCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #826: the gate that keeps a relative's account out of the app. */
class IgnoredAccountsStoreTest {

    private val ignored = setOf(keyFor("HDFC Bank", "4321"))

    @Test
    fun `an ignored account is skipped`() {
        assertTrue(isIgnored(ignored, "HDFC Bank", "4321"))
    }

    @Test
    fun `another account at the same bank still counts`() {
        assertFalse(isIgnored(ignored, "HDFC Bank", "9999"))
    }

    @Test
    fun `the same digits at a different bank still count`() {
        assertFalse(isIgnored(ignored, "ICICI Bank", "4321"))
    }

    @Test
    fun `a transaction we cannot attribute is never dropped`() {
        // Skipping on a null/blank account would silently lose real
        // transactions whose SMS had no account digits to parse.
        assertFalse(isIgnored(ignored, "HDFC Bank", null))
        assertFalse(isIgnored(ignored, null, "4321"))
        assertFalse(isIgnored(ignored, "HDFC Bank", ""))
        assertFalse(isIgnored(ignored, " ", "4321"))
    }

    @Test
    fun `a card purchase is dropped when its linked account is ignored`() {
        // A debit-card SMS carries the card's digits, but the money leaves the
        // linked account — ignoring the account has to stop the card too.
        assertTrue(isIgnored(ignored, "HDFC Bank", "8811", "4321"))
    }

    @Test
    fun `a card on a tracked account still counts`() {
        assertFalse(isIgnored(ignored, "HDFC Bank", "8811", "9999"))
        assertFalse(isIgnored(ignored, "HDFC Bank", "8811", null))
    }

    @Test
    fun `an ignored wallet is dropped even with no account digits`() {
        // Mobile-money wallets are one account keyed on the bank name, with
        // WALLET standing in for the digits the SMS never carries.
        val wallets = setOf(keyFor("eMola", "WALLET"))
        assertTrue(isIgnored(wallets, "eMola", null))
        assertTrue(isIgnored(wallets, "eMola"))
    }

    @Test
    fun `a tracked wallet still counts`() {
        val wallets = setOf(keyFor("eMola", "WALLET"))
        assertFalse(isIgnored(wallets, "M-PESA", null))
    }

    @Test
    fun `nothing is ignored when the set is empty`() {
        assertFalse(isIgnored(emptySet(), "HDFC Bank", "4321"))
    }

    private fun debitCard(digits: String, account: String?) = CardEntity(
        cardLast4 = digits, cardType = CardType.DEBIT, bankName = "HDFC Bank", accountLast4 = account,
    )

    private fun account(digits: String) = AccountBalanceEntity(
        bankName = "HDFC Bank", accountLast4 = digits, balance = BigDecimal.ONE, timestamp = LocalDateTime.MIN,
    )

    @Test
    fun `a card linked to an ignored account joins the set`() {
        val keys = withLinkedCards(ignored, listOf(debitCard("8811", "4321")), emptyList())
        assertTrue(isIgnored(keys, "HDFC Bank", "8811"))
    }

    @Test
    fun `a card on a tracked account stays out of the set`() {
        val keys = withLinkedCards(ignored, listOf(debitCard("8811", "9999"), debitCard("8812", null)), emptyList())
        assertEquals(ignored, keys)
    }

    @Test
    fun `a card whose digits match a tracked account stays out of the set`() {
        // Adding it would hide the real account, whose rows carry the same digits.
        val keys = withLinkedCards(ignored, listOf(debitCard("9999", "4321")), listOf(account("9999")))
        assertFalse(isIgnored(keys, "HDFC Bank", "9999"))
    }
}
